package com.drewshelper.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperWalkingRouteEngine
{
    private static final int MAX_EXPANDED_NODES_PER_SEGMENT = 2_000_000;
    private static final int MAX_A_STAR_TIE_REFINEMENT_DISTANCE = 256;
    private static final int MAX_A_STAR_TIE_REFINEMENT_EXTRA_EXPANDED = 10_000;
    private static final int MAX_CLIENT_PATH_RANKING_EXPANDED = 100_000;
    private static final Move[] MOVES = {
        new Move(-1, 0),
        new Move(1, 0),
        new Move(0, -1),
        new Move(0, 1),
        new Move(-1, -1),
        new Move(1, -1),
        new Move(-1, 1),
        new Move(1, 1)
    };

    private final DrewsHelperMovementMap movementMap;
    private final DrewsHelperTransportGraph transportGraph;

    public DrewsHelperWalkingRouteEngine(DrewsHelperMovementMap movementMap)
    {
        this(movementMap, DrewsHelperTransportGraph.empty());
    }

    public DrewsHelperWalkingRouteEngine(DrewsHelperMovementMap movementMap, DrewsHelperTransportGraph transportGraph)
    {
        this.movementMap = movementMap;
        this.transportGraph = transportGraph == null ? DrewsHelperTransportGraph.empty() : transportGraph;
    }

    public DrewsHelperRouteSnapshot solve(WorldPoint start, List<WorldPoint> destinations) throws InterruptedException
    {
        return solve(start, destinations, DrewsHelperRouteSolverMode.A_STAR, false);
    }

    public DrewsHelperRouteSnapshot solve(
        WorldPoint start,
        List<WorldPoint> destinations,
        DrewsHelperRouteSolverMode solverMode,
        boolean benchmarkAgainstOtherSolver
    ) throws InterruptedException
    {
        if (start == null)
        {
            return DrewsHelperRouteSnapshot.noPlayer();
        }

        if (destinations.isEmpty())
        {
            return DrewsHelperRouteSnapshot.noWaypoints();
        }

        DrewsHelperRouteSolverMode selectedSolver = solverMode == null
            ? DrewsHelperRouteSolverMode.A_STAR
            : solverMode;
        RouteComputation primary = solveRoute(start, destinations, selectedSolver);
        RouteComputation benchmark = benchmarkAgainstOtherSolver
            ? solveRoute(start, destinations, selectedSolver.opposite())
            : RouteComputation.notRun(selectedSolver.opposite());

        if (!primary.isRouteFound())
        {
            return DrewsHelperRouteSnapshot.noPath(
                primary.path,
                destinations,
                primary.message,
                primary.walkingDistance
            );
        }

        return DrewsHelperRouteSnapshot.ready(
            primary.path,
            destinations,
            primary.walkingDistance,
            selectedSolver,
            primary.metrics,
            benchmark.path,
            benchmark.metrics
        );
    }

    public List<MoveCandidate> moveCandidates(WorldPoint from, WorldPoint target)
    {
        if (from == null || target == null)
        {
            return Collections.emptyList();
        }

        List<RouteStep> steps = legalSteps(from, target);
        List<MoveCandidate> candidates = new ArrayList<>(steps.size());
        for (RouteStep step : steps)
        {
            candidates.add(new MoveCandidate(
                step.order,
                step.destination,
                step.move.x,
                step.move.y,
                step.transport,
                heuristic(step.destination, target),
                step.preferencePenalty
            ));
        }

        return candidates;
    }

    private RouteComputation solveRoute(
        WorldPoint start,
        List<WorldPoint> destinations,
        DrewsHelperRouteSolverMode solverMode
    ) throws InterruptedException
    {
        long startedAt = System.nanoTime();
        List<WorldPoint> route = new ArrayList<>();
        WorldPoint segmentStart = start;
        int walkingDistance = 0;
        int expandedNodes = 0;

        for (int index = 0; index < destinations.size(); index++)
        {
            WorldPoint target = destinations.get(index);
            SearchResult segment = solveSegment(segmentStart, target, solverMode);
            expandedNodes += segment.expandedNodes;
            if (!segment.isFound())
            {
                String message = "No route to waypoint #" + (index + 1);
                return RouteComputation.notFound(
                    route,
                    walkingDistance,
                    message,
                    DrewsHelperRouteSearchMetrics.notFound(
                        solverMode,
                        System.nanoTime() - startedAt,
                        expandedNodes
                    )
                );
            }

            if (route.isEmpty())
            {
                route.addAll(segment.path);
            }
            else if (segment.path.size() > 1)
            {
                route.addAll(segment.path.subList(1, segment.path.size()));
            }

            walkingDistance += Math.max(0, segment.path.size() - 1);
            segmentStart = target;
        }

        return RouteComputation.found(
            route,
            walkingDistance,
            DrewsHelperRouteSearchMetrics.completed(
                solverMode,
                System.nanoTime() - startedAt,
                expandedNodes,
                route
            )
        );
    }

    private SearchResult solveSegment(
        WorldPoint start,
        WorldPoint target,
        DrewsHelperRouteSolverMode solverMode
    ) throws InterruptedException
    {
        if (solverMode == DrewsHelperRouteSolverMode.BFS)
        {
            return solveSegmentBfs(start, target);
        }

        return solveSegmentAStar(start, target);
    }

    private SearchResult solveSegmentAStar(WorldPoint start, WorldPoint target) throws InterruptedException
    {
        if (start.getPlane() != target.getPlane() && transportGraph.isEmpty())
        {
            return SearchResult.notFound(0);
        }

        if (start.equals(target))
        {
            return SearchResult.found(Collections.singletonList(start), 0);
        }

        PriorityQueue<SearchNode> open = new PriorityQueue<>();
        Map<WorldPoint, SearchNode> bestNodes = new HashMap<>();
        SearchContext context = new SearchContext(target);
        int startRemaining = heuristic(start, target);
        SearchNode startNode = new SearchNode(
            start,
            null,
            0,
            startRemaining,
            startRemaining,
            0,
            0,
            context.nextSequence()
        );
        open.add(startNode);
        bestNodes.put(start, startNode);
        int expanded = 0;
        SearchNode bestTarget = null;
        int expandedWhenFirstTargetFound = 0;

        while (!open.isEmpty())
        {
            if (Thread.currentThread().isInterrupted())
            {
                throw new InterruptedException("Walking route calculation cancelled");
            }

            SearchNode node = open.poll();
            SearchNode bestKnownNode = bestNodes.get(node.point);
            if (bestKnownNode != node)
            {
                continue;
            }

            if (bestTarget != null)
            {
                if (node.priority > bestTarget.distance
                    || expanded - expandedWhenFirstTargetFound > MAX_A_STAR_TIE_REFINEMENT_EXTRA_EXPANDED)
                {
                    break;
                }
            }

            if (node.point.equals(target))
            {
                if (node.distance > MAX_A_STAR_TIE_REFINEMENT_DISTANCE)
                {
                    return SearchResult.found(node.path(), expanded);
                }

                if (bestTarget == null)
                {
                    expandedWhenFirstTargetFound = expanded;
                    bestTarget = node;
                }
                else if (node.isBetterPathToSamePointThan(bestTarget, context.target))
                {
                    bestTarget = node;
                }
                continue;
            }

            expanded++;
            if (expanded > MAX_EXPANDED_NODES_PER_SEGMENT)
            {
                return SearchResult.notFound(expanded);
            }

            addNeighbors(node, context, open, bestNodes);
        }

        if (bestTarget != null)
        {
            SearchResult result = SearchResult.found(bestTarget.path(), expanded);
            return preferClientStyleShortestPath(start, target, result);
        }

        return SearchResult.notFound(expanded);
    }

    private SearchResult solveSegmentBfs(WorldPoint start, WorldPoint target) throws InterruptedException
    {
        if (start.getPlane() != target.getPlane() && transportGraph.isEmpty())
        {
            return SearchResult.notFound(0);
        }

        if (start.equals(target))
        {
            return SearchResult.found(Collections.singletonList(start), 0);
        }

        SearchBounds[] bounds = transportGraph.isEmpty()
            ? SearchBounds.walkingOnlyBounds(start, target)
            : new SearchBounds[] { SearchBounds.unbounded() };
        int totalExpanded = 0;
        for (SearchBounds bound : bounds)
        {
            SearchResult result = solveSegmentBfs(start, target, bound, totalExpanded);
            totalExpanded = result.expandedNodes;
            if (result.isFound() || totalExpanded > MAX_EXPANDED_NODES_PER_SEGMENT)
            {
                return result;
            }
        }

        return SearchResult.notFound(totalExpanded);
    }

    private SearchResult solveSegmentBfs(
        WorldPoint start,
        WorldPoint target,
        SearchBounds bounds,
        int alreadyExpanded
    ) throws InterruptedException
    {
        Queue<SearchNode> open = new ArrayDeque<>();
        Map<WorldPoint, SearchNode> visited = new HashMap<>();
        SearchContext context = new SearchContext(target);
        SearchNode startNode = new SearchNode(
            start,
            null,
            0,
            0,
            0,
            0,
            0,
            context.nextSequence()
        );
        open.add(startNode);
        visited.put(start, startNode);
        int expanded = alreadyExpanded;

        while (!open.isEmpty())
        {
            if (Thread.currentThread().isInterrupted())
            {
                throw new InterruptedException("Walking route calculation cancelled");
            }

            SearchNode node = open.remove();
            if (node.point.equals(target))
            {
                SearchResult result = SearchResult.found(node.path(), expanded);
                return preferClientStyleShortestPath(start, target, result);
            }

            expanded++;
            if (expanded > MAX_EXPANDED_NODES_PER_SEGMENT)
            {
                return SearchResult.notFound(expanded);
            }

            addBfsNeighbors(node, context, bounds, open, visited);
        }

        return SearchResult.notFound(expanded);
    }

    private void addNeighbors(
        SearchNode node,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes
    )
    {
        int x = node.point.getX();
        int y = node.point.getY();
        int plane = node.point.getPlane();

        for (Move move : orderedMoves(node.point, context.target))
        {
            if (canMove(x, y, plane, move))
            {
                addNeighbor(node, move, context, open, bestNodes);
            }
        }

        for (DrewsHelperTransportEdge edge : transportGraph.edgesFrom(node.point))
        {
            addTransportNeighbor(node, edge, context, open, bestNodes);
        }
    }

    private SearchResult preferClientStyleShortestPath(
        WorldPoint start,
        WorldPoint target,
        SearchResult initialResult
    )
    {
        if (!initialResult.isFound() || initialResult.path.size() - 1 > MAX_A_STAR_TIE_REFINEMENT_DISTANCE)
        {
            return initialResult;
        }

        ReverseDistanceResult distances = reverseDistancesToTarget(
            target,
            initialResult.path.size() - 1,
            SearchBounds.aroundPath(initialResult.path)
        );
        Integer startDistance = distances.distances.get(start);
        if (startDistance == null || startDistance != initialResult.path.size() - 1)
        {
            return initialResult.withAdditionalExpanded(distances.expandedNodes);
        }

        List<WorldPoint> rankedPath = new ArrayList<>(initialResult.path.size());
        rankedPath.add(start);
        WorldPoint current = start;
        int remainingDistance = startDistance;
        while (remainingDistance > 0)
        {
            WorldPoint next = null;
            for (RouteStep step : legalSteps(current, target))
            {
                Integer stepDistance = distances.distances.get(step.destination);
                if (stepDistance != null && stepDistance == remainingDistance - 1)
                {
                    next = step.destination;
                    break;
                }
            }

            if (next == null)
            {
                return initialResult.withAdditionalExpanded(distances.expandedNodes);
            }

            rankedPath.add(next);
            current = next;
            remainingDistance--;
        }

        if (!target.equals(rankedPath.get(rankedPath.size() - 1)))
        {
            return initialResult.withAdditionalExpanded(distances.expandedNodes);
        }

        return SearchResult.found(rankedPath, initialResult.expandedNodes + distances.expandedNodes);
    }

    private ReverseDistanceResult reverseDistancesToTarget(
        WorldPoint target,
        int maxDistance,
        SearchBounds bounds
    )
    {
        Queue<WorldPoint> open = new ArrayDeque<>();
        Map<WorldPoint, Integer> distances = new HashMap<>();
        open.add(target);
        distances.put(target, 0);
        int expanded = 0;

        while (!open.isEmpty())
        {
            WorldPoint point = open.remove();
            int distance = distances.get(point);
            if (distance >= maxDistance)
            {
                continue;
            }

            expanded++;
            if (expanded > MAX_CLIENT_PATH_RANKING_EXPANDED)
            {
                break;
            }

            addReverseWalkingPredecessors(point, distance, bounds, open, distances);
            addReverseTransportPredecessors(point, distance, bounds, open, distances);
        }

        return new ReverseDistanceResult(distances, expanded);
    }

    private void addReverseWalkingPredecessors(
        WorldPoint point,
        int distance,
        SearchBounds bounds,
        Queue<WorldPoint> open,
        Map<WorldPoint, Integer> distances
    )
    {
        int x = point.getX();
        int y = point.getY();
        int plane = point.getPlane();
        for (Move move : MOVES)
        {
            WorldPoint predecessor = new WorldPoint(x - move.x, y - move.y, plane);
            if (!bounds.contains(predecessor) || distances.containsKey(predecessor))
            {
                continue;
            }

            if (canMove(predecessor.getX(), predecessor.getY(), predecessor.getPlane(), move))
            {
                distances.put(predecessor, distance + 1);
                open.add(predecessor);
            }
        }
    }

    private void addReverseTransportPredecessors(
        WorldPoint point,
        int distance,
        SearchBounds bounds,
        Queue<WorldPoint> open,
        Map<WorldPoint, Integer> distances
    )
    {
        for (DrewsHelperTransportEdge edge : transportGraph.edgesTo(point))
        {
            WorldPoint predecessor = edge.getSource();
            if (!bounds.contains(predecessor) || distances.containsKey(predecessor))
            {
                continue;
            }

            distances.put(predecessor, distance + 1);
            open.add(predecessor);
        }
    }

    private void addBfsNeighbors(
        SearchNode node,
        SearchContext context,
        SearchBounds bounds,
        Queue<SearchNode> open,
        Map<WorldPoint, SearchNode> visited
    )
    {
        int x = node.point.getX();
        int y = node.point.getY();
        int plane = node.point.getPlane();

        for (Move move : orderedMoves(node.point, context.target))
        {
            if (canMove(x, y, plane, move))
            {
                WorldPoint neighbor = new WorldPoint(x + move.x, y + move.y, plane);
                if (bounds.contains(neighbor))
                {
                    addBfsNeighbor(node, neighbor, move, context, open, visited);
                }
            }
        }

        for (DrewsHelperTransportEdge edge : transportGraph.edgesFrom(node.point))
        {
            WorldPoint neighbor = edge.getDestination();
            Move direction = new Move(
                Integer.compare(neighbor.getX(), node.point.getX()),
                Integer.compare(neighbor.getY(), node.point.getY())
            );
            addBfsNeighbor(node, neighbor, direction, context, open, visited);
        }
    }

    private void addBfsNeighbor(
        SearchNode node,
        WorldPoint neighbor,
        Move move,
        SearchContext context,
        Queue<SearchNode> open,
        Map<WorldPoint, SearchNode> visited
    )
    {
        if (visited.containsKey(neighbor))
        {
            return;
        }

        int turns = node.previous == null || (node.directionX == move.x && node.directionY == move.y)
            ? node.turns
            : node.turns + 1;
        SearchNode next = new SearchNode(
            neighbor,
            node,
            node.distance + 1,
            node.distance + 1,
            0,
            0,
            turns,
            context.nextSequence(),
            move.x,
            move.y
        );
        visited.put(neighbor, next);
        open.add(next);
    }

    private void addNeighbor(
        SearchNode node,
        Move move,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes
    )
    {
        WorldPoint neighbor = new WorldPoint(
            node.point.getX() + move.x,
            node.point.getY() + move.y,
            node.point.getPlane()
        );
        addNeighbor(node, neighbor, move, context, open, bestNodes);
    }

    private void addTransportNeighbor(
        SearchNode node,
        DrewsHelperTransportEdge edge,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes
    )
    {
        WorldPoint neighbor = edge.getDestination();
        Move direction = new Move(
            Integer.compare(neighbor.getX(), node.point.getX()),
            Integer.compare(neighbor.getY(), node.point.getY())
        );
        addNeighbor(node, neighbor, direction, context, open, bestNodes);
    }

    private void addNeighbor(
        SearchNode node,
        WorldPoint neighbor,
        Move move,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes
    )
    {
        int distance = node.distance + 1;
        int remaining = heuristic(neighbor, context.target);
        int preferencePenalty = node.preferencePenalty + movePreferencePenalty(node.point, move, context.target);
        int turns = node.previous == null || (node.directionX == move.x && node.directionY == move.y)
            ? node.turns
            : node.turns + 1;
        SearchNode next = new SearchNode(
            neighbor,
            node,
            distance,
            distance + remaining,
            remaining,
            preferencePenalty,
            turns,
            context.nextSequence(),
            move.x,
            move.y
        );

        SearchNode bestNode = bestNodes.get(neighbor);
        if (bestNode != null && !next.isBetterPathToSamePointThan(bestNode, context.target))
        {
            return;
        }

        bestNodes.put(neighbor, next);
        open.add(next);
    }

    private List<RouteStep> legalSteps(WorldPoint from, WorldPoint target)
    {
        List<RouteStep> steps = new ArrayList<>();
        int x = from.getX();
        int y = from.getY();
        int plane = from.getPlane();
        int order = 1;

        for (Move move : orderedMoves(from, target))
        {
            if (canMove(x, y, plane, move))
            {
                steps.add(new RouteStep(
                    order++,
                    new WorldPoint(x + move.x, y + move.y, plane),
                    move,
                    false,
                    movePreferencePenalty(from, move, target)
                ));
            }
        }

        for (DrewsHelperTransportEdge edge : transportGraph.edgesFrom(from))
        {
            WorldPoint destination = edge.getDestination();
            Move direction = new Move(
                Integer.compare(destination.getX(), from.getX()),
                Integer.compare(destination.getY(), from.getY())
            );
            steps.add(new RouteStep(order++, destination, direction, true, 0));
        }

        return steps;
    }

    private static int heuristic(WorldPoint point, WorldPoint target)
    {
        return Math.max(Math.abs(point.getX() - target.getX()), Math.abs(point.getY() - target.getY()));
    }

    private static int movePreferencePenalty(WorldPoint point, Move move, WorldPoint target)
    {
        int xDirection = Integer.compare(target.getX(), point.getX());
        int yDirection = Integer.compare(target.getY(), point.getY());
        int xRemaining = Math.abs(target.getX() - point.getX());
        int yRemaining = Math.abs(target.getY() - point.getY());

        if (xRemaining > yRemaining)
        {
            return primaryAxisMovePenalty(move, xDirection, yDirection, true);
        }

        if (yRemaining > xRemaining)
        {
            return primaryAxisMovePenalty(move, xDirection, yDirection, false);
        }

        return tiedAxisMovePenalty(move, xDirection, yDirection);
    }

    private static int primaryAxisMovePenalty(Move move, int xDirection, int yDirection, boolean xPrimary)
    {
        int primaryMove = xPrimary ? move.x : move.y;
        int secondaryMove = xPrimary ? move.y : move.x;
        int primaryDirection = xPrimary ? xDirection : yDirection;
        int secondaryDirection = xPrimary ? yDirection : xDirection;

        if (primaryMove == primaryDirection && secondaryMove == 0)
        {
            return 0;
        }

        if (primaryMove == 0 && secondaryMove == secondaryDirection)
        {
            return 1;
        }

        if (primaryMove == primaryDirection && secondaryDirection != 0 && secondaryMove == secondaryDirection)
        {
            return 2;
        }

        if (primaryMove == primaryDirection)
        {
            return 3;
        }

        return 4;
    }

    private static int tiedAxisMovePenalty(Move move, int xDirection, int yDirection)
    {
        if (move.x == xDirection && move.y == yDirection)
        {
            return 0;
        }

        if ((move.x == xDirection && move.y == 0) || (move.x == 0 && move.y == yDirection))
        {
            return 1;
        }

        if (move.x == xDirection || move.y == yDirection)
        {
            return 2;
        }

        return 3;
    }

    private boolean canMove(int x, int y, int plane, Move move)
    {
        if (move.x < 0 && move.y == 0)
        {
            return movementMap.canMoveWest(x, y, plane);
        }
        if (move.x > 0 && move.y == 0)
        {
            return movementMap.canMoveEast(x, y, plane);
        }
        if (move.x == 0 && move.y < 0)
        {
            return movementMap.canMoveSouth(x, y, plane);
        }
        if (move.x == 0 && move.y > 0)
        {
            return movementMap.canMoveNorth(x, y, plane);
        }
        if (move.x < 0 && move.y < 0)
        {
            return movementMap.canMoveSouthWest(x, y, plane);
        }
        if (move.x > 0 && move.y < 0)
        {
            return movementMap.canMoveSouthEast(x, y, plane);
        }
        if (move.x < 0 && move.y > 0)
        {
            return movementMap.canMoveNorthWest(x, y, plane);
        }
        if (move.x > 0 && move.y > 0)
        {
            return movementMap.canMoveNorthEast(x, y, plane);
        }
        return false;
    }

    private static List<Move> orderedMoves(WorldPoint point, WorldPoint target)
    {
        int xDirection = Integer.compare(target.getX(), point.getX());
        int yDirection = Integer.compare(target.getY(), point.getY());
        int xRemaining = Math.abs(target.getX() - point.getX());
        int yRemaining = Math.abs(target.getY() - point.getY());

        List<Move> moves = new ArrayList<>(MOVES.length);
        if (xRemaining > yRemaining)
        {
            addMove(moves, xDirection, 0);
            addMove(moves, 0, yDirection);
            addMove(moves, xDirection, yDirection);
        }
        else if (yRemaining > xRemaining)
        {
            addMove(moves, 0, yDirection);
            addMove(moves, xDirection, 0);
            addMove(moves, xDirection, yDirection);
        }
        else
        {
            addMove(moves, xDirection, yDirection);
            addMove(moves, xDirection, 0);
            addMove(moves, 0, yDirection);
        }

        addMove(moves, xDirection, -yDirection);
        addMove(moves, -xDirection, yDirection);
        addMove(moves, -xDirection, 0);
        addMove(moves, 0, -yDirection);
        addMove(moves, -xDirection, -yDirection);

        for (Move move : MOVES)
        {
            addMove(moves, move.x, move.y);
        }
        return moves;
    }

    private static void addMove(List<Move> moves, int x, int y)
    {
        if (x == 0 && y == 0)
        {
            return;
        }

        Move move = move(x, y);
        if (move != null && !moves.contains(move))
        {
            moves.add(move);
        }
    }

    private static Move move(int x, int y)
    {
        for (Move move : MOVES)
        {
            if (move.x == x && move.y == y)
            {
                return move;
            }
        }
        return null;
    }

    private static final class RouteComputation
    {
        private final List<WorldPoint> path;
        private final int walkingDistance;
        private final String message;
        private final DrewsHelperRouteSearchMetrics metrics;

        private RouteComputation(
            List<WorldPoint> path,
            int walkingDistance,
            String message,
            DrewsHelperRouteSearchMetrics metrics
        )
        {
            this.path = path == null ? Collections.emptyList() : path;
            this.walkingDistance = walkingDistance;
            this.message = message;
            this.metrics = metrics;
        }

        private static RouteComputation found(
            List<WorldPoint> path,
            int walkingDistance,
            DrewsHelperRouteSearchMetrics metrics
        )
        {
            return new RouteComputation(path, walkingDistance, "Route ready", metrics);
        }

        private static RouteComputation notFound(
            List<WorldPoint> partialPath,
            int walkingDistance,
            String message,
            DrewsHelperRouteSearchMetrics metrics
        )
        {
            return new RouteComputation(partialPath, walkingDistance, message, metrics);
        }

        private static RouteComputation notRun(DrewsHelperRouteSolverMode solverMode)
        {
            return new RouteComputation(
                Collections.emptyList(),
                0,
                "Benchmark disabled",
                DrewsHelperRouteSearchMetrics.empty(solverMode)
            );
        }

        private boolean isRouteFound()
        {
            return metrics != null && metrics.isRouteFound();
        }
    }

    private static final class SearchResult
    {
        private final List<WorldPoint> path;
        private final int expandedNodes;

        private SearchResult(List<WorldPoint> path, int expandedNodes)
        {
            this.path = path == null ? Collections.emptyList() : path;
            this.expandedNodes = expandedNodes;
        }

        private static SearchResult found(List<WorldPoint> path, int expandedNodes)
        {
            return new SearchResult(path, expandedNodes);
        }

        private static SearchResult notFound(int expandedNodes)
        {
            return new SearchResult(Collections.emptyList(), expandedNodes);
        }

        private SearchResult withAdditionalExpanded(int additionalExpandedNodes)
        {
            return new SearchResult(path, expandedNodes + additionalExpandedNodes);
        }

        private boolean isFound()
        {
            return !path.isEmpty();
        }
    }

    private static final class SearchBounds
    {
        private static final int[] WALKING_ONLY_MARGINS = { 64, 128, 256, 512, Integer.MAX_VALUE };

        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final boolean unbounded;

        private SearchBounds(int minX, int maxX, int minY, int maxY, boolean unbounded)
        {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.unbounded = unbounded;
        }

        private static SearchBounds unbounded()
        {
            return new SearchBounds(0, 0, 0, 0, true);
        }

        private static SearchBounds aroundPath(List<WorldPoint> path)
        {
            if (path == null || path.isEmpty())
            {
                return unbounded();
            }

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (WorldPoint point : path)
            {
                minX = Math.min(minX, point.getX());
                maxX = Math.max(maxX, point.getX());
                minY = Math.min(minY, point.getY());
                maxY = Math.max(maxY, point.getY());
            }

            int margin = Math.max(8, Math.min(64, path.size() - 1));
            return new SearchBounds(minX - margin, maxX + margin, minY - margin, maxY + margin, false);
        }

        private static SearchBounds[] walkingOnlyBounds(WorldPoint start, WorldPoint target)
        {
            SearchBounds[] bounds = new SearchBounds[WALKING_ONLY_MARGINS.length];
            for (int index = 0; index < WALKING_ONLY_MARGINS.length; index++)
            {
                int margin = WALKING_ONLY_MARGINS[index];
                bounds[index] = margin == Integer.MAX_VALUE
                    ? unbounded()
                    : new SearchBounds(
                        Math.min(start.getX(), target.getX()) - margin,
                        Math.max(start.getX(), target.getX()) + margin,
                        Math.min(start.getY(), target.getY()) - margin,
                        Math.max(start.getY(), target.getY()) + margin,
                        false
                    );
            }
            return bounds;
        }

        private boolean contains(WorldPoint point)
        {
            return unbounded
                || (point.getX() >= minX
                && point.getX() <= maxX
                && point.getY() >= minY
                && point.getY() <= maxY);
        }
    }

    private static final class SearchContext
    {
        private final WorldPoint target;
        private long sequence;

        private SearchContext(WorldPoint target)
        {
            this.target = target;
        }

        private long nextSequence()
        {
            return sequence++;
        }
    }

    private static final class Move
    {
        private final int x;
        private final int y;

        private Move(int x, int y)
        {
            this.x = x;
            this.y = y;
        }
    }

    private static final class RouteStep
    {
        private final int order;
        private final WorldPoint destination;
        private final Move move;
        private final boolean transport;
        private final int preferencePenalty;

        private RouteStep(int order, WorldPoint destination, Move move, boolean transport, int preferencePenalty)
        {
            this.order = order;
            this.destination = destination;
            this.move = move;
            this.transport = transport;
            this.preferencePenalty = preferencePenalty;
        }
    }

    private static final class ReverseDistanceResult
    {
        private final Map<WorldPoint, Integer> distances;
        private final int expandedNodes;

        private ReverseDistanceResult(Map<WorldPoint, Integer> distances, int expandedNodes)
        {
            this.distances = distances;
            this.expandedNodes = expandedNodes;
        }
    }

    public static final class MoveCandidate
    {
        private final int order;
        private final WorldPoint destination;
        private final int directionX;
        private final int directionY;
        private final boolean transport;
        private final int distanceToTarget;
        private final int preferencePenalty;

        MoveCandidate(
            int order,
            WorldPoint destination,
            int directionX,
            int directionY,
            boolean transport,
            int distanceToTarget,
            int preferencePenalty
        )
        {
            this.order = order;
            this.destination = destination;
            this.directionX = directionX;
            this.directionY = directionY;
            this.transport = transport;
            this.distanceToTarget = distanceToTarget;
            this.preferencePenalty = preferencePenalty;
        }

        public int getOrder()
        {
            return order;
        }

        public WorldPoint getDestination()
        {
            return destination;
        }

        public int getDirectionX()
        {
            return directionX;
        }

        public int getDirectionY()
        {
            return directionY;
        }

        public boolean isTransport()
        {
            return transport;
        }

        public int getDistanceToTarget()
        {
            return distanceToTarget;
        }

        public int getPreferencePenalty()
        {
            return preferencePenalty;
        }

        public String getMoveType()
        {
            if (transport)
            {
                return "transport";
            }

            if (directionX != 0 && directionY != 0)
            {
                return "diagonal";
            }

            return "cardinal";
        }
    }

    private static final class SearchNode implements Comparable<SearchNode>
    {
        private final WorldPoint point;
        private final SearchNode previous;
        private final int distance;
        private final int priority;
        private final int remaining;
        private final int preferencePenalty;
        private final int turns;
        private final long sequence;
        private final int directionX;
        private final int directionY;

        private SearchNode(
            WorldPoint point,
            SearchNode previous,
            int distance,
            int priority,
            int remaining,
            int preferencePenalty,
            int turns,
            long sequence
        )
        {
            this(point, previous, distance, priority, remaining, preferencePenalty, turns, sequence, 0, 0);
        }

        private SearchNode(
            WorldPoint point,
            SearchNode previous,
            int distance,
            int priority,
            int remaining,
            int preferencePenalty,
            int turns,
            long sequence,
            int directionX,
            int directionY
        )
        {
            this.point = point;
            this.previous = previous;
            this.distance = distance;
            this.priority = priority;
            this.remaining = remaining;
            this.preferencePenalty = preferencePenalty;
            this.turns = turns;
            this.sequence = sequence;
            this.directionX = directionX;
            this.directionY = directionY;
        }

        private List<WorldPoint> path()
        {
            List<WorldPoint> path = new ArrayList<>();
            SearchNode node = this;
            while (node != null)
            {
                path.add(0, node.point);
                node = node.previous;
            }
            return path;
        }

        @Override
        public int compareTo(SearchNode other)
        {
            int byPriority = Integer.compare(priority, other.priority);
            if (byPriority != 0)
            {
                return byPriority;
            }

            int byRemaining = Integer.compare(remaining, other.remaining);
            if (byRemaining != 0)
            {
                return byRemaining;
            }

            int byPreferencePenalty = Integer.compare(preferencePenalty, other.preferencePenalty);
            if (byPreferencePenalty != 0)
            {
                return byPreferencePenalty;
            }

            return Long.compare(sequence, other.sequence);
        }

        private boolean isBetterPathToSamePointThan(SearchNode other, WorldPoint target)
        {
            if (distance != other.distance)
            {
                return distance < other.distance;
            }

            int byClientMovePreference = compareClientMovePreference(other, target);
            if (byClientMovePreference != 0)
            {
                return byClientMovePreference < 0;
            }

            if (preferencePenalty != other.preferencePenalty)
            {
                return preferencePenalty < other.preferencePenalty;
            }

            return sequence < other.sequence;
        }

        private int compareClientMovePreference(SearchNode other, WorldPoint target)
        {
            List<Integer> thisPreferences = clientMovePreferences(target);
            List<Integer> otherPreferences = other.clientMovePreferences(target);
            int limit = Math.min(thisPreferences.size(), otherPreferences.size());
            for (int index = 0; index < limit; index++)
            {
                int byPreference = Integer.compare(thisPreferences.get(index), otherPreferences.get(index));
                if (byPreference != 0)
                {
                    return byPreference;
                }
            }
            return Integer.compare(thisPreferences.size(), otherPreferences.size());
        }

        private List<Integer> clientMovePreferences(WorldPoint target)
        {
            List<Integer> preferences = new ArrayList<>(distance);
            SearchNode node = this;
            while (node.previous != null)
            {
                preferences.add(0, movePreferencePenalty(
                    node.previous.point,
                    new Move(node.directionX, node.directionY),
                    target
                ));
                node = node.previous;
            }
            return preferences;
        }
    }
}
