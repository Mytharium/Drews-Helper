package com.drewshelper.routing;

import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DrewsHelperRouteSnapshotTest
{
    @Test
    public void consumesOnlyOneLeadingTile()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(
                new WorldPoint(100, 100, 0),
                new WorldPoint(100, 101, 0),
                new WorldPoint(100, 102, 0)
            ),
            Arrays.asList(new WorldPoint(100, 102, 0)),
            2
        );

        DrewsHelperRouteSnapshot consumed = snapshot.consumeFirstPathTile();

        assertEquals(2, consumed.getPath().size());
        assertEquals(new WorldPoint(100, 101, 0), consumed.getPath().get(0));
        assertEquals(new WorldPoint(100, 102, 0), consumed.getPath().get(1));
        assertEquals(1, consumed.getWalkingDistance());
        assertEquals(snapshot.getDestinations(), consumed.getDestinations());
    }

    @Test
    public void consumesMultipleLeadingTilesForRunSpeedProgress()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(
                new WorldPoint(100, 100, 0),
                new WorldPoint(100, 101, 0),
                new WorldPoint(100, 102, 0),
                new WorldPoint(100, 103, 0)
            ),
            Arrays.asList(new WorldPoint(100, 103, 0)),
            3
        );

        DrewsHelperRouteSnapshot consumed = snapshot.consumeLeadingPathTiles(2);

        assertEquals(2, consumed.getPath().size());
        assertEquals(new WorldPoint(100, 102, 0), consumed.getPath().get(0));
        assertEquals(new WorldPoint(100, 103, 0), consumed.getPath().get(1));
        assertEquals(1, consumed.getWalkingDistance());
        assertEquals(snapshot.getDestinations(), consumed.getDestinations());
    }

    @Test
    public void keepsSingleTileRouteStable()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(new WorldPoint(100, 100, 0)),
            Arrays.asList(new WorldPoint(100, 100, 0)),
            0
        );

        assertSame(snapshot, snapshot.consumeFirstPathTile());
    }

    @Test
    public void keepsEndpointTileWhenConsumingPastRouteLength()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(
                new WorldPoint(100, 100, 0),
                new WorldPoint(100, 101, 0),
                new WorldPoint(100, 102, 0)
            ),
            Arrays.asList(new WorldPoint(100, 102, 0)),
            2
        );

        DrewsHelperRouteSnapshot consumed = snapshot.consumeLeadingPathTiles(99);

        assertEquals(1, consumed.getPath().size());
        assertEquals(new WorldPoint(100, 102, 0), consumed.getPath().get(0));
        assertEquals(0, consumed.getWalkingDistance());
    }

    @Test
    public void calculatingSnapshotCanCarryPreviousPathForOverlayContinuity()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.calculating(
            Arrays.asList(new WorldPoint(200, 200, 0)),
            Arrays.asList(
                new WorldPoint(100, 100, 0),
                new WorldPoint(101, 101, 0)
            )
        );

        assertEquals(DrewsHelperRouteStatus.CALCULATING, snapshot.getStatus());
        assertTrue(snapshot.hasPath());
        assertEquals(new WorldPoint(100, 100, 0), snapshot.getPath().get(0));
        assertEquals(new WorldPoint(101, 101, 0), snapshot.getPath().get(1));
        assertEquals(new WorldPoint(200, 200, 0), snapshot.getDestinations().get(0));
    }

    @Test
    public void doesNotTreatNormalWalkingStepsAsTransportJumps()
    {
        WorldPoint point = new WorldPoint(100, 100, 0);

        assertFalse(DrewsHelperRouteSnapshot.isTransportJump(point, new WorldPoint(101, 100, 0)));
        assertFalse(DrewsHelperRouteSnapshot.isTransportJump(point, new WorldPoint(101, 101, 0)));
    }

    @Test
    public void treatsDistantAndPlaneChangingStepsAsTransportJumps()
    {
        WorldPoint point = new WorldPoint(100, 100, 0);

        assertTrue(DrewsHelperRouteSnapshot.isTransportJump(point, new WorldPoint(110, 100, 0)));
        assertTrue(DrewsHelperRouteSnapshot.isTransportJump(point, new WorldPoint(100, 100, 1)));
    }
}
