package com.drewshelper.routing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Records what actually happened when the player used an object, so route data can later be
 * checked against observed behaviour rather than against itself.
 *
 * <p>This is a recorder, not a judge. It writes down the click, where the player was, where they
 * ended up, how long it took, and how far away the nearest matching transport edge was from each
 * of two anchors. It deliberately does NOT decide whether a row is wrong: at present coverage a
 * missing edge is as likely to mean "we have no data here" as "this object misbehaves", and
 * baking that judgement into the write would discard the evidence needed to tell them apart.
 *
 * <p>Two anchors are recorded because it is not yet known which one transport rows were authored
 * against - the tile the player stood on, or the tile the object occupies. The observed offsets
 * answer that question; guessing it now would bias every row collected.
 */
public final class DrewsHelperTraversalRecorder
{
    /** Ticks to wait for movement to settle before writing the observation. */
    private static final int SETTLE_TICKS = 12;

    /** Guards against a single stuck click pinning the recorder forever. */
    private static final int ABANDON_TICKS = 40;

    private final File output;

    private String option;
    private String target;
    private int objectId;
    private WorldPoint objectTile;
    private WorldPoint fromTile;
    private int clickTick;
    private WorldPoint lastSeenTile;
    private int lastMovedTick;
    private boolean pending;

    public DrewsHelperTraversalRecorder(File output)
    {
        this.output = output;
    }

    /**
     * Begins watching for a move. A second click before the first settles replaces it: the player
     * changed their mind, and attributing the eventual move to the abandoned click would be wrong.
     */
    public void recordClick(
        String option,
        String target,
        int objectId,
        WorldPoint objectTile,
        WorldPoint playerTile,
        int tick
    )
    {
        this.option = option;
        this.target = target;
        this.objectId = objectId;
        this.objectTile = objectTile;
        this.fromTile = playerTile;
        this.clickTick = tick;
        this.lastSeenTile = playerTile;
        this.lastMovedTick = tick;
        this.pending = true;
    }

    /**
     * Advances the pending observation. Returns the line written, or null if nothing was written
     * this tick. A move is considered settled once the player has held the same tile for
     * {@link #SETTLE_TICKS}; a click that never produces a move is dropped rather than recorded as
     * a zero-distance traversal, because standing still is not evidence about an edge.
     */
    public String onTick(WorldPoint playerTile, int tick, DrewsHelperTransportGraph graph)
    {
        if (!pending || playerTile == null)
        {
            return null;
        }

        if (!playerTile.equals(lastSeenTile))
        {
            lastSeenTile = playerTile;
            lastMovedTick = tick;
            return null;
        }

        if (tick - clickTick >= ABANDON_TICKS)
        {
            pending = false;
            return null;
        }

        if (tick - lastMovedTick < SETTLE_TICKS)
        {
            return null;
        }

        pending = false;
        if (playerTile.equals(fromTile))
        {
            // Clicked something and did not move. Says nothing about any edge.
            return null;
        }

        String line = format(playerTile, tick, graph);
        append(line);
        return line;
    }

    public void reset()
    {
        pending = false;
    }

    String format(WorldPoint toTile, int settledTick, DrewsHelperTransportGraph graph)
    {
        DrewsHelperTraversalMatch byPlayer =
            DrewsHelperTraversalMatch.nearest(graph, fromTile, toTile);
        DrewsHelperTraversalMatch byObject =
            DrewsHelperTraversalMatch.nearest(graph, objectTile, toTile);

        StringBuilder line = new StringBuilder("DREW_TRAVERSAL v1");
        line.append(" action=").append(sanitise(option));
        line.append(" target=").append(sanitise(target));
        line.append(" objId=").append(objectId);
        line.append(" objTile=").append(point(objectTile));
        line.append(" from=").append(point(fromTile));
        line.append(" to=").append(point(toTile));
        line.append(" ticks=").append(settledTick - clickTick - SETTLE_TICKS);
        line.append(" playerOff=").append(offset(byPlayer));
        line.append(" playerSrc=").append(byPlayer == null ? "-" : point(byPlayer.getSource()));
        line.append(" objOff=").append(offset(byObject));
        line.append(" objSrc=").append(byObject == null ? "-" : point(byObject.getSource()));
        return line.toString();
    }

    private static String offset(DrewsHelperTraversalMatch match)
    {
        return match == null ? "-" : Integer.toString(match.getOffset());
    }

    private static String point(WorldPoint point)
    {
        if (point == null)
        {
            return "-";
        }
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    /**
     * Menu text carries colour tags and spaces, both of which would break a space-delimited row.
     * Replaced rather than dropped so a mangled value is still visibly a value.
     */
    static String sanitise(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return "-";
        }
        String cleaned = raw.replaceAll("<[^>]*>", "").trim();
        cleaned = cleaned.replaceAll("\\s+", "_");
        return cleaned.isEmpty() ? "-" : cleaned;
    }

    private void append(String line)
    {
        try
        {
            List<String> lines = Collections.singletonList(line);
            Files.write(output.toPath(), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ex)
        {
            // Losing an observation is not worth interrupting play over; the next one still lands.
            pending = false;
        }
    }
}
