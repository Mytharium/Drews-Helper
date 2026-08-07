package com.drewshelper;

import com.google.inject.Provides;
import com.drewshelper.routing.DrewsHelperCollisionMap;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteStatus;
import com.drewshelper.routing.DrewsHelperWalkingRouteEngine;
import com.drewshelper.routing.ui.DrewsHelperRouteMapOverlay;
import com.drewshelper.routing.ui.DrewsHelperRouteMinimapOverlay;
import com.drewshelper.routing.ui.DrewsHelperRouteTileOverlay;
import java.io.IOException;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    description = "Waypoint placement and walking route guidance.",
    tags = {"ui", "helper", "waypoint", "route"}
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
    private DrewsHelperWalkingRouteEngine routeEngine;
    private volatile DrewsHelperRouteSnapshot routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
    private int routeRequestId;
    private boolean routeDirty = true;
    private String lastRouteSignature = "";

    @Override
    protected void startUp()
    {
        routeExecutor = Executors.newSingleThreadExecutor(r ->
        {
            Thread thread = new Thread(r, "drews-helper-walking-route");
            thread.setDaemon(true);
            return thread;
        });
        routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
        routeDirty = true;
        lastRouteSignature = "";
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

        if ("pathingReplacementEnabled".equals(event.getKey()))
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
            routeSnapshot = DrewsHelperRouteSnapshot.disabled();
            return;
        }

        List<WorldPoint> destinations = orderedWaypointDestinations();
        if (destinations.isEmpty())
        {
            routeDirty = false;
            lastRouteSignature = "";
            cancelRouteFuture();
            routeSnapshot = DrewsHelperRouteSnapshot.noWaypoints();
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeDirty = true;
            lastRouteSignature = "";
            cancelRouteFuture();
            routeSnapshot = DrewsHelperRouteSnapshot.noPlayer();
            return;
        }

        WorldPoint start = localPlayer.getWorldLocation();
        String signature = routeSignature(start, destinations);
        if (!routeDirty && signature.equals(lastRouteSignature))
        {
            return;
        }

        submitRoute(start, destinations, signature);
    }

    private void advanceCommittedRouteIfNeeded()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getWorldLocation() == null)
        {
            routeDirty = true;
            lastRouteSignature = "";
            cancelRouteFuture();
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

    private void submitRoute(WorldPoint start, List<WorldPoint> destinations, String signature)
    {
        if (routeExecutor == null)
        {
            return;
        }

        cancelRouteFuture();
        int requestId = ++routeRequestId;
        List<WorldPoint> routeDestinations = new ArrayList<>(destinations);
        routeSnapshot = DrewsHelperRouteSnapshot.calculating(routeDestinations);
        lastRouteSignature = signature;
        routeDirty = false;

        routeFuture = routeExecutor.submit(() ->
        {
            DrewsHelperRouteSnapshot calculatedSnapshot;
            try
            {
                calculatedSnapshot = routeEngine().solve(start, routeDestinations);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (RuntimeException | IOException ex)
            {
                log.warn("Failed to calculate Drew's walking route", ex);
                calculatedSnapshot = DrewsHelperRouteSnapshot.error(routeDestinations, ex.getMessage());
            }

            DrewsHelperRouteSnapshot publishSnapshot = calculatedSnapshot;
            clientThread.invokeLater(() ->
            {
                if (requestId == routeRequestId)
                {
                    routeSnapshot = publishSnapshot;
                }
                return true;
            });
        });
    }

    private synchronized DrewsHelperWalkingRouteEngine routeEngine() throws IOException
    {
        if (routeEngine == null)
        {
            routeEngine = new DrewsHelperWalkingRouteEngine(DrewsHelperCollisionMap.loadDefault());
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

    private static String routeSignature(WorldPoint start, List<WorldPoint> destinations)
    {
        StringBuilder signature = new StringBuilder();
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
