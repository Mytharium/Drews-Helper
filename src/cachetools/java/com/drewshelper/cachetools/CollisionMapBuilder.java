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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

/**
 * Builds the v2 per-region edge map from the OSRS cache.
 *
 * <p>The shipped v1 map remains untouched. This tool writes a new format-compatible archive whose
 * extra door flags are ignored by old callers and available to a future door-aware reader.
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
    private static final int STORED_EDGE_COUNT = 2;

    private static final int FLAG_NORTH_PASSABLE = 0;
    private static final int FLAG_EAST_PASSABLE = 1;
    private static final int FLAG_NORTH_DOOR = 2;
    private static final int FLAG_EAST_DOOR = 3;

    private static final String DEFAULT_ZIP = "build/collision-map-v2.zip";
    private static final String PROOF_FILE = "tools/route-a-live-mismatches.txt";
    private static final String REPORT_FILE = "tools/collision-map-v2-report.txt";
    private static final int STILL_BLOCKED_EXAMPLE_LIMIT = 30;

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
            String roundTrip = verifyRoundTrip(request.outputZip, result.regions);
            Comparison comparison = compareProofEdges(proofEdges, result.regions);
            String report = buildReport(cacheDir, project, request, result, roundTrip, comparison);

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

    private static BuildRequest parseRequest(
        String[] args,
        Path project,
        List<ProofEdge> proofEdges
    ) throws IOException
    {
        Path outputZip = args.length == 0 ? project.resolve(DEFAULT_ZIP) : resolve(project, args[0]);
        if (args.length <= 1)
        {
            TreeSet<Integer> regions = defaultProofRegions(proofEdges);
            return new BuildRequest(outputZip, false, regions, true);
        }

        String selector = joinRegionSelector(args);
        if ("all".equalsIgnoreCase(selector.trim()))
        {
            return new BuildRequest(outputZip, true, Collections.emptySet(), false);
        }

        TreeSet<Integer> regions = parseRegionIds(selector);
        if (regions.isEmpty())
        {
            throw new IOException("No region ids parsed from selector: " + selector);
        }
        return new BuildRequest(outputZip, false, regions, false);
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

    private static String joinRegionSelector(String[] args)
    {
        StringBuilder selector = new StringBuilder();
        for (int i = 1; i < args.length; i++)
        {
            if (selector.length() > 0)
            {
                selector.append(' ');
            }
            selector.append(args[i]);
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
        TreeMap<String, BuiltRegion> regions = new TreeMap<>();

        if (request.allRegions)
        {
            for (int regionX = MIN_REGION_X; regionX <= MAX_REGION_X; regionX++)
            {
                for (int regionY = MIN_REGION_Y; regionY <= MAX_REGION_Y; regionY++)
                {
                    loadAndBuild(loader, objects, regionId(regionX, regionY), true, stats, regions);
                }
            }
        }
        else
        {
            for (int regionId : request.regionIds)
            {
                loadAndBuild(loader, objects, regionId, false, stats, regions);
            }
        }

        if (!request.allRegions && regions.size() != request.regionIds.size())
        {
            throw new IOException("Only built " + regions.size() + " of " + request.regionIds.size()
                + " requested regions.");
        }

        stats.totalRegionsBuilt = regions.size();
        for (BuiltRegion region : regions.values())
        {
            stats.totalEdgesMadePassable += region.countPassableEdges();
            stats.doorEdgesWritten += region.countDoorEdges();
        }
        return new BuildResult(regions, stats);
    }

    private static void loadAndBuild(
        RegionLoader loader,
        Map<Integer, ObjectDefinition> objects,
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

        BuiltRegion region = buildRegion(source, objects, stats);
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
        BuildStats stats
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
                applyLocation(minX, minY, location, objects.get(location.getId()), bits, stats);
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
                     * A CONVENTION NOT YET VERIFIED AGAINST GROUND TRUTH in this project.
                     * The rebuilt map must be checked against live client data before any full
                     * archive commit treats this bridge handling as proven.
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
        BuildStats stats
    )
    {
        stats.placementsByLocType.merge(location.getType(), 1L, Long::sum);

        int plane = location.getPosition().getZ();
        if (plane < 0 || plane >= PLANE_COUNT)
        {
            return;
        }

        Direction[] shape = shapeFor(location.getType(), location.getOrientation(), stats);
        if (shape.length == 0)
        {
            return;
        }

        int x = baseX + location.getPosition().getX();
        int y = baseY + location.getPosition().getY();
        boolean openable = firstOpenStyleAction(def) != null;
        for (Direction direction : shape)
        {
            bits.markEdge(x, y, plane, direction, openable, stats);
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
                    for (int flag = 0; flag < FLAG_COUNT; flag++)
                    {
                        int index = bits.index(x, y, plane, flag);
                        if (actual.get(index) != bits.flags.get(index))
                        {
                            throw new IOException("Round trip bit mismatch in " + expected.name
                                + " at " + x + "," + y + "," + plane + " flag " + flag);
                        }
                    }
                }
            }
        }

        int unexpected = actual.nextSetBit(bits.totalBits());
        if (unexpected >= 0)
        {
            throw new IOException("Round trip found out-of-range bit " + unexpected + " in " + expected.name);
        }
        assertNoPassableDoorOverlap(actual, bits);
    }

    private static void assertNoPassableDoorOverlap(BitSet actual, RegionBits bits) throws IOException
    {
        for (int plane = 0; plane < PLANE_COUNT; plane++)
        {
            for (int y = bits.minY; y <= bits.maxY; y++)
            {
                for (int x = bits.minX; x <= bits.maxX; x++)
                {
                    if (actual.get(bits.index(x, y, plane, FLAG_NORTH_PASSABLE))
                        && actual.get(bits.index(x, y, plane, FLAG_NORTH_DOOR)))
                    {
                        throw new IOException("N passable and N door both set at " + x + "," + y + "," + plane);
                    }
                    if (actual.get(bits.index(x, y, plane, FLAG_EAST_PASSABLE))
                        && actual.get(bits.index(x, y, plane, FLAG_EAST_DOOR)))
                    {
                        throw new IOException("E passable and E door both set at " + x + "," + y + "," + plane);
                    }
                }
            }
        }
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

    private static String buildReport(
        File cacheDir,
        Path project,
        BuildRequest request,
        BuildResult result,
        String roundTrip,
        Comparison comparison
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
        report.append(roundTrip).append('\n');
        report.append('\n');
        appendProofComparison(report, comparison);
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
        report.append("  terrain-blocked count: ").append(stats.terrainBlockedTiles).append('\n');
        report.append("  bridge-branch count: ").append(stats.bridgeBranchTiles).append('\n');
        report.append("  out-of-region neighbour skips: ").append(stats.outOfRegionNeighbourSkips).append('\n');
        report.append("  total edges made passable: ").append(stats.totalEdgesMadePassable).append('\n');
        report.append("  door edges written: ").append(stats.doorEdgesWritten).append('\n');
        report.append("  terrain note: tile-setting floor blocking and bridge lowering are implemented as ");
        report.append("a convention not yet verified against this project's ground truth.").append('\n');
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
        private final boolean allRegions;
        private final Set<Integer> regionIds;
        private final boolean defaultedRegions;

        private BuildRequest(
            Path outputZip,
            boolean allRegions,
            Set<Integer> regionIds,
            boolean defaultedRegions
        )
        {
            this.outputZip = outputZip;
            this.allRegions = allRegions;
            this.regionIds = regionIds;
            this.defaultedRegions = defaultedRegions;
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

        private long locType1EdgesBlockedTotal;
        private long locType1Orientation3Placements;
        private long locType1InvalidOrientationFallbacks;
        private long ignoredLocTypePlacements;
        private long terrainBlockedTiles;
        private long bridgeBranchTiles;
        private long outOfRegionNeighbourSkips;
        private long totalEdgesMadePassable;
        private long doorEdgesWritten;
        private long totalRegionsBuilt;
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
            byte[] bitsBytes = bits.flags.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(16 + bitsBytes.length);
            buffer.putInt(bits.minX);
            buffer.putInt(bits.minY);
            buffer.putInt(bits.maxX);
            buffer.putInt(bits.maxY);
            buffer.put(bitsBytes);
            return buffer.array();
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
            StoredEdge edge = storedEdgeFor(x, y, plane, direction, stats);
            if (edge == null)
            {
                return;
            }

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
            Direction direction,
            BuildStats stats
        )
        {
            if (plane < 0 || plane >= PLANE_COUNT)
            {
                return null;
            }

            switch (direction)
            {
                case NORTH:
                    return edgeIfInside(x, y, plane, FLAG_NORTH_PASSABLE, FLAG_NORTH_DOOR, 0, stats);
                case EAST:
                    return edgeIfInside(x, y, plane, FLAG_EAST_PASSABLE, FLAG_EAST_DOOR, 1, stats);
                case SOUTH:
                    return edgeIfInside(x, y - 1, plane, FLAG_NORTH_PASSABLE, FLAG_NORTH_DOOR, 0, stats);
                case WEST:
                    return edgeIfInside(x - 1, y, plane, FLAG_EAST_PASSABLE, FLAG_EAST_DOOR, 1, stats);
                default:
                    throw new IllegalArgumentException("Unhandled direction " + direction);
            }
        }

        private StoredEdge edgeIfInside(
            int x,
            int y,
            int plane,
            int passableFlag,
            int doorFlag,
            int storedEdgeOrdinal,
            BuildStats stats
        )
        {
            if (!contains(x, y, plane))
            {
                stats.outOfRegionNeighbourSkips++;
                return null;
            }
            return new StoredEdge(x, y, plane, passableFlag, doorFlag, storedEdgeOrdinal);
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
}
