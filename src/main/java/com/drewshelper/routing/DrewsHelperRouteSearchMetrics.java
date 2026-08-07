package com.drewshelper.routing;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperRouteSearchMetrics
{
    private final DrewsHelperRouteSolverMode solverMode;
    private final boolean routeFound;
    private final long solveTimeNanos;
    private final int expandedNodes;
    private final int routeStepCount;
    private final int turnCount;

    private DrewsHelperRouteSearchMetrics(
        DrewsHelperRouteSolverMode solverMode,
        boolean routeFound,
        long solveTimeNanos,
        int expandedNodes,
        int routeStepCount,
        int turnCount
    )
    {
        this.solverMode = solverMode == null ? DrewsHelperRouteSolverMode.A_STAR : solverMode;
        this.routeFound = routeFound;
        this.solveTimeNanos = Math.max(0L, solveTimeNanos);
        this.expandedNodes = Math.max(0, expandedNodes);
        this.routeStepCount = Math.max(0, routeStepCount);
        this.turnCount = Math.max(0, turnCount);
    }

    public static DrewsHelperRouteSearchMetrics empty(DrewsHelperRouteSolverMode solverMode)
    {
        return new DrewsHelperRouteSearchMetrics(solverMode, false, 0L, 0, 0, 0);
    }

    public static DrewsHelperRouteSearchMetrics completed(
        DrewsHelperRouteSolverMode solverMode,
        long solveTimeNanos,
        int expandedNodes,
        List<WorldPoint> path
    )
    {
        return new DrewsHelperRouteSearchMetrics(
            solverMode,
            true,
            solveTimeNanos,
            expandedNodes,
            DrewsHelperRouteBenchmark.pathDistance(path),
            DrewsHelperRouteBenchmark.turnCount(path)
        );
    }

    public static DrewsHelperRouteSearchMetrics notFound(
        DrewsHelperRouteSolverMode solverMode,
        long solveTimeNanos,
        int expandedNodes
    )
    {
        return new DrewsHelperRouteSearchMetrics(solverMode, false, solveTimeNanos, expandedNodes, 0, 0);
    }

    public DrewsHelperRouteSolverMode getSolverMode()
    {
        return solverMode;
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
