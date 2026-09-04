package com.drewshelper.routing;

import java.util.List;
import net.runelite.api.coords.WorldPoint;

final class DrewsHelperRouteDiagnostics
{
    private DrewsHelperRouteDiagnostics()
    {
    }

    static String formatCandidates(
        DrewsHelperWalkingRouteEngine routeEngine,
        WorldPoint from,
        WorldPoint target
    )
    {
        if (routeEngine == null || from == null || target == null)
        {
            return "none";
        }
        return formatCandidates(routeEngine.moveCandidates(from, target), null, null);
    }

    static String formatCandidates(
        List<DrewsHelperWalkingRouteEngine.MoveCandidate> candidates,
        WorldPoint expected,
        WorldPoint actual
    )
    {
        if (candidates == null || candidates.isEmpty())
        {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++)
        {
            DrewsHelperWalkingRouteEngine.MoveCandidate candidate = candidates.get(i);
            if (i > 0)
            {
                builder.append(';');
            }
            WorldPoint destination = candidate.getDestination();
            builder.append(candidate.getOrder())
                .append(':').append(DrewsHelperRouteBenchmark.formatPoint(destination))
                .append(":dx=").append(candidate.getDirectionX())
                .append(":dy=").append(candidate.getDirectionY())
                .append(":type=").append(candidate.getMoveType())
                .append(":remaining=").append(candidate.getDistanceToTarget())
                .append(":penalty=").append(candidate.getPreferencePenalty());
            if (expected != null)
            {
                builder.append(":expected=").append(destination != null && destination.equals(expected));
            }
            if (actual != null)
            {
                builder.append(":actual=").append(destination != null && destination.equals(actual));
            }
        }
        return builder.toString();
    }

    static WorldPoint currentRouteTarget(DrewsHelperRouteSnapshot snapshot)
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
}
