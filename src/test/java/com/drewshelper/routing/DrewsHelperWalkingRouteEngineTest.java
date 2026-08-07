package com.drewshelper.routing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
}
