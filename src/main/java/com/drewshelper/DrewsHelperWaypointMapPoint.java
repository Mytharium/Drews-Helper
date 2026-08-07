package com.drewshelper;

import java.awt.Color;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

final class DrewsHelperWaypointMapPoint extends WorldMapPoint
{
    private static final int IMAGE_SIZE = 26;
    private static final int CENTER = IMAGE_SIZE / 2;
    private static final String TOOLTIP_PREFIX = "Drew's Helper Waypoint #";

    DrewsHelperWaypointMapPoint(int waypointNumber, WorldPoint worldPoint, Color color)
    {
        super(worldPoint, DrewsHelperWaypointIcon.createImage(waypointNumber, color));
        setName("Waypoint #" + waypointNumber);
        setTooltip(TOOLTIP_PREFIX + waypointNumber + " (" + worldPoint.getX() + ", "
            + worldPoint.getY() + ", " + worldPoint.getPlane() + ")");
        setTarget(worldPoint);
        setImagePoint(new Point(CENTER, CENTER));
        setSnapToEdge(true);
        setJumpOnClick(true);
    }

    static boolean isDrewsHelperWaypoint(WorldMapPoint point)
    {
        String tooltip = point.getTooltip();
        return tooltip != null && tooltip.startsWith(TOOLTIP_PREFIX);
    }
}
