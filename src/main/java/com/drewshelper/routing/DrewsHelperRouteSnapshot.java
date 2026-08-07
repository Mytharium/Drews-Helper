package com.drewshelper.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperRouteSnapshot
{
    private static final DrewsHelperRouteSnapshot DISABLED = new DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus.DISABLED,
        Collections.emptyList(),
        Collections.emptyList(),
        "Route guidance disabled",
        0
    );

    private static final DrewsHelperRouteSnapshot NO_PLAYER = new DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus.NO_PLAYER,
        Collections.emptyList(),
        Collections.emptyList(),
        "Waiting for player location",
        0
    );

    private static final DrewsHelperRouteSnapshot NO_WAYPOINTS = new DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus.NO_WAYPOINTS,
        Collections.emptyList(),
        Collections.emptyList(),
        "No waypoints placed",
        0
    );

    private final DrewsHelperRouteStatus status;
    private final List<WorldPoint> path;
    private final List<WorldPoint> destinations;
    private final String message;
    private final int walkingDistance;
    private final DrewsHelperRouteSearchMetrics primaryMetrics;
    private final List<WorldPoint> benchmarkPath;
    private final DrewsHelperRouteSearchMetrics benchmarkMetrics;

    private DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus status,
        List<WorldPoint> path,
        List<WorldPoint> destinations,
        String message,
        int walkingDistance
    )
    {
        this(
            status,
            path,
            destinations,
            message,
            walkingDistance,
            DrewsHelperRouteSearchMetrics.empty(DrewsHelperRouteSolverMode.A_STAR),
            Collections.emptyList(),
            DrewsHelperRouteSearchMetrics.empty(DrewsHelperRouteSolverMode.BFS)
        );
    }

    private DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus status,
        List<WorldPoint> path,
        List<WorldPoint> destinations,
        String message,
        int walkingDistance,
        DrewsHelperRouteSearchMetrics primaryMetrics,
        List<WorldPoint> benchmarkPath,
        DrewsHelperRouteSearchMetrics benchmarkMetrics
    )
    {
        this.status = status;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        this.destinations = Collections.unmodifiableList(new ArrayList<>(destinations));
        this.message = message;
        this.walkingDistance = walkingDistance;
        this.primaryMetrics = primaryMetrics == null
            ? DrewsHelperRouteSearchMetrics.empty(DrewsHelperRouteSolverMode.A_STAR)
            : primaryMetrics;
        this.benchmarkPath = Collections.unmodifiableList(new ArrayList<>(
            benchmarkPath == null ? Collections.emptyList() : benchmarkPath));
        this.benchmarkMetrics = benchmarkMetrics == null
            ? DrewsHelperRouteSearchMetrics.empty(DrewsHelperRouteSolverMode.BFS)
            : benchmarkMetrics;
    }

    public static DrewsHelperRouteSnapshot disabled()
    {
        return DISABLED;
    }

    public static DrewsHelperRouteSnapshot noPlayer()
    {
        return NO_PLAYER;
    }

    public static DrewsHelperRouteSnapshot noWaypoints()
    {
        return NO_WAYPOINTS;
    }

    public static DrewsHelperRouteSnapshot calculating(List<WorldPoint> destinations)
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.CALCULATING,
            Collections.emptyList(),
            destinations,
            "Calculating route",
            0
        );
    }

    public static DrewsHelperRouteSnapshot ready(List<WorldPoint> path, List<WorldPoint> destinations, int walkingDistance)
    {
        return ready(
            path,
            destinations,
            walkingDistance,
            DrewsHelperRouteSolverMode.A_STAR,
            DrewsHelperRouteSearchMetrics.empty(DrewsHelperRouteSolverMode.A_STAR),
            Collections.emptyList(),
            DrewsHelperRouteSearchMetrics.empty(DrewsHelperRouteSolverMode.BFS)
        );
    }

    public static DrewsHelperRouteSnapshot ready(
        List<WorldPoint> path,
        List<WorldPoint> destinations,
        int walkingDistance,
        DrewsHelperRouteSolverMode solverMode,
        DrewsHelperRouteSearchMetrics primaryMetrics,
        List<WorldPoint> benchmarkPath,
        DrewsHelperRouteSearchMetrics benchmarkMetrics
    )
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.READY,
            path,
            destinations,
            "Route ready",
            walkingDistance,
            primaryMetrics == null
                ? DrewsHelperRouteSearchMetrics.empty(solverMode)
                : primaryMetrics,
            benchmarkPath,
            benchmarkMetrics
        );
    }

    public static DrewsHelperRouteSnapshot noPath(List<WorldPoint> path, List<WorldPoint> destinations, String message, int walkingDistance)
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.NO_PATH,
            path,
            destinations,
            message,
            walkingDistance
        );
    }

    public static DrewsHelperRouteSnapshot error(List<WorldPoint> destinations, String message)
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.ERROR,
            Collections.emptyList(),
            destinations,
            message,
            0
        );
    }

    public DrewsHelperRouteStatus getStatus()
    {
        return status;
    }

    public List<WorldPoint> getPath()
    {
        return path;
    }

    public List<WorldPoint> getDestinations()
    {
        return destinations;
    }

    public String getMessage()
    {
        return message;
    }

    public int getWalkingDistance()
    {
        return walkingDistance;
    }

    public DrewsHelperRouteSnapshot consumeFirstPathTile()
    {
        return consumeLeadingPathTiles(1);
    }

    public DrewsHelperRouteSnapshot consumeLeadingPathTiles(int tileCount)
    {
        if (status != DrewsHelperRouteStatus.READY || path.size() <= 1)
        {
            return this;
        }

        int consumedTileCount = Math.min(tileCount, path.size() - 1);
        if (consumedTileCount <= 0)
        {
            return this;
        }

        return new DrewsHelperRouteSnapshot(
            status,
            path.subList(consumedTileCount, path.size()),
            destinations,
            message,
            Math.max(0, walkingDistance - consumedTileCount),
            primaryMetrics,
            benchmarkPath,
            benchmarkMetrics
        );
    }

    public boolean hasPath()
    {
        return !path.isEmpty();
    }

    public DrewsHelperRouteSearchMetrics getPrimaryMetrics()
    {
        return primaryMetrics;
    }

    public List<WorldPoint> getBenchmarkPath()
    {
        return benchmarkPath;
    }

    public DrewsHelperRouteSearchMetrics getBenchmarkMetrics()
    {
        return benchmarkMetrics;
    }

    public boolean hasBenchmarkPath()
    {
        return !benchmarkPath.isEmpty() && benchmarkMetrics.isRouteFound();
    }

    public static boolean isTransportJump(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null)
        {
            return false;
        }

        if (from.getPlane() != to.getPlane())
        {
            return true;
        }

        int deltaX = Math.abs(from.getX() - to.getX());
        int deltaY = Math.abs(from.getY() - to.getY());
        return Math.max(deltaX, deltaY) > 1;
    }
}
