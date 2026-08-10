package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperWaypointIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
