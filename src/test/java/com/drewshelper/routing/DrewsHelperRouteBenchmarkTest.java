package com.drewshelper.routing;

import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrewsHelperRouteBenchmarkTest
{
    @Test
    public void comparesFirstStepPrefixLengthDeviationAndTurns()
    {
        DrewsHelperRouteBenchmark.Report report = DrewsHelperRouteBenchmark.compare(
            DrewsHelperRouteSolverMode.BFS,
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0),
                new WorldPoint(3, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 1, 0),
                new WorldPoint(3, 1, 0)
            )
        );

        assertEquals(DrewsHelperRouteSolverMode.BFS, report.getSolverMode());
        assertTrue(report.isFirstStepDirectionMatches());
        assertEquals(1, report.getFirstFiveMatches());
        assertEquals(3, report.getFirstFiveCompared());
        assertFalse(report.isFullTileSequenceMatches());
        assertEquals(3, report.getExpectedPathLength());
        assertEquals(3, report.getActualPathLength());
        assertEquals(1, report.getMaxLateralDeviation());
        assertEquals(0, report.getExpectedTurnCount());
        assertEquals(2, report.getActualTurnCount());
    }

    @Test
    public void formatsCoordinateTracePrefixes()
    {
        assertEquals("(3200,3210,2)", DrewsHelperRouteBenchmark.formatPoint(new WorldPoint(3200, 3210, 2)));
        assertEquals("(null)", DrewsHelperRouteBenchmark.formatPoint(null));
        assertEquals(
            "[(0,0,0) -> (1,0,0) -> ... total=3]",
            DrewsHelperRouteBenchmark.formatPathPrefix(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(2, 0, 0)
                ),
                2
            )
        );
        assertEquals("[]", DrewsHelperRouteBenchmark.formatPathPrefix(null));
    }

    @Test
    public void findsAndFormatsFirstDivergence()
    {
        assertEquals(
            2,
            DrewsHelperRouteBenchmark.firstDivergenceIndex(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(2, 0, 0)
                ),
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(1, 1, 0)
                ),
                false
            )
        );

        assertEquals(
            -1,
            DrewsHelperRouteBenchmark.firstDivergenceIndex(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(2, 0, 0)
                ),
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0)
                ),
                false
            )
        );

        assertEquals(
            2,
            DrewsHelperRouteBenchmark.firstDivergenceIndex(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(2, 0, 0)
                ),
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0)
                ),
                true
            )
        );

        assertEquals(
            "idx=2 prevDir=E predicted=(2,0,0) actual=(1,1,0) "
                + "predictedWindow=[0:(0,0,0) -> 1:(1,0,0) -> 2:(2,0,0)] "
                + "actualWindow=[0:(0,0,0) -> 1:(1,0,0) -> 2:(1,1,0)]",
            DrewsHelperRouteBenchmark.formatDivergence(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(2, 0, 0)
                ),
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(1, 1, 0)
                ),
                false
            )
        );
    }

    @Test
    public void formatsPathWindowsAroundDivergence()
    {
        assertEquals(
            "[1:(1,0,0) -> 2:(2,0,0) -> 3:(3,0,0) start=1 end=3 total=5]",
            DrewsHelperRouteBenchmark.formatPathWindow(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 0, 0),
                    new WorldPoint(2, 0, 0),
                    new WorldPoint(3, 0, 0),
                    new WorldPoint(4, 0, 0)
                ),
                2,
                1
            )
        );
        assertEquals("[]", DrewsHelperRouteBenchmark.formatPathWindow(null, 3));
    }
}
