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
 * Records the tile destination the client accepts after a walk-relevant click.
 */
public final class DrewsHelperClickPathRecorder
{
    private static final int CLICK_ACCEPTANCE_WINDOW_TICKS = 3;

    private final File output;

    private PendingClick pendingClick;
    private WorldPoint lastDestination;
    private boolean writeWarned;

    public DrewsHelperClickPathRecorder(File output)
    {
        this.output = output;
    }

    public void recordClick(
        String source,
        String action,
        String option,
        String target,
        int id,
        int param0,
        int param1,
        WorldPoint clickedTile,
        WorldPoint playerTile,
        WorldPoint destinationBefore,
        int tick
    )
    {
        if (playerTile == null)
        {
            return;
        }
        pendingClick = new PendingClick(
            clean(source),
            clean(action),
            clean(option),
            clean(target),
            id,
            param0,
            param1,
            clickedTile,
            playerTile,
            destinationBefore,
            tick
        );
    }

    public List<String> onTick(
        WorldPoint playerTile,
        WorldPoint currentDestination,
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
        if (currentDestination != null && !samePoint(currentDestination, lastDestination))
        {
            addLine(lines, acceptedLine(playerTile, currentDestination, snapshot, routeEngine, tick));
            pendingClick = null;
        }
        else if (pendingClick != null
            && tick - pendingClick.tick > CLICK_ACCEPTANCE_WINDOW_TICKS)
        {
            addLine(lines, pendingClick.timeoutLine(tick));
            pendingClick = null;
        }

        lastDestination = currentDestination;
        return lines;
    }

    public void reset()
    {
        pendingClick = null;
        lastDestination = null;
    }

    public boolean hasWriteFailed()
    {
        return writeWarned;
    }

    private String acceptedLine(
        WorldPoint playerTile,
        WorldPoint acceptedDestination,
        DrewsHelperRouteSnapshot snapshot,
        DrewsHelperWalkingRouteEngine routeEngine,
        int tick
    )
    {
        PendingClick click = pendingClick;
        WorldPoint start = click == null ? playerTile : click.playerTile;
        WorldPoint routeTarget = DrewsHelperRouteDiagnostics.currentRouteTarget(snapshot);
        return "DREW_CLICK_PATH v1"
            + " tick=" + tick
            + " result=accepted"
            + " source=" + (click == null ? "destination-change" : click.source)
            + " clickTick=" + (click == null ? -1 : click.tick)
            + " clickAge=" + (click == null ? -1 : Math.max(0, tick - click.tick))
            + " action=" + (click == null ? "-" : click.action)
            + " option=" + (click == null ? "-" : click.option)
            + " target=" + (click == null ? "-" : click.target)
            + " id=" + (click == null ? -1 : click.id)
            + " param0=" + (click == null ? -1 : click.param0)
            + " param1=" + (click == null ? -1 : click.param1)
            + " start=" + DrewsHelperRouteBenchmark.formatPoint(start)
            + " clickedTile=" + DrewsHelperRouteBenchmark.formatPoint(click == null ? null : click.clickedTile)
            + " destBefore=" + DrewsHelperRouteBenchmark.formatPoint(click == null ? null : click.destinationBefore)
            + " acceptedDest=" + DrewsHelperRouteBenchmark.formatPoint(acceptedDestination)
            + " routeTarget=" + DrewsHelperRouteBenchmark.formatPoint(routeTarget)
            + " forkCandidates={" + DrewsHelperRouteDiagnostics.formatCandidates(
                routeEngine,
                start,
                acceptedDestination
            ) + "}";
    }

    private void addLine(List<String> lines, String line)
    {
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

    private static boolean samePoint(WorldPoint first, WorldPoint second)
    {
        return first == null ? second == null : first.equals(second);
    }

    private static String clean(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "-";
        }
        return value.replaceAll("[^A-Za-z0-9._:/,()#-]+", "_");
    }

    private static final class PendingClick
    {
        private final String source;
        private final String action;
        private final String option;
        private final String target;
        private final int id;
        private final int param0;
        private final int param1;
        private final WorldPoint clickedTile;
        private final WorldPoint playerTile;
        private final WorldPoint destinationBefore;
        private final int tick;

        private PendingClick(
            String source,
            String action,
            String option,
            String target,
            int id,
            int param0,
            int param1,
            WorldPoint clickedTile,
            WorldPoint playerTile,
            WorldPoint destinationBefore,
            int tick
        )
        {
            this.source = source;
            this.action = action;
            this.option = option;
            this.target = target;
            this.id = id;
            this.param0 = param0;
            this.param1 = param1;
            this.clickedTile = clickedTile;
            this.playerTile = playerTile;
            this.destinationBefore = destinationBefore;
            this.tick = tick;
        }

        private String timeoutLine(int tick)
        {
            return "DREW_CLICK_PATH v1"
                + " tick=" + tick
                + " result=no-destination"
                + " source=" + source
                + " clickTick=" + this.tick
                + " clickAge=" + Math.max(0, tick - this.tick)
                + " action=" + action
                + " option=" + option
                + " target=" + target
                + " id=" + id
                + " param0=" + param0
                + " param1=" + param1
                + " start=" + DrewsHelperRouteBenchmark.formatPoint(playerTile)
                + " clickedTile=" + DrewsHelperRouteBenchmark.formatPoint(clickedTile)
                + " destBefore=" + DrewsHelperRouteBenchmark.formatPoint(destinationBefore)
                + " acceptedDest=" + DrewsHelperRouteBenchmark.formatPoint(null)
                + " routeTarget=" + DrewsHelperRouteBenchmark.formatPoint(null)
                + " forkCandidates={none}";
        }
    }
}
