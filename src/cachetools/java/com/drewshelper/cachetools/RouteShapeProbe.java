package com.drewshelper.cachetools;

import com.drewshelper.routing.DrewsHelperCollisionMap;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteStatus;
import com.drewshelper.routing.DrewsHelperWalkingRouteEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.coords.WorldPoint;

/**
 * Searches route shapes for excess turns so diagonal rendering artifacts are not confused with
 * genuine route weaving.
 *
 * <p>Run it with {@code gradlew.bat probeRouteShape}. Optional args are search boxes in the form
 * {@code minX,minY,maxX,maxY,plane}.
 */
public final class RouteShapeProbe
{
    private static final int POINT_STRIDE = 10;
    private static final int MAX_POINTS_PER_BOX = 64;
    // Each candidate now runs CLIENT and SHAPE, so halve the old cap to keep runtime sane.
    private static final int MAX_PAIRS_PER_BOX = 300;
    private static final int MAX_TOTAL_PAIRS = 1800;
    private static final int TOP_OFFENDER_LIMIT = 15;
    private static final int CONTROL_LIMIT = 5;
    private static final int SHAPE_LONGER_EXAMPLE_LIMIT = 10;

    private RouteShapeProbe()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path project = Paths.get(System.getProperty("user.dir"));
        Path outFile = project.resolve("tools/route-shape-probe.txt");
        List<SearchBox> boxes = parseBoxes(args);

        DrewsHelperCollisionMap map = DrewsHelperCollisionMap.loadDefault();
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(map);
        ProbeResult result = probe(engine, boxes);
        String report = buildReport(boxes, result);

        Files.createDirectories(outFile.getParent());
        Files.write(outFile, report.getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
    }

    private static List<SearchBox> parseBoxes(String[] args)
    {
        if (args.length == 0)
        {
            List<SearchBox> defaults = new ArrayList<>();
            defaults.add(new SearchBox(3200, 3200, 3240, 3240, 2));
            defaults.add(new SearchBox(3220, 3210, 3260, 3240, 0));
            return defaults;
        }

        List<SearchBox> boxes = new ArrayList<>();
        for (String arg : args)
        {
            String[] parts = arg.split(",");
            if (parts.length != 5)
            {
                throw new IllegalArgumentException("Search box must be minX,minY,maxX,maxY,plane: " + arg);
            }

            int minX = parseInt(parts[0], arg);
            int minY = parseInt(parts[1], arg);
            int maxX = parseInt(parts[2], arg);
            int maxY = parseInt(parts[3], arg);
            int plane = parseInt(parts[4], arg);
            boxes.add(new SearchBox(minX, minY, maxX, maxY, plane));
        }
        return boxes;
    }

    private static int parseInt(String value, String source)
    {
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("Invalid integer in search box " + source + ": " + value, e);
        }
    }

    private static ProbeResult probe(DrewsHelperWalkingRouteEngine engine, List<SearchBox> boxes)
    {
        ProbeResult result = new ProbeResult();

        outer:
        for (SearchBox box : boxes)
        {
            BoxResult boxResult = new BoxResult(box);
            result.boxes.add(boxResult);
            List<CandidatePair> pairs = candidatePairs(box);
            boxResult.candidatePairs = pairs.size();

            for (CandidatePair pair : pairs)
            {
                if (result.attempted >= MAX_TOTAL_PAIRS)
                {
                    result.hitGlobalCap = true;
                    break outer;
                }

                result.attempted++;
                boxResult.attempted++;

                List<WorldPoint> destinations = Collections.singletonList(pair.end);
                DrewsHelperRouteSnapshot clientRoute;
                DrewsHelperRouteSnapshot shapeRoute;
                try
                {
                    clientRoute = engine.solve(pair.start, destinations);
                    shapeRoute = engine.solveWithShapeRankingWithoutLocalWalkingOverrides(pair.start, destinations);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    result.interrupted = true;
                    break outer;
                }

                boolean clientReady = clientRoute.getStatus() == DrewsHelperRouteStatus.READY;
                boolean shapeReady = shapeRoute.getStatus() == DrewsHelperRouteStatus.READY;
                if (!clientReady || !shapeReady)
                {
                    if (clientReady)
                    {
                        result.clientOnlySolved++;
                        boxResult.clientOnlySolved++;
                    }
                    else if (shapeReady)
                    {
                        result.shapeOnlySolved++;
                        boxResult.shapeOnlySolved++;
                    }
                    else
                    {
                        result.neitherSolved++;
                        boxResult.neitherSolved++;
                    }
                    continue;
                }

                RouteMeasurement clientMeasurement = measure(pair, clientRoute.getPath());
                RouteMeasurement shapeMeasurement = measure(pair, shapeRoute.getPath());
                boxResult.solved++;
                result.addMeasuredPair(clientMeasurement, shapeMeasurement);
            }
        }

        return result;
    }

    private static List<CandidatePair> candidatePairs(SearchBox box)
    {
        List<WorldPoint> points = sampledPoints(box);
        List<CandidatePair> pairs = new ArrayList<>();
        for (WorldPoint start : points)
        {
            for (WorldPoint end : points)
            {
                if (!start.equals(end))
                {
                    pairs.add(new CandidatePair(start, end));
                }
            }
        }

        pairs.sort(Comparator
            .comparingInt(CandidatePair::chebyshevDistance).reversed()
            .thenComparingInt(CandidatePair::diagonalMiss)
            .thenComparingInt(CandidatePair::startX)
            .thenComparingInt(CandidatePair::startY)
            .thenComparingInt(CandidatePair::endX)
            .thenComparingInt(CandidatePair::endY));

        if (pairs.size() > MAX_PAIRS_PER_BOX)
        {
            return new ArrayList<>(pairs.subList(0, MAX_PAIRS_PER_BOX));
        }
        return pairs;
    }

    private static List<WorldPoint> sampledPoints(SearchBox box)
    {
        List<Integer> xs = sampledAxis(box.minX, box.maxX);
        List<Integer> ys = sampledAxis(box.minY, box.maxY);
        List<WorldPoint> points = new ArrayList<>();
        for (int x : xs)
        {
            for (int y : ys)
            {
                points.add(new WorldPoint(x, y, box.plane));
            }
        }

        if (points.size() <= MAX_POINTS_PER_BOX)
        {
            return points;
        }

        List<WorldPoint> capped = new ArrayList<>();
        int stride = (points.size() + MAX_POINTS_PER_BOX - 1) / MAX_POINTS_PER_BOX;
        for (int index = 0; index < points.size() && capped.size() < MAX_POINTS_PER_BOX; index += stride)
        {
            capped.add(points.get(index));
        }

        WorldPoint last = points.get(points.size() - 1);
        if (!capped.contains(last))
        {
            if (capped.size() == MAX_POINTS_PER_BOX)
            {
                capped.set(capped.size() - 1, last);
            }
            else
            {
                capped.add(last);
            }
        }
        return capped;
    }

    private static List<Integer> sampledAxis(int min, int max)
    {
        if (min > max)
        {
            throw new IllegalArgumentException("Search box minimum exceeds maximum: " + min + " > " + max);
        }

        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value += POINT_STRIDE)
        {
            values.add(value);
        }
        if (values.get(values.size() - 1) != max)
        {
            values.add(max);
        }
        return values;
    }

    private static RouteMeasurement measure(CandidatePair pair, List<WorldPoint> path)
    {
        List<StepDelta> deltas = stepDeltas(path);
        int actualDirectionChanges = actualDirectionChanges(deltas);
        int minimumDirectionChanges = minimumDirectionChanges(pair.start, pair.end);
        int excessTurns = actualDirectionChanges - minimumDirectionChanges;
        int longestAlternationRun = longestAlternationRun(deltas);
        int longestPureDiagonalRun = longestPureDiagonalRun(deltas);

        return new RouteMeasurement(
            pair,
            deltas.size(),
            actualDirectionChanges,
            minimumDirectionChanges,
            excessTurns,
            longestAlternationRun,
            longestPureDiagonalRun,
            formatDeltas(deltas)
        );
    }

    private static List<StepDelta> stepDeltas(List<WorldPoint> path)
    {
        if (path == null || path.size() < 2)
        {
            return Collections.emptyList();
        }

        List<StepDelta> deltas = new ArrayList<>();
        for (int index = 1; index < path.size(); index++)
        {
            WorldPoint previous = path.get(index - 1);
            WorldPoint current = path.get(index);
            deltas.add(new StepDelta(
                current.getX() - previous.getX(),
                current.getY() - previous.getY()
            ));
        }
        return deltas;
    }

    private static int actualDirectionChanges(List<StepDelta> deltas)
    {
        int changes = 0;
        for (int index = 1; index < deltas.size(); index++)
        {
            if (!deltas.get(index).sameDirection(deltas.get(index - 1)))
            {
                changes++;
            }
        }
        return changes;
    }

    private static int minimumDirectionChanges(WorldPoint start, WorldPoint end)
    {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        if (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy))
        {
            return 0;
        }
        return 1;
    }

    private static int longestAlternationRun(List<StepDelta> deltas)
    {
        int longest = 0;
        int current = 0;
        for (int index = 1; index < deltas.size(); index++)
        {
            if (isAlternation(deltas.get(index - 1), deltas.get(index)))
            {
                current++;
                longest = Math.max(longest, current);
            }
            else
            {
                current = 0;
            }
        }
        return longest;
    }

    private static boolean isAlternation(StepDelta previous, StepDelta current)
    {
        // The N -> NE -> N example toggles one axis while the other keeps its heading.
        boolean xChanged = previous.dx != current.dx;
        boolean yChanged = previous.dy != current.dy;
        return xChanged != yChanged;
    }

    private static int longestPureDiagonalRun(List<StepDelta> deltas)
    {
        int longest = 0;
        int current = 0;
        for (StepDelta delta : deltas)
        {
            if (delta.dx != 0 && Math.abs(delta.dx) == Math.abs(delta.dy))
            {
                current++;
                longest = Math.max(longest, current);
            }
            else
            {
                current = 0;
            }
        }
        return longest;
    }

    private static String formatDeltas(List<StepDelta> deltas)
    {
        if (deltas.isEmpty())
        {
            return "(none)";
        }

        StringBuilder text = new StringBuilder();
        for (StepDelta delta : deltas)
        {
            if (text.length() > 0)
            {
                text.append(' ');
            }
            text.append(delta.dx).append('/').append(delta.dy);
        }
        return text.toString();
    }

    private static String buildReport(List<SearchBox> boxes, ProbeResult result)
    {
        StringBuilder report = new StringBuilder();
        report.append("Route shape probe").append('\n');
        report.append("boxes: ").append(formatBoxes(boxes)).append('\n');
        report.append("pointStride: ").append(POINT_STRIDE)
            .append(" maxPairsPerBox: ").append(MAX_PAIRS_PER_BOX)
            .append(" maxTotalPairs: ").append(MAX_TOTAL_PAIRS).append('\n');
        report.append("Caveat: SHAPE uses solveWithShapeRankingWithoutLocalWalkingOverrides, which also disables local walking overrides; this comparison varies shape ranking and local-walking override handling together.")
            .append('\n');
        if (result.hitGlobalCap)
        {
            report.append("globalPairCapHit: true").append('\n');
        }
        if (result.interrupted)
        {
            report.append("interrupted: true").append('\n');
        }
        report.append('\n');

        report.append("Box counts").append('\n');
        for (BoxResult boxResult : result.boxes)
        {
            report.append("  ").append(boxResult.box.raw())
                .append(" candidatePairs=").append(boxResult.candidatePairs)
                .append(" attempted=").append(boxResult.attempted)
                .append(" solved=").append(boxResult.solved)
                .append(" clientOnlySolved=").append(boxResult.clientOnlySolved)
                .append(" shapeOnlySolved=").append(boxResult.shapeOnlySolved)
                .append(" neitherSolved=").append(boxResult.neitherSolved).append('\n');
        }
        report.append('\n');

        report.append("Totals").append('\n');
        report.append("  pairsAttempted: ").append(result.attempted).append('\n');
        report.append("  pairsSolved: ").append(result.solved).append('\n');
        report.append("  pairsMeasured: ").append(result.solved).append('\n');
        report.append("  pairsDroppedOnlyOneSolved: ").append(result.onlyOneSolved()).append('\n');
        report.append("  clientOnlySolved: ").append(result.clientOnlySolved).append('\n');
        report.append("  shapeOnlySolved: ").append(result.shapeOnlySolved).append('\n');
        report.append("  neitherSolved: ").append(result.neitherSolved).append('\n');
        report.append('\n');

        report.append("Excess turns distributions").append('\n');
        report.append("  excessTurnsDistribution: CLIENT ")
            .append(formatDistribution(result.excessDistribution))
            .append(" | SHAPE ")
            .append(formatDistribution(result.shapeExcessDistribution)).append('\n');
        report.append('\n');

        appendModeAggregates(report, result);
        report.append('\n');

        appendPairComparison(report, result);
        report.append('\n');

        appendStepCountCheck(report, result);
        report.append('\n');

        report.append("Top ").append(TOP_OFFENDER_LIMIT).append(" offenders by CLIENT excessTurns").append('\n');
        List<RouteMeasurement> offenders = sortedOffenders(result.measurements);
        if (offenderSortBroken(offenders, result.measurements))
        {
            appendOffenderSortBroken(report, offenders, result.measurements);
        }
        else
        {
            appendClientOffenderComparisonList(report, result.routePairs, offenders, TOP_OFFENDER_LIMIT);
        }
        report.append('\n');

        report.append("Top ").append(CONTROL_LIMIT)
            .append(" CLIENT zero-excess routes by pure diagonal run").append('\n');
        List<RouteMeasurement> controls = sortedControls(result.measurements);
        appendRouteList(report, controls, CONTROL_LIMIT, true);
        report.append('\n');

        report.append(summaryLine(result)).append('\n');
        return report.toString();
    }

    private static void appendModeAggregates(StringBuilder report, ProbeResult result)
    {
        report.append("Mode aggregates").append('\n');
        appendModeAggregateLine(report, "CLIENT", excessStats(result.measurements));
        appendModeAggregateLine(report, "SHAPE", excessStats(result.shapeMeasurements));
    }

    private static void appendModeAggregateLine(StringBuilder report, String mode, ExcessStats stats)
    {
        report.append("  ").append(mode)
            .append(" pairsMeasured=").append(stats.pairsMeasured)
            .append(" meanExcessTurns=").append(formatDecimal(stats.meanExcessTurns))
            .append(" medianExcessTurns=").append(formatDecimal(stats.medianExcessTurns))
            .append(" countExcessTurnsEq0=").append(stats.zeroExcessTurns)
            .append(" countExcessTurnsGt0=").append(stats.positiveExcessTurns)
            .append(" maxExcessTurns=").append(stats.maxExcessTurns).append('\n');
    }

    private static void appendPairComparison(StringBuilder report, ProbeResult result)
    {
        report.append("Per-pair comparison").append('\n');
        report.append("  SHAPE worsened: ").append(result.shapeWorsened).append('\n');
        report.append("  SHAPE improved: ").append(result.shapeImproved).append('\n');
        report.append("  unchanged: ").append(result.shapeUnchanged).append('\n');
    }

    private static void appendStepCountCheck(StringBuilder report, ProbeResult result)
    {
        report.append("Step-count check").append('\n');
        report.append("  correctnessGate: SHAPE must not make routes longer").append('\n');
        report.append("  differentStepCounts: ").append(result.differentStepCounts).append('\n');
        report.append("  SHAPE-LONGER count: ").append(result.shapeLonger).append('\n');
        for (RoutePairMeasurement routePair : result.shapeLongerExamples)
        {
            report.append("  SHAPE LONGER start=").append(formatPoint(routePair.client.pair.start))
                .append(" end=").append(formatPoint(routePair.client.pair.end))
                .append(" displacement=").append(routePair.client.displacement())
                .append(" clientSteps=").append(routePair.client.stepCount)
                .append(" shapeSteps=").append(routePair.shape.stepCount).append('\n');
        }
    }

    private static List<RouteMeasurement> sortedOffenders(List<RouteMeasurement> measurements)
    {
        List<RouteMeasurement> sorted = new ArrayList<>(measurements);
        // Reverse each descending key independently; chaining .reversed() flips the whole prefix.
        sorted.sort(Comparator
            .comparingInt(RouteMeasurement::excessTurnsValue).reversed()
            .thenComparing(Comparator.comparingInt(RouteMeasurement::longestAlternationRunValue).reversed())
            .thenComparing(Comparator.comparingInt(RouteMeasurement::actualDirectionChangesValue).reversed())
            .thenComparing(Comparator.comparingInt(RouteMeasurement::stepCountValue).reversed())
            .thenComparingInt(RouteMeasurement::startX)
            .thenComparingInt(RouteMeasurement::startY)
            .thenComparingInt(RouteMeasurement::endX)
            .thenComparingInt(RouteMeasurement::endY));
        return sorted;
    }

    private static List<RouteMeasurement> sortedControls(List<RouteMeasurement> measurements)
    {
        List<RouteMeasurement> controls = new ArrayList<>();
        for (RouteMeasurement measurement : measurements)
        {
            if (measurement.excessTurns == 0 && measurement.longestPureDiagonalRun > 0)
            {
                controls.add(measurement);
            }
        }

        // Reverse each descending key independently; chaining .reversed() flips the whole prefix.
        controls.sort(Comparator
            .comparingInt(RouteMeasurement::longestPureDiagonalRunValue).reversed()
            .thenComparing(Comparator.comparingInt(RouteMeasurement::stepCountValue).reversed())
            .thenComparingInt(RouteMeasurement::startX)
            .thenComparingInt(RouteMeasurement::startY)
            .thenComparingInt(RouteMeasurement::endX)
            .thenComparingInt(RouteMeasurement::endY));
        return controls;
    }

    private static boolean offenderSortBroken(
        List<RouteMeasurement> offenders,
        List<RouteMeasurement> measurements
    )
    {
        if (offenders.isEmpty())
        {
            return false;
        }

        int firstExcessTurns = offenders.get(0).excessTurns;
        int lastExcessTurns = offenders.get(offenders.size() - 1).excessTurns;
        int maxExcessTurns = maxExcessTurns(measurements);
        return firstExcessTurns < lastExcessTurns || firstExcessTurns != maxExcessTurns;
    }

    private static void appendOffenderSortBroken(
        StringBuilder report,
        List<RouteMeasurement> offenders,
        List<RouteMeasurement> measurements
    )
    {
        int firstExcessTurns = offenders.isEmpty() ? 0 : offenders.get(0).excessTurns;
        int lastExcessTurns = offenders.isEmpty() ? 0 : offenders.get(offenders.size() - 1).excessTurns;
        report.append("  OFFENDER SORT BROKEN firstExcessTurns=").append(firstExcessTurns)
            .append(" lastExcessTurns=").append(lastExcessTurns)
            .append(" maxExcessTurns=").append(maxExcessTurns(measurements))
            .append(" measurements=").append(measurements.size()).append('\n');
    }

    private static int maxExcessTurns(List<RouteMeasurement> measurements)
    {
        int maxExcessTurns = Integer.MIN_VALUE;
        for (RouteMeasurement measurement : measurements)
        {
            maxExcessTurns = Math.max(maxExcessTurns, measurement.excessTurns);
        }
        return measurements.isEmpty() ? 0 : maxExcessTurns;
    }

    private static void appendClientOffenderComparisonList(
        StringBuilder report,
        List<RoutePairMeasurement> routePairs,
        List<RouteMeasurement> clientRoutes,
        int limit
    )
    {
        if (clientRoutes.isEmpty())
        {
            report.append("  (none)").append('\n');
            return;
        }

        int count = Math.min(limit, clientRoutes.size());
        for (int index = 0; index < count; index++)
        {
            RouteMeasurement clientRoute = clientRoutes.get(index);
            RoutePairMeasurement routePair = routePairForClient(routePairs, clientRoute);
            report.append("  ").append(index + 1).append(". ")
                .append("start=").append(formatPoint(clientRoute.pair.start))
                .append(" end=").append(formatPoint(clientRoute.pair.end))
                .append(" displacement=").append(clientRoute.displacement())
                .append(" clientSteps=").append(routePair.client.stepCount)
                .append(" shapeSteps=").append(routePair.shape.stepCount)
                .append(" clientExcessTurns=").append(routePair.client.excessTurns)
                .append(" shapeExcessTurns=").append(routePair.shape.excessTurns)
                .append(" clientActualDirectionChanges=").append(routePair.client.actualDirectionChanges)
                .append(" shapeActualDirectionChanges=").append(routePair.shape.actualDirectionChanges)
                .append('\n');
            report.append("     clientDeltas=").append(routePair.client.deltas).append('\n');
            report.append("     shapeDeltas=").append(routePair.shape.deltas).append('\n');
        }
    }

    private static RoutePairMeasurement routePairForClient(
        List<RoutePairMeasurement> routePairs,
        RouteMeasurement clientRoute
    )
    {
        for (RoutePairMeasurement routePair : routePairs)
        {
            if (routePair.client == clientRoute)
            {
                return routePair;
            }
        }
        throw new IllegalStateException("Missing SHAPE measurement for CLIENT route");
    }

    private static void appendRouteList(
        StringBuilder report,
        List<RouteMeasurement> routes,
        int limit,
        boolean includePureDiagonalRun
    )
    {
        if (routes.isEmpty())
        {
            report.append("  (none)").append('\n');
            return;
        }

        int count = Math.min(limit, routes.size());
        for (int index = 0; index < count; index++)
        {
            RouteMeasurement route = routes.get(index);
            report.append("  ").append(index + 1).append(". ")
                .append("start=").append(formatPoint(route.pair.start))
                .append(" end=").append(formatPoint(route.pair.end))
                .append(" displacement=").append(route.displacement())
                .append(" steps=").append(route.stepCount)
                .append(" actualDirectionChanges=").append(route.actualDirectionChanges)
                .append(" minimumDirectionChanges=").append(route.minimumDirectionChanges)
                .append(" excessTurns=").append(route.excessTurns)
                .append(" longestAlternationRun=").append(route.longestAlternationRun);
            if (includePureDiagonalRun)
            {
                report.append(" longestPureDiagonalRun=").append(route.longestPureDiagonalRun);
            }
            report.append('\n');
            report.append("     deltas=").append(route.deltas).append('\n');
        }
    }

    private static String formatBoxes(List<SearchBox> boxes)
    {
        List<String> raw = new ArrayList<>();
        for (SearchBox box : boxes)
        {
            raw.add(box.raw());
        }
        return String.join(" ", raw);
    }

    private static String formatDistribution(Map<Integer, Integer> distribution)
    {
        if (distribution.isEmpty())
        {
            return "(none)";
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet())
        {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", parts);
    }

    private static ExcessStats excessStats(List<RouteMeasurement> measurements)
    {
        if (measurements.isEmpty())
        {
            return new ExcessStats(0, 0.0, 0.0, 0, 0, 0);
        }

        int total = 0;
        int zeroExcessTurns = 0;
        int positiveExcessTurns = 0;
        int maxExcessTurns = Integer.MIN_VALUE;
        List<Integer> values = new ArrayList<>();
        for (RouteMeasurement measurement : measurements)
        {
            int excessTurns = measurement.excessTurns;
            values.add(excessTurns);
            total += excessTurns;
            if (excessTurns == 0)
            {
                zeroExcessTurns++;
            }
            if (excessTurns > 0)
            {
                positiveExcessTurns++;
            }
            maxExcessTurns = Math.max(maxExcessTurns, excessTurns);
        }
        Collections.sort(values);

        int middle = values.size() / 2;
        double medianExcessTurns;
        if (values.size() % 2 == 0)
        {
            medianExcessTurns = (values.get(middle - 1) + values.get(middle)) / 2.0;
        }
        else
        {
            medianExcessTurns = values.get(middle);
        }

        return new ExcessStats(
            measurements.size(),
            (double) total / measurements.size(),
            medianExcessTurns,
            zeroExcessTurns,
            positiveExcessTurns,
            maxExcessTurns
        );
    }

    private static String formatDecimal(double value)
    {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String summaryLine(ProbeResult result)
    {
        if (result.solved == 0)
        {
            return "Summary: No sampled routes solved, so this run does not distinguish routing zigzags from diagonal drawing artifacts.";
        }

        int positiveExcess = 0;
        int zeroExcessDiagonal = 0;
        int maxExcess = Integer.MIN_VALUE;
        int maxZeroExcessDiagonalRun = 0;
        for (RouteMeasurement measurement : result.measurements)
        {
            if (measurement.excessTurns > 0)
            {
                positiveExcess++;
            }
            if (measurement.excessTurns == 0 && measurement.longestPureDiagonalRun > 0)
            {
                zeroExcessDiagonal++;
                maxZeroExcessDiagonalRun = Math.max(maxZeroExcessDiagonalRun, measurement.longestPureDiagonalRun);
            }
            maxExcess = Math.max(maxExcess, measurement.excessTurns);
        }

        String dominant;
        if (positiveExcess > zeroExcessDiagonal)
        {
            dominant = "excess-turn routes dominate";
        }
        else if (zeroExcessDiagonal > positiveExcess)
        {
            dominant = "zero-excess diagonal runs dominate";
        }
        else
        {
            dominant = "neither case dominates";
        }

        return String.format(Locale.ROOT,
            "Summary: In the measured CLIENT sample, %s: %d routes had excessTurns > 0, %d routes had excessTurns == 0 with a pure diagonal run, maxExcessTurns=%d, maxZeroExcessPureDiagonalRun=%d.",
            dominant,
            positiveExcess,
            zeroExcessDiagonal,
            maxExcess,
            maxZeroExcessDiagonalRun);
    }

    private static String formatPoint(WorldPoint point)
    {
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private static final class SearchBox
    {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;
        private final int plane;

        private SearchBox(int minX, int minY, int maxX, int maxY, int plane)
        {
            if (minX > maxX || minY > maxY)
            {
                throw new IllegalArgumentException("Invalid search box bounds: "
                    + minX + "," + minY + "," + maxX + "," + maxY + "," + plane);
            }

            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.plane = plane;
        }

        private String raw()
        {
            return minX + "," + minY + "," + maxX + "," + maxY + "," + plane;
        }
    }

    private static final class CandidatePair
    {
        private final WorldPoint start;
        private final WorldPoint end;

        private CandidatePair(WorldPoint start, WorldPoint end)
        {
            this.start = start;
            this.end = end;
        }

        private int chebyshevDistance()
        {
            return Math.max(Math.abs(end.getX() - start.getX()), Math.abs(end.getY() - start.getY()));
        }

        private int diagonalMiss()
        {
            return Math.abs(Math.abs(end.getX() - start.getX()) - Math.abs(end.getY() - start.getY()));
        }

        private int startX()
        {
            return start.getX();
        }

        private int startY()
        {
            return start.getY();
        }

        private int endX()
        {
            return end.getX();
        }

        private int endY()
        {
            return end.getY();
        }
    }

    private static final class StepDelta
    {
        private final int dx;
        private final int dy;

        private StepDelta(int dx, int dy)
        {
            this.dx = dx;
            this.dy = dy;
        }

        private boolean sameDirection(StepDelta other)
        {
            return dx == other.dx && dy == other.dy;
        }
    }

    private static final class RouteMeasurement
    {
        private final CandidatePair pair;
        private final int stepCount;
        private final int actualDirectionChanges;
        private final int minimumDirectionChanges;
        private final int excessTurns;
        private final int longestAlternationRun;
        private final int longestPureDiagonalRun;
        private final String deltas;

        private RouteMeasurement(
            CandidatePair pair,
            int stepCount,
            int actualDirectionChanges,
            int minimumDirectionChanges,
            int excessTurns,
            int longestAlternationRun,
            int longestPureDiagonalRun,
            String deltas
        )
        {
            this.pair = pair;
            this.stepCount = stepCount;
            this.actualDirectionChanges = actualDirectionChanges;
            this.minimumDirectionChanges = minimumDirectionChanges;
            this.excessTurns = excessTurns;
            this.longestAlternationRun = longestAlternationRun;
            this.longestPureDiagonalRun = longestPureDiagonalRun;
            this.deltas = deltas;
        }

        private String displacement()
        {
            return (pair.end.getX() - pair.start.getX()) + "/" + (pair.end.getY() - pair.start.getY());
        }

        private int excessTurnsValue()
        {
            return excessTurns;
        }

        private int longestAlternationRunValue()
        {
            return longestAlternationRun;
        }

        private int longestPureDiagonalRunValue()
        {
            return longestPureDiagonalRun;
        }

        private int actualDirectionChangesValue()
        {
            return actualDirectionChanges;
        }

        private int stepCountValue()
        {
            return stepCount;
        }

        private int startX()
        {
            return pair.start.getX();
        }

        private int startY()
        {
            return pair.start.getY();
        }

        private int endX()
        {
            return pair.end.getX();
        }

        private int endY()
        {
            return pair.end.getY();
        }
    }

    private static final class RoutePairMeasurement
    {
        private final RouteMeasurement client;
        private final RouteMeasurement shape;

        private RoutePairMeasurement(RouteMeasurement client, RouteMeasurement shape)
        {
            this.client = client;
            this.shape = shape;
        }
    }

    private static final class ExcessStats
    {
        private final int pairsMeasured;
        private final double meanExcessTurns;
        private final double medianExcessTurns;
        private final int zeroExcessTurns;
        private final int positiveExcessTurns;
        private final int maxExcessTurns;

        private ExcessStats(
            int pairsMeasured,
            double meanExcessTurns,
            double medianExcessTurns,
            int zeroExcessTurns,
            int positiveExcessTurns,
            int maxExcessTurns
        )
        {
            this.pairsMeasured = pairsMeasured;
            this.meanExcessTurns = meanExcessTurns;
            this.medianExcessTurns = medianExcessTurns;
            this.zeroExcessTurns = zeroExcessTurns;
            this.positiveExcessTurns = positiveExcessTurns;
            this.maxExcessTurns = maxExcessTurns;
        }
    }

    private static final class BoxResult
    {
        private final SearchBox box;
        private int candidatePairs;
        private int attempted;
        private int solved;
        private int clientOnlySolved;
        private int shapeOnlySolved;
        private int neitherSolved;

        private BoxResult(SearchBox box)
        {
            this.box = box;
        }
    }

    private static final class ProbeResult
    {
        private final List<BoxResult> boxes = new ArrayList<>();
        private final List<RouteMeasurement> measurements = new ArrayList<>();
        private final List<RouteMeasurement> shapeMeasurements = new ArrayList<>();
        private final List<RoutePairMeasurement> routePairs = new ArrayList<>();
        private final List<RoutePairMeasurement> shapeLongerExamples = new ArrayList<>();
        private final Map<Integer, Integer> excessDistribution = new TreeMap<>();
        private final Map<Integer, Integer> shapeExcessDistribution = new TreeMap<>();
        private int attempted;
        private int solved;
        private int clientOnlySolved;
        private int shapeOnlySolved;
        private int neitherSolved;
        private int shapeImproved;
        private int shapeWorsened;
        private int shapeUnchanged;
        private int differentStepCounts;
        private int shapeLonger;
        private boolean hitGlobalCap;
        private boolean interrupted;

        private void addMeasuredPair(RouteMeasurement clientMeasurement, RouteMeasurement shapeMeasurement)
        {
            RoutePairMeasurement routePair = new RoutePairMeasurement(clientMeasurement, shapeMeasurement);
            solved++;
            measurements.add(clientMeasurement);
            shapeMeasurements.add(shapeMeasurement);
            routePairs.add(routePair);
            excessDistribution.merge(clientMeasurement.excessTurns, 1, Integer::sum);
            shapeExcessDistribution.merge(shapeMeasurement.excessTurns, 1, Integer::sum);

            if (shapeMeasurement.excessTurns < clientMeasurement.excessTurns)
            {
                shapeImproved++;
            }
            else if (shapeMeasurement.excessTurns > clientMeasurement.excessTurns)
            {
                shapeWorsened++;
            }
            else
            {
                shapeUnchanged++;
            }

            if (shapeMeasurement.stepCount != clientMeasurement.stepCount)
            {
                differentStepCounts++;
                if (shapeMeasurement.stepCount > clientMeasurement.stepCount)
                {
                    shapeLonger++;
                    if (shapeLongerExamples.size() < SHAPE_LONGER_EXAMPLE_LIMIT)
                    {
                        shapeLongerExamples.add(routePair);
                    }
                }
            }
        }

        private int onlyOneSolved()
        {
            return clientOnlySolved + shapeOnlySolved;
        }
    }
}
