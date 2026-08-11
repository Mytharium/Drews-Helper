package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperWaypointIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DrewsHelperRouteTileOverlayTest
{
    @Test
    public void usesCompactWaypointIconsForTileEndpoints()
    {
        assertEquals(24, DrewsHelperRouteTileOverlay.waypointIconSize());
        BufferedImage icon = DrewsHelperWaypointIcon.createImage(5, Color.ORANGE, DrewsHelperRouteTileOverlay.waypointIconSize());
        assertEquals(24, icon.getWidth());
        assertEquals(24, icon.getHeight());
    }

    @Test
    public void crossedWallBitMapsCardinalSteps()
    {
        WorldPoint origin = new WorldPoint(3200, 3200, 0);

        assertEquals(DrewsHelperRouteTileOverlay.WALL_NORTH, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin.dy(1)));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_EAST, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin.dx(1)));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_SOUTH, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin.dy(-1)));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_WEST, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin.dx(-1)));
    }

    @Test
    public void crossedWallBitRejectsNonDoorSteps()
    {
        WorldPoint origin = new WorldPoint(3200, 3200, 0);

        assertEquals(0, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin.dx(1).dy(1)));
        assertEquals(0, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin.dx(2)));
        assertEquals(0, DrewsHelperRouteTileOverlay.crossedWallBit(origin, origin));
        assertEquals(0, DrewsHelperRouteTileOverlay.crossedWallBit(origin, new WorldPoint(3200, 3201, 1)));
        assertEquals(0, DrewsHelperRouteTileOverlay.crossedWallBit(null, origin));
        assertEquals(0, DrewsHelperRouteTileOverlay.crossedWallBit(origin, null));
    }

    @Test
    public void oppositeWallBitMirrorsCardinalBits()
    {
        assertEquals(DrewsHelperRouteTileOverlay.WALL_SOUTH,
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_NORTH));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_NORTH,
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_SOUTH));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_WEST,
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_EAST));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_EAST,
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_WEST));

        assertEquals(DrewsHelperRouteTileOverlay.WALL_NORTH, DrewsHelperRouteTileOverlay.oppositeWallBit(
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_NORTH)));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_SOUTH, DrewsHelperRouteTileOverlay.oppositeWallBit(
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_SOUTH)));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_EAST, DrewsHelperRouteTileOverlay.oppositeWallBit(
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_EAST)));
        assertEquals(DrewsHelperRouteTileOverlay.WALL_WEST, DrewsHelperRouteTileOverlay.oppositeWallBit(
            DrewsHelperRouteTileOverlay.oppositeWallBit(DrewsHelperRouteTileOverlay.WALL_WEST)));
        assertEquals(0, DrewsHelperRouteTileOverlay.oppositeWallBit(0));
        assertEquals(0, DrewsHelperRouteTileOverlay.oppositeWallBit(16));
    }

    @Test
    public void doorwayRunsPerpendicularToTheCrossing()
    {
        assertEquals(1, DrewsHelperRouteTileOverlay.doorwayRunDx(DrewsHelperRouteTileOverlay.WALL_NORTH));
        assertEquals(1, DrewsHelperRouteTileOverlay.doorwayRunDx(DrewsHelperRouteTileOverlay.WALL_SOUTH));
        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDy(DrewsHelperRouteTileOverlay.WALL_NORTH));
        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDy(DrewsHelperRouteTileOverlay.WALL_SOUTH));

        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDx(DrewsHelperRouteTileOverlay.WALL_EAST));
        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDx(DrewsHelperRouteTileOverlay.WALL_WEST));
        assertEquals(1, DrewsHelperRouteTileOverlay.doorwayRunDy(DrewsHelperRouteTileOverlay.WALL_EAST));
        assertEquals(1, DrewsHelperRouteTileOverlay.doorwayRunDy(DrewsHelperRouteTileOverlay.WALL_WEST));

        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDx(0));
        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDy(0));
        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDx(16));
        assertEquals(0, DrewsHelperRouteTileOverlay.doorwayRunDy(16));
    }

    @Test
    public void doorEdgeKeyIsTheSameFromEitherSide()
    {
        WorldPoint south = new WorldPoint(2960, 3334, 2);
        WorldPoint north = new WorldPoint(2960, 3335, 2);
        assertEquals(
            DrewsHelperRouteTileOverlay.doorEdgeKey(south, north, DrewsHelperRouteTileOverlay.WALL_NORTH),
            DrewsHelperRouteTileOverlay.doorEdgeKey(north, south, DrewsHelperRouteTileOverlay.WALL_SOUTH));

        WorldPoint west = new WorldPoint(2964, 3338, 0);
        WorldPoint east = new WorldPoint(2965, 3338, 0);
        assertEquals(
            DrewsHelperRouteTileOverlay.doorEdgeKey(west, east, DrewsHelperRouteTileOverlay.WALL_EAST),
            DrewsHelperRouteTileOverlay.doorEdgeKey(east, west, DrewsHelperRouteTileOverlay.WALL_WEST));
    }

    @Test
    public void doorEdgeKeySeparatesTheTwoLeavesOfADoorway()
    {
        // The whole double-door scan depends on these two edges being distinct keys.
        long left = DrewsHelperRouteTileOverlay.doorEdgeKey(
            new WorldPoint(2959, 3334, 2), new WorldPoint(2959, 3335, 2),
            DrewsHelperRouteTileOverlay.WALL_NORTH);
        long right = DrewsHelperRouteTileOverlay.doorEdgeKey(
            new WorldPoint(2960, 3334, 2), new WorldPoint(2960, 3335, 2),
            DrewsHelperRouteTileOverlay.WALL_NORTH);
        assertNotEquals(left, right);

        long otherPlane = DrewsHelperRouteTileOverlay.doorEdgeKey(
            new WorldPoint(2959, 3334, 0), new WorldPoint(2959, 3335, 0),
            DrewsHelperRouteTileOverlay.WALL_NORTH);
        assertNotEquals(left, otherPlane);
    }
}
