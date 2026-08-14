package com.drewshelper.routing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrewsHelperRouteValidationHarnessTest
{
    @Test
    public void parsesNestedRouteSegmentFieldsAndPaths()
    {
        Map<String, String> fields = DrewsHelperRouteValidationHarness.parseFields(
            segmentLine("legal-detour-or-object-pressure"),
            DrewsHelperRouteValidationHarness.SEGMENT_PREFIX
        );

        assertEquals("destination", fields.get("reason"));
        assertEquals("true", fields.get("completed"));
        assertEquals("legal-detour-or-object-pressure", fields.get("classification"));
        assertTrue(fields.get("route").contains("first=miss"));
        assertTrue(fields.get("divergence").contains("mergeBack={expectedIdx=2 actualIdx=3"));
        assertEquals(3, DrewsHelperRouteValidationHarness.parsePath(fields.get("expectedPath")).size());
        assertEquals(4, DrewsHelperRouteValidationHarness.parsePath(fields.get("actualPath")).size());
    }

    @Test
    public void summarisesRouteAndObjectEvidenceTogether()
    {
        DrewsHelperRouteValidationHarness.EvidenceReport report =
            DrewsHelperRouteValidationHarness.analyseEvidence(
                Collections.singletonList(segmentLine("legal-detour-or-object-pressure")),
                Collections.singletonList(objectLine())
            );

        assertEquals(1, report.segmentRows);
        assertEquals(1, report.completedSegments);
        assertEquals(1, report.divergentSegments);
        assertEquals(1, report.objectRows);
        assertEquals(Integer.valueOf(1), report.segmentClassifications.get("legal-detour-or-object-pressure"));
        assertEquals(Integer.valueOf(1), report.objectCategories.get("door"));
        assertEquals(Integer.valueOf(1), report.objectStates.get("CLOSED_OPENABLE"));
        assertFalse(report.correlations.isEmpty());
        assertTrue(report.correlations.get(0).contains("nearbyObjects=["));
        assertTrue(report.correlations.get(0).contains("state=CLOSED_OPENABLE"));
    }

    @Test
    public void interruptedNonAdjacentIllegalRowsAreNotHardGates()
    {
        DrewsHelperRouteValidationHarness.EvidenceReport report =
            DrewsHelperRouteValidationHarness.analyseEvidence(
                Collections.singletonList(interruptedNonAdjacentIllegalSegmentLine()),
                Collections.emptyList()
            );

        assertEquals(1, report.segmentRows);
        assertEquals(0, report.completedSegments);
        assertEquals(1, report.interruptedSegments);
        assertEquals(0, report.illegalObservedEdges);
        assertEquals(1, report.nonPromotableIllegalObservedEdges);
    }

    @Test
    public void pilotReportFiltersToPilotRegionAndNamesRecaptureNeeded()
    {
        DrewsHelperRouteValidationHarness.PilotReport report =
            DrewsHelperRouteValidationHarness.analysePilot(
                null,
                Arrays.asList(interruptedNonAdjacentIllegalSegmentLine(), segmentLine("match")),
                Collections.singletonList(pilotObjectLine())
            );

        assertEquals(1, report.segmentRows);
        assertEquals(1, report.interruptedSegments);
        assertEquals(1, report.nonPromotableIllegalEdges);
        assertEquals(1, report.objectRows);
        assertTrue(report.touchedRegions.containsKey("48_50"));
        assertEquals("NEEDS_FOCUSED_RECAPTURE", report.verdict());
    }

    @Test
    public void pilotReportSupersedesOldInterruptedIllegalRowsWithFocusedCleanRecapture()
    {
        DrewsHelperRouteValidationHarness.PilotReport report =
            DrewsHelperRouteValidationHarness.analysePilot(
                null,
                Arrays.asList(interruptedNonAdjacentIllegalSegmentLine(), focusedCleanRecaptureLine()),
                Collections.singletonList(pilotObjectLine())
            );

        assertEquals(2, report.segmentRows);
        assertEquals(1, report.completedSegments);
        assertEquals(1, report.interruptedSegments);
        assertEquals(0, report.nonPromotableIllegalEdges);
        assertEquals(1, report.supersededNonPromotableIllegalEdges);
        assertEquals(0, report.completedAdjacentIllegalEdges);
        assertEquals("NO_COMPLETED_STATIC_DISAGREEMENT", report.verdict());
    }

    @Test
    public void structuralPathCheckAcceptsLegalStepsAndRejectsBlockedEdges()
    {
        List<WorldPoint> path = Arrays.asList(point(0, 0), point(1, 0), point(2, 0));

        assertTrue(DrewsHelperRouteValidationHarness.pathIssues(
            new OpenMovementMap(),
            DrewsHelperTransportGraph.empty(),
            path,
            point(0, 0),
            point(2, 0)
        ).isEmpty());

        List<String> issues = DrewsHelperRouteValidationHarness.pathIssues(
            new BlockedMovementMap(),
            DrewsHelperTransportGraph.empty(),
            path,
            point(0, 0),
            point(2, 0)
        );

        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("illegal-step@1"));
    }

    private static String segmentLine(String classification)
    {
        return "DREW_ROUTE_SEGMENT v1"
            + " tick=42 reason=destination completed=true"
            + " start=(0,0,0) clickDest=(2,0,0) routeTarget=(2,0,0)"
            + " routeStart=exact:idx=0:dist=0 routeDest=exact:idx=2:dist=0"
            + " expectedPoints=3 actualPoints=4"
            + " classification=" + classification
            + " route={first=miss 5=1/2 10=1/2 full=false lenDelta=1 maxDev=1 turnDelta=1}"
            + " divergence={idx=1 predicted=(1,0,0) actual=(0,1,0)"
            + " mergeBack={expectedIdx=2 actualIdx=3 stepDelta=1 point=(2,0,0)}}"
            + " edgeValidation={from=(0,0,0) actual=(0,1,0) target=(2,0,0)"
            + " legal=true type=cardinal continuation=found continuationDist=2"
            + " totalFromFork=3 expectedFromFork=2 delta=1 longer=true expanded=10"
            + " repeat=1 overrideCandidate=false}"
            + " expectedPath=[(0,0,0) -> (1,0,0) -> (2,0,0)]"
            + " actualPath=[(0,0,0) -> (0,1,0) -> (1,1,0) -> (2,0,0)]";
    }

    private static String interruptedNonAdjacentIllegalSegmentLine()
    {
        return "DREW_ROUTE_SEGMENT v1"
            + " tick=209 reason=destination-changed completed=false"
            + " start=(3092,3245,0) clickDest=(3131,3252,0) routeTarget=(3222,3218,0)"
            + " routeStart=exact:idx=0:dist=0 routeDest=exact:idx=40:dist=0"
            + " expectedPoints=41 actualPoints=27"
            + " classification=static-map-disagrees-with-live-step"
            + " route={first=match 5=0/5 10=0/10 full=false lenDelta=-11 maxDev=2 turnDelta=-2}"
            + " divergence={idx=1 predicted=(3093,3246,0) actual=(3093,3247,0)"
            + " mergeBack={expectedIdx=4 actualIdx=2 stepDelta=-2 point=(3095,3248,0)}}"
            + " edgeValidation={from=(3092,3245,0) actual=(3093,3247,0) target=(3131,3252,0)"
            + " legal=false type=non-adjacent continuation=found continuationDist=38"
            + " totalFromFork=39 expectedFromFork=40 delta=-1 longer=false expanded=5069"
            + " repeat=1 overrideCandidate=false}"
            + " expectedPath=[(3092,3245,0) -> (3093,3246,0) -> (3093,3247,0)]"
            + " actualPath=[(3092,3245,0) -> (3093,3247,0)]";
    }

    private static String focusedCleanRecaptureLine()
    {
        return "DREW_ROUTE_SEGMENT v1"
            + " tick=288 reason=destination completed=true"
            + " start=(3092,3246,0) clickDest=(3131,3252,0) routeTarget=(3131,3252,0)"
            + " routeStart=exact:idx=1:dist=0 routeDest=exact:idx=44:dist=0"
            + " expectedPoints=44 actualPoints=42"
            + " classification=legal-detour-or-object-pressure"
            + " route={first=match 5=5/5 10=10/10 full=false lenDelta=-2 maxDev=5 turnDelta=-6}"
            + " divergence={idx=16 predicted=(3107,3252,0) actual=(3108,3251,0)}"
            + " edgeValidation={from=(3107,3251,0) actual=(3108,3251,0) target=(3131,3252,0)"
            + " legal=true type=cardinal continuation=found continuationDist=28"
            + " totalFromFork=29 expectedFromFork=28 delta=1 longer=true expanded=2265"
            + " repeat=1 overrideCandidate=false}"
            + " expectedPath=[(3092,3246,0) -> (3093,3247,0) -> (3131,3252,0)]"
            + " actualPath=[(3092,3246,0) -> (3108,3251,0) -> (3131,3252,0)]";
    }

    private static String objectLine()
    {
        return "DREW_OBJECT_STATE v1"
            + " tick=25 scene=0:0:0 kind=wall tile=0,1,0 sceneTile=0,1"
            + " objectId=100 activeId=100 activeChanged=false"
            + " category=door state=CLOSED_OPENABLE name=Door actions=Open"
            + " varbit=- varp=- objectSize=1x1 definitionSize=1x1"
            + " orientation=1/0 config=0 hash=100 liveEdges=10 rawFlags=1026"
            + " confidence=CONFIRMED provenance=runelite-scene-live"
            + " mapConfidence=CONTRADICTED mapProvenance=missing-collision-map";
    }

    private static String pilotObjectLine()
    {
        return "DREW_OBJECT_STATE v1"
            + " tick=25 scene=0:0:0 kind=wall tile=3092,3245,0 sceneTile=20,45"
            + " objectId=100 activeId=100 activeChanged=false"
            + " category=door state=CLOSED_OPENABLE name=Door actions=Open"
            + " varbit=- varp=- objectSize=1x1 definitionSize=1x1"
            + " orientation=1/0 config=0 hash=100 liveEdges=10 rawFlags=1026"
            + " confidence=CONFIRMED provenance=runelite-scene-live"
            + " mapConfidence=CONFIRMED mapProvenance=pilot-test";
    }

    private static WorldPoint point(int x, int y)
    {
        return new WorldPoint(x, y, 0);
    }

    private static class OpenMovementMap implements DrewsHelperMovementMap
    {
        @Override
        public boolean canMoveNorth(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouth(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveWest(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveNorthEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveNorthWest(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouthEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouthWest(int x, int y, int plane)
        {
            return true;
        }
    }

    private static final class BlockedMovementMap extends OpenMovementMap
    {
        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return false;
        }
    }
}
