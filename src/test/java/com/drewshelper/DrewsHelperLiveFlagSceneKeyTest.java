package com.drewshelper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The dumped-scene set is rebuilt from the capture file's own headers on start-up, so this parse is
 * the only thing standing between an accumulating capture and one that re-dumps ground it already
 * holds. A header it fails to recognise costs a duplicate scene; a data row it wrongly accepts
 * would silently suppress a real capture.
 */
public class DrewsHelperLiveFlagSceneKeyTest
{
    @Test
    public void readsTheSceneKeyFromAHeaderThisPluginWrote()
    {
        assertEquals("2912:3160:0", DrewsHelperPlugin.parseLiveFlagSceneKey(
            "DREW_LIVE_FLAGS scene 2912:3160:0 size=104 covered=103"));
    }

    @Test
    public void readsNegativeAndUpperPlaneCoordinates()
    {
        assertEquals("-64:-128:3", DrewsHelperPlugin.parseLiveFlagSceneKey(
            "DREW_LIVE_FLAGS scene -64:-128:3 size=104 covered=103"));
    }

    @Test
    public void ignoresDataRows()
    {
        assertNull(DrewsHelperPlugin.parseLiveFlagSceneKey("3221,3218,0 10 2097152"));
    }

    @Test
    public void ignoresBlankAndNullLines()
    {
        assertNull(DrewsHelperPlugin.parseLiveFlagSceneKey(""));
        assertNull(DrewsHelperPlugin.parseLiveFlagSceneKey(null));
    }

    @Test
    public void ignoresAHeaderMissingItsCoveredBound()
    {
        // Rejected rather than guessed at: a header with no covered= is from a format whose bound
        // we cannot establish, and treating it as already-captured would skip re-recording it.
        assertNull(DrewsHelperPlugin.parseLiveFlagSceneKey(
            "DREW_LIVE_FLAGS scene 2912:3160:0 size=104"));
    }

    @Test
    public void ignoresATruncatedTrailingLine()
    {
        // A client killed mid-write can leave a partial final line. It must not seed a scene key,
        // or that scene is never captured again.
        assertNull(DrewsHelperPlugin.parseLiveFlagSceneKey("DREW_LIVE_FLAGS scene 2912:31"));
    }
}
