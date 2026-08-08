package com.drewshelper;

import com.google.inject.Provides;
import com.drewshelper.routing.DrewsHelperCollisionMap;
import com.drewshelper.routing.DrewsHelperRouteBenchmark;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteSearchMetrics;
import com.drewshelper.routing.DrewsHelperRouteStatus;
import com.drewshelper.routing.DrewsHelperTransportGraph;
import com.drewshelper.routing.DrewsHelperWalkingRouteEngine;
import com.drewshelper.routing.ui.DrewsHelperRouteMapOverlay;
import com.drewshelper.routing.ui.DrewsHelperRouteMinimapOverlay;
import com.drewshelper.routing.ui.DrewsHelperRouteTileOverlay;
import java.io.IOException;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

@Slf4j
@PluginDescriptor(
    name = "Drew's Helper",
    description = "Waypoint placement and route guidance.",
    tags = {"ui", "helper", "waypoint", "route", "transport"}
)
public class DrewsHelperPlugin extends Plugin
{
    public static final int MAX_WAYPOINTS = 5;

    private static final int COMMITTED_ROUTE_RECALCULATE_DISTANCE = 10;
    private static final String CONFIG_GROUP = "drewshelper";
    private static final String SET = "Set";
    private static final String CANCEL = "Cancel";
    private static final String CLEAR = "Clear";
    private static final String WAYPOINT_TARGET_PREFIX = "Waypoint #";
    private static final String ALL_WAYPOINTS_TARGET = "All Waypoints";
    private static final String WAYPOINT_POSITION_KEY_PREFIX = "waypoint";
    private static final String WAYPOINT_POSITION_KEY_SUFFIX = "Position";
    private static final String WAYPOINT_COLOR_KEY_SUFFIX = "PathColor";
    private static final int OBSERVED_EDGE_OVERRIDE_REPEAT_THRESHOLD = 2;
    private static final int ROUTE_BENCHMARK_START_SYNC_TILE_LIMIT = 3;
    private static final int ROUTE_BENCHMARK_PENDING_START_TICK_LIMIT = 10;
    private static final int ROUTE_BENCHMARK_PENDING_START_MOVE_LIMIT = 3;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private WorldMapPointManager worldMapPointManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DrewsHelperOverlay overlay;

    @Inject
    private DrewsHelperRouteMapOverlay routeMapOverlay;

    @Inject
    private DrewsHelperRouteMinimapOverlay routeMinimapOverlay;

    @Inject
    private DrewsHelperRouteTileOverlay routeTileOverlay;

    private final WorldPoint[] waypoints = new WorldPoint[MAX_WAYPOINTS];
    private final WorldMapPoint[] waypointMarkers = new WorldMapPoint[MAX_WAYPOINTS];

    private Point lastMenuOpenedPoint;
    private ExecutorService routeExecutor;
    private Future<?> routeFuture;
    private DrewsHelperCollisionMap collisionMap;
    private DrewsHelperWalkingRouteEngine routeEngine;
    private boolean routeEngineUsesWildernessTransports;
    private volatile DrewsHelperRouteSnapshot routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
    private int routeRequestId;
    private boolean routeDirty = true;
    private String lastRouteSignature = "";
    private RouteBenchmarkCapture routeBenchmarkCapture;
    private final Map<String, Integer> routeBenchmarkObservedEdgeCounts = new HashMap<>();
    private volatile String routeBenchmarkSummary = "";

    @Override
    protected void startUp()
    {
        routeExecutor = Executors.newSingleThreadExecutor(r ->
        {
            Thread thread = new Thread(r, "drews-helper-route");
            thread.setDaemon(true);
            return thread;
        });
        routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
        routeDirty = true;
        lastRouteSignature = "";
        clearRouteBenchmark();
        routeBenchmarkObservedEdgeCounts.clear();
        overlayManager.remove(overlay);
        overlayManager.remove(routeMapOverlay);
        overlayManager.remove(routeMinimapOverlay);
        overlayManager.remove(routeTileOverlay);
        overlayManager.add(overlay);
        overlayManager.add(routeMapOverlay);
        overlayManager.add(routeMinimapOverlay);
        overlayManager.add(routeTileOverlay);
        removeWaypointMarkers();
        loadWaypoints();
        log.debug("Drew's Helper waypoint route UI started");
    }

    @Override
    protected void shutDown()
    {
        routeRequestId++;
        cancelRouteFuture();
        if (routeExecutor != null)
        {
            routeExecutor.shutdownNow();
            routeExecutor = null;
        }
        routeSnapshot = DrewsHelperRouteSnapshot.disabled();
        clearRouteBenchmark();
        removeWaypointMarkers();
        overlayManager.remove(routeTileOverlay);
        overlayManager.remove(routeMinimapOverlay);
        overlayManager.remove(routeMapOverlay);
        overlayManager.remove(overlay);
        log.debug("Drew's Helper waypoint route UI stopped");
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (routeDirty)
        {
            refreshRouteIfNeeded();
            return;
        }

        if (routeSnapshot.getStatus() == DrewsHelperRouteStatus.READY)
        {
            recordRouteBenchmarkPosition();
            advanceCommittedRouteIfNeeded();
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!isMouseOverWorldMap())
        {
            return;
        }

        if (getPlacedWaypointCount() > 0)
        {
            addMenuEntry(event, CLEAR, ALL_WAYPOINTS_TARGET, 0);
        }

        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            addMenuEntry(event, waypointMenuOption(waypoints[index]), waypointLabel(index), 0);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()) || event.getKey() == null)
        {
            return;
        }

        if (isWaypointPositionConfigKey(event.getKey()))
        {
            refreshWaypointMarkers();
            markRouteDirty();
        }

        if (isWaypointColorConfigKey(event.getKey()))
        {
            refreshWaypointMarkers();
        }

        if ("pathingReplacementEnabled".equals(event.getKey())
            || "routeBenchmarkEnabled".equals(event.getKey())
            || "useWildernessTransports".equals(event.getKey()))
        {
            markRouteDirty();
        }
    }

    @Provides
    DrewsHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }

    int getPlacedWaypointCount()
    {
        int count = 0;
        for (WorldPoint waypoint : waypoints)
        {
            if (waypoint != null)
            {
                count++;
            }
        }
        return count;
    }

    public WorldPoint getWaypoint(int index)
    {
        if (index < 0 || index >= MAX_WAYPOINTS)
        {
            return null;
        }
        return waypoints[index];
    }

    public DrewsHelperRouteSnapshot getRouteSnapshot()
    {
        return routeSnapshot;
    }

    public String getRouteBenchmarkSummary()
    {
        return routeBenchmarkSummary;
    }

    private void loadWaypoints()
    {
        Arrays.fill(waypoints, null);
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            waypoints[index] = WaypointPositionCodec.decode(
                configManager.getConfiguration(CONFIG_GROUP, waypointPositionKey(index)));
            syncWaypointMarker(index);
        }
        markRouteDirty();
    }

    private void setWaypoint(int index, WorldPoint point)
    {
        if (point == null || index < 0 || index >= MAX_WAYPOINTS)
        {
            return;
        }

        waypoints[index] = point;
        configManager.setConfiguration(CONFIG_GROUP, waypointPositionKey(index), WaypointPositionCodec.encode(point));
        syncWaypointMarker(index);
        markRouteDirty();
        log.debug("Set {} at {}", waypointLabel(index), point);
    }

    private void clearWaypoints()
    {
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            clearWaypoint(index);
        }
        log.debug("Cleared Drew's Helper waypoints");
    }

    private void clearWaypoint(int index)
    {
        if (index < 0 || index >= MAX_WAYPOINTS)
        {
            return;
        }

        waypoints[index] = null;
        configManager.unsetConfiguration(CONFIG_GROUP, waypointPositionKey(index));
        syncWaypointMarker(index);
        markRouteDirty();
        log.debug("Cleared {}", waypointLabel(index));
    }

    private void markRouteDirty()
    {
        routeDirty = true;
    }

    private void refreshRouteIfNeeded()
    {
        if (!config().pathingReplacementEnabled())
        {
            routeDirty = false;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.disabled();
            return;
        }

        List<WorldPoint> destinations = orderedWaypointDestinations();
        if (destinations.isEmpty())
        {
            routeDirty = false;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeDirty = true;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.noPlayer();
            return;
        }

        WorldPoint start = localPlayer.getWorldLocation();
        boolean useWildernessTransports = config().wildernessTransportsEnabled();
        boolean benchmarkMovement = config().routeBenchmarkEnabled();
        String signature = routeSignature(start, destinations, useWildernessTransports, benchmarkMovement);
        if (!routeDirty && signature.equals(lastRouteSignature))
        {
            return;
        }

        submitRoute(start, destinations, signature, useWildernessTransports);
    }

    private void advanceCommittedRouteIfNeeded()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeDirty = true;
            lastRouteSignature = "";
            cancelRouteFuture();
            clearRouteBenchmark();
            routeSnapshot = DrewsHelperRouteSnapshot.noPlayer();
            return;
        }

        DrewsHelperRouteSnapshot snapshot = routeSnapshot;
        CommittedRouteProgress progress = committedRouteProgress(
            snapshot.getPath(),
            localPlayer.getWorldLocation(),
            COMMITTED_ROUTE_RECALCULATE_DISTANCE
        );
        if (progress.shouldRecalculate())
        {
            markRouteDirty();
            refreshRouteIfNeeded();
            return;
        }

        if (progress.getConsumeCount() > 0)
        {
            routeSnapshot = snapshot.consumeLeadingPathTiles(progress.getConsumeCount());
        }
    }

    private List<WorldPoint> orderedWaypointDestinations()
    {
        List<WorldPoint> destinations = new ArrayList<>();
        for (WorldPoint waypoint : waypoints)
        {
            if (waypoint != null)
            {
                destinations.add(waypoint);
            }
        }
        return destinations;
    }

    private void submitRoute(
        WorldPoint start,
        List<WorldPoint> destinations,
        String signature,
        boolean useWildernessTransports
    )
    {
        if (routeExecutor == null)
        {
            return;
        }

        cancelRouteFuture();
        int requestId = ++routeRequestId;
        List<WorldPoint> routeDestinations = new ArrayList<>(destinations);
        DrewsHelperRouteSnapshot previousSnapshot = routeSnapshot;
        routeSnapshot = DrewsHelperRouteSnapshot.calculating(routeDestinations, previousSnapshot.getPath());
        clearRouteBenchmark();
        lastRouteSignature = signature;
        routeDirty = false;

        routeFuture = routeExecutor.submit(() ->
        {
            DrewsHelperRouteSnapshot calculatedSnapshot;
            try
            {
                calculatedSnapshot = routeEngine(useWildernessTransports).solve(
                    start,
                    routeDestinations
                );
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (RuntimeException | IOException ex)
            {
                log.warn("Failed to calculate Drew's route", ex);
                calculatedSnapshot = DrewsHelperRouteSnapshot.error(routeDestinations, ex.getMessage());
            }

            DrewsHelperRouteSnapshot publishSnapshot = calculatedSnapshot;
            clientThread.invokeLater(() ->
            {
                if (requestId == routeRequestId)
                {
                    routeSnapshot = publishSnapshot;
                    startRouteBenchmarkIfNeeded(publishSnapshot);
                }
                return true;
            });
        });
    }

    private synchronized DrewsHelperWalkingRouteEngine routeEngine(boolean useWildernessTransports) throws IOException
    {
        if (collisionMap == null)
        {
            collisionMap = DrewsHelperCollisionMap.loadDefault();
        }

        if (routeEngine == null || routeEngineUsesWildernessTransports != useWildernessTransports)
        {
            routeEngine = new DrewsHelperWalkingRouteEngine(
                collisionMap,
                DrewsHelperTransportGraph.loadDefault(useWildernessTransports)
            );
            routeEngineUsesWildernessTransports = useWildernessTransports;
        }
        return routeEngine;
    }

    private void cancelRouteFuture()
    {
        if (routeFuture != null)
        {
            routeRequestId++;
            routeFuture.cancel(true);
            routeFuture = null;
        }
    }

    private static String routeSignature(
        WorldPoint start,
        List<WorldPoint> destinations,
        boolean useWildernessTransports,
        boolean benchmarkMovement
    )
    {
        StringBuilder signature = new StringBuilder();
        signature.append(useWildernessTransports ? "wilderness=1|" : "wilderness=0|");
        signature.append(benchmarkMovement ? "benchmark=1|" : "benchmark=0|");
        appendPoint(signature, start);
        for (WorldPoint destination : destinations)
        {
            signature.append('|');
            appendPoint(signature, destination);
        }
        return signature.toString();
    }

    private static void appendPoint(StringBuilder signature, WorldPoint point)
    {
        signature.append(point.getX())
            .append(',')
            .append(point.getY())
            .append(',')
            .append(point.getPlane());
    }

    private void clearRouteBenchmark()
    {
        routeBenchmarkCapture = null;
        routeBenchmarkSummary = "";
    }

    private void startRouteBenchmarkIfNeeded(DrewsHelperRouteSnapshot snapshot)
    {
        clearRouteBenchmark();
        if (!config().routeBenchmarkEnabled()
            || snapshot.getStatus() != DrewsHelperRouteStatus.READY
            || !snapshot.hasPath())
        {
            return;
        }

        routeBenchmarkCapture = new RouteBenchmarkCapture(
            snapshot.getPath(),
            snapshot.getDestinations(),
            routeEngine,
            routeBenchmarkObservedEdgeCounts
        );
        routeBenchmarkSummary = "Waiting for movement";
        log.info(
            "DREW_ROUTE_BENCH start route={}",
            searchMetricsSummary(snapshot.getPrimaryMetrics())
        );
        log.info("DREW_ROUTE_BENCH {}", routeBenchmarkCapture.startTraceLine());
    }

    private void recordRouteBenchmarkPosition()
    {
        RouteBenchmarkCapture capture = routeBenchmarkCapture;
        if (capture == null)
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            return;
        }

        RouteBenchmarkUpdate update = capture.record(localPlayer.getWorldLocation());
        if (update == null)
        {
            return;
        }

        routeBenchmarkSummary = update.overlaySummary();
        log.info("DREW_ROUTE_BENCH {}", update.logLine());
        if (update.isComplete())
        {
            routeBenchmarkCapture = null;
        }
    }

    private static String searchMetricsSummary(DrewsHelperRouteSearchMetrics metrics)
    {
        return "found=" + metrics.isRouteFound()
            + " solve=" + formatMillis(metrics.getSolveTimeMillis())
            + " expanded=" + metrics.getExpandedNodes()
            + " steps=" + metrics.getRouteStepCount()
            + " turns=" + metrics.getTurnCount();
    }

    private static String formatMillis(double millis)
    {
        return String.format(Locale.ROOT, "%.2fms", millis);
    }

    static int routePathIndex(List<WorldPoint> path, WorldPoint point)
    {
        if (path == null || point == null)
        {
            return -1;
        }

        for (int index = 0; index < path.size(); index++)
        {
            if (point.equals(path.get(index)))
            {
                return index;
            }
        }
        return -1;
    }

    static CommittedRouteProgress committedRouteProgress(List<WorldPoint> path, WorldPoint point, int recalculateDistance)
    {
        if (path == null || path.isEmpty() || point == null)
        {
            return CommittedRouteProgress.recalculate();
        }

        int nearestIndex = -1;
        int nearestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < path.size(); index++)
        {
            WorldPoint routePoint = path.get(index);
            if (routePoint.getPlane() != point.getPlane())
            {
                continue;
            }

            int distance = tileDistance(routePoint, point);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }

        if (nearestIndex < 0 || nearestDistance > recalculateDistance)
        {
            return CommittedRouteProgress.recalculate();
        }

        int consumeCount = nearestDistance == 0 ? Math.max(0, nearestIndex) : 0;
        return CommittedRouteProgress.keep(consumeCount, nearestDistance);
    }

    private static int tileDistance(WorldPoint first, WorldPoint second)
    {
        return Math.max(
            Math.abs(first.getX() - second.getX()),
            Math.abs(first.getY() - second.getY())
        );
    }

    static final class CommittedRouteProgress
    {
        private final boolean recalculate;
        private final int consumeCount;
        private final int distanceFromRoute;

        private CommittedRouteProgress(boolean recalculate, int consumeCount, int distanceFromRoute)
        {
            this.recalculate = recalculate;
            this.consumeCount = consumeCount;
            this.distanceFromRoute = distanceFromRoute;
        }

        static CommittedRouteProgress recalculate()
        {
            return new CommittedRouteProgress(true, 0, Integer.MAX_VALUE);
        }

        static CommittedRouteProgress keep(int consumeCount, int distanceFromRoute)
        {
            return new CommittedRouteProgress(false, consumeCount, distanceFromRoute);
        }

        boolean shouldRecalculate()
        {
            return recalculate;
        }

        int getConsumeCount()
        {
            return consumeCount;
        }

        int getDistanceFromRoute()
        {
            return distanceFromRoute;
        }
    }

    static final class RouteBenchmarkCapture
    {
        private final List<WorldPoint> primaryPath;
        private final DrewsHelperWalkingRouteEngine routeEngine;
        private final Map<String, Integer> observedEdgeCounts;
        private final WorldPoint start;
        private final WorldPoint target;
        private final List<RouteBenchmarkSegment> segments;
        private final int maxMovementTicks;
        private final List<WorldPoint> actualPath = new ArrayList<>();
        private WorldPoint pendingLastPoint;
        private int pendingTicks;
        private int pendingMoves;
        private String observedEdgeKey;
        private int observedEdgeRepeatCount;
        private String additionalObservedEdgeKey;
        private int additionalObservedEdgeRepeatCount;

        RouteBenchmarkCapture(
            List<WorldPoint> primaryPath,
            DrewsHelperWalkingRouteEngine routeEngine,
            Map<String, Integer> observedEdgeCounts
        )
        {
            this(
                primaryPath,
                primaryPath == null || primaryPath.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(primaryPath.get(primaryPath.size() - 1)),
                routeEngine,
                observedEdgeCounts
            );
        }

        RouteBenchmarkCapture(
            List<WorldPoint> primaryPath,
            List<WorldPoint> destinations,
            DrewsHelperWalkingRouteEngine routeEngine,
            Map<String, Integer> observedEdgeCounts
        )
        {
            this.primaryPath = new ArrayList<>(primaryPath);
            this.routeEngine = routeEngine;
            this.observedEdgeCounts = observedEdgeCounts;
            this.start = primaryPath.get(0);
            this.target = primaryPath.get(primaryPath.size() - 1);
            this.segments = buildSegments(this.primaryPath, destinations);
            this.maxMovementTicks = Math.max(
                50,
                DrewsHelperRouteBenchmark.pathDistance(primaryPath) + 25
            );
        }

        private String startTraceLine()
        {
            return "trace capture=pendingStart start=" + DrewsHelperRouteBenchmark.formatPoint(start)
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(target)
                + " destinations=" + DrewsHelperRouteBenchmark.formatPathPrefix(
                    segmentTargets(),
                    MAX_WAYPOINTS
                )
                + " expectedPath10=" + DrewsHelperRouteBenchmark.formatPathPrefix(primaryPath);
        }

        RouteBenchmarkUpdate record(WorldPoint point)
        {
            if (actualPath.isEmpty())
            {
                return recordPendingStart(point);
            }

            if (actualPath.get(actualPath.size() - 1).equals(point))
            {
                return null;
            }

            actualPath.add(point);
            int movementTicks = actualPath.size() - 1;
            boolean reachedTarget = point.equals(target);
            boolean movementLimitReached = movementTicks >= maxMovementTicks;
            boolean shouldReport = reachedTarget
                || movementLimitReached
                || movementTicks == 1
                || movementTicks == 5
                || movementTicks == 10
                || movementTicks % 25 == 0;

            if (!shouldReport)
            {
                return null;
            }

            return new RouteBenchmarkUpdate(
                movementTicks,
                reachedTarget || movementLimitReached,
                reachedTarget ? "target" : movementLimitReached ? "limit" : "progress",
                DrewsHelperRouteBenchmark.compare(primaryPath, actualPath),
                DrewsHelperRouteBenchmark.formatPathPrefix(primaryPath),
                DrewsHelperRouteBenchmark.formatPathPrefix(actualPath),
                DrewsHelperRouteBenchmark.formatDivergence(primaryPath, actualPath, reachedTarget || movementLimitReached),
                candidateTrace(primaryPath, reachedTarget || movementLimitReached),
                edgeValidationTrace(primaryPath, reachedTarget || movementLimitReached),
                additionalDivergenceTrace(primaryPath, reachedTarget || movementLimitReached),
                shapeTrace(primaryPath, reachedTarget),
                shadowTrace(reachedTarget || movementLimitReached),
                shapeShadowTrace(reachedTarget || movementLimitReached)
            );
        }

        private RouteBenchmarkUpdate recordPendingStart(WorldPoint point)
        {
            if (point == null)
            {
                return null;
            }

            int routeIndex = routePathIndex(primaryPath, point);
            if (routeIndex >= 0 && routeIndex <= ROUTE_BENCHMARK_START_SYNC_TILE_LIMIT)
            {
                actualPath.addAll(primaryPath.subList(0, routeIndex + 1));
                return null;
            }

            pendingTicks++;
            if (pendingLastPoint == null)
            {
                pendingLastPoint = point;
            }
            else if (!pendingLastPoint.equals(point))
            {
                pendingMoves++;
                pendingLastPoint = point;
            }

            int startDistance = tileDistance(point, start);
            if (pendingTicks >= ROUTE_BENCHMARK_PENDING_START_TICK_LIMIT
                || pendingMoves >= ROUTE_BENCHMARK_PENDING_START_MOVE_LIMIT
                || startDistance > COMMITTED_ROUTE_RECALCULATE_DISTANCE)
            {
                return RouteBenchmarkUpdate.ignored(
                    pendingTicks,
                    "stale-start",
                    "expectedStart=" + DrewsHelperRouteBenchmark.formatPoint(start)
                        + " current=" + DrewsHelperRouteBenchmark.formatPoint(point)
                        + " distance=" + startDistance
                        + " pendingMoves=" + pendingMoves
                );
            }

            return null;
        }

        private String candidateTrace(List<WorldPoint> predictedPath, boolean actualComplete)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
                predictedPath,
                actualPath,
                actualComplete
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            return candidateTraceAt(predictedPath, divergenceIndex);
        }

        private String candidateTraceAt(List<WorldPoint> predictedPath, int divergenceIndex)
        {
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint predicted = DrewsHelperRouteBenchmark.pointAt(predictedPath, divergenceIndex);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            WorldPoint segmentTarget = segment.getTarget();
            if (from == null)
            {
                return "none";
            }

            return "from=" + DrewsHelperRouteBenchmark.formatPoint(from)
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(segmentTarget)
                + finalTargetTrace(segmentTarget)
                + " candidates=" + DrewsHelperRouteBenchmark.formatMoveCandidates(
                    routeEngine.moveCandidates(from, segmentTarget),
                    predicted,
                    actual
                );
        }

        private String edgeValidationTrace(List<WorldPoint> predictedPath, boolean actualComplete)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(
                predictedPath,
                actualPath,
                actualComplete
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            return edgeValidationTraceAt(predictedPath, divergenceIndex, true);
        }

        private String edgeValidationTraceAt(
            List<WorldPoint> predictedPath,
            int divergenceIndex,
            boolean primaryDivergence
        )
        {
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            WorldPoint segmentTarget = segment.getTarget();
            if (from == null || actual == null)
            {
                return "none";
            }

            String key = edgeKey(from, actual, segmentTarget);
            int repeatCount;
            if (primaryDivergence)
            {
                if (observedEdgeKey == null || !observedEdgeKey.equals(key))
                {
                    observedEdgeKey = key;
                    observedEdgeRepeatCount = observedEdgeCounts.merge(key, 1, Integer::sum);
                }
                repeatCount = observedEdgeRepeatCount;
            }
            else
            {
                if (additionalObservedEdgeKey == null || !additionalObservedEdgeKey.equals(key))
                {
                    additionalObservedEdgeKey = key;
                    additionalObservedEdgeRepeatCount = observedEdgeCounts.merge(key, 1, Integer::sum);
                }
                repeatCount = additionalObservedEdgeRepeatCount;
            }

            DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic =
                routeEngine.validateObservedEdge(
                    from,
                    actual,
                    segmentTarget,
                    segment.expectedRemainingFromFork(divergenceIndex)
                );
            return DrewsHelperRouteBenchmark.formatObservedEdgeDiagnostic(
                diagnostic,
                repeatCount,
                OBSERVED_EDGE_OVERRIDE_REPEAT_THRESHOLD
            );
        }

        private String additionalDivergenceTrace(List<WorldPoint> predictedPath, boolean actualComplete)
        {
            int divergenceIndex = DrewsHelperRouteBenchmark.additionalDivergenceIndexAfterFirstMerge(
                predictedPath,
                actualPath,
                actualComplete
            );
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            return "idx=" + divergenceIndex
                + " candidates={" + candidateTraceAt(predictedPath, divergenceIndex) + "}"
                + " edgeValidation={" + edgeValidationTraceAt(predictedPath, divergenceIndex, false) + "}"
                + " forkRank={" + (actualComplete ? forkRankTraceAt(predictedPath, divergenceIndex) : "pending") + "}";
        }

        private String forkRankTraceAt(List<WorldPoint> predictedPath, int divergenceIndex)
        {
            if (divergenceIndex < 1 || routeEngine == null)
            {
                return "none";
            }

            WorldPoint from = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex - 1);
            WorldPoint predicted = DrewsHelperRouteBenchmark.pointAt(predictedPath, divergenceIndex);
            WorldPoint actual = DrewsHelperRouteBenchmark.pointAt(actualPath, divergenceIndex);
            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            WorldPoint segmentTarget = segment.getTarget();
            if (from == null)
            {
                return "none";
            }

            int expectedRemaining = segment.expectedRemainingFromFork(divergenceIndex);
            List<DrewsHelperWalkingRouteEngine.MoveCandidate> candidates =
                routeEngine.moveCandidates(from, segmentTarget);
            if (candidates.isEmpty())
            {
                return "none";
            }

            List<ForkCandidateRank> ranks = new ArrayList<>(candidates.size());
            for (DrewsHelperWalkingRouteEngine.MoveCandidate candidate : candidates)
            {
                DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic =
                    routeEngine.validateObservedEdge(
                        from,
                        candidate.getDestination(),
                        segmentTarget,
                        expectedRemaining
                    );
                ranks.add(new ForkCandidateRank(
                    candidate,
                    diagnostic,
                    candidate.getDestination().equals(predicted),
                    candidate.getDestination().equals(actual)
                ));
            }

            ranks.sort((left, right) -> compareForkCandidateRank(left, right));

            StringBuilder entries = new StringBuilder();
            for (int index = 0; index < ranks.size(); index++)
            {
                if (index > 0)
                {
                    entries.append("; ");
                }
                entries.append(ranks.get(index).format(index + 1));
            }

            return "from=" + DrewsHelperRouteBenchmark.formatPoint(from)
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(segmentTarget)
                + finalTargetTrace(segmentTarget)
                + " expectedRemaining=" + expectedRemaining
                + " best=" + ranks.get(0).formatBest()
                + " predictedRank=" + rankOf(ranks, true)
                + " actualRank=" + rankOf(ranks, false)
                + " entries=[" + entries + "]";
        }

        private static int compareForkCandidateRank(ForkCandidateRank left, ForkCandidateRank right)
        {
            int compared = Integer.compare(left.availabilityPenalty(), right.availabilityPenalty());
            if (compared != 0)
            {
                return compared;
            }

            compared = Integer.compare(left.totalRemainingFromFork(), right.totalRemainingFromFork());
            if (compared != 0)
            {
                return compared;
            }

            compared = Integer.compare(left.getCandidate().getPreferencePenalty(), right.getCandidate().getPreferencePenalty());
            if (compared != 0)
            {
                return compared;
            }

            compared = Integer.compare(left.getCandidate().getDistanceToTarget(), right.getCandidate().getDistanceToTarget());
            if (compared != 0)
            {
                return compared;
            }

            return Integer.compare(left.getCandidate().getOrder(), right.getCandidate().getOrder());
        }

        private static int rankOf(List<ForkCandidateRank> ranks, boolean predicted)
        {
            for (int index = 0; index < ranks.size(); index++)
            {
                ForkCandidateRank rank = ranks.get(index);
                if ((predicted && rank.isPredicted()) || (!predicted && rank.isActual()))
                {
                    return index + 1;
                }
            }
            return -1;
        }

        private String shapeTrace(List<WorldPoint> predictedPath, boolean reachedTarget)
        {
            if (!reachedTarget)
            {
                return "pending";
            }

            if (segments.size() <= 1)
            {
                return DrewsHelperRouteBenchmark.formatShapeDiagnostic(predictedPath, actualPath, true);
            }

            int divergenceIndex = DrewsHelperRouteBenchmark.firstDivergenceIndex(predictedPath, actualPath, true);
            if (divergenceIndex < 0)
            {
                return "scope=segments count=" + segments.size() + " status=match winner=tie";
            }

            RouteBenchmarkSegment segment = segmentForPathIndex(divergenceIndex);
            List<WorldPoint> expectedSegment = segment.expectedPath(predictedPath);
            List<WorldPoint> actualSegment = actualSegmentPath(segment);
            if (actualSegment.isEmpty())
            {
                return "scope=segment"
                    + " target=" + DrewsHelperRouteBenchmark.formatPoint(segment.getTarget())
                    + " status=unavailable reason=actual-segment-not-found";
            }

            return "scope=segment"
                + " target=" + DrewsHelperRouteBenchmark.formatPoint(segment.getTarget())
                + finalTargetTrace(segment.getTarget())
                + " "
                + DrewsHelperRouteBenchmark.formatShapeDiagnostic(expectedSegment, actualSegment, true);
        }

        private String shadowTrace(boolean actualComplete)
        {
            if (!actualComplete)
            {
                return "pending";
            }

            if (routeEngine == null)
            {
                return "status=unavailable reason=no-engine";
            }

            try
            {
                DrewsHelperRouteSnapshot shadowRoute =
                    routeEngine.solveWithoutLocalWalkingOverrides(start, segmentTargets());
                if (shadowRoute.getStatus() != DrewsHelperRouteStatus.READY)
                {
                    return "status=" + shadowRoute.getStatus()
                        + " message=" + shadowRoute.getMessage();
                }

                return DrewsHelperRouteBenchmark.formatShadowRouteDiagnostic(
                    primaryPath,
                    shadowRoute.getPath(),
                    actualPath,
                    true
                );
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return "status=interrupted";
            }
        }

        private String shapeShadowTrace(boolean actualComplete)
        {
            if (!actualComplete)
            {
                return "pending";
            }

            if (routeEngine == null)
            {
                return "status=unavailable reason=no-engine";
            }

            try
            {
                DrewsHelperRouteSnapshot shapeShadowRoute =
                    routeEngine.solveWithShapeRankingWithoutLocalWalkingOverrides(start, segmentTargets());
                if (shapeShadowRoute.getStatus() != DrewsHelperRouteStatus.READY)
                {
                    return "status=" + shapeShadowRoute.getStatus()
                        + " message=" + shapeShadowRoute.getMessage();
                }

                return DrewsHelperRouteBenchmark.formatShapeShadowRouteDiagnostic(
                    primaryPath,
                    shapeShadowRoute.getPath(),
                    actualPath,
                    true
                );
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return "status=interrupted";
            }
        }

        private List<WorldPoint> actualSegmentPath(RouteBenchmarkSegment segment)
        {
            WorldPoint segmentStart = DrewsHelperRouteBenchmark.pointAt(primaryPath, segment.getStartIndex());
            int actualStartIndex = indexOfPathPoint(actualPath, segmentStart, 0);
            if (actualStartIndex < 0)
            {
                return Collections.emptyList();
            }

            int actualEndIndex = indexOfPathPoint(actualPath, segment.getTarget(), actualStartIndex);
            if (actualEndIndex < actualStartIndex)
            {
                return Collections.emptyList();
            }

            return actualPath.subList(actualStartIndex, actualEndIndex + 1);
        }

        private RouteBenchmarkSegment segmentForPathIndex(int pathIndex)
        {
            for (RouteBenchmarkSegment segment : segments)
            {
                if (pathIndex <= segment.getEndIndex())
                {
                    return segment;
                }
            }
            return segments.get(segments.size() - 1);
        }

        private List<WorldPoint> segmentTargets()
        {
            List<WorldPoint> targets = new ArrayList<>(segments.size());
            for (RouteBenchmarkSegment segment : segments)
            {
                targets.add(segment.getTarget());
            }
            return targets;
        }

        private String finalTargetTrace(WorldPoint segmentTarget)
        {
            if (target.equals(segmentTarget))
            {
                return "";
            }

            return " finalTarget=" + DrewsHelperRouteBenchmark.formatPoint(target);
        }

        private static String edgeKey(WorldPoint from, WorldPoint actual, WorldPoint target)
        {
            return DrewsHelperRouteBenchmark.formatPoint(from)
                + "->"
                + DrewsHelperRouteBenchmark.formatPoint(actual)
                + "|target="
                + DrewsHelperRouteBenchmark.formatPoint(target);
        }

        private static List<RouteBenchmarkSegment> buildSegments(
            List<WorldPoint> primaryPath,
            List<WorldPoint> destinations
        )
        {
            if (primaryPath == null || primaryPath.isEmpty())
            {
                return Collections.emptyList();
            }

            List<RouteBenchmarkSegment> routeSegments = new ArrayList<>();
            List<WorldPoint> orderedDestinations = destinations == null
                ? Collections.emptyList()
                : destinations;
            int segmentStartIndex = 0;
            for (WorldPoint destination : orderedDestinations)
            {
                int endIndex = indexOfPathPoint(primaryPath, destination, segmentStartIndex);
                if (endIndex < segmentStartIndex)
                {
                    continue;
                }

                routeSegments.add(new RouteBenchmarkSegment(
                    segmentStartIndex,
                    endIndex,
                    destination
                ));
                segmentStartIndex = endIndex;
            }

            int finalIndex = primaryPath.size() - 1;
            WorldPoint finalTarget = primaryPath.get(finalIndex);
            if (routeSegments.isEmpty() || routeSegments.get(routeSegments.size() - 1).getEndIndex() < finalIndex)
            {
                routeSegments.add(new RouteBenchmarkSegment(
                    segmentStartIndex,
                    finalIndex,
                    finalTarget
                ));
            }

            return Collections.unmodifiableList(routeSegments);
        }

        private static int indexOfPathPoint(List<WorldPoint> path, WorldPoint point, int startIndex)
        {
            if (path == null || point == null)
            {
                return -1;
            }

            for (int index = Math.max(0, startIndex); index < path.size(); index++)
            {
                if (point.equals(path.get(index)))
                {
                    return index;
                }
            }
            return -1;
        }
    }

    private static final class ForkCandidateRank
    {
        private final DrewsHelperWalkingRouteEngine.MoveCandidate candidate;
        private final DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic;
        private final boolean predicted;
        private final boolean actual;

        private ForkCandidateRank(
            DrewsHelperWalkingRouteEngine.MoveCandidate candidate,
            DrewsHelperWalkingRouteEngine.ObservedEdgeDiagnostic diagnostic,
            boolean predicted,
            boolean actual
        )
        {
            this.candidate = candidate;
            this.diagnostic = diagnostic;
            this.predicted = predicted;
            this.actual = actual;
        }

        private DrewsHelperWalkingRouteEngine.MoveCandidate getCandidate()
        {
            return candidate;
        }

        private boolean isPredicted()
        {
            return predicted;
        }

        private boolean isActual()
        {
            return actual;
        }

        private int availabilityPenalty()
        {
            if (!diagnostic.isAvailable())
            {
                return 4;
            }
            if (!diagnostic.isEdgeLegal())
            {
                return 3;
            }
            if (!diagnostic.isContinuationFound())
            {
                return 2;
            }
            return 0;
        }

        private int totalRemainingFromFork()
        {
            return diagnostic.isContinuationFound()
                ? diagnostic.getTotalRemainingFromFork()
                : Integer.MAX_VALUE;
        }

        private String formatBest()
        {
            return role()
                + "@"
                + DrewsHelperRouteBenchmark.formatPoint(candidate.getDestination())
                + " total="
                + totalTrace()
                + " delta="
                + deltaTrace();
        }

        private String format(int rank)
        {
            return rank
                + ":"
                + DrewsHelperRouteBenchmark.formatPoint(candidate.getDestination())
                + " role=" + role()
                + " type=" + candidate.getMoveType()
                + " dist=" + candidate.getDistanceToTarget()
                + " pref=" + candidate.getPreferencePenalty()
                + " legal=" + diagnostic.isEdgeLegal()
                + " total=" + totalTrace()
                + " delta=" + deltaTrace()
                + " reason=" + diagnostic.getReason();
        }

        private String role()
        {
            if (predicted && actual)
            {
                return "predicted+actual";
            }
            if (predicted)
            {
                return "predicted";
            }
            if (actual)
            {
                return "actual";
            }
            return "candidate";
        }

        private String totalTrace()
        {
            return diagnostic.isContinuationFound()
                ? Integer.toString(diagnostic.getTotalRemainingFromFork())
                : "none";
        }

        private String deltaTrace()
        {
            return diagnostic.isContinuationFound()
                ? Integer.toString(diagnostic.getContinuationDelta())
                : "none";
        }
    }

    private static final class RouteBenchmarkSegment
    {
        private final int startIndex;
        private final int endIndex;
        private final WorldPoint target;

        private RouteBenchmarkSegment(int startIndex, int endIndex, WorldPoint target)
        {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.target = target;
        }

        private int getStartIndex()
        {
            return startIndex;
        }

        private int getEndIndex()
        {
            return endIndex;
        }

        private WorldPoint getTarget()
        {
            return target;
        }

        private int expectedRemainingFromFork(int divergenceIndex)
        {
            return Math.max(0, endIndex - divergenceIndex + 1);
        }

        private List<WorldPoint> expectedPath(List<WorldPoint> routePath)
        {
            if (routePath == null || routePath.isEmpty())
            {
                return Collections.emptyList();
            }

            int from = Math.max(0, Math.min(startIndex, routePath.size() - 1));
            int to = Math.max(from, Math.min(endIndex, routePath.size() - 1));
            return routePath.subList(from, to + 1);
        }
    }

    static final class RouteBenchmarkUpdate
    {
        private final int movementTicks;
        private final boolean complete;
        private final String reason;
        private final DrewsHelperRouteBenchmark.Report primaryReport;
        private final String primaryPathTrace;
        private final String actualPathTrace;
        private final String divergenceTrace;
        private final String primaryCandidateTrace;
        private final String edgeValidationTrace;
        private final String additionalDivergenceTrace;
        private final String shapeTrace;
        private final String shadowTrace;
        private final String shapeShadowTrace;
        private final String ignoredTrace;

        private RouteBenchmarkUpdate(
            int movementTicks,
            boolean complete,
            String reason,
            DrewsHelperRouteBenchmark.Report primaryReport,
            String primaryPathTrace,
            String actualPathTrace,
            String divergenceTrace,
            String primaryCandidateTrace,
            String edgeValidationTrace,
            String additionalDivergenceTrace,
            String shapeTrace,
            String shadowTrace,
            String shapeShadowTrace
        )
        {
            this.movementTicks = movementTicks;
            this.complete = complete;
            this.reason = reason;
            this.primaryReport = primaryReport;
            this.primaryPathTrace = primaryPathTrace;
            this.actualPathTrace = actualPathTrace;
            this.divergenceTrace = divergenceTrace;
            this.primaryCandidateTrace = primaryCandidateTrace;
            this.edgeValidationTrace = edgeValidationTrace;
            this.additionalDivergenceTrace = additionalDivergenceTrace;
            this.shapeTrace = shapeTrace;
            this.shadowTrace = shadowTrace;
            this.shapeShadowTrace = shapeShadowTrace;
            this.ignoredTrace = null;
        }

        private RouteBenchmarkUpdate(int movementTicks, String reason, String ignoredTrace)
        {
            this.movementTicks = movementTicks;
            this.complete = true;
            this.reason = reason;
            this.primaryReport = null;
            this.primaryPathTrace = "";
            this.actualPathTrace = "";
            this.divergenceTrace = "";
            this.primaryCandidateTrace = "";
            this.edgeValidationTrace = "";
            this.additionalDivergenceTrace = "";
            this.shapeTrace = "";
            this.shadowTrace = "";
            this.shapeShadowTrace = "";
            this.ignoredTrace = ignoredTrace;
        }

        private static RouteBenchmarkUpdate ignored(int movementTicks, String reason, String ignoredTrace)
        {
            return new RouteBenchmarkUpdate(movementTicks, reason, ignoredTrace);
        }

        boolean isComplete()
        {
            return complete;
        }

        String overlaySummary()
        {
            if (ignoredTrace != null)
            {
                return "Benchmark ignored: " + reason;
            }

            return "Route " + primaryReport.getFirstTenMatches() + "/" + primaryReport.getFirstTenCompared();
        }

        String logLine()
        {
            if (ignoredTrace != null)
            {
                return "ticks=" + movementTicks
                    + " reason=" + reason
                    + " ignored={" + ignoredTrace + "}";
            }

            return "ticks=" + movementTicks
                + " reason=" + reason
                + " route={" + primaryReport.summary() + "}"
                + " expectedPath10=" + primaryPathTrace
                + " actualPath10=" + actualPathTrace
                + " divergence={" + divergenceTrace + "}"
                + " candidates={" + primaryCandidateTrace + "}"
                + " edgeValidation={" + edgeValidationTrace + "}"
                + " additionalDivergenceDetail={" + additionalDivergenceTrace + "}"
                + " shape={" + shapeTrace + "}"
                + " shadow={" + shadowTrace + "}"
                + " shapeShadow={" + shapeShadowTrace + "}";
        }
    }

    private void refreshWaypointMarkers()
    {
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            syncWaypointMarker(index);
        }
    }

    private void syncWaypointMarker(int index)
    {
        if (waypointMarkers[index] != null)
        {
            worldMapPointManager.remove(waypointMarkers[index]);
            waypointMarkers[index] = null;
        }

        WorldPoint waypoint = waypoints[index];
        if (waypoint == null)
        {
            return;
        }

        WorldMapPoint marker = new DrewsHelperWaypointMapPoint(index + 1, waypoint, getWaypointColor(index));
        waypointMarkers[index] = marker;
        worldMapPointManager.add(marker);
    }

    private void removeWaypointMarkers()
    {
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            if (waypointMarkers[index] != null)
            {
                worldMapPointManager.remove(waypointMarkers[index]);
                waypointMarkers[index] = null;
            }
        }

        worldMapPointManager.removeIf(DrewsHelperWaypointMapPoint::isDrewsHelperWaypoint);
    }

    private void onWaypointMenuClicked(MenuEntry entry)
    {
        if (SET.equals(entry.getOption()))
        {
            int waypointIndex = waypointIndexFromTarget(entry.getTarget());
            if (waypointIndex < 0)
            {
                return;
            }

            setWaypoint(waypointIndex, getSelectedMapPoint());
            return;
        }

        if (CANCEL.equals(entry.getOption()))
        {
            clearWaypoint(waypointIndexFromTarget(entry.getTarget()));
            return;
        }

        if (CLEAR.equals(entry.getOption()) && ALL_WAYPOINTS_TARGET.equals(entry.getTarget()))
        {
            clearWaypoints();
        }
    }

    private WorldPoint getSelectedMapPoint()
    {
        Point selectedPoint = client.isMenuOpen() && lastMenuOpenedPoint != null
            ? lastMenuOpenedPoint
            : client.getMouseCanvasPosition();

        if (selectedPoint == null)
        {
            return null;
        }

        return calculateMapPoint(selectedPoint.getX(), selectedPoint.getY());
    }

    private WorldPoint calculateMapPoint(int pointX, int pointY)
    {
        if (pointX == Integer.MIN_VALUE || pointY == Integer.MIN_VALUE || client.getWorldMap() == null)
        {
            return null;
        }

        Point worldMapPosition = client.getWorldMap().getWorldMapPosition();
        if (worldMapPosition == null)
        {
            return null;
        }

        float zoom = client.getWorldMap().getWorldMapZoom();
        if (zoom <= 0)
        {
            return null;
        }

        WorldPoint center = new WorldPoint(worldMapPosition.getX(), worldMapPosition.getY(), 0);
        int middleX = mapWorldPointToGraphicsPointX(center);
        int middleY = mapWorldPointToGraphicsPointY(center);

        if (middleX == Integer.MIN_VALUE || middleY == Integer.MIN_VALUE)
        {
            return null;
        }

        int dx = (int) ((pointX - middleX) / zoom);
        int dy = (int) ((-(pointY - middleY)) / zoom);

        return new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane());
    }

    private int mapWorldPointToGraphicsPointX(WorldPoint worldPoint)
    {
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map == null || client.getWorldMap() == null)
        {
            return Integer.MIN_VALUE;
        }

        float pixelsPerTile = client.getWorldMap().getWorldMapZoom();
        if (pixelsPerTile <= 0)
        {
            return Integer.MIN_VALUE;
        }
        Rectangle worldMapRect = map.getBounds();
        int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
        Point worldMapPosition = client.getWorldMap().getWorldMapPosition();
        if (worldMapPosition == null)
        {
            return Integer.MIN_VALUE;
        }
        int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();
        int xGraphDiff = (int) (xTileOffset * pixelsPerTile);
        xGraphDiff += (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
        xGraphDiff += (int) worldMapRect.getX();
        return xGraphDiff;
    }

    private int mapWorldPointToGraphicsPointY(WorldPoint worldPoint)
    {
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map == null || client.getWorldMap() == null)
        {
            return Integer.MIN_VALUE;
        }

        float pixelsPerTile = client.getWorldMap().getWorldMapZoom();
        if (pixelsPerTile <= 0)
        {
            return Integer.MIN_VALUE;
        }
        Rectangle worldMapRect = map.getBounds();
        int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);
        Point worldMapPosition = client.getWorldMap().getWorldMapPosition();
        if (worldMapPosition == null)
        {
            return Integer.MIN_VALUE;
        }
        int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
        int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
        int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
        yGraphDiff -= (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
        yGraphDiff = worldMapRect.height - yGraphDiff;
        yGraphDiff += (int) worldMapRect.getY();
        return yGraphDiff;
    }

    private boolean isMouseOverWorldMap()
    {
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        Point mouse = client.getMouseCanvasPosition();
        return map != null
            && mouse != null
            && map.getBounds().contains(mouse.getX(), mouse.getY());
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position)
    {
        if (menuContains(option, target))
        {
            return;
        }

        client.getMenu().createMenuEntry(position)
            .setOption(option)
            .setTarget(target)
            .setParam0(event.getActionParam0())
            .setParam1(event.getActionParam1())
            .setIdentifier(event.getIdentifier())
            .setType(MenuAction.RUNELITE)
            .onClick(this::onWaypointMenuClicked);
    }

    private boolean menuContains(String option, String target)
    {
        for (MenuEntry entry : client.getMenu().getMenuEntries())
        {
            if (option.equals(entry.getOption()) && target.equals(entry.getTarget()))
            {
                return true;
            }
        }
        return false;
    }

    private String waypointPositionKey(int index)
    {
        return WAYPOINT_POSITION_KEY_PREFIX + (index + 1) + WAYPOINT_POSITION_KEY_SUFFIX;
    }

    private static String waypointLabel(int index)
    {
        return WAYPOINT_TARGET_PREFIX + (index + 1);
    }

    static String waypointMenuOption(WorldPoint waypoint)
    {
        return waypoint == null ? SET : CANCEL;
    }

    static String[] waypointMenuDisplayLabels(WorldPoint[] waypointState)
    {
        if (waypointState == null || waypointState.length != MAX_WAYPOINTS)
        {
            throw new IllegalArgumentException("Waypoint menu state must contain exactly " + MAX_WAYPOINTS + " slots");
        }

        String[] labels = new String[MAX_WAYPOINTS];
        for (int index = 0; index < MAX_WAYPOINTS; index++)
        {
            labels[index] = waypointMenuOption(waypointState[index]) + " " + waypointLabel(index);
        }
        return labels;
    }

    static boolean isWaypointPositionConfigKey(String key)
    {
        return key != null
            && key.startsWith(WAYPOINT_POSITION_KEY_PREFIX)
            && key.endsWith(WAYPOINT_POSITION_KEY_SUFFIX);
    }

    static boolean isWaypointColorConfigKey(String key)
    {
        return key != null && key.endsWith(WAYPOINT_COLOR_KEY_SUFFIX);
    }

    private int waypointIndexFromTarget(String target)
    {
        if (target == null || !target.startsWith(WAYPOINT_TARGET_PREFIX))
        {
            return -1;
        }

        try
        {
            int waypointNumber = Integer.parseInt(target.substring(WAYPOINT_TARGET_PREFIX.length()));
            int waypointIndex = waypointNumber - 1;
            return waypointIndex >= 0 && waypointIndex < MAX_WAYPOINTS ? waypointIndex : -1;
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    public java.awt.Color getWaypointColor(int index)
    {
        switch (index)
        {
            case 0:
                return config().waypoint1PathColor();
            case 1:
                return config().waypoint2PathColor();
            case 2:
                return config().waypoint3PathColor();
            case 3:
                return config().waypoint4PathColor();
            case 4:
                return config().waypoint5PathColor();
            default:
                throw new IllegalArgumentException("Unsupported waypoint index " + index);
        }
    }

    private DrewsHelperConfig config()
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }
}
