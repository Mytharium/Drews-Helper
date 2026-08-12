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
            DrewsHelperRouteSearchMetrics.empty()
        );
    }

    private DrewsHelperRouteSnapshot(
        DrewsHelperRouteStatus status,
        List<WorldPoint> path,
        List<WorldPoint> destinations,
        String message,
        int walkingDistance,
        DrewsHelperRouteSearchMetrics primaryMetrics
    )
    {
        this.status = status;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        this.destinations = Collections.unmodifiableList(new ArrayList<>(destinations));
        this.message = message;
        this.walkingDistance = walkingDistance;
        this.primaryMetrics = primaryMetrics == null
            ? DrewsHelperRouteSearchMetrics.empty()
            : primaryMetrics;
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
        return calculating(destinations, Collections.emptyList());
    }

    public static DrewsHelperRouteSnapshot calculating(List<WorldPoint> destinations, List<WorldPoint> previousPath)
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.CALCULATING,
            previousPath == null ? Collections.emptyList() : previousPath,
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
            DrewsHelperRouteSearchMetrics.empty()
        );
    }

    public static DrewsHelperRouteSnapshot ready(
        List<WorldPoint> path,
        List<WorldPoint> destinations,
        int walkingDistance,
        DrewsHelperRouteSearchMetrics primaryMetrics
    )
    {
        return new DrewsHelperRouteSnapshot(
            DrewsHelperRouteStatus.READY,
            path,
            destinations,
            "Route ready",
            walkingDistance,
            primaryMetrics
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

    /**
     * The path as far as the first remaining waypoint, or the whole path when that waypoint is
     * not on it.
     *
     * <p>The ground overlay draws this instead of the full path. With several waypoints placed, a
     * later leg crossing the current one is indistinguishable underfoot, and the player follows
     * the floor rather than the map. The world map still draws the whole journey, where the
     * crossing is readable and the overview is the point.
     *
     * <p>Falls back to the full path rather than to an empty one: a destination that never
     * appears on the path - reached by transport, or snapped to a different tile - must degrade
     * to the old behaviour, never to drawing nothing.
     */
    public List<WorldPoint> getCurrentLegPath()
    {
        if (path.isEmpty() || destinations.isEmpty())
        {
            return path;
        }

        WorldPoint legEnd = destinations.get(0);
        for (int index = 0; index < path.size(); index++)
        {
            if (legEnd.equals(path.get(index)))
            {
                return Collections.unmodifiableList(path.subList(0, index + 1));
            }
        }
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
            primaryMetrics
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
