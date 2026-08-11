package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.DrewsHelperWaypointIcon;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperTransportEdge;
import com.drewshelper.routing.DrewsHelperTransportGraph;
import com.drewshelper.routing.DrewsHelperTravelEstimate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

public final class DrewsHelperRouteTileOverlay extends Overlay
{
    private static final int WAYPOINT_ICON_SIZE = 24;
    private static final Color TRANSPORT_FILL = new Color(0, 255, 255, 120);
    private static final Color TRANSPORT_OUTLINE = new Color(0, 255, 255, 255);
    private static final BasicStroke TRANSPORT_STROKE = new BasicStroke(2f);
    /** An NPC wanders off its transport tile, so match on proximity rather than an exact match. */
    private static final int NPC_SEARCH_RADIUS = 15;
    /** Height above the marked tile for its label, matching RuneLite's own object markers. */
    private static final int TILE_LABEL_HEIGHT = 40;
    /** Cap per-frame door outlines so very long routes cannot flood scene rendering. */
    private static final int MAX_DOOR_HIGHLIGHTS = 64;
    /** How far to either side of the crossed edge a single doorway is allowed to extend. */
    private static final int MAX_DOORWAY_WIDTH = 3;
    /** OSRS wall-orientation bits carried by wall objects on their tile. */
    static final int WALL_WEST = 1;
    static final int WALL_NORTH = 2;
    static final int WALL_EAST = 4;
    static final int WALL_SOUTH = 8;

    private final Client client;
    private final DrewsHelperPlugin plugin;
    private final DrewsHelperConfig config;
    private final Map<Integer, Boolean> doorLikeCache = new HashMap<>();

    @Inject
    public DrewsHelperRouteTileOverlay(Client client, DrewsHelperPlugin plugin, DrewsHelperConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.pathingReplacementEnabled())
        {
            return null;
        }

        DrewsHelperRouteSnapshot snapshot = plugin.getRouteSnapshot();
        if (snapshot.hasPath())
        {
            Color color = translucentPathColor();
            for (WorldPoint point : snapshot.getPath())
            {
                drawTile(graphics, point, color);
            }
            drawTransportEndpoints(graphics, snapshot);
            drawDoorSteps(graphics, snapshot);
        }

        drawWaypointEndpoints(graphics);

        return null;
    }

    private void drawWaypointEndpoints(Graphics2D graphics)
    {
        for (int index = 0; index < DrewsHelperPlugin.MAX_WAYPOINTS; index++)
        {
            WorldPoint waypoint = plugin.getWaypoint(index);
            if (waypoint != null)
            {
                drawWaypointEndpoint(graphics, waypoint, index);
            }
        }
    }

    private void drawWaypointEndpoint(Graphics2D graphics, WorldPoint waypoint, int index)
    {
        Polygon tile = canvasTile(waypoint);
        if (tile == null)
        {
            return;
        }

        graphics.setColor(translucentWaypointColor(index));
        graphics.fill(tile);
        graphics.setColor(Color.WHITE);
        graphics.draw(tile);
        drawWaypointIcon(graphics, tile, index);
    }

    /**
     * Marks every transport hop on the route - and marks the thing you actually click.
     *
     * <p>Highlighting the tile alone was not enough. Elkoy walks, so the tile is where he *was*;
     * a spirit tree is an object, not a tile at all. What the player needs outlined is the NPC
     * or the scene object carrying the menu option.
     *
     * <p>The id is already in the data. Upstream labels end in the id of the thing you interact
     * with - "Follow Elkoy 4968", "Squeeze-through Loose Railing 2186" - which is the same digits
     * the HUD label strips for readability. Whether a given id is an NPC or an object is not
     * recorded anywhere, so this does not try to know: it looks for both and highlights whichever
     * exists. The ambiguity never has to be resolved.
     *
     * <p>Cyan deliberately: it is the one strong colour absent from the configurable path and
     * waypoint palette, so a highlight can never be confused with a route line the user chose.
     */
    private void drawTransportEndpoints(Graphics2D graphics, DrewsHelperRouteSnapshot snapshot)
    {
        DrewsHelperTransportGraph graph = plugin.getTransportGraph();
        List<WorldPoint> path = snapshot.getPath();

        for (int index = 0; index + 1 < path.size(); index++)
        {
            WorldPoint from = path.get(index);
            WorldPoint to = path.get(index + 1);
            if (!DrewsHelperRouteSnapshot.isTransportJump(from, to))
            {
                continue;
            }

            String label = graph == null
                ? null
                : DrewsHelperTravelEstimate.transportLabel(graph, from, to);

            // The far side is a landing tile - there is nothing to click there, so the tile
            // marker is the right and only marker for it.
            drawTransportTile(graphics, to, label);

            int targetId = graph == null ? -1 : DrewsHelperTravelEstimate.targetId(graph, from, to);
            if (targetId >= 0 && drawInteractable(graphics, from, targetId))
            {
                continue;
            }

            // Nothing resolvable in the loaded scene: fall back to marking the tile, which is
            // still better than drawing nothing.
            drawTransportTile(graphics, from, label);
        }
    }

    /**
     * Door edges are one-tile walking steps, so the transport-jump heuristic cannot see them.
     * They still come from route data or loaded scene walls and need the same cyan object
     * outline as other click targets.
     */
    private void drawDoorSteps(Graphics2D graphics, DrewsHelperRouteSnapshot snapshot)
    {
        DrewsHelperTransportGraph graph = plugin.getTransportGraph();
        List<WorldPoint> path = snapshot.getPath();
        Set<Long> outlinedEdges = new HashSet<>();
        int outlines = 0;

        for (int index = 0; index + 1 < path.size() && outlines < MAX_DOOR_HIGHLIGHTS; index++)
        {
            WorldPoint from = path.get(index);
            WorldPoint to = path.get(index + 1);
            if (DrewsHelperRouteSnapshot.isTransportJump(from, to))
            {
                continue;
            }

            int bit = crossedWallBit(from, to);
            if (bit == 0)
            {
                continue;
            }
            if (from.getPlane() != client.getPlane())
            {
                continue;
            }

            if (!outlinedEdges.add(doorEdgeKey(from, to, bit)))
            {
                continue;
            }

            if (graph != null)
            {
                boolean graphBacked = false;
                for (DrewsHelperTransportEdge edge : graph.edgesFrom(from))
                {
                    if (to.equals(edge.getDestination()))
                    {
                        graphBacked = true;
                        break;
                    }
                }

                if (graphBacked)
                {
                    int targetId = DrewsHelperTravelEstimate.targetId(graph, from, to);
                    if (targetId >= 0 && drawInteractable(graphics, from, targetId))
                    {
                        outlines++;
                        outlines += outlineDoorwayLeaves(graphics, from, to, bit, false,
                            outlinedEdges, MAX_DOOR_HIGHLIGHTS - outlines);
                        continue;
                    }
                }
            }

            Tile fromTile = sceneTile(from);
            WallObject fromWall = fromTile == null ? null : fromTile.getWallObject();
            if (fromWall != null && ((fromWall.getOrientationA() | fromWall.getOrientationB()) & bit) != 0
                && drawDoorWallObject(graphics, fromWall))
            {
                outlines++;
                outlines += outlineDoorwayLeaves(graphics, from, to, bit, false,
                    outlinedEdges, MAX_DOOR_HIGHLIGHTS - outlines);
                continue;
            }

            Tile toTile = sceneTile(to);
            WallObject toWall = toTile == null ? null : toTile.getWallObject();
            int oppositeBit = oppositeWallBit(bit);
            if (toWall != null && ((toWall.getOrientationA() | toWall.getOrientationB()) & oppositeBit) != 0
                && drawDoorWallObject(graphics, toWall))
            {
                outlines++;
                outlines += outlineDoorwayLeaves(graphics, from, to, bit, false,
                    outlinedEdges, MAX_DOOR_HIGHLIGHTS - outlines);
                continue;
            }

            // An open door has swung, so its recorded orientation may no longer cross the path.
            if (fromWall != null && drawDoorWallObject(graphics, fromWall))
            {
                outlines++;
                outlines += outlineDoorwayLeaves(graphics, from, to, bit, true,
                    outlinedEdges, MAX_DOOR_HIGHLIGHTS - outlines);
            }
        }
    }

    /**
     * Outlines the remaining leaves of the doorway the route just crossed.
     *
     * <p>A route only ever crosses one tile edge, so a two-tile doorway lights one leaf and
     * leaves the other dark. Both leaves open together, so both are part of the same crossing.
     *
     * <p>Walks outward along the wall - perpendicular to the step - and stops at the first tile
     * with no leaf on it. A doorway is contiguous; carrying on past a gap would start lighting
     * unrelated doors further down the same wall.
     *
     * @param lenient true when the primary leaf itself only matched with the orientation test
     *     dropped. An open double door has BOTH leaves swung, so neither still records an
     *     orientation across the path - but relaxing this unconditionally starts matching doors
     *     on the perpendicular walls beside the opening.
     * @return how many extra leaves were outlined
     */
    private int outlineDoorwayLeaves(Graphics2D graphics, WorldPoint from, WorldPoint to, int bit,
        boolean lenient, Set<Long> outlinedEdges, int budget)
    {
        int runDx = doorwayRunDx(bit);
        int runDy = doorwayRunDy(bit);
        if ((runDx == 0 && runDy == 0) || budget <= 0)
        {
            return 0;
        }

        int drawn = 0;
        for (int dir = -1; dir <= 1; dir += 2)
        {
            for (int step = 1; step <= MAX_DOORWAY_WIDTH && drawn < budget; step++)
            {
                int offsetX = runDx * step * dir;
                int offsetY = runDy * step * dir;
                WorldPoint leafFrom = from.dx(offsetX).dy(offsetY);
                WorldPoint leafTo = to.dx(offsetX).dy(offsetY);

                // Already outlined - the route crossed this edge too. The doorway is still
                // contiguous here, so keep walking rather than stopping.
                if (!outlinedEdges.add(doorEdgeKey(leafFrom, leafTo, bit)))
                {
                    continue;
                }

                if (!outlineDoorLeaf(graphics, leafFrom, leafTo, bit, lenient))
                {
                    break;
                }
                drawn++;
            }
        }
        return drawn;
    }

    /** @return true when a door-like wall object sits on this edge and was outlined */
    private boolean outlineDoorLeaf(Graphics2D graphics, WorldPoint from, WorldPoint to, int bit,
        boolean lenient)
    {
        Tile fromTile = sceneTile(from);
        WallObject fromWall = fromTile == null ? null : fromTile.getWallObject();
        if (fromWall != null && ((fromWall.getOrientationA() | fromWall.getOrientationB()) & bit) != 0
            && drawDoorWallObject(graphics, fromWall))
        {
            return true;
        }

        Tile toTile = sceneTile(to);
        WallObject toWall = toTile == null ? null : toTile.getWallObject();
        int oppositeBit = oppositeWallBit(bit);
        if (toWall != null && ((toWall.getOrientationA() | toWall.getOrientationB()) & oppositeBit) != 0
            && drawDoorWallObject(graphics, toWall))
        {
            return true;
        }

        return lenient && fromWall != null && drawDoorWallObject(graphics, fromWall);
    }

    private boolean drawDoorWallObject(Graphics2D graphics, WallObject wallObject)
    {
        Shape clickbox = wallObject.getClickbox();
        if (clickbox == null)
        {
            return false;
        }

        if (!isDoorLike(wallObject.getId()))
        {
            return false;
        }

        outline(graphics, clickbox);
        return true;
    }

    private boolean isDoorLike(int objectId)
    {
        ObjectComposition composition = client.getObjectDefinition(objectId);
        if (composition == null)
        {
            return false;
        }

        // getImpostor() throws on any object that has no impostor configuration at all, which is
        // most of them - it must never be called without this guard. matchesObjectId below only
        // survives because it reaches getImpostor() after an identical getImpostorIds() null check.
        ObjectComposition active = null;
        if (composition.getImpostorIds() != null)
        {
            active = composition.getImpostor();
        }

        if (active == null)
        {
            Boolean cached = doorLikeCache.get(objectId);
            if (cached != null)
            {
                return cached;
            }
        }

        String[] actions = active == null ? composition.getActions() : active.getActions();
        boolean doorLike = false;
        if (actions != null)
        {
            for (String action : actions)
            {
                // Open and Close both matter because the route threads the door either way.
                if ("Open".equalsIgnoreCase(action) || "Close".equalsIgnoreCase(action))
                {
                    doorLike = true;
                    break;
                }
            }
        }

        // Impostors can change at runtime, so only stable base definitions are cached.
        if (active == null)
        {
            doorLikeCache.put(objectId, doorLike);
        }
        return doorLike;
    }

    /**
     * @return true when something was actually outlined, so the caller knows whether the tile
     *     fallback is still needed
     */
    private boolean drawInteractable(Graphics2D graphics, WorldPoint origin, int targetId)
    {
        Shape npc = npcShape(origin, targetId);
        if (npc != null)
        {
            outline(graphics, npc);
            return true;
        }

        Shape object = objectShape(origin, targetId);
        if (object != null)
        {
            outline(graphics, object);
            return true;
        }

        return false;
    }

    private Shape npcShape(WorldPoint origin, int targetId)
    {
        for (NPC npc : client.getNpcs())
        {
            if (npc == null || npc.getId() != targetId)
            {
                continue;
            }

            // An NPC wanders, so match on proximity to the transport rather than an exact tile.
            WorldPoint location = npc.getWorldLocation();
            if (location == null || location.getPlane() != origin.getPlane()
                || location.distanceTo(origin) > NPC_SEARCH_RADIUS)
            {
                continue;
            }

            return npc.getConvexHull();
        }
        return null;
    }

    private Shape objectShape(WorldPoint origin, int targetId)
    {
        // Indexed straight into the scene rather than scanned - this runs every frame, and a
        // full scene sweep per transport would be a real cost for no benefit. An object can be
        // anchored on a neighbouring tile, so the immediate ring is checked too.
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                Tile tile = sceneTile(origin.dx(dx).dy(dy));
                if (tile == null)
                {
                    continue;
                }

                Shape shape = matchingObject(tile, targetId);
                if (shape != null)
                {
                    return shape;
                }
            }
        }
        return null;
    }

    private Shape matchingObject(Tile tile, int targetId)
    {
        GameObject[] gameObjects = tile.getGameObjects();
        if (gameObjects != null)
        {
            for (GameObject gameObject : gameObjects)
            {
                if (gameObject != null && matchesObjectId(gameObject.getId(), targetId))
                {
                    return gameObject.getConvexHull();
                }
            }
        }

        TileObject[] singles = {tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()};
        for (TileObject single : singles)
        {
            if (single != null && matchesObjectId(single.getId(), targetId))
            {
                return single.getClickbox();
            }
        }

        return null;
    }

    /**
     * Whether a scene object is the thing the transport row names.
     *
     * <p>A direct id comparison is not enough. Objects whose appearance depends on game state -
     * and a spirit tree is exactly that - are placed in the scene under a base id and swapped at
     * runtime to one of several "impostor" ids. The transport data records the id the player
     * clicks, which for those objects is the impostor, not the base. Comparing only the scene id
     * is why gates and agility shortcuts highlighted and spirit trees never did.
     */
    private boolean matchesObjectId(int sceneId, int targetId)
    {
        if (sceneId == targetId)
        {
            return true;
        }

        ObjectComposition composition = client.getObjectDefinition(sceneId);
        if (composition == null)
        {
            return false;
        }

        int[] impostorIds = composition.getImpostorIds();
        if (impostorIds == null)
        {
            return false;
        }

        for (int impostorId : impostorIds)
        {
            if (impostorId == targetId)
            {
                return true;
            }
        }

        // The currently active impostor, in case the id list is not exhaustive.
        ObjectComposition active = composition.getImpostor();
        return active != null && active.getId() == targetId;
    }

    private Tile sceneTile(WorldPoint point)
    {
        if (point.getPlane() != client.getPlane())
        {
            return null;
        }

        LocalPoint local = LocalPoint.fromWorld(client, point);
        if (local == null)
        {
            return null;
        }

        Tile[][][] tiles = client.getScene().getTiles();
        int plane = point.getPlane();
        int x = local.getSceneX();
        int y = local.getSceneY();
        if (plane < 0 || plane >= tiles.length
            || x < 0 || x >= tiles[plane].length
            || y < 0 || y >= tiles[plane][x].length)
        {
            return null;
        }

        return tiles[plane][x][y];
    }

    private void outline(Graphics2D graphics, Shape shape)
    {
        graphics.setColor(TRANSPORT_FILL);
        graphics.fill(shape);
        graphics.setStroke(TRANSPORT_STROKE);
        graphics.setColor(TRANSPORT_OUTLINE);
        graphics.draw(shape);
    }

    /**
     * A cyan square on its own says "something happens here" without saying what. An
     * originless teleport has no object to outline and no menu entry to read, so the label is
     * the only thing in the world telling you where the hop puts you.
     */
    private void drawTransportTile(Graphics2D graphics, WorldPoint point, String label)
    {
        Polygon tile = canvasTile(point);
        if (tile == null)
        {
            return;
        }

        graphics.setColor(TRANSPORT_FILL);
        graphics.fill(tile);
        graphics.setStroke(TRANSPORT_STROKE);
        graphics.setColor(TRANSPORT_OUTLINE);
        graphics.draw(tile);

        if (label == null || label.isEmpty())
        {
            return;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client, point);
        if (localPoint == null)
        {
            return;
        }

        Point text = Perspective.getCanvasTextLocation(
            client, graphics, localPoint, label, TILE_LABEL_HEIGHT);
        if (text != null)
        {
            OverlayUtil.renderTextLocation(graphics, text, label, Color.WHITE);
        }
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color)
    {
        Polygon tile = canvasTile(point);
        if (tile == null)
        {
            return;
        }

        graphics.setColor(color);
        graphics.fill(tile);
    }

    private Polygon canvasTile(WorldPoint point)
    {
        if (point.getPlane() != client.getPlane())
        {
            return null;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client, point);
        if (localPoint == null)
        {
            return null;
        }

        return Perspective.getCanvasTilePoly(client, localPoint);
    }

    private void drawWaypointIcon(Graphics2D graphics, Polygon tile, int index)
    {
        Rectangle bounds = tile.getBounds();
        BufferedImage icon = DrewsHelperWaypointIcon.createImage(index + 1, plugin.getWaypointColor(index), WAYPOINT_ICON_SIZE);
        int x = (int) bounds.getCenterX() - icon.getWidth() / 2;
        int y = (int) bounds.getCenterY() - icon.getHeight() / 2;
        graphics.drawImage(icon, x, y, null);
    }

    private Color translucentPathColor()
    {
        Color color = config.pathColor();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 150);
    }

    private Color translucentWaypointColor(int index)
    {
        Color color = plugin.getWaypointColor(index);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 210);
    }

    static int waypointIconSize()
    {
        return WAYPOINT_ICON_SIZE;
    }

    static int crossedWallBit(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null)
        {
            return 0;
        }
        if (from.getPlane() != to.getPlane())
        {
            return 0;
        }

        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (Math.abs(dx) + Math.abs(dy) != 1)
        {
            return 0;
        }
        if (dy == 1)
        {
            return WALL_NORTH;
        }
        if (dy == -1)
        {
            return WALL_SOUTH;
        }
        if (dx == 1)
        {
            return WALL_EAST;
        }
        if (dx == -1)
        {
            return WALL_WEST;
        }
        return 0;
    }

    static int oppositeWallBit(int bit)
    {
        if (bit == WALL_NORTH)
        {
            return WALL_SOUTH;
        }
        if (bit == WALL_SOUTH)
        {
            return WALL_NORTH;
        }
        if (bit == WALL_EAST)
        {
            return WALL_WEST;
        }
        if (bit == WALL_WEST)
        {
            return WALL_EAST;
        }
        return 0;
    }

    /**
     * One key per physical tile edge, whichever way it is crossed.
     *
     * <p>Normalised onto the southern/western tile of the pair plus the north/east bit, so
     * "north edge of A" and "south edge of B" collapse to the same key.
     */
    static long doorEdgeKey(WorldPoint from, WorldPoint to, int bit)
    {
        int edgeX = from.getX();
        int edgeY = from.getY();
        int edgeBit = bit;
        if (bit == WALL_SOUTH)
        {
            edgeY = to.getY();
            edgeBit = WALL_NORTH;
        }
        else if (bit == WALL_WEST)
        {
            edgeX = to.getX();
            edgeBit = WALL_EAST;
        }

        return (((long) from.getPlane() & 0x3L) << 32)
            | (((long) edgeX & 0x7FFFL) << 17)
            | (((long) edgeY & 0x7FFFL) << 2)
            | (edgeBit == WALL_NORTH ? 0L : 1L);
    }

    /**
     * The axis a doorway runs along, which is perpendicular to the step that crosses it: cross a
     * north or south edge and the wall runs east-west, so its other leaves are at x +/- 1.
     */
    static int doorwayRunDx(int bit)
    {
        if (bit == WALL_NORTH || bit == WALL_SOUTH)
        {
            return 1;
        }
        return 0;
    }

    static int doorwayRunDy(int bit)
    {
        if (bit == WALL_EAST || bit == WALL_WEST)
        {
            return 1;
        }
        return 0;
    }
}
