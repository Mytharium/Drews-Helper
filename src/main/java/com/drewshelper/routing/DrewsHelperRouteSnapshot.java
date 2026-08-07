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

    private DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus status,
        List<WorldPoint> path,
        List<WorldPoint> destinations,
        String message,
        int walkingDistance
    )
    {
        this.status = status;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        this.destinations = Collections.unmodifiableList(new ArrayList<>(destinations));
        this.message = message;
        this.walkingDistance = walkingDistance;
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
            "Calculating walking route",
            0
        );
    }

    public static DrewsHelperRouteSnapshot ready(List<WorldPoint> path, List<WorldPoint> destinations, int walkingDistance)
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.READY,
            path,
            destinations,
            "Walking route ready",
            walkingDistance
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
            Math.max(0, walkingDistance - consumedTileCount)
        );
    }

    public boolean hasPath()
    {
        return !path.isEmpty();
    }
}
