package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.DrewsHelperWaypointIcon;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public final class DrewsHelperRouteMinimapOverlay extends Overlay
{
    private static final int TILE_WIDTH = 4;
    private static final int TILE_HEIGHT = 4;
    private static final int WAYPOINT_ICON_SIZE = 20;
    private static final int MAX_MINIMAP_DISTANCE = 50;
    private static final Stroke TRANSPORT_STROKE = new BasicStroke(
        2.0f,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND,
        0.0f,
        new float[] { 5.0f, 5.0f },
        0.0f
    );

    private final Client client;
    private final DrewsHelperPlugin plugin;
    private final DrewsHelperConfig config;

    @Inject
    public DrewsHelperRouteMinimapOverlay(Client client, DrewsHelperPlugin plugin, DrewsHelperConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.pathingReplacementEnabled())
        {
            return null;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return null;
        }

        DrewsHelperRouteSnapshot snapshot = plugin.getDisplayRouteSnapshot();
        WorldPoint playerLocation = localPlayer.getWorldLocation();
        if (playerLocation == null)
        {
            return null;
        }

        if (snapshot.hasPath())
        {
            Color color = opaquePathColor();
            for (WorldPoint point : snapshot.getPath())
            {
                drawOnMinimap(graphics, point, playerLocation, color);
            }
            drawTransportJumps(graphics, snapshot.getPath(), playerLocation, color);
        }

        drawWaypointEndpoints(graphics, playerLocation);
        return null;
    }

    private void drawOnMinimap(Graphics2D graphics, WorldPoint point, WorldPoint playerLocation, Color color)
    {
        Point minimapPoint = minimapPoint(point, playerLocation);
        if (minimapPoint == null)
        {
            return;
        }

        graphics.setColor(color);
        graphics.fillRect(
            minimapPoint.getX() - TILE_WIDTH / 2,
            minimapPoint.getY() - TILE_HEIGHT / 2,
            TILE_WIDTH,
            TILE_HEIGHT
        );
    }

    private void drawTransportJumps(Graphics2D graphics, List<WorldPoint> path, WorldPoint playerLocation, Color color)
    {
        if (path.size() < 2)
        {
            return;
        }

        Stroke originalStroke = graphics.getStroke();
        Color originalColor = graphics.getColor();
        graphics.setColor(color);
        graphics.setStroke(TRANSPORT_STROKE);
        try
        {
            for (int index = 1; index < path.size(); index++)
            {
                WorldPoint from = path.get(index - 1);
                WorldPoint to = path.get(index);
                if (DrewsHelperRouteSnapshot.isTransportJump(from, to))
                {
                    drawTransportJump(graphics, from, to, playerLocation);
                }
            }
        }
        finally
        {
            graphics.setStroke(originalStroke);
            graphics.setColor(originalColor);
        }
    }

    private void drawTransportJump(Graphics2D graphics, WorldPoint from, WorldPoint to, WorldPoint playerLocation)
    {
        Point start = minimapPoint(from, playerLocation);
        Point end = minimapPoint(to, playerLocation);
        if (start == null || end == null)
        {
            return;
        }

        graphics.drawLine(start.getX(), start.getY(), end.getX(), end.getY());
    }

    private Point minimapPoint(WorldPoint point, WorldPoint playerLocation)
    {
        if (point.getPlane() != client.getPlane() || point.distanceTo(playerLocation) >= MAX_MINIMAP_DISTANCE)
        {
            return null;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client, point);
        if (localPoint == null)
        {
            return null;
        }

        return Perspective.localToMinimap(client, localPoint);
    }

    private void drawWaypointEndpoints(Graphics2D graphics, WorldPoint playerLocation)
    {
        for (int index = 0; index < DrewsHelperPlugin.MAX_WAYPOINTS; index++)
        {
            WorldPoint waypoint = plugin.getWaypoint(index);
            if (waypoint != null)
            {
                drawWaypointOnMinimap(graphics, waypoint, playerLocation, index);
            }
        }
    }

    private void drawWaypointOnMinimap(Graphics2D graphics, WorldPoint waypoint, WorldPoint playerLocation, int index)
    {
        Point minimapPoint = minimapPoint(waypoint, playerLocation);
        if (minimapPoint == null)
        {
            return;
        }

        BufferedImage icon = DrewsHelperWaypointIcon.createImage(index + 1, plugin.getWaypointColor(index), WAYPOINT_ICON_SIZE);
        graphics.drawImage(
            icon,
            minimapPoint.getX() - icon.getWidth() / 2,
            minimapPoint.getY() - icon.getHeight() / 2,
            null
        );
    }

    private Color opaquePathColor()
    {
        Color color = config.pathColor();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
    }
}
