package com.drewshelper.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class DrewsHelperTraversableTilesTest
{
    /** Every tile is standable except a blocked set, which nothing can move out of. */
    private static final class TestMap implements DrewsHelperMovementMap
    {
        private final Set<String> blocked = new HashSet<>();

        TestMap block(int x, int y, int plane)
        {
            blocked.add(x + ":" + y + ":" + plane);
            return this;
        }

        private boolean open(int x, int y, int plane)
        {
            return !blocked.contains(x + ":" + y + ":" + plane);
        }

        @Override public boolean canMoveNorth(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveSouth(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveEast(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveWest(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveNorthEast(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveNorthWest(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveSouthEast(int x, int y, int plane) { return open(x, y, plane); }
        @Override public boolean canMoveSouthWest(int x, int y, int plane) { return open(x, y, plane); }
    }

    @Test
    public void aStandableTileIsReturnedUnchanged()
    {
        TestMap map = new TestMap();
        WorldPoint point = new WorldPoint(3200, 3200, 0);

        assertEquals(point, DrewsHelperTraversableTiles.nearest(map, point, 32));
    }

    @Test
    public void aTileInTheRiverSnapsToTheBank()
    {
        // A three-tile-wide river running north-south through x = 3200..3202.
        TestMap map = new TestMap();
        for (int x = 3200; x <= 3202; x++)
        {
            for (int y = 3190; y <= 3210; y++)
            {
                map.block(x, y, 0);
            }
        }

        WorldPoint inTheWater = new WorldPoint(3201, 3200, 0);
        WorldPoint snapped = DrewsHelperTraversableTiles.nearest(map, inTheWater, 32);

        assertFalse("must not stay in the water", snapped.equals(inTheWater));
        assertTrue("must land on a standable tile",
            DrewsHelperTraversableTiles.isTraversable(map, snapped));
        assertEquals("must stay on the same plane", 0, snapped.getPlane());

        // Both banks are two tiles away; either is correct, nothing further out is.
        assertEquals(2, Math.abs(snapped.getX() - inTheWater.getX()));
        assertEquals(3200, snapped.getY());
    }

    @Test
    public void theNearestStandableTileWinsNotTheFirstFound()
    {
        TestMap map = new TestMap();
        map.block(3200, 3200, 0);
        // Block everything within 3 tiles except one opening 2 tiles north.
        for (int dx = -3; dx <= 3; dx++)
        {
            for (int dy = -3; dy <= 3; dy++)
            {
                if (!(dx == 0 && dy == 2))
                {
                    map.block(3200 + dx, 3200 + dy, 0);
                }
            }
        }

        WorldPoint snapped = DrewsHelperTraversableTiles.nearest(map, new WorldPoint(3200, 3200, 0), 32);
        assertEquals(new WorldPoint(3200, 3202, 0), snapped);
    }

    @Test
    public void nothingStandableNearbyLeavesTheRequestAlone()
    {
        TestMap map = new TestMap();
        for (int dx = -6; dx <= 6; dx++)
        {
            for (int dy = -6; dy <= 6; dy++)
            {
                map.block(3200 + dx, 3200 + dy, 0);
            }
        }

        // Radius 4 cannot escape a 6-tile block, so the request is honoured unchanged rather
        // than the waypoint being relocated somewhere the user did not click.
        WorldPoint requested = new WorldPoint(3200, 3200, 0);
        assertEquals(requested, DrewsHelperTraversableTiles.nearest(map, requested, 4));
    }

    @Test
    public void aNullMapNeverBlocksPlacingAWaypoint()
    {
        WorldPoint requested = new WorldPoint(3200, 3200, 0);
        assertEquals(requested, DrewsHelperTraversableTiles.nearest(null, requested, 32));
    }
}
