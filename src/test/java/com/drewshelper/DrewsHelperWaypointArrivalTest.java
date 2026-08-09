package com.drewshelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class DrewsHelperWaypointArrivalTest
{
    private static final WorldPoint TILE = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint ELSEWHERE = new WorldPoint(3210, 3200, 0);

    @Test
    public void arrivingOnAnArmedWaypointReachesIt()
    {
        boolean[] armed = new boolean[5];

        // Placed from a distance: the first tick away from it arms it.
        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, ELSEWHERE, armed, 0));
        assertTrue("standing elsewhere must arm the waypoint", armed[0]);

        assertTrue("arriving on an armed waypoint counts as reaching it",
            DrewsHelperPlugin.reachedWaypoint(TILE, TILE, armed, 0));
    }

    @Test
    public void aWaypointPlacedOnYourOwnTileDoesNotDeleteItself()
    {
        boolean[] armed = new boolean[5];

        // setWaypoint leaves the slot unarmed, so standing on it must not clear it - otherwise
        // dropping a waypoint where you stand would vanish a tick later.
        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, TILE, armed, 0));
        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, TILE, armed, 0));

        // Walk off it, come back, and now it counts.
        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, ELSEWHERE, armed, 0));
        assertTrue(DrewsHelperPlugin.reachedWaypoint(TILE, TILE, armed, 0));
    }

    @Test
    public void planeMattersAndEmptySlotsAreIgnored()
    {
        boolean[] armed = new boolean[5];
        WorldPoint upstairs = new WorldPoint(3200, 3200, 1);

        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, upstairs, armed, 0));
        assertTrue("a different plane is not the same tile", armed[0]);
        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, upstairs, armed, 0));

        assertFalse(DrewsHelperPlugin.reachedWaypoint(null, TILE, armed, 1));
        assertFalse(DrewsHelperPlugin.reachedWaypoint(TILE, null, armed, 2));
    }

    @Test
    public void routeLegsAreNumberedByWaypointSlotNotByPosition() throws Exception
    {
        // Destinations are the placed waypoints in slot order, so leg 0 is the first non-empty
        // slot. Labelling legs by their own index mislabels a lone Waypoint #3 as "WP1", and
        // does so constantly once reached waypoints start clearing themselves.
        DrewsHelperPlugin plugin = new DrewsHelperPlugin();
        Field field = DrewsHelperPlugin.class.getDeclaredField("waypoints");
        field.setAccessible(true);
        WorldPoint[] waypoints = (WorldPoint[]) field.get(plugin);

        waypoints[2] = TILE;
        assertEquals("a lone Waypoint #3 must stay WP3", 2, plugin.waypointSlotForLeg(0));

        waypoints[0] = ELSEWHERE;
        assertEquals(0, plugin.waypointSlotForLeg(0));
        assertEquals(2, plugin.waypointSlotForLeg(1));

        // Reaching #1 clears its slot; #3 must keep its number and colour rather than shift up.
        waypoints[0] = null;
        assertEquals(2, plugin.waypointSlotForLeg(0));
    }
}
