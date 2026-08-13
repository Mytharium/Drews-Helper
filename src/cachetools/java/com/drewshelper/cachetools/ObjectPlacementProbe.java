package com.drewshelper.cachetools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;

/**
 * Dumps object placements around hand-checked collision-map problem areas.
 *
 * <p>This is intentionally a diagnostic, not a map writer. The furniture pass needs object
 * identity before it can safely block chairs without reviving the old phase 2 beach seal, so this
 * tool prints the cache facts at the known chair and ferry-strip controls first.
 *
 * <p>Run with {@code gradlew.bat probeObjectPlacements}. Optional args:
 * {@code x1,y1,z,x2,y2,label}. When args are present, every arg must be one full box.
 */
public final class ObjectPlacementProbe
{
    private static final KeyProvider ZERO_KEYS = regionId -> new int[]{0, 0, 0, 0};
    private static final int REGION_SIZE = 64;

    private static final Box[] DEFAULT_BOXES = {
        new Box(2571, 3240, 0, 2578, 3252, "ardougne_mansion_chair_box"),
        new Box(3142, 2832, 0, 3162, 2848, "ruins_of_unkah_ferry_strip")
    };

    private ObjectPlacementProbe()
    {
    }

    public static void main(String[] args) throws IOException
    {
        File cacheDir = new File(System.getProperty("user.home"), ".runelite/jagexcache/oldschool/LIVE");
        if (!cacheDir.isDirectory())
        {
            throw new IOException("No OSRS cache at " + cacheDir + " - populate RuneLite first.");
        }

        Path project = Paths.get(System.getProperty("user.dir"));
        Path outFile = project.resolve("tools/object-placement-probe.txt");
        List<Box> boxes = parseBoxes(args);

        Store store = new Store(cacheDir);
        store.load();
        try
        {
            Map<Integer, ObjectDefinition> objects = loadObjectDefinitions(store);
            RegionLoader loader = new RegionLoader(store, ZERO_KEYS);
            List<PlacementRow> rows = new ArrayList<>();
            StringBuilder report = new StringBuilder();
            report.append("cache: ").append(cacheDir).append('\n');
            report.append("boxes: ").append(boxes.size()).append('\n');
            report.append('\n');

            for (Box box : boxes)
            {
                rows.clear();
                scanBox(loader, objects, box, rows);
                rows.sort(Comparator
                    .comparingInt((PlacementRow row) -> row.anchorX)
                    .thenComparingInt(row -> row.anchorY)
                    .thenComparingInt(row -> row.plane)
                    .thenComparingInt(row -> row.locType)
                    .thenComparingInt(row -> row.objectId));
                appendBoxReport(report, box, rows);
            }

            String text = report.toString();
            Files.createDirectories(outFile.getParent());
            Files.write(outFile, text.getBytes(StandardCharsets.UTF_8));
            System.out.print(text);
        }
        finally
        {
            store.close();
        }
    }

    private static List<Box> parseBoxes(String[] args)
    {
        List<Box> boxes = new ArrayList<>();
        if (args.length == 0)
        {
            for (Box box : DEFAULT_BOXES)
            {
                boxes.add(box);
            }
            return boxes;
        }

        for (String arg : args)
        {
            String[] parts = arg.split(",", 6);
            if (parts.length != 6)
            {
                throw new IllegalArgumentException(
                    "Expected x1,y1,z,x2,y2,label but got: " + arg);
            }
            boxes.add(new Box(
                parseInt(parts[0], arg),
                parseInt(parts[1], arg),
                parseInt(parts[2], arg),
                parseInt(parts[3], arg),
                parseInt(parts[4], arg),
                parts[5]
            ));
        }
        return boxes;
    }

    private static int parseInt(String value, String arg)
    {
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("Bad integer in " + arg + ": " + value, ex);
        }
    }

    private static Map<Integer, ObjectDefinition> loadObjectDefinitions(Store store)
        throws IOException
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

    private static void scanBox(
        RegionLoader loader,
        Map<Integer, ObjectDefinition> objects,
        Box box,
        List<PlacementRow> rows
    )
    {
        int minRegionX = Math.floorDiv(box.minX, REGION_SIZE);
        int maxRegionX = Math.floorDiv(box.maxX, REGION_SIZE);
        int minRegionY = Math.floorDiv(box.minY, REGION_SIZE);
        int maxRegionY = Math.floorDiv(box.maxY, REGION_SIZE);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++)
        {
            for (int regionY = minRegionY; regionY <= maxRegionY; regionY++)
            {
                scanRegion(loader, objects, box, regionX, regionY, rows);
            }
        }
    }

    private static void scanRegion(
        RegionLoader loader,
        Map<Integer, ObjectDefinition> objects,
        Box box,
        int regionX,
        int regionY,
        List<PlacementRow> rows
    )
    {
        int regionId = (regionX << 8) | regionY;
        LocationsDefinition locations;
        try
        {
            locations = loader.loadLocDef(regionId);
        }
        catch (Exception ex)
        {
            return;
        }
        if (locations == null || locations.getLocations() == null)
        {
            return;
        }

        int baseX = regionX * REGION_SIZE;
        int baseY = regionY * REGION_SIZE;
        for (Location location : locations.getLocations())
        {
            if (location.getPosition().getZ() != box.plane)
            {
                continue;
            }

            ObjectDefinition def = objects.get(location.getId());
            int anchorX = baseX + location.getPosition().getX();
            int anchorY = baseY + location.getPosition().getY();
            Footprint footprint = footprint(anchorX, anchorY, location, def);
            if (!box.intersects(footprint))
            {
                continue;
            }

            rows.add(new PlacementRow(location, def, anchorX, anchorY, footprint));
        }
    }

    private static Footprint footprint(
        int anchorX,
        int anchorY,
        Location location,
        ObjectDefinition def
    )
    {
        int sizeX = def == null ? 1 : Math.max(1, def.getSizeX());
        int sizeY = def == null ? 1 : Math.max(1, def.getSizeY());
        if ((location.getOrientation() & 1) == 1)
        {
            int originalX = sizeX;
            sizeX = sizeY;
            sizeY = originalX;
        }
        return new Footprint(anchorX, anchorY, anchorX + sizeX - 1, anchorY + sizeY - 1);
    }

    private static void appendBoxReport(StringBuilder report, Box box, List<PlacementRow> rows)
    {
        report.append("box ").append(box.label)
            .append(" x=").append(box.minX).append("..").append(box.maxX)
            .append(" y=").append(box.minY).append("..").append(box.maxY)
            .append(" plane=").append(box.plane)
            .append(" placements=").append(rows.size())
            .append('\n');
        report.append("anchor footprint id name locType orient size interact blockingMask ")
            .append("blocksProjectile obstructsGround wallOrDoor actions")
            .append('\n');
        for (PlacementRow row : rows)
        {
            report.append(row.anchorX).append(',').append(row.anchorY).append(',').append(row.plane)
                .append(' ')
                .append(row.footprint.minX).append(',').append(row.footprint.minY)
                .append("..")
                .append(row.footprint.maxX).append(',').append(row.footprint.maxY)
                .append(' ')
                .append(row.objectId)
                .append(' ')
                .append(quote(row.name))
                .append(' ')
                .append(row.locType)
                .append(' ')
                .append(row.orientation)
                .append(' ')
                .append(row.sizeX).append('x').append(row.sizeY)
                .append(' ')
                .append(row.interactType)
                .append(' ')
                .append(row.blockingMask)
                .append(' ')
                .append(row.blocksProjectile)
                .append(' ')
                .append(row.obstructsGround)
                .append(' ')
                .append(row.wallOrDoor)
                .append(' ')
                .append(quote(row.actions))
                .append('\n');
        }
        report.append('\n');
        appendNameSummary(report, rows);
        report.append('\n');
    }

    private static void appendNameSummary(StringBuilder report, List<PlacementRow> rows)
    {
        Map<String, Integer> byName = new HashMap<>();
        for (PlacementRow row : rows)
        {
            String key = row.name + " | type=" + row.locType
                + " interact=" + row.interactType
                + " size=" + row.sizeX + "x" + row.sizeY;
            byName.merge(key, 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(byName.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
            .thenComparing(Map.Entry::getKey));
        report.append("summary count name/type").append('\n');
        for (Map.Entry<String, Integer> entry : entries)
        {
            report.append("  ").append(entry.getValue()).append("  ").append(entry.getKey())
                .append('\n');
        }
    }

    private static String actions(ObjectDefinition def)
    {
        if (def == null || def.getOps() == null || def.getOps().getOps() == null)
        {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (EntityOpsDefinition.Op op : def.getOps().getOps())
        {
            if (op == null || op.text == null)
            {
                continue;
            }
            if (text.length() > 0)
            {
                text.append('|');
            }
            text.append(op.text);
        }
        return text.toString();
    }

    private static String quote(String value)
    {
        if (value == null)
        {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static final class Box
    {
        private final int minX;
        private final int minY;
        private final int plane;
        private final int maxX;
        private final int maxY;
        private final String label;

        private Box(int x1, int y1, int plane, int x2, int y2, String label)
        {
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.plane = plane;
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.label = label == null || label.trim().isEmpty()
                ? "unnamed"
                : label.trim().toLowerCase(Locale.ROOT);
        }

        private boolean intersects(Footprint footprint)
        {
            return footprint.maxX >= minX
                && footprint.minX <= maxX
                && footprint.maxY >= minY
                && footprint.minY <= maxY;
        }
    }

    private static final class Footprint
    {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private Footprint(int minX, int minY, int maxX, int maxY)
        {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private static final class PlacementRow
    {
        private final int anchorX;
        private final int anchorY;
        private final int plane;
        private final int objectId;
        private final String name;
        private final int locType;
        private final int orientation;
        private final int sizeX;
        private final int sizeY;
        private final int interactType;
        private final int blockingMask;
        private final boolean blocksProjectile;
        private final boolean obstructsGround;
        private final int wallOrDoor;
        private final String actions;
        private final Footprint footprint;

        private PlacementRow(Location location, ObjectDefinition def, int anchorX, int anchorY, Footprint footprint)
        {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.plane = location.getPosition().getZ();
            this.objectId = location.getId();
            this.name = def == null || def.getName() == null ? "(unknown)" : def.getName();
            this.locType = location.getType();
            this.orientation = location.getOrientation();
            this.sizeX = def == null ? 1 : Math.max(1, def.getSizeX());
            this.sizeY = def == null ? 1 : Math.max(1, def.getSizeY());
            this.interactType = def == null ? -1 : def.getInteractType();
            this.blockingMask = def == null ? -1 : def.getBlockingMask();
            this.blocksProjectile = def != null && def.isBlocksProjectile();
            this.obstructsGround = def != null && def.isObstructsGround();
            this.wallOrDoor = def == null ? -1 : def.getWallOrDoor();
            this.actions = actions(def);
            this.footprint = footprint;
        }
    }
}
