package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperConfig;
import com.drewshelper.DrewsHelperPlugin;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.util.List;
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
    private static final int MIN_ROUTE_TILE_SIZE = 4;
    private static final Stroke TRANSPORT_STROKE = new BasicStroke(
        2.5f,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND,
        0.0f,
        new float[] { 7.0f, 7.0f },
        0.0f
    );

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

        DrewsHelperRouteSnapshot snapshot = plugin.getDisplayRouteSnapshot();
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
        Shape originalClip = graphics.getClip();
        graphics.setClip(map.getBounds());
        try
        {
            for (WorldPoint point : snapshot.getPath())
            {
                drawOnMap(graphics, clip, point, color);
            }
            drawTransportJumps(graphics, clip, snapshot.getPath(), color);
        }
        finally
        {
            graphics.setClip(originalClip);
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

        Rectangle tile = routeTileRectangle(start, end);
        if (!clip.intersects(tile))
        {
            return;
        }

        graphics.setColor(color);
        graphics.fill(tile);
    }

    static Rectangle routeTileRectangle(Point start, Point end)
    {
        int minX = Math.min(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int tileWidth = Math.abs(end.getX() - start.getX());
        int tileHeight = Math.abs(end.getY() - start.getY());
        int drawWidth = Math.max(MIN_ROUTE_TILE_SIZE, tileWidth);
        int drawHeight = Math.max(MIN_ROUTE_TILE_SIZE, tileHeight);

        return new Rectangle(
            minX - Math.max(0, drawWidth - tileWidth) / 2,
            minY - Math.max(0, drawHeight - tileHeight) / 2,
            drawWidth,
            drawHeight
        );
    }

    private void drawTransportJumps(Graphics2D graphics, Area clip, List<WorldPoint> path, Color color)
    {
        if (path.size() < 2)
        {
            return;
        }

        Shape originalClip = graphics.getClip();
        Stroke originalStroke = graphics.getStroke();
        Color originalColor = graphics.getColor();
        Rectangle bounds = clip.getBounds();

        graphics.setClip(bounds);
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
                    drawTransportJump(graphics, bounds, from, to);
                }
            }
        }
        finally
        {
            graphics.setClip(originalClip);
            graphics.setStroke(originalStroke);
            graphics.setColor(originalColor);
        }
    }

    private void drawTransportJump(Graphics2D graphics, Rectangle bounds, WorldPoint from, WorldPoint to)
    {
        Point start = mapTileCenter(from);
        Point end = mapTileCenter(to);
        if (start == null || end == null)
        {
            return;
        }

        Line2D line = new Line2D.Double(start.getX(), start.getY(), end.getX(), end.getY());
        if (!bounds.intersectsLine(start.getX(), start.getY(), end.getX(), end.getY())
            && !bounds.contains(start.getX(), start.getY())
            && !bounds.contains(end.getX(), end.getY()))
        {
            return;
        }

        graphics.draw(line);
    }

    private Point mapTileCenter(WorldPoint point)
    {
        Point start = worldMapOverlay.mapWorldPointToGraphicsPoint(point);
        Point end = worldMapOverlay.mapWorldPointToGraphicsPoint(point.dx(1).dy(-1));
        if (start == null || end == null)
        {
            return null;
        }

        return new Point(
            (start.getX() + end.getX()) / 2,
            (start.getY() + end.getY()) / 2
        );
    }

    private Color opaquePathColor()
    {
        Color color = config.pathColor();
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 220);
    }
}
