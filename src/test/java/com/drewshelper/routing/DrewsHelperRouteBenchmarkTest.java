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
                + "mergeBack={none} classification=noMergeDrift benign=false "
                + "additionalDivergences={not-scanned} "
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
    public void formatsMergeBackAfterLocalStepPermutation()
    {
        String diagnostic = DrewsHelperRouteBenchmark.formatDivergence(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0),
                new WorldPoint(3, 0, 0),
                new WorldPoint(4, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 1, 0),
                new WorldPoint(3, 1, 0),
                new WorldPoint(4, 0, 0)
            ),
            true
        );

        assertTrue(diagnostic.contains("idx=2"));
        assertTrue(diagnostic.contains("mergeBack={expectedIdx=4 actualIdx=4 stepDelta=0 point=(4,0,0)}"));
        assertTrue(diagnostic.contains("classification=sameTimePermutation benign=true"));
        assertTrue(diagnostic.contains("additionalDivergences={none}"));
    }

    @Test
    public void reportsLengthDifferenceAfterBenignMergeBack()
    {
        String diagnostic = DrewsHelperRouteBenchmark.formatDivergence(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0),
                new WorldPoint(3, 0, 0),
                new WorldPoint(4, 0, 0),
                new WorldPoint(5, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 1, 0),
                new WorldPoint(3, 0, 0),
                new WorldPoint(4, 0, 0)
            ),
            true
        );

        assertTrue(diagnostic.contains("classification=sameTimePermutation benign=true"));
        assertTrue(diagnostic.contains(
            "additionalDivergences={idx=5 predicted=(5,0,0) actual=(null) "
                + "mergeBack={none} classification=noMergeDrift benign=false}"
        ));
    }

    @Test
    public void exposesAdditionalDivergenceIndexAfterFirstMergeBack()
    {
        int additionalIndex = DrewsHelperRouteBenchmark.additionalDivergenceIndexAfterFirstMerge(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0),
                new WorldPoint(3, 0, 0),
                new WorldPoint(4, 0, 0),
                new WorldPoint(5, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 1, 0),
                new WorldPoint(3, 0, 0),
                new WorldPoint(4, 0, 0)
            ),
            true
        );

        assertEquals(5, additionalIndex);
    }

    @Test
    public void sameTimePermutationBeatsNonBenignMergeInDiagnosticWinner()
    {
        String diagnostic = DrewsHelperRouteBenchmark.formatShadowRouteDiagnostic(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 1, 0),
                new WorldPoint(3, 1, 0),
                new WorldPoint(4, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(1, 1, 0),
                new WorldPoint(2, 1, 0),
                new WorldPoint(3, 1, 0),
                new WorldPoint(4, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0),
                new WorldPoint(3, 0, 0),
                new WorldPoint(4, 0, 0)
            ),
            true
        );

        assertTrue(diagnostic.contains("fit={visible=sameTimePermutation shadow=earlyMerge}"));
        assertTrue(diagnostic.contains("winner=visible"));
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

    @Test
    public void formatsShapeDiagnosticsOnlyForCompletedRoutes()
    {
        assertEquals(
            "pending",
            DrewsHelperRouteBenchmark.formatShapeDiagnostic(
                Arrays.asList(
                    new WorldPoint(0, 0, 0),
                    new WorldPoint(1, 1, 0)
                ),
                Arrays.asList(
                    new WorldPoint(0, 0, 0)
                ),
                false
            )
        );

        String diagnostic = DrewsHelperRouteBenchmark.formatShapeDiagnostic(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(0, 2, 0),
                new WorldPoint(1, 3, 0),
                new WorldPoint(2, 4, 0),
                new WorldPoint(3, 5, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(1, 2, 0),
                new WorldPoint(1, 3, 0),
                new WorldPoint(2, 4, 0),
                new WorldPoint(3, 5, 0)
            ),
            true
        );

        assertTrue(diagnostic.contains("expected={lineError="));
        assertTrue(diagnostic.contains("actual={lineError="));
        assertTrue(diagnostic.contains("diag=3"));
        assertTrue(diagnostic.contains("card=2"));
        assertTrue(diagnostic.contains("winner=actual"));
    }

    @Test
    public void formatsShadowRouteDiagnosticsAgainstActualMovement()
    {
        assertEquals(
            "pending",
            DrewsHelperRouteBenchmark.formatShadowRouteDiagnostic(
                Arrays.asList(new WorldPoint(0, 0, 0), new WorldPoint(1, 0, 0)),
                Arrays.asList(new WorldPoint(0, 0, 0), new WorldPoint(0, 1, 0)),
                Arrays.asList(new WorldPoint(0, 0, 0)),
                false
            )
        );

        String diagnostic = DrewsHelperRouteBenchmark.formatShadowRouteDiagnostic(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(1, 1, 0),
                new WorldPoint(2, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0)
            ),
            true
        );

        assertTrue(diagnostic.contains("status=ready"));
        assertTrue(diagnostic.contains("overridesMatter=true"));
        assertTrue(diagnostic.contains("visibleVsShadow={idx=1"));
        assertTrue(diagnostic.contains("shadowVsActual={idx=1"));
        assertTrue(diagnostic.contains("winner=visible"));
    }

    @Test
    public void formatsShapeShadowRouteDiagnosticsAgainstActualMovement()
    {
        assertEquals(
            "pending",
            DrewsHelperRouteBenchmark.formatShapeShadowRouteDiagnostic(
                Arrays.asList(new WorldPoint(0, 0, 0), new WorldPoint(1, 0, 0)),
                Arrays.asList(new WorldPoint(0, 0, 0), new WorldPoint(0, 1, 0)),
                Arrays.asList(new WorldPoint(0, 0, 0)),
                false
            )
        );

        String diagnostic = DrewsHelperRouteBenchmark.formatShapeShadowRouteDiagnostic(
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(1, 0, 0),
                new WorldPoint(2, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(1, 1, 0),
                new WorldPoint(2, 0, 0)
            ),
            Arrays.asList(
                new WorldPoint(0, 0, 0),
                new WorldPoint(0, 1, 0),
                new WorldPoint(1, 1, 0),
                new WorldPoint(2, 0, 0)
            ),
            true
        );

        assertTrue(diagnostic.contains("status=ready"));
        assertTrue(diagnostic.contains("differsFromVisible=true"));
        assertTrue(diagnostic.contains("visibleVsShapeShadow={idx=1"));
        assertTrue(diagnostic.contains("shapeShadowVsActual={none"));
        assertTrue(diagnostic.contains("winner=shapeShadow"));
    }
}
