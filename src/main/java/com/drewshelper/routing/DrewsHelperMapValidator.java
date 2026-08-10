package com.drewshelper.routing;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.CollisionDataFlag;

/**
 * Route A - checks our shipped walking data against the game's own collision flags.
 *
 * <p>Route B (the cache dumper) builds the data; this is what catches it being wrong. The Falador
 * west wall gate was a routing symptom for weeks before anyone dug into it - with this running it
 * would have printed itself the first time Myth walked past.
 *
 * <p>Deliberately takes a raw flag array rather than a {@code Client}, so the whole comparison is
 * unit-testable with no game running. The plugin does the client-thread work of fetching flags and
 * hands them here.
 */
public final class DrewsHelperMapValidator
{
    /** RuneLite's loaded scene is always 104x104 tiles. */
    public static final int SCENE_SIZE = 104;

    /**
     * A tile you cannot stand on at all, for any of the reasons the client tracks. Movement into
     * a tile is blocked if the destination carries any of these, independent of the wall flags on
     * the tile you are leaving.
     */
    private static final int BLOCKED_TILE =
        CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
            | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
            | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
            | CollisionDataFlag.BLOCK_MOVEMENT_FULL;

    /**
     * Above this share of fully-blocked tiles the scene is almost certainly a region our shipped
     * map simply does not contain - a missing region reads as solid rock. Reporting 10,000
     * per-tile mismatches for that is noise, so it is reported once as a coverage hole instead.
     */
    private static final double NO_COVERAGE_RATIO = 0.9;

    private DrewsHelperMapValidator()
    {
    }

    /** Which side disagreed, because the two directions mean very different things. */
    public enum Kind
    {
        /**
         * Our data refuses a step the game allows. This is the Falador-gate class - routes get
         * pushed onto long detours around something that is actually passable.
         */
        OURS_BLOCKS_LIVE_OPEN,

        /**
         * Our data allows a step the game refuses. Worse in a sense: the router plans a path
         * straight through a wall and the player just stops.
         */
        OURS_OPEN_LIVE_BLOCKS
    }

    /** One disagreement between our shipped map and the live client, at one tile edge. */
    public static final class Mismatch
    {
        private final int x;
        private final int y;
        private final int plane;
        private final char direction;
        private final Kind kind;

        Mismatch(int x, int y, int plane, char direction, Kind kind)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.direction = direction;
            this.kind = kind;
        }

        public int getX()
        {
            return x;
        }

        public int getY()
        {
            return y;
        }

        public int getPlane()
        {
            return plane;
        }

        /** 'N' or 'E'. Only two are checked; south and west are the same edges seen backwards. */
        public char getDirection()
        {
            return direction;
        }

        public Kind getKind()
        {
            return kind;
        }

        @Override
        public String toString()
        {
            return x + "," + y + "," + plane + " " + direction + " " + kind;
        }
    }

    /** Outcome of one scene comparison. */
    public static final class Report
    {
        private final boolean coverageHole;
        private final List<Mismatch> mismatches;
        private final int tilesChecked;

        Report(boolean coverageHole, List<Mismatch> mismatches, int tilesChecked)
        {
            this.coverageHole = coverageHole;
            this.mismatches = mismatches;
            this.tilesChecked = tilesChecked;
        }

        /** True when our map has no data for this area at all, so per-tile diffs are meaningless. */
        public boolean isCoverageHole()
        {
            return coverageHole;
        }

        public List<Mismatch> getMismatches()
        {
            return mismatches;
        }

        public int getTilesChecked()
        {
            return tilesChecked;
        }
    }

    /**
     * Entry point for the plugin. Takes the concrete shipped map rather than the movement
     * interface, because that interface is package-private and callers outside this package
     * could not name it. The testable core below takes the interface so tests can pass fakes.
     */
    public static Report validate(
        int[][] flags, int baseX, int baseY, int plane, DrewsHelperCollisionMap ours)
    {
        return validateScene(flags, baseX, baseY, plane, ours);
    }

    /**
     * Compares one loaded scene against our shipped map.
     *
     * @param flags  the client's collision flags for this plane, in scene coordinates
     * @param baseX  world x of scene tile 0
     * @param baseY  world y of scene tile 0
     * @param plane  the plane these flags belong to
     * @param ours   our shipped walking data
     */
    static Report validateScene(
        int[][] flags, int baseX, int baseY, int plane, DrewsHelperMovementMap ours)
    {
        List<Mismatch> mismatches = new ArrayList<>();
        if (flags == null || ours == null)
        {
            return new Report(false, mismatches, 0);
        }

        int limit = Math.min(SCENE_SIZE - 1, flags.length - 1);
        int fullyBlockedForUs = 0;
        int tilesChecked = 0;

        for (int sx = 0; sx < limit; sx++)
        {
            if (flags[sx] == null)
            {
                continue;
            }
            for (int sy = 0; sy < Math.min(limit, flags[sx].length - 1); sy++)
            {
                int wx = baseX + sx;
                int wy = baseY + sy;
                tilesChecked++;

                boolean ourN = ours.canMoveNorth(wx, wy, plane);
                boolean ourE = ours.canMoveEast(wx, wy, plane);
                if (!ourN && !ourE && !ours.canMoveSouth(wx, wy, plane)
                    && !ours.canMoveWest(wx, wy, plane))
                {
                    fullyBlockedForUs++;
                }

                boolean liveN = liveCanMoveNorth(flags, sx, sy);
                if (liveN != ourN)
                {
                    mismatches.add(new Mismatch(wx, wy, plane, 'N',
                        liveN ? Kind.OURS_BLOCKS_LIVE_OPEN : Kind.OURS_OPEN_LIVE_BLOCKS));
                }

                boolean liveE = liveCanMoveEast(flags, sx, sy);
                if (liveE != ourE)
                {
                    mismatches.add(new Mismatch(wx, wy, plane, 'E',
                        liveE ? Kind.OURS_BLOCKS_LIVE_OPEN : Kind.OURS_OPEN_LIVE_BLOCKS));
                }
            }
        }

        boolean coverageHole = tilesChecked > 0
            && (double) fullyBlockedForUs / tilesChecked >= NO_COVERAGE_RATIO;
        if (coverageHole)
        {
            return new Report(true, new ArrayList<>(), tilesChecked);
        }
        return new Report(false, mismatches, tilesChecked);
    }

    /**
     * Moving north needs two things: no wall on the north edge of the tile you are leaving, and a
     * destination tile you are allowed to occupy.
     */
    static boolean liveCanMoveNorth(int[][] flags, int sx, int sy)
    {
        return (flags[sx][sy] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
            && (flags[sx][sy + 1] & BLOCKED_TILE) == 0;
    }

    static boolean liveCanMoveEast(int[][] flags, int sx, int sy)
    {
        return (flags[sx][sy] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
            && (flags[sx + 1][sy] & BLOCKED_TILE) == 0;
    }
}
