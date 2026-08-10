package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.DrewsHelperWaypointIcon;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
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
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public final class DrewsHelperRouteTileOverlay extends Overlay
{
    private static final int WAYPOINT_ICON_SIZE = 24;
    private static final Color TRANSPORT_FILL = new Color(0, 255, 255, 120);
    private static final Color TRANSPORT_OUTLINE = new Color(0, 255, 255, 255);
    private static final BasicStroke TRANSPORT_STROKE = new BasicStroke(2f);
    /** An NPC wanders off its transport tile, so match on proximity rather than an exact match. */
    private static final int NPC_SEARCH_RADIUS = 15;

    private final Client client;
    private final DrewsHelperPlugin plugin;
    private final DrewsHelperConfig config;

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

            // The far side is a landing tile - there is nothing to click there, so the tile
            // marker is the right and only marker for it.
            drawTransportTile(graphics, to);

            int targetId = graph == null ? -1 : DrewsHelperTravelEstimate.targetId(graph, from, to);
            if (targetId >= 0 && drawInteractable(graphics, from, targetId))
            {
                continue;
            }

            // Nothing resolvable in the loaded scene: fall back to marking the tile, which is
            // still better than drawing nothing.
            drawTransportTile(graphics, from);
        }
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

    private void drawTransportTile(Graphics2D graphics, WorldPoint point)
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
}
