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

    public static Report compare(List<WorldPoint> expectedPath, List<WorldPoint> actualPath)
    {
        List<WorldPoint> expected = expectedPath == null ? Collections.emptyList() : expectedPath;
        List<WorldPoint> actual = actualPath == null ? Collections.emptyList() : actualPath;
        return new Report(
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

        DivergenceFit fit = divergenceFit(expectedPath, actualPath, actualComplete);
        return "idx=" + divergenceIndex
            + " prevDir=" + formatPreviousDirection(actualPath, divergenceIndex)
            + " predicted=" + formatPoint(pointAt(expectedPath, divergenceIndex))
            + " actual=" + formatPoint(pointAt(actualPath, divergenceIndex))
            + " mergeBack={" + fit.mergeBackSummary() + "}"
            + " classification=" + fit.getClassification()
            + " benign=" + fit.isBenign()
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

    public static String formatObservedEdgeDiagnostic(
        DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic,
        int repeatCount,
        int overrideCandidateRepeatThreshold
    )
    {
        if (diagnostic == null)
        {
            return "none";
        }

        int normalizedRepeatCount = Math.max(0, repeatCount);
        int threshold = Math.max(1, overrideCandidateRepeatThreshold);
        if (!diagnostic.isAvailable())
        {
            return "status=unavailable"
                + " reason=" + diagnostic.getReason()
                + " repeat=" + normalizedRepeatCount
                + " overrideCandidate=false";
        }

        boolean overrideCandidate = normalizedRepeatCount >= threshold
            && (!diagnostic.isEdgeLegal() || diagnostic.isContinuationLonger());
        StringBuilder builder = new StringBuilder();
        builder.append("from=")
            .append(formatPoint(diagnostic.getFrom()))
            .append(" actual=")
            .append(formatPoint(diagnostic.getObserved()))
            .append(" target=")
            .append(formatPoint(diagnostic.getTarget()))
            .append(" legal=")
            .append(diagnostic.isEdgeLegal())
            .append(" type=")
            .append(diagnostic.getEdgeType())
            .append(" continuation=")
            .append(diagnostic.getReason());

        if (diagnostic.isContinuationFound())
        {
            builder.append(" continuationDist=")
                .append(diagnostic.getContinuationDistance())
                .append(" totalFromFork=")
                .append(diagnostic.getTotalRemainingFromFork())
                .append(" expectedFromFork=")
                .append(diagnostic.getExpectedRemainingFromFork())
                .append(" delta=")
                .append(diagnostic.getContinuationDelta())
                .append(" longer=")
                .append(diagnostic.isContinuationLonger());
        }

        return builder.append(" expanded=")
            .append(diagnostic.getExpandedNodes())
            .append(" repeat=")
            .append(normalizedRepeatCount)
            .append(" overrideCandidate=")
            .append(overrideCandidate)
            .toString();
    }

    public static String formatShapeDiagnostic(
        List<WorldPoint> expectedPath,
        List<WorldPoint> actualPath,
        boolean actualReachedTarget
    )
    {
        if (!actualReachedTarget)
        {
            return "pending";
        }

        ShapeStats expected = shapeStats(expectedPath);
        ShapeStats actual = shapeStats(actualPath);
        if (!expected.available || !actual.available)
        {
            return "status=unavailable";
        }

        int lineErrorDelta = actual.lineError - expected.lineError;
        return "expected={lineError=" + expected.lineError
            + " diag=" + expected.diagonalSteps
            + " card=" + expected.cardinalSteps
            + " turns=" + expected.turns
            + "}"
            + " actual={lineError=" + actual.lineError
            + " diag=" + actual.diagonalSteps
            + " card=" + actual.cardinalSteps
            + " turns=" + actual.turns
            + "}"
            + " lineErrorDelta=" + lineErrorDelta
            + " winner=" + shapeWinner(lineErrorDelta);
    }

    public static String formatShadowRouteDiagnostic(
        List<WorldPoint> visiblePath,
        List<WorldPoint> shadowPath,
        List<WorldPoint> actualPath,
        boolean actualComplete
    )
    {
        if (!actualComplete)
        {
            return "pending";
        }

        List<WorldPoint> visible = visiblePath == null ? Collections.emptyList() : visiblePath;
        List<WorldPoint> shadow = shadowPath == null ? Collections.emptyList() : shadowPath;
        List<WorldPoint> actual = actualPath == null ? Collections.emptyList() : actualPath;
        if (shadow.isEmpty())
        {
            return "status=not-found";
        }

        Report shadowReport = compare(shadow, actual);
        boolean overridesMatter = !visible.equals(shadow);
        return "status=ready"
            + " overridesMatter=" + overridesMatter
            + " route={" + shadowReport.summary() + "}"
            + " visibleVsShadow={" + formatDivergence(visible, shadow, true) + "}"
            + " shadowVsActual={" + formatDivergence(shadow, actual, true) + "}"
            + " fit={visible=" + divergenceFit(visible, actual, true).getClassification()
            + " shadow=" + divergenceFit(shadow, actual, true).getClassification() + "}"
            + " winner=" + shadowWinner(visible, shadow, actual);
    }

    public static String formatShapeShadowRouteDiagnostic(
        List<WorldPoint> visiblePath,
        List<WorldPoint> shapeShadowPath,
        List<WorldPoint> actualPath,
        boolean actualComplete
    )
    {
        if (!actualComplete)
        {
            return "pending";
        }

        List<WorldPoint> visible = visiblePath == null ? Collections.emptyList() : visiblePath;
        List<WorldPoint> shapeShadow = shapeShadowPath == null ? Collections.emptyList() : shapeShadowPath;
        List<WorldPoint> actual = actualPath == null ? Collections.emptyList() : actualPath;
        if (shapeShadow.isEmpty())
        {
            return "status=not-found";
        }

        Report shapeShadowReport = compare(shapeShadow, actual);
        boolean differsFromVisible = !visible.equals(shapeShadow);
        return "status=ready"
            + " differsFromVisible=" + differsFromVisible
            + " route={" + shapeShadowReport.summary() + "}"
            + " visibleVsShapeShadow={" + formatDivergence(visible, shapeShadow, true) + "}"
            + " shapeShadowVsActual={" + formatDivergence(shapeShadow, actual, true) + "}"
            + " fit={visible=" + divergenceFit(visible, actual, true).getClassification()
            + " shapeShadow=" + divergenceFit(shapeShadow, actual, true).getClassification() + "}"
            + " winner=" + shapeShadowWinner(visible, shapeShadow, actual);
    }

    public static WorldPoint pointAt(List<WorldPoint> path, int index)
    {
        if (path == null || index < 0 || index >= path.size())
        {
            return null;
        }

        return path.get(index);
    }

    private static DivergenceFit divergenceFit(
        List<WorldPoint> expectedPath,
        List<WorldPoint> actualPath,
        boolean actualComplete
    )
    {
        int divergenceIndex = firstDivergenceIndex(expectedPath, actualPath, actualComplete);
        if (divergenceIndex < 0)
        {
            return DivergenceFit.exact();
        }

        MergeBack mergeBack = findMergeBack(expectedPath, actualPath, divergenceIndex);
        if (!mergeBack.isFound())
        {
            return DivergenceFit.noMergeDrift();
        }

        if (mergeBack.getStepDelta() == 0)
        {
            return DivergenceFit.sameTimePermutation(mergeBack);
        }

        if (mergeBack.getStepDelta() < 0)
        {
            return DivergenceFit.earlyMerge(mergeBack);
        }

        return DivergenceFit.laggingMerge(mergeBack);
    }

    private static MergeBack findMergeBack(List<WorldPoint> expectedPath, List<WorldPoint> actualPath, int divergenceIndex)
    {
        List<WorldPoint> expected = expectedPath == null ? Collections.emptyList() : expectedPath;
        List<WorldPoint> actual = actualPath == null ? Collections.emptyList() : actualPath;
        if (divergenceIndex < 0 || expected.isEmpty() || actual.isEmpty())
        {
            return MergeBack.none();
        }

        for (int actualIndex = divergenceIndex + 1; actualIndex < actual.size(); actualIndex++)
        {
            WorldPoint actualPoint = actual.get(actualIndex);
            for (int expectedIndex = divergenceIndex + 1; expectedIndex < expected.size(); expectedIndex++)
            {
                if (!actualPoint.equals(expected.get(expectedIndex)))
                {
                    continue;
                }

                return new MergeBack(expectedIndex, actualIndex, actualPoint);
            }
        }

        return MergeBack.none();
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

    private static ShapeStats shapeStats(List<WorldPoint> path)
    {
        List<WorldPoint> points = path == null ? Collections.emptyList() : path;
        if (points.size() < 2)
        {
            return ShapeStats.unavailable();
        }

        WorldPoint start = points.get(0);
        WorldPoint target = points.get(points.size() - 1);
        int totalX = target.getX() - start.getX();
        int totalY = target.getY() - start.getY();
        int majorAxis = Math.max(Math.abs(totalX), Math.abs(totalY));
        int lineError = 0;
        int diagonalSteps = 0;
        int cardinalSteps = 0;

        for (int index = 0; index < points.size(); index++)
        {
            WorldPoint point = points.get(index);
            int relativeX = point.getX() - start.getX();
            int relativeY = point.getY() - start.getY();
            if (majorAxis > 0)
            {
                int cross = Math.abs(relativeX * totalY - relativeY * totalX);
                lineError += cross / majorAxis;
            }

            if (index == 0)
            {
                continue;
            }

            WorldPoint previous = points.get(index - 1);
            int stepX = Math.abs(point.getX() - previous.getX());
            int stepY = Math.abs(point.getY() - previous.getY());
            if (stepX != 0 && stepY != 0)
            {
                diagonalSteps++;
            }
            else if (stepX != 0 || stepY != 0)
            {
                cardinalSteps++;
            }
        }

        return new ShapeStats(lineError, diagonalSteps, cardinalSteps, turnCount(points), true);
    }

    private static String shapeWinner(int lineErrorDelta)
    {
        if (lineErrorDelta < 0)
        {
            return "actual";
        }

        if (lineErrorDelta > 0)
        {
            return "expected";
        }

        return "tie";
    }

    private static String shadowWinner(
        List<WorldPoint> visiblePath,
        List<WorldPoint> shadowPath,
        List<WorldPoint> actualPath
    )
    {
        int visibleScore = routeFitScore(visiblePath, actualPath, true);
        int shadowScore = routeFitScore(shadowPath, actualPath, true);
        if (visibleScore < shadowScore)
        {
            return "visible";
        }

        if (shadowScore < visibleScore)
        {
            return "shadow";
        }

        return "tie";
    }

    private static String shapeShadowWinner(
        List<WorldPoint> visiblePath,
        List<WorldPoint> shapeShadowPath,
        List<WorldPoint> actualPath
    )
    {
        int visibleScore = routeFitScore(visiblePath, actualPath, true);
        int shapeShadowScore = routeFitScore(shapeShadowPath, actualPath, true);
        if (visibleScore < shapeShadowScore)
        {
            return "visible";
        }

        if (shapeShadowScore < visibleScore)
        {
            return "shapeShadow";
        }

        return "tie";
    }

    private static int routeFitScore(
        List<WorldPoint> expectedPath,
        List<WorldPoint> actualPath,
        boolean actualComplete
    )
    {
        Report report = compare(expectedPath, actualPath);
        DivergenceFit fit = divergenceFit(expectedPath, actualPath, actualComplete);
        int lengthDelta = Math.abs(report.getActualPathLength() - report.getExpectedPathLength());
        int firstTenMisses = report.getFirstTenCompared() - report.getFirstTenMatches();
        int turnDelta = Math.abs(report.getActualTurnCount() - report.getExpectedTurnCount());
        return fit.getSequencePenalty()
            + report.getMaxLateralDeviation() * 1_000
            + lengthDelta * 100
            + firstTenMisses * 10
            + turnDelta;
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
            return "first=" + (firstStepDirectionMatches ? "match" : "miss")
                + " 5=" + firstFiveMatches + "/" + firstFiveCompared
                + " 10=" + firstTenMatches + "/" + firstTenCompared
                + " full=" + fullTileSequenceMatches
                + " lenDelta=" + (actualPathLength - expectedPathLength)
                + " maxDev=" + maxLateralDeviation
                + " turnDelta=" + (actualTurnCount - expectedTurnCount);
        }
    }

    private static final class ShapeStats
    {
        private final int lineError;
        private final int diagonalSteps;
        private final int cardinalSteps;
        private final int turns;
        private final boolean available;

        private ShapeStats(int lineError, int diagonalSteps, int cardinalSteps, int turns, boolean available)
        {
            this.lineError = lineError;
            this.diagonalSteps = diagonalSteps;
            this.cardinalSteps = cardinalSteps;
            this.turns = turns;
            this.available = available;
        }

        private static ShapeStats unavailable()
        {
            return new ShapeStats(0, 0, 0, 0, false);
        }
    }

    private static final class MergeBack
    {
        private final boolean found;
        private final int expectedIndex;
        private final int actualIndex;
        private final WorldPoint point;

        private MergeBack(int expectedIndex, int actualIndex, WorldPoint point)
        {
            this.found = true;
            this.expectedIndex = expectedIndex;
            this.actualIndex = actualIndex;
            this.point = point;
        }

        private MergeBack()
        {
            this.found = false;
            this.expectedIndex = -1;
            this.actualIndex = -1;
            this.point = null;
        }

        private static MergeBack none()
        {
            return new MergeBack();
        }

        private boolean isFound()
        {
            return found;
        }

        private int getStepDelta()
        {
            return actualIndex - expectedIndex;
        }

        private String summary()
        {
            if (!found)
            {
                return "none";
            }

            return "expectedIdx=" + expectedIndex
                + " actualIdx=" + actualIndex
                + " stepDelta=" + getStepDelta()
                + " point=" + formatPoint(point);
        }
    }

    private static final class DivergenceFit
    {
        private final String classification;
        private final boolean benign;
        private final int sequencePenalty;
        private final MergeBack mergeBack;

        private DivergenceFit(String classification, boolean benign, int sequencePenalty, MergeBack mergeBack)
        {
            this.classification = classification;
            this.benign = benign;
            this.sequencePenalty = sequencePenalty;
            this.mergeBack = mergeBack;
        }

        private static DivergenceFit exact()
        {
            return new DivergenceFit("exact", true, 0, MergeBack.none());
        }

        private static DivergenceFit sameTimePermutation(MergeBack mergeBack)
        {
            return new DivergenceFit("sameTimePermutation", true, 100, mergeBack);
        }

        private static DivergenceFit earlyMerge(MergeBack mergeBack)
        {
            return new DivergenceFit("earlyMerge", false, 50_000, mergeBack);
        }

        private static DivergenceFit laggingMerge(MergeBack mergeBack)
        {
            return new DivergenceFit("laggingMerge", false, 100_000, mergeBack);
        }

        private static DivergenceFit noMergeDrift()
        {
            return new DivergenceFit("noMergeDrift", false, 1_000_000, MergeBack.none());
        }

        private String getClassification()
        {
            return classification;
        }

        private boolean isBenign()
        {
            return benign;
        }

        private int getSequencePenalty()
        {
            return sequencePenalty;
        }

        private String mergeBackSummary()
        {
            return mergeBack.summary();
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
