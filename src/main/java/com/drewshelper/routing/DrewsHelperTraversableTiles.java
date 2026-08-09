package com.drewshelper.routing;

import net.runelite.api.coords.WorldPoint;

/**
 * Snaps a requested tile onto one the character can actually stand on.
 *
 * <p>Clicking the middle of a river or the inside of a wall produces a waypoint the router can
 * never reach, and the only feedback is a route that silently fails to solve. Moving the request
 * to the nearest standable tile turns that into the obvious behaviour instead.
 *
 * <p>"Standable" here means the tile has at least one legal move out of it. The collision data is
 * expressed purely as directional moves, so a tile nothing can leave - open water, the inside of
 * a building's wall - is one nothing can stand on either.
 */
public final class DrewsHelperTraversableTiles
{
    /** Far enough to escape a river or a building, short enough to stay obviously "near here". */
    public static final int DEFAULT_MAX_RADIUS = 32;

    private DrewsHelperTraversableTiles()
    {
    }

    public static WorldPoint nearest(DrewsHelperCollisionMap map, WorldPoint requested)
    {
        return nearest((DrewsHelperMovementMap) map, requested, DEFAULT_MAX_RADIUS);
    }

    /**
     * The nearest standable tile to {@code requested}, or {@code requested} itself if it is
     * already standable or nothing standable is within {@code maxRadius}.
     *
     * <p>Search stays on the requested plane - snapping between floors would move the waypoint
     * somewhere the user cannot see. Nearest is measured by true (squared) distance rather than
     * ring order, so a tile straight ahead beats one diagonally further out.
     */
    static WorldPoint nearest(DrewsHelperMovementMap map, WorldPoint requested, int maxRadius)
    {
        if (map == null || requested == null || isTraversable(map, requested))
        {
            return requested;
        }

        int plane = requested.getPlane();
        WorldPoint best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int dx = -maxRadius; dx <= maxRadius; dx++)
        {
            for (int dy = -maxRadius; dy <= maxRadius; dy++)
            {
                int distance = dx * dx + dy * dy;
                if (distance == 0 || distance >= bestDistance)
                {
                    continue;
                }

                WorldPoint candidate = new WorldPoint(requested.getX() + dx, requested.getY() + dy, plane);
                if (isTraversable(map, candidate))
                {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        // Nothing walkable nearby - hand back what was asked for rather than silently relocating
        // the waypoint somewhere unrelated. The route will fail to solve, which is honest.
        return best == null ? requested : best;
    }

    static boolean isTraversable(DrewsHelperMovementMap map, WorldPoint point)
    {
        int x = point.getX();
        int y = point.getY();
        int plane = point.getPlane();

        return map.canMoveNorth(x, y, plane)
            || map.canMoveSouth(x, y, plane)
            || map.canMoveEast(x, y, plane)
            || map.canMoveWest(x, y, plane)
            || map.canMoveNorthEast(x, y, plane)
            || map.canMoveNorthWest(x, y, plane)
            || map.canMoveSouthEast(x, y, plane)
            || map.canMoveSouthWest(x, y, plane);
    }
}
