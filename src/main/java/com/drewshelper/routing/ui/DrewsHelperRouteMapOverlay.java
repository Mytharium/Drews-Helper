package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

public final class DrewsHelperRouteMapOverlay extends Overlay
{
    private final Client client;
    private final DrewsHelperPlugin plugin;
    private final DrewsHelperConfig config;
    private final WorldMapOverlay worldMapOverlay;

    @Inject
    public DrewsHelperRouteMapOverlay(
        Client client,
        DrewsHelperPlugin plugin,
        DrewsHelperConfig config,
        WorldMapOverlay worldMapOverlay
    )
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.worldMapOverlay = worldMapOverlay;
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

        DrewsHelperRouteSnapshot snapshot = plugin.getRouteSnapshot();
        if (!snapshot.hasPath())
        {
            return null;
        }

        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map == null)
        {
            return null;
        }

        Area clip = new Area(map.getBounds());
        Color color = opaquePathColor();
        for (WorldPoint point : snapshot.getPath())
        {
            drawOnMap(graphics, clip, point, color);
        }

        return null;
    }

    private void drawOnMap(Graphics2D graphics, Area clip, WorldPoint point, Color color)
    {
        Point start = worldMapOverlay.mapWorldPointToGraphicsPoint(point);
        Point end = worldMapOverlay.mapWorldPointToGraphicsPoint(point.dx(1).dy(-1));
        if (start == null || end == null)
        {
            return;
        }

        if (!clip.contains(start.getX(), start.getY()) || !clip.contains(end.getX(), end.getY()))
        {
            return;
        }

        Rectangle tile = new Rectangle(
            Math.min(start.getX(), end.getX()),
            Math.min(start.getY(), end.getY()),
            Math.abs(end.getX() - start.getX()),
            Math.abs(end.getY() - start.getY())
        );
        graphics.setColor(color);
        graphics.fill(tile);
    }

    private Color opaquePathColor()
    {
        Color color = config.pathColor();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 220);
    }
}
