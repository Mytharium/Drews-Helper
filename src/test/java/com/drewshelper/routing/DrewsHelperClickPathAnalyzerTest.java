package com.drewshelper.routing;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DrewsHelperClickPathAnalyzerTest
{
    @Test
    public void groupsClickSegmentsIntoRankerDecisionBuckets()
    {
        String click = "DREW_CLICK_PATH v1 tick=2 result=accepted source=walk"
            + " clickTick=1 clickAge=1 action=WALK option=Walk_here target=- id=0 param0=2 param1=0"
            + " start=(0,0,0) clickedTile=(null) destBefore=(null)"
            + " acceptedDest=(2,0,0) routeTarget=(2,0,0) forkCandidates={none}";
        String segment = "DREW_ROUTE_SEGMENT v1 tick=4 reason=destination completed=true"
            + " start=(0,0,0) clickDest=(2,0,0) routeTarget=(2,0,0)"
            + " routeStart=exact:idx=0:dist=0 routeDest=exact:idx=2:dist=0"
            + " expectedPoints=3 actualPoints=3 classification=legal-route-ranker-or-click-shape"
            + " route={first=miss full=false} divergence={idx=1}"
            + " edgeValidation={from=(0,0,0) actual=(0,1,0) legal=true type=cardinal}"
            + " forkCandidates={1:(1,0,0):expected=true;2:(0,1,0):actual=true}"
            + " ranking={actualRank=2 expectedRank=1 clientWon=false clientRawWon=false shapeRawWon=true}"
            + " expectedPath=[(0,0,0) -> (1,0,0) -> (2,0,0)]"
            + " actualPath=[(0,0,0) -> (0,1,0) -> (2,0,0)]";

        DrewsHelperClickPathAnalyzer.Analysis analysis =
            DrewsHelperClickPathAnalyzer.analyse(Arrays.asList(click), Arrays.asList(segment));

        assertEquals(1, analysis.clickRows);
        assertEquals(1, (int) analysis.clickResults.get("accepted"));
        assertEquals(1, analysis.segmentRows);
        assertEquals(1, analysis.matchedSegments);
        assertEquals(1, (int) analysis.decisionBuckets.get("same-length-ranker-wrong"));
        assertEquals(1, (int) analysis.matchedDecisionBuckets.get("same-length-ranker-wrong"));
        assertEquals(1, (int) analysis.actualCandidateRanks.get("2"));
        assertEquals(1, (int) analysis.expectedCandidateRanks.get("1"));
        assertEquals(1, analysis.matchedExamples.size());
    }

    @Test
    public void ignoresWalkMenuParamsWhenCountingDestinationShifts()
    {
        String click = "DREW_CLICK_PATH v1 tick=2 result=accepted source=walk"
            + " start=(0,0,0) clickedTile=(99,99,0) acceptedDest=(2,0,0)";
        String segment = "DREW_ROUTE_SEGMENT v1 tick=4 reason=destination completed=true"
            + " start=(0,0,0) clickDest=(2,0,0) routeTarget=(2,0,0)"
            + " classification=match ranking={actualRank=-1 expectedRank=-1}";

        DrewsHelperClickPathAnalyzer.Analysis analysis =
            DrewsHelperClickPathAnalyzer.analyse(Collections.singletonList(click), Collections.singletonList(segment));

        assertEquals(1, analysis.matchedSegments);
        assertEquals(0, analysis.acceptedDestinationDiffersFromClickTile);
    }

    @Test
    public void countsDestinationChangesWithoutMatchingSegments()
    {
        String click = "DREW_CLICK_PATH v1 tick=9 result=accepted source=destination-change"
            + " start=(10,10,0) clickedTile=(null) acceptedDest=(11,10,0)";

        DrewsHelperClickPathAnalyzer.Analysis analysis =
            DrewsHelperClickPathAnalyzer.analyse(Collections.singletonList(click), Collections.emptyList());

        assertEquals(1, analysis.clickRows);
        assertEquals(1, (int) analysis.clickSources.get("destination-change"));
        assertEquals(0, analysis.segmentRows);
    }
}
