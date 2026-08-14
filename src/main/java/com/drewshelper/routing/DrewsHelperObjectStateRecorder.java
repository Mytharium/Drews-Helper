package com.drewshelper.routing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Records stateful scene objects exactly as the live client sees them.
 *
 * <p>This is evidence-only. It does not add transports, mutate the collision map, or decide whether
 * a row should be promoted. The important part is keeping the live state attached to the object:
 * base id, active impostor id, actions, var state hooks, live edge flags, and collision provenance.
 */
public final class DrewsHelperObjectStateRecorder
{
    static final String ROW_PREFIX = "DREW_OBJECT_STATE v1";
    private static final String LIVE_PROVENANCE = "runelite-scene-live";
    private static final String[] STATE_ACTIONS = {
        "Open", "Close", "Unlock", "Lock", "Push", "Pull", "Operate", "Activate",
        "Deactivate", "Lift", "Lower", "Raise", "Repair", "Fix", "Build", "Pay", "Use"
    };
    private static final String[] TRAVERSAL_ACTIONS = {
        "Climb", "Climb-up", "Climb-down", "Cross", "Jump", "Squeeze", "Squeeze-through",
        "Crawl-through", "Enter", "Exit", "Pass", "Pass-through", "Go-through", "Walk-across",
        "Travel", "Board", "Leave", "Traverse"
    };
    private static final String[] SAILING_ACTIONS = {
        "Sail", "Set-sail", "Set sail", "Embark", "Disembark", "Dock", "Moor", "Board",
        "Travel"
    };
    private static final String[] SAILING_DIRECT_ACTION_TOKENS = {
        "sail", "set sail", "embark", "disembark", "dock", "moor"
    };
    private static final String[] SAILING_NAME_TOKENS = {
        "sailing", "gangplank", "mooring", "moor", "dock", "pier", "quay", "ship", "boat",
        "barge", "raft", "rowboat"
    };

    private final File output;
    private final Set<String> seenStateRows = new HashSet<>();
    private boolean writeWarned;

    public DrewsHelperObjectStateRecorder(File output)
    {
        this.output = output;
    }

    public List<String> recordScene(Client client, DrewsHelperCollisionMap collisionMap, int tick)
    {
        if (client == null)
        {
            return Collections.emptyList();
        }

        Scene scene = client.getScene();
        if (scene == null || scene.getTiles() == null)
        {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        Tile[][][] tiles = scene.getTiles();
        CollisionData[] collision = client.getCollisionMaps();
        int baseX = client.getBaseX();
        int baseY = client.getBaseY();
        for (int plane = 0; plane < tiles.length; plane++)
        {
            scanPlane(lines, client, collisionMap, collision, tiles, baseX, baseY, plane, tick);
        }

        if (!lines.isEmpty())
        {
            append(lines);
        }
        return lines;
    }

    public void reset()
    {
        seenStateRows.clear();
    }

    public boolean hasWriteFailed()
    {
        return writeWarned;
    }

    private void scanPlane(
        List<String> lines,
        Client client,
        DrewsHelperCollisionMap collisionMap,
        CollisionData[] collision,
        Tile[][][] tiles,
        int baseX,
        int baseY,
        int plane,
        int tick
    )
    {
        if (tiles[plane] == null)
        {
            return;
        }

        Tile[][] planeTiles = tiles[plane];
        for (int sx = 0; sx < planeTiles.length; sx++)
        {
            if (planeTiles[sx] == null)
            {
                continue;
            }
            for (int sy = 0; sy < planeTiles[sx].length; sy++)
            {
                Tile tile = planeTiles[sx][sy];
                if (tile == null)
                {
                    continue;
                }
                WorldPoint fallback = new WorldPoint(baseX + sx, baseY + sy, plane);
                recordTile(lines, client, collisionMap, collision, baseX, baseY, tick, fallback, tile);
            }
        }
    }

    private void recordTile(
        List<String> lines,
        Client client,
        DrewsHelperCollisionMap collisionMap,
        CollisionData[] collision,
        int baseX,
        int baseY,
        int tick,
        WorldPoint fallback,
        Tile tile
    )
    {
        WallObject wall = tile.getWallObject();
        if (wall != null)
        {
            recordObject(lines, client, collisionMap, collision, baseX, baseY, tick,
                ObservedObject.wall(wall, fallback));
        }

        DecorativeObject decorative = tile.getDecorativeObject();
        if (decorative != null)
        {
            recordObject(lines, client, collisionMap, collision, baseX, baseY, tick,
                ObservedObject.decorative(decorative, fallback));
        }

        GroundObject ground = tile.getGroundObject();
        if (ground != null)
        {
            recordObject(lines, client, collisionMap, collision, baseX, baseY, tick,
                ObservedObject.ground(ground, fallback));
        }

        GameObject[] gameObjects = tile.getGameObjects();
        if (gameObjects == null)
        {
            return;
        }
        for (GameObject gameObject : gameObjects)
        {
            if (gameObject != null)
            {
                recordObject(lines, client, collisionMap, collision, baseX, baseY, tick,
                    ObservedObject.game(gameObject, fallback));
            }
        }
    }

    private void recordObject(
        List<String> lines,
        Client client,
        DrewsHelperCollisionMap collisionMap,
        CollisionData[] collision,
        int baseX,
        int baseY,
        int tick,
        ObservedObject object
    )
    {
        ObjectComposition base = client.getObjectDefinition(object.objectId);
        ObjectComposition active = DrewsHelperObjectDefinitions.active(base);
        String[] actions = DrewsHelperObjectDefinitions.activeActions(base);
        if (!isStateCandidate(object.kind, base, active, actions))
        {
            return;
        }

        String scene = baseX + ":" + baseY + ":" + object.tile.getPlane();
        String body = formatBody(scene, object, base, active, actions,
            liveEdges(collision, object.tile, baseX, baseY),
            rawFlags(collision, object.tile, baseX, baseY),
            mapProvenance(collisionMap, object.tile));
        if (seenStateRows.add(body))
        {
            lines.add(ROW_PREFIX + " tick=" + tick + " " + body);
        }
    }

    static String formatBody(
        String scene,
        ObservedObject object,
        ObjectComposition base,
        ObjectComposition active,
        String[] actions,
        String liveEdges,
        String rawFlags,
        DrewsHelperDataProvenance mapProvenance
    )
    {
        ObjectComposition selected = active == null ? base : active;
        int activeId = selected == null ? object.objectId : selected.getId();
        String name = selected == null ? "-" : DrewsHelperObjectDefinitions.sanitise(selected.getName());
        String category = category(object.kind, name, actions, active);
        String state = state(name, actions, active);
        DrewsHelperDataProvenance safeMapProvenance = mapProvenance == null
            ? new DrewsHelperDataProvenance(DrewsHelperDataConfidence.CONTRADICTED, "missing-map-provenance")
            : mapProvenance;

        return "scene=" + safe(scene)
            + " kind=" + object.kind
            + " tile=" + point(object.tile)
            + " sceneTile=" + object.sceneX + "," + object.sceneY
            + " objectId=" + object.objectId
            + " activeId=" + activeId
            + " activeChanged=" + (active != null && active.getId() != object.objectId)
            + " category=" + category
            + " state=" + state
            + " name=" + name
            + " actions=" + DrewsHelperObjectDefinitions.actionTokenList(actions)
            + " varbit=" + intOrDash(base == null ? -1 : base.getVarbitId())
            + " varp=" + intOrDash(base == null ? -1 : base.getVarPlayerId())
            + " objectSize=" + object.size
            + " definitionSize=" + definitionSize(selected)
            + " orientation=" + safe(object.orientation)
            + " config=" + intOrDash(object.config)
            + " hash=" + object.hash
            + " liveEdges=" + liveEdges
            + " rawFlags=" + rawFlags
            + " confidence=" + DrewsHelperDataConfidence.CONFIRMED
            + " provenance=" + LIVE_PROVENANCE
            + " mapConfidence=" + safeMapProvenance.getConfidence()
            + " mapProvenance=" + DrewsHelperObjectDefinitions.sanitise(safeMapProvenance.getSource());
    }

    static boolean isStateCandidate(
        String kind,
        ObjectComposition base,
        ObjectComposition active,
        String[] actions
    )
    {
        if (base == null)
        {
            return false;
        }
        return base.getImpostorIds() != null
            || active != null
            || isSailingCandidate(base.getName(), actions)
            || hasAnyAction(actions, STATE_ACTIONS)
            || hasAnyAction(actions, TRAVERSAL_ACTIONS)
            || isDoorLike(kind, base.getName(), actions);
    }

    static String state(String[] actions, ObjectComposition active)
    {
        return state("", actions, active);
    }

    static String state(String nameToken, String[] actions, ObjectComposition active)
    {
        boolean open = DrewsHelperObjectDefinitions.hasAction(actions, "Open");
        boolean close = DrewsHelperObjectDefinitions.hasAction(actions, "Close");
        if (open && !close)
        {
            return "CLOSED_OPENABLE";
        }
        if (close && !open)
        {
            return "OPEN_CLOSEABLE";
        }
        if (open)
        {
            return "OPEN_AND_CLOSE_ACTIONS";
        }
        if (hasAnyAction(actions, STATE_ACTIONS))
        {
            return "STATEFUL_ACTION";
        }
        if (isSailingCandidate(nameToken, actions))
        {
            return "SAILING_ACCESS";
        }
        if (hasAnyAction(actions, TRAVERSAL_ACTIONS))
        {
            return "TRAVERSAL_ACTION";
        }
        return active == null ? "INTERACTIVE" : "IMPOSTOR_STATE";
    }

    static String category(String kind, String nameToken, String[] actions, ObjectComposition active)
    {
        if (isDoorLike(kind, nameToken, actions))
        {
            return "door";
        }
        if (isSailingCandidate(nameToken, actions))
        {
            return "sailing";
        }
        if (hasAnyAction(actions, TRAVERSAL_ACTIONS))
        {
            return "traversal";
        }
        if (active != null || hasAnyAction(actions, STATE_ACTIONS))
        {
            return "stateful";
        }
        return "object";
    }

    private static boolean isDoorLike(String kind, String name, String[] actions)
    {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("door") || normalized.contains("gate")
            || normalized.contains("portcullis") || normalized.contains("barrier")
            || normalized.contains("grille"))
        {
            return true;
        }
        return "wall".equals(kind)
            && (DrewsHelperObjectDefinitions.hasAction(actions, "Open")
                || DrewsHelperObjectDefinitions.hasAction(actions, "Close"));
    }

    static boolean isSailingCandidate(String name, String[] actions)
    {
        String normalizedName = normalize(name);
        boolean sailingNamed = containsAny(normalizedName, SAILING_NAME_TOKENS);
        boolean directSailingAction = hasDirectSailingAction(actions);
        if (sailingNamed && (directSailingAction
            || hasAnyAction(actions, SAILING_ACTIONS)
            || hasAnyAction(actions, TRAVERSAL_ACTIONS)))
        {
            return true;
        }

        // A direct sailing verb is strong enough evidence even when the object's name is generic.
        return directSailingAction;
    }

    private static boolean hasDirectSailingAction(String[] actions)
    {
        if (actions == null)
        {
            return false;
        }
        for (String action : actions)
        {
            String normalized = normalize(DrewsHelperObjectDefinitions.plainText(action));
            if (containsAny(normalized, SAILING_DIRECT_ACTION_TOKENS))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String normalized, String[] tokens)
    {
        if (normalized == null || tokens == null)
        {
            return false;
        }
        for (String token : tokens)
        {
            if (normalized.contains(token))
            {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static boolean hasAnyAction(String[] actions, String[] expected)
    {
        if (expected == null)
        {
            return false;
        }
        for (String action : expected)
        {
            if (DrewsHelperObjectDefinitions.hasAction(actions, action))
            {
                return true;
            }
        }
        return false;
    }

    private static String definitionSize(ObjectComposition composition)
    {
        return composition == null ? "-" : composition.getSizeX() + "x" + composition.getSizeY();
    }

    private static DrewsHelperDataProvenance mapProvenance(
        DrewsHelperCollisionMap collisionMap,
        WorldPoint point
    )
    {
        if (collisionMap == null)
        {
            return new DrewsHelperDataProvenance(
                DrewsHelperDataConfidence.CONTRADICTED,
                "missing-collision-map"
            );
        }
        return collisionMap.provenanceAt(point);
    }

    private static String liveEdges(CollisionData[] collision, WorldPoint point, int baseX, int baseY)
    {
        int[][] flags = flagsFor(collision, point);
        if (flags == null)
        {
            return "-";
        }
        int sx = point.getX() - baseX;
        int sy = point.getY() - baseY;
        if (sx < 0 || sy < 0 || sx + 1 >= flags.length || flags[sx] == null
            || sy + 1 >= flags[sx].length)
        {
            return "-";
        }
        int mask = DrewsHelperMapValidator.liveBlockedMask(flags, sx, sy);
        return ((mask & 1) != 0 ? "1" : "0") + ((mask & 2) != 0 ? "1" : "0");
    }

    private static String rawFlags(CollisionData[] collision, WorldPoint point, int baseX, int baseY)
    {
        int[][] flags = flagsFor(collision, point);
        if (flags == null)
        {
            return "-";
        }
        int sx = point.getX() - baseX;
        int sy = point.getY() - baseY;
        if (sx < 0 || sy < 0 || sx >= flags.length || flags[sx] == null || sy >= flags[sx].length)
        {
            return "-";
        }
        return Integer.toString(flags[sx][sy]);
    }

    private static int[][] flagsFor(CollisionData[] collision, WorldPoint point)
    {
        if (collision == null || point == null || point.getPlane() < 0 || point.getPlane() >= collision.length
            || collision[point.getPlane()] == null)
        {
            return null;
        }
        return collision[point.getPlane()].getFlags();
    }

    private static String point(WorldPoint point)
    {
        return point == null ? "-" : point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private static String intOrDash(int value)
    {
        return value < 0 ? "-" : Integer.toString(value);
    }

    private static String safe(String value)
    {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private void append(List<String> lines)
    {
        if (output == null)
        {
            return;
        }
        try
        {
            Files.write(output.toPath(), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ex)
        {
            writeWarned = true;
        }
    }

    static final class ObservedObject
    {
        private final String kind;
        private final int objectId;
        private final WorldPoint tile;
        private final int sceneX;
        private final int sceneY;
        private final String orientation;
        private final String size;
        private final int config;
        private final long hash;

        ObservedObject(
            String kind,
            int objectId,
            WorldPoint tile,
            int sceneX,
            int sceneY,
            String orientation,
            String size,
            int config,
            long hash
        )
        {
            this.kind = kind;
            this.objectId = objectId;
            this.tile = tile;
            this.sceneX = sceneX;
            this.sceneY = sceneY;
            this.orientation = orientation;
            this.size = size;
            this.config = config;
            this.hash = hash;
        }

        static ObservedObject wall(WallObject object, WorldPoint fallback)
        {
            return new ObservedObject(
                "wall",
                object.getId(),
                pointOrFallback(object, fallback),
                sceneX(object, fallback),
                sceneY(object, fallback),
                object.getOrientationA() + "/" + object.getOrientationB(),
                "1x1",
                object.getConfig(),
                object.getHash()
            );
        }

        static ObservedObject decorative(DecorativeObject object, WorldPoint fallback)
        {
            return new ObservedObject(
                "decorative",
                object.getId(),
                pointOrFallback(object, fallback),
                sceneX(object, fallback),
                sceneY(object, fallback),
                object.getXOffset() + "/" + object.getYOffset() + "/"
                    + object.getXOffset2() + "/" + object.getYOffset2(),
                "1x1",
                object.getConfig(),
                object.getHash()
            );
        }

        static ObservedObject ground(GroundObject object, WorldPoint fallback)
        {
            return new ObservedObject(
                "ground",
                object.getId(),
                pointOrFallback(object, fallback),
                sceneX(object, fallback),
                sceneY(object, fallback),
                "-",
                "1x1",
                object.getConfig(),
                object.getHash()
            );
        }

        static ObservedObject game(GameObject object, WorldPoint fallback)
        {
            return new ObservedObject(
                "game",
                object.getId(),
                pointOrFallback(object, fallback),
                sceneX(object, fallback),
                sceneY(object, fallback),
                Integer.toString(object.getOrientation()),
                object.sizeX() + "x" + object.sizeY(),
                object.getConfig(),
                object.getHash()
            );
        }

        private static WorldPoint pointOrFallback(TileObject object, WorldPoint fallback)
        {
            WorldPoint point = object.getWorldLocation();
            return point == null ? fallback : point;
        }

        private static int sceneX(TileObject object, WorldPoint fallback)
        {
            LocalPoint local = object.getLocalLocation();
            if (local != null)
            {
                return local.getSceneX();
            }
            return fallback == null ? -1 : fallback.getX() & 0x3f;
        }

        private static int sceneY(TileObject object, WorldPoint fallback)
        {
            LocalPoint local = object.getLocalLocation();
            if (local != null)
            {
                return local.getSceneY();
            }
            return fallback == null ? -1 : fallback.getY() & 0x3f;
        }
    }
}
