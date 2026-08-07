package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.DrewsHelperWaypointIcon;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public final class DrewsHelperRouteTileOverlay extends Overlay
{
    private static final int WAYPOINT_ICON_SIZE = 24;

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
