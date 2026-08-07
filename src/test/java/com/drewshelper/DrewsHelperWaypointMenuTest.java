package com.drewshelper;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DrewsHelperWaypointMenuTest
{
    @Test
    public void displaysSetWaypointsInAscendingOrder()
    {
        assertArrayEquals(new String[]
        {
            "Set Waypoint #1",
            "Set Waypoint #2",
            "Set Waypoint #3",
            "Set Waypoint #4",
            "Set Waypoint #5"
        }, DrewsHelperPlugin.waypointMenuDisplayLabels(new WorldPoint[DrewsHelperPlugin.MAX_WAYPOINTS]));
    }

    @Test
    public void displaysCancelForPlacedWaypointSlots()
    {
        WorldPoint[] waypoints = new WorldPoint[DrewsHelperPlugin.MAX_WAYPOINTS];
        waypoints[0] = new WorldPoint(3200, 3201, 0);
        waypoints[3] = new WorldPoint(3210, 3220, 0);

        assertArrayEquals(new String[]
        {
            "Cancel Waypoint #1",
            "Set Waypoint #2",
            "Set Waypoint #3",
            "Cancel Waypoint #4",
            "Set Waypoint #5"
        }, DrewsHelperPlugin.waypointMenuDisplayLabels(waypoints));
    }

    @Test
    public void rejectsIncompleteWaypointMenuState()
    {
        try
        {
            DrewsHelperPlugin.waypointMenuDisplayLabels(new WorldPoint[1]);
        }
        catch (IllegalArgumentException ex)
        {
            assertEquals("Waypoint menu state must contain exactly 5 slots", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected incomplete waypoint state to be rejected");
    }
}
