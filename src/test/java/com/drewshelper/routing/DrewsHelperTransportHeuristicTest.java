package com.drewshelper.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * A teleport standing next to you must not lose to a boat across the ocean.
 *
 * <p>The Chebyshev heuristic assumes you walk. That is a valid lower bound for walking but a
 * wild over-estimate once a transport exists: the Battlefield of Khazard spirit tree carries
 * you ~630 tiles for a cost of 6, while the heuristic charged ~621 for standing beside it. An
 * over-estimating heuristic is inadmissible, and the search's early break then discarded the
 * tree outright the moment the cheaper-looking Ardougne boat reached the Grand Exchange.
 *
 * <p>Reported from live play: waypoint on the Grand Exchange, standing at the spirit tree, and
 * a 517-tile route via the docks instead of one hop. Measured before the fix, this start gave
 * a 418-step route with two hops (walk to docks, then boat); after, 86 steps with one.
 *
 * <p>The start deliberately sits a short walk from the tree. Starting exactly ON it cannot
 * reproduce the bug at all - the start node is expanded first unconditionally, so its
 * transport edges are offered before anything can out-compete them on f-value.
 */
public class DrewsHelperTransportHeuristicTest
{
    /** A short walk east of the Battlefield of Khazard spirit tree. */
    private static final WorldPoint NEAR_KHAZARD_TREE = new WorldPoint(2570, 3245, 0);

    /** Standing on the tree itself - the case that always worked. */
    private static final WorldPoint ON_KHAZARD_TREE = new WorldPoint(2556, 3258, 0);

    private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3176, 3504, 0);

    /** Well below the 418-step boat detour, well above the ~86-step hop. */
    private static final int SHORT_ROUTE_LIMIT = 150;

    @Test
    public void aSpiritTreeAShortWalkAwayBeatsABoatAcrossTheOcean() throws Exception
    {
        List<WorldPoint> path = solveToGrandExchange(NEAR_KHAZARD_TREE);

        assertTrue(
            "expected the spirit tree hop, got a " + path.size() + "-step route - the heuristic"
                + " is over-estimating again and the teleport is being pruned",
            path.size() < SHORT_ROUTE_LIMIT
        );
    }

    @Test
    public void thatRouteTakesExactlyOneTransport() throws Exception
    {
        // Two hops means it walked to the docks and took the boat. One means it took the tree.
        assertEquals(
            "expected a single spirit tree hop rather than a walk-plus-boat",
            1,
            countTransportJumps(solveToGrandExchange(NEAR_KHAZARD_TREE))
        );
    }

    @Test
    public void standingOnTheTreeStillWorks() throws Exception
    {
        // This case was never broken; pinned so the fix cannot regress it.
        List<WorldPoint> path = solveToGrandExchange(ON_KHAZARD_TREE);

        assertTrue("expected a handful of steps, got " + path.size(), path.size() < 40);
        assertEquals("expected a single spirit tree hop", 1, countTransportJumps(path));
    }

    private static int countTransportJumps(List<WorldPoint> path)
    {
        int jumps = 0;
        for (int index = 0; index + 1 < path.size(); index++)
        {
            if (DrewsHelperRouteSnapshot.isTransportJump(path.get(index), path.get(index + 1)))
            {
                jumps++;
            }
        }
        return jumps;
    }

    private static List<WorldPoint> solveToGrandExchange(WorldPoint start)
        throws IOException, InterruptedException
    {
        // UNRESTRICTED stands in for an account that has done Tree Gnome Village, which is the
        // only requirement on the Khazard -> Grand Exchange rows.
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.loadDefault(
            DrewsHelperTransportPolicy.builder().build(),
            DrewsHelperPlayerCapability.UNRESTRICTED
        );

        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(
            DrewsHelperCollisionMap.loadDefault(),
            graph
        );

        DrewsHelperRouteSnapshot snapshot =
            engine.solve(start, Collections.singletonList(GRAND_EXCHANGE));
        assertTrue("no route found at all from " + start, snapshot.hasPath());
        return snapshot.getPath();
    }
}
