package com.drewshelper.routing;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DrewsHelperWalkingRouteEngineTest
{
    @Test
    public void solvesOrderedWaypointChain() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(0, 0, 0),
            Arrays.asList(new WorldPoint(2, 0, 0), new WorldPoint(2, 2, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(4, route.getWalkingDistance());
        assertEquals(new WorldPoint(0, 0, 0), route.getPath().get(0));
        assertEquals(new WorldPoint(2, 0, 0), route.getPath().get(2));
        assertEquals(new WorldPoint(2, 2, 0), route.getPath().get(route.getPath().size() - 1));
    }

    @Test
    public void refusesCrossPlaneWalkingRoute() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(3200, 3200, 0),
            Arrays.asList(new WorldPoint(3200, 3201, 1))
        );

        assertEquals(DrewsHelperRouteStatus.NO_PATH, route.getStatus());
        assertTrue(route.getPath().isEmpty());
    }

    @Test
    public void usesTransportEdgeWhenItShortensRoute() throws Exception
    {
        WorldPoint source = new WorldPoint(0, 0, 0);
        WorldPoint destination = new WorldPoint(100, 100, 0);
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(Collections.singletonList(
            new DrewsHelperTransportEdge(source, destination, DrewsHelperTransportCategory.BASELINE, "Test ship")
        ));
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap(), graph);

        DrewsHelperRouteSnapshot route = engine.solve(source, Collections.singletonList(destination));

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(1, route.getWalkingDistance());
        assertEquals(Arrays.asList(source, destination), route.getPath());
        assertEquals(1, graph.edgesTo(destination).size());
        assertEquals(source, graph.edgesTo(destination).get(0).getSource());
        assertTrue(graph.edgesTo(source).isEmpty());
    }

    @Test
    public void transportEdgeCanChangePlanes() throws Exception
    {
        WorldPoint source = new WorldPoint(10, 10, 0);
        WorldPoint destination = new WorldPoint(10, 10, 1);
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.of(Collections.singletonList(
            new DrewsHelperTransportEdge(source, destination, DrewsHelperTransportCategory.BASELINE, "Test ladder")
        ));
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap(), graph);

        DrewsHelperRouteSnapshot route = engine.solve(source, Collections.singletonList(destination));

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(1, route.getWalkingDistance());
        assertEquals(Arrays.asList(source, destination), route.getPath());
    }

    @Test
    public void keepsStraightAxisRouteStraight() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(10, 10, 0),
            Arrays.asList(new WorldPoint(10, 20, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(10, route.getWalkingDistance());

        List<WorldPoint> path = route.getPath();
        for (int index = 0; index < path.size(); index++)
        {
            assertEquals(new WorldPoint(10, 10 + index, 0), path.get(index));
        }
    }

    @Test
    public void prefersPrimaryAxisForwardBeforeDiagonalProgress() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(0, 0, 0),
            Arrays.asList(new WorldPoint(-3, 10, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(10, route.getWalkingDistance());

        for (int index = 1; index <= 7; index++)
        {
            assertEquals(new WorldPoint(0, index, 0), route.getPath().get(index));
        }

        assertEquals(new WorldPoint(-1, 8, 0), route.getPath().get(8));
        assertEquals(new WorldPoint(-2, 9, 0), route.getPath().get(9));
        assertEquals(new WorldPoint(-3, 10, 0), route.getPath().get(10));
    }

    @Test
    public void keepsDiagonalWhenAxesAreTied() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(0, 0, 0),
            Arrays.asList(new WorldPoint(3, 3, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(3, route.getWalkingDistance());
        assertEquals(new WorldPoint(1, 1, 0), route.getPath().get(1));
        assertEquals(new WorldPoint(2, 2, 0), route.getPath().get(2));
        assertEquals(new WorldPoint(3, 3, 0), route.getPath().get(3));
    }

    @Test
    public void prefersClientCardinalForkWhenSameLengthPathRejoins() throws Exception
    {
        WorldPoint start = new WorldPoint(0, 0, 0);
        WorldPoint target = new WorldPoint(2, -3, 0);
        EdgeMovementMap movementMap = new EdgeMovementMap()
            .allow(start, new WorldPoint(1, 0, 0))
            .allow(new WorldPoint(1, 0, 0), new WorldPoint(2, -1, 0))
            .allow(new WorldPoint(2, -1, 0), new WorldPoint(2, -2, 0))
            .allow(new WorldPoint(2, -2, 0), target)
            .allow(start, new WorldPoint(1, -1, 0))
            .allow(new WorldPoint(1, -1, 0), new WorldPoint(1, -2, 0))
            .allow(new WorldPoint(1, -2, 0), new WorldPoint(1, -3, 0))
            .allow(new WorldPoint(1, -3, 0), target);
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(movementMap);

        DrewsHelperRouteSnapshot route = engine.solve(start, Collections.singletonList(target));

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(4, route.getWalkingDistance());
        assertEquals(new WorldPoint(1, 0, 0), route.getPath().get(1));
        assertEquals(new WorldPoint(2, -1, 0), route.getPath().get(2));
        assertEquals(target, route.getPath().get(route.getPath().size() - 1));
    }

    @Test
    public void matchesLiveClientForkTowardSoutheastWaypoints() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(DrewsHelperCollisionMap.loadDefault());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(2942, 3243, 0),
            Collections.singletonList(new WorldPoint(2962, 3214, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(32, route.getWalkingDistance());
        assertEquals(new WorldPoint(2950, 3228, 0), route.getPath().get(16));
        assertEquals(new WorldPoint(2951, 3228, 0), route.getPath().get(17));

        DrewsHelperRouteSnapshot southernRoute = engine.solve(
            new WorldPoint(2942, 3243, 0),
            Collections.singletonList(new WorldPoint(2951, 3208, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, southernRoute.getStatus());
        assertEquals(38, southernRoute.getWalkingDistance());
        assertEquals(new WorldPoint(2950, 3228, 0), southernRoute.getPath().get(16));
        assertEquals(new WorldPoint(2951, 3228, 0), southernRoute.getPath().get(17));
    }

    @Test
    public void clientStyleRankingWorksWithLoadedTransportGraph() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(
            DrewsHelperCollisionMap.loadDefault(),
            DrewsHelperTransportGraph.loadDefault(false)
        );

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(2942, 3243, 0),
            Collections.singletonList(new WorldPoint(2962, 3214, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(32, route.getWalkingDistance());
        assertEquals(new WorldPoint(2950, 3228, 0), route.getPath().get(16));
        assertEquals(new WorldPoint(2951, 3228, 0), route.getPath().get(17));
    }

    @Test
    public void bfsSolverModeProducesLayerAccurateRoute() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(0, 0, 0),
            Arrays.asList(new WorldPoint(0, 4, 0)),
            DrewsHelperRouteSolverMode.BFS,
            false
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(DrewsHelperRouteSolverMode.BFS, route.getPrimaryMetrics().getSolverMode());
        assertEquals(4, route.getWalkingDistance());
        assertEquals(new WorldPoint(0, 1, 0), route.getPath().get(1));
        assertTrue(route.getPrimaryMetrics().getExpandedNodes() > 0);
    }

    @Test
    public void benchmarkModeKeepsAlternateSolverPathAndMetrics() throws Exception
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(0, 0, 0),
            Arrays.asList(new WorldPoint(3, 8, 0)),
            DrewsHelperRouteSolverMode.A_STAR,
            true
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertEquals(DrewsHelperRouteSolverMode.A_STAR, route.getPrimaryMetrics().getSolverMode());
        assertEquals(DrewsHelperRouteSolverMode.BFS, route.getBenchmarkMetrics().getSolverMode());
        assertTrue(route.hasBenchmarkPath());
        assertEquals(route.getPath().get(0), route.getBenchmarkPath().get(0));
        assertEquals(route.getPath().get(route.getPath().size() - 1), route.getBenchmarkPath().get(route.getBenchmarkPath().size() - 1));
        assertTrue(route.getBenchmarkMetrics().getExpandedNodes() > 0);
    }

    @Test
    public void exposesLegalMoveCandidatesInSolverOrder()
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        List<DrewsHelperWalkingRouteEngine.MoveCandidate> candidates = engine.moveCandidates(
            new WorldPoint(0, 0, 0),
            new WorldPoint(2, 5, 0)
        );

        assertEquals(8, candidates.size());
        assertEquals(new WorldPoint(0, 1, 0), candidates.get(0).getDestination());
        assertEquals("cardinal", candidates.get(0).getMoveType());
        assertEquals(4, candidates.get(0).getDistanceToTarget());
        assertEquals(0, candidates.get(0).getPreferencePenalty());
        assertEquals(new WorldPoint(1, 0, 0), candidates.get(1).getDestination());
        assertEquals("cardinal", candidates.get(1).getMoveType());
        assertEquals(5, candidates.get(1).getDistanceToTarget());
        assertEquals(1, candidates.get(1).getPreferencePenalty());
        assertEquals(new WorldPoint(1, 1, 0), candidates.get(2).getDestination());
        assertEquals("diagonal", candidates.get(2).getMoveType());
        assertEquals(4, candidates.get(2).getDistanceToTarget());
        assertEquals(2, candidates.get(2).getPreferencePenalty());
    }

    @Test
    public void marksPredictedAndActualMoveCandidates()
    {
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(new OpenMovementMap());

        String trace = DrewsHelperRouteBenchmark.formatMoveCandidates(
            engine.moveCandidates(new WorldPoint(0, 0, 0), new WorldPoint(2, 5, 0)),
            new WorldPoint(1, 1, 0),
            new WorldPoint(1, 0, 0)
        );

        assertTrue(trace.contains("(1,1,0) diagonal dist=4 pref=2 predicted"));
        assertTrue(trace.contains("(1,0,0) cardinal dist=5 pref=1 actual"));
    }

    @Test
    public void loadsDefaultCollisionResource() throws Exception
    {
        assertNotNull(DrewsHelperCollisionMap.loadDefault());
    }

    @Test
    public void loadsBaselineTransportResourceAndFiltersWildernessToggle() throws Exception
    {
        WorldPoint ardougneLever = new WorldPoint(2561, 3311, 0);
        WorldPoint wildernessLeverDestination = new WorldPoint(3154, 3924, 0);
        WorldPoint portSarim = new WorldPoint(3029, 3217, 0);
        WorldPoint musaPoint = new WorldPoint(2956, 3146, 0);

        DrewsHelperTransportGraph baseline = DrewsHelperTransportGraph.loadDefault(false);
        DrewsHelperTransportGraph withWilderness = DrewsHelperTransportGraph.loadDefault(true);

        assertTrue(baseline.getEdgeCount() > 5_000);
        assertTrue(hasEdge(baseline, portSarim, musaPoint));
        assertFalse(hasEdge(baseline, ardougneLever, wildernessLeverDestination));
        assertTrue(hasEdge(withWilderness, ardougneLever, wildernessLeverDestination));
        assertTrue(withWilderness.getEdgeCount() > baseline.getEdgeCount());
    }

    private static boolean hasEdge(DrewsHelperTransportGraph graph, WorldPoint source, WorldPoint destination)
    {
        for (DrewsHelperTransportEdge edge : graph.edgesFrom(source))
        {
            if (destination.equals(edge.getDestination()))
            {
                return true;
            }
        }
        return false;
    }

    private static final class OpenMovementMap implements DrewsHelperMovementMap
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

    private static final class EdgeMovementMap implements DrewsHelperMovementMap
    {
        private final Set<String> allowedEdges = new HashSet<>();

        private EdgeMovementMap allow(WorldPoint from, WorldPoint to)
        {
            allowedEdges.add(edgeKey(from.getX(), from.getY(), from.getPlane(), to.getX(), to.getY()));
            return this;
        }

        @Override
        public boolean canMoveNorth(int x, int y, int plane)
        {
            return canMove(x, y, plane, 0, 1);
        }

        @Override
        public boolean canMoveSouth(int x, int y, int plane)
        {
            return canMove(x, y, plane, 0, -1);
        }

        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return canMove(x, y, plane, 1, 0);
        }

        @Override
        public boolean canMoveWest(int x, int y, int plane)
        {
            return canMove(x, y, plane, -1, 0);
        }

        @Override
        public boolean canMoveNorthEast(int x, int y, int plane)
        {
            return canMove(x, y, plane, 1, 1);
        }

        @Override
        public boolean canMoveNorthWest(int x, int y, int plane)
        {
            return canMove(x, y, plane, -1, 1);
        }

        @Override
        public boolean canMoveSouthEast(int x, int y, int plane)
        {
            return canMove(x, y, plane, 1, -1);
        }

        @Override
        public boolean canMoveSouthWest(int x, int y, int plane)
        {
            return canMove(x, y, plane, -1, -1);
        }

        private boolean canMove(int x, int y, int plane, int dx, int dy)
        {
            return allowedEdges.contains(edgeKey(x, y, plane, x + dx, y + dy));
        }

        private static String edgeKey(int fromX, int fromY, int plane, int toX, int toY)
        {
            return fromX + "," + fromY + "," + plane + ">" + toX + "," + toY;
        }
    }
}
