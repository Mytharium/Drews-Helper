package com.drewshelper;

import com.drewshelper.routing.DrewsHelperCollisionMap;
import com.drewshelper.routing.DrewsHelperWalkingRouteEngine;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DrewsHelperPluginBenchmarkCaptureTest
{
    @Test
    public void waitsForDisplayedRouteStartBeforeRecordingMovement() throws Exception
    {
        DrewsHelperPlugin.RouteBenchmarkCapture capture = new DrewsHelperPlugin.RouteBenchmarkCapture(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(0, 2, 0)
            ),
            new DrewsHelperWalkingRouteEngine(DrewsHelperCollisionMap.loadDefault()),
            new HashMap<>()
        );

        assertNull(capture.record(new WorldPoint(1, 0, 0)));
        assertNull(capture.record(new WorldPoint(1, 1, 0)));
        assertNull(capture.record(new WorldPoint(0, 0, 0)));

        DrewsHelperPlugin.RouteBenchmarkUpdate update = capture.record(new WorldPoint(0, 1, 0));

        assertNotNull(update);
        assertFalse(update.isComplete());
        assertTrue(update.logLine().contains("first=match"));
        assertTrue(update.logLine().contains("divergence={none}"));
        assertTrue(update.logLine().contains("shape={pending}"));
        assertTrue(update.logLine().contains("shadow={pending}"));
        assertTrue(update.logLine().contains("shapeShadow={pending}"));
    }

    @Test
    public void discardsCaptureWhenMovementNeverReachesRouteStart() throws Exception
    {
        DrewsHelperPlugin.RouteBenchmarkCapture capture = new DrewsHelperPlugin.RouteBenchmarkCapture(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(0, 2, 0)
            ),
            new DrewsHelperWalkingRouteEngine(DrewsHelperCollisionMap.loadDefault()),
            new HashMap<>()
        );

        assertNull(capture.record(new WorldPoint(1, 0, 0)));
        assertNull(capture.record(new WorldPoint(1, 1, 0)));
        assertNull(capture.record(new WorldPoint(1, 2, 0)));
        DrewsHelperPlugin.RouteBenchmarkUpdate update = capture.record(new WorldPoint(1, 3, 0));

        assertNotNull(update);
        assertTrue(update.isComplete());
        assertTrue(update.overlaySummary().contains("stale-start"));
        assertTrue(update.logLine().contains("reason=stale-start"));
        assertTrue(update.logLine().contains("expectedStart=(0,0,0)"));
    }

    @Test
    public void reportsMultiWaypointDiagnosticsAgainstCurrentSegmentTarget() throws Exception
    {
        List<WorldPoint> expectedRoute = Arrays.asList(
            point(2942, 3243),
            point(2943, 3243),
            point(2944, 3243),
            point(2945, 3244),
            point(2946, 3244),
            point(2947, 3244),
            point(2948, 3244),
            point(2949, 3243),
            point(2950, 3242),
            point(2951, 3242),
            point(2952, 3241),
            point(2953, 3240),
            point(2954, 3239),
            point(2955, 3238),
            point(2956, 3237),
            point(2957, 3236),
            point(2958, 3235),
            point(2959, 3235),
            point(2960, 3235)
        );
        WorldPoint firstWaypoint = point(2958, 3235);
        WorldPoint finalWaypoint = point(2960, 3235);
        DrewsHelperPlugin.RouteBenchmarkCapture capture = new DrewsHelperPlugin.RouteBenchmarkCapture(
            expectedRoute,
            Arrays.asList(firstWaypoint, finalWaypoint),
            new DrewsHelperWalkingRouteEngine(DrewsHelperCollisionMap.loadDefault()),
            new HashMap<>()
        );

        List<WorldPoint> actualRoute = Arrays.asList(
            point(2942, 3243),
            point(2943, 3243),
            point(2944, 3243),
            point(2945, 3244),
            point(2946, 3244),
            point(2947, 3244),
            point(2948, 3244),
            point(2949, 3243),
            point(2950, 3242),
            point(2951, 3242),
            point(2952, 3242),
            point(2953, 3241),
            point(2954, 3240),
            point(2955, 3239),
            point(2956, 3238),
            point(2957, 3237),
            point(2958, 3236),
            firstWaypoint,
            point(2959, 3235),
            finalWaypoint
        );

        DrewsHelperPlugin.RouteBenchmarkUpdate finalUpdate = null;
        for (WorldPoint point : actualRoute)
        {
            DrewsHelperPlugin.RouteBenchmarkUpdate update = capture.record(point);
            if (update != null && update.isComplete())
            {
                finalUpdate = update;
            }
        }

        assertNotNull(finalUpdate);
        assertTrue(finalUpdate.logLine().contains("candidates={from=(2951,3242,0) target=(2958,3235,0) finalTarget=(2960,3235,0)"));
        assertTrue(finalUpdate.logLine().contains("edgeValidation={from=(2951,3242,0) actual=(2952,3242,0) target=(2958,3235,0)"));
        assertTrue(finalUpdate.logLine().contains("shape={scope=segment target=(2958,3235,0) finalTarget=(2960,3235,0)"));
        assertTrue(finalUpdate.logLine().contains("shadow={status="));
        assertTrue(finalUpdate.logLine().contains("shapeShadow={status="));
    }

    private static WorldPoint point(int x, int y)
    {
        return new WorldPoint(x, y, 0);
    }
}
