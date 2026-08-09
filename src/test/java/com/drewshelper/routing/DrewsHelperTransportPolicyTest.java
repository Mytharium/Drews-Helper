package com.drewshelper.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DrewsHelperTransportPolicyTest
{
    private static DrewsHelperTransportPolicy everything()
    {
        return DrewsHelperTransportPolicy.builder()
            .wilderness(true)
            .magicMushtrees(true)
            .build();
    }

    @Test
    public void capabilityGatedFamiliesAreAlwaysEnabled()
    {
        // These are no longer checkboxes - the account's skills, items, quests and unlock
        // varbits decide each edge, so the policy must never withhold the family itself.
        DrewsHelperTransportPolicy plain = DrewsHelperTransportPolicy.builder().build();

        assertTrue(plain.allows(DrewsHelperTransportCategory.BASELINE));
        assertTrue(plain.allows(DrewsHelperTransportCategory.AGILITY_SHORTCUT));
        assertTrue(plain.allows(DrewsHelperTransportCategory.GRAPPLE_SHORTCUT));
        assertTrue(plain.allows(DrewsHelperTransportCategory.CANOE));
        assertTrue(plain.allows(DrewsHelperTransportCategory.GNOME_GLIDER));
        assertTrue(plain.allows(DrewsHelperTransportCategory.HOT_AIR_BALLOON));
        assertTrue(plain.allows(DrewsHelperTransportCategory.QUETZAL));
    }

    @Test
    public void theTwoRemainingChoicesDefaultToOff()
    {
        DrewsHelperTransportPolicy plain = DrewsHelperTransportPolicy.builder().build();

        // Mushtrees have no requirement data to verify; the Wilderness is a risk preference.
        assertFalse(plain.allows(DrewsHelperTransportCategory.MAGIC_MUSHTREE));
        assertFalse(plain.allows(DrewsHelperTransportCategory.WILDERNESS));

        assertTrue(everything().allows(DrewsHelperTransportCategory.MAGIC_MUSHTREE));
        assertTrue(everything().allows(DrewsHelperTransportCategory.WILDERNESS));
    }

    @Test
    public void bothRemainingChoicesChangeTheSignature()
    {
        String baseline = DrewsHelperTransportPolicy.baselineOnly().signature();

        assertNotEquals(baseline, DrewsHelperTransportPolicy.builder().wilderness(true).build().signature());
        assertNotEquals(baseline, DrewsHelperTransportPolicy.builder().magicMushtrees(true).build().signature());

        // The two must not collide, or the route cache would serve a stale route.
        assertNotEquals(
            DrewsHelperTransportPolicy.builder().wilderness(true).build().signature(),
            DrewsHelperTransportPolicy.builder().magicMushtrees(true).build().signature()
        );
    }

    @Test
    public void disabledFamiliesAreNotLoadedIntoTheGraph() throws Exception
    {
        DrewsHelperTransportGraph baseline =
            DrewsHelperTransportGraph.loadDefault(DrewsHelperTransportPolicy.baselineOnly());
        DrewsHelperTransportGraph withMushtrees = DrewsHelperTransportGraph.loadDefault(
            DrewsHelperTransportPolicy.builder().magicMushtrees(true).build());
        DrewsHelperTransportGraph withEverything = DrewsHelperTransportGraph.loadDefault(everything());

        assertTrue(baseline.getEdgeCount() > 5_000);
        assertTrue(withMushtrees.getEdgeCount() > baseline.getEdgeCount());
        assertTrue(withEverything.getEdgeCount() > withMushtrees.getEdgeCount());
    }

    @Test
    public void legacyBooleanOverloadStillMeansBaselinePlusWilderness() throws Exception
    {
        assertEquals(
            DrewsHelperTransportGraph.loadDefault(false).getEdgeCount(),
            DrewsHelperTransportGraph.loadDefault(DrewsHelperTransportPolicy.baselineOnly()).getEdgeCount()
        );
        assertEquals(
            DrewsHelperTransportGraph.loadDefault(true).getEdgeCount(),
            DrewsHelperTransportGraph.loadDefault(
                DrewsHelperTransportPolicy.builder().wilderness(true).build()).getEdgeCount()
        );
    }

    @Test
    public void transportEdgesCarryDurationAndRequirements() throws Exception
    {
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.loadDefault(everything());

        boolean sawDurationAboveOne = false;
        boolean sawSkillRequirement = false;
        for (DrewsHelperTransportEdge edge : graph.allEdges())
        {
            if (edge.getDurationTicks() > 1)
            {
                sawDurationAboveOne = true;
            }
            if (!edge.getSkills().isEmpty())
            {
                sawSkillRequirement = true;
            }
            if (sawDurationAboveOne && sawSkillRequirement)
            {
                break;
            }
        }

        assertTrue("expected at least one edge with a real duration", sawDurationAboveOne);
        assertTrue("expected at least one edge with a skill requirement", sawSkillRequirement);
    }

    @Test
    public void requirementIdsAreDerivedFromTheData() throws Exception
    {
        // The plugin snapshots exactly these, so an empty list would mean nothing gets checked.
        assertTrue(DrewsHelperTransportGraph.requiredQuestNames().size() >= 39);
        assertTrue(DrewsHelperTransportGraph.requiredVarbitIds().size() >= 40);
        assertFalse(DrewsHelperTransportGraph.requiredVarPlayerIds().isEmpty());

        // Ids must be parsed out of terms like "4182&128", not swallowed whole.
        for (Integer id : DrewsHelperTransportGraph.requiredVarbitIds())
        {
            assertTrue("varbit id must be positive, got " + id, id > 0);
        }
    }
}
