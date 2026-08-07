package com.drewshelper.routing;

import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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
}
