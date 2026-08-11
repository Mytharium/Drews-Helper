package com.drewshelper.cachetools;

import com.drewshelper.routing.DrewsHelperCollisionMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;

/**
 * Measures which shipped collision-map edge is blocked by each wall placement shape.
 *
 * <p>The v2 builder must not inherit the old orientation assumptions, so this probe treats
 * locType and orientation as grouping fields only. It reads cache placements, reads the shipped
 * {@link DrewsHelperCollisionMap}, and reports the observed N/E/S/W block rates against a
 * no-wall baseline.
 *
 * <p>Run it with {@code gradlew.bat probeLocTypeShapes}. Optional args: [cacheDir] [projectDir].
 */
public final class LocTypeShapeProbe
{
    /** Wall locTypes being measured; no edge rule is implied by membership in this list. */
    private static final int[] WALL_LOC_TYPES = {0, 1, 2, 3, 9};

    /**
     * The cache stores map archives unencrypted, so a zero key means "do not decrypt" and
     * everything parses. This is the same cache-opening path used by CacheAccessPointDumper.
     */
    private static final KeyProvider ZERO_KEYS = regionId -> new int[]{0, 0, 0, 0};

    private static final int REGION_SIZE = 64;
    private static final int MIN_REGION_X = 0;
    private static final int MAX_REGION_X = 255;
    private static final int MIN_REGION_Y = 0;
    private static final int MAX_REGION_Y = 255;
    private static final int PLANE_COUNT = 4;

    private static final int BASELINE_SAMPLE_LIMIT = 250000;
    private static final long BASELINE_SAMPLE_SEED = 0x5A17E5EEDL;

    private static final int MIN_CLEAR_BAR_SAMPLES = 100;
    private static final double DECISIVE_LIFT_POINTS = 30.0;

    private static final Method DUMPER_FIRST_MOVEMENT_OP = findDumperFirstMovementOp();

    private LocTypeShapeProbe()
    {
    }

    public static void main(String[] args) throws IOException
    {
        File cacheDir = args.length > 0
            ? new File(args[0])
            : new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");
        Path project = Paths.get(args.length > 1 ? args[1] : System.getProperty("user.dir"));

        if (!cacheDir.isDirectory())
        {
            throw new IOException("No OSRS cache at " + cacheDir + " - pass the path as the first argument.");
        }

        Path collisionZip = project.resolve("src/main/resources/collision-map.zip");
        Path outFile = project.resolve("tools/loctype-shape-probe.txt");

        ShippedRegions shippedRegions = loadShippedRegions(collisionZip);
        DrewsHelperCollisionMap map = DrewsHelperCollisionMap.loadDefault();

        Store store = new Store(cacheDir);
        store.load();
        try
        {
            ScanResult scan = scanPlacements(store, shippedRegions, map);
            BaselineSample baseline = sampleNoWallBaseline(
                map,
                scan.coveredCacheRegions,
                scan.wallTilesInCoveredRegions
            );
            String report = buildReport(cacheDir, project, shippedRegions, scan, baseline);

            Files.createDirectories(outFile.getParent());
            Files.write(outFile, report.getBytes(StandardCharsets.UTF_8));
            System.out.print(report);
        }
        finally
        {
            store.close();
        }
    }

    private static ShippedRegions loadShippedRegions(Path zip) throws IOException
    {
        if (!Files.isRegularFile(zip))
        {
            throw new IOException("Missing shipped collision map: " + zip);
        }

        Set<String> names = new HashSet<>();
        List<RegionCoord> coords = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip.toFile())))
        {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    continue;
                }

                RegionCoord coord = RegionCoord.parse(entry.getName());
                if (coord == null)
                {
                    continue;
                }
                if (names.add(coord.name))
                {
                    coords.add(coord);
                }
            }
        }

        coords.sort(Comparator
            .comparingInt((RegionCoord coord) -> coord.regionX)
            .thenComparingInt(coord -> coord.regionY));
        return new ShippedRegions(names, coords);
    }

    private static ScanResult scanPlacements(
        Store store,
        ShippedRegions shippedRegions,
        DrewsHelperCollisionMap map
    )
        throws IOException
    {
        ObjectManager manager = new ObjectManager(store);
        manager.load();
        Map<Integer, ObjectDefinition> objects = new HashMap<>();
        for (ObjectDefinition def : manager.getObjects())
        {
            objects.put(def.getId(), def);
        }

        RegionLoader loader = new RegionLoader(store, ZERO_KEYS);
        ScanResult result = new ScanResult();
        Set<String> coveredCacheRegionNames = new TreeSet<>();

        for (int rx = MIN_REGION_X; rx <= MAX_REGION_X; rx++)
        {
            for (int ry = MIN_REGION_Y; ry <= MAX_REGION_Y; ry++)
            {
                int regionId = (rx << 8) | ry;
                LocationsDefinition locations;
                try
                {
                    locations = loader.loadLocDef(regionId);
                }
                catch (Exception e)
                {
                    continue;
                }

                if (locations == null || locations.getLocations() == null)
                {
                    continue;
                }

                result.cacheRegionsLoaded++;
                String regionName = rx + "_" + ry;
                boolean coveredRegion = shippedRegions.names.contains(regionName);
                if (coveredRegion && coveredCacheRegionNames.add(regionName))
                {
                    result.coveredCacheRegions.add(new RegionCoord(rx, ry));
                }

                int baseX = rx * REGION_SIZE;
                int baseY = ry * REGION_SIZE;
                for (Location location : locations.getLocations())
                {
                    if (!isWallPlacement(location.getType()))
                    {
                        continue;
                    }

                    result.placementsSeen++;
                    if (!coveredRegion)
                    {
                        result.skippedUncoveredRegion++;
                        continue;
                    }

                    int x = baseX + location.getPosition().getX();
                    int y = baseY + location.getPosition().getY();
                    int plane = location.getPosition().getZ();
                    if (plane < 0 || plane >= PLANE_COUNT)
                    {
                        result.skippedInvalidPlane++;
                        continue;
                    }

                    ObjectDefinition def = objects.get(location.getId());
                    WallPlacement placement = new WallPlacement(
                        x,
                        y,
                        plane,
                        location.getType(),
                        location.getOrientation(),
                        isOpenableByDumper(def)
                    );
                    long tileKey = key(x, y, plane);
                    result.wallTilesInCoveredRegions.add(tileKey);
                    result.placementsByTile.computeIfAbsent(tileKey, ignored -> new ArrayList<>())
                        .add(placement);
                }
            }
        }

        for (List<WallPlacement> placements : result.placementsByTile.values())
        {
            if (placements.size() != 1)
            {
                result.multiplePlacementTiles++;
                result.skippedMultiplePlacements += placements.size();
                continue;
            }

            WallPlacement placement = placements.get(0);
            GroupKey groupKey = new GroupKey(
                placement.locType,
                placement.orientation,
                placement.openable
            );
            DirectionStats stats = result.groups.computeIfAbsent(groupKey, ignored -> new DirectionStats());
            stats.add(readBlockedEdges(map, placement.x, placement.y, placement.plane));
        }

        return result;
    }

    private static BaselineSample sampleNoWallBaseline(
        DrewsHelperCollisionMap map,
        List<RegionCoord> regions,
        Set<Long> wallTiles
    )
    {
        BaselineSample sample = new BaselineSample();
        sample.regionCount = regions.size();
        sample.limit = BASELINE_SAMPLE_LIMIT;
        sample.seed = BASELINE_SAMPLE_SEED;
        if (regions.isEmpty())
        {
            return sample;
        }

        Random random = new Random(BASELINE_SAMPLE_SEED);
        Set<Long> selected = new HashSet<>(BASELINE_SAMPLE_LIMIT * 2);
        long maxAttempts = BASELINE_SAMPLE_LIMIT * 20L;

        while (sample.stats.samples < BASELINE_SAMPLE_LIMIT && sample.attempts < maxAttempts)
        {
            sample.attempts++;
            RegionCoord region = regions.get(random.nextInt(regions.size()));
            int x = region.regionX * REGION_SIZE + random.nextInt(REGION_SIZE);
            int y = region.regionY * REGION_SIZE + random.nextInt(REGION_SIZE);
            int plane = random.nextInt(PLANE_COUNT);
            long tileKey = key(x, y, plane);

            if (wallTiles.contains(tileKey))
            {
                sample.rejectedWallTiles++;
                continue;
            }
            if (!selected.add(tileKey))
            {
                sample.rejectedDuplicates++;
                continue;
            }

            sample.stats.add(readBlockedEdges(map, x, y, plane));
        }

        return sample;
    }

    private static String buildReport(
        File cacheDir,
        Path project,
        ShippedRegions shippedRegions,
        ScanResult scan,
        BaselineSample baseline
    )
    {
        StringBuilder report = new StringBuilder();
        report.append("locType shape probe").append('\n');
        report.append("cache: ").append(cacheDir).append('\n');
        report.append("project: ").append(project).append('\n');
        report.append('\n');
        appendTotals(report, shippedRegions, scan);
        report.append('\n');
        appendBaseline(report, baseline);
        report.append('\n');
        appendMainTable(report, scan);
        report.append('\n');
        appendClearBarRules(report, scan, baseline.stats);
        report.append('\n');
        appendUnknownRules(report, scan, baseline.stats);
        report.append('\n');
        appendReading(report, scan, baseline.stats);
        return report.toString();
    }

    private static void appendTotals(
        StringBuilder report,
        ShippedRegions shippedRegions,
        ScanResult scan
    )
    {
        report.append("totals:").append('\n');
        report.append("  shipped-map regions: ").append(shippedRegions.coords.size()).append('\n');
        report.append("  cache regions loaded: ").append(scan.cacheRegionsLoaded).append('\n');
        report.append("  covered cache regions sampled: ").append(scan.coveredCacheRegions.size())
            .append('\n');
        report.append("  placements seen: ").append(scan.placementsSeen).append('\n');
        report.append("  sampled: ").append(scan.sampledPlacements()).append('\n');
        report.append("  skipped-uncovered-region: ").append(scan.skippedUncoveredRegion)
            .append('\n');
        report.append("  skipped-multiple-placements: ")
            .append(scan.skippedMultiplePlacements)
            .append(" placements across ")
            .append(scan.multiplePlacementTiles)
            .append(" tiles").append('\n');
        if (scan.skippedInvalidPlane > 0)
        {
            report.append("  skipped-invalid-plane: ").append(scan.skippedInvalidPlane).append('\n');
        }
    }

    private static void appendBaseline(StringBuilder report, BaselineSample baseline)
    {
        report.append("null baseline over no-wall tiles:").append('\n');
        report.append("  sampling: deterministic random rejection sample from covered cache regions; ")
            .append("cap=").append(baseline.limit)
            .append(", seed=0x").append(Long.toHexString(baseline.seed).toUpperCase(Locale.ROOT))
            .append(", regions=").append(baseline.regionCount)
            .append(", attempts=").append(baseline.attempts)
            .append(", rejected-wall-tiles=").append(baseline.rejectedWallTiles)
            .append(", rejected-duplicates=").append(baseline.rejectedDuplicates)
            .append('\n');
        report.append(String.format(Locale.ROOT, "  %-10s %8s %8s %8s %8s%n",
            "samples", "N%", "E%", "S%", "W%"));
        appendStatsRow(report, baseline.stats);
    }

    private static void appendMainTable(StringBuilder report, ScanResult scan)
    {
        report.append("main table:").append('\n');
        report.append(String.format(Locale.ROOT,
            "  %7s %7s %8s %8s %8s %8s %8s %8s%n",
            "locType", "orient", "openable", "samples", "N%", "E%", "S%", "W%"));
        if (scan.groups.isEmpty())
        {
            report.append("  (none)").append('\n');
            return;
        }

        for (Map.Entry<GroupKey, DirectionStats> entry : scan.groups.entrySet())
        {
            GroupKey key = entry.getKey();
            DirectionStats stats = entry.getValue();
            report.append(String.format(Locale.ROOT,
                "  %7d %7d %8s %8d %8s %8s %8s %8s%n",
                key.locType,
                key.orientation,
                key.openable ? "yes" : "no",
                stats.samples,
                stats.percent(Direction.NORTH),
                stats.percent(Direction.EAST),
                stats.percent(Direction.SOUTH),
                stats.percent(Direction.WEST)));
        }
    }

    private static void appendClearBarRules(
        StringBuilder report,
        ScanResult scan,
        DirectionStats baseline
    )
    {
        report.append("rules that clear the bar:").append('\n');
        report.append("  thresholds: samples >= ").append(MIN_CLEAR_BAR_SAMPLES)
            .append("; a direction clears when its blocked-rate is at least ")
            .append(formatPoints(DECISIVE_LIFT_POINTS))
            .append(" percentage points above that same direction in the null baseline.")
            .append('\n');

        boolean any = false;
        for (Map.Entry<GroupKey, DirectionStats> entry : scan.groups.entrySet())
        {
            RuleAssessment assessment = assessRule(entry.getValue(), baseline);
            if (!assessment.clears)
            {
                continue;
            }

            any = true;
            appendRuleLine(report, entry.getKey(), entry.getValue(), assessment);
        }

        if (!any)
        {
            report.append("  (none)").append('\n');
        }
    }

    private static void appendUnknownRules(
        StringBuilder report,
        ScanResult scan,
        DirectionStats baseline
    )
    {
        report.append("rules that do NOT clear the bar, so the builder must treat them as UNKNOWN:")
            .append('\n');
        boolean any = false;
        for (Map.Entry<GroupKey, DirectionStats> entry : scan.groups.entrySet())
        {
            RuleAssessment assessment = assessRule(entry.getValue(), baseline);
            if (assessment.clears)
            {
                continue;
            }

            any = true;
            report.append("  locType ").append(entry.getKey().locType)
                .append(" orient ").append(entry.getKey().orientation)
                .append(" openable ").append(entry.getKey().openable ? "yes" : "no")
                .append(" samples ").append(entry.getValue().samples)
                .append("; best ").append(assessment.bestDirection.label)
                .append('=').append(entry.getValue().percent(assessment.bestDirection))
                .append(" vs baseline ").append(baseline.percent(assessment.bestDirection))
                .append(" (lift ").append(formatSignedPoints(assessment.bestLift)).append(')');
            if (entry.getValue().samples < MIN_CLEAR_BAR_SAMPLES)
            {
                report.append("; sample count below threshold");
            }
            if (assessment.bestLift < DECISIVE_LIFT_POINTS)
            {
                report.append("; lift below threshold");
            }
            report.append('\n');
        }

        if (!any)
        {
            report.append("  (none)").append('\n');
        }
    }

    private static void appendRuleLine(
        StringBuilder report,
        GroupKey key,
        DirectionStats stats,
        RuleAssessment assessment
    )
    {
        report.append("  locType ").append(key.locType)
            .append(" orient ").append(key.orientation)
            .append(" openable ").append(key.openable ? "yes" : "no")
            .append(" samples ").append(stats.samples)
            .append("; clear directions: ");

        boolean first = true;
        for (Direction direction : Direction.values())
        {
            if (!assessment.clearedDirections.contains(direction))
            {
                continue;
            }
            if (!first)
            {
                report.append(", ");
            }
            first = false;
            report.append(direction.label)
                .append('=').append(stats.percent(direction))
                .append(" (lift ").append(formatSignedPoints(assessment.lift(direction))).append(')');
        }
        report.append('\n');
    }

    private static void appendReading(
        StringBuilder report,
        ScanResult scan,
        DirectionStats baseline
    )
    {
        Map<Integer, LocTypeReading> byLocType = new TreeMap<>();
        Map<LocOrientationKey, OpenablePair> pairs = new TreeMap<>();

        for (Map.Entry<GroupKey, DirectionStats> entry : scan.groups.entrySet())
        {
            RuleAssessment assessment = assessRule(entry.getValue(), baseline);
            byLocType.computeIfAbsent(entry.getKey().locType, ignored -> new LocTypeReading())
                .add(assessment);
            pairs.computeIfAbsent(
                new LocOrientationKey(entry.getKey().locType, entry.getKey().orientation),
                ignored -> new OpenablePair()
            ).put(entry.getKey().openable, assessment);
        }

        report.append("plain-English reading:").append('\n');
        appendLocTypeReading(report, byLocType);
        appendOpenableReading(report, pairs);
    }

    private static void appendLocTypeReading(
        StringBuilder report,
        Map<Integer, LocTypeReading> byLocType
    )
    {
        if (byLocType.isEmpty())
        {
            report.append("  No locType had a sampled single-placement row. The builder has no measured ")
                .append("shape rule to use.").append('\n');
            return;
        }

        List<String> clean = new ArrayList<>();
        List<String> mixed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (Map.Entry<Integer, LocTypeReading> entry : byLocType.entrySet())
        {
            LocTypeReading reading = entry.getValue();
            if (reading.sizableGroups == 0)
            {
                unknown.add(entry.getKey() + " (no group reached the sample threshold)");
            }
            else if (reading.clearGroups == reading.sizableGroups)
            {
                clean.add(String.valueOf(entry.getKey()));
            }
            else if (reading.clearGroups > 0)
            {
                mixed.add(entry.getKey() + " (" + reading.clearGroups + "/"
                    + reading.sizableGroups + " sizable groups cleared)");
            }
            else
            {
                unknown.add(entry.getKey() + " (0/" + reading.sizableGroups
                    + " sizable groups cleared)");
            }
        }

        report.append("  Clean locTypes: ").append(formatList(clean)).append('.').append('\n');
        report.append("  Mixed locTypes: ").append(formatList(mixed)).append('.').append('\n');
        report.append("  Unknown locTypes: ").append(formatList(unknown)).append('.').append('\n');
    }

    private static void appendOpenableReading(
        StringBuilder report,
        Map<LocOrientationKey, OpenablePair> pairs
    )
    {
        int comparable = 0;
        int same = 0;
        List<String> different = new ArrayList<>();

        for (Map.Entry<LocOrientationKey, OpenablePair> entry : pairs.entrySet())
        {
            OpenablePair pair = entry.getValue();
            if (!pair.comparable())
            {
                continue;
            }

            comparable++;
            if (pair.openable.clearedDirections.equals(pair.solid.clearedDirections))
            {
                same++;
            }
            else
            {
                different.add("locType " + entry.getKey().locType + " orient "
                    + entry.getKey().orientation + " solid="
                    + pair.solid.directionList() + " openable="
                    + pair.openable.directionList());
            }
        }

        if (comparable == 0)
        {
            report.append("  Openable vs solid: no locType/orientation pair had both rows above the ")
                .append("sample threshold, so this run cannot claim a measured door difference.")
                .append('\n');
        }
        else if (different.isEmpty())
        {
            report.append("  Openable vs solid: matched in all ").append(comparable)
                .append(" comparable locType/orientation pairs. Doors did not need a separate ")
                .append("shape rule in this measurement.").append('\n');
        }
        else
        {
            report.append("  Openable vs solid: matched in ").append(same).append('/')
                .append(comparable).append(" comparable pairs and differed in ")
                .append(different.size()).append(": ")
                .append(String.join("; ", different)).append('.').append('\n');
        }
    }

    private static RuleAssessment assessRule(DirectionStats stats, DirectionStats baseline)
    {
        RuleAssessment assessment = new RuleAssessment();
        assessment.sampledEnough = stats.samples >= MIN_CLEAR_BAR_SAMPLES;

        for (Direction direction : Direction.values())
        {
            double lift = stats.rate(direction) - baseline.rate(direction);
            assessment.lifts.put(direction, lift);
            if (lift > assessment.bestLift || assessment.bestDirection == null)
            {
                assessment.bestLift = lift;
                assessment.bestDirection = direction;
            }
            if (assessment.sampledEnough && lift >= DECISIVE_LIFT_POINTS)
            {
                assessment.clearedDirections.add(direction);
            }
        }

        assessment.clears = !assessment.clearedDirections.isEmpty();
        return assessment;
    }

    private static BlockedEdges readBlockedEdges(
        DrewsHelperCollisionMap map,
        int x,
        int y,
        int plane
    )
    {
        return new BlockedEdges(
            !map.canMoveNorth(x, y, plane),
            !map.canMoveEast(x, y, plane),
            !map.canMoveSouth(x, y, plane),
            !map.canMoveWest(x, y, plane)
        );
    }

    private static void appendStatsRow(StringBuilder report, DirectionStats stats)
    {
        report.append(String.format(Locale.ROOT, "  %10d %8s %8s %8s %8s%n",
            stats.samples,
            stats.percent(Direction.NORTH),
            stats.percent(Direction.EAST),
            stats.percent(Direction.SOUTH),
            stats.percent(Direction.WEST)));
    }

    private static String formatList(List<String> values)
    {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String formatSignedPoints(double points)
    {
        return (points >= 0.0 ? "+" : "") + formatPoints(points) + "pp";
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

    /**
     * Delegates to the dumper's already-audited movement-action test. This probe is constrained
     * to a new class only, so reflection is the least-drifty way to keep "openable" identical
     * without extracting a helper into CacheAccessPointDumper.
     */
    private static boolean isOpenableByDumper(ObjectDefinition def)
    {
        return firstMovementOpByDumper(def) != null;
    }

    private static String firstMovementOpByDumper(ObjectDefinition def)
    {
        if (def == null)
        {
            return null;
        }

        try
        {
            return (String) DUMPER_FIRST_MOVEMENT_OP.invoke(null, def);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot call CacheAccessPointDumper.firstMovementOp", e);
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
            throw new IllegalStateException("CacheAccessPointDumper.firstMovementOp failed", cause);
        }
    }

    private static Method findDumperFirstMovementOp()
    {
        try
        {
            Method method = CacheAccessPointDumper.class.getDeclaredMethod(
                "firstMovementOp",
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

    private static long key(int x, int y, int plane)
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

    private static final class ShippedRegions
    {
        private final Set<String> names;
        private final List<RegionCoord> coords;

        private ShippedRegions(Set<String> names, List<RegionCoord> coords)
        {
            this.names = names;
            this.coords = coords;
        }
    }

    private static final class RegionCoord
    {
        private final int regionX;
        private final int regionY;
        private final String name;

        private RegionCoord(int regionX, int regionY)
        {
            this.regionX = regionX;
            this.regionY = regionY;
            this.name = regionX + "_" + regionY;
        }

        private static RegionCoord parse(String entryName)
        {
            int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
            String leaf = slash < 0 ? entryName : entryName.substring(slash + 1);
            int separator = leaf.indexOf('_');
            if (separator <= 0 || separator == leaf.length() - 1)
            {
                return null;
            }

            try
            {
                int regionX = Integer.parseInt(leaf.substring(0, separator));
                int regionY = Integer.parseInt(leaf.substring(separator + 1));
                return new RegionCoord(regionX, regionY);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
    }

    private static final class ScanResult
    {
        private final Map<Long, List<WallPlacement>> placementsByTile = new HashMap<>();
        private final Set<Long> wallTilesInCoveredRegions = new HashSet<>();
        private final List<RegionCoord> coveredCacheRegions = new ArrayList<>();
        private final Map<GroupKey, DirectionStats> groups = new TreeMap<>();

        private int cacheRegionsLoaded;
        private long placementsSeen;
        private long skippedUncoveredRegion;
        private long skippedMultiplePlacements;
        private long multiplePlacementTiles;
        private long skippedInvalidPlane;

        private long sampledPlacements()
        {
            long total = 0;
            for (DirectionStats stats : groups.values())
            {
                total += stats.samples;
            }
            return total;
        }
    }

    private static final class WallPlacement
    {
        private final int x;
        private final int y;
        private final int plane;
        private final int locType;
        private final int orientation;
        private final boolean openable;

        private WallPlacement(
            int x,
            int y,
            int plane,
            int locType,
            int orientation,
            boolean openable
        )
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.locType = locType;
            this.orientation = orientation;
            this.openable = openable;
        }
    }

    private static final class GroupKey implements Comparable<GroupKey>
    {
        private final int locType;
        private final int orientation;
        private final boolean openable;

        private GroupKey(int locType, int orientation, boolean openable)
        {
            this.locType = locType;
            this.orientation = orientation;
            this.openable = openable;
        }

        @Override
        public int compareTo(GroupKey other)
        {
            int byLocType = Integer.compare(locType, other.locType);
            if (byLocType != 0)
            {
                return byLocType;
            }

            int byOrientation = Integer.compare(orientation, other.orientation);
            if (byOrientation != 0)
            {
                return byOrientation;
            }

            return Boolean.compare(openable, other.openable);
        }
    }

    private static final class LocOrientationKey implements Comparable<LocOrientationKey>
    {
        private final int locType;
        private final int orientation;

        private LocOrientationKey(int locType, int orientation)
        {
            this.locType = locType;
            this.orientation = orientation;
        }

        @Override
        public int compareTo(LocOrientationKey other)
        {
            int byLocType = Integer.compare(locType, other.locType);
            if (byLocType != 0)
            {
                return byLocType;
            }
            return Integer.compare(orientation, other.orientation);
        }
    }

    private static final class DirectionStats
    {
        private long samples;
        private long northBlocked;
        private long eastBlocked;
        private long southBlocked;
        private long westBlocked;

        private void add(BlockedEdges blocked)
        {
            samples++;
            if (blocked.north)
            {
                northBlocked++;
            }
            if (blocked.east)
            {
                eastBlocked++;
            }
            if (blocked.south)
            {
                southBlocked++;
            }
            if (blocked.west)
            {
                westBlocked++;
            }
        }

        private double rate(Direction direction)
        {
            if (samples == 0)
            {
                return 0.0;
            }
            return blocked(direction) * 100.0 / samples;
        }

        private String percent(Direction direction)
        {
            return String.format(Locale.ROOT, "%.1f%%", rate(direction));
        }

        private long blocked(Direction direction)
        {
            switch (direction)
            {
                case NORTH:
                    return northBlocked;
                case EAST:
                    return eastBlocked;
                case SOUTH:
                    return southBlocked;
                case WEST:
                    return westBlocked;
                default:
                    throw new IllegalArgumentException("Unhandled direction " + direction);
            }
        }
    }

    private static final class BlockedEdges
    {
        private final boolean north;
        private final boolean east;
        private final boolean south;
        private final boolean west;

        private BlockedEdges(boolean north, boolean east, boolean south, boolean west)
        {
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
        }
    }

    private static final class BaselineSample
    {
        private final DirectionStats stats = new DirectionStats();

        private int regionCount;
        private int limit;
        private long seed;
        private long attempts;
        private long rejectedWallTiles;
        private long rejectedDuplicates;
    }

    private static final class RuleAssessment
    {
        private final Set<Direction> clearedDirections = new TreeSet<>();
        private final Map<Direction, Double> lifts = new HashMap<>();

        private boolean sampledEnough;
        private boolean clears;
        private Direction bestDirection;
        private double bestLift = Double.NEGATIVE_INFINITY;

        private double lift(Direction direction)
        {
            Double lift = lifts.get(direction);
            return lift == null ? 0.0 : lift;
        }

        private String directionList()
        {
            if (clearedDirections.isEmpty())
            {
                return "UNKNOWN";
            }

            List<String> labels = new ArrayList<>();
            for (Direction direction : clearedDirections)
            {
                labels.add(direction.label);
            }
            return String.join("", labels);
        }
    }

    private static final class LocTypeReading
    {
        private int sizableGroups;
        private int clearGroups;

        private void add(RuleAssessment assessment)
        {
            if (!assessment.sampledEnough)
            {
                return;
            }
            sizableGroups++;
            if (assessment.clears)
            {
                clearGroups++;
            }
        }
    }

    private static final class OpenablePair
    {
        private RuleAssessment solid;
        private RuleAssessment openable;

        private void put(boolean isOpenable, RuleAssessment assessment)
        {
            if (isOpenable)
            {
                openable = assessment;
            }
            else
            {
                solid = assessment;
            }
        }

        private boolean comparable()
        {
            return solid != null && openable != null
                && solid.sampledEnough && openable.sampledEnough;
        }
    }
}
