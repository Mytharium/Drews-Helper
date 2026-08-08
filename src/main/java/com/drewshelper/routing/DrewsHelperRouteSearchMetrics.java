package com.drewshelper.routing;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperRouteSearchMetrics
{
    private final boolean routeFound;
    private final long solveTimeNanos;
    private final int expandedNodes;
    private final int routeStepCount;
    private final int turnCount;

    private DrewsHelperRouteSearchMetrics(
        boolean routeFound,
        long solveTimeNanos,
        int expandedNodes,
        int routeStepCount,
        int turnCount
    )
    {
        this.routeFound = routeFound;
        this.solveTimeNanos = Math.max(0L, solveTimeNanos);
        this.expandedNodes = Math.max(0, expandedNodes);
        this.routeStepCount = Math.max(0, routeStepCount);
        this.turnCount = Math.max(0, turnCount);
    }

    public static DrewsHelperRouteSearchMetrics empty()
    {
        return new DrewsHelperRouteSearchMetrics(false, 0L, 0, 0, 0);
    }

    public static DrewsHelperRouteSearchMetrics completed(
        long solveTimeNanos,
        int expandedNodes,
        List<WorldPoint> path
    )
    {
        return new DrewsHelperRouteSearchMetrics(
            true,
            solveTimeNanos,
            expandedNodes,
            DrewsHelperRouteBenchmark.pathDistance(path),
            DrewsHelperRouteBenchmark.turnCount(path)
        );
    }

    public static DrewsHelperRouteSearchMetrics notFound(
        long solveTimeNanos,
        int expandedNodes
    )
    {
        return new DrewsHelperRouteSearchMetrics(false, solveTimeNanos, expandedNodes, 0, 0);
    }

    public boolean isRouteFound()
    {
        return routeFound;
    }

    public long getSolveTimeNanos()
    {
        return solveTimeNanos;
    }

    public double getSolveTimeMillis()
    {
        return solveTimeNanos / 1_000_000.0;
    }

    public int getExpandedNodes()
    {
        return expandedNodes;
    }

    public int getRouteStepCount()
    {
        return routeStepCount;
    }

    public int getTurnCount()
    {
        return turnCount;
    }
}
