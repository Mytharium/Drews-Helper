package com.drewshelper.routing;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Transport lookup is exact-tile-match, so a traversal recorded from wherever the player happened
 * to stand would report a missing edge for almost every real click. These cover the sweep that
 * makes the match survivable, and the offset it reports - the offset is the measurement, so a
 * wrong one is worse than no match at all.
 */
public class DrewsHelperTraversalMatchTest
{
    private static final WorldPoint SOURCE = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint DESTINATION = new WorldPoint(3200, 3200, 1);

    private static DrewsHelperTransportEdge edge(WorldPoint from, WorldPoint to)
    {
        return new DrewsHelperTransportEdge(
            from, to, DrewsHelperTransportCategory.BASELINE, "Climb-up");
    }

    @Test
    public void findsAnEdgeOnTheAnchorTileAtOffsetZero()
    {
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(
            Collections.singletonList(edge(SOURCE, DESTINATION)));

        DrewsHelperTraversalMatch match =
            DrewsHelperTraversalMatch.nearest(graph, SOURCE, DESTINATION);

        assertNotNull(match);
        assertEquals(0, match.getOffset());
        assertEquals(SOURCE, match.getSource());
        assertEquals(DESTINATION, match.getDestination());
    }

    @Test
    public void findsAnEdgeThePlayerWasStandingAwayFromAndReportsTheDistance()
    {
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(
            Collections.singletonList(edge(SOURCE, DESTINATION)));

        // Standing three tiles east and two north of the tile the row was authored against.
        WorldPoint anchor = new WorldPoint(3203, 3202, 0);

        DrewsHelperTraversalMatch match =
            DrewsHelperTraversalMatch.nearest(graph, anchor, DESTINATION);

        assertNotNull(match);
        assertEquals(3, match.getOffset());
        assertEquals(SOURCE, match.getSource());
    }

    @Test
    public void prefersTheNearerOfTwoEdgesThatBothExplainTheMove()
    {
        WorldPoint far = new WorldPoint(3190, 3200, 0);
        WorldPoint near = new WorldPoint(3198, 3200, 0);
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(
            Arrays.asList(edge(far, DESTINATION), edge(near, DESTINATION)));

        DrewsHelperTraversalMatch match =
            DrewsHelperTraversalMatch.nearest(graph, SOURCE, DESTINATION);

        assertNotNull(match);
        assertEquals(2, match.getOffset());
        assertEquals(near, match.getSource());
    }

    @Test
    public void returnsNothingWhenNoEdgeEndsWhereThePlayerLanded()
    {
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(
            Collections.singletonList(edge(SOURCE, DESTINATION)));

        WorldPoint somewhereElse = new WorldPoint(2500, 2500, 0);

        assertNull(DrewsHelperTraversalMatch.nearest(graph, SOURCE, somewhereElse));
    }

    @Test
    public void returnsNothingBeyondTheSearchRadiusRatherThanScanningTheWorld()
    {
        WorldPoint distant = new WorldPoint(
            SOURCE.getX() + DrewsHelperTraversalMatch.SEARCH_RADIUS + 1, SOURCE.getY(), 0);
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(
            Collections.singletonList(edge(distant, DESTINATION)));

        assertNull(DrewsHelperTraversalMatch.nearest(graph, SOURCE, DESTINATION));
    }

    @Test
    public void toleratesMissingInputsRatherThanThrowingMidTraversal()
    {
        assertNull(DrewsHelperTraversalMatch.nearest(null, SOURCE, DESTINATION));
        assertNull(DrewsHelperTraversalMatch.nearest(
            DrewsHelperTransportGraph.empty(), null, DESTINATION));
        assertNull(DrewsHelperTraversalMatch.nearest(
            DrewsHelperTransportGraph.empty(), SOURCE, null));
    }

    @Test
    public void sanitiseStripsColourTagsAndSpacesSoRowsStayParseable()
    {
        assertEquals("Trapdoor", DrewsHelperTraversalRecorder.sanitise("<col=00ff00>Trapdoor"));
        assertEquals("Large_door", DrewsHelperTraversalRecorder.sanitise("Large door"));
        assertEquals("-", DrewsHelperTraversalRecorder.sanitise(null));
        assertEquals("-", DrewsHelperTraversalRecorder.sanitise(""));
        assertEquals("-", DrewsHelperTraversalRecorder.sanitise("<col=ffffff>"));
    }
}
