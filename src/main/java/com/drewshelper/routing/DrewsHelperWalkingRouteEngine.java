package com.drewshelper.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperWalkingRouteEngine
{
    private static final int MAX_EXPANDED_NODES_PER_SEGMENT = 2_000_000;
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

    public DrewsHelperWalkingRouteEngine(DrewsHelperMovementMap movementMap)
    {
        this.movementMap = movementMap;
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

        List<WorldPoint> route = new ArrayList<>();
        WorldPoint segmentStart = start;
        int walkingDistance = 0;

        for (int index = 0; index < destinations.size(); index++)
        {
            WorldPoint target = destinations.get(index);
            List<WorldPoint> segment = solveSegment(segmentStart, target);
            if (segment == null)
            {
                String message = "No walking path to waypoint #" + (index + 1);
                return DrewsHelperRouteSnapshot.noPath(route, destinations, message, walkingDistance);
            }

            if (route.isEmpty())
            {
                route.addAll(segment);
            }
            else if (segment.size() > 1)
            {
                route.addAll(segment.subList(1, segment.size()));
            }

            walkingDistance += Math.max(0, segment.size() - 1);
            segmentStart = target;
        }

        return DrewsHelperRouteSnapshot.ready(route, destinations, walkingDistance);
    }

    private List<WorldPoint> solveSegment(WorldPoint start, WorldPoint target) throws InterruptedException
    {
        if (start.getPlane() != target.getPlane())
        {
            return null;
        }

        if (start.equals(target))
        {
            return Collections.singletonList(start);
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

            if (node.point.equals(target))
            {
                return node.path();
            }

            expanded++;
            if (expanded > MAX_EXPANDED_NODES_PER_SEGMENT)
            {
                return null;
            }

            addNeighbors(node, context, open, bestNodes);
        }

        return null;
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
        if (bestNode != null && !next.isBetterPathToSamePointThan(bestNode))
        {
            return;
        }

        bestNodes.put(neighbor, next);
        open.add(next);
    }

    private static int heuristic(WorldPoint point, WorldPoint target)
    {
        if (point.getPlane() != target.getPlane())
        {
            return Integer.MAX_VALUE / 4;
        }

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

        if (primaryMove == primaryDirection && secondaryDirection != 0 && secondaryMove == secondaryDirection)
        {
            return 1;
        }

        if (primaryMove == 0 && secondaryMove == secondaryDirection)
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
            addMove(moves, xDirection, yDirection);
            addMove(moves, 0, yDirection);
        }
        else if (yRemaining > xRemaining)
        {
            addMove(moves, 0, yDirection);
            addMove(moves, xDirection, yDirection);
            addMove(moves, xDirection, 0);
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

            int byTurns = Integer.compare(turns, other.turns);
            if (byTurns != 0)
            {
                return byTurns;
            }

            return Long.compare(sequence, other.sequence);
        }

        private boolean isBetterPathToSamePointThan(SearchNode other)
        {
            if (distance != other.distance)
            {
                return distance < other.distance;
            }

            if (preferencePenalty != other.preferencePenalty)
            {
                return preferencePenalty < other.preferencePenalty;
            }

            if (turns != other.turns)
            {
                return turns < other.turns;
            }

            return sequence < other.sequence;
        }
    }
}
