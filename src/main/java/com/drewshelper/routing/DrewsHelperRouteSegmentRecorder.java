package com.drewshelper.routing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Records each player-clicked walking segment against the route that was visible when the click
 * happened.
 *
 * <p>The full route benchmark is still useful for single-click repros, but Batch A showed that a
 * long trip is often several visible-tile clicks plus manual door/object choices. This recorder
 * keeps those segments separate so a long mismatch can be sorted into click choice, route ranking,
 * object profile, traversal state, or collision data without treating the whole trip as one blob.
 */
public final class DrewsHelperRouteSegmentRecorder
{
    private static final int ROUTE_ANCHOR_TOLERANCE = 2;
    private static final int SETTLE_TICKS = 2;
    private static final int MAX_SEGMENT_EXTRA_TICKS = 25;
    private static final int OBSERVED_EDGE_REPEAT_COUNT = 1;
    private static final int OBSERVED_EDGE_OVERRIDE_THRESHOLD = 2;

    private final File output;

    private SegmentCapture capture;
    private boolean writeWarned;

    public DrewsHelperRouteSegmentRecorder(File output)
    {
        this.output = output;
    }

    public List<String> onTick(
        WorldPoint playerTile,
        WorldPoint clickDestination,
        DrewsHelperRouteSnapshot snapshot,
        DrewsHelperWalkingRouteEngine routeEngine,
        int tick
    )
    {
        if (playerTile == null)
        {
            reset();
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        if (capture != null && clickDestination != null
            && !clickDestination.equals(capture.getClickDestination()))
        {
            addLine(lines, capture.finish("destination-changed", tick, routeEngine));
            capture = null;
        }

        if (capture != null)
        {
            String line = capture.record(playerTile, clickDestination, tick, routeEngine);
            if (line != null)
            {
                addLine(lines, line);
                capture = null;
            }
        }

        if (capture == null && canStart(playerTile, clickDestination, snapshot))
        {
            capture = SegmentCapture.start(
                playerTile,
                clickDestination,
                snapshot.getCurrentLegPath(),
                currentRouteTarget(snapshot),
                tick
            );
        }

        return lines;
    }

    public void reset()
    {
        capture = null;
    }

    private static boolean canStart(
        WorldPoint playerTile,
        WorldPoint clickDestination,
        DrewsHelperRouteSnapshot snapshot
    )
    {
        return clickDestination != null
            && !clickDestination.equals(playerTile)
            && snapshot != null
            && snapshot.getStatus() == DrewsHelperRouteStatus.READY
            && snapshot.hasPath();
    }

    private static WorldPoint currentRouteTarget(DrewsHelperRouteSnapshot snapshot)
    {
        if (snapshot == null)
        {
            return null;
        }

        List<WorldPoint> destinations = snapshot.getDestinations();
        if (destinations != null && !destinations.isEmpty())
        {
            return destinations.get(0);
        }

        List<WorldPoint> path = snapshot.getCurrentLegPath();
        return path == null || path.isEmpty() ? null : path.get(path.size() - 1);
    }

    private void addLine(List<String> lines, String line)
    {
        if (line == null)
        {
            return;
        }

        append(line);
        lines.add(line);
    }

    private void append(String line)
    {
        if (output == null)
        {
            return;
        }

        try
        {
            Files.write(output.toPath(), Collections.singletonList(line), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ex)
        {
            writeWarned = true;
        }
    }

    boolean hasWriteFailed()
    {
        return writeWarned;
    }

    private static final class SegmentCapture
    {
        private final WorldPoint start;
        private final WorldPoint clickDestination;
        private final WorldPoint routeTarget;
        private final Anchor startAnchor;
        private final Anchor destinationAnchor;
        private final List<WorldPoint> expectedPath;
        private final List<WorldPoint> actualPath = new ArrayList<>();
        private final int startTick;
        private final int maxTicks;

        private WorldPoint lastTile;
        private int lastMovedTick;

        private SegmentCapture(
            WorldPoint start,
            WorldPoint clickDestination,
            WorldPoint routeTarget,
            Anchor startAnchor,
            Anchor destinationAnchor,
            List<WorldPoint> expectedPath,
            int startTick
        )
        {
            this.start = start;
            this.clickDestination = clickDestination;
            this.routeTarget = routeTarget;
            this.startAnchor = startAnchor;
            this.destinationAnchor = destinationAnchor;
            this.expectedPath = expectedPath;
            this.startTick = startTick;
            this.maxTicks = Math.max(10,
                DrewsHelperRouteBenchmark.pathDistance(expectedPath) + MAX_SEGMENT_EXTRA_TICKS);
            this.actualPath.add(start);
            this.lastTile = start;
            this.lastMovedTick = startTick;
        }

        private static SegmentCapture start(
            WorldPoint start,
            WorldPoint clickDestination,
            List<WorldPoint> routePath,
            WorldPoint routeTarget,
            int tick
        )
        {
            List<WorldPoint> copiedPath = routePath == null
                ? Collections.emptyList()
                : new ArrayList<>(routePath);
            Anchor startAnchor = Anchor.find(copiedPath, start, 0);
            Anchor destinationAnchor = Anchor.find(
                copiedPath,
                clickDestination,
                startAnchor.hasIndex() ? startAnchor.getIndex() : 0
            );
            List<WorldPoint> expectedPath =
                expectedSegment(copiedPath, startAnchor, destinationAnchor);
            return new SegmentCapture(
                start,
                clickDestination,
                routeTarget,
                startAnchor,
                destinationAnchor,
                expectedPath,
                tick
            );
        }

        WorldPoint getClickDestination()
        {
            return clickDestination;
        }

        String record(
            WorldPoint playerTile,
            WorldPoint currentDestination,
            int tick,
            DrewsHelperWalkingRouteEngine routeEngine
        )
        {
            if (!playerTile.equals(lastTile))
            {
                actualPath.add(playerTile);
                lastTile = playerTile;
                lastMovedTick = tick;
            }

            if (playerTile.equals(clickDestination))
            {
                return finish("destination", tick, routeEngine);
            }

            if (currentDestination == null && actualPath.size() > 1)
            {
                return finish("destination-cleared", tick, routeEngine);
            }

            if (actualPath.size() > 1 && tick - lastMovedTick >= SETTLE_TICKS)
            {
                return finish("settled", tick, routeEngine);
            }

            if (tick - startTick >= maxTicks)
            {
                return finish("limit", tick, routeEngine);
            }

            return null;
        }

        String finish(String reason, int tick, DrewsHelperWalkingRouteEngine routeEngine)
        {
            if (actualPath.size() <= 1)
            {
                return null;
            }

            String routeTrace = "unavailable";
            String divergenceTrace = "unavailable";
            String edgeValidationTrace = "none";
            if (!expectedPath.isEmpty())
            {
                DrewsHelperRouteBenchmark.Report report =
                    DrewsHelperRouteBenchmark.compare(expectedPath, actualPath);
                routeTrace = report.summary();
                divergenceTrace = DrewsHelperRouteBenchmark.formatDivergence(
                    expectedPath,
                    actualPath,
                    true
                );
                edgeValidationTrace = edgeValidationTrace(routeEngine);
            }

            return "DREW_ROUTE_SEGMENT v1"
                + " tick=" + tick
                + " reason=" + reason
                + " start=" + DrewsHelperRouteBenchmark.formatPoint(start)
                + " clickDest=" + DrewsHelperRouteBenchmark.formatPoint(clickDestination)
                + " routeTarget=" + DrewsHelperRouteBenchmark.formatPoint(routeTarget)
                + " routeStart=" + startAnchor.format()
                + " routeDest=" + destinationAnchor.format()
                + " expectedPoints=" + expectedPath.size()
                + " actualPoints=" + actualPath.size()
                + " classification=" + classification(routeEngine)
                + " route={" + routeTrace + "}"
                + " divergence={" + divergenceTrace + "}"
                + " edgeValidation={" + edgeValidationTrace + "}"
                + " expectedPath=" + DrewsHelperRouteBenchmark.formatPath(expectedPath)
                + " actualPath=" + DrewsHelperRouteBenchmark.formatPath(actualPath);
        }

        private String edgeValidationTrace(DrewsHelperWalkingRouteEngine routeEngine)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
                expectedPath,
                actualPath,
                true
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            WorldPoint target = expectedPath.get(expectedPath.size() - 1);
            int expectedRemaining = Math.max(0, expectedPath.size() - divergenceIndex);
            DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic =
                routeEngine.validateObservedEdge(from, actual, target, expectedRemaining);
            return DrewsHelperRouteBenchmark.formatObservedEdgeDiagnostic(
                diagnostic,
                OBSERVED_EDGE_REPEAT_COUNT,
                OBSERVED_EDGE_OVERRIDE_THRESHOLD
            );
        }

        private String classification(DrewsHelperWalkingRouteEngine routeEngine)
        {
            if (!startAnchor.hasIndex())
            {
                return "expected-start-off-route";
            }
            if (!destinationAnchor.hasIndex())
            {
                return "click-destination-off-route";
            }
            if (expectedPath.isEmpty())
            {
                return "expected-segment-unavailable";
            }

            DrewsHelperRouteBenchmark.Report report =
                DrewsHelperRouteBenchmark.compare(expectedPath, actualPath);
            if (report.isFullTileSequenceMatches())
            {
                return "match";
            }

            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
                expectedPath,
                actualPath,
                true
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "route-shape-mismatch";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            WorldPoint target = expectedPath.get(expectedPath.size() - 1);
            int expectedRemaining = Math.max(0, expectedPath.size() - divergenceIndex);
            DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic =
                routeEngine.validateObservedEdge(from, actual, target, expectedRemaining);
            if (!diagnostic.isAvailable())
            {
                return "edge-validation-unavailable";
            }
            if (!diagnostic.isEdgeLegal())
            {
                return "static-map-disagrees-with-live-step";
            }
            if (!diagnostic.isContinuationFound())
            {
                return "live-step-continuation-unknown";
            }
            if (diagnostic.isContinuationLonger())
            {
                return "legal-detour-or-object-pressure";
            }
            return "legal-route-ranker-or-click-shape";
        }

        private static List<WorldPoint> expectedSegment(
            List<WorldPoint> routePath,
            Anchor startAnchor,
            Anchor destinationAnchor
        )
        {
            if (!startAnchor.hasIndex() || !destinationAnchor.hasIndex()
                || destinationAnchor.getIndex() < startAnchor.getIndex())
            {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(
                routePath.subList(startAnchor.getIndex(), destinationAnchor.getIndex() + 1)));
        }
    }

    private static final class Anchor
    {
        private final int index;
        private final int distance;
        private final String status;

        private Anchor(int index, int distance, String status)
        {
            this.index = index;
            this.distance = distance;
            this.status = status;
        }

        private static Anchor find(List<WorldPoint> path, WorldPoint point, int startIndex)
        {
            if (path == null || path.isEmpty() || point == null)
            {
                return new Anchor(-1, -1, "off");
            }

            for (int index = Math.max(0, startIndex); index < path.size(); index++)
            {
                if (point.equals(path.get(index)))
                {
                    return new Anchor(index, 0, "exact");
                }
            }

            int bestIndex = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int index = Math.max(0, startIndex); index < path.size(); index++)
            {
                int distance = tileDistance(path.get(index), point);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }

            if (bestIndex >= 0 && bestDistance <= ROUTE_ANCHOR_TOLERANCE)
            {
                return new Anchor(bestIndex, bestDistance, "near");
            }
            return new Anchor(-1, bestDistance == Integer.MAX_VALUE ? -1 : bestDistance, "off");
        }

        private boolean hasIndex()
        {
            return index >= 0;
        }

        private int getIndex()
        {
            return index;
        }

        private String format()
        {
            return status + ":idx=" + index + ":dist=" + distance;
        }
    }

    private static int tileDistance(WorldPoint first, WorldPoint second)
    {
        if (first == null || second == null || first.getPlane() != second.getPlane())
        {
            return Integer.MAX_VALUE;
        }
        return Math.max(
            Math.abs(first.getX() - second.getX()),
            Math.abs(first.getY() - second.getY())
        );
    }
}
