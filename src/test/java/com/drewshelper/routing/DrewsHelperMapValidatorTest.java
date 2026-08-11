package com.drewshelper.routing;

import java.util.List;
import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrewsHelperMapValidatorTest
{
    private static final int BASE_X = 3200;
    private static final int BASE_Y = 3200;

    /**
     * The Falador-gate shape: the live client lets you walk east, our shipped data says you
     * cannot. That is exactly the bug that went unnoticed for weeks, so it has to be the first
     * thing this reports.
     */
    @Test
    public void reportsWhereOurDataBlocksAStepTheGameAllows()
    {
        int[][] flags = openScene();
        DrewsHelperMovementMap ours = new BlockingEastMap(3210, 3210);

        DrewsHelperMapValidator.Report report =
            DrewsHelperMapValidator.validateScene(flags, BASE_X, BASE_Y, 0, ours);

        assertFalse("an otherwise-open scene is not a coverage hole", report.isCoverageHole());
        List<DrewsHelperMapValidator.Mismatch> found = report.getMismatches();
        assertEquals("exactly one edge disagrees", 1, found.size());
        assertEquals(3210, found.get(0).getX());
        assertEquals(3210, found.get(0).getY());
        assertEquals('E', found.get(0).getDirection());
        assertEquals(DrewsHelperMapValidator.Kind.OURS_BLOCKS_LIVE_OPEN, found.get(0).getKind());
    }

    /**
     * The opposite failure, which matters just as much: we would route a player straight through
     * a wall the game will not let them pass.
     */
    @Test
    public void reportsWhereOurDataAllowsAStepTheGameRefuses()
    {
        int[][] flags = openScene();
        // wall on the north edge of one tile, in the client only
        flags[10][10] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        DrewsHelperMovementMap ours = new FullyOpenMap();

        DrewsHelperMapValidator.Report report =
            DrewsHelperMapValidator.validateScene(flags, BASE_X, BASE_Y, 0, ours);

        assertEquals(1, report.getMismatches().size());
        DrewsHelperMapValidator.Mismatch m = report.getMismatches().get(0);
        assertEquals(BASE_X + 10, m.getX());
        assertEquals(BASE_Y + 10, m.getY());
        assertEquals('N', m.getDirection());
        assertEquals(DrewsHelperMapValidator.Kind.OURS_OPEN_LIVE_BLOCKS, m.getKind());
    }

    /** A tile flagged unusable blocks entry regardless of the wall flags on the tile you leave. */
    @Test
    public void treatsAnUnusableDestinationTileAsBlocked()
    {
        int[][] flags = openScene();
        flags[20][21] |= CollisionDataFlag.BLOCK_MOVEMENT_FULL;

        assertFalse("cannot step onto a fully blocked tile",
            DrewsHelperMapValidator.liveCanMoveNorth(flags, 20, 20));
        assertTrue("neighbouring edges are unaffected",
            DrewsHelperMapValidator.liveCanMoveEast(flags, 20, 20));
    }

    /**
     * Our map returns false everywhere for a region it does not ship, and there are 1,425 of
     * those. Without this guard every one would produce thousands of bogus mismatches and bury
     * the real ones.
     */
    @Test
    public void reportsAMissingRegionAsACoverageHoleRatherThanTenThousandMismatches()
    {
        int[][] flags = openScene();
        DrewsHelperMovementMap ours = new FullyBlockedMap();

        DrewsHelperMapValidator.Report report =
            DrewsHelperMapValidator.validateScene(flags, BASE_X, BASE_Y, 0, ours);

        assertTrue("a wholly absent region must be reported once", report.isCoverageHole());
        assertTrue("and must not spam per-tile rows", report.getMismatches().isEmpty());
        assertTrue(report.getTilesChecked() > 0);
    }

    /** Agreement produces nothing. Guards against the validator crying wolf on every scene. */
    @Test
    public void staysQuietWhenBothAgree()
    {
        DrewsHelperMapValidator.Report report = DrewsHelperMapValidator.validateScene(
            openScene(), BASE_X, BASE_Y, 0, new FullyOpenMap());

        assertFalse(report.isCoverageHole());
        assertTrue("no disagreement means no output", report.getMismatches().isEmpty());
    }

    /**
     * The mask is what turns a validated scene into reusable ground truth, so it must be exact
     * on the bit positions and forgiving on the bounds - callers sweep whole scenes and cannot
     * afford an exception on an edge tile.
     */
    @Test
    public void northWallReportsNorthBitOnly()
    {
        int[][] flags = openScene();
        flags[1][1] = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;

        assertEquals(1, DrewsHelperMapValidator.liveBlockedMask(flags, 1, 1));
    }

    @Test
    public void eastWallReportsEastBitOnly()
    {
        int[][] flags = openScene();
        flags[1][1] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;

        assertEquals(2, DrewsHelperMapValidator.liveBlockedMask(flags, 1, 1));
    }

    @Test
    public void blockedBothWaysReportsBothBits()
    {
        int[][] flags = openScene();
        flags[1][1] = CollisionDataFlag.BLOCK_MOVEMENT_NORTH
            | CollisionDataFlag.BLOCK_MOVEMENT_EAST;

        assertEquals(3, DrewsHelperMapValidator.liveBlockedMask(flags, 1, 1));
    }

    @Test
    public void openTileReportsZero()
    {
        assertEquals(0, DrewsHelperMapValidator.liveBlockedMask(openScene(), 1, 1));
    }

    @Test
    public void outOfRangeAndNullInputsReportZero()
    {
        int[][] flags = openScene();

        assertEquals(0, DrewsHelperMapValidator.liveBlockedMask(null, 1, 1));
        assertEquals(0, DrewsHelperMapValidator.liveBlockedMask(flags, -1, 1));
        assertEquals(0, DrewsHelperMapValidator.liveBlockedMask(flags, 1, -1));
        assertEquals(0, DrewsHelperMapValidator.liveBlockedMask(flags, flags.length, 1));
        assertEquals(0, DrewsHelperMapValidator.liveBlockedMask(flags, 1, flags[1].length));
    }

    private static int[][] openScene()
    {
        return new int[DrewsHelperMapValidator.SCENE_SIZE][DrewsHelperMapValidator.SCENE_SIZE];
    }

    private static class FullyOpenMap implements DrewsHelperMovementMap
    {
        @Override
        public boolean canMoveNorth(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouth(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveWest(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveNorthEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveNorthWest(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouthEast(int x, int y, int plane)
        {
            return true;
        }

        @Override
        public boolean canMoveSouthWest(int x, int y, int plane)
        {
            return true;
        }
    }

    private static final class FullyBlockedMap extends FullyOpenMap
    {
        @Override
        public boolean canMoveNorth(int x, int y, int plane)
        {
            return false;
        }

        @Override
        public boolean canMoveSouth(int x, int y, int plane)
        {
            return false;
        }

        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return false;
        }

        @Override
        public boolean canMoveWest(int x, int y, int plane)
        {
            return false;
        }
    }

    private static final class BlockingEastMap extends FullyOpenMap
    {
        private final int blockedX;
        private final int blockedY;

        private BlockingEastMap(int blockedX, int blockedY)
        {
            this.blockedX = blockedX;
            this.blockedY = blockedY;
        }

        @Override
        public boolean canMoveEast(int x, int y, int plane)
        {
            return !(x == blockedX && y == blockedY);
        }
    }
}
