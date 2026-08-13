package com.drewshelper.cachetools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;
import net.runelite.api.CollisionDataFlag;

/**
 * Builds the v2 per-region edge map from the OSRS cache.
 *
 * <p>The shipped archive stays runtime-compatible with the two-flag reader. Door flags are kept in
 * the builder/report only until a door-aware runtime reader exists, and a door edge is written
 * PASSABLE in that archive - see archivePassable.
 */
public final class CollisionMapBuilder
{
    /**
     * The cache stores map archives unencrypted, so a zero key means "do not decrypt" and
     * everything parses. This matches the other cachetools programs.
     */
    private static final KeyProvider ZERO_KEYS = regionId -> new int[]{0, 0, 0, 0};

    private static final int REGION_SIZE = 64;
    private static final int MIN_REGION_X = 0;
    private static final int MAX_REGION_X = 255;
    private static final int MIN_REGION_Y = 0;
    private static final int MAX_REGION_Y = 255;
    private static final int PLANE_COUNT = 4;
    private static final int FLAG_COUNT = 4;
    private static final int ARCHIVE_FLAG_COUNT = 2;
    private static final int STORED_EDGE_COUNT = 2;

    private static final int FLAG_NORTH_PASSABLE = 0;
    private static final int FLAG_EAST_PASSABLE = 1;
    private static final int FLAG_NORTH_DOOR = 2;
    private static final int FLAG_EAST_DOOR = 3;

    private static final String DEFAULT_ZIP = "build/collision-map-v2.zip";
    private static final String PROOF_FILE = "tools/route-a-live-mismatches.txt";
    private static final String REPORT_FILE = "tools/collision-map-v2-report.txt";
    private static final String LIVE_FLAGS_ARG = "--live-flags";
    private static final String DISABLE_PHASE2_SOLID_OBJECTS_ARG = "--disable-phase2-solid-objects";
    private static final String DISABLE_PHASE3_ROOF_BLOCKING_ARG = "--disable-phase3-roof-blocking";
    private static final String ROOF_LOC_TYPES_ARG = "--roof-loctypes";
    /*
     * The cache has no map or loc archive for a handful of regions that the SHIPPED archive still
     * contains, so an all-region rebuild is not a superset of it. Measured 2026-08-12: 13 such
     * regions, and 275 transport endpoints land inside three of them. Overwriting the shipped
     * archive would silently delete map data that cannot be regenerated, so the ship path carries
     * those entries across verbatim instead.
     */
    private static final String MERGE_FROM_ARG = "--merge-from";
    /*
     * 12, 13, 14, 16, 17, 18, 19, 21 - the roof locTypes the per-locType proof pass cleared on
     * 2026-08-12. Overridable from the command line so a single locType can be added or removed
     * and re-measured without a recompile, which is how the set should be revised: by evidence.
     */
    private static final int DEFAULT_ROOF_LOC_TYPE_MASK =
        (1 << 12) | (1 << 13) | (1 << 14) | (1 << 16)
        | (1 << 17) | (1 << 18) | (1 << 19) | (1 << 21);
    private static final int STILL_BLOCKED_EXAMPLE_LIMIT = 30;
    private static final int DANGEROUS_EXAMPLE_LIMIT = 30;
    private static final int DANGEROUS_UNEXPLAINED_EXAMPLE_LIMIT = 20;
    private static final int ORIENT3_DANGEROUS_SAMPLE_FLOOR = 30;
    // Defines both the border histogram verdict bucket and the headline comparison exclusion.
    private static final int BORDER_MAX_DISTANCE = 2;
    private static final int INTERIOR_MIN_DISTANCE = 20;
    private static final double BORDER_CONFIRMED_RATE_MULTIPLIER = 3.0;
    private static final double BORDER_REFUTED_RATE_MULTIPLIER = 1.5;
    private static final double BORDER_CONFIRMED_UNEXPLAINED_SHARE = 0.40;
    private static final long BORDER_INTERIOR_COMPARED_EDGE_FLOOR = 500L;
    // A room interior floor tile carries no placement of its own but is adjacent to its walls.
    private static final int NEAR_STRUCTURE_RADIUS = 1;
    private static final int OCCUPANCY_CENSUS_REGION_ID = (46 << 8) | 52;
    /*
     * locType 22 is ground decoration, expected to be non-blocking, and is therefore excluded from
     * the candidate ignored-placement set on purpose.
     */
    private static final int GROUND_DECOR_LOC_TYPE = 22;
    /*
     * The phase 2 gate used to compare against hardcoded baselines measured on a 24-region run
     * (DANGEROUS 33672, AGREE_OPEN 161245, OVERBLOCK 4239, route-aware OVERBLOCK 2616). On any
     * other region set the verdict was meaningless: a 62-region run read AGREE_OPEN 854157
     * against a 161245 baseline and reported ABORT on nothing but a bigger sample. The baseline
     * is now measured live - same regions, same capture, built again with phase 2 forced off.
     */
    private static final int LOC_TYPE_MASK_BITS = 24;
    private static final int LIVE_BLOCKED_TILE_MASK = CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
        | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
        | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
        | CollisionDataFlag.BLOCK_MOVEMENT_FULL;

    private static final BorderDistanceBucket[] BORDER_DISTANCE_BUCKETS = {
        new BorderDistanceBucket("0", 0, 0),
        new BorderDistanceBucket("1", 1, 1),
        new BorderDistanceBucket("2", 2, 2),
        new BorderDistanceBucket("3", 3, 3),
        new BorderDistanceBucket("4", 4, 4),
        new BorderDistanceBucket("5-9", 5, 9),
        new BorderDistanceBucket("10-19", 10, 19),
        new BorderDistanceBucket("20+", 20, Integer.MAX_VALUE)
    };

    private static final Pattern LIVE_SCENE_HEADER = Pattern.compile(
        "^DREW_LIVE_FLAGS\\s+scene\\s+(-?\\d+):(-?\\d+):(-?\\d+)\\s+size=(\\d+)\\s+covered=(\\d+)\\s*$"
    );
    private static final Pattern LIVE_DATA_ROW = Pattern.compile(
        "^(-?\\d+),(-?\\d+),(-?\\d+)\\s+([01])([01])(?:\\s+(-?\\d+))?\\s*$"
    );

    /*
     * Measured shape table: derived by LocTypeShapeProbe over 558,894 tiles against a 22% null
     * baseline. Do not replace these with convention-based orientation assumptions.
     */
    private static final Direction[] LOC_TYPE_0_EDGES_BY_ORIENTATION = {
        Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH
    };
    private static final Direction[] LOC_TYPE_3_EDGES_BY_ORIENTATION = {
        Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH
    };
    private static final Direction[][] LOC_TYPE_2_EDGES_BY_ORIENTATION = {
        {Direction.NORTH, Direction.WEST},
        {Direction.NORTH, Direction.EAST},
        {Direction.EAST, Direction.SOUTH},
        {Direction.SOUTH, Direction.WEST}
    };
    /*
     * LiveFlagCrossTab measured locType 1 over 1,408 single-placement tiles. The selected peaks are
     * 61-66% against a ~25% null baseline, a 35-40pp lift, which is real but markedly weaker than
     * locType 0's 97-99%; locType 1 probably covers more than one underlying shape. Orientation 3
     * blocking nothing moves those edges from blocked to PASSABLE, which is the dangerous direction
     * under D-0120 because a wrongly-passable edge makes the router plan through a wall. It is
     * justified only because 15-18% is below the null baseline, measured-open rather than unknown.
     * If the proof numbers worsen, this is the first thing to revert.
     */
    private static final Direction[][] LOC_TYPE_1_EDGES_BY_ORIENTATION = {
        {Direction.NORTH},
        {Direction.NORTH, Direction.EAST},
        {Direction.EAST},
        {}
    };
    private static final Direction[] LOC_TYPE_9_EDGES = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final Method PROOF_PARSE_EDGES = findProofParseEdges();
    private static final Method PROOF_FIRST_OPEN_STYLE_ACTION = findProofFirstOpenStyleAction();

    private CollisionMapBuilder()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Path project = Paths.get(System.getProperty("user.dir"));
        Path proofFile = project.resolve(PROOF_FILE);
        List<ProofEdge> proofEdges = parseProofEdges(proofFile);
        if (proofEdges.isEmpty())
        {
            throw new IOException("Proof file contains no OURS_BLOCKS_LIVE_OPEN edges: " + proofFile);
        }

        BuildRequest request = parseRequest(args, project, proofEdges);
        File cacheDir = new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");
        if (!cacheDir.isDirectory())
        {
            throw new IOException("No OSRS cache at " + cacheDir + " - populate the RuneLite cache first.");
        }

        Store store = new Store(cacheDir);
        store.load();
        try
        {
            BuildResult result = build(store, request);
            writeZip(request.outputZip, result.regions);
            String roundTrip = verifyRoundTrip(request.outputZip, result.regions)
                + mergeMissingRegions(request.outputZip, request.mergeFrom);
            Comparison comparison = compareProofEdges(proofEdges, result.regions);
            DangerousDirectionComparison dangerousComparison = compareDangerousDirections(request.liveFlagsFile, result);
            Phase2Baseline phase2Baseline = measurePhase2Baseline(store, request, proofEdges);
            String report = buildReport(
                cacheDir,
                project,
                request,
                result,
                roundTrip,
                comparison,
                dangerousComparison,
                phase2Baseline
            );

            Path reportFile = project.resolve(REPORT_FILE);
            Files.createDirectories(reportFile.getParent());
            Files.write(reportFile, report.getBytes(StandardCharsets.UTF_8));
            System.out.print(report);
        }
        finally
        {
            store.close();
        }
    }

    private static Phase2Baseline measurePhase2Baseline(
        Store store,
        BuildRequest request,
        List<ProofEdge> proofEdges
    ) throws IOException
    {
        if (!request.phase2SolidObjectBlocking && !request.phase3RoofBlocking)
        {
            return null;
        }

        /*
         * The gate asks "is enabling phase 2 better than not enabling it, on THIS region set".
         * Only a baseline measured on the same regions and the same capture can answer that, so
         * the build is simply run again with phase 2 forced off. No zip is written for it.
         */
        BuildResult baselineResult = build(store, request.withObjectBlockingDisabled());
        return new Phase2Baseline(
            compareDangerousDirections(request.liveFlagsFile, baselineResult),
            compareProofEdges(proofEdges, baselineResult.regions)
        );
    }

    private static BuildRequest parseRequest(
        String[] args,
        Path project,
        List<ProofEdge> proofEdges
    ) throws IOException
    {
        Path outputZip = project.resolve(DEFAULT_ZIP);
        int selectorStart = 0;
        if (args.length > 0 && !isBuildOptionArg(args[0]))
        {
            outputZip = resolve(project, args[0]);
            selectorStart = 1;
        }

        Path liveFlagsFile = defaultLiveFlagsFile();
        boolean phase2SolidObjectBlocking = true;
        boolean phase3RoofBlocking = true;
        int roofLocTypeMask = DEFAULT_ROOF_LOC_TYPE_MASK;
        Path mergeFrom = null;
        List<String> selectorArgs = new ArrayList<>();
        for (int i = selectorStart; i < args.length; i++)
        {
            String arg = args[i];
            if (DISABLE_PHASE2_SOLID_OBJECTS_ARG.equals(arg))
            {
                phase2SolidObjectBlocking = false;
                continue;
            }
            if (DISABLE_PHASE3_ROOF_BLOCKING_ARG.equals(arg))
            {
                phase3RoofBlocking = false;
                continue;
            }
            if (arg.startsWith(ROOF_LOC_TYPES_ARG + "="))
            {
                roofLocTypeMask = parseRoofLocTypeMask(
                    arg.substring((ROOF_LOC_TYPES_ARG + "=").length()));
                continue;
            }
            if (arg.startsWith(MERGE_FROM_ARG + "="))
            {
                String value = arg.substring((MERGE_FROM_ARG + "=").length());
                if (value.isEmpty())
                {
                    throw new IOException(MERGE_FROM_ARG + " requires a non-empty path");
                }
                mergeFrom = resolve(project, value);
                continue;
            }
            if (LIVE_FLAGS_ARG.equals(arg))
            {
                if (i + 1 >= args.length)
                {
                    throw new IOException(LIVE_FLAGS_ARG + " requires a path");
                }
                liveFlagsFile = resolve(project, args[++i]);
                continue;
            }
            if (arg.startsWith(LIVE_FLAGS_ARG + "="))
            {
                String value = arg.substring((LIVE_FLAGS_ARG + "=").length());
                if (value.isEmpty())
                {
                    throw new IOException(LIVE_FLAGS_ARG + " requires a non-empty path");
                }
                liveFlagsFile = resolve(project, value);
                continue;
            }
            selectorArgs.add(arg);
        }

        if (selectorArgs.isEmpty())
        {
            TreeSet<Integer> regions = defaultProofRegions(proofEdges);
            return new BuildRequest(
                outputZip, liveFlagsFile, false, regions, true, phase2SolidObjectBlocking,
                phase3RoofBlocking, roofLocTypeMask, mergeFrom);
        }

        String selector = joinRegionSelector(selectorArgs);
        if ("all".equalsIgnoreCase(selector.trim()))
        {
            return new BuildRequest(
                outputZip, liveFlagsFile, true, Collections.emptySet(), false, phase2SolidObjectBlocking,
                phase3RoofBlocking, roofLocTypeMask, mergeFrom);
        }

        TreeSet<Integer> regions = parseRegionIds(selector);
        if (regions.isEmpty())
        {
            throw new IOException("No region ids parsed from selector: " + selector);
        }
        return new BuildRequest(
            outputZip, liveFlagsFile, false, regions, false, phase2SolidObjectBlocking, phase3RoofBlocking,
            roofLocTypeMask, mergeFrom);
    }

    private static boolean isLiveFlagsArg(String arg)
    {
        return LIVE_FLAGS_ARG.equals(arg) || arg.startsWith(LIVE_FLAGS_ARG + "=");
    }

    private static boolean isBuildOptionArg(String arg)
    {
        return isLiveFlagsArg(arg)
            || DISABLE_PHASE2_SOLID_OBJECTS_ARG.equals(arg)
            || DISABLE_PHASE3_ROOF_BLOCKING_ARG.equals(arg)
            || arg.startsWith(ROOF_LOC_TYPES_ARG + "=")
            || arg.startsWith(MERGE_FROM_ARG + "=");
    }

    private static Path defaultLiveFlagsFile()
    {
        return Paths.get(System.getProperty("user.home"), ".runelite", "drews-live-flags.txt");
    }

    private static Path resolve(Path project, String value)
    {
        Path path = Paths.get(value);
        if (path.isAbsolute())
        {
            return path.normalize();
        }
        return project.resolve(path).normalize();
    }

    private static String joinRegionSelector(List<String> args)
    {
        StringBuilder selector = new StringBuilder();
        for (String arg : args)
        {
            if (selector.length() > 0)
            {
                selector.append(' ');
            }
            selector.append(arg);
        }
        return selector.toString();
    }

    private static TreeSet<Integer> parseRegionIds(String selector) throws IOException
    {
        TreeSet<Integer> regions = new TreeSet<>();
        for (String token : selector.split("[,\\s]+"))
        {
            if (token.isEmpty())
            {
                continue;
            }
            regions.add(parseRegionId(token));
        }
        return regions;
    }

    private static int parseRegionId(String token) throws IOException
    {
        int separator = token.indexOf('_');
        if (separator > 0 && separator < token.length() - 1)
        {
            int regionX = parseBoundedInt(token.substring(0, separator), "region x", MIN_REGION_X, MAX_REGION_X);
            int regionY = parseBoundedInt(token.substring(separator + 1), "region y", MIN_REGION_Y, MAX_REGION_Y);
            return regionId(regionX, regionY);
        }

        int regionId = parseBoundedInt(token, "region id", 0, 0xFFFF);
        int regionX = regionId >> 8;
        int regionY = regionId & 0xFF;
        if (regionX < MIN_REGION_X || regionX > MAX_REGION_X
            || regionY < MIN_REGION_Y || regionY > MAX_REGION_Y)
        {
            throw new IOException("Region id is outside 0..65535: " + token);
        }
        return regionId;
    }

    private static int parseBoundedInt(String value, String label, int min, int max) throws IOException
    {
        try
        {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max)
            {
                throw new IOException(label + " outside " + min + ".." + max + ": " + value);
            }
            return parsed;
        }
        catch (NumberFormatException e)
        {
            throw new IOException("Invalid " + label + ": " + value, e);
        }
    }

    private static TreeSet<Integer> defaultProofRegions(List<ProofEdge> proofEdges)
    {
        TreeSet<Integer> regions = new TreeSet<>();
        for (ProofEdge edge : proofEdges)
        {
            regions.add(edge.regionId());
        }
        return regions;
    }

    private static BuildResult build(Store store, BuildRequest request) throws IOException
    {
        Map<Integer, ObjectDefinition> objects = loadObjectDefinitions(store);
        RegionLoader loader = new RegionLoader(store, ZERO_KEYS);
        BuildStats stats = new BuildStats();
        stats.phase2SolidObjectBlockingEnabled = request.phase2SolidObjectBlocking;
        stats.phase3RoofBlockingEnabled = request.phase3RoofBlocking;
        stats.roofLocTypeMask = request.roofLocTypeMask;
        TreeMap<String, BuiltRegion> regions = new TreeMap<>();

        if (request.allRegions)
        {
            for (int regionX = MIN_REGION_X; regionX <= MAX_REGION_X; regionX++)
            {
                for (int regionY = MIN_REGION_Y; regionY <= MAX_REGION_Y; regionY++)
                {
                    loadAndBuild(loader, objects, request, regionId(regionX, regionY), true, stats, regions);
                }
            }
        }
        else
        {
            for (int regionId : request.regionIds)
            {
                loadAndBuild(loader, objects, request, regionId, false, stats, regions);
            }
        }

        if (!request.allRegions && regions.size() != request.regionIds.size())
        {
            throw new IOException("Only built " + regions.size() + " of " + request.regionIds.size()
                + " requested regions.");
        }

        applyDeferredNeighbourEdges(regions, stats);

        stats.totalRegionsBuilt = regions.size();
        for (BuiltRegion region : regions.values())
        {
            stats.totalEdgesMadePassable += region.countPassableEdges();
            stats.doorEdgesWritten += region.countDoorEdges();
        }
        return new BuildResult(regions, stats);
    }

    private static void applyDeferredNeighbourEdges(TreeMap<String, BuiltRegion> regions, BuildStats stats)
    {
        for (DeferredEdge deferred : stats.deferredNeighbourEdges)
        {
            StoredEdge edge = deferred.edge;
            BuiltRegion region = regions.get(regionNameFor(edge.x, edge.y));
            if (region == null || !region.bits.contains(edge.x, edge.y, edge.plane))
            {
                stats.outOfRegionNeighbourSkips++;
                continue;
            }

            region.bits.markStoredEdge(edge, deferred.openable);
            stats.outOfRegionNeighbourEdgesApplied++;
        }
    }

    private static String regionNameFor(int x, int y)
    {
        return Math.floorDiv(x, REGION_SIZE) + "_" + Math.floorDiv(y, REGION_SIZE);
    }

    private static void loadAndBuild(
        RegionLoader loader,
        Map<Integer, ObjectDefinition> objects,
        BuildRequest request,
        int regionId,
        boolean skipMissing,
        BuildStats stats,
        TreeMap<String, BuiltRegion> regions
    ) throws IOException
    {
        RegionSource source = loadRegionSource(loader, regionId);
        if (!source.present())
        {
            if (skipMissing)
            {
                return;
            }
            throw new IOException("Cache has no map or loc archive for region " + formatRegionId(regionId));
        }

        BuiltRegion region = buildRegion(source, objects, stats, request.phase2SolidObjectBlocking);
        regions.put(region.name, region);
    }

    private static RegionSource loadRegionSource(RegionLoader loader, int regionId) throws IOException
    {
        MapDefinition map = null;
        LocationsDefinition locations = null;

        try
        {
            map = loader.loadMapDef(regionId);
        }
        catch (Exception ignored)
        {
            map = null;
        }

        try
        {
            locations = loader.loadLocDef(regionId);
        }
        catch (Exception ignored)
        {
            locations = null;
        }

        return new RegionSource(regionId, map, locations);
    }

    private static BuiltRegion buildRegion(
        RegionSource source,
        Map<Integer, ObjectDefinition> objects,
        BuildStats stats,
        boolean phase2SolidObjectBlocking
    )
    {
        int regionX = source.regionId >> 8;
        int regionY = source.regionId & 0xFF;
        int minX = regionX * REGION_SIZE;
        int minY = regionY * REGION_SIZE;
        RegionBits bits = new RegionBits(minX, minY, minX + REGION_SIZE - 1, minY + REGION_SIZE - 1);

        if (source.map != null)
        {
            Region terrain = new Region(source.regionId);
            terrain.loadTerrain(source.map);
            applyTerrain(terrain, bits, stats);
        }

        if (source.locations != null && source.locations.getLocations() != null)
        {
            List<Location> locations = new ArrayList<>(source.locations.getLocations());
            locations.sort(Comparator
                .comparingInt((Location location) -> location.getPosition().getZ())
                .thenComparingInt(location -> location.getPosition().getX())
                .thenComparingInt(location -> location.getPosition().getY())
                .thenComparingInt(Location::getId)
                .thenComparingInt(Location::getType)
                .thenComparingInt(Location::getOrientation));

            for (Location location : locations)
            {
                applyLocation(
                    minX,
                    minY,
                    location,
                    objects.get(location.getId()),
                    bits,
                    stats,
                    phase2SolidObjectBlocking
                );
            }
        }

        bits.assertPassableDoorExclusion();
        return new BuiltRegion(regionX + "_" + regionY, source.regionId, bits);
    }

    private static void applyTerrain(Region terrain, RegionBits bits, BuildStats stats)
    {
        for (int plane = 0; plane < PLANE_COUNT; plane++)
        {
            for (int localX = 0; localX < REGION_SIZE; localX++)
            {
                for (int localY = 0; localY < REGION_SIZE; localY++)
                {
                    int tileSetting = terrain.getTileSetting(plane, localX, localY) & 0xFF;
                    if ((tileSetting & 1) == 0)
                    {
                        continue;
                    }

                    /*
                     * VERIFIED AGAINST LIVE CLIENT GROUND TRUTH on 2026-08-12. Measured over 49
                     * scenes / 192,061 usable observations / 26,962 client BLOCK_MOVEMENT_FLOOR
                     * tiles: bit 0 plus this bridge lowering scores precision 98.086%, recall
                     * 100.000% - zero false negatives. The bridge branch alone is not vacuous and
                     * not cosmetic: of 862 bridge-flagged blocked tiles it agrees with the client
                     * 859/862 = 99.65% with lowering versus 525/862 = 60.9% without, so removing
                     * it is a regression. Every alternative predicate measured (bit 2, bit 4, void
                     * tile, water overlay, and their unions with bit 0) buys no recall and costs
                     * precision. Do not "improve" this rule. See D-0169 in
                     * docs/C2_Guides/CHANGELOG_AGENT_NOTES.md and D-0134 in docs/C2_Guides/DECISION_LOG.md.
                     */
                    boolean bridge = (terrain.getTileSetting(1, localX, localY) & 2) != 0;
                    if (bridge)
                    {
                        stats.bridgeBranchTiles++;
                    }
                    int realPlane = bridge ? plane - 1 : plane;
                    if (realPlane >= 0)
                    {
                        stats.terrainBlockedTiles++;
                        bits.markSolidAllEdges(bits.minX + localX, bits.minY + localY, realPlane, stats);
                    }
                }
            }
        }
    }

    private static void applyLocation(
        int baseX,
        int baseY,
        Location location,
        ObjectDefinition def,
        RegionBits bits,
        BuildStats stats,
        boolean phase2SolidObjectBlocking
    )
    {
        stats.placementsByLocType.merge(location.getType(), 1L, Long::sum);

        int plane = location.getPosition().getZ();
        if (plane < 0 || plane >= PLANE_COUNT)
        {
            return;
        }

        int x = baseX + location.getPosition().getX();
        int y = baseY + location.getPosition().getY();
        long key = tileKey(x, y, plane);
        stats.placementTileKeys.add(key);
        recordIgnoredNonDecorDefinitionPlacement(stats, key, location.getType(), def);
        recordIgnoredPlacementTile(stats, x, y, plane, location.getType());
        if (location.getType() == 1 && location.getOrientation() == 3)
        {
            stats.locType1Orientation3TileKeys.add(key);
        }

        boolean openable = firstOpenStyleAction(def) != null;
        if (openable)
        {
            /*
             * This runs before shapeFor() so ignored locTypes are measured without changing any
             * edge-writing rule. Multiple open-style placements on the same tile keep the earliest
             * sorted locType, because the attribution needs a stable single cause.
             */
            recordDoorCapablePlacement(stats, x, y, plane, location.getType());
        }

        Direction[] shape = shapeFor(location.getType(), location.getOrientation(), stats);
        if (shape.length == 0)
        {
            applyIgnoredSolidObjectBlocking(
                location.getType(),
                def,
                openable,
                x,
                y,
                plane,
                bits,
                stats,
                phase2SolidObjectBlocking
            );
            return;
        }

        for (Direction direction : shape)
        {
            bits.markEdge(x, y, plane, direction, openable, stats);
        }
    }

    private static void applyIgnoredSolidObjectBlocking(
        int locType,
        ObjectDefinition def,
        boolean openable,
        int x,
        int y,
        int plane,
        RegionBits bits,
        BuildStats stats,
        boolean phase2SolidObjectBlocking
    )
    {
        if (stats.phase3RoofBlockingEnabled && isProvenBlockableRoofLocType(stats, locType))
        {
            /*
             * Phase 3. Blocked on locType alone, with no interactType and no footprint condition,
             * because locType alone is exactly what the proof pass measured. Adding an unmeasured
             * extra condition here would ship a rule other than the proven one. The anchor tile
             * only, matching the placement tile the proof pass counted - footprint expansion is a
             * separate, separately-provable change.
             */
            stats.phase3RoofPlacements++;
            if (plane >= 0 && plane < PLANE_COUNT)
            {
                stats.phase3RoofPlacementsByPlane[plane]++;
            }
            if (openable)
            {
                stats.phase3RoofOpenStylePlacements++;
            }
            bits.markSolidAllEdges(x, y, plane, stats);
            return;
        }
        if (!phase2SolidObjectBlocking)
        {
            return;
        }
        if (!shouldBlockIgnoredSolidObject(locType, def, stats))
        {
            return;
        }

        stats.phase2SolidObjectPlacements++;
        if (openable)
        {
            /*
             * Open/Close is not a door definition here; containers use it too. Count it so the
             * report keeps visibility into any open-style objects admitted by this phase.
             */
            stats.phase2SolidObjectOpenStylePlacements++;
        }
        bits.markSolidAllEdges(x, y, plane, stats);
    }

    private static boolean isProvenBlockableRoofLocType(BuildStats stats, int locType)
    {
        /*
         * Measured 2026-08-12 over the 62-region capture with phase 2 off, against NOT_ADJACENT
         * at 3.013% dangerousUnexplained and 1.074% overblock. Columns are dangerousUnexplained,
         * its ratio against NOT_ADJACENT, and the overblock ratio:
         *   12  85.4%  28.4x  0.19x        13  90.8%  30.1x  0.18x
         *   14  91.7%  30.4x  0.00x        16  86.9%  28.9x  0.07x
         *   17  91.5%  30.4x  0.00x        18  80.9%  26.9x  0.11x
         *   19  74.6%  24.8x  0.04x        21  69.8%  23.2x  0.19x
         * Every row clears 3.0x danger and sits far under the 1.5x overblock ceiling.
         *
         * locTypes 15 and 20 look identical to their neighbours (90.6% and 98.2%) but sit under
         * the 500 compared-edge floor at 384 and 488 edges, so they are held back until the
         * capture covers more ground rather than promoted on a short sample. locType 4 clears the
         * danger bar at 5.3x but it is a wall decoration and is much more likely to be standing
         * next to the wall that actually blocks; it needs its own pass. locTypes 5-8 are
         * inconclusive or vacuous.
         *
         * The set is a mask on BuildStats rather than a switch so that --roof-loctypes can add or
         * remove one locType and re-measure without a recompile.
         */
        if (locType < 0 || locType >= LOC_TYPE_MASK_BITS)
        {
            return false;
        }
        return (stats.roofLocTypeMask & (1 << locType)) != 0;
    }

    private static int parseRoofLocTypeMask(String value) throws IOException
    {
        int mask = 0;
        for (String token : value.split(","))
        {
            String trimmed = token.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            mask |= 1 << parseBoundedInt(trimmed, ROOF_LOC_TYPES_ARG, 0, LOC_TYPE_MASK_BITS - 1);
        }
        return mask;
    }

    private static String formatRoofLocTypes(int mask)
    {
        StringBuilder text = new StringBuilder();
        for (int locType = 0; locType < LOC_TYPE_MASK_BITS; locType++)
        {
            if ((mask & (1 << locType)) != 0)
            {
                if (text.length() > 0)
                {
                    text.append(',');
                }
                text.append(locType);
            }
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    private static boolean shouldBlockIgnoredSolidObject(
        int locType,
        ObjectDefinition def,
        BuildStats stats
    )
    {
        if (shapeForHandlesLocType(locType) || locType == GROUND_DECOR_LOC_TYPE)
        {
            return false;
        }
        /*
         * Phase 2 is deliberately limited to standard scenery/object placements. Other ignored
         * locTypes include roofs and structural encodings that are not safe to treat as full-tile
         * blockers without their own proof pass.
         */
        if (locType != 10 && locType != 11)
        {
            return false;
        }
        if (def == null)
        {
            stats.phase2SolidObjectMissingDefinitionSkipped++;
            return false;
        }
        if (def.getInteractType() == 0)
        {
            return false;
        }
        if (def.getSizeX() != 1 || def.getSizeY() != 1)
        {
            stats.phase2SolidObjectFootprintHeldBackPlacements++;
            return false;
        }
        return true;
    }

    private static void recordIgnoredNonDecorDefinitionPlacement(
        BuildStats stats,
        long key,
        int locType,
        ObjectDefinition def
    )
    {
        if (shapeForHandlesLocType(locType))
        {
            return;
        }
        if (locType == GROUND_DECOR_LOC_TYPE)
        {
            return;
        }

        stats.ignoredNonDecorPlacements++;
        if (def == null)
        {
            stats.ignoredNonDecorMissingDefinitionPlacements++;
            return;
        }

        int interactType = def.getInteractType();
        int sizeX = def.getSizeX();
        int sizeY = def.getSizeY();
        int blockingMask = def.getBlockingMask();
        boolean blocksProjectile = def.isBlocksProjectile();
        boolean obstructsGround = def.isObstructsGround();
        int wallOrDoor = def.getWallOrDoor();

        if (interactType != 0)
        {
            stats.interactTypeNonZeroTileKeys.add(key);
        }
        if (blocksProjectile)
        {
            stats.blocksProjectileTileKeys.add(key);
        }
        if (obstructsGround)
        {
            stats.obstructsGroundTileKeys.add(key);
        }

        if (interactType == 0)
        {
            stats.ignoredNonDecorInteractType0Placements++;
        }
        else if (interactType == 1)
        {
            stats.ignoredNonDecorInteractType1Placements++;
        }
        else if (interactType == 2)
        {
            stats.ignoredNonDecorInteractType2Placements++;
        }
        else
        {
            stats.ignoredNonDecorInteractTypeOtherPlacements++;
        }

        stats.ignoredNonDecorFootprintHistogram.merge(sizeX + "x" + sizeY, 1L, Long::sum);
        if (sizeX > 1 || sizeY > 1)
        {
            stats.ignoredNonDecorFootprintLargerThanOneByOnePlacements++;
        }
        if (blocksProjectile)
        {
            stats.ignoredNonDecorBlocksProjectilePlacements++;
        }
        if (obstructsGround)
        {
            stats.ignoredNonDecorObstructsGroundPlacements++;
        }
        if (wallOrDoor != 0)
        {
            stats.ignoredNonDecorWallOrDoorPlacements++;
        }
        if (blockingMask != 0)
        {
            stats.ignoredNonDecorBlockingMaskPlacements++;
        }
    }

    private static void recordIgnoredPlacementTile(BuildStats stats, int x, int y, int plane, int locType)
    {
        boolean ignored = !shapeForHandlesLocType(locType);
        if (!ignored)
        {
            return;
        }

        if (locType != GROUND_DECOR_LOC_TYPE && locType >= 0 && locType < LOC_TYPE_MASK_BITS)
        {
            /*
             * Proof-pass state for the per-locType table. A tile can carry several ignored
             * placements of different locTypes, so this is a bitmask rather than a single
             * attribution: a per-locType row is allowed to overlap another row rather than one
             * arbitrarily-first locType stealing the edge from the rest.
             */
            long maskKey = tileKey(x, y, plane);
            Integer previousMask = stats.ignoredLocTypeMaskByTile.get(maskKey);
            stats.ignoredLocTypeMaskByTile.put(
                maskKey,
                (previousMask == null ? 0 : previousMask.intValue()) | (1 << locType)
            );
        }

        if (locType == 10 || locType == 11)
        {
            stats.sceneryPlacementTileKeys.add(tileKey(x, y, plane));
            return;
        }

        if (locType == GROUND_DECOR_LOC_TYPE)
        {
            return;
        }

        stats.otherIgnoredTileKeys.add(tileKey(x, y, plane));
    }

    private static void recordDoorCapablePlacement(BuildStats stats, int x, int y, int plane, int locType)
    {
        Integer previous = stats.doorCapableLocTypeByTile.putIfAbsent(tileKey(x, y, plane), locType);
        if (previous != null)
        {
            stats.doorCapableTileCollisions++;
        }
    }

    private static Direction[] shapeFor(int locType, int orientation, BuildStats stats)
    {
        switch (locType)
        {
            case 0:
                return oneEdgeByOrientation(LOC_TYPE_0_EDGES_BY_ORIENTATION, orientation);
            case 1:
                return locType1EdgesByOrientation(orientation, stats);
            case 2:
                return twoEdgesByOrientation(orientation);
            case 3:
                return oneEdgeByOrientation(LOC_TYPE_3_EDGES_BY_ORIENTATION, orientation);
            case 9:
                return LOC_TYPE_9_EDGES;
            default:
                stats.ignoredLocTypePlacements++;
                return EmptyDirectionArray.HOLDER;
        }
    }

    private static boolean shapeForHandlesLocType(int locType)
    {
        /*
         * Deliberately mirrors the shapeFor() locType cases without calling shapeFor(), because the
         * report must not mutate BuildStats just to label a histogram row.
         */
        switch (locType)
        {
            case 0:
            case 1:
            case 2:
            case 3:
            case 9:
                return true;
            default:
                return false;
        }
    }

    private static Direction[] locType1EdgesByOrientation(int orientation, BuildStats stats)
    {
        if (orientation < 0 || orientation >= LOC_TYPE_1_EDGES_BY_ORIENTATION.length)
        {
            stats.locType1InvalidOrientationFallbacks++;
            stats.locType1EdgesBlockedTotal += LOC_TYPE_9_EDGES.length;
            return LOC_TYPE_9_EDGES;
        }

        stats.locType1PlacementsByOrientation[orientation]++;
        if (orientation == 3)
        {
            stats.locType1Orientation3Placements++;
        }

        Direction[] edges = LOC_TYPE_1_EDGES_BY_ORIENTATION[orientation];
        stats.locType1EdgesBlockedTotal += edges.length;
        return edges;
    }

    private static Direction[] oneEdgeByOrientation(Direction[] table, int orientation)
    {
        if (orientation < 0 || orientation >= table.length)
        {
            return EmptyDirectionArray.HOLDER;
        }
        return new Direction[]{table[orientation]};
    }

    private static Direction[] twoEdgesByOrientation(int orientation)
    {
        if (orientation < 0 || orientation >= LOC_TYPE_2_EDGES_BY_ORIENTATION.length)
        {
            return EmptyDirectionArray.HOLDER;
        }
        return LOC_TYPE_2_EDGES_BY_ORIENTATION[orientation];
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

    private static void writeZip(Path outputZip, TreeMap<String, BuiltRegion> regions) throws IOException
    {
        Path parent = outputZip.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputZip)))
        {
            for (BuiltRegion region : regions.values())
            {
                ZipEntry entry = new ZipEntry(region.name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(gzip(region.encode()));
                zip.closeEntry();
            }
        }
    }

    private static String mergeMissingRegions(Path outputZip, Path mergeFrom) throws IOException
    {
        if (mergeFrom == null)
        {
            return "";
        }
        if (!Files.isRegularFile(mergeFrom))
        {
            throw new IOException(MERGE_FROM_ARG + " file missing: " + mergeFrom);
        }

        /*
         * Runs AFTER verifyRoundTrip on purpose. That method throws on any entry it did not build,
         * so carried entries have to arrive once it has already passed on the built set. They are
         * copied as raw stored bytes and never decoded, then read back and compared byte for byte
         * - the build cannot vouch for their contents, only that it did not alter them.
         */
        TreeMap<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(outputZip)))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    continue;
                }
                entries.put(entry.getName(), readAll(zip));
            }
        }
        int builtCount = entries.size();

        TreeMap<String, byte[]> carried = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(mergeFrom)))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                if (entry.isDirectory() || entries.containsKey(entry.getName()))
                {
                    continue;
                }
                carried.put(entry.getName(), readAll(zip));
            }
        }
        if (carried.isEmpty())
        {
            return " MERGE 0 carried (built " + builtCount + " already covers " + mergeFrom.getFileName() + ")";
        }

        entries.putAll(carried);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputZip)))
        {
            for (String name : entries.keySet())
            {
                ZipEntry out = new ZipEntry(name);
                out.setTime(0L);
                zip.putNextEntry(out);
                zip.write(entries.get(name));
                zip.closeEntry();
            }
        }

        int verified = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(outputZip)))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                byte[] expected = carried.get(entry.getName());
                if (expected == null)
                {
                    continue;
                }
                byte[] actual = readAll(zip);
                if (actual.length != expected.length)
                {
                    throw new IOException("Carried region length changed during merge: " + entry.getName());
                }
                for (int i = 0; i < expected.length; i++)
                {
                    if (actual[i] != expected[i])
                    {
                        throw new IOException("Carried region bytes changed during merge: " + entry.getName());
                    }
                }
                verified++;
            }
        }
        if (verified != carried.size())
        {
            throw new IOException("Carried region count mismatch: wrote " + carried.size()
                + ", verified " + verified);
        }

        return " MERGE " + carried.size() + " regions carried byte-verified from "
            + mergeFrom.getFileName() + " (built " + builtCount + ", total " + entries.size() + ")";
    }

    private static byte[] gzip(byte[] bytes) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out))
        {
            gzip.write(bytes);
        }
        return out.toByteArray();
    }

    private static String verifyRoundTrip(Path outputZip, TreeMap<String, BuiltRegion> expected)
        throws IOException
    {
        TreeSet<String> seen = new TreeSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(outputZip)))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    continue;
                }

                BuiltRegion built = expected.get(entry.getName());
                if (built == null)
                {
                    throw new IOException("Unexpected zip entry during round trip: " + entry.getName());
                }
                seen.add(entry.getName());
                byte[] decoded = gunzip(readAll(zip));
                verifyRegionBytes(built, decoded);
            }
        }

        if (!seen.equals(expected.keySet()))
        {
            TreeSet<String> missing = new TreeSet<>(expected.keySet());
            missing.removeAll(seen);
            throw new IOException("Round trip missing entries: " + missing);
        }

        return "ROUND TRIP OK " + expected.size() + " regions";
    }

    private static byte[] readAll(InputStream in) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] bytes) throws IOException
    {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes)))
        {
            return readAll(gzip);
        }
    }

    private static void verifyRegionBytes(BuiltRegion expected, byte[] decoded) throws IOException
    {
        if (decoded.length < 16)
        {
            throw new IOException("Round trip entry too short for " + expected.name + ": " + decoded.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(decoded);
        int minX = buffer.getInt();
        int minY = buffer.getInt();
        int maxX = buffer.getInt();
        int maxY = buffer.getInt();
        RegionBits bits = expected.bits;
        if (minX != bits.minX || minY != bits.minY || maxX != bits.maxX || maxY != bits.maxY)
        {
            throw new IOException("Round trip header mismatch for " + expected.name);
        }

        byte[] bitBytes = new byte[buffer.remaining()];
        buffer.get(bitBytes);
        BitSet actual = BitSet.valueOf(bitBytes);

        for (int plane = 0; plane < PLANE_COUNT; plane++)
        {
            for (int y = bits.minY; y <= bits.maxY; y++)
            {
                for (int x = bits.minX; x <= bits.maxX; x++)
                {
                    int northIndex = archiveIndex(bits, x, y, plane, FLAG_NORTH_PASSABLE);
                    if (actual.get(northIndex)
                        != archivePassable(bits, x, y, plane, FLAG_NORTH_PASSABLE, FLAG_NORTH_DOOR))
                    {
                        throw new IOException("Round trip north-passable mismatch in " + expected.name
                            + " at " + x + "," + y + "," + plane);
                    }

                    int eastIndex = archiveIndex(bits, x, y, plane, FLAG_EAST_PASSABLE);
                    if (actual.get(eastIndex)
                        != archivePassable(bits, x, y, plane, FLAG_EAST_PASSABLE, FLAG_EAST_DOOR))
                    {
                        throw new IOException("Round trip east-passable mismatch in " + expected.name
                            + " at " + x + "," + y + "," + plane);
                    }
                }
            }
        }

        int unexpected = actual.nextSetBit(archiveTotalBits(bits));
        if (unexpected >= 0)
        {
            throw new IOException("Round trip found out-of-range bit " + unexpected + " in " + expected.name);
        }
    }

    private static int archiveTotalBits(RegionBits bits)
    {
        return PLANE_COUNT * bits.width * bits.height * ARCHIVE_FLAG_COUNT;
    }

    private static int archiveIndex(RegionBits bits, int x, int y, int plane, int flag)
    {
        return (plane * bits.width * bits.height + (y - bits.minY) * bits.width + (x - bits.minX))
            * ARCHIVE_FLAG_COUNT + flag;
    }

    private static Comparison compareProofEdges(
        List<ProofEdge> proofEdges,
        TreeMap<String, BuiltRegion> regions
    )
    {
        Map<Integer, BuiltRegion> byRegionId = new HashMap<>();
        for (BuiltRegion region : regions.values())
        {
            byRegionId.put(region.regionId, region);
        }

        Comparison comparison = new Comparison(proofEdges.size());
        for (ProofEdge edge : proofEdges)
        {
            BuiltRegion region = byRegionId.get(edge.regionId());
            if (region == null || !region.bits.contains(edge.x, edge.y, edge.plane))
            {
                comparison.outsideBuiltRegions++;
                continue;
            }

            if (region.bits.isPassable(edge.x, edge.y, edge.plane, edge.direction))
            {
                comparison.passableInV2++;
            }
            else if (region.bits.isDoor(edge.x, edge.y, edge.plane, edge.direction))
            {
                comparison.doorInV2++;
            }
            else
            {
                comparison.stillBlockedInV2++;
                if (comparison.stillBlockedExamples.size() < STILL_BLOCKED_EXAMPLE_LIMIT)
                {
                    comparison.stillBlockedExamples.add(edge.raw());
                }
            }
        }
        return comparison;
    }

    private static DangerousDirectionComparison compareDangerousDirections(
        Path liveFlagsFile,
        BuildResult result
    )
        throws IOException
    {
        if (!Files.exists(liveFlagsFile))
        {
            return DangerousDirectionComparison.skipped(
                liveFlagsFile,
                "DANGEROUS PASS SKIPPED - live capture file missing: " + liveFlagsFile
            );
        }
        if (!Files.isRegularFile(liveFlagsFile))
        {
            throw new IOException("Live flag capture is not a regular file: " + liveFlagsFile);
        }

        LiveCapture live = parseLiveCapture(liveFlagsFile);
        Map<Integer, BuiltRegion> byRegionId = new HashMap<>();
        for (BuiltRegion region : result.regions.values())
        {
            byRegionId.put(region.regionId, region);
        }

        DangerousDirectionComparison comparison = new DangerousDirectionComparison(liveFlagsFile, live);
        comparison.orient3TileCount = result.stats.locType1Orientation3TileKeys.size();
        for (LiveTile tile : live.tiles.values())
        {
            int regionId = regionIdForTile(tile.x, tile.y);
            BuiltRegion region = regionId < 0 ? null : byRegionId.get(regionId);
            if (region == null || !region.bits.contains(tile.x, tile.y, tile.plane))
            {
                comparison.outsideBuiltRegions++;
                continue;
            }

            boolean orient3 = result.stats.locType1Orientation3TileKeys.contains(tile.key());
            BorderDistances borderDistances = borderDistancesFor(live, tile);
            compareLiveEdge(
                comparison,
                result.stats,
                region,
                tile,
                'N',
                tile.northBlocked,
                orient3,
                borderDistances
            );
            compareLiveEdge(
                comparison,
                result.stats,
                region,
                tile,
                'E',
                tile.eastBlocked,
                orient3,
                borderDistances
            );
        }
        return comparison;
    }

    private static BorderDistances borderDistancesFor(LiveCapture live, LiveTile tile)
    {
        int minBorderDistance = Integer.MAX_VALUE;
        int maxBorderDistance = Integer.MIN_VALUE;
        for (LiveSceneBlockGeometry block : live.sceneBlockGeometries)
        {
            if (!block.contains(tile.x, tile.y, tile.plane))
            {
                continue;
            }

            int distance = block.borderDistance(tile.x, tile.y);
            minBorderDistance = Math.min(minBorderDistance, distance);
            maxBorderDistance = Math.max(maxBorderDistance, distance);
        }
        if (minBorderDistance == Integer.MAX_VALUE)
        {
            return BorderDistances.notContained();
        }
        return new BorderDistances(minBorderDistance, maxBorderDistance);
    }

    private static void compareLiveEdge(
        DangerousDirectionComparison comparison,
        BuildStats stats,
        BuiltRegion region,
        LiveTile tile,
        char direction,
        boolean liveBlocked,
        boolean orient3,
        BorderDistances borderDistances
    )
    {
        boolean passable = region.bits.isPassable(tile.x, tile.y, tile.plane, direction);
        boolean door = region.bits.isDoor(tile.x, tile.y, tile.plane, direction);
        boolean borderExcluded = borderDistances.contained
            && borderDistances.maxBorderDistance <= BORDER_MAX_DISTANCE;
        /*
         * archiveFlags writes a door edge PASSABLE, so the report has to read it the same way or it
         * measures a map nobody runs. Only the open side moves: a door the client says is OPEN was
         * being counted as an overblock and it is not one - the shipped archive lets you walk it.
         * The blocked side is deliberately untouched, so a door the client saw SHUT still lands in
         * DOOR_SHUT rather than inflating DANGEROUS with cases that are benign because the door
         * opens.
         */
        boolean archivePassable = passable || door;
        boolean dangerous = liveBlocked && passable;
        boolean overblock = !liveBlocked && !archivePassable;
        /*
         * The client says this edge is open and so do we. These are the edges a NEW blocking rule
         * would turn into overblocks, which is the one thing the per-locType table could not see:
         * its overblock column only counts blocking the current build already does, so a build
         * with the rule switched off reports zero cost for that rule by construction.
         */
        boolean agreeOpen = !liveBlocked && archivePassable;
        boolean dangerousUnexplained = dangerous
            && classifyDangerousEdge(comparison, stats, tile, direction) == DangerousSplit.UNEXPLAINED;

        if (borderExcluded)
        {
            comparison.borderExcludedEdges++;
            if (dangerous)
            {
                comparison.borderExcludedDangerous++;
            }
            if (dangerousUnexplained)
            {
                comparison.borderExcludedDangerousUnexplained++;
            }
            recordBorderHistogramEdge(comparison, borderDistances, dangerous, dangerousUnexplained);
            return;
        }

        comparison.comparedEdges++;
        if (orient3)
        {
            comparison.orient3ComparedEdges++;
        }

        if (liveBlocked)
        {
            if (passable)
            {
                comparison.dangerous++;
                if (orient3)
                {
                    comparison.dangerousOrient3++;
                }
                if (comparison.dangerousExamples.size() < DANGEROUS_EXAMPLE_LIMIT)
                {
                    comparison.dangerousExamples.add(
                        tile.x + "," + tile.y + "," + tile.plane
                            + " " + direction + " orient3=" + orient3
                    );
                }
                dangerousUnexplained = splitDangerousEdge(comparison, stats, tile, direction)
                    == DangerousSplit.UNEXPLAINED;
            }
            else if (door)
            {
                comparison.doorShut++;
            }
            else
            {
                comparison.agreeBlocked++;
            }
        }
        else if (archivePassable)
        {
            comparison.agreeOpen++;
        }
        else
        {
            comparison.overblock++;
            if (liveRawBlockedTile(tile))
            {
                comparison.overblockSourceTileBlockedRaw++;
            }
        }

        recordBorderHistogramEdge(comparison, borderDistances, dangerous, dangerousUnexplained);
        recordInteriorMeasurementEdge(comparison, stats, region, tile, dangerous, dangerousUnexplained);
        recordSceneryAdjacencyMeasurementEdge(
            comparison,
            stats,
            tile,
            direction,
            dangerous,
            dangerousUnexplained,
            overblock,
            agreeOpen
        );
    }

    private static boolean liveRawBlockedTile(LiveTile tile)
    {
        return tile.rawFlagsSeen && (tile.rawFlags & LIVE_BLOCKED_TILE_MASK) != 0;
    }

    private static void recordInteriorMeasurementEdge(
        DangerousDirectionComparison comparison,
        BuildStats stats,
        BuiltRegion region,
        LiveTile tile,
        boolean dangerous,
        boolean dangerousUnexplained
    )
    {
        InteriorBucket bucket = classifyInteriorBucket(stats, tile);
        boolean nearStructure = isNearStructure(stats, tile);
        comparison.interiorMeasurement.record(
            bucket,
            region.regionId,
            tile.plane,
            nearStructure,
            dangerous,
            dangerousUnexplained
        );
    }

    private static void recordSceneryAdjacencyMeasurementEdge(
        DangerousDirectionComparison comparison,
        BuildStats stats,
        LiveTile tile,
        char direction,
        boolean dangerous,
        boolean dangerousUnexplained,
        boolean overblock,
        boolean agreeOpen
    )
    {
        int otherX = tile.x;
        int otherY = tile.y;
        if (direction == 'N')
        {
            otherY = tile.y + 1;
        }
        else if (direction == 'E')
        {
            otherX = tile.x + 1;
        }
        else
        {
            throw new IllegalArgumentException("Unhandled adjacency direction " + direction);
        }

        SceneryAdjacencyBucket bucket = classifySceneryAdjacencyBucket(stats, tile, otherX, otherY);
        comparison.sceneryAdjacencyMeasurement.record(
            bucket,
            tile.plane,
            dangerous,
            dangerousUnexplained,
            overblock
        );
        comparison.ignoredLocTypeMeasurement.record(
            ignoredLocTypeMaskForEdge(stats, tile, otherX, otherY),
            dangerous,
            dangerousUnexplained,
            overblock,
            agreeOpen
        );
        recordIgnoredObjectDefinitionFlagMeasurements(
            comparison.sceneryAdjacencyMeasurement,
            stats,
            tile,
            otherX,
            otherY,
            bucket,
            dangerous,
            dangerousUnexplained,
            overblock
        );
    }

    private static SceneryAdjacencyBucket classifySceneryAdjacencyBucket(
        BuildStats stats,
        LiveTile tile,
        int otherX,
        int otherY
    )
    {
        /*
         * Stored north/east edges sit between two same-plane tiles. A placement on either endpoint
         * can explain the client blocking that edge, so the adjacency census checks both endpoints.
         */
        if (edgeTouchesTileKey(stats.sceneryPlacementTileKeys, tile, otherX, otherY))
        {
            return SceneryAdjacencyBucket.ADJ_SCENERY;
        }
        if (edgeTouchesTileKey(stats.otherIgnoredTileKeys, tile, otherX, otherY))
        {
            return SceneryAdjacencyBucket.ADJ_OTHER_IGNORED;
        }
        return SceneryAdjacencyBucket.NOT_ADJACENT;
    }

    private static void recordIgnoredObjectDefinitionFlagMeasurements(
        SceneryAdjacencyMeasurement measurement,
        BuildStats stats,
        LiveTile tile,
        int otherX,
        int otherY,
        SceneryAdjacencyBucket bucket,
        boolean dangerous,
        boolean dangerousUnexplained,
        boolean overblock
    )
    {
        if (bucket == SceneryAdjacencyBucket.NOT_ADJACENT)
        {
            return;
        }

        boolean interactTypeNonZero = edgeTouchesTileKey(
            stats.interactTypeNonZeroTileKeys,
            tile,
            otherX,
            otherY
        );
        if (interactTypeNonZero)
        {
            measurement.recordIgnoredSolidity(
                IgnoredSolidityBucket.ADJ_SOLID_FLAGGED,
                dangerous,
                dangerousUnexplained,
                overblock
            );
        }
        else
        {
            measurement.recordIgnoredSolidity(
                IgnoredSolidityBucket.ADJ_NONSOLID_ONLY,
                dangerous,
                dangerousUnexplained,
                overblock
            );
        }

        recordIgnoredObjectFlagMeasurement(
            measurement,
            IgnoredObjectFlag.INTERACT_TYPE_NONZERO,
            interactTypeNonZero,
            dangerous,
            dangerousUnexplained,
            overblock
        );
        recordIgnoredObjectFlagMeasurement(
            measurement,
            IgnoredObjectFlag.BLOCKS_PROJECTILE,
            edgeTouchesTileKey(stats.blocksProjectileTileKeys, tile, otherX, otherY),
            dangerous,
            dangerousUnexplained,
            overblock
        );
        recordIgnoredObjectFlagMeasurement(
            measurement,
            IgnoredObjectFlag.OBSTRUCTS_GROUND,
            edgeTouchesTileKey(stats.obstructsGroundTileKeys, tile, otherX, otherY),
            dangerous,
            dangerousUnexplained,
            overblock
        );
    }

    private static void recordIgnoredObjectFlagMeasurement(
        SceneryAdjacencyMeasurement measurement,
        IgnoredObjectFlag flag,
        boolean marked,
        boolean dangerous,
        boolean dangerousUnexplained,
        boolean overblock
    )
    {
        if (!marked)
        {
            return;
        }
        measurement.recordIgnoredObjectFlag(flag, dangerous, dangerousUnexplained, overblock);
    }

    private static int ignoredLocTypeMaskForEdge(
        BuildStats stats,
        LiveTile tile,
        int otherX,
        int otherY
    )
    {
        Integer here = stats.ignoredLocTypeMaskByTile.get(tile.key());
        Integer other = stats.ignoredLocTypeMaskByTile.get(tileKey(otherX, otherY, tile.plane));
        return (here == null ? 0 : here.intValue()) | (other == null ? 0 : other.intValue());
    }

    private static boolean edgeTouchesTileKey(Set<Long> tileKeys, LiveTile tile, int otherX, int otherY)
    {
        if (tileKeys.contains(tile.key()))
        {
            return true;
        }
        return tileKeys.contains(tileKey(otherX, otherY, tile.plane));
    }

    private static InteriorBucket classifyInteriorBucket(BuildStats stats, LiveTile tile)
    {
        if (tile.plane > 0)
        {
            return InteriorBucket.UPPER;
        }

        if (tile.plane == 0 && stats.placementTileKeys.contains(tileKey(tile.x, tile.y, 1)))
        {
            return InteriorBucket.UNDER_STRUCTURE;
        }

        return InteriorBucket.OUTDOOR;
    }

    private static boolean isNearStructure(BuildStats stats, LiveTile tile)
    {
        for (int dx = -NEAR_STRUCTURE_RADIUS; dx <= NEAR_STRUCTURE_RADIUS; dx++)
        {
            for (int dy = -NEAR_STRUCTURE_RADIUS; dy <= NEAR_STRUCTURE_RADIUS; dy++)
            {
                if (stats.placementTileKeys.contains(tileKey(tile.x + dx, tile.y + dy, tile.plane)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static DangerousSplit classifyDangerousEdge(
        DangerousDirectionComparison comparison,
        BuildStats stats,
        LiveTile tile,
        char direction
    )
    {
        long key = tile.key();
        Integer doorCapableLocType = stats.doorCapableLocTypeByTile.get(key);
        boolean conflicted = dangerousAxisConflicted(comparison.live, key, direction);
        if (doorCapableLocType != null)
        {
            return DangerousSplit.DOOR_CAPABLE;
        }

        if (conflicted)
        {
            return DangerousSplit.CONFLICTED;
        }

        return DangerousSplit.UNEXPLAINED;
    }

    private static DangerousSplit splitDangerousEdge(
        DangerousDirectionComparison comparison,
        BuildStats stats,
        LiveTile tile,
        char direction
    )
    {
        DangerousSplit split = classifyDangerousEdge(comparison, stats, tile, direction);
        long key = tile.key();
        Integer doorCapableLocType = stats.doorCapableLocTypeByTile.get(key);
        boolean conflicted = dangerousAxisConflicted(comparison.live, key, direction);
        if (split == DangerousSplit.DOOR_CAPABLE)
        {
            comparison.dangerousDoorCapable++;
            if (conflicted)
            {
                comparison.dangerousDoorCapableAndConflicted++;
            }
            comparison.dangerousDoorCapableByLocType.merge(doorCapableLocType, 1L, Long::sum);
            if (!shapeForHandlesLocType(doorCapableLocType))
            {
                comparison.dangerousDoorCapableIgnoredLocType++;
            }
            return split;
        }

        if (split == DangerousSplit.CONFLICTED)
        {
            comparison.dangerousConflicted++;
            return split;
        }

        comparison.dangerousUnexplained++;
        if (comparison.dangerousUnexplainedExamples.size() < DANGEROUS_UNEXPLAINED_EXAMPLE_LIMIT)
        {
            comparison.dangerousUnexplainedExamples.add(
                tile.x + "," + tile.y + "," + tile.plane + " " + direction
            );
        }
        return DangerousSplit.UNEXPLAINED;
    }

    private static void recordBorderHistogramEdge(
        DangerousDirectionComparison comparison,
        BorderDistances borderDistances,
        boolean dangerous,
        boolean dangerousUnexplained
    )
    {
        if (!borderDistances.contained)
        {
            comparison.noContainingBlockComparedEdges++;
            return;
        }
        if (borderDistances.minBorderDistance != borderDistances.maxBorderDistance)
        {
            comparison.disagreeingBorderDistanceComparedEdges++;
        }
        comparison.minBorderDistanceHistogram.record(
            borderDistances.minBorderDistance,
            dangerous,
            dangerousUnexplained
        );
        comparison.maxBorderDistanceHistogram.record(
            borderDistances.maxBorderDistance,
            dangerous,
            dangerousUnexplained
        );
    }

    private static boolean dangerousAxisConflicted(LiveCapture live, long key, char direction)
    {
        if (direction == 'N')
        {
            return live.conflictingNorthTileKeys.contains(key);
        }
        if (direction == 'E')
        {
            return live.conflictingEastTileKeys.contains(key);
        }
        throw new IllegalArgumentException("Unhandled dangerous direction " + direction);
    }

    private static String buildReport(
        File cacheDir,
        Path project,
        BuildRequest request,
        BuildResult result,
        String roundTrip,
        Comparison comparison,
        DangerousDirectionComparison dangerousComparison,
        Phase2Baseline phase2Baseline
    )
    {
        StringBuilder report = new StringBuilder();
        report.append("collision map v2 build").append('\n');
        report.append("cache: ").append(cacheDir).append('\n');
        report.append("project: ").append(project).append('\n');
        report.append("output zip: ").append(request.outputZip).append('\n');
        report.append("report: ").append(project.resolve(REPORT_FILE)).append('\n');
        report.append("region selector: ");
        if (request.allRegions)
        {
            report.append("all");
        }
        else if (request.defaultedRegions)
        {
            report.append("defaulted from proof file to ");
            appendRegionList(report, request.regionIds);
        }
        else
        {
            appendRegionList(report, request.regionIds);
        }
        report.append('\n');
        report.append("phase2 solid-object blocking: ")
            .append(request.phase2SolidObjectBlocking ? "enabled" : "disabled")
            .append('\n');
        report.append("merge source: ")
            .append(request.mergeFrom == null ? "none" : request.mergeFrom.toString())
            .append('\n');
        report.append("phase3 roof blocking (locTypes ")
            .append(formatRoofLocTypes(request.roofLocTypeMask))
            .append("): ")
            .append(request.phase3RoofBlocking ? "enabled" : "disabled")
            .append('\n');
        report.append("archive format: ")
            .append(ARCHIVE_FLAG_COUNT)
            .append(" runtime passability flags; door flags are report-only")
            .append('\n');
        report.append(roundTrip).append('\n');
        report.append('\n');
        appendProofComparison(report, comparison);
        report.append('\n');
        appendDangerousDirectionComparison(
            report,
            result.stats,
            dangerousComparison,
            comparison,
            phase2Baseline
        );
        report.append('\n');
        appendBuildStats(report, result.stats);
        return report.toString();
    }

    private static void appendProofComparison(StringBuilder report, Comparison comparison)
    {
        report.append("proof-edge comparison:").append('\n');
        report.append("  proof edges parsed: ").append(comparison.parsedEdges).append('\n');
        report.append("  passable in v2: ").append(comparison.passableInV2).append('\n');
        report.append("  door in v2: ").append(comparison.doorInV2).append('\n');
        report.append("  still blocked in v2: ").append(comparison.stillBlockedInV2).append('\n');
        report.append("  outside built regions: ").append(comparison.outsideBuiltRegions).append('\n');
        report.append("  percentage fixed: ")
            .append(percent(comparison.fixedEdges(), comparison.insideBuiltRegions()))
            .append('\n');
        report.append("  example still-blocked edges:").append('\n');
        if (comparison.stillBlockedExamples.isEmpty())
        {
            report.append("    (none)").append('\n');
        }
        else
        {
            for (String example : comparison.stillBlockedExamples)
            {
                report.append("    ").append(example).append('\n');
            }
        }
    }

    private static void appendDangerousDirectionComparison(
        StringBuilder report,
        BuildStats stats,
        DangerousDirectionComparison comparison,
        Comparison proofComparison,
        Phase2Baseline phase2Baseline
    )
    {
        report.append("dangerous-direction pass:").append('\n');
        report.append("  live capture: ").append(comparison.liveFlagsFile).append('\n');
        if (comparison.skipped)
        {
            report.append("  ").append(comparison.skipReason).append('\n');
            return;
        }

        appendBorderHistogramInterpretationRule(report);
        appendDangerousInterpretationRule(report, comparison);
        double dangerousRateAll = rate(comparison.dangerous, comparison.comparedEdges);
        double dangerousRateOrient3 = rate(comparison.dangerousOrient3, comparison.orient3ComparedEdges);
        report.append("  live scene blocks: ").append(comparison.live.sceneBlocks).append('\n');
        report.append("  live rows parsed: ").append(comparison.live.rowsParsed).append('\n');
        report.append("  live rows with raw flags: ")
            .append(comparison.live.rowsWithRawFlags).append('\n');
        report.append("  live rows without raw flags: ")
            .append(comparison.live.rowsWithoutRawFlags).append('\n');
        report.append("  raw flag coverage: ")
            .append(formatRateWithCounts(comparison.live.rowsWithRawFlags, comparison.live.rowsParsed)).append('\n');
        if (comparison.live.rowsWithRawFlags == 0)
        {
            report.append("  OLD FORMAT CAPTURE - no raw flags present").append('\n');
        }
        report.append("  live covered tile observations: ")
            .append(comparison.live.coveredTileObservations).append('\n');
        report.append("  live unique covered tiles: ").append(comparison.live.tiles.size()).append('\n');
        report.append("  live duplicate rows: ").append(comparison.live.duplicateRows).append('\n');
        report.append("  live duplicate row conflicts: ")
            .append(comparison.live.duplicateRowConflicts).append('\n');
        report.append("  live conflicting north observations: ")
            .append(comparison.live.conflictingNorthObservations).append('\n');
        report.append("  live conflicting north tiles: ")
            .append(comparison.live.conflictingNorthTileKeys.size()).append('\n');
        report.append("  live conflicting east observations: ")
            .append(comparison.live.conflictingEastObservations).append('\n');
        report.append("  live conflicting east tiles: ")
            .append(comparison.live.conflictingEastTileKeys.size()).append('\n');
        report.append("  border-ring exclusion: ACTIVE (maxBorderDistance 0..")
            .append(BORDER_MAX_DISTANCE)
            .append(" withheld from every count below)").append('\n');
        report.append("    borderExcludedEdges: ").append(comparison.borderExcludedEdges).append('\n');
        report.append("    borderExcludedDangerous: ")
            .append(comparison.borderExcludedDangerous).append('\n');
        report.append("    borderExcludedDangerousUnexplained: ")
            .append(comparison.borderExcludedDangerousUnexplained).append('\n');
        report.append("    these edges are scene-capture artifacts, not map defects; the histogram below still counts them").append('\n');
        report.append("  comparedEdges: ").append(comparison.comparedEdges).append('\n');
        report.append("  outsideBuiltRegions: ").append(comparison.outsideBuiltRegions).append('\n');
        report.append("  DANGEROUS: ").append(comparison.dangerous).append('\n');
        report.append("  DOOR_SHUT: ").append(comparison.doorShut).append('\n');
        report.append("  AGREE_BLOCKED: ").append(comparison.agreeBlocked).append('\n');
        report.append("  AGREE_OPEN: ").append(comparison.agreeOpen).append('\n');
        report.append("  OVERBLOCK: ").append(comparison.overblock).append('\n');
        report.append("  OVERBLOCK with source tile carrying live BLOCKED_TILE raw flag: ")
            .append(comparison.overblockSourceTileBlockedRaw);
        if (comparison.overblock != 0)
        {
            report.append(" (")
                .append(formatPercentOnly((double) comparison.overblockSourceTileBlockedRaw
                    / (double) comparison.overblock))
                .append(" of OVERBLOCK)");
        }
        report.append('\n');
        appendDangerousSplit(report, comparison);
        appendBorderHistograms(report, comparison);
        appendInteriorMeasurement(report, comparison);
        appendSceneryAdjacencyMeasurement(report, stats, comparison);
        appendPhase2Gate(report, stats, comparison, proofComparison, phase2Baseline);
        report.append("  dangerousRateAll: ").append(formatRate(dangerousRateAll)).append('\n');
        report.append("  orient-3 tile count: ").append(comparison.orient3TileCount).append('\n');
        report.append("  orient-3 compared-edge count: ")
            .append(comparison.orient3ComparedEdges).append('\n');
        report.append("  orient-3 dangerous edges: ").append(comparison.dangerousOrient3).append('\n');
        report.append("  dangerousRateOrient3: ")
            .append(formatRate(dangerousRateOrient3)).append('\n');
        report.append("  interpretation result: ")
            .append(dangerousInterpretation(comparison, dangerousRateAll, dangerousRateOrient3))
            .append('\n');
        report.append("  example DANGEROUS edges:").append('\n');
        if (comparison.dangerousExamples.isEmpty())
        {
            report.append("    (none)").append('\n');
        }
        else
        {
            for (String example : comparison.dangerousExamples)
            {
                report.append("    ").append(example).append('\n');
            }
        }
    }

    private static void appendPhase2Gate(
        StringBuilder report,
        BuildStats stats,
        DangerousDirectionComparison comparison,
        Comparison proofComparison,
        Phase2Baseline baseline
    )
    {
        if (!stats.phase2SolidObjectBlockingEnabled && !stats.phase3RoofBlockingEnabled)
        {
            report.append("  object-blocking gate: NOT APPLICABLE - phase 2 and phase 3 are both ")
                .append("disabled, so this run IS the baseline.").append('\n');
            return;
        }
        if (baseline == null || baseline.dangerous.skipped || comparison.skipped)
        {
            report.append("  Phase 2 route-aware solid-object gate: NOT APPLICABLE - no live ")
                .append("baseline was measured for this run.").append('\n');
            return;
        }

        long unexplainedDrop =
            baseline.dangerous.dangerousUnexplained - comparison.dangerousUnexplained;
        long overblockRise = comparison.overblock - baseline.dangerous.overblock;
        long routeAwareCurrent = comparison.overblock - comparison.overblockSourceTileBlockedRaw;
        long routeAwareBaseline =
            baseline.dangerous.overblock - baseline.dangerous.overblockSourceTileBlockedRaw;
        long routeAwareRise = routeAwareCurrent - routeAwareBaseline;
        double proofCurrent = rate(proofComparison.fixedEdges(), proofComparison.insideBuiltRegions());
        double proofBaseline = rate(baseline.proof.fixedEdges(), baseline.proof.insideBuiltRegions());
        boolean netOk = unexplainedDrop > Math.max(0L, routeAwareRise);
        boolean proofOk = proofCurrent >= proofBaseline;

        report.append("  object-blocking gate (phase2=")
            .append(stats.phase2SolidObjectBlockingEnabled ? "on" : "off")
            .append(" phase3=")
            .append(stats.phase3RoofBlockingEnabled ? "on" : "off")
            .append("): ")
            .append(netOk && proofOk ? "PASS" : "ABORT")
            .append(" (baseline measured live: same regions, same capture, phase 2 forced off)")
            .append('\n');
        report.append("    DANGEROUS_UNEXPLAINED: baseline ")
            .append(baseline.dangerous.dangerousUnexplained)
            .append(", current ").append(comparison.dangerousUnexplained)
            .append(", drop ").append(unexplainedDrop).append('\n');
        report.append("    OVERBLOCK: baseline ").append(baseline.dangerous.overblock)
            .append(", current ").append(comparison.overblock)
            .append(", rise ").append(overblockRise).append('\n');
        report.append("    route-aware OVERBLOCK: baseline ").append(routeAwareBaseline)
            .append(", current ").append(routeAwareCurrent)
            .append(" (OVERBLOCK minus source-tile live BLOCKED_TILE cases)")
            .append(", rise ").append(routeAwareRise).append('\n');
        report.append("    proof edges fixed: baseline ").append(formatRate(proofBaseline))
            .append(", current ").append(formatRate(proofCurrent))
            .append(" -> ").append(okFail(proofOk)).append('\n');
        report.append("    net criterion: DANGEROUS_UNEXPLAINED drop must be > max(0, route-aware ")
            .append("OVERBLOCK rise) -> ").append(okFail(netOk))
            .append(" (").append(unexplainedDrop)
            .append(" > ").append(Math.max(0L, routeAwareRise)).append(")").append('\n');
        report.append("    proof criterion: the proof-edge fixed rate must not fall below the ")
            .append("baseline. A phase that trades route-proven open edges back into blocked ")
            .append("ones is not an improvement whatever the aggregate counts do.").append('\n');
    }

    private static void appendBorderHistogramInterpretationRule(StringBuilder report)
    {
        report.append("  border histogram interpretation rule:").append('\n');
        report.append("    BORDER   = maxBorderDistance 0..2").append('\n');
        report.append("    INTERIOR = maxBorderDistance >= 20").append('\n');
        report.append("    - CONFIRMED (border artifact): rate(BORDER) >= 3x rate(INTERIOR) AND BORDER holds >= 40% of all").append('\n');
        report.append("      DANGEROUS_UNEXPLAINED. Then the comparison needs a margin on ALL FOUR sides and the 28k").append('\n');
        report.append("      headline is not a defect count.").append('\n');
        report.append("    - REFUTED: rate(BORDER) < 1.5x rate(INTERIOR). Then the border is not the cause and there are").append('\n');
        report.append("      genuinely tens of thousands of wrongly-passable edges, which is a serious map defect.").append('\n');
        report.append("    - INCONCLUSIVE: anything between 1.5x and 3x, or fewer than 500 compared edges in INTERIOR.").append('\n');
        report.append("      Print INCONCLUSIVE explicitly rather than picking a side.").append('\n');
    }

    private static void appendBorderHistograms(
        StringBuilder report,
        DangerousDirectionComparison comparison
    )
    {
        report.append("  border histogram verdict: ")
            .append(borderHistogramVerdict(comparison))
            .append('\n');
        report.append("  compared edges with NO containing block on their plane: ")
            .append(comparison.noContainingBlockComparedEdges).append('\n');
        if (comparison.noContainingBlockComparedEdges != 0)
        {
            report.append("  NO CONTAINING BLOCK ON PLANE - BORDER ATTRIBUTION IS BROKEN").append('\n');
        }
        report.append("  compared edges where minBorderDistance != maxBorderDistance: ")
            .append(comparison.disagreeingBorderDistanceComparedEdges).append('\n');
        appendBorderHistogram(report, "maxBorderDistance", comparison.maxBorderDistanceHistogram);
        appendBorderHistogram(report, "minBorderDistance", comparison.minBorderDistanceHistogram);
    }

    private static void appendInteriorMeasurement(
        StringBuilder report,
        DangerousDirectionComparison comparison
    )
    {
        InteriorMeasurement measurement = comparison.interiorMeasurement;
        long bucketComparedTotal = measurement.bucketComparedTotal();
        long bucketDangerousTotal = measurement.bucketDangerousTotal();
        long bucketDangerousUnexplainedTotal = measurement.bucketDangerousUnexplainedTotal();
        long planeComparedTotal = measurement.planeComparedTotal();

        report.append("  interior measurement pass:").append('\n');
        appendInteriorMeasurementInterpretationRule(report);
        report.append("    bucket comparedEdges DANGEROUS dangerousRate(DANGEROUS/comparedEdges) ")
            .append("DANGEROUS_UNEXPLAINED unexplainedShare(DANGEROUS_UNEXPLAINED/bucket-sum)").append('\n');
        for (InteriorBucket bucket : InteriorBucket.values())
        {
            InteriorBucketCounts counts = measurement.counts(bucket);
            appendInteriorCountsRow(report, "    ", bucket.name(), counts, bucketDangerousUnexplainedTotal);
            if (bucket == InteriorBucket.UNDER_STRUCTURE)
            {
                report.append("      caveat: UNDER_STRUCTURE includes bridge tiles and they are ")
                    .append("unquantified in this pass.")
                    .append('\n');
            }
        }

        appendInteriorMeasurementAssertions(
            report,
            comparison,
            bucketComparedTotal,
            bucketDangerousTotal,
            bucketDangerousUnexplainedTotal,
            planeComparedTotal
        );
        appendInteriorMeasurementVerdicts(report, measurement, bucketDangerousUnexplainedTotal);
        appendInteriorPlaneTable(report, measurement, bucketDangerousUnexplainedTotal);
        appendInteriorRegionPlaneTable(report, measurement, bucketDangerousUnexplainedTotal);
        appendUpperOccupancyMeasurement(report, measurement, bucketDangerousUnexplainedTotal);
        appendOccupancyCensus(report, measurement);
    }

    private static void appendInteriorMeasurementInterpretationRule(StringBuilder report)
    {
        report.append("    interior interpretation rule:").append('\n');
        report.append("      UPPER = plane > 0").append('\n');
        report.append("      UNDER_STRUCTURE = plane 0 with a placement tile directly above on plane 1").append('\n');
        report.append("      OUTDOOR = all remaining post-exclusion compared edges").append('\n');
        report.append("      INTERIOR = UPPER + UNDER_STRUCTURE").append('\n');
        report.append("      Reuses the border hypothesis thresholds deliberately: this hypothesis ")
            .append("must clear the same bar.")
            .append('\n');
        report.append("      Rate denominator is comparedEdges for that row; share denominator is the bucket-sum ")
            .append("DANGEROUS_UNEXPLAINED from UPPER + UNDER_STRUCTURE + OUTDOOR.").append('\n');
        report.append("      Sub-results compare each proxy against OUTDOOR with the same share ")
            .append("denominator.").append('\n');
        report.append("      - VACUOUS: INTERIOR comparedEdges < ")
            .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
            .append(" OR OUTDOOR comparedEdges < ")
            .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
            .append(" -> INCONCLUSIVE - VACUOUS.").append('\n');
        report.append("      - CONFIRMED (interiors): rate(INTERIOR) >= ")
            .append(BORDER_CONFIRMED_RATE_MULTIPLIER)
            .append("x rate(OUTDOOR) AND INTERIOR holds >= ")
            .append(formatRate(BORDER_CONFIRMED_UNEXPLAINED_SHARE))
            .append(" of post-exclusion DANGEROUS_UNEXPLAINED.").append('\n');
        report.append("      - REFUTED: rate(INTERIOR) < ")
            .append(BORDER_REFUTED_RATE_MULTIPLIER)
            .append("x rate(OUTDOOR).").append('\n');
        report.append("      - INCONCLUSIVE: anything else.").append('\n');
    }

    private static void appendInteriorCountsRow(
        StringBuilder report,
        String indent,
        String label,
        InteriorBucketCounts counts,
        long dangerousUnexplainedTotal
    )
    {
        report.append(indent).append(label)
            .append(' ').append(counts.comparedEdges)
            .append(' ').append(counts.dangerous)
            .append(' ').append(formatRateWithCounts(counts.dangerous, counts.comparedEdges))
            .append(' ').append(counts.dangerousUnexplained)
            .append(' ').append(formatRateWithCounts(counts.dangerousUnexplained, dangerousUnexplainedTotal))
            .append('\n');
    }

    private static void appendInteriorMeasurementAssertions(
        StringBuilder report,
        DangerousDirectionComparison comparison,
        long bucketComparedTotal,
        long bucketDangerousTotal,
        long bucketDangerousUnexplainedTotal,
        long planeComparedTotal
    )
    {
        InteriorMeasurement measurement = comparison.interiorMeasurement;
        report.append("    UPPER.comparedEdges + UNDER_STRUCTURE.comparedEdges + ")
            .append("OUTDOOR.comparedEdges == comparedEdges: ")
            .append(okFail(bucketComparedTotal == comparison.comparedEdges))
            .append(" (").append(measurement.counts(InteriorBucket.UPPER).comparedEdges)
            .append(" + ").append(measurement.counts(InteriorBucket.UNDER_STRUCTURE).comparedEdges)
            .append(" + ").append(measurement.counts(InteriorBucket.OUTDOOR).comparedEdges)
            .append(" == ").append(comparison.comparedEdges).append(")").append('\n');
        report.append("    UPPER.dangerous + UNDER_STRUCTURE.dangerous + OUTDOOR.dangerous == DANGEROUS: ")
            .append(okFail(bucketDangerousTotal == comparison.dangerous))
            .append(" (").append(measurement.counts(InteriorBucket.UPPER).dangerous)
            .append(" + ").append(measurement.counts(InteriorBucket.UNDER_STRUCTURE).dangerous)
            .append(" + ").append(measurement.counts(InteriorBucket.OUTDOOR).dangerous)
            .append(" == ").append(comparison.dangerous).append(")").append('\n');
        report.append("    UPPER.unexplained + UNDER_STRUCTURE.unexplained + OUTDOOR.unexplained ")
            .append("== DANGEROUS_UNEXPLAINED: ")
            .append(okFail(bucketDangerousUnexplainedTotal == comparison.dangerousUnexplained))
            .append(" (").append(measurement.counts(InteriorBucket.UPPER).dangerousUnexplained)
            .append(" + ").append(measurement.counts(InteriorBucket.UNDER_STRUCTURE).dangerousUnexplained)
            .append(" + ").append(measurement.counts(InteriorBucket.OUTDOOR).dangerousUnexplained)
            .append(" == ").append(comparison.dangerousUnexplained).append(")").append('\n');
        report.append("    sum over planes of comparedEdges == comparedEdges: ")
            .append(okFail(planeComparedTotal == comparison.comparedEdges))
            .append(" (").append(measurement.planeCounts[0].comparedEdges)
            .append(" + ").append(measurement.planeCounts[1].comparedEdges)
            .append(" + ").append(measurement.planeCounts[2].comparedEdges)
            .append(" + ").append(measurement.planeCounts[3].comparedEdges)
            .append(" == ").append(comparison.comparedEdges).append(")").append('\n');
    }

    private static void appendInteriorMeasurementVerdicts(
        StringBuilder report,
        InteriorMeasurement measurement,
        long dangerousUnexplainedTotal
    )
    {
        InteriorBucketCounts interior = new InteriorBucketCounts();
        interior.add(measurement.counts(InteriorBucket.UPPER));
        interior.add(measurement.counts(InteriorBucket.UNDER_STRUCTURE));
        InteriorBucketCounts outdoor = measurement.counts(InteriorBucket.OUTDOOR);

        report.append("    verdict combined INTERIOR (UPPER + UNDER_STRUCTURE): ")
            .append(interiorMeasurementVerdict("INTERIOR", interior, outdoor, dangerousUnexplainedTotal))
            .append('\n');
        report.append("    verdict sub-result UPPER: ")
            .append(interiorMeasurementVerdict(
                "UPPER",
                measurement.counts(InteriorBucket.UPPER),
                outdoor,
                dangerousUnexplainedTotal
            ))
            .append('\n');
        report.append("    verdict sub-result UNDER_STRUCTURE: ")
            .append(interiorMeasurementVerdict(
                "UNDER_STRUCTURE",
                measurement.counts(InteriorBucket.UNDER_STRUCTURE),
                outdoor,
                dangerousUnexplainedTotal
            ))
            .append('\n');
    }

    private static String interiorMeasurementVerdict(
        String candidateLabel,
        InteriorBucketCounts candidate,
        InteriorBucketCounts outdoor,
        long dangerousUnexplainedTotal
    )
    {
        /*
         * Reuse the border-hypothesis thresholds deliberately: this interior hypothesis must clear
         * exactly the same bar the border hypothesis had to clear.
         */
        if (candidate.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR
            || outdoor.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            return interiorMeasurementVacuousVerdict(candidateLabel, candidate, outdoor);
        }

        double candidateRate = rate(candidate.dangerous, candidate.comparedEdges);
        double outdoorRate = rate(outdoor.dangerous, outdoor.comparedEdges);
        double candidateUnexplainedShare = rate(candidate.dangerousUnexplained, dangerousUnexplainedTotal);
        String details = " - " + candidateLabel + " rate "
            + formatRateWithCounts(candidate.dangerous, candidate.comparedEdges)
            + ", OUTDOOR rate "
            + formatRateWithCounts(outdoor.dangerous, outdoor.comparedEdges)
            + ", unexplainedShare "
            + formatRateWithCounts(candidate.dangerousUnexplained, dangerousUnexplainedTotal);

        if (candidateRate >= outdoorRate * BORDER_CONFIRMED_RATE_MULTIPLIER
            && candidateUnexplainedShare >= BORDER_CONFIRMED_UNEXPLAINED_SHARE)
        {
            return interiorMeasurementConfirmedVerdict(candidateLabel) + details;
        }
        if (candidateRate < outdoorRate * BORDER_REFUTED_RATE_MULTIPLIER)
        {
            return "REFUTED" + details;
        }
        return "INCONCLUSIVE" + details;
    }

    private static String interiorMeasurementConfirmedVerdict(String candidateLabel)
    {
        if ("INTERIOR".equals(candidateLabel))
        {
            return "CONFIRMED (interiors)";
        }
        return "CONFIRMED (" + candidateLabel + ")";
    }

    private static String interiorMeasurementVacuousVerdict(
        String candidateLabel,
        InteriorBucketCounts candidate,
        InteriorBucketCounts outdoor
    )
    {
        StringBuilder verdict = new StringBuilder("INCONCLUSIVE - VACUOUS - ");
        boolean first = true;
        if (candidate.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            verdict.append(candidateLabel)
                .append(" comparedEdges ")
                .append(candidate.comparedEdges)
                .append(" < ")
                .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR);
            first = false;
        }
        if (outdoor.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            if (!first)
            {
                verdict.append("; ");
            }
            verdict.append("OUTDOOR comparedEdges ")
                .append(outdoor.comparedEdges)
                .append(" < ")
                .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR);
        }
        return verdict.toString();
    }

    private static void appendInteriorPlaneTable(
        StringBuilder report,
        InteriorMeasurement measurement,
        long dangerousUnexplainedTotal
    )
    {
        report.append("    per-plane interior measurement:").append('\n');
        report.append("      plane comparedEdges DANGEROUS dangerousRate(DANGEROUS/comparedEdges) ")
            .append("DANGEROUS_UNEXPLAINED unexplainedShare(DANGEROUS_UNEXPLAINED/bucket-sum)").append('\n');
        for (int plane = 0; plane < measurement.planeCounts.length; plane++)
        {
            appendInteriorCountsRow(
                report,
                "      ",
                Integer.toString(plane),
                measurement.planeCounts[plane],
                dangerousUnexplainedTotal
            );
        }
    }

    private static void appendInteriorRegionPlaneTable(
        StringBuilder report,
        InteriorMeasurement measurement,
        long dangerousUnexplainedTotal
    )
    {
        report.append("    per-region-per-plane interior measurement:").append('\n');
        if (measurement.regionPlaneCounts.isEmpty())
        {
            report.append("      (none)").append('\n');
            return;
        }

        List<Map.Entry<RegionPlaneKey, InteriorBucketCounts>> rows = new ArrayList<>(
            measurement.regionPlaneCounts.entrySet());
        int rowLimit = 40;
        if (rows.size() > rowLimit)
        {
            rows.sort((left, right) ->
            {
                int byCount = Long.compare(right.getValue().comparedEdges, left.getValue().comparedEdges);
                if (byCount != 0)
                {
                    return byCount;
                }
                return left.getKey().compareTo(right.getKey());
            });
        }

        report.append("      region plane comparedEdges DANGEROUS dangerousRate(DANGEROUS/comparedEdges) ")
            .append("DANGEROUS_UNEXPLAINED unexplainedShare(DANGEROUS_UNEXPLAINED/bucket-sum)").append('\n');
        int rowsToPrint = Math.min(rows.size(), rowLimit);
        for (int i = 0; i < rowsToPrint; i++)
        {
            Map.Entry<RegionPlaneKey, InteriorBucketCounts> row = rows.get(i);
            report.append("      ").append(formatRegionId(row.getKey().regionId))
                .append(' ').append(row.getKey().plane)
                .append(' ').append(row.getValue().comparedEdges)
                .append(' ').append(row.getValue().dangerous)
                .append(' ').append(formatRateWithCounts(row.getValue().dangerous, row.getValue().comparedEdges))
                .append(' ').append(row.getValue().dangerousUnexplained)
                .append(' ')
                .append(formatRateWithCounts(row.getValue().dangerousUnexplained, dangerousUnexplainedTotal))
                .append('\n');
        }
        if (rows.size() > rowLimit)
        {
            report.append("      suppressed ")
                .append(rows.size() - rowLimit)
                .append(" region-plane rows with fewer compared edges").append('\n');
        }
    }

    private static void appendUpperOccupancyMeasurement(
        StringBuilder report,
        InteriorMeasurement measurement,
        long dangerousUnexplainedTotal
    )
    {
        InteriorBucketCounts upperNearStructure = measurement.upperNearStructureCounts;
        InteriorBucketCounts upperOpen = measurement.upperOpenCounts;
        InteriorBucketCounts outdoor = measurement.counts(InteriorBucket.OUTDOOR);

        report.append("    upper-floor occupancy subdivision:").append('\n');
        appendUpperOccupancyInterpretationRule(report);
        report.append("      bucket comparedEdges DANGEROUS dangerousRate(DANGEROUS/comparedEdges) ")
            .append("DANGEROUS_UNEXPLAINED unexplainedShare(DANGEROUS_UNEXPLAINED/bucket-sum)").append('\n');
        appendInteriorCountsRow(
            report,
            "      ",
            "UPPER_NEAR_STRUCTURE",
            upperNearStructure,
            dangerousUnexplainedTotal
        );
        appendInteriorCountsRow(report, "      ", "UPPER_OPEN", upperOpen, dangerousUnexplainedTotal);
        report.append("      caveat: a large open interior (a hall wider than 3 tiles) has floor tiles ")
            .append("further than ")
            .append(NEAR_STRUCTURE_RADIUS)
            .append(" from any wall, so they land in UPPER_OPEN. That biases this test AGAINST ")
            .append("the occupied-upper-floor hypothesis.")
            .append('\n');
        appendUpperOccupancyAssertions(report, measurement);
        report.append("      verdict occupied upper floors: ")
            .append(upperOccupancyVerdict(upperNearStructure, outdoor, dangerousUnexplainedTotal))
            .append('\n');
        report.append("      secondary read (NOT the verdict) UPPER_NEAR_STRUCTURE vs UPPER_OPEN: ")
            .append(upperOccupancySecondaryRead(upperNearStructure, upperOpen))
            .append('\n');
    }

    private static void appendUpperOccupancyInterpretationRule(StringBuilder report)
    {
        report.append("      upper occupancy interpretation rule:").append('\n');
        report.append("        UPPER_NEAR_STRUCTURE = plane > 0 AND NEAR_STRUCTURE").append('\n');
        report.append("        UPPER_OPEN = plane > 0 AND NOT NEAR_STRUCTURE").append('\n');
        report.append("        NEAR_STRUCTURE = any placement tile in the same-plane 3x3 block ")
            .append("around the compared edge tile.").append('\n');
        report.append("        Reuses the border and interior hypothesis thresholds deliberately: ")
            .append("this third hypothesis must clear the same bar.")
            .append('\n');
        report.append("        Rate denominator is comparedEdges for that row; share denominator is ")
            .append("the bucket-sum DANGEROUS_UNEXPLAINED from UPPER + UNDER_STRUCTURE + OUTDOOR.")
            .append('\n');
        report.append("        Verdict compares UPPER_NEAR_STRUCTURE against OUTDOOR.").append('\n');
        report.append("        - VACUOUS: UPPER_NEAR_STRUCTURE comparedEdges < ")
            .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
            .append(" OR OUTDOOR comparedEdges < ")
            .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
            .append(" -> INCONCLUSIVE - VACUOUS.").append('\n');
        report.append("        - CONFIRMED (occupied upper floors): rate(UPPER_NEAR_STRUCTURE) >= ")
            .append(BORDER_CONFIRMED_RATE_MULTIPLIER)
            .append("x rate(OUTDOOR) AND UPPER_NEAR_STRUCTURE holds >= ")
            .append(formatRate(BORDER_CONFIRMED_UNEXPLAINED_SHARE))
            .append(" of post-exclusion DANGEROUS_UNEXPLAINED.").append('\n');
        report.append("        - REFUTED: rate(UPPER_NEAR_STRUCTURE) < ")
            .append(BORDER_REFUTED_RATE_MULTIPLIER)
            .append("x rate(OUTDOOR).").append('\n');
        report.append("        - INCONCLUSIVE: anything else.").append('\n');
    }

    private static void appendUpperOccupancyAssertions(
        StringBuilder report,
        InteriorMeasurement measurement
    )
    {
        InteriorBucketCounts upper = measurement.counts(InteriorBucket.UPPER);
        InteriorBucketCounts upperNearStructure = measurement.upperNearStructureCounts;
        InteriorBucketCounts upperOpen = measurement.upperOpenCounts;
        long comparedTotal = upperNearStructure.comparedEdges + upperOpen.comparedEdges;
        long dangerousTotal = upperNearStructure.dangerous + upperOpen.dangerous;
        long unexplainedTotal = upperNearStructure.dangerousUnexplained + upperOpen.dangerousUnexplained;

        report.append("      UPPER occupancy split assertion: UPPER_NEAR_STRUCTURE.comparedEdges + ")
            .append("UPPER_OPEN.comparedEdges == UPPER.comparedEdges: ")
            .append(okFail(comparedTotal == upper.comparedEdges))
            .append(" (").append(upperNearStructure.comparedEdges)
            .append(" + ").append(upperOpen.comparedEdges)
            .append(" == ").append(upper.comparedEdges).append(")").append('\n');
        report.append("      UPPER occupancy split assertion: UPPER_NEAR_STRUCTURE.dangerous + ")
            .append("UPPER_OPEN.dangerous == UPPER.dangerous: ")
            .append(okFail(dangerousTotal == upper.dangerous))
            .append(" (").append(upperNearStructure.dangerous)
            .append(" + ").append(upperOpen.dangerous)
            .append(" == ").append(upper.dangerous).append(")").append('\n');
        report.append("      UPPER occupancy split assertion: UPPER_NEAR_STRUCTURE.unexplained + ")
            .append("UPPER_OPEN.unexplained == UPPER.unexplained: ")
            .append(okFail(unexplainedTotal == upper.dangerousUnexplained))
            .append(" (").append(upperNearStructure.dangerousUnexplained)
            .append(" + ").append(upperOpen.dangerousUnexplained)
            .append(" == ").append(upper.dangerousUnexplained).append(")").append('\n');
    }

    private static String upperOccupancyVerdict(
        InteriorBucketCounts upperNearStructure,
        InteriorBucketCounts outdoor,
        long dangerousUnexplainedTotal
    )
    {
        /*
         * Reuse the border and interior hypothesis thresholds deliberately: this occupied-upper-floor
         * hypothesis must clear exactly the same bar, with no new thresholds.
         */
        if (upperNearStructure.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR
            || outdoor.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            return interiorMeasurementVacuousVerdict("UPPER_NEAR_STRUCTURE", upperNearStructure, outdoor);
        }

        double upperNearStructureRate = rate(upperNearStructure.dangerous, upperNearStructure.comparedEdges);
        double outdoorRate = rate(outdoor.dangerous, outdoor.comparedEdges);
        double upperNearStructureUnexplainedShare = rate(
            upperNearStructure.dangerousUnexplained,
            dangerousUnexplainedTotal
        );
        String details = " - UPPER_NEAR_STRUCTURE rate "
            + formatRateWithCounts(upperNearStructure.dangerous, upperNearStructure.comparedEdges)
            + ", OUTDOOR rate "
            + formatRateWithCounts(outdoor.dangerous, outdoor.comparedEdges)
            + ", unexplainedShare "
            + formatRateWithCounts(upperNearStructure.dangerousUnexplained, dangerousUnexplainedTotal);

        if (upperNearStructureRate >= outdoorRate * BORDER_CONFIRMED_RATE_MULTIPLIER
            && upperNearStructureUnexplainedShare >= BORDER_CONFIRMED_UNEXPLAINED_SHARE)
        {
            return "CONFIRMED (occupied upper floors)" + details;
        }
        if (upperNearStructureRate < outdoorRate * BORDER_REFUTED_RATE_MULTIPLIER)
        {
            return "REFUTED" + details;
        }
        return "INCONCLUSIVE" + details;
    }

    private static String upperOccupancySecondaryRead(
        InteriorBucketCounts upperNearStructure,
        InteriorBucketCounts upperOpen
    )
    {
        double upperNearStructureRate = rate(upperNearStructure.dangerous, upperNearStructure.comparedEdges);
        double upperOpenRate = rate(upperOpen.dangerous, upperOpen.comparedEdges);
        String details = "UPPER_NEAR_STRUCTURE rate "
            + formatRateWithCounts(upperNearStructure.dangerous, upperNearStructure.comparedEdges)
            + ", UPPER_OPEN rate "
            + formatRateWithCounts(upperOpen.dangerous, upperOpen.comparedEdges);

        if (upperOpenRate == 0.0)
        {
            return "dangerousRate ratio undefined - " + details;
        }
        return "dangerousRate ratio "
            + formatMultiplier(upperNearStructureRate / upperOpenRate)
            + " - "
            + details;
    }

    private static void appendOccupancyCensus(
        StringBuilder report,
        InteriorMeasurement measurement
    )
    {
        report.append("    occupancy census (post-exclusion compared edges):").append('\n');
        report.append("      NEAR_STRUCTURE = any placement tile in the same-plane 3x3 block ")
            .append("around the compared edge tile.").append('\n');
        report.append("      This census tests whether upper planes are mostly empty sky; it does ")
            .append("not decide the occupied-upper-floor verdict.")
            .append('\n');
        report.append("      plane comparedEdges NEAR_STRUCTURE notNEAR_STRUCTURE ")
            .append("nearStructureRate(NEAR_STRUCTURE/comparedEdges)").append('\n');
        for (int plane = 0; plane < measurement.occupancyPlaneCounts.length; plane++)
        {
            appendOccupancyCensusRow(
                report,
                "      ",
                Integer.toString(plane),
                measurement.occupancyPlaneCounts[plane]
            );
        }
        appendOccupancyCensusAssertions(report, measurement);

        report.append("      region ").append(formatRegionId(OCCUPANCY_CENSUS_REGION_ID))
            .append(" occupancy census:").append('\n');
        report.append("      region plane comparedEdges NEAR_STRUCTURE notNEAR_STRUCTURE ")
            .append("nearStructureRate(NEAR_STRUCTURE/comparedEdges)").append('\n');
        for (int plane = 0; plane < measurement.occupancyCensusRegionPlaneCounts.length; plane++)
        {
            appendOccupancyCensusRow(
                report,
                "      ",
                formatRegionId(OCCUPANCY_CENSUS_REGION_ID) + " " + plane,
                measurement.occupancyCensusRegionPlaneCounts[plane]
            );
        }
    }

    private static void appendOccupancyCensusRow(
        StringBuilder report,
        String indent,
        String label,
        OccupancyCensusCounts counts
    )
    {
        report.append(indent).append(label)
            .append(' ').append(counts.comparedEdges)
            .append(' ').append(counts.nearStructureEdges)
            .append(' ').append(counts.notNearStructureEdges)
            .append(' ').append(formatRateWithCounts(counts.nearStructureEdges, counts.comparedEdges))
            .append('\n');
    }

    private static void appendOccupancyCensusAssertions(
        StringBuilder report,
        InteriorMeasurement measurement
    )
    {
        for (int plane = 0; plane < measurement.occupancyPlaneCounts.length; plane++)
        {
            OccupancyCensusCounts counts = measurement.occupancyPlaneCounts[plane];
            long splitTotal = counts.nearStructureEdges + counts.notNearStructureEdges;
            report.append("      plane ").append(plane).append(" occupancy census assertion: ")
                .append("NEAR_STRUCTURE + notNEAR_STRUCTURE == comparedEdges: ")
                .append(okFail(splitTotal == counts.comparedEdges))
                .append(" (").append(counts.nearStructureEdges)
                .append(" + ").append(counts.notNearStructureEdges)
                .append(" == ").append(counts.comparedEdges).append(")").append('\n');
        }
    }

    private static void appendSceneryAdjacencyMeasurement(
        StringBuilder report,
        BuildStats stats,
        DangerousDirectionComparison comparison
    )
    {
        SceneryAdjacencyMeasurement measurement = comparison.sceneryAdjacencyMeasurement;
        long bucketComparedTotal = measurement.bucketComparedTotal();
        long bucketDangerousTotal = measurement.bucketDangerousTotal();
        long bucketDangerousUnexplainedTotal = measurement.bucketDangerousUnexplainedTotal();
        long bucketOverblockTotal = measurement.bucketOverblockTotal();

        report.append("  ignored-placement adjacency pass:").append('\n');
        appendSceneryAdjacencyInterpretationRule(report);
        report.append("    bucket comparedEdges DANGEROUS dangerousRate(DANGEROUS/comparedEdges) ")
            .append("DANGEROUS_UNEXPLAINED dangerousUnexplainedRate(DANGEROUS_UNEXPLAINED/comparedEdges) ")
            .append("unexplainedShare(DANGEROUS_UNEXPLAINED/bucket-sum) OVERBLOCK ")
            .append("overblockRate(OVERBLOCK/comparedEdges)").append('\n');
        for (SceneryAdjacencyBucket bucket : SceneryAdjacencyBucket.values())
        {
            appendSceneryAdjacencyCountsRow(
                report,
                "    ",
                bucket.name(),
                measurement.counts(bucket),
                bucketDangerousUnexplainedTotal
            );
        }
        appendSceneryAdjacencyCountsRow(
            report,
            "    ",
            "ADJ_IGNORED",
            combinedSceneryAdjacencyCounts(measurement),
            bucketDangerousUnexplainedTotal
        );

        appendSceneryAdjacencyAssertions(
            report,
            comparison,
            bucketComparedTotal,
            bucketDangerousTotal,
            bucketDangerousUnexplainedTotal,
            bucketOverblockTotal
        );
        appendSceneryAdjacencyClosureAssertions(report, measurement);
        appendSceneryAdjacencyVerdicts(report, stats, measurement, bucketDangerousUnexplainedTotal);
        appendSceneryAdjacencyOverblockControl(report, stats, measurement);
        appendSceneryAdjacencyCensus(report, stats, measurement);
        appendIgnoredLocTypeProofPass(report, stats, comparison);
        report.append("    caveat: adjacency does not prove causation. A tile next to a tree is also ")
            .append("a tile in a cluttered part of the world, and clutter correlates with lots of ")
            .append("things. This test can rule the theory OUT cheaply; it cannot on its own prove ")
            .append("the objects are what block those edges.")
            .append('\n');
    }

    private static void appendIgnoredLocTypeProofPass(
        StringBuilder report,
        BuildStats stats,
        DangerousDirectionComparison comparison
    )
    {
        IgnoredLocTypeMeasurement measurement = comparison.ignoredLocTypeMeasurement;
        SceneryAdjacencyBucketCounts notAdjacent = measurement.notAdjacent;
        double notAdjacentUnexplainedRate =
            rate(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges);
        double notAdjacentOverblockRate = rate(notAdjacent.overblock, notAdjacent.comparedEdges);

        report.append("    per-locType ignored-placement proof pass:").append('\n');
        report.append("      PREDICTION (stated before the run): phase 2 blocks only locTypes 10 ")
            .append("and 11 and excludes every other ignored locType as unsafe without its own ")
            .append("proof pass. If that exclusion is correct, no excluded locType clears the ")
            .append("same bar 10 and 11 clear. If some excluded locType does clear it, the ")
            .append("exclusion list is itself the defect.")
            .append('\n');
        report.append("      Rows are NOT mutually exclusive. A tile can carry several ignored ")
            .append("placements of different locTypes, and each row counts every edge touching ")
            .append("one of its own placements, so rows overlap and do not sum to comparedEdges. ")
            .append("The alternative - assigning each tile one arbitrary locType - would let one ")
            .append("locType silently steal another's edges.")
            .append('\n');
        report.append("      Ground decoration locType ").append(GROUND_DECOR_LOC_TYPE)
            .append(" is excluded from the mask, matching the adjacency pass above.").append('\n');
        report.append("      Thresholds are the existing ones, reused deliberately - no new ")
            .append("threshold is introduced.")
            .append('\n');
        report.append("      - INCONCLUSIVE-VACUOUS: comparedEdges < ")
            .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR).append('\n');
        report.append("      - REFUTED: dangerousUnexplainedRate < ")
            .append(BORDER_REFUTED_RATE_MULTIPLIER).append("x NOT_ADJACENT.").append('\n');
        report.append("      - BLOCKABLE: dangerousUnexplainedRate >= ")
            .append(BORDER_CONFIRMED_RATE_MULTIPLIER)
            .append("x NOT_ADJACENT AND overblockRate <= ")
            .append(BORDER_REFUTED_RATE_MULTIPLIER)
            .append("x NOT_ADJACENT.")
            .append('\n');
        report.append("      - UNSAFE-OVERBLOCK: clears the danger bar but its overblockRate is ")
            .append("already above the overblock bar. Blocking there would deepen the ")
            .append("sealed-building failure and needs its own pass first.")
            .append('\n');
        report.append("      - UNSAFE-COST: clears the danger bar but does not pay for itself. ")
            .append("projectedNewOverblock is the AGREE_OPEN count on this locType's tiles - ")
            .append("edges the client says are open and this build also says are open, every one ")
            .append("of which becomes an overblock the moment the locType is blocked. ")
            .append("benefitPerNewOverblock = DANGEROUS_UNEXPLAINED / projectedNewOverblock; ")
            .append("below ")
            .append(BORDER_CONFIRMED_RATE_MULTIPLIER)
            .append(" the locType is UNSAFE-COST.")
            .append('\n');
        report.append("      The overblockRate column measures what THIS build already overblocks, ")
            .append("so on a run with the rule switched off it reads zero for that rule by ")
            .append("construction. projectedNewOverblock is the column that can see the cost of a ")
            .append("rule that is not enabled yet, and it is the one the verdict uses. Added ")
            .append("2026-08-12 after the earlier set was chosen on the blind column.")
            .append('\n');
        report.append("      - INCONCLUSIVE: anything else.").append('\n');
        if (stats.phase2SolidObjectBlockingEnabled)
        {
            report.append("      WARNING: phase 2 is ENABLED, so the overblock column for ")
                .append("locTypes 10 and 11 counts edges this build wrote itself. Read this ")
                .append("table from a run with ")
                .append(DISABLE_PHASE2_SOLID_OBJECTS_ARG)
                .append(".")
                .append('\n');
        }
        report.append("      baseline NOT_ADJACENT comparedEdges ").append(notAdjacent.comparedEdges)
            .append(" dangerousUnexplainedRate ")
            .append(formatRateWithCounts(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges))
            .append(" overblockRate ")
            .append(formatRateWithCounts(notAdjacent.overblock, notAdjacent.comparedEdges))
            .append('\n');
        report.append("      locType phase2Handled comparedEdges DANGEROUS ")
            .append("dangerousUnexplainedRate unexplainedRatio overblockRate overblockRatio ")
            .append("projectedNewOverblock benefitPerNewOverblock verdict")
            .append('\n');
        for (int locType = 0; locType < LOC_TYPE_MASK_BITS; locType++)
        {
            SceneryAdjacencyBucketCounts counts = measurement.counts(locType);
            if (counts.comparedEdges == 0)
            {
                continue;
            }

            double unexplainedRate = rate(counts.dangerousUnexplained, counts.comparedEdges);
            double overblockRate = rate(counts.overblock, counts.comparedEdges);
            report.append("      ").append(locType)
                .append(' ').append(locType == 10 || locType == 11 ? "yes" : "no")
                .append(' ').append(counts.comparedEdges)
                .append(' ').append(counts.dangerous)
                .append(' ')
                .append(formatRateWithCounts(counts.dangerousUnexplained, counts.comparedEdges))
                .append(' ')
                .append(sceneryAdjacencyRateRatio(unexplainedRate, notAdjacentUnexplainedRate))
                .append(' ').append(formatRateWithCounts(counts.overblock, counts.comparedEdges))
                .append(' ').append(sceneryAdjacencyRateRatio(overblockRate, notAdjacentOverblockRate))
                .append(' ').append(counts.agreeOpen)
                .append(' ').append(formatBenefitPerNewOverblock(counts))
                .append(' ').append(ignoredLocTypeVerdict(
                    counts,
                    unexplainedRate,
                    overblockRate,
                    notAdjacentUnexplainedRate,
                    notAdjacentOverblockRate
                ))
                .append('\n');
        }
        report.append("      tiles carrying more than one ignored locType: ")
            .append(multiLocTypeTileCount(stats))
            .append(" of ").append(stats.ignoredLocTypeMaskByTile.size())
            .append(" masked tiles").append('\n');
    }

    private static String ignoredLocTypeVerdict(
        SceneryAdjacencyBucketCounts counts,
        double unexplainedRate,
        double overblockRate,
        double notAdjacentUnexplainedRate,
        double notAdjacentOverblockRate
    )
    {
        if (counts.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            return "INCONCLUSIVE-VACUOUS";
        }
        if (unexplainedRate < notAdjacentUnexplainedRate * BORDER_REFUTED_RATE_MULTIPLIER)
        {
            return "REFUTED";
        }
        if (unexplainedRate < notAdjacentUnexplainedRate * BORDER_CONFIRMED_RATE_MULTIPLIER)
        {
            return "INCONCLUSIVE";
        }
        if (overblockRate > notAdjacentOverblockRate * BORDER_REFUTED_RATE_MULTIPLIER)
        {
            return "UNSAFE-OVERBLOCK";
        }
        if (counts.agreeOpen > 0
            && (double) counts.dangerousUnexplained / (double) counts.agreeOpen
                < BORDER_CONFIRMED_RATE_MULTIPLIER)
        {
            return "UNSAFE-COST";
        }
        return "BLOCKABLE";
    }

    private static String formatBenefitPerNewOverblock(SceneryAdjacencyBucketCounts counts)
    {
        if (counts.agreeOpen == 0)
        {
            return "no-cost";
        }
        return formatMultiplier((double) counts.dangerousUnexplained / (double) counts.agreeOpen);
    }

    private static long multiLocTypeTileCount(BuildStats stats)
    {
        long count = 0;
        for (Integer mask : stats.ignoredLocTypeMaskByTile.values())
        {
            if (Integer.bitCount(mask) > 1)
            {
                count++;
            }
        }
        return count;
    }

    private static void appendSceneryAdjacencyInterpretationRule(StringBuilder report)
    {
        report.append("    ignored-placement adjacency interpretation rule:").append('\n');
        report.append("      ADJ_SCENERY = either endpoint has an ignored locType 10 or 11 placement.").append('\n');
        report.append("      ADJ_OTHER_IGNORED = not ADJ_SCENERY, and either endpoint has another ")
            .append("ignored placement excluding locTypes 10, 11, and ground decoration 22.")
            .append('\n');
        report.append("      NOT_ADJACENT = neither endpoint is in the ignored-placement candidate sets.").append('\n');
        report.append("      ADJ_SCENERY wins ties; the three buckets are exhaustive over every ")
            .append("post-exclusion compared edge.")
            .append('\n');
        report.append("      This fourth hypothesis reuses the border, interior, and occupied-upper-floor ")
            .append("thresholds deliberately; no new threshold is introduced.")
            .append('\n');
        report.append("      Rate denominator is comparedEdges for that row; share denominator is the ")
            .append("bucket-sum DANGEROUS_UNEXPLAINED from ADJ_SCENERY + ADJ_OTHER_IGNORED + ")
            .append("NOT_ADJACENT.")
            .append('\n');
        report.append("      Main verdict compares ADJ_SCENERY against NOT_ADJACENT on ")
            .append("dangerousUnexplainedRate; secondary read compares ADJ_OTHER_IGNORED against ")
            .append("NOT_ADJACENT.")
            .append('\n');
        report.append("      - VACUOUS: either side comparedEdges < ")
            .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
            .append(" -> INCONCLUSIVE - VACUOUS, naming the short side and count with no ratio ")
            .append("computed.")
            .append('\n');
        report.append("      - CONFIRMED (ignored scenery): rate(ADJ_SCENERY) >= ")
            .append(BORDER_CONFIRMED_RATE_MULTIPLIER)
            .append("x rate(NOT_ADJACENT) AND ADJ_SCENERY holds >= ")
            .append(formatRate(BORDER_CONFIRMED_UNEXPLAINED_SHARE))
            .append(" of post-exclusion DANGEROUS_UNEXPLAINED.")
            .append('\n');
        report.append("      - REFUTED: rate(ADJ_SCENERY) < ")
            .append(BORDER_REFUTED_RATE_MULTIPLIER)
            .append("x rate(NOT_ADJACENT).")
            .append('\n');
        report.append("      - INCONCLUSIVE: anything else.").append('\n');
    }

    private static void appendSceneryAdjacencyCountsRow(
        StringBuilder report,
        String indent,
        String label,
        SceneryAdjacencyBucketCounts counts,
        long dangerousUnexplainedTotal
    )
    {
        report.append(indent).append(label)
            .append(' ').append(counts.comparedEdges)
            .append(' ').append(counts.dangerous)
            .append(' ').append(formatRateWithCounts(counts.dangerous, counts.comparedEdges))
            .append(' ').append(counts.dangerousUnexplained)
            .append(' ').append(formatRateWithCounts(counts.dangerousUnexplained, counts.comparedEdges))
            .append(' ').append(formatRateWithCounts(counts.dangerousUnexplained, dangerousUnexplainedTotal))
            .append(' ').append(counts.overblock)
            .append(' ').append(formatRateWithCounts(counts.overblock, counts.comparedEdges))
            .append('\n');
    }

    private static SceneryAdjacencyBucketCounts combinedSceneryAdjacencyCounts(
        SceneryAdjacencyMeasurement measurement
    )
    {
        SceneryAdjacencyBucketCounts combined = new SceneryAdjacencyBucketCounts();
        combined.add(measurement.counts(SceneryAdjacencyBucket.ADJ_SCENERY));
        combined.add(measurement.counts(SceneryAdjacencyBucket.ADJ_OTHER_IGNORED));
        return combined;
    }

    private static void appendSceneryAdjacencyAssertions(
        StringBuilder report,
        DangerousDirectionComparison comparison,
        long bucketComparedTotal,
        long bucketDangerousTotal,
        long bucketDangerousUnexplainedTotal,
        long bucketOverblockTotal
    )
    {
        SceneryAdjacencyMeasurement measurement = comparison.sceneryAdjacencyMeasurement;
        SceneryAdjacencyBucketCounts scenery = measurement.counts(SceneryAdjacencyBucket.ADJ_SCENERY);
        SceneryAdjacencyBucketCounts other = measurement.counts(SceneryAdjacencyBucket.ADJ_OTHER_IGNORED);
        SceneryAdjacencyBucketCounts notAdjacent = measurement.counts(SceneryAdjacencyBucket.NOT_ADJACENT);

        report.append("    adjacency split assertion: ADJ_SCENERY.comparedEdges + ")
            .append("ADJ_OTHER_IGNORED.comparedEdges + NOT_ADJACENT.comparedEdges == comparedEdges: ")
            .append(okFail(bucketComparedTotal == comparison.comparedEdges))
            .append(" (").append(scenery.comparedEdges)
            .append(" + ").append(other.comparedEdges)
            .append(" + ").append(notAdjacent.comparedEdges)
            .append(" == ").append(comparison.comparedEdges).append(")").append('\n');
        report.append("    adjacency split assertion: ADJ_SCENERY.dangerous + ADJ_OTHER_IGNORED.dangerous ")
            .append("+ NOT_ADJACENT.dangerous == DANGEROUS: ")
            .append(okFail(bucketDangerousTotal == comparison.dangerous))
            .append(" (").append(scenery.dangerous)
            .append(" + ").append(other.dangerous)
            .append(" + ").append(notAdjacent.dangerous)
            .append(" == ").append(comparison.dangerous).append(")").append('\n');
        report.append("    adjacency split assertion: ADJ_SCENERY.dangerousUnexplained + ")
            .append("ADJ_OTHER_IGNORED.dangerousUnexplained + NOT_ADJACENT.dangerousUnexplained ")
            .append("== DANGEROUS_UNEXPLAINED: ")
            .append(okFail(bucketDangerousUnexplainedTotal == comparison.dangerousUnexplained))
            .append(" (").append(scenery.dangerousUnexplained)
            .append(" + ").append(other.dangerousUnexplained)
            .append(" + ").append(notAdjacent.dangerousUnexplained)
            .append(" == ").append(comparison.dangerousUnexplained).append(")").append('\n');
        report.append("    adjacency split assertion: ADJ_SCENERY.overblock + ADJ_OTHER_IGNORED.overblock ")
            .append("+ NOT_ADJACENT.overblock == OVERBLOCK: ")
            .append(okFail(bucketOverblockTotal == comparison.overblock))
            .append(" (").append(scenery.overblock)
            .append(" + ").append(other.overblock)
            .append(" + ").append(notAdjacent.overblock)
            .append(" == ").append(comparison.overblock).append(")").append('\n');
    }

    private static void appendSceneryAdjacencyClosureAssertions(
        StringBuilder report,
        SceneryAdjacencyMeasurement measurement
    )
    {
        SceneryAdjacencyBucketCounts scenery = measurement.counts(SceneryAdjacencyBucket.ADJ_SCENERY);
        SceneryAdjacencyBucketCounts other = measurement.counts(SceneryAdjacencyBucket.ADJ_OTHER_IGNORED);
        SceneryAdjacencyBucketCounts adjIgnored = combinedSceneryAdjacencyCounts(measurement);
        SceneryAdjacencyBucketCounts solid =
            measurement.ignoredSolidityCounts(IgnoredSolidityBucket.ADJ_SOLID_FLAGGED);
        SceneryAdjacencyBucketCounts nonsolid =
            measurement.ignoredSolidityCounts(IgnoredSolidityBucket.ADJ_NONSOLID_ONLY);

        appendTwoBucketAssertion(
            report,
            "    adjacency union assertion: ",
            "ADJ_SCENERY.comparedEdges",
            scenery.comparedEdges,
            "ADJ_OTHER_IGNORED.comparedEdges",
            other.comparedEdges,
            "ADJ_IGNORED.comparedEdges",
            adjIgnored.comparedEdges
        );
        appendTwoBucketAssertion(
            report,
            "    adjacency union assertion: ",
            "ADJ_SCENERY.dangerous",
            scenery.dangerous,
            "ADJ_OTHER_IGNORED.dangerous",
            other.dangerous,
            "ADJ_IGNORED.dangerous",
            adjIgnored.dangerous
        );
        appendTwoBucketAssertion(
            report,
            "    adjacency union assertion: ",
            "ADJ_SCENERY.dangerousUnexplained",
            scenery.dangerousUnexplained,
            "ADJ_OTHER_IGNORED.dangerousUnexplained",
            other.dangerousUnexplained,
            "ADJ_IGNORED.dangerousUnexplained",
            adjIgnored.dangerousUnexplained
        );
        appendTwoBucketAssertion(
            report,
            "    adjacency union assertion: ",
            "ADJ_SCENERY.overblock",
            scenery.overblock,
            "ADJ_OTHER_IGNORED.overblock",
            other.overblock,
            "ADJ_IGNORED.overblock",
            adjIgnored.overblock
        );
        appendTwoBucketAssertion(
            report,
            "    ignored-solid split assertion: ",
            "ADJ_SOLID_FLAGGED.comparedEdges",
            solid.comparedEdges,
            "ADJ_NONSOLID_ONLY.comparedEdges",
            nonsolid.comparedEdges,
            "ADJ_IGNORED.comparedEdges",
            adjIgnored.comparedEdges
        );
        appendTwoBucketAssertion(
            report,
            "    ignored-solid split assertion: ",
            "ADJ_SOLID_FLAGGED.dangerous",
            solid.dangerous,
            "ADJ_NONSOLID_ONLY.dangerous",
            nonsolid.dangerous,
            "ADJ_IGNORED.dangerous",
            adjIgnored.dangerous
        );
        appendTwoBucketAssertion(
            report,
            "    ignored-solid split assertion: ",
            "ADJ_SOLID_FLAGGED.dangerousUnexplained",
            solid.dangerousUnexplained,
            "ADJ_NONSOLID_ONLY.dangerousUnexplained",
            nonsolid.dangerousUnexplained,
            "ADJ_IGNORED.dangerousUnexplained",
            adjIgnored.dangerousUnexplained
        );
        appendTwoBucketAssertion(
            report,
            "    ignored-solid split assertion: ",
            "ADJ_SOLID_FLAGGED.overblock",
            solid.overblock,
            "ADJ_NONSOLID_ONLY.overblock",
            nonsolid.overblock,
            "ADJ_IGNORED.overblock",
            adjIgnored.overblock
        );
    }

    private static void appendTwoBucketAssertion(
        StringBuilder report,
        String prefix,
        String leftLabel,
        long leftValue,
        String rightLabel,
        long rightValue,
        String totalLabel,
        long totalValue
    )
    {
        long sum = leftValue + rightValue;
        report.append(prefix)
            .append(leftLabel)
            .append(" + ")
            .append(rightLabel)
            .append(" == ")
            .append(totalLabel)
            .append(": ")
            .append(okFail(sum == totalValue))
            .append(" (").append(leftValue)
            .append(" + ").append(rightValue)
            .append(" == ").append(totalValue).append(")").append('\n');
    }

    private static void appendSceneryAdjacencyVerdicts(
        StringBuilder report,
        BuildStats stats,
        SceneryAdjacencyMeasurement measurement,
        long dangerousUnexplainedTotal
    )
    {
        SceneryAdjacencyBucketCounts notAdjacent = measurement.counts(SceneryAdjacencyBucket.NOT_ADJACENT);

        report.append("    verdict ADJ_SCENERY vs NOT_ADJACENT: ")
            .append(sceneryAdjacencyVerdict(
                "ADJ_SCENERY",
                "CONFIRMED (ignored scenery)",
                measurement.counts(SceneryAdjacencyBucket.ADJ_SCENERY),
                notAdjacent,
                dangerousUnexplainedTotal
            ))
            .append('\n');
        report.append("    secondary read ADJ_OTHER_IGNORED vs NOT_ADJACENT: ")
            .append(sceneryAdjacencyVerdict(
                "ADJ_OTHER_IGNORED",
                "CONFIRMED (other ignored placements)",
                measurement.counts(SceneryAdjacencyBucket.ADJ_OTHER_IGNORED),
                notAdjacent,
                dangerousUnexplainedTotal
            ))
            .append('\n');
        SceneryAdjacencyBucketCounts adjIgnored = combinedSceneryAdjacencyCounts(measurement);
        report.append("    NOTE: this union verdict is arithmetic on two buckets already measured ")
            .append("in the previous run. It records the union as a stated test; it is NOT new ")
            .append("evidence. The new evidence in this report is the solid-flag split below.")
            .append('\n');
        report.append("    verdict ADJ_IGNORED vs NOT_ADJACENT: ")
            .append(sceneryAdjacencyVerdict(
                "ADJ_IGNORED",
                "CONFIRMED",
                adjIgnored,
                notAdjacent,
                dangerousUnexplainedTotal
            ))
            .append('\n');
        if (stats.phase2SolidObjectBlockingEnabled)
        {
            /*
             * Same premise failure as the ADJ_SCENERY overblock control: phase 2 writes
             * edges for the ignored-object set this control assumes is unwritten, so with
             * phase 2 on it reports FAIL by construction rather than by evidence.
             */
            report.append("    union overblock control: NOT APPLICABLE - phase 2 ")
                .append("solid-object blocking is ENABLED. Re-run with ")
                .append(DISABLE_PHASE2_SOLID_OBJECTS_ARG)
                .append(" to evaluate it.")
                .append('\n');
        }
        else
        {
            appendSceneryAdjacencyUnionOverblockControl(report, adjIgnored, notAdjacent);
        }
        appendIgnoredSoliditySplit(report, measurement, notAdjacent, dangerousUnexplainedTotal);
        appendIgnoredObjectFlagDiscriminationTable(report, measurement, notAdjacent);
    }

    private static void appendSceneryAdjacencyUnionOverblockControl(
        StringBuilder report,
        SceneryAdjacencyBucketCounts adjIgnored,
        SceneryAdjacencyBucketCounts notAdjacent
    )
    {
        double adjIgnoredOverblockRate = rate(adjIgnored.overblock, adjIgnored.comparedEdges);
        double notAdjacentOverblockRate = rate(notAdjacent.overblock, notAdjacent.comparedEdges);
        boolean controlPassed = adjIgnoredOverblockRate
            <= notAdjacentOverblockRate * BORDER_REFUTED_RATE_MULTIPLIER;

        if (controlPassed)
        {
            report.append("    union overblock control PASS (")
                .append(formatPercentOnly(adjIgnoredOverblockRate))
                .append(" <= ")
                .append(formatPercentOnly(notAdjacentOverblockRate))
                .append(" * ")
                .append(BORDER_REFUTED_RATE_MULTIPLIER)
                .append(")").append('\n');
        }
        else
        {
            report.append("    union overblock control FAIL (")
                .append(formatPercentOnly(adjIgnoredOverblockRate))
                .append(" > ")
                .append(formatPercentOnly(notAdjacentOverblockRate))
                .append(" * ")
                .append(BORDER_REFUTED_RATE_MULTIPLIER)
                .append(")").append('\n');
        }
    }

    private static void appendIgnoredSoliditySplit(
        StringBuilder report,
        SceneryAdjacencyMeasurement measurement,
        SceneryAdjacencyBucketCounts notAdjacent,
        long dangerousUnexplainedTotal
    )
    {
        SceneryAdjacencyBucketCounts solid =
            measurement.ignoredSolidityCounts(IgnoredSolidityBucket.ADJ_SOLID_FLAGGED);
        SceneryAdjacencyBucketCounts nonsolid =
            measurement.ignoredSolidityCounts(IgnoredSolidityBucket.ADJ_NONSOLID_ONLY);

        report.append("    ignored-solid split:").append('\n');
        report.append("      PREDICTION (stated before the run): if ignored solid objects are the cause, ")
            .append("ADJ_SOLID_FLAGGED carries the errors and ADJ_NONSOLID_ONLY looks like ")
            .append("NOT_ADJACENT. If ADJ_NONSOLID_ONLY is just as bad as ADJ_SOLID_FLAGGED, ")
            .append("then adjacency is tracking clutter, not solidity, and the object theory is ")
            .append("weakened even though the union verdict passed.")
            .append('\n');
        report.append("      bucket comparedEdges DANGEROUS dangerousRate(DANGEROUS/comparedEdges) ")
            .append("DANGEROUS_UNEXPLAINED dangerousUnexplainedRate(DANGEROUS_UNEXPLAINED/comparedEdges) ")
            .append("unexplainedShare(DANGEROUS_UNEXPLAINED/bucket-sum) OVERBLOCK ")
            .append("overblockRate(OVERBLOCK/comparedEdges)").append('\n');
        appendSceneryAdjacencyCountsRow(
            report,
            "      ",
            IgnoredSolidityBucket.ADJ_SOLID_FLAGGED.name(),
            solid,
            dangerousUnexplainedTotal
        );
        appendSceneryAdjacencyCountsRow(
            report,
            "      ",
            IgnoredSolidityBucket.ADJ_NONSOLID_ONLY.name(),
            nonsolid,
            dangerousUnexplainedTotal
        );
        report.append("      verdict ADJ_SOLID_FLAGGED vs NOT_ADJACENT: ")
            .append(sceneryAdjacencyVerdict(
                "ADJ_SOLID_FLAGGED",
                "CONFIRMED",
                solid,
                notAdjacent,
                dangerousUnexplainedTotal
            ))
            .append('\n');
        appendSceneryAdjacencyRateRead(report, "      ", "ADJ_NONSOLID_ONLY", nonsolid, notAdjacent);
    }

    private static void appendSceneryAdjacencyRateRead(
        StringBuilder report,
        String indent,
        String label,
        SceneryAdjacencyBucketCounts counts,
        SceneryAdjacencyBucketCounts notAdjacent
    )
    {
        double candidateRate = rate(counts.dangerousUnexplained, counts.comparedEdges);
        double notAdjacentRate = rate(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges);
        report.append(indent)
            .append("rate ")
            .append(label)
            .append(" vs NOT_ADJACENT: ")
            .append(label)
            .append(" dangerousUnexplainedRate ")
            .append(formatRateWithCounts(counts.dangerousUnexplained, counts.comparedEdges))
            .append(", NOT_ADJACENT dangerousUnexplainedRate ")
            .append(formatRateWithCounts(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges))
            .append(", rateRatio ")
            .append(sceneryAdjacencyRateRatio(candidateRate, notAdjacentRate))
            .append('\n');
    }

    private static void appendIgnoredObjectFlagDiscriminationTable(
        StringBuilder report,
        SceneryAdjacencyMeasurement measurement,
        SceneryAdjacencyBucketCounts notAdjacent
    )
    {
        report.append("    flag discrimination - higher ratio = better separator:").append('\n');
        report.append("      flag markedAdjIgnoredEdges dangerousUnexplainedRate ratioAgainstNOT_ADJACENT")
            .append('\n');
        for (IgnoredObjectFlag flag : IgnoredObjectFlag.values())
        {
            appendIgnoredObjectFlagDiscriminationRow(report, measurement, flag, notAdjacent);
        }
    }

    private static void appendIgnoredObjectFlagDiscriminationRow(
        StringBuilder report,
        SceneryAdjacencyMeasurement measurement,
        IgnoredObjectFlag flag,
        SceneryAdjacencyBucketCounts notAdjacent
    )
    {
        SceneryAdjacencyBucketCounts counts = measurement.ignoredObjectFlagCounts(flag);
        double candidateRate = rate(counts.dangerousUnexplained, counts.comparedEdges);
        double notAdjacentRate = rate(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges);
        report.append("      ")
            .append(ignoredObjectFlagLabel(flag))
            .append(": ")
            .append(counts.comparedEdges)
            .append(' ')
            .append(formatRateWithCounts(counts.dangerousUnexplained, counts.comparedEdges))
            .append(' ')
            .append(sceneryAdjacencyRateRatio(candidateRate, notAdjacentRate))
            .append('\n');
    }

    private static String ignoredObjectFlagLabel(IgnoredObjectFlag flag)
    {
        switch (flag)
        {
            case INTERACT_TYPE_NONZERO:
                return "getInteractType() != 0";
            case BLOCKS_PROJECTILE:
                return "isBlocksProjectile()";
            case OBSTRUCTS_GROUND:
                return "isObstructsGround()";
            default:
                throw new IllegalArgumentException("Unhandled ignored object flag " + flag);
        }
    }

    private static String sceneryAdjacencyVerdict(
        String candidateLabel,
        String confirmedVerdict,
        SceneryAdjacencyBucketCounts candidate,
        SceneryAdjacencyBucketCounts notAdjacent,
        long dangerousUnexplainedTotal
    )
    {
        /*
         * Reuse the border-hypothesis thresholds deliberately: this fourth hypothesis must clear
         * exactly the same bar the previous three hypotheses had to clear.
         */
        if (candidate.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR
            || notAdjacent.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            return sceneryAdjacencyVacuousVerdict(candidateLabel, candidate, notAdjacent);
        }

        double candidateRate = rate(candidate.dangerousUnexplained, candidate.comparedEdges);
        double notAdjacentRate = rate(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges);
        double candidateUnexplainedShare = rate(candidate.dangerousUnexplained, dangerousUnexplainedTotal);
        String details = " - " + candidateLabel + " dangerousUnexplainedRate "
            + formatRateWithCounts(candidate.dangerousUnexplained, candidate.comparedEdges)
            + ", NOT_ADJACENT dangerousUnexplainedRate "
            + formatRateWithCounts(notAdjacent.dangerousUnexplained, notAdjacent.comparedEdges)
            + ", rateRatio "
            + sceneryAdjacencyRateRatio(candidateRate, notAdjacentRate)
            + ", unexplainedShare "
            + formatRateWithCounts(candidate.dangerousUnexplained, dangerousUnexplainedTotal);

        if (candidateRate >= notAdjacentRate * BORDER_CONFIRMED_RATE_MULTIPLIER
            && candidateUnexplainedShare >= BORDER_CONFIRMED_UNEXPLAINED_SHARE)
        {
            return confirmedVerdict + details;
        }
        if (candidateRate < notAdjacentRate * BORDER_REFUTED_RATE_MULTIPLIER)
        {
            return "REFUTED" + details;
        }
        return "INCONCLUSIVE" + details;
    }

    private static String sceneryAdjacencyVacuousVerdict(
        String candidateLabel,
        SceneryAdjacencyBucketCounts candidate,
        SceneryAdjacencyBucketCounts notAdjacent
    )
    {
        StringBuilder verdict = new StringBuilder("INCONCLUSIVE - VACUOUS - ");
        boolean first = true;
        if (candidate.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            verdict.append(candidateLabel)
                .append(" comparedEdges ")
                .append(candidate.comparedEdges)
                .append(" < ")
                .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR);
            first = false;
        }
        if (notAdjacent.comparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            if (!first)
            {
                verdict.append("; ");
            }
            verdict.append("NOT_ADJACENT comparedEdges ")
                .append(notAdjacent.comparedEdges)
                .append(" < ")
                .append(BORDER_INTERIOR_COMPARED_EDGE_FLOOR);
        }
        verdict.append(" - no ratio computed");
        return verdict.toString();
    }

    private static String sceneryAdjacencyRateRatio(double candidateRate, double notAdjacentRate)
    {
        if (notAdjacentRate == 0.0)
        {
            if (candidateRate == 0.0)
            {
                return "undefined (both rates zero)";
            }
            return "undefined (NOT_ADJACENT rate zero)";
        }
        return formatMultiplier(candidateRate / notAdjacentRate);
    }

    private static void appendSceneryAdjacencyOverblockControl(
        StringBuilder report,
        BuildStats stats,
        SceneryAdjacencyMeasurement measurement
    )
    {
        SceneryAdjacencyBucketCounts scenery = measurement.counts(SceneryAdjacencyBucket.ADJ_SCENERY);
        SceneryAdjacencyBucketCounts notAdjacent = measurement.counts(SceneryAdjacencyBucket.NOT_ADJACENT);
        double sceneryOverblockRate = rate(scenery.overblock, scenery.comparedEdges);
        double notAdjacentOverblockRate = rate(notAdjacent.overblock, notAdjacent.comparedEdges);
        boolean controlPassed = sceneryOverblockRate
            <= notAdjacentOverblockRate * BORDER_REFUTED_RATE_MULTIPLIER;

        report.append("    overblock control rule:").append('\n');
        report.append("      Missing ignored objects can only make this builder say PASSABLE where ")
            .append("the client says BLOCKED; they cannot cause OVERBLOCK because no edge is ")
            .append("written for them.")
            .append('\n');
        report.append("      control PASS if overblockRate(ADJ_SCENERY) <= ")
            .append("overblockRate(NOT_ADJACENT) * ")
            .append(BORDER_REFUTED_RATE_MULTIPLIER)
            .append(".")
            .append('\n');
        report.append("    overblock rates:").append('\n');
        for (SceneryAdjacencyBucket bucket : SceneryAdjacencyBucket.values())
        {
            SceneryAdjacencyBucketCounts counts = measurement.counts(bucket);
            report.append("      ").append(bucket.name())
                .append(' ').append(formatRateWithCounts(counts.overblock, counts.comparedEdges))
                .append('\n');
        }
        if (stats.phase2SolidObjectBlockingEnabled)
        {
            /*
             * The control's premise - "no edge is written for ignored objects" - is false while
             * phase 2 is on, because phase 2 writes edges for exactly the ADJ_SCENERY set. Run
             * this way the control measures its own fix and reports FAIL by construction.
             * Measured 2026-08-12 over the same 62 regions and the same capture: ADJ_SCENERY
             * overblockRate 0.912% with phase 2 off against 14.026% with phase 2 on.
             */
            report.append("    overblock control: NOT APPLICABLE - phase 2 solid-object blocking ")
                .append("is ENABLED, so the premise above does not hold and the rates shown are ")
                .append("informational only. Re-run with ")
                .append(DISABLE_PHASE2_SOLID_OBJECTS_ARG)
                .append(" to evaluate this control.")
                .append('\n');
            return;
        }
        if (controlPassed)
        {
            report.append("    overblock control PASS - ADJ_SCENERY overblockRate ")
                .append(formatRateWithCounts(scenery.overblock, scenery.comparedEdges))
                .append(" <= NOT_ADJACENT overblockRate ")
                .append(formatRateWithCounts(notAdjacent.overblock, notAdjacent.comparedEdges))
                .append(" * ")
                .append(BORDER_REFUTED_RATE_MULTIPLIER)
                .append('\n');
        }
        else
        {
            report.append("    overblock control FAIL - ADJ_SCENERY overblockRate ")
                .append(formatRateWithCounts(scenery.overblock, scenery.comparedEdges))
                .append(" > NOT_ADJACENT overblockRate ")
                .append(formatRateWithCounts(notAdjacent.overblock, notAdjacent.comparedEdges))
                .append(" * ")
                .append(BORDER_REFUTED_RATE_MULTIPLIER)
                .append('\n');
            report.append("CONTROL FAILED - the object theory predicts no overblock concentration; this contradicts it")
                .append('\n');
        }
    }

    private static void appendSceneryAdjacencyCensus(
        StringBuilder report,
        BuildStats stats,
        SceneryAdjacencyMeasurement measurement
    )
    {
        report.append("    ignored-placement adjacency census:").append('\n');
        report.append("      unique tiles in sceneryPlacementTileKeys: ")
            .append(stats.sceneryPlacementTileKeys.size()).append('\n');
        report.append("      unique tiles in otherIgnoredTileKeys: ")
            .append(stats.otherIgnoredTileKeys.size()).append('\n');
        report.append("      plane comparedEdges ADJ_SCENERY ADJ_OTHER_IGNORED NOT_ADJACENT").append('\n');
        for (int plane = 0; plane < PLANE_COUNT; plane++)
        {
            appendSceneryAdjacencyPlaneCensusRow(report, measurement, plane);
        }
        appendSceneryAdjacencyPlaneAssertions(report, measurement);
        appendIgnoredNonDecorDefinitionCensus(report, stats);
    }

    private static void appendSceneryAdjacencyPlaneCensusRow(
        StringBuilder report,
        SceneryAdjacencyMeasurement measurement,
        int plane
    )
    {
        report.append("      ").append(plane)
            .append(' ').append(measurement.planeComparedTotal(plane))
            .append(' ').append(measurement.planeCounts(plane, SceneryAdjacencyBucket.ADJ_SCENERY).comparedEdges)
            .append(' ').append(measurement.planeCounts(plane, SceneryAdjacencyBucket.ADJ_OTHER_IGNORED).comparedEdges)
            .append(' ').append(measurement.planeCounts(plane, SceneryAdjacencyBucket.NOT_ADJACENT).comparedEdges)
            .append('\n');
    }

    private static void appendSceneryAdjacencyPlaneAssertions(
        StringBuilder report,
        SceneryAdjacencyMeasurement measurement
    )
    {
        for (int plane = 0; plane < PLANE_COUNT; plane++)
        {
            SceneryAdjacencyBucketCounts scenery =
                measurement.planeCounts(plane, SceneryAdjacencyBucket.ADJ_SCENERY);
            SceneryAdjacencyBucketCounts other =
                measurement.planeCounts(plane, SceneryAdjacencyBucket.ADJ_OTHER_IGNORED);
            SceneryAdjacencyBucketCounts notAdjacent =
                measurement.planeCounts(plane, SceneryAdjacencyBucket.NOT_ADJACENT);
            long comparedTotal = scenery.comparedEdges + other.comparedEdges + notAdjacent.comparedEdges;

            report.append("      plane ").append(plane).append(" adjacency split assertion: ")
                .append("ADJ_SCENERY + ADJ_OTHER_IGNORED + NOT_ADJACENT == comparedEdges: ")
                .append(okFail(comparedTotal == measurement.planeComparedTotal(plane)))
                .append(" (").append(scenery.comparedEdges)
                .append(" + ").append(other.comparedEdges)
                .append(" + ").append(notAdjacent.comparedEdges)
                .append(" == ").append(measurement.planeComparedTotal(plane)).append(")").append('\n');
        }
    }

    private static void appendIgnoredNonDecorDefinitionCensus(StringBuilder report, BuildStats stats)
    {
        report.append("    ignored non-decor ObjectDefinition census:").append('\n');
        report.append("      placements: ").append(stats.ignoredNonDecorPlacements).append('\n');
        report.append("      missing definitions: ")
            .append(stats.ignoredNonDecorMissingDefinitionPlacements).append('\n');
        report.append("      getInteractType() counts: 0=")
            .append(stats.ignoredNonDecorInteractType0Placements)
            .append(" 1=").append(stats.ignoredNonDecorInteractType1Placements)
            .append(" 2=").append(stats.ignoredNonDecorInteractType2Placements)
            .append(" other=").append(stats.ignoredNonDecorInteractTypeOtherPlacements)
            .append('\n');
        report.append("      footprint histogram, largest 12 rows:").append('\n');
        appendIgnoredNonDecorFootprintHistogram(report, stats);
        report.append("      placements with footprint larger than 1x1: ")
            .append(stats.ignoredNonDecorFootprintLargerThanOneByOnePlacements).append('\n');
        report.append("      isBlocksProjectile() true: ")
            .append(stats.ignoredNonDecorBlocksProjectilePlacements).append('\n');
        report.append("      isObstructsGround() true: ")
            .append(stats.ignoredNonDecorObstructsGroundPlacements).append('\n');
        report.append("      getWallOrDoor() != 0: ")
            .append(stats.ignoredNonDecorWallOrDoorPlacements).append('\n');
        report.append("      getBlockingMask() != 0: ")
            .append(stats.ignoredNonDecorBlockingMaskPlacements).append('\n');
    }

    private static void appendIgnoredNonDecorFootprintHistogram(StringBuilder report, BuildStats stats)
    {
        if (stats.ignoredNonDecorFootprintHistogram.isEmpty())
        {
            report.append("        (none)").append('\n');
            return;
        }

        List<Map.Entry<String, Long>> rows = new ArrayList<>(
            stats.ignoredNonDecorFootprintHistogram.entrySet());
        rows.sort((left, right) ->
        {
            int byCount = Long.compare(right.getValue(), left.getValue());
            if (byCount != 0)
            {
                return byCount;
            }
            return left.getKey().compareTo(right.getKey());
        });

        int rowLimit = 12;
        int rowsToPrint = Math.min(rows.size(), rowLimit);
        for (int i = 0; i < rowsToPrint; i++)
        {
            Map.Entry<String, Long> row = rows.get(i);
            report.append("        ").append(row.getKey()).append(": ")
                .append(row.getValue()).append('\n');
        }
        if (rows.size() > rowLimit)
        {
            report.append("        suppressed ")
                .append(rows.size() - rowLimit)
                .append(" footprint rows with fewer placements").append('\n');
        }
    }

    private static String borderHistogramVerdict(DangerousDirectionComparison comparison)
    {
        BorderHistogram histogram = comparison.maxBorderDistanceHistogram;
        if (histogram.bucketedComparedEdges == 0)
        {
            return "INCONCLUSIVE - BORDER HISTOGRAM VACUOUS - zero compared edges bucketed";
        }

        long borderComparedEdges = histogram.comparedEdges(BORDER_MAX_DISTANCE);
        long borderDangerousUnexplained = histogram.dangerousUnexplained(BORDER_MAX_DISTANCE);
        long interiorComparedEdges = histogram.comparedEdgesAtOrAbove(INTERIOR_MIN_DISTANCE);
        double borderRate = rate(histogram.dangerous(BORDER_MAX_DISTANCE), borderComparedEdges);
        double interiorRate = rate(histogram.dangerousAtOrAbove(INTERIOR_MIN_DISTANCE), interiorComparedEdges);
        /*
         * The denominator MUST come from the histogram, not from comparison.dangerousUnexplained.
         * The headline counter now excludes border rings 0..BORDER_MAX_DISTANCE, while the numerator
         * is drawn from those very rings. Dividing one by the other asks "of the unexplained edges
         * left after removing the border, how many are border edges" - which inflates the share and
         * flips this verdict to CONFIRMED on its own exclusion. The histogram still counts every
         * edge, so bucketedDangerousUnexplained is the exclusion-independent population and keeps
         * this test measuring what it was written to measure.
         */
        double borderUnexplainedShare = rate(borderDangerousUnexplained, histogram.bucketedDangerousUnexplained);

        if (interiorComparedEdges < BORDER_INTERIOR_COMPARED_EDGE_FLOOR)
        {
            return "INCONCLUSIVE - fewer than "
                + BORDER_INTERIOR_COMPARED_EDGE_FLOOR
                + " compared edges in INTERIOR";
        }
        if (borderRate >= interiorRate * BORDER_CONFIRMED_RATE_MULTIPLIER
            && borderUnexplainedShare >= BORDER_CONFIRMED_UNEXPLAINED_SHARE)
        {
            return "CONFIRMED (border artifact)";
        }
        if (borderRate < interiorRate * BORDER_REFUTED_RATE_MULTIPLIER)
        {
            return "REFUTED";
        }
        return "INCONCLUSIVE";
    }

    private static void appendBorderHistogram(
        StringBuilder report,
        String distanceLabel,
        BorderHistogram histogram
    )
    {
        report.append("  ").append(distanceLabel).append(" border histogram:").append('\n');
        if (histogram.bucketedComparedEdges == 0)
        {
            report.append("    BORDER HISTOGRAM VACUOUS - zero compared edges bucketed").append('\n');
        }
        report.append("    bucket comparedEdges DANGEROUS dangerousRate DANGEROUS_UNEXPLAINED").append('\n');
        for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
        {
            BorderDistanceBucket bucket = BORDER_DISTANCE_BUCKETS[i];
            BorderBucketCounts counts = histogram.counts[i];
            report.append("    ").append(bucket.label)
                .append(' ').append(counts.comparedEdges)
                .append(' ').append(counts.dangerous)
                .append(' ').append(formatRate(rate(counts.dangerous, counts.comparedEdges)))
                .append(' ').append(counts.dangerousUnexplained)
                .append('\n');
        }
    }

    private static void appendDangerousSplit(StringBuilder report, DangerousDirectionComparison comparison)
    {
        long splitTotal = comparison.dangerousSplitTotal();
        report.append("  dangerous split:").append('\n');
        if (comparison.dangerous == 0)
        {
            report.append("    DANGEROUS SPLIT VACUOUS - zero dangerous edges to split").append('\n');
        }
        report.append("    DANGEROUS_DOOR_CAPABLE: ").append(comparison.dangerousDoorCapable)
            .append(" (").append(percent(comparison.dangerousDoorCapable, comparison.dangerous))
            .append(" of DANGEROUS)").append('\n');
        report.append("    DANGEROUS_CONFLICTED: ").append(comparison.dangerousConflicted)
            .append(" (").append(percent(comparison.dangerousConflicted, comparison.dangerous))
            .append(" of DANGEROUS)").append('\n');
        report.append("    DANGEROUS_UNEXPLAINED: ").append(comparison.dangerousUnexplained)
            .append(" (").append(percent(comparison.dangerousUnexplained, comparison.dangerous))
            .append(" of DANGEROUS)").append('\n');
        report.append("    DANGEROUS_BOTH is NOT a bucket - door-capable wins before conflicted ")
            .append("(overlap assigned to DANGEROUS_DOOR_CAPABLE: ")
            .append(comparison.dangerousDoorCapableAndConflicted).append(")").append('\n');
        report.append("    DANGEROUS split assertion: DANGEROUS_DOOR_CAPABLE + ")
            .append("DANGEROUS_CONFLICTED + DANGEROUS_UNEXPLAINED == DANGEROUS: ")
            .append(splitTotal == comparison.dangerous ? "OK" : "FAIL")
            .append(" (").append(splitTotal).append(" == ").append(comparison.dangerous)
            .append(")").append('\n');
        appendDangerousDoorCapableHistogram(report, comparison);
        appendDangerousUnexplainedExamples(report, comparison);
    }

    private static void appendDangerousDoorCapableHistogram(
        StringBuilder report,
        DangerousDirectionComparison comparison
    )
    {
        report.append("    DANGEROUS_DOOR_CAPABLE locType histogram:").append('\n');
        if (comparison.dangerousDoorCapableByLocType.isEmpty())
        {
            report.append("      (none)").append('\n');
        }
        else
        {
            List<Map.Entry<Integer, Long>> entries = new ArrayList<>(
                comparison.dangerousDoorCapableByLocType.entrySet());
            entries.sort((left, right) ->
            {
                int byCount = Long.compare(right.getValue(), left.getValue());
                if (byCount != 0)
                {
                    return byCount;
                }
                return Integer.compare(left.getKey(), right.getKey());
            });
            for (Map.Entry<Integer, Long> entry : entries)
            {
                report.append("      locType ").append(entry.getKey()).append(" (shapeFor ")
                    .append(shapeForHandlesLocType(entry.getKey()) ? "HANDLED" : "IGNORED")
                    .append("): ").append(entry.getValue()).append('\n');
            }
        }
        report.append("    door-capable on IGNORED locType: ")
            .append(comparison.dangerousDoorCapableIgnoredLocType)
            .append(" (")
            .append(percent(comparison.dangerousDoorCapableIgnoredLocType, comparison.dangerousDoorCapable))
            .append(" of DANGEROUS_DOOR_CAPABLE)").append('\n');
        report.append("    largest door-capable locType group ignored by shapeFor: ")
            .append(largestDangerousDoorCapableLocTypeIgnored(comparison)).append('\n');
    }

    private static boolean largestDangerousDoorCapableLocTypeIgnored(DangerousDirectionComparison comparison)
    {
        Integer largestLocType = null;
        long largestCount = Long.MIN_VALUE;
        for (Map.Entry<Integer, Long> entry : comparison.dangerousDoorCapableByLocType.entrySet())
        {
            if (entry.getValue() > largestCount
                || (entry.getValue() == largestCount && largestLocType != null
                    && entry.getKey() < largestLocType))
            {
                largestLocType = entry.getKey();
                largestCount = entry.getValue();
            }
        }
        return largestLocType != null && !shapeForHandlesLocType(largestLocType);
    }

    private static void appendDangerousUnexplainedExamples(
        StringBuilder report,
        DangerousDirectionComparison comparison
    )
    {
        report.append("    example DANGEROUS_UNEXPLAINED edges:").append('\n');
        if (comparison.dangerousUnexplainedExamples.isEmpty())
        {
            report.append("      (none)").append('\n');
        }
        else
        {
            for (String example : comparison.dangerousUnexplainedExamples)
            {
                report.append("      ").append(example).append('\n');
            }
        }
    }

    private static void appendDangerousInterpretationRule(
        StringBuilder report,
        DangerousDirectionComparison comparison
    )
    {
        report.append("  dangerous split interpretation rule:").append('\n');
        report.append("    - if DANGEROUS_UNEXPLAINED is under ~10% of DANGEROUS, the 28k headline was essentially all "
            + "confound and the map is not carrying thousands of routing bugs.").append('\n');
        report.append("    - if door-capable-on-ignored-locType is the largest single locType group, the builder has a "
            + "door-classification gap and that is the actionable defect, NOT the raw DANGEROUS count.").append('\n');
        report.append("    - if DANGEROUS_UNEXPLAINED stays large and is not concentrated on any locType, then the "
            + "dangerous edges are real and need their own investigation.").append('\n');
        report.append("  interpretation rule:").append('\n');
        if (comparison.comparedEdges == 0)
        {
            report.append("    DANGEROUS PASS VACUOUS - zero edges compared, proves nothing").append('\n');
        }
        report.append("    orientation 3 is IMPLICATED only if BOTH: ");
        report.append("dangerousRateOrient3 >= 3x dangerousRateAll AND at least ");
        report.append("30 dangerous edges land on orient-3 tiles. ");
        report.append("Below 30 the sample is too small to conclude either way and ");
        report.append("the report must say so rather than read noise.").append('\n');
        report.append("    if dangerousRateOrient3 is at or below dangerousRateAll, ");
        report.append("orientation 3 is EXONERATED: it is no worse than the map average ");
        report.append("and the rule stays.").append('\n');
    }

    private static String dangerousInterpretation(
        DangerousDirectionComparison comparison,
        double dangerousRateAll,
        double dangerousRateOrient3
    )
    {
        if (comparison.comparedEdges == 0)
        {
            return "DANGEROUS PASS VACUOUS - zero edges compared, proves nothing";
        }
        if (dangerousRateOrient3 <= dangerousRateAll)
        {
            return "orientation 3 is EXONERATED: it is no worse than the map average and the rule stays";
        }
        if (comparison.dangerousOrient3 < ORIENT3_DANGEROUS_SAMPLE_FLOOR)
        {
            return "orientation 3 sample is too small to conclude either way: fewer than "
                + ORIENT3_DANGEROUS_SAMPLE_FLOOR + " dangerous edges land on orient-3 tiles";
        }
        if (dangerousRateOrient3 >= dangerousRateAll * 3.0)
        {
            return "orientation 3 is IMPLICATED: dangerousRateOrient3 is at least 3x dangerousRateAll "
                + "and at least " + ORIENT3_DANGEROUS_SAMPLE_FLOOR
                + " dangerous edges land on orient-3 tiles";
        }
        return "orientation 3 is not implicated: its dangerous rate is above average but below the 3x rule";
    }

    private static void appendBuildStats(StringBuilder report, BuildStats stats)
    {
        report.append("build statistics:").append('\n');
        report.append("  regions built: ").append(stats.totalRegionsBuilt).append('\n');
        report.append("  placements by locType:").append('\n');
        if (stats.placementsByLocType.isEmpty())
        {
            report.append("    (none)").append('\n');
        }
        else
        {
            for (Map.Entry<Integer, Long> entry : stats.placementsByLocType.entrySet())
            {
                report.append("    ").append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append('\n');
            }
        }
        for (int orientation = 0; orientation < stats.locType1PlacementsByOrientation.length; orientation++)
        {
            report.append("  locType1-orient").append(orientation).append(" placements: ")
                .append(stats.locType1PlacementsByOrientation[orientation]).append('\n');
        }
        report.append("  locType1 edges blocked total: ").append(stats.locType1EdgesBlockedTotal).append('\n');
        report.append("  locType1 orientation-3 placements: ")
            .append(stats.locType1Orientation3Placements).append('\n');
        report.append("  locType1 invalid-orientation fallbacks: ")
            .append(stats.locType1InvalidOrientationFallbacks).append('\n');
        report.append("  ignored-locType count: ").append(stats.ignoredLocTypePlacements).append('\n');
        report.append("  Phase 3 roof placements blocked: ")
            .append(stats.phase3RoofPlacements).append('\n');
        report.append("  Phase 3 roof placements blocked by plane: ")
            .append(stats.phase3RoofPlacementsByPlane[0]).append(' ')
            .append(stats.phase3RoofPlacementsByPlane[1]).append(' ')
            .append(stats.phase3RoofPlacementsByPlane[2]).append(' ')
            .append(stats.phase3RoofPlacementsByPlane[3]).append('\n');
        report.append("  Phase 3 roof open-style placements blocked: ")
            .append(stats.phase3RoofOpenStylePlacements).append('\n');
        report.append("  Phase 2 solid-object placements blocked: ")
            .append(stats.phase2SolidObjectPlacements).append('\n');
        report.append("  Phase 2 solid-object open-style placements blocked: ")
            .append(stats.phase2SolidObjectOpenStylePlacements).append('\n');
        report.append("  Phase 2 solid-object missing-definition placements skipped: ")
            .append(stats.phase2SolidObjectMissingDefinitionSkipped).append('\n');
        report.append("  Phase 2 solid-object footprint-held-back placements: ")
            .append(stats.phase2SolidObjectFootprintHeldBackPlacements).append('\n');
        report.append("  door-capable placement tiles: ")
            .append(stats.doorCapableLocTypeByTile.size()).append('\n');
        report.append("  door-capable placement tile collisions: ")
            .append(stats.doorCapableTileCollisions).append('\n');
        report.append("  terrain-blocked count: ").append(stats.terrainBlockedTiles).append('\n');
        report.append("  bridge-branch count: ").append(stats.bridgeBranchTiles).append('\n');
        report.append("  out-of-region neighbour edges deferred: ")
            .append(stats.outOfRegionNeighbourEdgesDeferred).append('\n');
        report.append("  out-of-region neighbour edges applied: ")
            .append(stats.outOfRegionNeighbourEdgesApplied).append('\n');
        report.append("  out-of-region neighbour skips: ").append(stats.outOfRegionNeighbourSkips).append('\n');
        report.append("  total edges made passable: ").append(stats.totalEdgesMadePassable).append('\n');
        report.append("  door edges written: ").append(stats.doorEdgesWritten).append('\n');
        report.append("  terrain note: tile-setting floor blocking and bridge lowering are VERIFIED against ");
        report.append("live client ground truth (2026-08-12): precision 98.086%, recall 100.000%.").append('\n');
    }

    private static void appendRegionList(StringBuilder report, Set<Integer> regionIds)
    {
        boolean first = true;
        for (int regionId : regionIds)
        {
            if (!first)
            {
                report.append(", ");
            }
            first = false;
            report.append(formatRegionId(regionId));
        }
    }

    private static String percent(long count, long total)
    {
        if (total == 0)
        {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", count * 100.0 / total);
    }

    private static double rate(long count, long total)
    {
        if (total == 0)
        {
            return 0.0;
        }
        return count / (double) total;
    }

    private static String formatRate(double rate)
    {
        return String.format(Locale.ROOT, "%.6f (%.3f%%)", rate, rate * 100.0);
    }

    private static String formatPercentOnly(double rate)
    {
        return String.format(Locale.ROOT, "%.3f%%", rate * 100.0);
    }

    private static String formatRateWithCounts(long count, long total)
    {
        return formatRate(rate(count, total)) + " (" + count + "/" + total + ")";
    }

    private static String formatMultiplier(double multiplier)
    {
        return String.format(Locale.ROOT, "%.3fx", multiplier);
    }

    private static String okFail(boolean ok)
    {
        if (ok)
        {
            return "OK";
        }
        return "FAIL";
    }

    private static LiveCapture parseLiveCapture(Path liveFlagsFile) throws IOException
    {
        if (Files.size(liveFlagsFile) == 0)
        {
            throw new IOException("Live flag capture is empty: " + liveFlagsFile);
        }

        LiveCapture capture = new LiveCapture();
        LiveSceneBlock current = null;
        List<String> lines = Files.readAllLines(liveFlagsFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++)
        {
            int lineNumber = i + 1;
            String line = lines.get(i).trim();
            if (line.isEmpty())
            {
                continue;
            }

            Matcher header = LIVE_SCENE_HEADER.matcher(line);
            if (header.matches())
            {
                finishLiveSceneBlock(capture, current);
                current = parseLiveSceneHeader(header, lineNumber);
                continue;
            }

            Matcher row = LIVE_DATA_ROW.matcher(line);
            if (row.matches())
            {
                if (current == null)
                {
                    throw new IOException("Live flag data row before first scene header at line " + lineNumber);
                }
                addLiveDataRow(capture, current, row, lineNumber);
                continue;
            }

            throw new IOException("Unrecognized live flag line " + lineNumber + ": " + line);
        }

        finishLiveSceneBlock(capture, current);
        if (capture.sceneBlocks == 0)
        {
            throw new IOException("Live flag capture contains no scene headers: " + liveFlagsFile);
        }
        return capture;
    }

    private static LiveSceneBlock parseLiveSceneHeader(Matcher header, int lineNumber) throws IOException
    {
        int baseX = parseLiveInt(header.group(1), "scene baseX", lineNumber);
        int baseY = parseLiveInt(header.group(2), "scene baseY", lineNumber);
        int plane = parseLiveInt(header.group(3), "scene plane", lineNumber);
        int size = parseLiveInt(header.group(4), "scene size", lineNumber);
        int covered = parseLiveInt(header.group(5), "scene covered", lineNumber);

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

        return new LiveSceneBlock(baseX, baseY, plane, size, covered, lineNumber);
    }

    private static void addLiveDataRow(
        LiveCapture capture,
        LiveSceneBlock block,
        Matcher row,
        int lineNumber
    )
        throws IOException
    {
        int x = parseLiveInt(row.group(1), "row x", lineNumber);
        int y = parseLiveInt(row.group(2), "row y", lineNumber);
        int plane = parseLiveInt(row.group(3), "row plane", lineNumber);
        boolean north = "1".equals(row.group(4));
        boolean east = "1".equals(row.group(5));
        String rawFlagsValue = row.group(6);
        boolean hasRawFlags = rawFlagsValue != null;
        int rawFlags = 0;
        if (hasRawFlags)
        {
            rawFlags = parseLiveInt(rawFlagsValue, "row raw flags", lineNumber);
        }

        if (plane != block.plane)
        {
            throw new IOException("Live flag row plane does not match scene at line " + lineNumber
                + ": row plane=" + plane + ", scene plane=" + block.plane);
        }
        if (!block.contains(x, y, plane))
        {
            throw new IOException("Live flag row outside exclusive covered bound at line " + lineNumber
                + ": " + x + "," + y + "," + plane + " not inside " + block.summary());
        }
        if (!north && !east)
        {
            throw new IOException("Live flag data row has no blocked edge at line " + lineNumber);
        }

        capture.rowsParsed++;
        if (hasRawFlags)
        {
            capture.rowsWithRawFlags++;
        }
        else
        {
            capture.rowsWithoutRawFlags++;
        }
        StoredLiveEdges previous = block.rows.put(
            tileKey(x, y, plane),
            new StoredLiveEdges(north, east, hasRawFlags, rawFlags)
        );
        if (previous != null)
        {
            capture.duplicateRows++;
            if (previous.north != north || previous.east != east)
            {
                capture.duplicateRowConflicts++;
            }
        }
    }

    private static int parseLiveInt(String value, String label, int lineNumber) throws IOException
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

    private static void finishLiveSceneBlock(LiveCapture capture, LiveSceneBlock block)
    {
        if (block == null)
        {
            return;
        }

        capture.sceneBlocks++;
        capture.sceneBlockGeometries.add(new LiveSceneBlockGeometry(
            block.baseX,
            block.baseY,
            block.plane,
            block.covered
        ));
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
                boolean hasRawFlags = row != null && row.hasRawFlags;
                int rawFlags = hasRawFlags ? row.rawFlags : 0;
                LiveTile tile = capture.tiles.get(key);
                if (tile == null)
                {
                    tile = new LiveTile(x, y, block.plane);
                    capture.tiles.put(key, tile);
                }
                tile.observe(north, east, hasRawFlags, rawFlags, capture);
            }
        }
    }

    private static List<ProofEdge> parseProofEdges(Path proofFile) throws IOException
    {
        if (!Files.isRegularFile(proofFile))
        {
            throw new IOException("Proof file missing: " + proofFile);
        }
        if (Files.size(proofFile) == 0)
        {
            throw new IOException("Proof file is empty: " + proofFile);
        }

        List<?> reflected = invokeProofParser(proofFile);
        List<ProofEdge> edges = new ArrayList<>(reflected.size());
        for (Object edge : reflected)
        {
            edges.add(new ProofEdge(
                readIntField(edge, "x"),
                readIntField(edge, "y"),
                readIntField(edge, "plane"),
                readCharField(edge, "direction")
            ));
        }
        return edges;
    }

    private static List<?> invokeProofParser(Path proofFile) throws IOException
    {
        try
        {
            return (List<?>) PROOF_PARSE_EDGES.invoke(null, proofFile);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot call ProofEdgeClassifier.parseProofEdges", e);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof IOException)
            {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            throw new IllegalStateException("ProofEdgeClassifier.parseProofEdges failed", cause);
        }
    }

    private static String firstOpenStyleAction(ObjectDefinition def)
    {
        if (def == null)
        {
            return null;
        }

        try
        {
            return (String) PROOF_FIRST_OPEN_STYLE_ACTION.invoke(null, def);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot call ProofEdgeClassifier.firstOpenStyleAction", e);
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
            throw new IllegalStateException("ProofEdgeClassifier.firstOpenStyleAction failed", cause);
        }
    }

    private static int readIntField(Object target, String name)
    {
        try
        {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Cannot read ProofEdgeClassifier.Edge." + name, e);
        }
    }

    private static char readCharField(Object target, String name)
    {
        try
        {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getChar(target);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Cannot read ProofEdgeClassifier.Edge." + name, e);
        }
    }

    private static Method findProofParseEdges()
    {
        try
        {
            Method method = ProofEdgeClassifier.class.getDeclaredMethod("parseProofEdges", Path.class);
            method.setAccessible(true);
            return method;
        }
        catch (NoSuchMethodException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method findProofFirstOpenStyleAction()
    {
        try
        {
            Method method = ProofEdgeClassifier.class.getDeclaredMethod(
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

    private static int regionId(int regionX, int regionY)
    {
        return (regionX << 8) | regionY;
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

    private static long tileKey(int x, int y, int plane)
    {
        return (((long) x) << 34) | (((long) y) << 4) | (plane & 0xFL);
    }

    private static String formatRegionId(int regionId)
    {
        return regionId + " (" + (regionId >> 8) + "_" + (regionId & 0xFF) + ")";
    }

    private enum Direction
    {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    private enum DangerousSplit
    {
        DOOR_CAPABLE,
        CONFLICTED,
        UNEXPLAINED
    }

    private enum InteriorBucket
    {
        UPPER,
        UNDER_STRUCTURE,
        OUTDOOR
    }

    private enum SceneryAdjacencyBucket
    {
        ADJ_SCENERY,
        ADJ_OTHER_IGNORED,
        NOT_ADJACENT
    }

    private enum IgnoredSolidityBucket
    {
        ADJ_SOLID_FLAGGED,
        ADJ_NONSOLID_ONLY
    }

    private enum IgnoredObjectFlag
    {
        INTERACT_TYPE_NONZERO,
        BLOCKS_PROJECTILE,
        OBSTRUCTS_GROUND
    }

    private static final class EmptyDirectionArray
    {
        private static final Direction[] HOLDER = new Direction[0];

        private EmptyDirectionArray()
        {
        }
    }

    private static final class BuildRequest
    {
        private final Path outputZip;
        private final Path liveFlagsFile;
        private final boolean allRegions;
        private final Set<Integer> regionIds;
        private final boolean defaultedRegions;
        private final boolean phase2SolidObjectBlocking;
        private final boolean phase3RoofBlocking;
        private final int roofLocTypeMask;
        private final Path mergeFrom;

        private BuildRequest(
            Path outputZip,
            Path liveFlagsFile,
            boolean allRegions,
            Set<Integer> regionIds,
            boolean defaultedRegions,
            boolean phase2SolidObjectBlocking,
            boolean phase3RoofBlocking,
            int roofLocTypeMask
        )
        {
            this(outputZip, liveFlagsFile, allRegions, regionIds, defaultedRegions,
                phase2SolidObjectBlocking, phase3RoofBlocking, roofLocTypeMask, null);
        }

        private BuildRequest(
            Path outputZip,
            Path liveFlagsFile,
            boolean allRegions,
            Set<Integer> regionIds,
            boolean defaultedRegions,
            boolean phase2SolidObjectBlocking,
            boolean phase3RoofBlocking,
            int roofLocTypeMask,
            Path mergeFrom
        )
        {
            this.mergeFrom = mergeFrom;
            this.roofLocTypeMask = roofLocTypeMask;
            this.outputZip = outputZip;
            this.liveFlagsFile = liveFlagsFile;
            this.allRegions = allRegions;
            this.regionIds = regionIds;
            this.defaultedRegions = defaultedRegions;
            this.phase2SolidObjectBlocking = phase2SolidObjectBlocking;
            this.phase3RoofBlocking = phase3RoofBlocking;
        }

        private BuildRequest withObjectBlockingDisabled()
        {
            /*
             * The gate baseline is "no object blocking at all", so both phases go off together.
             * Turning off only one would measure a mixture rather than a baseline.
             */
            return new BuildRequest(
                outputZip,
                liveFlagsFile,
                allRegions,
                regionIds,
                defaultedRegions,
                false,
                false,
                roofLocTypeMask,
                mergeFrom
            );
        }
    }

    private static final class BuildResult
    {
        private final TreeMap<String, BuiltRegion> regions;
        private final BuildStats stats;

        private BuildResult(TreeMap<String, BuiltRegion> regions, BuildStats stats)
        {
            this.regions = regions;
            this.stats = stats;
        }
    }

    private static final class BuildStats
    {
        private final TreeMap<Integer, Long> placementsByLocType = new TreeMap<>();
        private final long[] locType1PlacementsByOrientation = new long[LOC_TYPE_1_EDGES_BY_ORIENTATION.length];
        private final Set<Long> locType1Orientation3TileKeys = new HashSet<>();
        private final Set<Long> placementTileKeys = new HashSet<>();
        private final Set<Long> sceneryPlacementTileKeys = new HashSet<>();
        private final Set<Long> otherIgnoredTileKeys = new HashSet<>();
        private final Set<Long> interactTypeNonZeroTileKeys = new HashSet<>();
        private final Set<Long> blocksProjectileTileKeys = new HashSet<>();
        private final Set<Long> obstructsGroundTileKeys = new HashSet<>();
        private final Map<Long, Integer> doorCapableLocTypeByTile = new HashMap<>();
        private final Map<Long, Integer> ignoredLocTypeMaskByTile = new HashMap<>();
        private final TreeMap<String, Long> ignoredNonDecorFootprintHistogram = new TreeMap<>();
        private final List<DeferredEdge> deferredNeighbourEdges = new ArrayList<>();

        private long locType1EdgesBlockedTotal;
        private long locType1Orientation3Placements;
        private long locType1InvalidOrientationFallbacks;
        private long ignoredLocTypePlacements;
        private long ignoredNonDecorPlacements;
        private long ignoredNonDecorMissingDefinitionPlacements;
        private long ignoredNonDecorInteractType0Placements;
        private long ignoredNonDecorInteractType1Placements;
        private long ignoredNonDecorInteractType2Placements;
        private long ignoredNonDecorInteractTypeOtherPlacements;
        private long ignoredNonDecorFootprintLargerThanOneByOnePlacements;
        private long ignoredNonDecorBlocksProjectilePlacements;
        private long ignoredNonDecorObstructsGroundPlacements;
        private long ignoredNonDecorWallOrDoorPlacements;
        private long ignoredNonDecorBlockingMaskPlacements;
        private long phase2SolidObjectPlacements;
        private long phase2SolidObjectOpenStylePlacements;
        private long phase2SolidObjectMissingDefinitionSkipped;
        private long phase2SolidObjectFootprintHeldBackPlacements;
        private long doorCapableTileCollisions;
        private long terrainBlockedTiles;
        private long bridgeBranchTiles;
        private long outOfRegionNeighbourEdgesDeferred;
        private long outOfRegionNeighbourEdgesApplied;
        private long outOfRegionNeighbourSkips;
        private long totalEdgesMadePassable;
        private long doorEdgesWritten;
        private long totalRegionsBuilt;
        private final long[] phase3RoofPlacementsByPlane = new long[PLANE_COUNT];
        private long phase3RoofPlacements;
        private long phase3RoofOpenStylePlacements;
        private boolean phase2SolidObjectBlockingEnabled;
        private boolean phase3RoofBlockingEnabled;
        private int roofLocTypeMask = DEFAULT_ROOF_LOC_TYPE_MASK;
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

    private static final class BuiltRegion
    {
        private final String name;
        private final int regionId;
        private final RegionBits bits;

        private BuiltRegion(String name, int regionId, RegionBits bits)
        {
            this.name = name;
            this.regionId = regionId;
            this.bits = bits;
        }

        private byte[] encode()
        {
            byte[] bitsBytes = archiveFlags().toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(16 + bitsBytes.length);
            buffer.putInt(bits.minX);
            buffer.putInt(bits.minY);
            buffer.putInt(bits.maxX);
            buffer.putInt(bits.maxY);
            buffer.put(bitsBytes);
            return buffer.array();
        }

        private BitSet archiveFlags()
        {
            BitSet archive = new BitSet(archiveTotalBits(bits));
            for (int plane = 0; plane < PLANE_COUNT; plane++)
            {
                for (int y = bits.minY; y <= bits.maxY; y++)
                {
                    for (int x = bits.minX; x <= bits.maxX; x++)
                    {
                        if (archivePassable(bits, x, y, plane, FLAG_NORTH_PASSABLE, FLAG_NORTH_DOOR))
                        {
                            archive.set(archiveIndex(bits, x, y, plane, FLAG_NORTH_PASSABLE));
                        }
                        if (archivePassable(bits, x, y, plane, FLAG_EAST_PASSABLE, FLAG_EAST_DOOR))
                        {
                            archive.set(archiveIndex(bits, x, y, plane, FLAG_EAST_PASSABLE));
                        }
                    }
                }
            }
            return archive;
        }

        private long countPassableEdges()
        {
            return bits.countFlag(FLAG_NORTH_PASSABLE) + bits.countFlag(FLAG_EAST_PASSABLE);
        }

        private long countDoorEdges()
        {
            return bits.countFlag(FLAG_NORTH_DOOR) + bits.countFlag(FLAG_EAST_DOOR);
        }
    }

    /**
     * Collapses the builder's four flags onto the two the runtime reader understands.
     *
     * <p>markDoor clears PASSABLE and sets DOOR, and the archive carried only the PASSABLE flags -
     * so before this rule every door in the game was written to the shipped archive as a solid
     * wall. That seals buildings, and the cost is out of all proportion to the edge count: measured
     * 2026-08-12 in one 26-tile box around the Ardougne mansion, an all-region rebuild opened 501
     * edges and closed 28, yet those 28 were 14 matched door pairs and the only way out of the
     * mansion became a teleport. Live ground truth from the client says that walk is 54 tiles.
     *
     * <p>A shut door is not a wall, so a door edge is written passable. When a door-aware runtime
     * reader exists it can charge the tick that opening one costs; until then, free is far closer
     * to the truth than impassable.
     */
    private static boolean archivePassable(
        RegionBits bits, int x, int y, int plane, int passableFlag, int doorFlag)
    {
        return bits.flags.get(bits.index(x, y, plane, passableFlag))
            || bits.flags.get(bits.index(x, y, plane, doorFlag));
    }

    private static final class RegionBits
    {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;
        private final int width;
        private final int height;
        private final BitSet flags;
        private final BitSet solidEdges;

        private RegionBits(int minX, int minY, int maxX, int maxY)
        {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.width = maxX - minX + 1;
            this.height = maxY - minY + 1;
            this.flags = new BitSet(totalBits());
            this.solidEdges = new BitSet(PLANE_COUNT * width * height * STORED_EDGE_COUNT);
            initializePassable();
        }

        private int totalBits()
        {
            return PLANE_COUNT * width * height * FLAG_COUNT;
        }

        private void initializePassable()
        {
            for (int plane = 0; plane < PLANE_COUNT; plane++)
            {
                for (int y = minY; y <= maxY; y++)
                {
                    for (int x = minX; x <= maxX; x++)
                    {
                        flags.set(index(x, y, plane, FLAG_NORTH_PASSABLE));
                        flags.set(index(x, y, plane, FLAG_EAST_PASSABLE));
                    }
                }
            }
        }

        private boolean contains(int x, int y, int plane)
        {
            return plane >= 0 && plane < PLANE_COUNT
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY;
        }

        private void markSolidAllEdges(int x, int y, int plane, BuildStats stats)
        {
            markEdge(x, y, plane, Direction.NORTH, false, stats);
            markEdge(x, y, plane, Direction.EAST, false, stats);
            markEdge(x, y, plane, Direction.SOUTH, false, stats);
            markEdge(x, y, plane, Direction.WEST, false, stats);
        }

        private void markEdge(
            int x,
            int y,
            int plane,
            Direction direction,
            boolean openable,
            BuildStats stats
        )
        {
            StoredEdge edge = storedEdgeFor(x, y, plane, direction);
            if (edge == null)
            {
                return;
            }

            if (!contains(edge.x, edge.y, edge.plane))
            {
                stats.deferredNeighbourEdges.add(new DeferredEdge(edge, openable));
                stats.outOfRegionNeighbourEdgesDeferred++;
                return;
            }

            markStoredEdge(edge, openable);
        }

        private void markStoredEdge(StoredEdge edge, boolean openable)
        {
            if (openable)
            {
                markDoor(edge);
            }
            else
            {
                markSolid(edge);
            }
        }

        private StoredEdge storedEdgeFor(
            int x,
            int y,
            int plane,
            Direction direction
        )
        {
            if (plane < 0 || plane >= PLANE_COUNT)
            {
                return null;
            }

            switch (direction)
            {
                case NORTH:
                    return new StoredEdge(x, y, plane, FLAG_NORTH_PASSABLE, FLAG_NORTH_DOOR, 0);
                case EAST:
                    return new StoredEdge(x, y, plane, FLAG_EAST_PASSABLE, FLAG_EAST_DOOR, 1);
                case SOUTH:
                    return new StoredEdge(x, y - 1, plane, FLAG_NORTH_PASSABLE, FLAG_NORTH_DOOR, 0);
                case WEST:
                    return new StoredEdge(x - 1, y, plane, FLAG_EAST_PASSABLE, FLAG_EAST_DOOR, 1);
                default:
                    throw new IllegalArgumentException("Unhandled direction " + direction);
            }
        }

        private void markSolid(StoredEdge edge)
        {
            solidEdges.set(edgeIndex(edge));
            flags.clear(index(edge.x, edge.y, edge.plane, edge.passableFlag));
            flags.clear(index(edge.x, edge.y, edge.plane, edge.doorFlag));
        }

        private void markDoor(StoredEdge edge)
        {
            if (solidEdges.get(edgeIndex(edge)))
            {
                return;
            }
            flags.clear(index(edge.x, edge.y, edge.plane, edge.passableFlag));
            flags.set(index(edge.x, edge.y, edge.plane, edge.doorFlag));
        }

        private boolean isPassable(int x, int y, int plane, char direction)
        {
            int flag = direction == 'N' ? FLAG_NORTH_PASSABLE : FLAG_EAST_PASSABLE;
            return flags.get(index(x, y, plane, flag));
        }

        private boolean isDoor(int x, int y, int plane, char direction)
        {
            int flag = direction == 'N' ? FLAG_NORTH_DOOR : FLAG_EAST_DOOR;
            return flags.get(index(x, y, plane, flag));
        }

        private long countFlag(int flag)
        {
            long count = 0;
            for (int plane = 0; plane < PLANE_COUNT; plane++)
            {
                for (int y = minY; y <= maxY; y++)
                {
                    for (int x = minX; x <= maxX; x++)
                    {
                        if (flags.get(index(x, y, plane, flag)))
                        {
                            count++;
                        }
                    }
                }
            }
            return count;
        }

        private void assertPassableDoorExclusion()
        {
            for (int plane = 0; plane < PLANE_COUNT; plane++)
            {
                for (int y = minY; y <= maxY; y++)
                {
                    for (int x = minX; x <= maxX; x++)
                    {
                        if (flags.get(index(x, y, plane, FLAG_NORTH_PASSABLE))
                            && flags.get(index(x, y, plane, FLAG_NORTH_DOOR)))
                        {
                            throw new IllegalStateException(
                                "N passable and N door both set at " + x + "," + y + "," + plane
                            );
                        }
                        if (flags.get(index(x, y, plane, FLAG_EAST_PASSABLE))
                            && flags.get(index(x, y, plane, FLAG_EAST_DOOR)))
                        {
                            throw new IllegalStateException(
                                "E passable and E door both set at " + x + "," + y + "," + plane
                            );
                        }
                    }
                }
            }
        }

        private int index(int x, int y, int plane, int flag)
        {
            return (plane * width * height + (y - minY) * width + (x - minX)) * FLAG_COUNT + flag;
        }

        private int edgeIndex(StoredEdge edge)
        {
            return (edge.plane * width * height + (edge.y - minY) * width + (edge.x - minX))
                * STORED_EDGE_COUNT + edge.storedEdgeOrdinal;
        }
    }

    private static final class StoredEdge
    {
        private final int x;
        private final int y;
        private final int plane;
        private final int passableFlag;
        private final int doorFlag;
        private final int storedEdgeOrdinal;

        private StoredEdge(
            int x,
            int y,
            int plane,
            int passableFlag,
            int doorFlag,
            int storedEdgeOrdinal
        )
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.passableFlag = passableFlag;
            this.doorFlag = doorFlag;
            this.storedEdgeOrdinal = storedEdgeOrdinal;
        }
    }

    private static final class DeferredEdge
    {
        private final StoredEdge edge;
        private final boolean openable;

        private DeferredEdge(StoredEdge edge, boolean openable)
        {
            this.edge = edge;
            this.openable = openable;
        }
    }

    private static final class LiveCapture
    {
        private final Map<Long, LiveTile> tiles = new TreeMap<>();
        private final Set<Long> conflictingNorthTileKeys = new HashSet<>();
        private final Set<Long> conflictingEastTileKeys = new HashSet<>();
        private final List<LiveSceneBlockGeometry> sceneBlockGeometries = new ArrayList<>();

        private long sceneBlocks;
        private long rowsParsed;
        private long rowsWithRawFlags;
        private long rowsWithoutRawFlags;
        private long coveredTileObservations;
        private long duplicateRows;
        private long duplicateRowConflicts;
        private long conflictingNorthObservations;
        private long conflictingEastObservations;
    }

    private static final class LiveSceneBlock
    {
        private final int baseX;
        private final int baseY;
        private final int plane;
        private final int size;
        private final int covered;
        private final int lineNumber;
        private final Map<Long, StoredLiveEdges> rows = new TreeMap<>();

        private LiveSceneBlock(int baseX, int baseY, int plane, int size, int covered, int lineNumber)
        {
            this.baseX = baseX;
            this.baseY = baseY;
            this.plane = plane;
            this.size = size;
            this.covered = covered;
            this.lineNumber = lineNumber;
        }

        private boolean contains(int x, int y, int plane)
        {
            // covered is exclusive: the final included tile is base + covered - 1.
            return this.plane == plane
                && x >= baseX && x < baseX + covered
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

    private static final class LiveSceneBlockGeometry
    {
        private final int baseX;
        private final int baseY;
        private final int plane;
        private final int covered;

        private LiveSceneBlockGeometry(int baseX, int baseY, int plane, int covered)
        {
            this.baseX = baseX;
            this.baseY = baseY;
            this.plane = plane;
            this.covered = covered;
        }

        private boolean contains(int x, int y, int plane)
        {
            // Plane is part of containment; overlapping scenes on other floors are not observations.
            return this.plane == plane
                && x >= baseX && x < baseX + covered
                && y >= baseY && y < baseY + covered;
        }

        private int borderDistance(int x, int y)
        {
            int sx = x - baseX;
            int sy = y - baseY;
            int farX = covered - 1 - sx;
            int farY = covered - 1 - sy;
            return Math.min(Math.min(sx, sy), Math.min(farX, farY));
        }
    }

    private static final class BorderDistances
    {
        private final boolean contained;
        private final int minBorderDistance;
        private final int maxBorderDistance;

        private BorderDistances(int minBorderDistance, int maxBorderDistance)
        {
            this.contained = true;
            this.minBorderDistance = minBorderDistance;
            this.maxBorderDistance = maxBorderDistance;
        }

        private BorderDistances()
        {
            this.contained = false;
            this.minBorderDistance = -1;
            this.maxBorderDistance = -1;
        }

        private static BorderDistances notContained()
        {
            return new BorderDistances();
        }
    }

    private static final class BorderDistanceBucket
    {
        private final String label;
        private final int minDistance;
        private final int maxDistance;

        private BorderDistanceBucket(String label, int minDistance, int maxDistance)
        {
            this.label = label;
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
        }

        private boolean contains(int distance)
        {
            return distance >= minDistance && distance <= maxDistance;
        }
    }

    private static final class BorderBucketCounts
    {
        private long comparedEdges;
        private long dangerous;
        private long dangerousUnexplained;
    }

    private static final class BorderHistogram
    {
        private final BorderBucketCounts[] counts = new BorderBucketCounts[BORDER_DISTANCE_BUCKETS.length];

        private long bucketedComparedEdges;
        private long bucketedDangerousUnexplained;

        private BorderHistogram()
        {
            for (int i = 0; i < counts.length; i++)
            {
                counts[i] = new BorderBucketCounts();
            }
        }

        private void record(int distance, boolean dangerous, boolean dangerousUnexplained)
        {
            BorderBucketCounts bucket = counts[bucketIndex(distance)];
            bucket.comparedEdges++;
            bucketedComparedEdges++;
            if (dangerous)
            {
                bucket.dangerous++;
            }
            if (dangerousUnexplained)
            {
                bucket.dangerousUnexplained++;
                bucketedDangerousUnexplained++;
            }
        }

        private long comparedEdges(int maxDistance)
        {
            long total = 0L;
            for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
            {
                if (BORDER_DISTANCE_BUCKETS[i].minDistance <= maxDistance)
                {
                    total += counts[i].comparedEdges;
                }
            }
            return total;
        }

        private long dangerous(int maxDistance)
        {
            long total = 0L;
            for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
            {
                if (BORDER_DISTANCE_BUCKETS[i].minDistance <= maxDistance)
                {
                    total += counts[i].dangerous;
                }
            }
            return total;
        }

        private long dangerousUnexplained(int maxDistance)
        {
            long total = 0L;
            for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
            {
                if (BORDER_DISTANCE_BUCKETS[i].minDistance <= maxDistance)
                {
                    total += counts[i].dangerousUnexplained;
                }
            }
            return total;
        }

        private long comparedEdgesAtOrAbove(int minDistance)
        {
            long total = 0L;
            for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
            {
                if (BORDER_DISTANCE_BUCKETS[i].maxDistance >= minDistance)
                {
                    total += counts[i].comparedEdges;
                }
            }
            return total;
        }

        private long dangerousAtOrAbove(int minDistance)
        {
            long total = 0L;
            for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
            {
                if (BORDER_DISTANCE_BUCKETS[i].maxDistance >= minDistance)
                {
                    total += counts[i].dangerous;
                }
            }
            return total;
        }

        private int bucketIndex(int distance)
        {
            for (int i = 0; i < BORDER_DISTANCE_BUCKETS.length; i++)
            {
                if (BORDER_DISTANCE_BUCKETS[i].contains(distance))
                {
                    return i;
                }
            }
            throw new IllegalArgumentException("Border distance does not fit a bucket: " + distance);
        }
    }

    private static final class InteriorBucketCounts
    {
        private long comparedEdges;
        private long dangerous;
        private long dangerousUnexplained;

        private void record(boolean dangerous, boolean dangerousUnexplained)
        {
            comparedEdges++;
            if (dangerous)
            {
                this.dangerous++;
            }
            if (dangerousUnexplained)
            {
                this.dangerousUnexplained++;
            }
        }

        private void add(InteriorBucketCounts counts)
        {
            comparedEdges += counts.comparedEdges;
            dangerous += counts.dangerous;
            dangerousUnexplained += counts.dangerousUnexplained;
        }
    }

    private static final class OccupancyCensusCounts
    {
        private long comparedEdges;
        private long nearStructureEdges;
        private long notNearStructureEdges;

        private void record(boolean nearStructure)
        {
            comparedEdges++;
            if (nearStructure)
            {
                nearStructureEdges++;
            }
            else
            {
                notNearStructureEdges++;
            }
        }
    }

    private static final class InteriorMeasurement
    {
        private final InteriorBucketCounts[] bucketCounts =
            new InteriorBucketCounts[InteriorBucket.values().length];
        private final InteriorBucketCounts upperNearStructureCounts = new InteriorBucketCounts();
        private final InteriorBucketCounts upperOpenCounts = new InteriorBucketCounts();
        private final InteriorBucketCounts[] planeCounts = new InteriorBucketCounts[PLANE_COUNT];
        private final OccupancyCensusCounts[] occupancyPlaneCounts = new OccupancyCensusCounts[PLANE_COUNT];
        private final OccupancyCensusCounts[] occupancyCensusRegionPlaneCounts =
            new OccupancyCensusCounts[PLANE_COUNT];
        private final TreeMap<RegionPlaneKey, InteriorBucketCounts> regionPlaneCounts = new TreeMap<>();

        private InteriorMeasurement()
        {
            for (int i = 0; i < bucketCounts.length; i++)
            {
                bucketCounts[i] = new InteriorBucketCounts();
            }
            for (int i = 0; i < planeCounts.length; i++)
            {
                planeCounts[i] = new InteriorBucketCounts();
            }
            for (int i = 0; i < occupancyPlaneCounts.length; i++)
            {
                occupancyPlaneCounts[i] = new OccupancyCensusCounts();
            }
            for (int i = 0; i < occupancyCensusRegionPlaneCounts.length; i++)
            {
                occupancyCensusRegionPlaneCounts[i] = new OccupancyCensusCounts();
            }
        }

        private void record(
            InteriorBucket bucket,
            int regionId,
            int plane,
            boolean nearStructure,
            boolean dangerous,
            boolean dangerousUnexplained
        )
        {
            counts(bucket).record(dangerous, dangerousUnexplained);
            planeCounts[plane].record(dangerous, dangerousUnexplained);
            occupancyPlaneCounts[plane].record(nearStructure);
            if (regionId == OCCUPANCY_CENSUS_REGION_ID)
            {
                occupancyCensusRegionPlaneCounts[plane].record(nearStructure);
            }
            if (bucket == InteriorBucket.UPPER)
            {
                if (nearStructure)
                {
                    upperNearStructureCounts.record(dangerous, dangerousUnexplained);
                }
                else
                {
                    upperOpenCounts.record(dangerous, dangerousUnexplained);
                }
            }
            RegionPlaneKey key = new RegionPlaneKey(regionId, plane);
            InteriorBucketCounts regionPlane = regionPlaneCounts.get(key);
            if (regionPlane == null)
            {
                regionPlane = new InteriorBucketCounts();
                regionPlaneCounts.put(key, regionPlane);
            }
            regionPlane.record(dangerous, dangerousUnexplained);
        }

        private InteriorBucketCounts counts(InteriorBucket bucket)
        {
            return bucketCounts[bucket.ordinal()];
        }

        private long bucketComparedTotal()
        {
            long total = 0L;
            for (InteriorBucketCounts counts : bucketCounts)
            {
                total += counts.comparedEdges;
            }
            return total;
        }

        private long bucketDangerousTotal()
        {
            long total = 0L;
            for (InteriorBucketCounts counts : bucketCounts)
            {
                total += counts.dangerous;
            }
            return total;
        }

        private long bucketDangerousUnexplainedTotal()
        {
            long total = 0L;
            for (InteriorBucketCounts counts : bucketCounts)
            {
                total += counts.dangerousUnexplained;
            }
            return total;
        }

        private long planeComparedTotal()
        {
            long total = 0L;
            for (InteriorBucketCounts counts : planeCounts)
            {
                total += counts.comparedEdges;
            }
            return total;
        }
    }

    private static final class SceneryAdjacencyBucketCounts
    {
        private long comparedEdges;
        private long dangerous;
        private long dangerousUnexplained;
        private long overblock;
        private long agreeOpen;

        private void record(boolean dangerous, boolean dangerousUnexplained, boolean overblock)
        {
            /*
             * agreeOpen is left false on this overload. Only the per-locType measurement needs it,
             * and its zero must not be read as "no cost" anywhere else - the callers of this
             * overload do not display the column.
             */
            record(dangerous, dangerousUnexplained, overblock, false);
        }

        private void record(
            boolean dangerous,
            boolean dangerousUnexplained,
            boolean overblock,
            boolean agreeOpen
        )
        {
            comparedEdges++;
            if (dangerous)
            {
                this.dangerous++;
            }
            if (dangerousUnexplained)
            {
                this.dangerousUnexplained++;
            }
            if (overblock)
            {
                this.overblock++;
            }
            if (agreeOpen)
            {
                this.agreeOpen++;
            }
        }

        private void add(SceneryAdjacencyBucketCounts counts)
        {
            comparedEdges += counts.comparedEdges;
            dangerous += counts.dangerous;
            dangerousUnexplained += counts.dangerousUnexplained;
            overblock += counts.overblock;
            agreeOpen += counts.agreeOpen;
        }
    }

    private static final class Phase2Baseline
    {
        private final DangerousDirectionComparison dangerous;
        private final Comparison proof;

        private Phase2Baseline(DangerousDirectionComparison dangerous, Comparison proof)
        {
            this.dangerous = dangerous;
            this.proof = proof;
        }
    }

    private static final class IgnoredLocTypeMeasurement
    {
        private final SceneryAdjacencyBucketCounts[] byLocType =
            new SceneryAdjacencyBucketCounts[LOC_TYPE_MASK_BITS];
        private final SceneryAdjacencyBucketCounts notAdjacent = new SceneryAdjacencyBucketCounts();

        private IgnoredLocTypeMeasurement()
        {
            for (int i = 0; i < byLocType.length; i++)
            {
                byLocType[i] = new SceneryAdjacencyBucketCounts();
            }
        }

        private SceneryAdjacencyBucketCounts counts(int locType)
        {
            return byLocType[locType];
        }

        private void record(
            int mask,
            boolean dangerous,
            boolean dangerousUnexplained,
            boolean overblock,
            boolean agreeOpen
        )
        {
            if (mask == 0)
            {
                notAdjacent.record(dangerous, dangerousUnexplained, overblock, agreeOpen);
                return;
            }
            for (int locType = 0; locType < byLocType.length; locType++)
            {
                if ((mask & (1 << locType)) != 0)
                {
                    byLocType[locType].record(dangerous, dangerousUnexplained, overblock, agreeOpen);
                }
            }
        }
    }

    private static final class SceneryAdjacencyMeasurement
    {
        private final SceneryAdjacencyBucketCounts[] bucketCounts =
            new SceneryAdjacencyBucketCounts[SceneryAdjacencyBucket.values().length];
        private final SceneryAdjacencyBucketCounts[] ignoredSolidityCounts =
            new SceneryAdjacencyBucketCounts[IgnoredSolidityBucket.values().length];
        private final SceneryAdjacencyBucketCounts[] ignoredObjectFlagCounts =
            new SceneryAdjacencyBucketCounts[IgnoredObjectFlag.values().length];
        private final SceneryAdjacencyBucketCounts[][] planeBucketCounts =
            new SceneryAdjacencyBucketCounts[PLANE_COUNT][SceneryAdjacencyBucket.values().length];
        private final long[] planeComparedEdges = new long[PLANE_COUNT];

        private SceneryAdjacencyMeasurement()
        {
            for (int i = 0; i < bucketCounts.length; i++)
            {
                bucketCounts[i] = new SceneryAdjacencyBucketCounts();
            }
            for (int i = 0; i < ignoredSolidityCounts.length; i++)
            {
                ignoredSolidityCounts[i] = new SceneryAdjacencyBucketCounts();
            }
            for (int i = 0; i < ignoredObjectFlagCounts.length; i++)
            {
                ignoredObjectFlagCounts[i] = new SceneryAdjacencyBucketCounts();
            }
            for (int plane = 0; plane < planeBucketCounts.length; plane++)
            {
                for (int bucket = 0; bucket < planeBucketCounts[plane].length; bucket++)
                {
                    planeBucketCounts[plane][bucket] = new SceneryAdjacencyBucketCounts();
                }
            }
        }

        private void record(
            SceneryAdjacencyBucket bucket,
            int plane,
            boolean dangerous,
            boolean dangerousUnexplained,
            boolean overblock
        )
        {
            counts(bucket).record(dangerous, dangerousUnexplained, overblock);
            planeComparedEdges[plane]++;
            planeCounts(plane, bucket).record(dangerous, dangerousUnexplained, overblock);
        }

        private void recordIgnoredSolidity(
            IgnoredSolidityBucket bucket,
            boolean dangerous,
            boolean dangerousUnexplained,
            boolean overblock
        )
        {
            ignoredSolidityCounts(bucket).record(dangerous, dangerousUnexplained, overblock);
        }

        private void recordIgnoredObjectFlag(
            IgnoredObjectFlag flag,
            boolean dangerous,
            boolean dangerousUnexplained,
            boolean overblock
        )
        {
            ignoredObjectFlagCounts(flag).record(dangerous, dangerousUnexplained, overblock);
        }

        private SceneryAdjacencyBucketCounts counts(SceneryAdjacencyBucket bucket)
        {
            return bucketCounts[bucket.ordinal()];
        }

        private SceneryAdjacencyBucketCounts ignoredSolidityCounts(IgnoredSolidityBucket bucket)
        {
            return ignoredSolidityCounts[bucket.ordinal()];
        }

        private SceneryAdjacencyBucketCounts ignoredObjectFlagCounts(IgnoredObjectFlag flag)
        {
            return ignoredObjectFlagCounts[flag.ordinal()];
        }

        private SceneryAdjacencyBucketCounts planeCounts(int plane, SceneryAdjacencyBucket bucket)
        {
            return planeBucketCounts[plane][bucket.ordinal()];
        }

        private long bucketComparedTotal()
        {
            long total = 0L;
            for (SceneryAdjacencyBucketCounts counts : bucketCounts)
            {
                total += counts.comparedEdges;
            }
            return total;
        }

        private long bucketDangerousTotal()
        {
            long total = 0L;
            for (SceneryAdjacencyBucketCounts counts : bucketCounts)
            {
                total += counts.dangerous;
            }
            return total;
        }

        private long bucketDangerousUnexplainedTotal()
        {
            long total = 0L;
            for (SceneryAdjacencyBucketCounts counts : bucketCounts)
            {
                total += counts.dangerousUnexplained;
            }
            return total;
        }

        private long bucketOverblockTotal()
        {
            long total = 0L;
            for (SceneryAdjacencyBucketCounts counts : bucketCounts)
            {
                total += counts.overblock;
            }
            return total;
        }

        private long planeComparedTotal(int plane)
        {
            return planeComparedEdges[plane];
        }
    }

    private static final class RegionPlaneKey implements Comparable<RegionPlaneKey>
    {
        private final int regionId;
        private final int plane;

        private RegionPlaneKey(int regionId, int plane)
        {
            this.regionId = regionId;
            this.plane = plane;
        }

        @Override
        public int compareTo(RegionPlaneKey other)
        {
            int byRegion = Integer.compare(regionId, other.regionId);
            if (byRegion != 0)
            {
                return byRegion;
            }
            return Integer.compare(plane, other.plane);
        }
    }

    private static final class StoredLiveEdges
    {
        private final boolean north;
        private final boolean east;
        private final boolean hasRawFlags;
        private final int rawFlags;

        private StoredLiveEdges(boolean north, boolean east, boolean hasRawFlags, int rawFlags)
        {
            this.north = north;
            this.east = east;
            this.hasRawFlags = hasRawFlags;
            this.rawFlags = rawFlags;
        }
    }

    private static final class LiveTile
    {
        private final int x;
        private final int y;
        private final int plane;

        private boolean northSeen;
        private boolean eastSeen;
        private boolean rawFlagsSeen;
        private boolean northBlocked;
        private boolean eastBlocked;
        private int rawFlags;

        private LiveTile(int x, int y, int plane)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }

        private void observe(boolean north, boolean east, boolean hasRawFlags, int rawFlags, LiveCapture capture)
        {
            if (northSeen && northBlocked != north)
            {
                capture.conflictingNorthObservations++;
                capture.conflictingNorthTileKeys.add(key());
            }
            if (eastSeen && eastBlocked != east)
            {
                capture.conflictingEastObservations++;
                capture.conflictingEastTileKeys.add(key());
            }
            northSeen = true;
            eastSeen = true;
            northBlocked = north;
            eastBlocked = east;
            if (hasRawFlags)
            {
                rawFlagsSeen = true;
                this.rawFlags = rawFlags;
            }
        }

        private long key()
        {
            return tileKey(x, y, plane);
        }
    }

    private static final class ProofEdge
    {
        private final int x;
        private final int y;
        private final int plane;
        private final char direction;

        private ProofEdge(int x, int y, int plane, char direction)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.direction = direction;
        }

        private int regionId()
        {
            // Qualified deliberately: this no-arg method shadows the outer two-arg helper, so an
            // unqualified call resolves to itself and will not compile.
            return CollisionMapBuilder.regionId(
                Math.floorDiv(x, REGION_SIZE), Math.floorDiv(y, REGION_SIZE));
        }

        private String raw()
        {
            return x + "," + y + "," + plane + " " + direction;
        }
    }

    private static final class Comparison
    {
        private final int parsedEdges;
        private final List<String> stillBlockedExamples = new ArrayList<>();

        private long passableInV2;
        private long doorInV2;
        private long stillBlockedInV2;
        private long outsideBuiltRegions;

        private Comparison(int parsedEdges)
        {
            this.parsedEdges = parsedEdges;
        }

        private long fixedEdges()
        {
            return passableInV2 + doorInV2;
        }

        private long insideBuiltRegions()
        {
            return passableInV2 + doorInV2 + stillBlockedInV2;
        }
    }

    private static final class DangerousDirectionComparison
    {
        private final Path liveFlagsFile;
        private final LiveCapture live;
        private final boolean skipped;
        private final String skipReason;
        private final List<String> dangerousExamples = new ArrayList<>();
        private final List<String> dangerousUnexplainedExamples = new ArrayList<>();
        private final TreeMap<Integer, Long> dangerousDoorCapableByLocType = new TreeMap<>();
        private final BorderHistogram minBorderDistanceHistogram = new BorderHistogram();
        private final BorderHistogram maxBorderDistanceHistogram = new BorderHistogram();
        private final InteriorMeasurement interiorMeasurement = new InteriorMeasurement();
        private final SceneryAdjacencyMeasurement sceneryAdjacencyMeasurement =
            new SceneryAdjacencyMeasurement();
        private final IgnoredLocTypeMeasurement ignoredLocTypeMeasurement =
            new IgnoredLocTypeMeasurement();

        private long comparedEdges;
        private long borderExcludedEdges;
        private long borderExcludedDangerous;
        private long borderExcludedDangerousUnexplained;
        private long outsideBuiltRegions;
        private long noContainingBlockComparedEdges;
        private long disagreeingBorderDistanceComparedEdges;
        private long dangerous;
        private long dangerousDoorCapable;
        private long dangerousConflicted;
        private long dangerousUnexplained;
        private long dangerousDoorCapableAndConflicted;
        private long dangerousDoorCapableIgnoredLocType;
        private long doorShut;
        private long agreeBlocked;
        private long agreeOpen;
        private long overblock;
        private long overblockSourceTileBlockedRaw;
        private long orient3TileCount;
        private long orient3ComparedEdges;
        private long dangerousOrient3;

        private DangerousDirectionComparison(Path liveFlagsFile, LiveCapture live)
        {
            this.liveFlagsFile = liveFlagsFile;
            this.live = live;
            this.skipped = false;
            this.skipReason = "";
        }

        private DangerousDirectionComparison(Path liveFlagsFile, String skipReason)
        {
            this.liveFlagsFile = liveFlagsFile;
            this.live = new LiveCapture();
            this.skipped = true;
            this.skipReason = skipReason;
        }

        private static DangerousDirectionComparison skipped(Path liveFlagsFile, String skipReason)
        {
            return new DangerousDirectionComparison(liveFlagsFile, skipReason);
        }

        private long dangerousSplitTotal()
        {
            return dangerousDoorCapable + dangerousConflicted + dangerousUnexplained;
        }
    }
}
