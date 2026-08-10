package com.drewshelper.cachetools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;

/**
 * Route B - decodes the OSRS game cache and reports every object placement that can be moved
 * through but has no transport row in our shipped data.
 *
 * <p>This is the answer to "which gates are we missing". It needs no game client, no walking and
 * no manual list. Confirmed 2026-08-10: map archives in the live cache decode with ZERO xtea keys,
 * so no key file is required - {@link #ZERO_KEYS} is passed deliberately, not as a placeholder.
 *
 * <p>Run it with {@code gradlew.bat dumpAccessPoints}. Optional args: [cacheDir] [projectDir].
 */
public final class CacheAccessPointDumper
{
    /**
     * Movement-ish menu actions. An object carrying one of these is something a player can pass
     * through, so it is a transport candidate. Deliberately a prefix match - the cache holds
     * "Climb-over", "Squeeze-through", "Go-through" and friends.
     */
    private static final String[] MOVEMENT_OPS = {
        "open", "climb", "pass", "enter", "exit", "squeeze", "cross", "go-through",
        "walk-through", "jump", "vault", "board", "travel", "ride", "step-over",
        "crawl-through", "traverse", "swing-across", "balance", "grapple", "tunnel",
        "descend", "ascend", "climb-over", "climb-under", "climb-through", "use-boat",
        "get-in", "get-out", "leave", "escape", "teleport", "portal"
    };

    /**
     * Verbs that only mean "a way through" when the object is set into a wall.
     *
     * <p>Measured 2026-08-10: on non-wall placements, {@code Open} is 1,674 rows and every one
     * of the top names is a container - Chest, Drawers, Closed Chest, Wardrobe. On WALL
     * placements the same verb is Door, Gate and Large door. Same word, opposite meaning,
     * decided entirely by how the object is placed.
     */
    private static final String[] AMBIGUOUS_OPS = {"open", "close"};

    /**
     * Placement types that mount an object INTO a wall rather than standing it on the ground.
     *
     * <p>This is the discriminator, and it lives on the placement rather than on the object
     * definition. Measured: Gate 393/402 and Door 1850/1913 are type 0, Large door 188/188 are
     * type 0, and NOT ONE container placement is a wall type - Chest, Drawers, Wardrobe,
     * Cupboard, Barrel and Coffin are all type 10.
     *
     * <p>Do not try to replace this with an ObjectDefinition field. {@code interactType},
     * {@code blockingMask} and {@code wallOrDoor} were all measured and all three overlap
     * completely between gates and chests - a chest really is a solid object with an Open
     * action, and the definition never records "you can get through this".
     */
    private static final int[] WALL_LOC_TYPES = {0, 1, 2, 3, 9};

    /**
     * Name fragments that stay a passage even with an ambiguous verb on a non-wall placement.
     *
     * <p>The wall test alone is a good filter but not a complete one: a trapdoor is genuinely
     * floor-mounted and genuinely opens onto somewhere, and the first pass silently dropped 42
     * of them plus 23 ground-placed doors along with the chests. Geometry cannot see that, so
     * these few names are an explicit, auditable exception rather than a widened rule.
     *
     * <p>Substring match is deliberate - it catches "Trapdoor" and "Gate of War" alike. It is
     * also why this list must stay short: every entry is a chance to re-admit a container.
     * The dumper prints everything this rescues so the exception can never grow unnoticed.
     */
    private static final String[] PASSAGE_NAME_HINTS = {"door", "gate", "hatch", "grate"};

    /**
     * The cache stores map archives unencrypted, so a zero key means "do not decrypt" and
     * everything parses. Verified against the live cache: 2,747 regions, 0 failures.
     */
    private static final KeyProvider ZERO_KEYS = regionId -> new int[]{0, 0, 0, 0};

    private static final int REGION_SIZE = 64;
    private static final int MIN_REGION_X = 0;
    private static final int MAX_REGION_X = 255;
    private static final int MIN_REGION_Y = 0;
    private static final int MAX_REGION_Y = 255;

    /** The gap that started this whole exercise. If the dump misses it, the dump is wrong. */
    private static final int FIXTURE_X = 2935;
    private static final int FIXTURE_Y = 3450;

    private CacheAccessPointDumper()
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

        Path transportsTsv = project.resolve("src/main/resources/drewshelper-transports.tsv");
        Path collisionZip = project.resolve("src/main/resources/collision-map.zip");
        Path outFile = project.resolve("tools/cache-access-points.tsv");
        Path summaryFile = project.resolve("tools/cache-access-points-summary.txt");

        StringBuilder log = new StringBuilder();
        log.append("cache      : ").append(cacheDir).append('\n');
        log.append("project    : ").append(project).append('\n').append('\n');

        Store store = new Store(cacheDir);
        store.load();

        Map<Integer, ObjectDefinition> openable = loadOpenableObjects(store, log);
        Set<Long> covered = loadTransportTiles(transportsTsv, log);
        Set<String> shippedRegions = loadShippedRegions(collisionZip, log);

        List<AccessPoint> points = scanPlacements(store, openable, covered, shippedRegions, log);

        writeReport(outFile, points);
        log.append('\n').append("wrote ").append(points.size()).append(" rows to ").append(outFile).append('\n');

        appendFixtureCheck(points, log);
        appendBreakdown(points, log);

        String summary = log.toString();
        Files.createDirectories(summaryFile.getParent());
        Files.write(summaryFile, summary.getBytes(StandardCharsets.UTF_8));
        System.out.print(summary);

        store.close();
    }

    private static Map<Integer, ObjectDefinition> loadOpenableObjects(Store store, StringBuilder log)
        throws IOException
    {
        ObjectManager manager = new ObjectManager(store);
        manager.load();
        Collection<ObjectDefinition> all = manager.getObjects();

        Map<Integer, ObjectDefinition> openable = new HashMap<>();
        for (ObjectDefinition def : all)
        {
            if (firstMovementOp(def) != null)
            {
                openable.put(def.getId(), def);
            }
        }
        log.append("object definitions      : ").append(all.size()).append('\n');
        log.append("  with a movement action: ").append(openable.size()).append('\n');
        return openable;
    }

    /**
     * First menu action that reads like movement, or null. {@code EntityOpsDefinition.Op} exposes
     * a public {@code text} field and has no getter and no toString - printing the list gives
     * object hashes, which is a trap worth remembering.
     */
    private static String firstMovementOp(ObjectDefinition def)
    {
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
            for (String candidate : MOVEMENT_OPS)
            {
                if (lower.startsWith(candidate))
                {
                    return op.text;
                }
            }
        }
        return null;
    }

    /**
     * Whether this placement is something a route can actually pass through.
     *
     * <p>Neither half of this test works on its own. Placement type alone keeps chests (they
     * share type 10 with ladders and staircases); verb alone keeps chests too (they share
     * "Open" with doors). Together they separate cleanly.
     */
    private static boolean isPassage(String action, int locType, String name)
    {
        if (action == null || action.isEmpty())
        {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        for (String ambiguous : AMBIGUOUS_OPS)
        {
            if (lower.startsWith(ambiguous))
            {
                // Open/Close counts when the object is mounted in a wall, or when the name
                // says it is a way through wherever it sits (trapdoors, floor-placed doors).
                return isWallPlacement(locType) || hasPassageName(name);
            }
        }
        // Climb, Cross, Enter, Squeeze, Jump, Pass... are unambiguous wherever they sit.
        return true;
    }

    private static boolean isAmbiguousVerb(String action)
    {
        if (action == null)
        {
            return false;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        for (String ambiguous : AMBIGUOUS_OPS)
        {
            if (lower.startsWith(ambiguous))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether the object name itself says "way through", for the ambiguous-verb case only. */
    private static boolean hasPassageName(String name)
    {
        if (name == null)
        {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : PASSAGE_NAME_HINTS)
        {
            if (lower.contains(hint))
            {
                return true;
            }
        }
        return false;
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

    private static Set<Long> loadTransportTiles(Path tsv, StringBuilder log) throws IOException
    {
        Set<Long> tiles = new HashSet<>();
        if (!Files.exists(tsv))
        {
            log.append("transports tsv          : MISSING at ").append(tsv).append('\n');
            return tiles;
        }
        for (String line : Files.readAllLines(tsv, StandardCharsets.UTF_8))
        {
            if (line.startsWith("#"))
            {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 3)
            {
                continue;
            }
            for (int i = 1; i <= 2; i++)
            {
                String[] c = parts[i].split(",");
                if (c.length < 3)
                {
                    continue;
                }
                try
                {
                    int x = Integer.parseInt(c[0].trim());
                    int y = Integer.parseInt(c[1].trim());
                    int z = Integer.parseInt(c[2].trim());
                    if (x >= 0 && y >= 0)
                    {
                        tiles.add(key(x, y, z));
                    }
                }
                catch (NumberFormatException ignored)
                {
                    // a malformed coordinate is not worth aborting the whole dump for
                }
            }
        }
        log.append("transport endpoint tiles: ").append(tiles.size()).append('\n');
        return tiles;
    }

    private static Set<String> loadShippedRegions(Path zip, StringBuilder log) throws IOException
    {
        Set<String> regions = new HashSet<>();
        if (!Files.exists(zip))
        {
            log.append("collision-map.zip       : MISSING at ").append(zip).append('\n');
            return regions;
        }
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip.toFile())))
        {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null)
            {
                regions.add(entry.getName());
            }
        }
        log.append("regions in our shipped map: ").append(regions.size()).append('\n');
        return regions;
    }

    private static List<AccessPoint> scanPlacements(
        Store store,
        Map<Integer, ObjectDefinition> openable,
        Set<Long> covered,
        Set<String> shippedRegions,
        StringBuilder log
    ) throws IOException
    {
        RegionLoader loader = new RegionLoader(store, ZERO_KEYS);
        List<AccessPoint> points = new ArrayList<>();
        Set<String> cacheRegions = new TreeSet<>();
        Map<String, Integer> droppedByName = new HashMap<>();
        Map<String, Integer> rescuedByName = new HashMap<>();
        long placements = 0;
        long dropped = 0;

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
                cacheRegions.add(rx + "_" + ry);

                int baseX = rx * REGION_SIZE;
                int baseY = ry * REGION_SIZE;
                for (Location location : locations.getLocations())
                {
                    placements++;
                    ObjectDefinition def = openable.get(location.getId());
                    if (def == null)
                    {
                        continue;
                    }
                    String action = firstMovementOp(def);
                    String defName = def.getName() == null ? "(unnamed)" : def.getName();
                    if (!isWallPlacement(location.getType()) && hasPassageName(defName)
                        && isAmbiguousVerb(action))
                    {
                        rescuedByName.merge(defName, 1, Integer::sum);
                    }
                    if (!isPassage(action, location.getType(), defName))
                    {
                        dropped++;
                        droppedByName.merge(defName, 1, Integer::sum);
                        continue;
                    }
                    // LocationsDefinition holds region-LOCAL positions; world = region base + local.
                    int x = baseX + location.getPosition().getX();
                    int y = baseY + location.getPosition().getY();
                    int z = location.getPosition().getZ();
                    boolean isCovered = coveredNear(covered, x, y, z);
                    points.add(new AccessPoint(
                        x, y, z,
                        location.getId(),
                        def.getName(),
                        action,
                        location.getType(),
                        location.getOrientation(),
                        isCovered,
                        shippedRegions.contains(rx + "_" + ry)
                    ));
                }
            }
        }

        log.append("regions in the cache      : ").append(cacheRegions.size()).append('\n');
        log.append("object placements scanned : ").append(placements).append('\n');
        log.append("dropped as NOT-A-PASSAGE  : ").append(dropped)
            .append("  (Open/Close on a non-wall placement - containers)").append('\n');
        List<Map.Entry<String, Integer>> drops = new ArrayList<>(droppedByName.entrySet());
        drops.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        for (int i = 0; i < Math.min(25, drops.size()); i++)
        {
            log.append("    - ").append(drops.get(i).getValue()).append("  ")
                .append(drops.get(i).getKey()).append('\n');
        }
        long rescued = rescuedByName.values().stream().mapToInt(Integer::intValue).sum();
        log.append("RESCUED by name hint      : ").append(rescued)
            .append("  (ambiguous verb, non-wall, but the name says passage)").append('\n');
        List<Map.Entry<String, Integer>> saves = new ArrayList<>(rescuedByName.entrySet());
        saves.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        for (Map.Entry<String, Integer> s : saves)
        {
            log.append("    + ").append(s.getValue()).append("  ").append(s.getKey()).append('\n');
        }

        Set<String> missing = new TreeSet<>(cacheRegions);
        missing.removeAll(shippedRegions);
        log.append("regions the cache has that our collision map does NOT: ")
            .append(missing.size()).append('\n');

        points.sort(Comparator
            .comparing((AccessPoint p) -> p.covered)
            .thenComparing(p -> p.name == null ? "" : p.name)
            .thenComparingInt(p -> p.x)
            .thenComparingInt(p -> p.y));
        return points;
    }

    private static boolean coveredNear(Set<Long> covered, int x, int y, int z)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                if (covered.contains(key(x + dx, y + dy, z)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static void writeReport(Path out, List<AccessPoint> points) throws IOException
    {
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8))
        {
            w.write("# Openable objects decoded from the OSRS cache. Generated by dumpAccessPoints.");
            w.newLine();
            w.write("# covered=yes means our transport data already has a row within 1 tile.");
            w.newLine();
            w.write("x\ty\tplane\tobjectId\tname\taction\tlocType\torientation\tcovered\tinShippedMap");
            w.newLine();
            for (AccessPoint p : points)
            {
                w.write(p.x + "\t" + p.y + "\t" + p.z + "\t" + p.objectId + "\t"
                    + (p.name == null ? "" : p.name) + "\t"
                    + (p.action == null ? "" : p.action) + "\t"
                    + p.locType + "\t" + p.orientation + "\t"
                    + (p.covered ? "yes" : "no") + "\t"
                    + (p.inShippedMap ? "yes" : "no"));
                w.newLine();
            }
        }
    }

    private static void appendFixtureCheck(List<AccessPoint> points, StringBuilder log)
    {
        log.append('\n').append("--- ACCEPTANCE: Falador west wall gate at ")
            .append(FIXTURE_X).append(',').append(FIXTURE_Y).append(" ---\n");
        boolean found = false;
        for (AccessPoint p : points)
        {
            if (Math.abs(p.x - FIXTURE_X) <= 2 && Math.abs(p.y - FIXTURE_Y) <= 2 && p.z == 0)
            {
                found = true;
                log.append("  FOUND  ").append(p.x).append(',').append(p.y)
                    .append("  id=").append(p.objectId)
                    .append("  name=").append(p.name)
                    .append("  action=").append(p.action)
                    .append("  covered=").append(p.covered ? "yes" : "no").append('\n');
            }
        }
        log.append(found
            ? "  RESULT: PASS - world-coordinate maths and object filtering are both correct.\n"
            : "  RESULT: FAIL - do not trust this dump until this fixture is found.\n");
    }

    private static void appendBreakdown(List<AccessPoint> points, StringBuilder log)
    {
        int uncovered = 0;
        Map<String, Integer> byName = new HashMap<>();
        for (AccessPoint p : points)
        {
            if (!p.covered)
            {
                uncovered++;
                String n = p.name == null ? "(unnamed)" : p.name;
                byName.merge(n, 1, Integer::sum);
            }
        }
        log.append('\n').append("--- RESULT ---\n");
        log.append("openable placements total : ").append(points.size()).append('\n');
        log.append("  already covered by data : ").append(points.size() - uncovered).append('\n');
        log.append("  NOT covered             : ").append(uncovered).append('\n');

        List<Map.Entry<String, Integer>> top = new ArrayList<>(byName.entrySet());
        top.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        log.append('\n').append("most common uncovered object names:\n");
        for (int i = 0; i < Math.min(20, top.size()); i++)
        {
            log.append("  ").append(top.get(i).getValue()).append("  ").append(top.get(i).getKey()).append('\n');
        }
    }

    private static long key(int x, int y, int z)
    {
        return (((long) x) << 34) | (((long) y) << 4) | (z & 0xFL);
    }

    private static final class AccessPoint
    {
        private final int x;
        private final int y;
        private final int z;
        private final int objectId;
        private final String name;
        private final String action;
        private final int locType;
        private final int orientation;
        private final boolean covered;
        private final boolean inShippedMap;

        private AccessPoint(int x, int y, int z, int objectId, String name, String action,
            int locType, int orientation, boolean covered, boolean inShippedMap)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.objectId = objectId;
            this.name = name;
            this.action = action;
            this.locType = locType;
            this.orientation = orientation;
            this.covered = covered;
            this.inShippedMap = inShippedMap;
        }
    }
}
