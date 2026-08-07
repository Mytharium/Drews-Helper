package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.DrewsHelperWaypointIcon;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

        DrewsHelperRouteSnapshot snapshot = plugin.getRouteSnapshot();
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
        }

        drawWaypointEndpoints(graphics, playerLocation);
        return null;
    }

    private void drawOnMinimap(Graphics2D graphics, WorldPoint point, WorldPoint playerLocation, Color color)
    {
        if (point.getPlane() != client.getPlane() || point.distanceTo(playerLocation) >= MAX_MINIMAP_DISTANCE)
        {
            return;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client, point);
        if (localPoint == null)
        {
            return;
        }

        Point minimapPoint = Perspective.localToMinimap(client, localPoint);
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
        if (waypoint.getPlane() != client.getPlane() || waypoint.distanceTo(playerLocation) >= MAX_MINIMAP_DISTANCE)
        {
            return;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client, waypoint);
        if (localPoint == null)
        {
            return;
        }

        Point minimapPoint = Perspective.localToMinimap(client, localPoint);
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
