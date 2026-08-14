package com.drewshelper.routing;

import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrewsHelperObjectStateRecorderTest
{
    @Test
    public void formatsObjectStateWithActiveIdAndConfidenceProvenance()
    {
        ObjectComposition active = DrewsHelperObjectDefinitionsTest.composition(
            101, "Large door", new String[]{"Close", null, "Examine"}, null, null,
            -1, -1, 1, 1);
        ObjectComposition base = DrewsHelperObjectDefinitionsTest.composition(
            100, "Large door", new String[]{"Open", null, "Examine"}, new int[]{101}, active,
            1234, 5678, 1, 1);
        DrewsHelperObjectStateRecorder.ObservedObject object =
            new DrewsHelperObjectStateRecorder.ObservedObject(
                "wall", 100, new WorldPoint(3200, 3200, 0), 12, 34, "2/0", "1x1", 99, 123456L);

        String body = DrewsHelperObjectStateRecorder.formatBody(
            "3200:3200:0",
            object,
            base,
            active,
            active.getActions(),
            "01",
            "16777216",
            new DrewsHelperDataProvenance(DrewsHelperDataConfidence.INFERRED, "cache-derived:test")
        );

        assertTrue(body.contains("scene=3200:3200:0"));
        assertTrue(body.contains("kind=wall"));
        assertTrue(body.contains("tile=3200,3200,0"));
        assertTrue(body.contains("sceneTile=12,34"));
        assertTrue(body.contains("objectId=100"));
        assertTrue(body.contains("activeId=101"));
        assertTrue(body.contains("activeChanged=true"));
        assertTrue(body.contains("category=door"));
        assertTrue(body.contains("state=OPEN_CLOSEABLE"));
        assertTrue(body.contains("name=Large_door"));
        assertTrue(body.contains("actions=Close|Examine"));
        assertTrue(body.contains("varbit=1234"));
        assertTrue(body.contains("varp=5678"));
        assertTrue(body.contains("liveEdges=01"));
        assertTrue(body.contains("rawFlags=16777216"));
        assertTrue(body.contains("confidence=CONFIRMED"));
        assertTrue(body.contains("provenance=runelite-scene-live"));
        assertTrue(body.contains("mapConfidence=INFERRED"));
        assertTrue(body.contains("mapProvenance=cache-derived:test"));
    }

    @Test
    public void infersClosedAndOpenDoorStatesFromActions()
    {
        assertEquals("CLOSED_OPENABLE", DrewsHelperObjectStateRecorder.state(new String[]{"Open"}, null));
        assertEquals("OPEN_CLOSEABLE", DrewsHelperObjectStateRecorder.state(new String[]{"Close"}, null));
    }

    @Test
    public void staticObjectsWithoutStateHooksAreIgnored()
    {
        ObjectComposition staticTree = DrewsHelperObjectDefinitionsTest.composition(
            1276, "Tree", new String[]{"Chop down", null, "Examine"}, null, null);

        assertFalse(DrewsHelperObjectStateRecorder.isStateCandidate(
            "game", staticTree, null, staticTree.getActions()));
    }

    @Test
    public void movementAndDoorObjectsAreCandidates()
    {
        ObjectComposition trapdoor = DrewsHelperObjectDefinitionsTest.composition(
            200, "Trapdoor", new String[]{"Open", null, "Examine"}, null, null);
        ObjectComposition shortcut = DrewsHelperObjectDefinitionsTest.composition(
            201, "Gap", new String[]{"Squeeze-through", null, "Examine"}, null, null);

        assertTrue(DrewsHelperObjectStateRecorder.isStateCandidate(
            "wall", trapdoor, null, trapdoor.getActions()));
        assertTrue(DrewsHelperObjectStateRecorder.isStateCandidate(
            "game", shortcut, null, shortcut.getActions()));
    }
}
