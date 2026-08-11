package com.drewshelper.cachetools;

import java.io.File;
import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;

/**
 * Proof pass over live route mismatches: the live client already answered movement, so this
 * diagnostic deliberately reports only what the cache loc data places on each proof edge.
 *
 * <p>Confirmed 2026-08-10 in {@link CacheAccessPointDumper}: map archives in the live cache
 * decode with ZERO xtea keys. This class keeps the same cache-opening path instead of inventing
 * a second loader, because the output is only useful if both tools are reading the same facts.
 *
 * <p>Run it with {@code gradlew.bat classifyProofEdges}. Optional args: [cacheDir] [projectDir].
 */
public final class ProofEdgeClassifier
{
    private static final String PROOF_MARKER = "OURS_BLOCKS_LIVE_OPEN";
    private static final Pattern PROOF_EDGE =
        Pattern.compile("\\b(-?\\d+),(-?\\d+),(-?\\d+)\\s+([NE])\\b");

    /**
     * Verbs that make a wall placement door-aware for this proof. The broader route dumper cares
     * about movement verbs; this pass is narrower on purpose because the requested bucket is
     * Open/Close doors versus solid-looking walls.
     */
    private static final String[] OPEN_STYLE_OPS = {"open", "close"};

    /**
     * Placement types that mount an object into a wall rather than standing it on the ground.
     * This is copied from the dumper so the proof is comparing the same loc facts.
     */
    private static final int[] WALL_LOC_TYPES = {0, 1, 2, 3, 9};

    /**
     * The cache stores map archives unencrypted, so a zero key means "do not decrypt" and
     * everything parses. This is deliberate, not a placeholder.
     */
    private static final KeyProvider ZERO_KEYS = regionId -> new int[]{0, 0, 0, 0};

    private static final int REGION_SIZE = 64;
    private static final int TOP_OBJECT_LIMIT = 15;
    private static final int NOTHING_EXAMPLE_LIMIT = 20;

    private ProofEdgeClassifier()
    {
    }

    public static void main(String[] args) throws IOException
    {
        File cacheDir = args.length > 0
            ? new File(args[0])
            : new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");
        Path project = Paths.get(args.length > 1 ? args[1] : System.getProperty("user.dir"));
        Path proofFile = project.resolve("tools/route-a-live-mismatches.txt");
        Path outFile = project.resolve("tools/proof-edge-classification.txt");

        if (!Files.isRegularFile(proofFile))
        {
            exitNonZero("Proof file missing: " + proofFile);
            return;
        }
        if (Files.size(proofFile) == 0)
        {
            exitNonZero("Proof file is empty: " + proofFile);
            return;
        }

        List<Edge> edges = parseProofEdges(proofFile);
        if (edges.isEmpty())
        {
            exitNonZero("Proof file contains no " + PROOF_MARKER + " edges: " + proofFile);
            return;
        }

        if (!cacheDir.isDirectory())
        {
            exitNonZero("No OSRS cache at " + cacheDir + " - pass the path as the first argument.");
            return;
        }

        Store store = new Store(cacheDir);
        store.load();
        try
        {
            Classification classification = classifyEdges(store, edges);
            String report = buildReport(classification);

            Files.createDirectories(outFile.getParent());
            Files.write(outFile, report.getBytes(StandardCharsets.UTF_8));
            System.out.print(report);
        }
        finally
        {
            store.close();
        }
    }

    private static List<Edge> parseProofEdges(Path proofFile) throws IOException
    {
        List<Edge> edges = new ArrayList<>();
        for (String line : Files.readAllLines(proofFile, StandardCharsets.UTF_8))
        {
            if (!line.contains(PROOF_MARKER))
            {
                continue;
            }

            Matcher matcher = PROOF_EDGE.matcher(line);
            if (!matcher.find())
            {
                continue;
            }

            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int plane = Integer.parseInt(matcher.group(3));
            char direction = matcher.group(4).charAt(0);
            edges.add(new Edge(x, y, plane, direction));
        }
        return edges;
    }

    private static Classification classifyEdges(Store store, List<Edge> edges) throws IOException
    {
        Map<Integer, ObjectDefinition> objects = loadObjectDefinitions(store);
        RegionCache regions = new RegionCache(store);
        Classification classification = new Classification(edges.size());

        for (Edge edge : edges)
        {
            EdgeWalls edgeWalls = collectWallPlacements(edge, regions, objects);
            if (edgeWalls.missingRegion)
            {
                classification.missingRegionEdges++;
                continue;
            }

            classification.classifiedEdges++;
            if (edgeWalls.wallObjects.isEmpty())
            {
                classification.nothingEdges++;
                if (classification.nothingExamples.size() < NOTHING_EXAMPLE_LIMIT)
                {
                    classification.nothingExamples.add(edge.raw());
                }
                continue;
            }

            if (hasOpenableWall(edgeWalls.wallObjects))
            {
                classification.openableEdges++;
                addBreakdown(classification.openableObjects, edgeWalls.wallObjects, true);
                addDefinitionStats(classification.openableDefinitionStats, edgeWalls.wallObjects);
            }
            else
            {
                classification.solidEdges++;
                addBreakdown(classification.solidObjects, edgeWalls.wallObjects, false);
                addDefinitionStats(classification.solidDefinitionStats, edgeWalls.wallObjects);
                if (allWallPlacementsHaveInteractTypeZero(edgeWalls.wallObjects))
                {
                    classification.solidInteractTypeZeroEdges++;
                }
            }
        }

        return classification;
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

    private static EdgeWalls collectWallPlacements(
        Edge edge,
        RegionCache regions,
        Map<Integer, ObjectDefinition> objects
    ) throws IOException
    {
        List<WallObject> wallObjects = new ArrayList<>();
        boolean missingRegion = false;

        for (Tile tile : edge.tiles())
        {
            RegionData region = regions.load(tile.regionId());
            if (!region.present)
            {
                missingRegion = true;
                continue;
            }

            for (Location location : region.wallPlacementsAt(tile.x, tile.y, tile.plane))
            {
                ObjectDefinition def = objects.get(location.getId());
                String name = def == null || def.getName() == null ? "(unknown)" : def.getName();
                String openStyleAction = firstOpenStyleAction(def);
                wallObjects.add(new WallObject(
                    location.getId(),
                    name,
                    openStyleAction,
                    def == null ? null : def.getInteractType(),
                    def == null ? null : def.getBlockingMask(),
                    def == null ? null : def.getWallOrDoor(),
                    def == null ? null : def.isBlocksProjectile()
                ));
            }
        }

        return new EdgeWalls(wallObjects, missingRegion);
    }

    private static boolean hasOpenableWall(List<WallObject> wallObjects)
    {
        for (WallObject wallObject : wallObjects)
        {
            if (wallObject.openStyleAction != null)
            {
                return true;
            }
        }
        return false;
    }

    private static void addBreakdown(
        Map<String, ObjectBreakdown> byName,
        List<WallObject> wallObjects,
        boolean openableOnly
    )
    {
        Set<String> counted = new HashSet<>();
        for (WallObject wallObject : wallObjects)
        {
            if (openableOnly && wallObject.openStyleAction == null)
            {
                continue;
            }

            String key = wallObject.objectId + "\n" + wallObject.name;
            if (!counted.add(key))
            {
                continue;
            }

            ObjectBreakdown breakdown = byName.computeIfAbsent(wallObject.name, name -> new ObjectBreakdown());
            breakdown.count++;
            breakdown.ids.merge(wallObject.objectId, 1, Integer::sum);
        }
    }

    /**
     * Records declared object-definition fields without treating them as collision truth. The
     * point of this report is to expose the cache data behind each bucket, not to invent another
     * blocking classifier.
     */
    private static void addDefinitionStats(DefinitionStats stats, List<WallObject> wallObjects)
    {
        for (WallObject wallObject : wallObjects)
        {
            increment(stats.interactTypes, formatFieldValue(wallObject.interactType));
            increment(stats.blockingMasks, formatFieldValue(wallObject.blockingMask));
            increment(stats.wallOrDoors, formatFieldValue(wallObject.wallOrDoor));
            increment(stats.blocksProjectiles, formatFieldValue(wallObject.blocksProjectile));
        }
    }

    private static void increment(Map<String, Integer> counts, String key)
    {
        counts.merge(key, 1, Integer::sum);
    }

    private static String formatFieldValue(Integer value)
    {
        return value == null ? "(missing definition)" : String.valueOf(value);
    }

    private static String formatFieldValue(Boolean value)
    {
        return value == null ? "(missing definition)" : String.valueOf(value);
    }

    /**
     * Produces the single hypothesis headline for SOLID. {@code interactType == 0} is reported
     * because it declares no clipping, but it is not used to move an edge between buckets.
     */
    private static boolean allWallPlacementsHaveInteractTypeZero(List<WallObject> wallObjects)
    {
        if (wallObjects.isEmpty())
        {
            return false;
        }

        for (WallObject wallObject : wallObjects)
        {
            if (wallObject.interactType == null || wallObject.interactType != 0)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * First Open/Close menu action, or null. {@code EntityOpsDefinition.Op} exposes a public
     * {@code text} field and has no useful toString, so the text field has to be read directly.
     */
    private static String firstOpenStyleAction(ObjectDefinition def)
    {
        if (def == null)
        {
            return null;
        }

        EntityOpsDefinition ops = def.getOps();
        if (ops == null || ops.getOps() == null)
        {
            return null;
        }
        for (EntityOpsDefinition.Op op : ops.getOps())
        {
            if (op == null || op.text == null)
            {
                continue;
            }
            String lower = op.text.toLowerCase(Locale.ROOT);
            for (String candidate : OPEN_STYLE_OPS)
            {
                if (lower.startsWith(candidate))
                {
                    return op.text;
                }
            }
        }
        return null;
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

    private static String buildReport(Classification classification)
    {
        StringBuilder report = new StringBuilder();
        report.append("proof edges parsed: ").append(classification.parsedEdges).append('\n');
        report.append("inside cache regions: ").append(classification.classifiedEdges).append('\n');
        report.append("missing cache-region edges: ").append(classification.missingRegionEdges).append('\n');
        report.append("classified edges: ").append(classification.classifiedEdges).append('\n');
        report.append('\n');
        report.append("bucket counts (percent of classified edges):").append('\n');
        appendBucket(report, "NOTHING", classification.nothingEdges, classification.classifiedEdges);
        appendBucket(report, "OPENABLE", classification.openableEdges, classification.classifiedEdges);
        appendBucket(report, "SOLID", classification.solidEdges, classification.classifiedEdges);
        report.append('\n');
        appendTopObjects(report, "top 15 object names in OPENABLE bucket:",
            classification.openableObjects);
        report.append('\n');
        appendTopObjects(report, "top 15 object names in SOLID bucket:",
            classification.solidObjects);
        report.append('\n');
        appendNothingExamples(report, classification.nothingExamples);
        report.append('\n');
        appendReading(report, classification);
        report.append('\n');
        appendDefinitionStats(report, "object definition fields in OPENABLE bucket:",
            classification.openableDefinitionStats);
        report.append('\n');
        appendDefinitionStats(report, "object definition fields in SOLID bucket:",
            classification.solidDefinitionStats);
        report.append('\n');
        appendInteractTypeZeroHypothesis(report, classification);
        return report.toString();
    }

    private static void appendBucket(StringBuilder report, String name, int count, int total)
    {
        report.append("  ").append(String.format(Locale.ROOT, "%-8s", name))
            .append(" ").append(count)
            .append(" (").append(percent(count, total)).append(")").append('\n');
    }

    private static String percent(int count, int total)
    {
        if (total == 0)
        {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", (count * 100.0) / total);
    }

    private static void appendTopObjects(
        StringBuilder report,
        String title,
        Map<String, ObjectBreakdown> byName
    )
    {
        report.append(title).append('\n');
        if (byName.isEmpty())
        {
            report.append("  (none)").append('\n');
            return;
        }

        List<Map.Entry<String, ObjectBreakdown>> top = new ArrayList<>(byName.entrySet());
        top.sort(Comparator
            .<Map.Entry<String, ObjectBreakdown>>comparingInt(entry -> entry.getValue().count)
            .reversed()
            .thenComparing(Map.Entry::getKey));

        for (int i = 0; i < Math.min(TOP_OBJECT_LIMIT, top.size()); i++)
        {
            Map.Entry<String, ObjectBreakdown> entry = top.get(i);
            report.append("  ").append(entry.getValue().count)
                .append("  ").append(entry.getKey())
                .append("  ids: ").append(formatIds(entry.getValue().ids))
                .append('\n');
        }
    }

    private static String formatIds(Map<Integer, Integer> ids)
    {
        List<Map.Entry<Integer, Integer>> top = new ArrayList<>(ids.entrySet());
        top.sort(Comparator
            .<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
            .reversed()
            .thenComparingInt(Map.Entry::getKey));

        StringBuilder out = new StringBuilder();
        int limit = Math.min(12, top.size());
        for (int i = 0; i < limit; i++)
        {
            if (i > 0)
            {
                out.append(", ");
            }
            Map.Entry<Integer, Integer> entry = top.get(i);
            out.append(entry.getKey()).append("=").append(entry.getValue());
        }
        if (top.size() > limit)
        {
            out.append(", +").append(top.size() - limit).append(" more ids");
        }
        return out.toString();
    }

    private static void appendNothingExamples(StringBuilder report, List<String> nothingExamples)
    {
        report.append("example NOTHING edges:").append('\n');
        if (nothingExamples.isEmpty())
        {
            report.append("  (none)").append('\n');
            return;
        }

        for (String example : nothingExamples)
        {
            report.append("  ").append(example).append('\n');
        }
    }

    private static void appendDefinitionStats(
        StringBuilder report,
        String title,
        DefinitionStats stats
    )
    {
        report.append(title).append('\n');
        appendDistribution(report, "interactType", stats.interactTypes);
        appendDistribution(report, "blockingMask", stats.blockingMasks);
        appendDistribution(report, "wallOrDoor", stats.wallOrDoors);
        appendDistribution(report, "blocksProjectile", stats.blocksProjectiles);
    }

    private static void appendDistribution(
        StringBuilder report,
        String title,
        Map<String, Integer> counts
    )
    {
        report.append("  ").append(title).append(':').append('\n');
        if (counts.isEmpty())
        {
            report.append("    (none)").append('\n');
            return;
        }

        List<Map.Entry<String, Integer>> top = new ArrayList<>(counts.entrySet());
        top.sort(Comparator
            .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
            .reversed()
            .thenComparing(Map.Entry::getKey));

        for (Map.Entry<String, Integer> entry : top)
        {
            report.append("    ").append(entry.getKey())
                .append(" -> ").append(entry.getValue())
                .append('\n');
        }
    }

    private static void appendInteractTypeZeroHypothesis(
        StringBuilder report,
        Classification classification
    )
    {
        report.append("interactType == 0 hypothesis:").append('\n');
        report.append("  SOLID edges where EVERY wall placement on the edge has interactType == 0: ")
            .append(classification.solidInteractTypeZeroEdges)
            .append('\n');
        report.append("  Reading guide: close to the SOLID bucket count above points to non-blocking ");
        report.append("scenery; near zero points to a decode disagreement.").append('\n');
    }

    private static void appendReading(StringBuilder report, Classification classification)
    {
        report.append("reading: ");
        report.append("NOTHING edges are places where the proof edge has no cache wall placement on either ");
        report.append("tile, so those are rebuild wins if the shipped map is stale. ");
        report.append("OPENABLE edges are the door-aware prize because the cache sees an Open/Close wall ");
        report.append("object there. ");
        if (classification.solidEdges == 0)
        {
            report.append("SOLID is zero, so this proof did not find a live-open edge where the cache sees only ");
            report.append("non-open wall placements.");
        }
        else
        {
            report.append("SOLID is ").append(classification.solidEdges).append(", so those edges are a decode ");
            report.append("disagreement with the live client and the rebuild is not yet trustworthy until that ");
            report.append("bucket is understood.");
        }
        report.append('\n');
    }

    private static void exitNonZero(String message)
    {
        System.err.println(message);
        System.exit(1);
    }

    private static long key(int x, int y, int plane)
    {
        return (((long) x) << 34) | (((long) y) << 4) | (plane & 0xFL);
    }

    private static final class RegionCache
    {
        private final RegionLoader loader;
        private final Map<Integer, RegionData> regions = new HashMap<>();

        private RegionCache(Store store)
        {
            loader = new RegionLoader(store, ZERO_KEYS);
        }

        private RegionData load(int regionId) throws IOException
        {
            RegionData cached = regions.get(regionId);
            if (cached != null)
            {
                return cached;
            }

            RegionData loaded;
            try
            {
                LocationsDefinition locations = loader.loadLocDef(regionId);
                if (locations == null || locations.getLocations() == null)
                {
                    loaded = RegionData.missing();
                }
                else
                {
                    loaded = RegionData.from(regionId, locations);
                }
            }
            catch (Exception e)
            {
                loaded = RegionData.missing();
            }

            regions.put(regionId, loaded);
            return loaded;
        }
    }

    private static final class RegionData
    {
        private final boolean present;
        private final Map<Long, List<Location>> wallsByTile;

        private RegionData(boolean present, Map<Long, List<Location>> wallsByTile)
        {
            this.present = present;
            this.wallsByTile = wallsByTile;
        }

        private static RegionData missing()
        {
            return new RegionData(false, Collections.emptyMap());
        }

        private static RegionData from(int regionId, LocationsDefinition locations)
        {
            Map<Long, List<Location>> wallsByTile = new HashMap<>();
            int rx = regionId >> 8;
            int ry = regionId & 0xFF;
            int baseX = rx * REGION_SIZE;
            int baseY = ry * REGION_SIZE;

            for (Location location : locations.getLocations())
            {
                if (!isWallPlacement(location.getType()))
                {
                    continue;
                }

                int x = baseX + location.getPosition().getX();
                int y = baseY + location.getPosition().getY();
                int plane = location.getPosition().getZ();
                wallsByTile.computeIfAbsent(key(x, y, plane), ignored -> new ArrayList<>())
                    .add(location);
            }

            return new RegionData(true, wallsByTile);
        }

        private List<Location> wallPlacementsAt(int x, int y, int plane)
        {
            return wallsByTile.getOrDefault(key(x, y, plane), Collections.emptyList());
        }
    }

    private static final class Classification
    {
        private final int parsedEdges;
        private final Map<String, ObjectBreakdown> openableObjects = new HashMap<>();
        private final Map<String, ObjectBreakdown> solidObjects = new HashMap<>();
        private final DefinitionStats openableDefinitionStats = new DefinitionStats();
        private final DefinitionStats solidDefinitionStats = new DefinitionStats();
        private final List<String> nothingExamples = new ArrayList<>();

        private int classifiedEdges;
        private int missingRegionEdges;
        private int nothingEdges;
        private int openableEdges;
        private int solidEdges;
        private int solidInteractTypeZeroEdges;

        private Classification(int parsedEdges)
        {
            this.parsedEdges = parsedEdges;
        }
    }

    private static final class EdgeWalls
    {
        private final List<WallObject> wallObjects;
        private final boolean missingRegion;

        private EdgeWalls(List<WallObject> wallObjects, boolean missingRegion)
        {
            this.wallObjects = wallObjects;
            this.missingRegion = missingRegion;
        }
    }

    private static final class ObjectBreakdown
    {
        private final Map<Integer, Integer> ids = new HashMap<>();

        private int count;
    }

    private static final class DefinitionStats
    {
        private final Map<String, Integer> interactTypes = new HashMap<>();
        private final Map<String, Integer> blockingMasks = new HashMap<>();
        private final Map<String, Integer> wallOrDoors = new HashMap<>();
        private final Map<String, Integer> blocksProjectiles = new HashMap<>();
    }

    private static final class WallObject
    {
        private final int objectId;
        private final String name;
        private final String openStyleAction;
        private final Integer interactType;
        private final Integer blockingMask;
        private final Integer wallOrDoor;
        private final Boolean blocksProjectile;

        private WallObject(
            int objectId,
            String name,
            String openStyleAction,
            Integer interactType,
            Integer blockingMask,
            Integer wallOrDoor,
            Boolean blocksProjectile
        )
        {
            this.objectId = objectId;
            this.name = name;
            this.openStyleAction = openStyleAction;
            this.interactType = interactType;
            this.blockingMask = blockingMask;
            this.wallOrDoor = wallOrDoor;
            this.blocksProjectile = blocksProjectile;
        }
    }

    private static final class Edge
    {
        private final int x;
        private final int y;
        private final int plane;
        private final char direction;

        private Edge(int x, int y, int plane, char direction)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.direction = direction;
        }

        private List<Tile> tiles()
        {
            List<Tile> tiles = new ArrayList<>(2);
            tiles.add(new Tile(x, y, plane));
            if (direction == 'N')
            {
                tiles.add(new Tile(x, y + 1, plane));
            }
            else
            {
                tiles.add(new Tile(x + 1, y, plane));
            }
            return tiles;
        }

        private String raw()
        {
            return x + "," + y + "," + plane + " " + direction;
        }
    }

    private static final class Tile
    {
        private final int x;
        private final int y;
        private final int plane;

        private Tile(int x, int y, int plane)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }

        private int regionId()
        {
            return ((x / REGION_SIZE) << 8) | (y / REGION_SIZE);
        }
    }
}
