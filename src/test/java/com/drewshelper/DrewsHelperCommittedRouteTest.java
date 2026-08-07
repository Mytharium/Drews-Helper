package com.drewshelper;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrewsHelperCommittedRouteTest
{
    @Test
    public void findsPlayerTileOnCommittedRoute()
    {
        List<WorldPoint> path = Arrays.asList(
            new WorldPoint(3200, 3200, 0),
            new WorldPoint(3201, 3201, 0),
            new WorldPoint(3202, 3202, 0)
        );

        assertEquals(0, DrewsHelperPlugin.routePathIndex(path, new WorldPoint(3200, 3200, 0)));
        assertEquals(1, DrewsHelperPlugin.routePathIndex(path, new WorldPoint(3201, 3201, 0)));
        assertEquals(2, DrewsHelperPlugin.routePathIndex(path, new WorldPoint(3202, 3202, 0)));
    }

    @Test
    public void returnsMinusOneWhenPlayerStraysFromCommittedRoute()
    {
        List<WorldPoint> path = Arrays.asList(
            new WorldPoint(3200, 3200, 0),
            new WorldPoint(3201, 3201, 0),
            new WorldPoint(3202, 3202, 0)
        );

        assertEquals(-1, DrewsHelperPlugin.routePathIndex(path, new WorldPoint(3201, 3200, 0)));
        assertEquals(-1, DrewsHelperPlugin.routePathIndex(path, new WorldPoint(3201, 3201, 1)));
        assertEquals(-1, DrewsHelperPlugin.routePathIndex(null, new WorldPoint(3201, 3201, 0)));
    }

    @Test
    public void consumesThroughCurrentRouteTileForRunSpeedProgress()
    {
        List<WorldPoint> path = Arrays.asList(
            new WorldPoint(3200, 3200, 0),
            new WorldPoint(3201, 3200, 0),
            new WorldPoint(3202, 3200, 0),
            new WorldPoint(3203, 3200, 0)
        );

        DrewsHelperPlugin.CommittedRouteProgress progress = DrewsHelperPlugin.committedRouteProgress(
            path,
            new WorldPoint(3202, 3200, 0),
            10
        );

        assertFalse(progress.shouldRecalculate());
        assertEquals(2, progress.getConsumeCount());
        assertEquals(0, progress.getDistanceFromRoute());
    }

    @Test
    public void keepsCommittedRouteWhenPlayerIsWithinTenTiles()
    {
        List<WorldPoint> path = Arrays.asList(
            new WorldPoint(3200, 3200, 0),
            new WorldPoint(3201, 3200, 0),
            new WorldPoint(3202, 3200, 0)
        );

        DrewsHelperPlugin.CommittedRouteProgress progress = DrewsHelperPlugin.committedRouteProgress(
            path,
            new WorldPoint(3202, 3210, 0),
            10
        );

        assertFalse(progress.shouldRecalculate());
        assertEquals(0, progress.getConsumeCount());
        assertEquals(10, progress.getDistanceFromRoute());
    }

    @Test
    public void recalculatesWhenPlayerIsMoreThanTenTilesOffRoute()
    {
        List<WorldPoint> path = Arrays.asList(
            new WorldPoint(3200, 3200, 0),
            new WorldPoint(3201, 3200, 0),
            new WorldPoint(3202, 3200, 0)
        );

        DrewsHelperPlugin.CommittedRouteProgress progress = DrewsHelperPlugin.committedRouteProgress(
            path,
            new WorldPoint(3202, 3211, 0),
            10
        );

        assertTrue(progress.shouldRecalculate());
    }

    @Test
    public void recalculatesWhenNoSamePlaneRouteTileIsNear()
    {
        List<WorldPoint> path = Arrays.asList(
            new WorldPoint(3200, 3200, 0),
            new WorldPoint(3201, 3200, 0),
            new WorldPoint(3202, 3200, 0)
        );

        DrewsHelperPlugin.CommittedRouteProgress progress = DrewsHelperPlugin.committedRouteProgress(
            path,
            new WorldPoint(3201, 3200, 1),
            10
        );

        assertTrue(progress.shouldRecalculate());
    }

    @Test
    public void separatesWaypointPositionKeysFromColorKeys()
    {
        assertTrue(DrewsHelperPlugin.isWaypointPositionConfigKey("waypoint1Position"));
        assertFalse(DrewsHelperPlugin.isWaypointPositionConfigKey("waypoint1PathColor"));
        assertFalse(DrewsHelperPlugin.isWaypointPositionConfigKey("pathColor"));

        assertTrue(DrewsHelperPlugin.isWaypointColorConfigKey("waypoint1PathColor"));
        assertFalse(DrewsHelperPlugin.isWaypointColorConfigKey("waypoint1Position"));
    }
}
