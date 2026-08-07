package com.drewshelper.routing.ui;

import com.drewshelper.DrewsHelperWaypointIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
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
}
