package com.drewshelper.routing;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperRouteBenchmark
{
    public static final int DEFAULT_TRACE_TILE_LIMIT = 10;
    public static final int DEFAULT_DIVERGENCE_WINDOW_RADIUS = 4;

    private DrewsHelperRouteBenchmark()
    {
    }

    public static Report compare(
        DrewsHelperRouteSolverMode solverMode,
        List<WorldPoint> expectedPath,
        List<WorldPoint> actualPath
    )
    {
        List<WorldPoint> expected = expectedPath == null ? Collections.emptyList() : expectedPath;
        List<WorldPoint> actual = actualPath == null ? Collections.emptyList() : actualPath;
        return new Report(
            solverMode,
            firstStepDirectionMatches(expected, actual),
            prefixMatches(expected, actual, 5),
            comparedMovementTicks(actual, 5),
            prefixMatches(expected, actual, 10),
            comparedMovementTicks(actual, 10),
            expected.equals(actual),
            pathDistance(expected),
            pathDistance(actual),
            maxLateralDeviation(expected, actual),
            turnCount(expected),
            turnCount(actual)
        );
    }

    public static int pathDistance(List<WorldPoint> path)
    {
        if (path == null || path.size() < 2)
        {
            return 0;
        }

        int distance = 0;
        for (int index = 1; index < path.size(); index++)
        {
            distance += tileDistance(path.get(index - 1), path.get(index));
        }
        return distance;
    }

    public static int turnCount(List<WorldPoint> path)
    {
        if (path == null || path.size() < 3)
        {
            return 0;
        }

        Direction previous = null;
        int turns = 0;
        for (int index = 1; index < path.size(); index++)
        {
            Direction direction = Direction.between(path.get(index - 1), path.get(index));
            if (direction.isStationary())
            {
                continue;
            }

            if (previous != null && !previous.equals(direction))
            {
                turns++;
            }
            previous = direction;
        }
        return turns;
    }

    public static String formatPoint(WorldPoint point)
    {
        if (point == null)
        {
            return "(null)";
        }

        return "(" + point.getX() + "," + point.getY() + "," + point.getPlane() + ")";
    }

    public static String formatPathPrefix(List<WorldPoint> path)
    {
        return formatPathPrefix(path, DEFAULT_TRACE_TILE_LIMIT);
    }

    public static String formatPathPrefix(List<WorldPoint> path, int limit)
    {
        List<WorldPoint> points = path == null ? Collections.emptyList() : path;
        int prefixLength = Math.min(points.size(), Math.max(0, limit));
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < prefixLength; index++)
        {
            if (index > 0)
            {
                builder.append(" -> ");
            }
            builder.append(formatPoint(points.get(index)));
        }

        if (points.size() > prefixLength)
        {
            if (prefixLength > 0)
            {
                builder.append(" -> ");
            }
            builder.append("... total=").append(points.size());
        }

        return builder.append(']').toString();
    }

    public static int firstDivergenceIndex(
        List<WorldPoint> expectedPath,
        List<WorldPoint> actualPath,
        boolean actualComplete
    )
    {
        List<WorldPoint> expected = expectedPath == null ? Collections.emptyList() : expectedPath;
        List<WorldPoint> actual = actualPath == null ? Collections.emptyList() : actualPath;
        int compared = Math.min(expected.size(), actual.size());
        for (int index = 0; index < compared; index++)
        {
            if (!expected.get(index).equals(actual.get(index)))
            {
                return index;
            }
        }

        if (actualComplete && expected.size() != actual.size())
        {
            return compared;
        }

        return -1;
    }

    public static String formatDivergence(
        List<WorldPoint> expectedPath,
        List<WorldPoint> actualPath,
        boolean actualComplete
    )
    {
        int divergenceIndex = firstDivergenceIndex(expectedPath, actualPath, actualComplete);
        if (divergenceIndex < 0)
        {
            return "none";
        }

        return "idx=" + divergenceIndex
            + " prevDir=" + formatPreviousDirection(actualPath, divergenceIndex)
            + " predicted=" + formatPoint(pointAt(expectedPath, divergenceIndex))
            + " actual=" + formatPoint(pointAt(actualPath, divergenceIndex))
            + " predictedWindow=" + formatPathWindow(expectedPath, divergenceIndex)
            + " actualWindow=" + formatPathWindow(actualPath, divergenceIndex);
    }

    public static String formatPathWindow(List<WorldPoint> path, int centerIndex)
    {
        return formatPathWindow(path, centerIndex, DEFAULT_DIVERGENCE_WINDOW_RADIUS);
    }

    public static String formatPathWindow(List<WorldPoint> path, int centerIndex, int radius)
    {
        List<WorldPoint> points = path == null ? Collections.emptyList() : path;
        if (points.isEmpty())
        {
            return "[]";
        }

        int clampedCenter = Math.max(0, Math.min(centerIndex, points.size() - 1));
        int windowRadius = Math.max(0, radius);
        int start = Math.max(0, clampedCenter - windowRadius);
        int end = Math.min(points.size() - 1, clampedCenter + windowRadius);
        StringBuilder builder = new StringBuilder("[");
        for (int index = start; index <= end; index++)
        {
            if (index > start)
            {
                builder.append(" -> ");
            }
            builder.append(index).append(':').append(formatPoint(points.get(index)));
        }
        if (start > 0)
        {
            builder.append(" start=").append(start);
        }
        if (end < points.size() - 1)
        {
            builder.append(" end=").append(end).append(" total=").append(points.size());
        }
        return builder.append(']').toString();
    }

    public static String formatMoveCandidates(
        List<DrewsHelperWalkingRouteEngine.MoveCandidate> candidates,
        WorldPoint predicted,
        WorldPoint actual
    )
    {
        List<DrewsHelperWalkingRouteEngine.MoveCandidate> moveCandidates =
            candidates == null ? Collections.emptyList() : candidates;
        if (moveCandidates.isEmpty())
        {
            return "[]";
        }

        boolean predictedListed = predicted == null;
        boolean actualListed = actual == null;
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < moveCandidates.size(); index++)
        {
            DrewsHelperWalkingRouteEngine.MoveCandidate candidate = moveCandidates.get(index);
            if (index > 0)
            {
                builder.append("; ");
            }

            boolean isPredicted = candidate.getDestination().equals(predicted);
            boolean isActual = candidate.getDestination().equals(actual);
            predictedListed |= isPredicted;
            actualListed |= isActual;
            builder.append(candidate.getOrder())
                .append(':')
                .append(formatPoint(candidate.getDestination()))
                .append(' ')
                .append(candidate.getMoveType())
                .append(" dist=")
                .append(candidate.getDistanceToTarget())
                .append(" pref=")
                .append(candidate.getPreferencePenalty());
            if (isPredicted)
            {
                builder.append(" predicted");
            }
            if (isActual)
            {
                builder.append(" actual");
            }
        }

        if (!predictedListed)
        {
            builder.append("; predicted=").append(formatPoint(predicted)).append(" notLegal");
        }
        if (!actualListed)
        {
            builder.append("; actual=").append(formatPoint(actual)).append(" notLegal");
        }
        return builder.append(']').toString();
    }

    public static WorldPoint pointAt(List<WorldPoint> path, int index)
    {
        if (path == null || index < 0 || index >= path.size())
        {
            return null;
        }

        return path.get(index);
    }

    private static int comparedMovementTicks(List<WorldPoint> actual, int limit)
    {
        return Math.min(limit, Math.max(0, actual.size() - 1));
    }

    private static String formatPreviousDirection(List<WorldPoint> path, int divergenceIndex)
    {
        WorldPoint from = pointAt(path, divergenceIndex - 2);
        WorldPoint to = pointAt(path, divergenceIndex - 1);
        if (from == null || to == null)
        {
            return "none";
        }

        Direction direction = Direction.between(from, to);
        if (direction.isStationary())
        {
            return "stationary";
        }
        return direction.name();
    }

    private static int prefixMatches(List<WorldPoint> expected, List<WorldPoint> actual, int limit)
    {
        int compared = comparedMovementTicks(actual, limit);
        int matches = 0;
        for (int movementIndex = 1; movementIndex <= compared; movementIndex++)
        {
            if (movementIndex < expected.size() && expected.get(movementIndex).equals(actual.get(movementIndex)))
            {
                matches++;
            }
        }
        return matches;
    }

    private static boolean firstStepDirectionMatches(List<WorldPoint> expected, List<WorldPoint> actual)
    {
        if (expected.size() < 2 || actual.size() < 2)
        {
            return false;
        }

        return Direction.between(expected.get(0), expected.get(1))
            .equals(Direction.between(actual.get(0), actual.get(1)));
    }

    private static int maxLateralDeviation(List<WorldPoint> expected, List<WorldPoint> actual)
    {
        if (expected.isEmpty() || actual.isEmpty())
        {
            return 0;
        }

        int maxDeviation = 0;
        for (WorldPoint actualPoint : actual)
        {
            int nearest = Integer.MAX_VALUE;
            for (WorldPoint expectedPoint : expected)
            {
                if (actualPoint.getPlane() != expectedPoint.getPlane())
                {
                    continue;
                }
                nearest = Math.min(nearest, tileDistance(actualPoint, expectedPoint));
            }

            if (nearest != Integer.MAX_VALUE)
            {
                maxDeviation = Math.max(maxDeviation, nearest);
            }
        }
        return maxDeviation;
    }

    private static int tileDistance(WorldPoint first, WorldPoint second)
    {
        if (first == null || second == null)
        {
            return 0;
        }

        if (first.getPlane() != second.getPlane())
        {
            return 1;
        }

        return Math.max(
            Math.abs(first.getX() - second.getX()),
            Math.abs(first.getY() - second.getY())
        );
    }

    public static final class Report
    {
        private final DrewsHelperRouteSolverMode solverMode;
        private final boolean firstStepDirectionMatches;
        private final int firstFiveMatches;
        private final int firstFiveCompared;
        private final int firstTenMatches;
        private final int firstTenCompared;
        private final boolean fullTileSequenceMatches;
        private final int expectedPathLength;
        private final int actualPathLength;
        private final int maxLateralDeviation;
        private final int expectedTurnCount;
        private final int actualTurnCount;

        private Report(
            DrewsHelperRouteSolverMode solverMode,
            boolean firstStepDirectionMatches,
            int firstFiveMatches,
            int firstFiveCompared,
            int firstTenMatches,
            int firstTenCompared,
            boolean fullTileSequenceMatches,
            int expectedPathLength,
            int actualPathLength,
            int maxLateralDeviation,
            int expectedTurnCount,
            int actualTurnCount
        )
        {
            this.solverMode = solverMode == null ? DrewsHelperRouteSolverMode.A_STAR : solverMode;
            this.firstStepDirectionMatches = firstStepDirectionMatches;
            this.firstFiveMatches = firstFiveMatches;
            this.firstFiveCompared = firstFiveCompared;
            this.firstTenMatches = firstTenMatches;
            this.firstTenCompared = firstTenCompared;
            this.fullTileSequenceMatches = fullTileSequenceMatches;
            this.expectedPathLength = expectedPathLength;
            this.actualPathLength = actualPathLength;
            this.maxLateralDeviation = maxLateralDeviation;
            this.expectedTurnCount = expectedTurnCount;
            this.actualTurnCount = actualTurnCount;
        }

        public DrewsHelperRouteSolverMode getSolverMode()
        {
            return solverMode;
        }

        public boolean isFirstStepDirectionMatches()
        {
            return firstStepDirectionMatches;
        }

        public int getFirstFiveMatches()
        {
            return firstFiveMatches;
        }

        public int getFirstFiveCompared()
        {
            return firstFiveCompared;
        }

        public int getFirstTenMatches()
        {
            return firstTenMatches;
        }

        public int getFirstTenCompared()
        {
            return firstTenCompared;
        }

        public boolean isFullTileSequenceMatches()
        {
            return fullTileSequenceMatches;
        }

        public int getExpectedPathLength()
        {
            return expectedPathLength;
        }

        public int getActualPathLength()
        {
            return actualPathLength;
        }

        public int getMaxLateralDeviation()
        {
            return maxLateralDeviation;
        }

        public int getExpectedTurnCount()
        {
            return expectedTurnCount;
        }

        public int getActualTurnCount()
        {
            return actualTurnCount;
        }

        public String summary()
        {
            return solverMode
                + " first=" + (firstStepDirectionMatches ? "match" : "miss")
                + " 5=" + firstFiveMatches + "/" + firstFiveCompared
                + " 10=" + firstTenMatches + "/" + firstTenCompared
                + " full=" + fullTileSequenceMatches
                + " lenDelta=" + (actualPathLength - expectedPathLength)
                + " maxDev=" + maxLateralDeviation
                + " turnDelta=" + (actualTurnCount - expectedTurnCount);
        }
    }

    private static final class Direction
    {
        private final int x;
        private final int y;
        private final int plane;

        private Direction(int x, int y, int plane)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }

        private static Direction between(WorldPoint from, WorldPoint to)
        {
            return new Direction(
                Integer.compare(to.getX(), from.getX()),
                Integer.compare(to.getY(), from.getY()),
                Integer.compare(to.getPlane(), from.getPlane())
            );
        }

        private boolean isStationary()
        {
            return x == 0 && y == 0 && plane == 0;
        }

        private String name()
        {
            if (plane != 0)
            {
                return "plane" + plane;
            }

            StringBuilder builder = new StringBuilder();
            if (y > 0)
            {
                builder.append('N');
            }
            else if (y < 0)
            {
                builder.append('S');
            }

            if (x > 0)
            {
                builder.append('E');
            }
            else if (x < 0)
            {
                builder.append('W');
            }

            return builder.length() == 0 ? "stationary" : builder.toString();
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof Direction))
            {
                return false;
            }

            Direction direction = (Direction) other;
            return x == direction.x && y == direction.y && plane == direction.plane;
        }

        @Override
        public int hashCode()
        {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + plane;
            return result;
        }
    }
}
