package com.drewshelper.routing.ui;

import java.awt.Rectangle;
import net.runelite.api.Point;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DrewsHelperRouteMapOverlayTest
{
    @Test
    public void keepsProjectedTileSizeWhenZoomedIn()
    {
        Rectangle rectangle = DrewsHelperRouteMapOverlay.routeTileRectangle(
            new Point(100, 200),
            new Point(108, 208)
        );

        assertEquals(new Rectangle(100, 200, 8, 8), rectangle);
    }

    @Test
    public void usesMinimumScreenSizeWhenZoomedOut()
    {
        Rectangle rectangle = DrewsHelperRouteMapOverlay.routeTileRectangle(
            new Point(100, 200),
            new Point(101, 201)
        );

        assertEquals(new Rectangle(99, 199, 4, 4), rectangle);
    }

    @Test
    public void usesMinimumScreenSizeWhenTileProjectionCollapses()
    {
        Rectangle rectangle = DrewsHelperRouteMapOverlay.routeTileRectangle(
            new Point(100, 200),
            new Point(100, 200)
        );

        assertEquals(new Rectangle(98, 198, 4, 4), rectangle);
    }
}
