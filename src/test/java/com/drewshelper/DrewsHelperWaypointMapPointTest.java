package com.drewshelper;

import java.awt.Color;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DrewsHelperWaypointMapPointTest
{
    @Test
    public void waypointMapPointCarriesMarkerMetadata()
    {
        WorldPoint worldPoint = new WorldPoint(3200, 3201, 0);
        DrewsHelperWaypointMapPoint mapPoint = new DrewsHelperWaypointMapPoint(1, worldPoint, new Color(0x800020));

        assertEquals(worldPoint, mapPoint.getWorldPoint());
        assertEquals(worldPoint, mapPoint.getTarget());
        assertEquals("Waypoint #1", mapPoint.getName());
        assertTrue(mapPoint.getTooltip().startsWith("Drew's Helper Waypoint #1"));
        assertTrue(mapPoint.isSnapToEdge());
        assertTrue(mapPoint.isJumpOnClick());
        assertEquals(13, mapPoint.getImagePoint().getX());
        assertEquals(13, mapPoint.getImagePoint().getY());
        assertNotNull(mapPoint.getImage());
        assertTrue(DrewsHelperWaypointMapPoint.isDrewsHelperWaypoint(mapPoint));
    }
}
