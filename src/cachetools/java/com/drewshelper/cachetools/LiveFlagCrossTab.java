package com.drewshelper.cachetools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;

/**
 * Cross-tabulates the live client edge capture against cache wall and terrain facts.
 *
 * <p>The live file stores only north and east edges. South and west are derived from the neighbour
 * tile only when that neighbour is inside the measured covered area, because outside that bound the
 * capture has no ground truth.
 */
public final class LiveFlagCrossTab
{
    private static final Pattern SCENE_HEADER = Pattern.compile(
        "^DREW_LIVE_FLAGS\\s+scene\\s+(-?\\d+):(-?\\d+):(-?\\d+)\\s+size=(\\d+)\\s+covered=(\\d+)\\s*$"
    );
    private static final Pattern DATA_ROW = Pattern.compile(
        "^(-?\\d+),(-?\\d+),(-?\\d+)\\s+([01])([01])\\s*$"
    );

    private static final KeyProvider ZERO_KEYS = regionId -> new int[]{0, 0, 0, 0};

    private static final int REGION_SIZE = 64;
    private static final int MIN_REGION_X = 0;
    private static final int MAX_REGION_X = 255;
    private static final int MIN_REGION_Y = 0;
    private static final int MAX_REGION_Y = 255;
    private static final int PLANE_COUNT = 4;
    private static final int SMALL_SAMPLE_WARNING = 100;

    private static final int[] WALL_LOC_TYPES = {0, 1, 2, 3, 9};
    private static final Direction[] LOC_TYPE_0_EDGES_BY_ORIENTATION = {
        Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH
    };

    private static final String REPORT_FILE = "tools/live-flag-crosstab.txt";

    private static final Method BUILDER_FIRST_OPEN_STYLE_ACTION = findBuilderFirstOpenStyleAction();

    private LiveFlagCrossTab()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path liveFile = args.length == 0
            ? Paths.get(System.getProperty("user.home"), ".runelite", "drews-live-flags.txt")
            : Paths.get(args[0]);
        Path project = Paths.get(System.getProperty("user.dir"));
        Path outFile = project.resolve(REPORT_FILE);
        File cacheDir = new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");

        LiveCapture live = parseLiveCapture(liveFile);
        if (!cacheDir.isDirectory())
        {
            throw new IOException("No OSRS cache at " + cacheDir + " - populate the RuneLite cache first.");
        }

        Store store = new Store(cacheDir);
        store.load();
        try
        {
            CacheScan cache = scanCache(store, live);
            CrossTab tab = crossTab(live, cache);
            String report = buildReport(liveFile, cacheDir, project, live, cache, tab);

            Files.createDirectories(outFile.getParent());
            Files.write(outFile, report.getBytes(StandardCharsets.UTF_8));
            System.out.print(report);
        }
        finally
        {
            store.close();
        }
    }

    private static LiveCapture parseLiveCapture(Path liveFile) throws IOException
    {
        if (!Files.isRegularFile(liveFile))
        {
            throw new IOException("Live flag capture missing: " + liveFile);
        }
        if (Files.size(liveFile) == 0)
        {
            throw new IOException("Live flag capture is empty: " + liveFile);
        }

        LiveCapture capture = new LiveCapture();
        SceneBlock current = null;
        List<String> lines = Files.readAllLines(liveFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++)
        {
            int lineNumber = i + 1;
            String line = lines.get(i).trim();
            if (line.isEmpty())
            {
                continue;
            }

            Matcher header = SCENE_HEADER.matcher(line);
            if (header.matches())
            {
                finishSceneBlock(capture, current);
                current = parseSceneHeader(header, lineNumber);
                continue;
            }

            Matcher row = DATA_ROW.matcher(line);
            if (row.matches())
            {
                if (current == null)
                {
                    throw new IOException("Live flag data row before first scene header at line " + lineNumber);
                }
                addDataRow(capture, current, row, lineNumber);
                continue;
            }

            throw new IOException("Unrecognized live flag line " + lineNumber + ": " + line);
        }

        finishSceneBlock(capture, current);
        if (capture.sceneBlocks == 0)
        {
            throw new IOException("Live flag capture contains no scene headers: " + liveFile);
        }
        if (!capture.zeroRowSceneBlocks.isEmpty())
        {
            throw new IOException("Live flag capture has scene header blocks with zero data rows: "
                + String.join("; ", capture.zeroRowSceneBlocks));
        }

        return capture;
    }

    private static SceneBlock parseSceneHeader(Matcher header, int lineNumber) throws IOException
    {
        int baseX = parseInt(header.group(1), "scene baseX", lineNumber);
        int baseY = parseInt(header.group(2), "scene baseY", lineNumber);
        int plane = parseInt(header.group(3), "scene plane", lineNumber);
        int size = parseInt(header.group(4), "scene size", lineNumber);
        int covered = parseInt(header.group(5), "scene covered", lineNumber);

        if (size <= 0)
        {
            throw new IOException("Scene size must be positive at line " + lineNumber + ": " + size);
        }
        if (covered <= 0 || covered > size)
        {
            throw new IOException("Scene covered bound must be in 1..size at line " + lineNumber
                + ": covered=" + covered + ", size=" + size);
        }
        if (plane < 0 || plane >= PLANE_COUNT)
        {
            throw new IOException("Scene plane outside 0.." + (PLANE_COUNT - 1)
                + " at line " + lineNumber + ": " + plane);
        }

        return new SceneBlock(baseX, baseY, plane, size, covered, lineNumber);
    }

    private static void addDataRow(
        LiveCapture capture,
        SceneBlock block,
        Matcher row,
        int lineNumber
    )
        throws IOException
    {
        int x = parseInt(row.group(1), "row x", lineNumber);
        int y = parseInt(row.group(2), "row y", lineNumber);
        int plane = parseInt(row.group(3), "row plane", lineNumber);
        boolean north = "1".equals(row.group(4));
        boolean east = "1".equals(row.group(5));

        if (plane != block.plane)
        {
            throw new IOException("Live flag row plane does not match scene at line " + lineNumber
                + ": row plane=" + plane + ", scene plane=" + block.plane);
        }
        if (!block.contains(x, y))
        {
            throw new IOException("Live flag row outside exclusive covered bound at line " + lineNumber
                + ": " + x + "," + y + "," + plane + " not inside " + block.summary());
        }
        if (!north && !east)
        {
            throw new IOException("Live flag data row has no blocked edge at line " + lineNumber);
        }

        capture.rowsParsed++;
        long key = tileKey(x, y, plane);
        StoredLiveEdges previous = block.rows.put(key, new StoredLiveEdges(north, east));
        if (previous != null)
        {
            capture.duplicateRows++;
            if (previous.north != north || previous.east != east)
            {
                capture.duplicateRowConflicts++;
            }
        }
    }

    private static int parseInt(String value, String label, int lineNumber) throws IOException
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            throw new IOException("Invalid " + label + " at line " + lineNumber + ": " + value, e);
        }
    }

    private static void finishSceneBlock(LiveCapture capture, SceneBlock block)
    {
        if (block == null)
        {
            return;
        }

        capture.sceneBlocks++;
        capture.scenes.add(new SceneKey(block.baseX, block.baseY, block.plane));
        if (block.rows.isEmpty())
        {
            capture.zeroRowSceneBlocks.add(block.summary());
        }

        capture.coveredTileObservations += (long) block.covered * block.covered;
        int maxXExclusive = block.baseX + block.covered;
        int maxYExclusive = block.baseY + block.covered;
        for (int x = block.baseX; x < maxXExclusive; x++)
        {
            for (int y = block.baseY; y < maxYExclusive; y++)
            {
                long key = tileKey(x, y, block.plane);
                StoredLiveEdges row = block.rows.get(key);
                boolean north = row != null && row.north;
                boolean east = row != null && row.east;
                LiveTile tile = capture.tiles.get(key);
                if (tile == null)
                {
                    tile = new LiveTile(x, y, block.plane);
                    capture.tiles.put(key, tile);
                }
                tile.observe(north, east, capture);
            }
        }
    }

    private static CacheScan scanCache(Store store, LiveCapture live) throws IOException
    {
        Map<Integer, ObjectDefinition> objects = loadObjectDefinitions(store);
        RegionLoader loader = new RegionLoader(store, ZERO_KEYS);
        CacheScan scan = new CacheScan();

        for (int regionX = MIN_REGION_X; regionX <= MAX_REGION_X; regionX++)
        {
            for (int regionY = MIN_REGION_Y; regionY <= MAX_REGION_Y; regionY++)
            {
                int currentRegionId = regionId(regionX, regionY);
                RegionSource source = loadRegionSource(loader, currentRegionId);
                if (!source.present())
                {
                    scan.cacheRegionsMissing++;
                    continue;
                }

                scan.cacheRegionsLoaded++;
                scan.presentRegionIds.add(currentRegionId);
                if (source.map != null)
                {
                    scan.mapRegionsLoaded++;
                    scanTerrain(live, source, scan);
                }
                else
                {
                    scan.mapRegionsMissing++;
                }

                if (source.locations != null && source.locations.getLocations() != null)
                {
                    scan.locationRegionsLoaded++;
                    scanLocations(live, source, objects, scan);
                }
                else
                {
                    scan.locationRegionsMissing++;
                }
            }
        }

        for (LiveTile tile : live.tiles.values())
        {
            int currentRegionId = regionIdForTile(tile.x, tile.y);
            if (currentRegionId >= 0 && scan.presentRegionIds.contains(currentRegionId))
            {
                scan.coveredCacheTiles.add(tile.key());
            }
            else
            {
                scan.coveredTilesOutsideCacheRegion++;
            }
        }

        for (List<WallPlacement> placements : scan.wallPlacementsByTile.values())
        {
            placements.sort(Comparator
                .comparingInt((WallPlacement placement) -> placement.locType)
                .thenComparingInt(placement -> placement.orientation)
                .thenComparingInt(placement -> placement.id)
                .thenComparingInt(placement -> placement.openable ? 1 : 0));
        }

        return scan;
    }

    private static Map<Integer, ObjectDefinition> loadObjectDefinitions(Store store) throws IOException
    {
        ObjectManager manager = new ObjectManager(store);
        manager.load();
        Collection<ObjectDefinition> all = manager.getObjects();

        Map<Integer, ObjectDefinition> objects = new HashMap<>();
        for (ObjectDefinition def : all)
        {
            objects.put(def.getId(), def);
        }
        return objects;
    }

    private static RegionSource loadRegionSource(RegionLoader loader, int currentRegionId) throws IOException
    {
        MapDefinition map = null;
        LocationsDefinition locations = null;

        try
        {
            map = loader.loadMapDef(currentRegionId);
        }
        catch (Exception ignored)
        {
            map = null;
        }

        try
        {
            locations = loader.loadLocDef(currentRegionId);
        }
        catch (Exception ignored)
        {
            locations = null;
        }

        return new RegionSource(currentRegionId, map, locations);
    }

    private static void scanLocations(
        LiveCapture live,
        RegionSource source,
        Map<Integer, ObjectDefinition> objects,
        CacheScan scan
    )
    {
        int baseX = (source.regionId >> 8) * REGION_SIZE;
        int baseY = (source.regionId & 0xFF) * REGION_SIZE;

        for (Location location : source.locations.getLocations())
        {
            if (!isWallPlacement(location.getType()))
            {
                continue;
            }

            scan.wallPlacementsSeen++;
            int plane = location.getPosition().getZ();
            if (plane < 0 || plane >= PLANE_COUNT)
            {
                scan.skippedInvalidPlaneWallPlacements++;
                continue;
            }

            int x = baseX + location.getPosition().getX();
            int y = baseY + location.getPosition().getY();
            if (!live.isCovered(x, y, plane))
            {
                scan.skippedUncoveredWallPlacements++;
                continue;
            }

            ObjectDefinition def = objects.get(location.getId());
            boolean openable = firstOpenStyleAction(def) != null;
            if (openable)
            {
                scan.openableWallPlacementsCovered++;
            }

            WallPlacement placement = new WallPlacement(
                location.getId(),
                x,
                y,
                plane,
                location.getType(),
                location.getOrientation(),
                openable
            );
            scan.wallPlacementsByTile.computeIfAbsent(placement.key(), ignored -> new ArrayList<>())
                .add(placement);
            scan.wallPlacementsCovered++;
        }
    }

    private static void scanTerrain(LiveCapture live, RegionSource source, CacheScan scan)
    {
        int baseX = (source.regionId >> 8) * REGION_SIZE;
        int baseY = (source.regionId & 0xFF) * REGION_SIZE;
        Region terrain = new Region(source.regionId);
        terrain.loadTerrain(source.map);

        for (int plane = 0; plane < PLANE_COUNT; plane++)
        {
            for (int localX = 0; localX < REGION_SIZE; localX++)
            {
                for (int localY = 0; localY < REGION_SIZE; localY++)
                {
                    int tileSetting = terrain.getTileSetting(plane, localX, localY) & 0xFF;

                    /*
                     * This is the exact unverified bridge convention from CollisionMapBuilder.
                     * The cross-tab's job is to measure it, not improve or reinterpret it.
                     */
                    boolean bridge = (terrain.getTileSetting(1, localX, localY) & 2) != 0;
                    if ((tileSetting & 1) != 0 && bridge)
                    {
                        scan.bridgeBranchSourceTiles++;
                    }
                    int realPlane = bridge ? plane - 1 : plane;
                    if (realPlane < 0)
                    {
                        if ((tileSetting & 1) != 0 && bridge)
                        {
                            scan.bridgeBranchNegativePlaneTiles++;
                        }
                        continue;
                    }

                    int x = baseX + localX;
                    int y = baseY + localY;
                    long key = tileKey(x, y, realPlane);
                    boolean covered = live.isCovered(x, y, realPlane);
                    if (covered)
                    {
                        scan.tileSettingByTile.putIfAbsent(key, tileSetting);
                    }
                    if ((tileSetting & 1) == 0)
                    {
                        continue;
                    }

                    scan.terrainRuleSourceTiles++;
                    if (!covered)
                    {
                        scan.skippedUncoveredTerrainTiles++;
                        continue;
                    }

                    scan.terrainRuleCoveredMarks++;
                    if (!scan.terrainBlockedTiles.add(key))
                    {
                        scan.duplicateTerrainCoveredMarks++;
                    }
                    if (bridge)
                    {
                        scan.bridgeBranchCoveredMarks++;
                        if (!scan.bridgeBranchTiles.add(key))
                        {
                            scan.duplicateBridgeCoveredMarks++;
                        }
                    }
                }
            }
        }
    }

    private static CrossTab crossTab(LiveCapture live, CacheScan cache)
    {
        CrossTab tab = new CrossTab();
        for (LiveTile tile : live.tiles.values())
        {
            if (!cache.coveredCacheTiles.contains(tile.key()))
            {
                tab.excludedOutsideCacheRegion++;
                continue;
            }

            tab.coveredCacheTiles++;
            List<WallPlacement> placements = cache.wallPlacementsByTile.get(tile.key());
            if (placements == null)
            {
                placements = Collections.emptyList();
            }

            BlockedEdges liveEdges = readLiveEdges(live, tile);
            if (placements.isEmpty())
            {
                tab.noWallTiles++;
                tab.baseline.add(liveEdges);
            }
            else if (placements.size() == 1)
            {
                tab.singleWallTiles++;
                WallPlacement placement = placements.get(0);
                if (placement.locType == 1)
                {
                    tab.locType1.add(placement, liveEdges);
                }
                else if (placement.locType == 0)
                {
                    tab.locType0.add(placement, liveEdges);
                }
                else if (placement.locType == 9)
                {
                    tab.locType9.add(placement, liveEdges);
                    // S and W are DERIVED from the neighbour tile (S of T = N of T.y-1,
                    // W of T = E of T.x-1), so a wall on the neighbour blocks them for
                    // reasons that have nothing to do with THIS placement. Diagonal walls
                    // are corner fillers and sit next to other walls, so that confound is
                    // expected to be common. Split the sample on it.
                    boolean southNeighbourClean = !cache.wallPlacementsByTile
                        .containsKey(tileKey(tile.x, tile.y - 1, tile.plane));
                    boolean westNeighbourClean = !cache.wallPlacementsByTile
                        .containsKey(tileKey(tile.x - 1, tile.y, tile.plane));
                    if (southNeighbourClean && westNeighbourClean)
                    {
                        tab.locType9NeighbourClean.add(placement, liveEdges);
                    }
                    else
                    {
                        tab.locType9NeighbourContaminated.add(placement, liveEdges);
                    }
                }
            }
            else
            {
                tab.multipleWallTiles++;
                tab.skippedMultipleWallPlacements += placements.size();
            }

            boolean terrainBlocked = cache.terrainBlockedTiles.contains(tile.key());
            if (terrainBlocked)
            {
                tab.terrain.add(liveEdges);
            }
            if (cache.bridgeBranchTiles.contains(tile.key()))
            {
                tab.bridge.add(liveEdges);
            }
            tab.reverse.add(liveEdges, terrainBlocked, placements.isEmpty(),
                cache.tileSettingByTile.get(tile.key()));
        }

        return tab;
    }

    private static BlockedEdges readLiveEdges(LiveCapture live, LiveTile tile)
    {
        LiveTile southTile = live.tileAt(tile.x, tile.y - 1, tile.plane);
        LiveTile westTile = live.tileAt(tile.x - 1, tile.y, tile.plane);

        return new BlockedEdges(
            EdgeValue.usable(tile.northBlocked),
            EdgeValue.usable(tile.eastBlocked),
            southTile == null ? EdgeValue.unusable() : EdgeValue.usable(southTile.northBlocked),
            westTile == null ? EdgeValue.unusable() : EdgeValue.usable(westTile.eastBlocked)
        );
    }

    private static String buildReport(
        Path liveFile,
        File cacheDir,
        Path project,
        LiveCapture live,
        CacheScan cache,
        CrossTab tab
    )
    {
        StringBuilder report = new StringBuilder();
        report.append("live flag cross-tab").append('\n');
        report.append("live capture: ").append(liveFile).append('\n');
        report.append("cache: ").append(cacheDir).append('\n');
        report.append("project: ").append(project).append('\n');
        report.append("report: ").append(project.resolve(REPORT_FILE)).append('\n');
        report.append('\n');
        appendInputSummary(report, live);
        report.append('\n');
        appendCacheSummary(report, cache, tab);
        report.append('\n');
        appendNullBaseline(report, tab.baseline);
        report.append('\n');
        appendLocTypeTable(report, "Q1 locType 1 single-placement live edge rates", "UNKNOWN",
            tab.locType1, false);
        report.append('\n');
        report.append("locType 0 positive control: expected orientation peaks are ")
            .append("{0:W, 1:N, 2:E, 3:S}. If locType 0 does not come out clean against ")
            .append("live data, the harness itself is wrong and the locType 1 numbers ")
            .append("cannot be trusted.").append('\n');
        appendLocTypeTable(report, "locType 0 single-placement live edge rates", "expected",
            tab.locType0, true);
        report.append('\n');
        report.append("Q4 locType 9 is the diagonal-wall shape. The builder currently blocks ")
            .append("ALL FOUR edges on every locType 9 placement regardless of orientation, ")
            .append("so this table decides whether that is over-blocking or simply correct. ")
            .append("INTERPRETATION RULE, stated before the numbers: if all four edges sit ")
            .append("far above the no-wall baseline on every orientation, the all-four rule ")
            .append("is CORRECT and there is no win here - report that and stop. Only a ")
            .append("per-orientation split, where some edges lift and others sit at ")
            .append("baseline, justifies a narrower rule.").append('\n');
        appendLocTypeTable(report, "Q4 locType 9 single-placement live edge rates", "UNKNOWN",
            tab.locType9, false);
        report.append('\n');
        report.append("Q5 splits Q4 on neighbour contamination. Q4 showed S and W blocked ")
            .append("~100% on EVERY orientation while N and E sat at baseline. That split is ")
            .append("orientation-INVARIANT, which no real geometric rule can be - locType 0 ")
            .append("peaks move with orientation. The suspected cause is that S and W are ")
            .append("DERIVED from the neighbour tile, and diagonal walls are corner fillers ")
            .append("that sit beside other walls. NEIGHBOUR-CLEAN below means neither the S ")
            .append("(y-1) nor the W (x-1) neighbour carries any wall placement of its own.")
            .append('\n')
            .append("INTERPRETATION RULE, stated before the numbers: if S and W stay near ")
            .append("100% on the NEIGHBOUR-CLEAN table, the confound is refuted and locType ")
            .append("9 genuinely blocks S+W only - a narrower rule is then justified and must ")
            .append("still pass the 2,248-edge proof. If S and W COLLAPSE toward the no-wall ")
            .append("baseline once contaminated neighbours are removed, the confound is ")
            .append("confirmed, the existing all-four rule is correct, and locType 9 closes ")
            .append("as a dead lever. A clean-sample size below ~40 placements is too small ")
            .append("to conclude either way - say so rather than reading noise.").append('\n');
        appendLocTypeTable(report, "Q5a locType 9 NEIGHBOUR-CLEAN (no wall on S or W neighbour)",
            "UNKNOWN", tab.locType9NeighbourClean, false);
        report.append('\n');
        appendLocTypeTable(report, "Q5b locType 9 NEIGHBOUR-CONTAMINATED (wall on S and/or W neighbour)",
            "UNKNOWN", tab.locType9NeighbourContaminated, false);
        report.append('\n');
        appendTerrainReport(report, tab, cache);
        return report.toString();
    }

    private static void appendInputSummary(StringBuilder report, LiveCapture live)
    {
        report.append("input summary:").append('\n');
        report.append("  rows parsed: ").append(live.rowsParsed).append('\n');
        report.append("  scene headers: ").append(live.sceneBlocks).append('\n');
        report.append("  scenes found: ").append(live.scenes.size()).append('\n');
        report.append("  covered tile observations: ").append(live.coveredTileObservations).append('\n');
        report.append("  covered tiles unique: ").append(live.tiles.size()).append('\n');
        report.append("  duplicate data rows: ").append(live.duplicateRows).append('\n');
        report.append("  duplicate row conflicts: ").append(live.duplicateRowConflicts).append('\n');
        report.append("  overlapping live N/E conflicts: ")
            .append(live.conflictingNorthObservations + live.conflictingEastObservations)
            .append(" (N=").append(live.conflictingNorthObservations)
            .append(", E=").append(live.conflictingEastObservations)
            .append(')').append('\n');
    }

    private static void appendCacheSummary(StringBuilder report, CacheScan cache, CrossTab tab)
    {
        report.append("cache and exclusion summary:").append('\n');
        report.append("  cache regions loaded: ").append(cache.cacheRegionsLoaded).append('\n');
        report.append("  cache regions missing: ").append(cache.cacheRegionsMissing).append('\n');
        report.append("  map regions loaded: ").append(cache.mapRegionsLoaded).append('\n');
        report.append("  loc regions loaded: ").append(cache.locationRegionsLoaded).append('\n');
        report.append("  covered tiles inside cache regions: ").append(tab.coveredCacheTiles).append('\n');
        report.append("  covered tiles outside any cache region: ")
            .append(tab.excludedOutsideCacheRegion).append('\n');
        report.append("  wall placements seen: ").append(cache.wallPlacementsSeen).append('\n');
        report.append("  wall placements covered: ").append(cache.wallPlacementsCovered).append('\n');
        report.append("  wall placements excluded uncovered: ")
            .append(cache.skippedUncoveredWallPlacements).append('\n');
        report.append("  wall placements skipped invalid plane: ")
            .append(cache.skippedInvalidPlaneWallPlacements).append('\n');
        report.append("  covered openable wall placements: ")
            .append(cache.openableWallPlacementsCovered).append('\n');
        report.append("  no-wall covered tiles: ").append(tab.noWallTiles).append('\n');
        report.append("  single-wall covered tiles: ").append(tab.singleWallTiles).append('\n');
        report.append("  multi-wall covered tiles excluded from locType tables: ")
            .append(tab.multipleWallTiles)
            .append(" tiles / ")
            .append(tab.skippedMultipleWallPlacements)
            .append(" placements").append('\n');
        report.append("  terrain rule source tiles: ").append(cache.terrainRuleSourceTiles).append('\n');
        report.append("  terrain rule covered marks: ").append(cache.terrainRuleCoveredMarks).append('\n');
        report.append("  terrain rule covered unique tiles: ")
            .append(cache.terrainBlockedTiles.size()).append('\n');
        report.append("  terrain rule tiles excluded uncovered: ")
            .append(cache.skippedUncoveredTerrainTiles).append('\n');
        report.append("  bridge-branch source tiles: ").append(cache.bridgeBranchSourceTiles).append('\n');
        report.append("  bridge-branch negative-plane skips: ")
            .append(cache.bridgeBranchNegativePlaneTiles).append('\n');
        report.append("  bridge-branch covered marks: ")
            .append(cache.bridgeBranchCoveredMarks).append('\n');
        report.append("  bridge-branch covered unique tiles: ")
            .append(cache.bridgeBranchTiles.size()).append('\n');
        report.append("  duplicate terrain covered marks: ")
            .append(cache.duplicateTerrainCoveredMarks).append('\n');
        report.append("  duplicate bridge covered marks: ")
            .append(cache.duplicateBridgeCoveredMarks).append('\n');
    }

    private static void appendNullBaseline(StringBuilder report, DirectionStats baseline)
    {
        report.append("null baseline over covered tiles with no wall placement:").append('\n');
        appendStatsHeader(report, "group", true);
        appendStatsRow(report, "no-wall", "", baseline, true);
        appendSkipLine(report, "  derived-edge skips in baseline: ", baseline);
        report.append("  Percentages below should be read against this baseline.").append('\n');
    }

    private static void appendLocTypeTable(
        StringBuilder report,
        String title,
        String expectedHeader,
        LocTypeTable table,
        boolean showExpectedShape
    )
    {
        report.append(title).append(':').append('\n');
        appendStatsHeader(report, expectedHeader, true);

        for (int orientation : table.orientationsForReport())
        {
            String expected = showExpectedShape ? expectedLocType0(orientation) : "";
            appendStatsRow(
                report,
                "orient " + orientation,
                expected,
                table.statsForOrientation(orientation),
                table.openableForOrientation(orientation),
                true
            );
        }

        appendStatsRow(report, "combined", showExpectedShape ? "mixed" : "", table.combined,
            table.combinedOpenable, true);
        appendSkipLine(report, "  derived-edge skips in this table: ", table.combined);
    }

    private static void appendTerrainReport(StringBuilder report, CrossTab tab, CacheScan cache)
    {
        report.append("Q2 terrain floor-blocking rule:").append('\n');
        appendStatsHeader(report, "sample", false);
        appendStatsRow(report, "terrain", "all", tab.terrain.edges, false);
        appendLiftLine(report, tab.terrain.edges, tab.baseline);
        appendAgreementLine(report, "  terrain agreement", tab.terrain);
        report.append("  terrain sample count: ").append(tab.terrain.edges.tiles).append(" covered unique tiles")
            .append('\n');
        report.append('\n');

        report.append("Q2 bridge branch using plane-1 convention:").append('\n');
        report.append("  bridge sample count: ").append(tab.bridge.edges.tiles)
            .append(" covered unique tiles; source branch tiles=")
            .append(cache.bridgeBranchSourceTiles)
            .append(", negative-plane skips=")
            .append(cache.bridgeBranchNegativePlaneTiles)
            .append('\n');
        if (tab.bridge.edges.tiles < SMALL_SAMPLE_WARNING)
        {
            report.append("  bridge sample is small; read the counts before the percentages.").append('\n');
        }
        appendStatsHeader(report, "sample", false);
        appendStatsRow(report, "bridge", "plane-1", tab.bridge.edges, false);
        appendLiftLine(report, tab.bridge.edges, tab.baseline);
        appendAgreementLine(report, "  bridge agreement", tab.bridge);
        report.append('\n');

        report.append("Q2 reverse terrain miss direction:").append('\n');
        report.append("  covered cache tiles checked: ").append(tab.reverse.coveredCacheTiles).append('\n');
        report.append("  skipped because S/W neighbour was uncovered: ")
            .append(tab.reverse.skippedDerivedBoundaryTiles).append('\n');
        report.append("  tiles with all four live edges usable: ")
            .append(tab.reverse.allFourUsableTiles).append('\n');
        report.append("  live blocks all four edges: ")
            .append(tab.reverse.liveAllFourBlockedTiles).append('\n');
        report.append("  live-all-four tiles not terrain-marked: ")
            .append(tab.reverse.liveAllFourWithoutTerrainTiles)
            .append(" of ")
            .append(tab.reverse.liveAllFourBlockedTiles)
            .append(" (")
            .append(percent(tab.reverse.liveAllFourWithoutTerrainTiles,
                tab.reverse.liveAllFourBlockedTiles))
            .append(')').append('\n');
        report.append("  no-wall subset of live-all-four not terrain-marked: ")
            .append(tab.reverse.liveAllFourWithoutTerrainNoWallTiles).append('\n');
        report.append("  with-wall subset of live-all-four not terrain-marked: ")
            .append(tab.reverse.liveAllFourWithoutTerrainWithWallTiles).append('\n');
        report.append('\n');

        appendTileSettingBitsReport(report, tab.reverse);
    }

    private static void appendTileSettingBitsReport(StringBuilder report, ReverseTerrainStats stats)
    {
        long targetObserved = stats.targetTileSettingObserved();
        long controlObserved = stats.controlTileSettingObserved();

        report.append("Q3 terrain completeness - tileSetting bits").append('\n');
        report.append("  target total: ").append(stats.liveAllFourWithoutTerrainNoWallTiles)
            .append("; with tileSetting: ").append(targetObserved)
            .append("; missing tileSetting: ").append(stats.targetMissingTileSetting)
            .append('\n');
        report.append("  control total: ").append(stats.controlZeroBlockedNoWallTiles)
            .append("; with tileSetting: ").append(controlObserved)
            .append("; missing tileSetting: ").append(stats.controlMissingTileSetting)
            .append('\n');
        if (targetObserved == 0)
        {
            report.append("TERRAIN BITS VACUOUS - no target tiles carried a tileSetting").append('\n');
        }
        report.append("  per-bit set counts:").append('\n');
        for (int bit = 0; bit < 8; bit++)
        {
            long targetCount = stats.targetTileSettingBitCounts[bit];
            long controlCount = stats.controlTileSettingBitCounts[bit];
            double delta = ratePoints(targetCount, targetObserved)
                - ratePoints(controlCount, controlObserved);
            report.append(String.format(Locale.ROOT,
                "  bit %d: target %d (%s) | control %d (%s) | delta %spp",
                bit,
                targetCount,
                percent(targetCount, targetObserved),
                controlCount,
                percent(controlCount, controlObserved),
                formatSignedPoints(delta)))
                .append('\n');
        }
        appendTileSettingTopValues(report, "target", stats.targetTileSettingCounts, targetObserved);
        appendTileSettingTopValues(report, "control", stats.controlTileSettingCounts, controlObserved);
    }

    private static void appendTileSettingTopValues(
        StringBuilder report,
        String label,
        Map<Integer, Long> counts,
        long total
    )
    {
        report.append("  ").append(label)
            .append(" exact tileSetting byte values (top 15):").append('\n');
        report.append("    byte          count      pct").append('\n');

        List<Map.Entry<Integer, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) ->
        {
            int byCount = Long.compare(right.getValue(), left.getValue());
            if (byCount != 0)
            {
                return byCount;
            }
            return Integer.compare(left.getKey(), right.getKey());
        });

        int rows = Math.min(15, entries.size());
        if (rows == 0)
        {
            report.append("    (none)").append('\n');
            return;
        }
        for (int index = 0; index < rows; index++)
        {
            Map.Entry<Integer, Long> entry = entries.get(index);
            String byteLabel = String.format(Locale.ROOT, "0x%02X (%d)", entry.getKey(),
                entry.getKey());
            report.append(String.format(Locale.ROOT,
                "    %-10s %8d %8s",
                byteLabel,
                entry.getValue(),
                percent(entry.getValue(), total)))
                .append('\n');
        }
    }

    private static double ratePoints(long count, long total)
    {
        if (total == 0)
        {
            return 0.0;
        }
        return count * 100.0 / total;
    }

    private static void appendStatsHeader(StringBuilder report, String secondColumn, boolean includeOpenable)
    {
        if (includeOpenable)
        {
            report.append(String.format(Locale.ROOT,
                "  %-12s %8s %8s %8s %11s %8s %11s %8s %11s %8s %11s %8s",
                "group", secondColumn, "tiles", "open",
                "N block/use", "N%", "E block/use", "E%",
                "S block/use", "S%", "W block/use", "W%"))
                .append('\n');
        }
        else
        {
            report.append(String.format(Locale.ROOT,
                "  %-12s %8s %8s %11s %8s %11s %8s %11s %8s %11s %8s",
                "group", secondColumn, "tiles",
                "N block/use", "N%", "E block/use", "E%",
                "S block/use", "S%", "W block/use", "W%"))
                .append('\n');
        }
    }

    private static void appendStatsRow(
        StringBuilder report,
        String group,
        String expected,
        DirectionStats stats,
        boolean includeOpenable
    )
    {
        appendStatsRow(report, group, expected, stats, 0L, includeOpenable);
    }

    private static void appendStatsRow(
        StringBuilder report,
        String group,
        String expected,
        DirectionStats stats,
        long openable,
        boolean includeOpenable
    )
    {
        if (includeOpenable)
        {
            report.append(String.format(Locale.ROOT,
                "  %-12s %8s %8d %8d %11s %8s %11s %8s %11s %8s %11s %8s",
                group,
                expected,
                stats.tiles,
                openable,
                stats.blockedOverUsable(Direction.NORTH),
                stats.percent(Direction.NORTH),
                stats.blockedOverUsable(Direction.EAST),
                stats.percent(Direction.EAST),
                stats.blockedOverUsable(Direction.SOUTH),
                stats.percent(Direction.SOUTH),
                stats.blockedOverUsable(Direction.WEST),
                stats.percent(Direction.WEST)))
                .append('\n');
        }
        else
        {
            report.append(String.format(Locale.ROOT,
                "  %-12s %8s %8d %11s %8s %11s %8s %11s %8s %11s %8s",
                group,
                expected,
                stats.tiles,
                stats.blockedOverUsable(Direction.NORTH),
                stats.percent(Direction.NORTH),
                stats.blockedOverUsable(Direction.EAST),
                stats.percent(Direction.EAST),
                stats.blockedOverUsable(Direction.SOUTH),
                stats.percent(Direction.SOUTH),
                stats.blockedOverUsable(Direction.WEST),
                stats.percent(Direction.WEST)))
                .append('\n');
        }
    }

    private static void appendSkipLine(StringBuilder report, String prefix, DirectionStats stats)
    {
        report.append(prefix)
            .append("N=").append(stats.skipped(Direction.NORTH))
            .append(", E=").append(stats.skipped(Direction.EAST))
            .append(", S=").append(stats.skipped(Direction.SOUTH))
            .append(", W=").append(stats.skipped(Direction.WEST))
            .append('\n');
    }

    private static void appendLiftLine(
        StringBuilder report,
        DirectionStats stats,
        DirectionStats baseline
    )
    {
        report.append("  lift vs null baseline: ");
        boolean first = true;
        for (Direction direction : Direction.values())
        {
            if (!first)
            {
                report.append(", ");
            }
            first = false;
            double lift = stats.rate(direction) - baseline.rate(direction);
            report.append(direction.label)
                .append('=')
                .append(formatSignedPoints(lift))
                .append("pp");
        }
        report.append('\n');
        appendSkipLine(report, "  derived-edge skips in this sample: ", stats);
    }

    private static void appendAgreementLine(StringBuilder report, String label, TerrainAgreement stats)
    {
        report.append(label)
            .append(": edges ")
            .append(stats.blockedEdges)
            .append('/')
            .append(stats.usableEdges)
            .append(" (")
            .append(percent(stats.blockedEdges, stats.usableEdges))
            .append("); all-four live ")
            .append(stats.allFourBlockedTiles)
            .append('/')
            .append(stats.allFourUsableTiles)
            .append(" (")
            .append(percent(stats.allFourBlockedTiles, stats.allFourUsableTiles))
            .append(')')
            .append('\n');
    }

    private static String expectedLocType0(int orientation)
    {
        if (orientation < 0 || orientation >= LOC_TYPE_0_EDGES_BY_ORIENTATION.length)
        {
            return "?";
        }
        return LOC_TYPE_0_EDGES_BY_ORIENTATION[orientation].label;
    }

    private static String percent(long count, long total)
    {
        if (total == 0)
        {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", count * 100.0 / total);
    }

    private static String formatSignedPoints(double points)
    {
        return (points >= 0.0 ? "+" : "") + formatPoints(points);
    }

    private static String formatPoints(double points)
    {
        return String.format(Locale.ROOT, "%.1f", points);
    }

    private static boolean isWallPlacement(int locType)
    {
        for (int wall : WALL_LOC_TYPES)
        {
            if (wall == locType)
            {
                return true;
            }
        }
        return false;
    }

    private static String firstOpenStyleAction(ObjectDefinition def)
    {
        if (def == null)
        {
            return null;
        }

        try
        {
            return (String) BUILDER_FIRST_OPEN_STYLE_ACTION.invoke(null, def);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot call CollisionMapBuilder.firstOpenStyleAction", e);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            throw new IllegalStateException("CollisionMapBuilder.firstOpenStyleAction failed", cause);
        }
    }

    private static Method findBuilderFirstOpenStyleAction()
    {
        try
        {
            Method method = CollisionMapBuilder.class.getDeclaredMethod(
                "firstOpenStyleAction",
                ObjectDefinition.class
            );
            method.setAccessible(true);
            return method;
        }
        catch (NoSuchMethodException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static int regionIdForTile(int x, int y)
    {
        int regionX = Math.floorDiv(x, REGION_SIZE);
        int regionY = Math.floorDiv(y, REGION_SIZE);
        if (regionX < MIN_REGION_X || regionX > MAX_REGION_X
            || regionY < MIN_REGION_Y || regionY > MAX_REGION_Y)
        {
            return -1;
        }
        return regionId(regionX, regionY);
    }

    private static int regionId(int regionX, int regionY)
    {
        return (regionX << 8) | regionY;
    }

    private static long tileKey(int x, int y, int plane)
    {
        return (((long) x) << 34) | (((long) y) << 4) | (plane & 0xFL);
    }

    private enum Direction
    {
        NORTH("N"),
        EAST("E"),
        SOUTH("S"),
        WEST("W");

        private final String label;

        Direction(String label)
        {
            this.label = label;
        }
    }

    private static final class LiveCapture
    {
        private final Map<Long, LiveTile> tiles = new TreeMap<>();
        private final Set<SceneKey> scenes = new TreeSet<>();
        private final List<String> zeroRowSceneBlocks = new ArrayList<>();

        private long rowsParsed;
        private long sceneBlocks;
        private long coveredTileObservations;
        private long duplicateRows;
        private long duplicateRowConflicts;
        private long conflictingNorthObservations;
        private long conflictingEastObservations;

        private boolean isCovered(int x, int y, int plane)
        {
            return tiles.containsKey(tileKey(x, y, plane));
        }

        private LiveTile tileAt(int x, int y, int plane)
        {
            return tiles.get(tileKey(x, y, plane));
        }
    }

    private static final class SceneBlock
    {
        private final int baseX;
        private final int baseY;
        private final int plane;
        private final int size;
        private final int covered;
        private final int lineNumber;
        private final Map<Long, StoredLiveEdges> rows = new TreeMap<>();

        private SceneBlock(int baseX, int baseY, int plane, int size, int covered, int lineNumber)
        {
            this.baseX = baseX;
            this.baseY = baseY;
            this.plane = plane;
            this.size = size;
            this.covered = covered;
            this.lineNumber = lineNumber;
        }

        private boolean contains(int x, int y)
        {
            return x >= baseX && x < baseX + covered
                && y >= baseY && y < baseY + covered;
        }

        private String summary()
        {
            return baseX + ":" + baseY + ":" + plane
                + " size=" + size
                + " covered=" + covered
                + " line=" + lineNumber;
        }
    }

    private static final class SceneKey implements Comparable<SceneKey>
    {
        private final int baseX;
        private final int baseY;
        private final int plane;

        private SceneKey(int baseX, int baseY, int plane)
        {
            this.baseX = baseX;
            this.baseY = baseY;
            this.plane = plane;
        }

        @Override
        public int compareTo(SceneKey other)
        {
            int byX = Integer.compare(baseX, other.baseX);
            if (byX != 0)
            {
                return byX;
            }
            int byY = Integer.compare(baseY, other.baseY);
            if (byY != 0)
            {
                return byY;
            }
            return Integer.compare(plane, other.plane);
        }
    }

    private static final class StoredLiveEdges
    {
        private final boolean north;
        private final boolean east;

        private StoredLiveEdges(boolean north, boolean east)
        {
            this.north = north;
            this.east = east;
        }
    }

    private static final class LiveTile
    {
        private final int x;
        private final int y;
        private final int plane;
        private boolean northSeen;
        private boolean eastSeen;
        private boolean northBlocked;
        private boolean eastBlocked;

        private LiveTile(int x, int y, int plane)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }

        private void observe(boolean north, boolean east, LiveCapture capture)
        {
            if (northSeen && northBlocked != north)
            {
                capture.conflictingNorthObservations++;
            }
            if (eastSeen && eastBlocked != east)
            {
                capture.conflictingEastObservations++;
            }
            northSeen = true;
            eastSeen = true;
            northBlocked = north;
            eastBlocked = east;
        }

        private long key()
        {
            return tileKey(x, y, plane);
        }
    }

    private static final class CacheScan
    {
        private final Set<Integer> presentRegionIds = new HashSet<>();
        private final Set<Long> coveredCacheTiles = new HashSet<>();
        private final Map<Long, List<WallPlacement>> wallPlacementsByTile = new HashMap<>();
        private final Map<Long, Integer> tileSettingByTile = new HashMap<>();
        private final Set<Long> terrainBlockedTiles = new HashSet<>();
        private final Set<Long> bridgeBranchTiles = new HashSet<>();

        private long cacheRegionsLoaded;
        private long cacheRegionsMissing;
        private long mapRegionsLoaded;
        private long mapRegionsMissing;
        private long locationRegionsLoaded;
        private long locationRegionsMissing;
        private long wallPlacementsSeen;
        private long wallPlacementsCovered;
        private long skippedUncoveredWallPlacements;
        private long skippedInvalidPlaneWallPlacements;
        private long openableWallPlacementsCovered;
        private long coveredTilesOutsideCacheRegion;
        private long terrainRuleSourceTiles;
        private long terrainRuleCoveredMarks;
        private long skippedUncoveredTerrainTiles;
        private long duplicateTerrainCoveredMarks;
        private long bridgeBranchSourceTiles;
        private long bridgeBranchNegativePlaneTiles;
        private long bridgeBranchCoveredMarks;
        private long duplicateBridgeCoveredMarks;
    }

    private static final class RegionSource
    {
        private final int regionId;
        private final MapDefinition map;
        private final LocationsDefinition locations;

        private RegionSource(int regionId, MapDefinition map, LocationsDefinition locations)
        {
            this.regionId = regionId;
            this.map = map;
            this.locations = locations;
        }

        private boolean present()
        {
            return map != null || locations != null;
        }
    }

    private static final class WallPlacement
    {
        private final int id;
        private final int x;
        private final int y;
        private final int plane;
        private final int locType;
        private final int orientation;
        private final boolean openable;

        private WallPlacement(
            int id,
            int x,
            int y,
            int plane,
            int locType,
            int orientation,
            boolean openable
        )
        {
            this.id = id;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.locType = locType;
            this.orientation = orientation;
            this.openable = openable;
        }

        private long key()
        {
            return tileKey(x, y, plane);
        }
    }

    private static final class CrossTab
    {
        private final DirectionStats baseline = new DirectionStats();
        private final LocTypeTable locType1 = new LocTypeTable();
        private final LocTypeTable locType0 = new LocTypeTable();
        private final LocTypeTable locType9 = new LocTypeTable();
        private final LocTypeTable locType9NeighbourClean = new LocTypeTable();
        private final LocTypeTable locType9NeighbourContaminated = new LocTypeTable();
        private final TerrainAgreement terrain = new TerrainAgreement();
        private final TerrainAgreement bridge = new TerrainAgreement();
        private final ReverseTerrainStats reverse = new ReverseTerrainStats();

        private long coveredCacheTiles;
        private long excludedOutsideCacheRegion;
        private long noWallTiles;
        private long singleWallTiles;
        private long multipleWallTiles;
        private long skippedMultipleWallPlacements;
    }

    private static final class LocTypeTable
    {
        private final DirectionStats combined = new DirectionStats();
        private final Map<Integer, DirectionStats> byOrientation = new TreeMap<>();
        private final Map<Integer, Long> openableByOrientation = new TreeMap<>();

        private long combinedOpenable;

        private void add(WallPlacement placement, BlockedEdges edges)
        {
            DirectionStats stats = byOrientation.computeIfAbsent(
                placement.orientation,
                ignored -> new DirectionStats()
            );
            stats.add(edges);
            combined.add(edges);
            if (placement.openable)
            {
                openableByOrientation.merge(placement.orientation, 1L, Long::sum);
                combinedOpenable++;
            }
        }

        private TreeSet<Integer> orientationsForReport()
        {
            TreeSet<Integer> orientations = new TreeSet<>();
            for (int orientation = 0; orientation < 4; orientation++)
            {
                orientations.add(orientation);
            }
            orientations.addAll(byOrientation.keySet());
            return orientations;
        }

        private DirectionStats statsForOrientation(int orientation)
        {
            DirectionStats stats = byOrientation.get(orientation);
            return stats == null ? DirectionStats.empty() : stats;
        }

        private long openableForOrientation(int orientation)
        {
            Long openable = openableByOrientation.get(orientation);
            return openable == null ? 0L : openable;
        }
    }

    private static final class DirectionStats
    {
        private final long[] blocked = new long[Direction.values().length];
        private final long[] usable = new long[Direction.values().length];
        private final long[] skipped = new long[Direction.values().length];

        private long tiles;

        private static DirectionStats empty()
        {
            return new DirectionStats();
        }

        private void add(BlockedEdges edges)
        {
            tiles++;
            for (Direction direction : Direction.values())
            {
                EdgeValue value = edges.value(direction);
                if (!value.usable)
                {
                    skipped[direction.ordinal()]++;
                    continue;
                }

                usable[direction.ordinal()]++;
                if (value.blocked)
                {
                    blocked[direction.ordinal()]++;
                }
            }
        }

        private double rate(Direction direction)
        {
            long directionUsable = usable[direction.ordinal()];
            if (directionUsable == 0)
            {
                return 0.0;
            }
            return blocked[direction.ordinal()] * 100.0 / directionUsable;
        }

        private String percent(Direction direction)
        {
            return String.format(Locale.ROOT, "%.1f%%", rate(direction));
        }

        private String blockedOverUsable(Direction direction)
        {
            return blocked[direction.ordinal()] + "/" + usable[direction.ordinal()];
        }

        private long skipped(Direction direction)
        {
            return skipped[direction.ordinal()];
        }
    }

    private static final class TerrainAgreement
    {
        private final DirectionStats edges = new DirectionStats();

        private long usableEdges;
        private long blockedEdges;
        private long allFourUsableTiles;
        private long allFourBlockedTiles;

        private void add(BlockedEdges liveEdges)
        {
            edges.add(liveEdges);
            usableEdges += liveEdges.usableCount();
            blockedEdges += liveEdges.blockedUsableCount();
            if (liveEdges.allUsable())
            {
                allFourUsableTiles++;
                if (liveEdges.allBlocked())
                {
                    allFourBlockedTiles++;
                }
            }
        }
    }

    private static final class ReverseTerrainStats
    {
        private long coveredCacheTiles;
        private long skippedDerivedBoundaryTiles;
        private long allFourUsableTiles;
        private long liveAllFourBlockedTiles;
        private long liveAllFourWithoutTerrainTiles;
        private long liveAllFourWithoutTerrainNoWallTiles;
        private long liveAllFourWithoutTerrainWithWallTiles;
        private final Map<Integer, Long> targetTileSettingCounts = new HashMap<>();
        private final Map<Integer, Long> controlTileSettingCounts = new HashMap<>();
        private final long[] targetTileSettingBitCounts = new long[8];
        private final long[] controlTileSettingBitCounts = new long[8];
        private long targetMissingTileSetting;
        private long controlMissingTileSetting;
        private long controlZeroBlockedNoWallTiles;

        private void add(
            BlockedEdges liveEdges,
            boolean terrainBlocked,
            boolean noWallPlacement,
            Integer tileSetting
        )
        {
            coveredCacheTiles++;
            if (!liveEdges.allUsable())
            {
                skippedDerivedBoundaryTiles++;
                return;
            }

            allFourUsableTiles++;
            if (noWallPlacement && liveEdges.blockedUsableCount() == 0)
            {
                controlZeroBlockedNoWallTiles++;
                addControlTileSetting(tileSetting);
            }
            if (!liveEdges.allBlocked())
            {
                return;
            }

            liveAllFourBlockedTiles++;
            if (terrainBlocked)
            {
                return;
            }

            liveAllFourWithoutTerrainTiles++;
            if (noWallPlacement)
            {
                liveAllFourWithoutTerrainNoWallTiles++;
                addTargetTileSetting(tileSetting);
            }
            else
            {
                liveAllFourWithoutTerrainWithWallTiles++;
            }
        }

        private void addTargetTileSetting(Integer tileSetting)
        {
            if (tileSetting == null)
            {
                targetMissingTileSetting++;
                return;
            }
            addTileSetting(targetTileSettingCounts, targetTileSettingBitCounts, tileSetting);
        }

        private void addControlTileSetting(Integer tileSetting)
        {
            if (tileSetting == null)
            {
                controlMissingTileSetting++;
                return;
            }
            addTileSetting(controlTileSettingCounts, controlTileSettingBitCounts, tileSetting);
        }

        private static void addTileSetting(
            Map<Integer, Long> counts,
            long[] bitCounts,
            int tileSetting
        )
        {
            counts.put(tileSetting, counts.getOrDefault(tileSetting, 0L) + 1L);
            for (int bit = 0; bit < 8; bit++)
            {
                if ((tileSetting & (1 << bit)) != 0)
                {
                    bitCounts[bit]++;
                }
            }
        }

        private long targetTileSettingObserved()
        {
            return liveAllFourWithoutTerrainNoWallTiles - targetMissingTileSetting;
        }

        private long controlTileSettingObserved()
        {
            return controlZeroBlockedNoWallTiles - controlMissingTileSetting;
        }
    }

    private static final class BlockedEdges
    {
        private final EdgeValue north;
        private final EdgeValue east;
        private final EdgeValue south;
        private final EdgeValue west;

        private BlockedEdges(EdgeValue north, EdgeValue east, EdgeValue south, EdgeValue west)
        {
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
        }

        private EdgeValue value(Direction direction)
        {
            switch (direction)
            {
                case NORTH:
                    return north;
                case EAST:
                    return east;
                case SOUTH:
                    return south;
                case WEST:
                    return west;
                default:
                    throw new IllegalArgumentException("Unhandled direction " + direction);
            }
        }

        private boolean allUsable()
        {
            return north.usable && east.usable && south.usable && west.usable;
        }

        private boolean allBlocked()
        {
            return allUsable() && north.blocked && east.blocked && south.blocked && west.blocked;
        }

        private long usableCount()
        {
            long count = 0;
            for (Direction direction : Direction.values())
            {
                if (value(direction).usable)
                {
                    count++;
                }
            }
            return count;
        }

        private long blockedUsableCount()
        {
            long count = 0;
            for (Direction direction : Direction.values())
            {
                EdgeValue edge = value(direction);
                if (edge.usable && edge.blocked)
                {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class EdgeValue
    {
        private final boolean usable;
        private final boolean blocked;

        private EdgeValue(boolean usable, boolean blocked)
        {
            this.usable = usable;
            this.blocked = blocked;
        }

        private static EdgeValue usable(boolean blocked)
        {
            return new EdgeValue(true, blocked);
        }

        private static EdgeValue unusable()
        {
            return new EdgeValue(false, false);
        }
    }
}
