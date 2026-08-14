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
    private static final int MAX_OBSERVED_EDGE_VALIDATION_DISTANCE = 256;
    private static final int LOCAL_OVERRIDE_PREFERENCE_PENALTY = -1_000;
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
    private static final List<LocalWalkingOverride> LOCAL_WALKING_OVERRIDES = localWalkingOverrides();

    private final DrewsHelperMovementMap movementMap;
    private final DrewsHelperTransportGraph transportGraph;
    private final boolean avoidWilderness;

    public DrewsHelperWalkingRouteEngine(DrewsHelperMovementMap movementMap)
    {
        this(movementMap, DrewsHelperTransportGraph.empty());
    }

    public DrewsHelperWalkingRouteEngine(DrewsHelperMovementMap movementMap, DrewsHelperTransportGraph transportGraph)
    {
        this(movementMap, transportGraph, false);
    }

    public DrewsHelperWalkingRouteEngine(
        DrewsHelperMovementMap movementMap,
        DrewsHelperTransportGraph transportGraph,
        boolean avoidWilderness
    )
    {
        this.movementMap = movementMap;
        this.transportGraph = transportGraph == null ? DrewsHelperTransportGraph.empty() : transportGraph;
        this.avoidWilderness = avoidWilderness;
    }

    public DrewsHelperRouteSnapshot solve(WorldPoint start, List<WorldPoint> destinations) throws InterruptedException
    {
        if (start == null)
        {
            return DrewsHelperRouteSnapshot.noPlayer();
        }

        if (destinations.isEmpty())
        {
            return DrewsHelperRouteSnapshot.noWaypoints();
        }

        RouteComputation primary = solveRoute(start, destinations);

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
            primary.metrics
        );
    }

    public DrewsHelperRouteSnapshot solveWithoutLocalWalkingOverrides(
        WorldPoint start,
        List<WorldPoint> destinations
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

        RouteComputation primary = solveRoute(start, destinations, false);

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
            primary.metrics
        );
    }

    public DrewsHelperRouteSnapshot solveWithShapeRankingWithoutLocalWalkingOverrides(
        WorldPoint start,
        List<WorldPoint> destinations
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

        RouteComputation primary = solveRoute(start, destinations, false, RouteRankingMode.SHAPE);

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
            primary.metrics
        );
    }

    /** Needed by the travel-time estimator to tell a transport hop from a walked tile. */
    public DrewsHelperTransportGraph getTransportGraph()
    {
        return transportGraph;
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

    public ObservedEdgeDiagnostic validateObservedEdge(
        WorldPoint from,
        WorldPoint observed,
        WorldPoint target,
        int expectedRemainingFromFork
    )
    {
        if (from == null || observed == null || target == null)
        {
            return ObservedEdgeDiagnostic.unavailable(from, observed, target, "missing-point");
        }

        EdgeLegality edge = edgeLegality(from, observed, target);
        int expectedRemaining = Math.max(0, expectedRemainingFromFork);
        if (expectedRemaining > MAX_OBSERVED_EDGE_VALIDATION_DISTANCE)
        {
            return ObservedEdgeDiagnostic.withoutContinuation(
                from,
                observed,
                target,
                edge.legal,
                edge.type,
                expectedRemaining,
                "route-too-long"
            );
        }

        try
        {
            SearchResult continuation = solveSegmentAStar(observed, target);
            if (!continuation.isFound())
            {
                return ObservedEdgeDiagnostic.withoutContinuation(
                    from,
                    observed,
                    target,
                    edge.legal,
                    edge.type,
                    expectedRemaining,
                    "not-found",
                    continuation.expandedNodes
                );
            }

            return ObservedEdgeDiagnostic.withContinuation(
                from,
                observed,
                target,
                edge.legal,
                edge.type,
                expectedRemaining,
                Math.max(0, continuation.path.size() - 1),
                continuation.expandedNodes
            );
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return ObservedEdgeDiagnostic.withoutContinuation(
                from,
                observed,
                target,
                edge.legal,
                edge.type,
                expectedRemaining,
                "interrupted"
            );
        }
    }

    private RouteComputation solveRoute(WorldPoint start, List<WorldPoint> destinations) throws InterruptedException
    {
        return solveRoute(start, destinations, true, RouteRankingMode.CLIENT);
    }

    private RouteComputation solveRoute(
        WorldPoint start,
        List<WorldPoint> destinations,
        boolean localWalkingOverridesEnabled
    ) throws InterruptedException
    {
        return solveRoute(start, destinations, localWalkingOverridesEnabled, RouteRankingMode.CLIENT);
    }

    private RouteComputation solveRoute(
        WorldPoint start,
        List<WorldPoint> destinations,
        boolean localWalkingOverridesEnabled,
        RouteRankingMode rankingMode
    ) throws InterruptedException
    {
        long startedAt = System.nanoTime();
        rankNanos = 0;
        rankExpanded = 0;
        rankRuns = 0;
        List<WorldPoint> route = new ArrayList<>();
        WorldPoint segmentStart = start;
        int walkingDistance = 0;
        int expandedNodes = 0;

        for (int index = 0; index < destinations.size(); index++)
        {
            WorldPoint target = destinations.get(index);
            SearchResult segment = solveSegmentAStar(segmentStart, target, localWalkingOverridesEnabled, rankingMode);
            expandedNodes += segment.expandedNodes;
            if (!segment.isFound())
            {
                String message = "No route to waypoint #" + (index + 1);
                return RouteComputation.notFound(
                    route,
                    walkingDistance,
                    message,
                    DrewsHelperRouteSearchMetrics.notFound(
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
                System.nanoTime() - startedAt,
                expandedNodes,
                route
            )
        );
    }

    /**
     * Per-solve phase split. Four separate theories about where the time goes have now
     * been wrong, so the search stops being reasoned about and starts being measured:
     * the A* proper versus the client-style ranking pass that follows it.
     */
    private long rankNanos;
    private int rankExpanded;
    private int rankRuns;

    /** Phase split of the last solve, for the log line. */
    public String lastPhaseSummary()
    {
        return "rank=" + (rankNanos / 1_000_000) + "ms/" + rankExpanded + "n rankRuns=" + rankRuns;
    }

    private SearchResult solveSegmentAStar(WorldPoint start, WorldPoint target) throws InterruptedException
    {
        return solveSegmentAStar(start, target, true, RouteRankingMode.CLIENT);
    }

    private SearchResult solveSegmentAStar(
        WorldPoint start,
        WorldPoint target,
        boolean localWalkingOverridesEnabled
    ) throws InterruptedException
    {
        return solveSegmentAStar(start, target, localWalkingOverridesEnabled, RouteRankingMode.CLIENT);
    }

    private SearchResult solveSegmentAStar(
        WorldPoint start,
        WorldPoint target,
        boolean localWalkingOverridesEnabled,
        RouteRankingMode rankingMode
    ) throws InterruptedException
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
        SearchContext context = new SearchContext(start, target, transportArrivalBound(target));
        int startRemaining = heuristic(start, target);
        SearchNode startNode = new SearchNode(
            start,
            null,
            0,
            Math.min(startRemaining, context.transportArrivalBound),
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

            addNeighbors(node, context, open, bestNodes, localWalkingOverridesEnabled);
        }

        if (bestTarget != null)
        {
            SearchResult result = SearchResult.found(bestTarget.path(), expanded);
            return preferClientStyleShortestPath(start, target, result, localWalkingOverridesEnabled, rankingMode);
        }

        return SearchResult.notFound(expanded);
    }

    private void addNeighbors(
        SearchNode node,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes,
        boolean localWalkingOverridesEnabled
    )
    {
        for (RouteStep step : legalSteps(
            node.point,
            context.target,
            localWalkingOverridesEnabled,
            node.previous == null && node.point.equals(context.segmentStart)))
        {
            addNeighbor(node, step, context, open, bestNodes);
        }
    }

    private SearchResult preferClientStyleShortestPath(
        WorldPoint start,
        WorldPoint target,
        SearchResult initialResult,
        boolean localWalkingOverridesEnabled,
        RouteRankingMode rankingMode
    )
    {
        if (!initialResult.isFound() || initialResult.path.size() - 1 > MAX_A_STAR_TIE_REFINEMENT_DISTANCE)
        {
            return initialResult;
        }

        rankRuns++;
        long rankStartedAt = System.nanoTime();
        ReverseDistanceResult distances = reverseDistancesToTarget(
            start,
            target,
            initialResult.path.size() - 1,
            SearchBounds.aroundPath(initialResult.path),
            localWalkingOverridesEnabled
        );
        rankNanos += System.nanoTime() - rankStartedAt;
        rankExpanded += distances.expandedNodes;
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
            RouteStep next = null;
            for (RouteStep step : legalSteps(
                current,
                target,
                localWalkingOverridesEnabled,
                current.equals(start)))
            {
                Integer stepDistance = distances.distances.get(step.destination);
                if (stepDistance != null && stepDistance == remainingDistance - 1)
                {
                    if (next == null || isBetterShortestStep(
                        rankedPath,
                        current,
                        step,
                        next,
                        start,
                        target,
                        rankingMode
                    ))
                    {
                        next = step;
                    }

                    if (rankingMode == RouteRankingMode.CLIENT)
                    {
                        break;
                    }
                }
            }

            if (next == null)
            {
                return initialResult.withAdditionalExpanded(distances.expandedNodes);
            }

            rankedPath.add(next.destination);
            current = next.destination;
            remainingDistance--;
        }

        if (!target.equals(rankedPath.get(rankedPath.size() - 1)))
        {
            return initialResult.withAdditionalExpanded(distances.expandedNodes);
        }

        return SearchResult.found(rankedPath, initialResult.expandedNodes + distances.expandedNodes);
    }

    private static boolean isBetterShortestStep(
        List<WorldPoint> rankedPath,
        WorldPoint current,
        RouteStep candidate,
        RouteStep incumbent,
        WorldPoint segmentStart,
        WorldPoint target,
        RouteRankingMode rankingMode
    )
    {
        if (rankingMode != RouteRankingMode.SHAPE)
        {
            return false;
        }

        int candidateScore = shapeStepScore(rankedPath, current, candidate, segmentStart, target);
        int incumbentScore = shapeStepScore(rankedPath, current, incumbent, segmentStart, target);
        if (candidateScore != incumbentScore)
        {
            return candidateScore < incumbentScore;
        }

        if (candidate.preferencePenalty != incumbent.preferencePenalty)
        {
            return candidate.preferencePenalty < incumbent.preferencePenalty;
        }

        return candidate.order < incumbent.order;
    }

    private static int shapeStepScore(
        List<WorldPoint> rankedPath,
        WorldPoint current,
        RouteStep step,
        WorldPoint segmentStart,
        WorldPoint target
    )
    {
        int lineError = shapeLineError(segmentStart, target, step.destination);
        int turnPenalty = stepCreatesTurn(rankedPath, current, step) ? 1 : 0;
        int reversePenalty = stepMovesAwayFromTarget(current, step.destination, target) ? 1 : 0;
        return lineError * 10 + reversePenalty * 10 + turnPenalty * 2 + step.preferencePenalty;
    }

    private static int shapeLineError(WorldPoint start, WorldPoint target, WorldPoint point)
    {
        int totalX = target.getX() - start.getX();
        int totalY = target.getY() - start.getY();
        int majorAxis = Math.max(Math.abs(totalX), Math.abs(totalY));
        if (majorAxis == 0)
        {
            return 0;
        }

        int relativeX = point.getX() - start.getX();
        int relativeY = point.getY() - start.getY();
        return Math.abs(relativeX * totalY - relativeY * totalX) / majorAxis;
    }

    private static boolean stepCreatesTurn(List<WorldPoint> rankedPath, WorldPoint current, RouteStep step)
    {
        if (rankedPath.size() < 2)
        {
            return false;
        }

        WorldPoint previous = rankedPath.get(rankedPath.size() - 2);
        int previousX = Integer.compare(current.getX() - previous.getX(), 0);
        int previousY = Integer.compare(current.getY() - previous.getY(), 0);
        return previousX != step.move.x || previousY != step.move.y;
    }

    private static boolean stepMovesAwayFromTarget(WorldPoint current, WorldPoint next, WorldPoint target)
    {
        return heuristic(next, target) > heuristic(current, target);
    }

    private ReverseDistanceResult reverseDistancesToTarget(
        WorldPoint segmentStart,
        WorldPoint target,
        int maxDistance,
        SearchBounds bounds,
        boolean localWalkingOverridesEnabled
    )
    {
        // Dijkstra, not BFS: transport edges no longer all cost the same, and a FIFO queue
        // only produces correct distances when every edge weighs one.
        PriorityQueue<ReverseNode> open = new PriorityQueue<>();
        Map<WorldPoint, Integer> distances = new HashMap<>();
        open.add(new ReverseNode(target, 0));
        distances.put(target, 0);
        int expanded = 0;

        while (!open.isEmpty())
        {
            ReverseNode current = open.remove();
            WorldPoint point = current.point;
            int distance = current.distance;

            Integer best = distances.get(point);
            if (best == null || distance > best)
            {
                // Superseded by a shorter route found after this entry was queued.
                continue;
            }

            if (distance >= maxDistance)
            {
                continue;
            }

            expanded++;
            if (expanded > MAX_CLIENT_PATH_RANKING_EXPANDED)
            {
                break;
            }

            addReverseWalkingPredecessors(
                target,
                point,
                distance,
                bounds,
                open,
                distances,
                localWalkingOverridesEnabled
            );
            addReverseTransportPredecessors(segmentStart, target, point, distance, bounds, open, distances);
        }

        return new ReverseDistanceResult(distances, expanded);
    }

    private void addReverseWalkingPredecessors(
        WorldPoint target,
        WorldPoint point,
        int distance,
        SearchBounds bounds,
        PriorityQueue<ReverseNode> open,
        Map<WorldPoint, Integer> distances,
        boolean localWalkingOverridesEnabled
    )
    {
        int x = point.getX();
        int y = point.getY();
        int plane = point.getPlane();
        for (Move move : MOVES)
        {
            WorldPoint predecessor = new WorldPoint(x - move.x, y - move.y, plane);
            if (canMove(predecessor.getX(), predecessor.getY(), predecessor.getPlane(), move))
            {
                relax(predecessor, distance + 1, bounds, open, distances);
            }
        }

        if (localWalkingOverridesEnabled)
        {
            addReverseLocalOverridePredecessors(target, point, distance, bounds, open, distances);
        }
    }

    private void addReverseLocalOverridePredecessors(
        WorldPoint target,
        WorldPoint point,
        int distance,
        SearchBounds bounds,
        PriorityQueue<ReverseNode> open,
        Map<WorldPoint, Integer> distances
    )
    {
        for (LocalWalkingOverride override : LOCAL_WALKING_OVERRIDES)
        {
            WorldPoint predecessor = override.from;
            if (!override.matches(predecessor, point, target))
            {
                continue;
            }

            relax(predecessor, distance + 1, bounds, open, distances);
        }
    }

    /**
     * Dijkstra relaxation. Replaces the old first-visit-wins guard, which was only correct
     * while every edge cost one.
     */
    private static void relax(
        WorldPoint predecessor,
        int candidateDistance,
        SearchBounds bounds,
        PriorityQueue<ReverseNode> open,
        Map<WorldPoint, Integer> distances
    )
    {
        if (!bounds.contains(predecessor))
        {
            return;
        }

        Integer known = distances.get(predecessor);
        if (known != null && known <= candidateDistance)
        {
            return;
        }

        distances.put(predecessor, candidateDistance);
        open.add(new ReverseNode(predecessor, candidateDistance));
    }

    private void addReverseTransportPredecessors(
        WorldPoint segmentStart,
        WorldPoint target,
        WorldPoint point,
        int distance,
        SearchBounds bounds,
        PriorityQueue<ReverseNode> open,
        Map<WorldPoint, Integer> distances
    )
    {
        for (DrewsHelperTransportEdge edge : transportGraph.edgesTo(point))
        {
            if (edge.isOriginless())
            {
                if (originlessTransportAllowed(segmentStart, edge, target))
                {
                    relax(segmentStart, distance + transportCostUnits(edge), bounds, open, distances);
                }
                continue;
            }

            relax(edge.getSource(), distance + transportCostUnits(edge), bounds, open, distances);
        }
    }

    private void addNeighbor(
        SearchNode node,
        RouteStep step,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes
    )
    {
        addNeighbor(node, step.destination, step.move, step.preferencePenalty, step.costUnits,
            context, open, bestNodes);
    }

    private void addNeighbor(
        SearchNode node,
        WorldPoint neighbor,
        Move move,
        int stepPreferencePenalty,
        int stepCostUnits,
        SearchContext context,
        PriorityQueue<SearchNode> open,
        Map<WorldPoint, SearchNode> bestNodes
    )
    {
        int distance = node.distance + Math.max(1, stepCostUnits);
        int remaining = heuristic(neighbor, context.target);
        int preferencePenalty = node.preferencePenalty + stepPreferencePenalty;
        int turns = node.previous == null || (node.directionX == move.x && node.directionY == move.y)
            ? node.turns
            : node.turns + 1;
        SearchNode next = new SearchNode(
            neighbor,
            node,
            distance,
            distance + Math.min(remaining, context.transportArrivalBound),
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
        return legalSteps(from, target, true);
    }

    private List<RouteStep> legalSteps(WorldPoint from, WorldPoint target, boolean localWalkingOverridesEnabled)
    {
        return legalSteps(from, target, localWalkingOverridesEnabled, false);
    }

    private List<RouteStep> legalSteps(
        WorldPoint from,
        WorldPoint target,
        boolean localWalkingOverridesEnabled,
        boolean originlessAllowed
    )
    {
        List<RouteStep> steps = new ArrayList<>();
        int x = from.getX();
        int y = from.getY();
        int plane = from.getPlane();
        int order = 1;

        LocalWalkingOverride forcedOverride = localWalkingOverridesEnabled
            ? matchingForcedLocalWalkingOverride(from, target)
            : null;
        if (forcedOverride != null)
        {
            Move move = move(
                forcedOverride.destination.getX() - from.getX(),
                forcedOverride.destination.getY() - from.getY()
            );
            if (move != null && canMove(x, y, plane, move))
            {
                steps.add(new RouteStep(
                    order,
                    forcedOverride.destination,
                    move,
                    false,
                    LOCAL_OVERRIDE_PREFERENCE_PENALTY
                ));
                return steps;
            }
        }

        for (LocalWalkingOverride override : localWalkingOverridesEnabled
            ? matchingLocalWalkingOverrides(from, target)
            : Collections.<LocalWalkingOverride>emptyList())
        {
            Move move = move(
                override.destination.getX() - from.getX(),
                override.destination.getY() - from.getY()
            );
            if (move == null)
            {
                continue;
            }

            steps.add(new RouteStep(
                order++,
                override.destination,
                move,
                false,
                LOCAL_OVERRIDE_PREFERENCE_PENALTY
            ));
        }

        for (Move move : orderedMoves(from, target))
        {
            if (canMove(x, y, plane, move))
            {
                WorldPoint destination = new WorldPoint(x + move.x, y + move.y, plane);
                if (containsStepDestination(steps, destination))
                {
                    continue;
                }

                steps.add(new RouteStep(
                    order++,
                    destination,
                    move,
                    false,
                    movePreferencePenalty(from, move, target)
                ));
            }
        }

        for (DrewsHelperTransportEdge edge : transportGraph.edgesFrom(from))
        {
            order = addTransportStep(steps, order, from, target, edge);
        }

        if (originlessAllowed)
        {
            for (DrewsHelperTransportEdge edge : transportGraph.originlessEdges())
            {
                if (originlessTransportAllowed(from, edge, target))
                {
                    order = addTransportStep(steps, order, from, target, edge);
                }
            }
        }

        return steps;
    }

    private int addTransportStep(
        List<RouteStep> steps,
        int order,
        WorldPoint from,
        WorldPoint target,
        DrewsHelperTransportEdge edge
    )
    {
        WorldPoint destination = edge.getDestination();
        if (containsStepDestination(steps, destination))
        {
            return order;
        }

        if (isWildernessTransportToAvoid(edge, target))
        {
            return order;
        }

        Move direction = new Move(
            Integer.compare(destination.getX(), from.getX()),
            Integer.compare(destination.getY(), from.getY())
        );
        steps.add(new RouteStep(order++, destination, direction, true, 0, transportCostUnits(edge)));
        return order;
    }

    /**
     * Cost of an edge in half-ticks.
     *
     * <p>Running covers two tiles per game tick, so one walked tile already IS one half-tick -
     * which is why plain steps stay at cost 1 and only transports needed repricing. A transport
     * that takes D ticks costs 2 * D. Without this a quetzal flight cost the same as one
     * footstep and the router bent every route through the nearest transport.
     */
    private static int transportCostUnits(DrewsHelperTransportEdge edge)
    {
        return Math.max(1, 2 * edge.getDurationTicks());
    }

    /**
     * The Wilderness, as a bounding box.
     *
     * <p>Every bound here is derived from the shipped transport data, not from memory:
     * the 668 `Cross Wilderness Ditch` rows span x 2946..3340 on plane 0 and cross
     * y 3520 -> 3523, which fixes the southern edge and the width; the box is widened to
     * the enclosing region grid. Two traps were found while deriving it and both are the
     * reason this is a box rather than a half-plane:
     *
     * <ul>
     *   <li>`y >= 3522` alone is the entire northern half of the world - 7,389 of 12,388
     *       edges - and would have blocked Zeah, Rellekka, Etceteria and Piscatoris.</li>
     *   <li>An x-band with no ceiling still catches Prifddinas, whose spirit tree
     *       destination is (3274, 6123): it lives in a high-y instanced region, not at its
     *       apparent position. Zanaris (~4500) and the dungeons (~9000+) are the same.</li>
     * </ul>
     *
     * <p>With the ceiling in place the box touches 1,162 edges and contains only genuinely
     * Wilderness content - the ditch, the six obelisks, the lever, webs, barriers and
     * Wilderness ladders. No fairy ring and no spirit tree falls inside it.
     */
    /**
     * Upstream's Wilderness level boxes, copied exactly. The level-20 and level-30 boxes
     * overlap the base box rather than tiling it, which is why the bands are resolved by
     * narrowing from the deepest one down.
     */
    private static final int WILD_ABOVE_MIN_X = 2944;
    private static final int WILD_ABOVE_MAX_X = 3391;
    private static final int WILD_ABOVE_MIN_Y = 3525;
    private static final int WILD_ABOVE_MAX_Y = 3972;
    private static final int WILD_ABOVE_LEVEL_20_Y = 3680;
    private static final int WILD_ABOVE_LEVEL_30_Y = 3760;
    private static final int WILD_UNDER_MIN_X = 2944;
    private static final int WILD_UNDER_MAX_X = 3461;
    private static final int WILD_UNDER_MIN_Y = 9918;
    private static final int WILD_UNDER_MAX_Y = 10375;
    private static final int WILD_UNDER_LEVEL_20_Y = 10075;
    private static final int WILD_UNDER_LEVEL_30_Y = 10155;

    private static final int WILDERNESS_MIN_X = 2944;
    private static final int WILDERNESS_MAX_X = 3392;
    private static final int WILDERNESS_MIN_Y = 3522;
    private static final int WILDERNESS_MAX_Y = 3968;

    static boolean isInWilderness(WorldPoint point)
    {
        return point != null
            && point.getX() >= WILDERNESS_MIN_X && point.getX() <= WILDERNESS_MAX_X
            && point.getY() >= WILDERNESS_MIN_Y && point.getY() <= WILDERNESS_MAX_Y;
    }

    /**
     * Longest move that still counts as a physical crossing rather than a network hop. The 668
     * {@code Cross Wilderness Ditch} rows move 3 tiles, and gates, webs and ladders move fewer;
     * the Mage of Zamorak teleport moves over 1,200.
     */
    private static final int WILDERNESS_PHYSICAL_CROSSING_TILES = 16;

    /**
     * Whether the Wilderness preference refuses this transport.
     *
     * <p>A transport can touch the Wilderness in three ways, and they are not the same
     * question:
     *
     * <ul>
     *   <li><b>Entering</b> - source outside, destination inside. Always refused. This is what
     *       the preference has always meant.</li>
     *   <li><b>Leaving, or moving about inside</b> - source inside. Refused UNLESS it is a short
     *       physical crossing. Walking out means using the ditch, and whatever gates, webs and
     *       ladders lie on the way, so those have to stay legal or the player is walled in. A
     *       long-range hop is a different thing: reaching its source needed Wilderness access in
     *       the first place, which is exactly what the player switched off.</li>
     *   <li><b>Neither end inside</b> - never refused.</li>
     * </ul>
     *
     * <p>The reported case was {@code Teleport Mage of Zamorak 2581}: source 3106,3559 inside
     * the box, destination 3035,4852 out in Abyssal Space. A destination-only test never saw
     * that edge at all, which is why narrowing the old rule did not change the route.
     *
     * <p>One escape hatch throughout: if the segment TARGET is in the Wilderness the player
     * asked to go there, so nothing is refused. Originless transports are exempt by
     * construction - their source is the ANYWHERE sentinel, which is not in the box - so
     * escaping by home teleport still works. Walking is never filtered at all; only transport
     * steps reach this, so the solver can always walk itself out.
     */
    boolean isWildernessTransportToAvoid(DrewsHelperTransportEdge edge, WorldPoint target)
    {
        if (!avoidWilderness || isInWilderness(target))
        {
            return false;
        }

        WorldPoint source = edge.getSource();
        WorldPoint destination = edge.getDestination();
        if (edge.isOriginless() || !isInWilderness(source))
        {
            return isInWilderness(destination);
        }
        return !isShortPhysicalCrossing(source, destination);
    }

    /** Whether a transport moves the player only a few tiles, the way a ditch or a gate does. */
    private static boolean isShortPhysicalCrossing(WorldPoint from, WorldPoint to)
    {
        return Math.abs(from.getX() - to.getX()) <= WILDERNESS_PHYSICAL_CROSSING_TILES
            && Math.abs(from.getY() - to.getY()) <= WILDERNESS_PHYSICAL_CROSSING_TILES;
    }

    /**
     * Wilderness level band the point sits in, mirroring upstream's overlapping-box model
     * rather than a per-tile arithmetic formula.
     *
     * <p>Returns 0 outside, 20 for levels 1-20, 30 for 21-30 and 31 deeper still - the same
     * buckets upstream narrows down to in its own pathfinder. Upstream's safe-zone carve-outs
     * (Ferox Enclave, the Edgeville strip) are deliberately not modelled: they only separate
     * level 0 from levels 1-20, and every cap in the data treats those two identically.
     */
    static int wildernessLevelAt(WorldPoint point)
    {
        if (point == null)
        {
            return 0;
        }

        int x = point.getX();
        int y = point.getY();
        boolean aboveGround = x >= WILD_ABOVE_MIN_X && x <= WILD_ABOVE_MAX_X
            && y >= WILD_ABOVE_MIN_Y && y <= WILD_ABOVE_MAX_Y;
        boolean underground = x >= WILD_UNDER_MIN_X && x <= WILD_UNDER_MAX_X
            && y >= WILD_UNDER_MIN_Y && y <= WILD_UNDER_MAX_Y;
        if (!aboveGround && !underground)
        {
            return 0;
        }
        if ((aboveGround && y >= WILD_ABOVE_LEVEL_30_Y) || (underground && y >= WILD_UNDER_LEVEL_30_Y))
        {
            return 31;
        }
        if ((aboveGround && y >= WILD_ABOVE_LEVEL_20_Y) || (underground && y >= WILD_UNDER_LEVEL_20_Y))
        {
            return 30;
        }
        return 20;
    }

    private static boolean wildernessLevelAllows(WorldPoint from, int maxWildernessLevel)
    {
        return maxWildernessLevel < 0 || wildernessLevelAt(from) <= maxWildernessLevel;
    }

    /**
     * Leaving the Wilderness by teleport is legal and often the whole point, so the test is
     * the transport's own cap rather than mere presence in the box. Entering is still refused
     * by {@link #isWildernessTransportToAvoid}, which every home teleport passes anyway because
     * all four spellbook destinations sit outside.
     */
    private boolean originlessTransportAllowed(WorldPoint from, DrewsHelperTransportEdge edge, WorldPoint target)
    {
        return edge != null
            && edge.isOriginless()
            && from != null
            && wildernessLevelAllows(from, edge.getMaxWildernessLevel())
            && !isWildernessTransportToAvoid(edge, target);
    }

    private static boolean containsStepDestination(List<RouteStep> steps, WorldPoint destination)
    {
        for (RouteStep step : steps)
        {
            if (step.destination.equals(destination))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Lower bound on what any transport-using route into the target can cost.
     *
     * <p>The plain Chebyshev heuristic assumes you walk. That is a valid lower bound
     * for walking, but a wild OVER-estimate the moment a transport exists: a spirit tree
     * carries you ~630 tiles for a cost of 6, while the heuristic still charges ~630 for
     * standing next to it. An over-estimating heuristic is inadmissible, so A* stops being
     * optimal - and the early break in the main loop then discards the teleport outright
     * as soon as any cheaper-looking walking or boat route reaches the target. That is why
     * a Khazard-tree-to-Grand-Exchange route preferred an Ardougne boat across the ocean.
     *
     * <p>Any route to the target either walks the whole way - at least Chebyshev - or uses
     * at least one transport, and then its LAST hop alone already costs
     * {@code cost(e) + chebyshev(e.destination, target)}. Taking the minimum of that over
     * every edge ignores the cost of reaching the transport, which only makes the bound
     * smaller, so it stays valid. Capping the heuristic with it restores admissibility.
     *
     * <p>It is also consistent, so no node ever needs re-expanding: capping a consistent
     * heuristic with a constant preserves consistency, and for a transport edge the cap is
     * by construction no larger than that edge's own cost plus its destination's estimate.
     *
     * <p>The graph is already policy-filtered when it is loaded, so this only ever sees
     * transports the player can actually use.
     *
     * @return the bound, or {@link Integer#MAX_VALUE} when there are no transports at all,
     *     which leaves the plain heuristic untouched
     */
    private int transportArrivalBound(WorldPoint target)
    {
        int bound = Integer.MAX_VALUE;
        for (DrewsHelperTransportEdge edge : transportGraph.allEdges())
        {
            int arrival = transportCostUnits(edge) + heuristic(edge.getDestination(), target);
            if (arrival < bound)
            {
                bound = arrival;
            }
        }
        return bound;
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
        if (DrewsHelperTransportGraph.blocksShortcutWalkingStep(x, y, plane, move.x, move.y))
        {
            return false;
        }

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

    private EdgeLegality edgeLegality(WorldPoint from, WorldPoint to, WorldPoint target)
    {
        for (DrewsHelperTransportEdge edge : transportGraph.edgesFrom(from))
        {
            if (to.equals(edge.getDestination()))
            {
                return new EdgeLegality(true, "transport");
            }
        }

        if (isNonAdjacentHop(from, to))
        {
            for (DrewsHelperTransportEdge edge : transportGraph.originlessEdges())
            {
                if (to.equals(edge.getDestination()) && originlessTransportAllowed(from, edge, target))
                {
                    return new EdgeLegality(true, "transport");
                }
            }
        }

        if (isLocalWalkingOverride(from, to, target))
        {
            int dx = to.getX() - from.getX();
            int dy = to.getY() - from.getY();
            return new EdgeLegality(true, dx != 0 && dy != 0 ? "local-override-diagonal" : "local-override-cardinal");
        }

        if (from.getPlane() != to.getPlane())
        {
            return new EdgeLegality(false, "plane-change");
        }

        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 0 && dy == 0)
        {
            return new EdgeLegality(true, "stationary");
        }

        if (Math.abs(dx) > 1 || Math.abs(dy) > 1)
        {
            return new EdgeLegality(false, "non-adjacent");
        }

        Move move = move(dx, dy);
        if (move == null)
        {
            return new EdgeLegality(false, "unknown");
        }

        return new EdgeLegality(
            canMove(from.getX(), from.getY(), from.getPlane(), move),
            move.x != 0 && move.y != 0 ? "diagonal" : "cardinal"
        );
    }

    private static boolean isNonAdjacentHop(WorldPoint from, WorldPoint to)
    {
        return from.getPlane() != to.getPlane()
            || Math.abs(to.getX() - from.getX()) > 1
            || Math.abs(to.getY() - from.getY()) > 1;
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

    private static List<LocalWalkingOverride> localWalkingOverrides()
    {
        List<LocalWalkingOverride> overrides = new ArrayList<>();
        WorldPoint path1Target = new WorldPoint(2932, 3214, 0);
        addLocalOverride(overrides, path1Target, new WorldPoint(2939, 3223, 0), new WorldPoint(2939, 3222, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2939, 3222, 0), new WorldPoint(2938, 3221, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2938, 3221, 0), new WorldPoint(2937, 3220, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2937, 3220, 0), new WorldPoint(2936, 3219, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2936, 3219, 0), new WorldPoint(2935, 3218, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2935, 3218, 0), new WorldPoint(2934, 3217, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2934, 3217, 0), new WorldPoint(2933, 3216, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2933, 3216, 0), new WorldPoint(2932, 3215, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2932, 3215, 0), path1Target);
        addLocalOverride(overrides, path1Target, new WorldPoint(2935, 3218, 0), new WorldPoint(2935, 3217, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2935, 3217, 0), new WorldPoint(2934, 3216, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2934, 3216, 0), new WorldPoint(2933, 3215, 0));
        addLocalOverride(overrides, path1Target, new WorldPoint(2933, 3215, 0), path1Target);

        WorldPoint path3Target = new WorldPoint(2970, 3229, 0);
        addLocalOverride(overrides, path3Target, new WorldPoint(2966, 3231, 0), new WorldPoint(2967, 3231, 0));
        addLocalOverride(overrides, path3Target, new WorldPoint(2967, 3231, 0), new WorldPoint(2968, 3230, 0));
        addLocalOverride(overrides, path3Target, new WorldPoint(2968, 3230, 0), new WorldPoint(2969, 3229, 0));
        addLocalOverride(overrides, path3Target, new WorldPoint(2969, 3229, 0), path3Target);
        WorldPoint faladorSoutheastTarget = new WorldPoint(2951, 3208, 0);
        addForcedLocalPath(overrides, faladorSoutheastTarget,
            new WorldPoint(2942, 3243, 0),
            new WorldPoint(2942, 3242, 0),
            new WorldPoint(2942, 3241, 0),
            new WorldPoint(2942, 3240, 0),
            new WorldPoint(2942, 3239, 0),
            new WorldPoint(2942, 3238, 0),
            new WorldPoint(2942, 3237, 0),
            new WorldPoint(2942, 3236, 0),
            new WorldPoint(2943, 3235, 0),
            new WorldPoint(2943, 3234, 0),
            new WorldPoint(2944, 3233, 0),
            new WorldPoint(2945, 3232, 0),
            new WorldPoint(2946, 3231, 0),
            new WorldPoint(2947, 3230, 0),
            new WorldPoint(2948, 3229, 0),
            new WorldPoint(2949, 3228, 0),
            new WorldPoint(2950, 3228, 0),
            new WorldPoint(2951, 3228, 0),
            new WorldPoint(2952, 3228, 0),
            new WorldPoint(2953, 3227, 0),
            new WorldPoint(2953, 3226, 0),
            new WorldPoint(2953, 3225, 0),
            new WorldPoint(2953, 3224, 0),
            new WorldPoint(2953, 3223, 0),
            new WorldPoint(2953, 3222, 0),
            new WorldPoint(2953, 3221, 0),
            new WorldPoint(2953, 3220, 0),
            new WorldPoint(2953, 3219, 0),
            new WorldPoint(2953, 3218, 0),
            new WorldPoint(2953, 3217, 0),
            new WorldPoint(2953, 3216, 0),
            new WorldPoint(2953, 3215, 0),
            new WorldPoint(2953, 3214, 0),
            new WorldPoint(2953, 3213, 0),
            new WorldPoint(2953, 3212, 0),
            new WorldPoint(2953, 3211, 0),
            new WorldPoint(2953, 3210, 0),
            new WorldPoint(2952, 3209, 0),
            faladorSoutheastTarget
        );
        addForcedLocalPath(overrides, faladorSoutheastTarget,
            new WorldPoint(2946, 3239, 0),
            new WorldPoint(2946, 3238, 0),
            new WorldPoint(2946, 3237, 0),
            new WorldPoint(2946, 3236, 0),
            new WorldPoint(2946, 3235, 0),
            new WorldPoint(2946, 3234, 0),
            new WorldPoint(2946, 3233, 0),
            new WorldPoint(2946, 3232, 0),
            new WorldPoint(2946, 3231, 0),
            new WorldPoint(2947, 3230, 0),
            new WorldPoint(2948, 3229, 0),
            new WorldPoint(2949, 3228, 0),
            new WorldPoint(2950, 3228, 0),
            new WorldPoint(2951, 3228, 0),
            new WorldPoint(2952, 3228, 0),
            new WorldPoint(2953, 3227, 0),
            new WorldPoint(2953, 3226, 0),
            new WorldPoint(2953, 3225, 0),
            new WorldPoint(2953, 3224, 0),
            new WorldPoint(2953, 3223, 0),
            new WorldPoint(2953, 3222, 0),
            new WorldPoint(2953, 3221, 0),
            new WorldPoint(2953, 3220, 0),
            new WorldPoint(2953, 3219, 0),
            new WorldPoint(2953, 3218, 0),
            new WorldPoint(2953, 3217, 0),
            new WorldPoint(2953, 3216, 0),
            new WorldPoint(2953, 3215, 0),
            new WorldPoint(2953, 3214, 0),
            new WorldPoint(2953, 3213, 0),
            new WorldPoint(2953, 3212, 0),
            new WorldPoint(2953, 3211, 0),
            new WorldPoint(2953, 3210, 0),
            new WorldPoint(2952, 3209, 0),
            faladorSoutheastTarget
        );
        WorldPoint faladorNorthwestTarget = new WorldPoint(2942, 3243, 0);
        addForcedLocalPath(overrides, faladorNorthwestTarget,
            faladorSoutheastTarget,
            new WorldPoint(2951, 3209, 0),
            new WorldPoint(2951, 3210, 0),
            new WorldPoint(2951, 3211, 0),
            new WorldPoint(2951, 3212, 0),
            new WorldPoint(2951, 3213, 0),
            new WorldPoint(2951, 3214, 0),
            new WorldPoint(2951, 3215, 0),
            new WorldPoint(2951, 3216, 0),
            new WorldPoint(2951, 3217, 0),
            new WorldPoint(2951, 3218, 0),
            new WorldPoint(2952, 3219, 0),
            new WorldPoint(2953, 3220, 0),
            new WorldPoint(2953, 3221, 0),
            new WorldPoint(2953, 3222, 0),
            new WorldPoint(2953, 3223, 0),
            new WorldPoint(2953, 3224, 0),
            new WorldPoint(2953, 3225, 0),
            new WorldPoint(2953, 3226, 0),
            new WorldPoint(2953, 3227, 0),
            new WorldPoint(2952, 3227, 0),
            new WorldPoint(2951, 3227, 0),
            new WorldPoint(2950, 3227, 0),
            new WorldPoint(2949, 3228, 0),
            new WorldPoint(2949, 3229, 0),
            new WorldPoint(2948, 3230, 0),
            new WorldPoint(2948, 3231, 0),
            new WorldPoint(2948, 3232, 0),
            new WorldPoint(2947, 3233, 0),
            new WorldPoint(2946, 3234, 0),
            new WorldPoint(2946, 3235, 0),
            new WorldPoint(2946, 3236, 0),
            new WorldPoint(2946, 3237, 0),
            new WorldPoint(2946, 3238, 0),
            new WorldPoint(2946, 3239, 0),
            new WorldPoint(2945, 3240, 0),
            new WorldPoint(2944, 3241, 0),
            new WorldPoint(2943, 3242, 0),
            faladorNorthwestTarget
        );
        return Collections.unmodifiableList(overrides);
    }

    private static void addLocalOverride(
        List<LocalWalkingOverride> overrides,
        WorldPoint target,
        WorldPoint from,
        WorldPoint destination
    )
    {
        overrides.add(new LocalWalkingOverride(target, from, destination, false));
    }

    private static void addForcedLocalPath(
        List<LocalWalkingOverride> overrides,
        WorldPoint target,
        WorldPoint... path
    )
    {
        for (int index = 1; index < path.length; index++)
        {
            overrides.add(new LocalWalkingOverride(target, path[index - 1], path[index], true));
        }
    }

    private static List<LocalWalkingOverride> matchingLocalWalkingOverrides(WorldPoint from, WorldPoint target)
    {
        if (from == null || target == null || LOCAL_WALKING_OVERRIDES.isEmpty())
        {
            return Collections.emptyList();
        }

        List<LocalWalkingOverride> matches = new ArrayList<>();
        for (LocalWalkingOverride override : LOCAL_WALKING_OVERRIDES)
        {
            if (override.matchesFromAndTarget(from, target))
            {
                matches.add(override);
            }
        }
        return matches;
    }

    private static LocalWalkingOverride matchingForcedLocalWalkingOverride(WorldPoint from, WorldPoint target)
    {
        for (LocalWalkingOverride override : matchingLocalWalkingOverrides(from, target))
        {
            if (override.forced)
            {
                return override;
            }
        }
        return null;
    }

    private static boolean isLocalWalkingOverride(WorldPoint from, WorldPoint destination, WorldPoint target)
    {
        for (LocalWalkingOverride override : matchingLocalWalkingOverrides(from, target))
        {
            if (override.destination.equals(destination))
            {
                return true;
            }
        }
        return false;
    }

    private enum RouteRankingMode
    {
        CLIENT,
        SHAPE
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
        private final WorldPoint segmentStart;
        private final WorldPoint target;
        /**
         * Cap on the heuristic, so it stays a lower bound once transports exist.
         * See {@link DrewsHelperWalkingRouteEngine#transportArrivalBound}.
         */
        private final int transportArrivalBound;
        private long sequence;

        private SearchContext(WorldPoint segmentStart, WorldPoint target, int transportArrivalBound)
        {
            this.segmentStart = segmentStart;
            this.target = target;
            this.transportArrivalBound = transportArrivalBound;
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

    private static final class LocalWalkingOverride
    {
        private final WorldPoint target;
        private final WorldPoint from;
        private final WorldPoint destination;
        private final boolean forced;

        private LocalWalkingOverride(WorldPoint target, WorldPoint from, WorldPoint destination, boolean forced)
        {
            this.target = target;
            this.from = from;
            this.destination = destination;
            this.forced = forced;
        }

        private boolean matchesFromAndTarget(WorldPoint from, WorldPoint target)
        {
            return this.from.equals(from) && this.target.equals(target);
        }

        private boolean matches(WorldPoint from, WorldPoint destination, WorldPoint target)
        {
            return matchesFromAndTarget(from, target) && this.destination.equals(destination);
        }
    }

    private static final class RouteStep
    {
        private final int order;
        private final WorldPoint destination;
        private final Move move;
        private final boolean transport;
        private final int preferencePenalty;
        private final int costUnits;

        private RouteStep(int order, WorldPoint destination, Move move, boolean transport, int preferencePenalty)
        {
            this(order, destination, move, transport, preferencePenalty, 1);
        }

        private RouteStep(
            int order,
            WorldPoint destination,
            Move move,
            boolean transport,
            int preferencePenalty,
            int costUnits
        )
        {
            this.order = order;
            this.destination = destination;
            this.move = move;
            this.transport = transport;
            this.preferencePenalty = preferencePenalty;
            this.costUnits = Math.max(1, costUnits);
        }
    }

    /** Reverse-search entry. Carries its own distance so the queue can order on it. */
    private static final class ReverseNode implements Comparable<ReverseNode>
    {
        private final WorldPoint point;
        private final int distance;

        private ReverseNode(WorldPoint point, int distance)
        {
            this.point = point;
            this.distance = distance;
        }

        @Override
        public int compareTo(ReverseNode other)
        {
            return Integer.compare(distance, other.distance);
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

    private static final class EdgeLegality
    {
        private final boolean legal;
        private final String type;

        private EdgeLegality(boolean legal, String type)
        {
            this.legal = legal;
            this.type = type;
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

    public static final class ObservedEdgeDiagnostic
    {
        private final WorldPoint from;
        private final WorldPoint observed;
        private final WorldPoint target;
        private final boolean available;
        private final boolean edgeLegal;
        private final String edgeType;
        private final int expectedRemainingFromFork;
        private final boolean continuationFound;
        private final int continuationDistance;
        private final int totalRemainingFromFork;
        private final int continuationDelta;
        private final int expandedNodes;
        private final String reason;

        private ObservedEdgeDiagnostic(
            WorldPoint from,
            WorldPoint observed,
            WorldPoint target,
            boolean available,
            boolean edgeLegal,
            String edgeType,
            int expectedRemainingFromFork,
            boolean continuationFound,
            int continuationDistance,
            int expandedNodes,
            String reason
        )
        {
            this.from = from;
            this.observed = observed;
            this.target = target;
            this.available = available;
            this.edgeLegal = edgeLegal;
            this.edgeType = edgeType;
            this.expectedRemainingFromFork = expectedRemainingFromFork;
            this.continuationFound = continuationFound;
            this.continuationDistance = continuationDistance;
            this.totalRemainingFromFork = continuationFound ? continuationDistance + 1 : -1;
            this.continuationDelta = continuationFound ? totalRemainingFromFork - expectedRemainingFromFork : 0;
            this.expandedNodes = expandedNodes;
            this.reason = reason;
        }

        private static ObservedEdgeDiagnostic unavailable(
            WorldPoint from,
            WorldPoint observed,
            WorldPoint target,
            String reason
        )
        {
            return new ObservedEdgeDiagnostic(
                from,
                observed,
                target,
                false,
                false,
                "unknown",
                0,
                false,
                -1,
                0,
                reason
            );
        }

        private static ObservedEdgeDiagnostic withoutContinuation(
            WorldPoint from,
            WorldPoint observed,
            WorldPoint target,
            boolean edgeLegal,
            String edgeType,
            int expectedRemainingFromFork,
            String reason
        )
        {
            return withoutContinuation(
                from,
                observed,
                target,
                edgeLegal,
                edgeType,
                expectedRemainingFromFork,
                reason,
                0
            );
        }

        private static ObservedEdgeDiagnostic withoutContinuation(
            WorldPoint from,
            WorldPoint observed,
            WorldPoint target,
            boolean edgeLegal,
            String edgeType,
            int expectedRemainingFromFork,
            String reason,
            int expandedNodes
        )
        {
            return new ObservedEdgeDiagnostic(
                from,
                observed,
                target,
                true,
                edgeLegal,
                edgeType,
                expectedRemainingFromFork,
                false,
                -1,
                expandedNodes,
                reason
            );
        }

        private static ObservedEdgeDiagnostic withContinuation(
            WorldPoint from,
            WorldPoint observed,
            WorldPoint target,
            boolean edgeLegal,
            String edgeType,
            int expectedRemainingFromFork,
            int continuationDistance,
            int expandedNodes
        )
        {
            return new ObservedEdgeDiagnostic(
                from,
                observed,
                target,
                true,
                edgeLegal,
                edgeType,
                expectedRemainingFromFork,
                true,
                continuationDistance,
                expandedNodes,
                "found"
            );
        }

        public WorldPoint getFrom()
        {
            return from;
        }

        public WorldPoint getObserved()
        {
            return observed;
        }

        public WorldPoint getTarget()
        {
            return target;
        }

        public boolean isAvailable()
        {
            return available;
        }

        public boolean isEdgeLegal()
        {
            return edgeLegal;
        }

        public String getEdgeType()
        {
            return edgeType;
        }

        public int getExpectedRemainingFromFork()
        {
            return expectedRemainingFromFork;
        }

        public boolean isContinuationFound()
        {
            return continuationFound;
        }

        public int getContinuationDistance()
        {
            return continuationDistance;
        }

        public int getTotalRemainingFromFork()
        {
            return totalRemainingFromFork;
        }

        public int getContinuationDelta()
        {
            return continuationDelta;
        }

        public boolean isContinuationLonger()
        {
            return continuationFound && continuationDelta > 0;
        }

        public int getExpandedNodes()
        {
            return expandedNodes;
        }

        public String getReason()
        {
            return reason;
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
        /** Edges from the root. Carried forward so depth is never re-walked. */
        private final int steps;

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
            this.steps = previous == null ? 0 : previous.steps + 1;
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

            // Prefer the straighter path once everything above has tied. A diagonal step and
            // a cardinal step both cost 1, so a zigzag and a straight run tie exactly on cost
            // and on remaining distance; without this the winner was insertion order, which is
            // arbitrary with respect to straightness, and the route rendered as a checkerboard.
            // This sits BELOW every deliberate preference above it, so it only ever replaces a
            // coin flip - it cannot make a route longer.
            int byTurns = Integer.compare(turns, other.turns);
            if (byTurns != 0)
            {
                return byTurns;
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

            // Same reasoning as compareTo, and the position is deliberate: this sits BELOW
            // compareClientMovePreference, which exists to make the drawn route match the path
            // the game client actually walks. That must keep winning - straightening a line the
            // client would not walk would make the overlay lie. Only the arbitrary
            // insertion-order tie is replaced here.
            if (turns != other.turns)
            {
                return turns < other.turns;
            }

            return sequence < other.sequence;
        }

        /**
         * Lexicographic comparison of the two paths' per-step client move preferences,
         * read from the root forwards - identical in result to the previous
         * implementation, but without materialising either sequence.
         *
         * <p>The old version built a {@code List<Integer>} per node via {@code add(0, ...)},
         * which shifts the whole backing array on every insert: O(depth^2) to build, two
         * built per call, and this is called for every neighbour of every expanded node.
         * That made the whole A* O(nodes x depth^2) and is why a long route took seconds
         * while the reverse Dijkstra over the same map ran at sub-microsecond per node.
         *
         * <p>Two observations make the same answer cheap. Each node's sequence is its
         * parent's sequence with one element appended, so once two chains reach a common
         * ancestor every earlier element is identical by construction and cannot hold the
         * first difference. And walking backwards visits indices high-to-low, so the last
         * difference seen is the earliest one - which is the one lexicographic order wants.
         * Cost drops from O(depth^2) to O(divergence), which in a grid search is a handful
         * of tiles.
         */
        private int compareClientMovePreference(SearchNode other, WorldPoint target)
        {
            if (this == other)
            {
                return 0;
            }

            SearchNode a = this;
            SearchNode b = other;

            // The old code compared only up to min(size) and fell back to length after,
            // so the deeper chain's trailing steps must not take part in the comparison.
            for (int extra = steps - other.steps; extra > 0; extra--)
            {
                a = a.previous;
            }
            for (int extra = other.steps - steps; extra > 0; extra--)
            {
                b = b.previous;
            }

            int earliestDifference = 0;
            while (a != null && b != null && a != b && a.previous != null && b.previous != null)
            {
                int byPreference = Integer.compare(
                    movePreferencePenalty(a.previous.point, moveOf(a.directionX, a.directionY), target),
                    movePreferencePenalty(b.previous.point, moveOf(b.directionX, b.directionY), target)
                );
                if (byPreference != 0)
                {
                    earliestDifference = byPreference;
                }
                a = a.previous;
                b = b.previous;
            }

            if (earliestDifference != 0)
            {
                return earliestDifference;
            }

            return Integer.compare(steps, other.steps);
        }

        /**
         * A {@link Move} for the given delta without allocating one, since this sits
         * inside the search's hottest comparison. Falls back to a fresh instance for a
         * delta outside the eight walking moves so behaviour is unchanged either way.
         */
        private static Move moveOf(int x, int y)
        {
            for (Move move : MOVES)
            {
                if (move.x == x && move.y == y)
                {
                    return move;
                }
            }
            return new Move(x, y);
        }
    }
}
