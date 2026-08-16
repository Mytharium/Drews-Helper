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
        assertTrue(body.contains("locType=3"));
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
    public void focusedPassiveObjectProfileCandidatesAreRecorded()
    {
        ObjectComposition table = DrewsHelperObjectDefinitionsTest.composition(
            596, "Table", new String[]{null, null, "Examine"}, null, null);

        assertEquals(10, DrewsHelperObjectStateRecorder.locTypeFromConfig(10));
        assertTrue(DrewsHelperObjectStateRecorder.isPassiveObjectProfileCandidate(596, 596, 10));
        assertFalse(DrewsHelperObjectStateRecorder.isPassiveObjectProfileCandidate(596, 596, 22));
        assertTrue(DrewsHelperObjectStateRecorder.isStateCandidate(
            "game", 596, 10, table, null, table.getActions()));
        assertEquals("object-profile", DrewsHelperObjectStateRecorder.category(
            "game", table.getName(), table.getActions(), null, 596, 596, 10));
        assertEquals("PASSIVE_OBJECT_PROFILE", DrewsHelperObjectStateRecorder.state(
            table.getName(), table.getActions(), null, 596, 596, 10));
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

    @Test
    public void sailingAccessObjectsAreTaggedSeparately()
    {
        ObjectComposition gangplank = DrewsHelperObjectDefinitionsTest.composition(
            300, "Gangplank", new String[]{"Cross", "Board", "Examine"}, null, null);

        assertTrue(DrewsHelperObjectStateRecorder.isStateCandidate(
            "game", gangplank, null, gangplank.getActions()));
        assertTrue(DrewsHelperObjectStateRecorder.isSailingCandidate(
            gangplank.getName(), gangplank.getActions()));
        assertEquals("sailing", DrewsHelperObjectStateRecorder.category(
            "game", gangplank.getName(), gangplank.getActions(), null));
        assertEquals("SAILING_ACCESS", DrewsHelperObjectStateRecorder.state(
            gangplank.getName(), gangplank.getActions(), null));
    }

    @Test
    public void genericTravelObjectsStayTraversalNotSailing()
    {
        ObjectComposition cart = DrewsHelperObjectDefinitionsTest.composition(
            301, "Cart", new String[]{"Travel", null, "Examine"}, null, null);

        assertFalse(DrewsHelperObjectStateRecorder.isSailingCandidate(
            cart.getName(), cart.getActions()));
        assertEquals("traversal", DrewsHelperObjectStateRecorder.category(
            "game", cart.getName(), cart.getActions(), null));
        assertEquals("TRAVERSAL_ACTION", DrewsHelperObjectStateRecorder.state(
            cart.getName(), cart.getActions(), null));
    }

    @Test
    public void directSailingVerbsAreTaggedEvenWithGenericNames()
    {
        ObjectComposition accessPoint = DrewsHelperObjectDefinitionsTest.composition(
            302, "Access point", new String[]{"Sail-to", null, "Examine"}, null, null);

        assertTrue(DrewsHelperObjectStateRecorder.isSailingCandidate(
            accessPoint.getName(), accessPoint.getActions()));
        assertEquals("SAILING_ACCESS", DrewsHelperObjectStateRecorder.state(
            accessPoint.getName(), accessPoint.getActions(), null));
    }
}
