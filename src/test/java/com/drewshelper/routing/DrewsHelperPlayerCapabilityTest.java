package com.drewshelper.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class DrewsHelperPlayerCapabilityTest
{
    private static DrewsHelperPlayerCapability.Builder base()
    {
        return DrewsHelperPlayerCapability.builder()
            .skill("AGILITY", 50)
            .skill("WOODCUTTING", 30)
            .skill("RANGED", 40)
            .skill("STRENGTH", 40);
    }

    @Test
    public void skillRequirementsUseRealLevels()
    {
        DrewsHelperPlayerCapability capability = base().build();

        assertTrue(capability.meetsSkills(""));
        assertTrue(capability.meetsSkills("Agility=50"));
        assertFalse(capability.meetsSkills("Agility=51"));
        assertTrue(capability.meetsSkills("Agility=8;Ranged=37;Strength=19"));
        assertFalse(capability.meetsSkills("Agility=8;Ranged=99;Strength=19"));
        // A skill we hold nothing in counts as level 0.
        assertFalse(capability.meetsSkills("Fishing=1"));
    }

    @Test
    public void itemSymbolsResolveAcrossEveryTier()
    {
        DrewsHelperPlayerCapability withoutAxe = base().build();
        DrewsHelperPlayerCapability withAxe = base().item(ItemID.DRAGON_AXE, 1).build();

        assertFalse(withoutAxe.meetsItems("AXE=1"));
        assertTrue(withAxe.meetsItems("AXE=1"));
        assertTrue(withAxe.meetsItems(""));
    }

    @Test
    public void ampersandMeansAndPipeMeansOr()
    {
        DrewsHelperPlayerCapability crossbowOnly = base()
            .item(ItemID.XBOWS_CROSSBOW_MITHRIL, 1)
            .build();
        DrewsHelperPlayerCapability grappleKit = base()
            .item(ItemID.XBOWS_CROSSBOW_MITHRIL, 1)
            .item(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1)
            .build();

        assertFalse(crossbowOnly.meetsItems("CROSSBOW=1&MITH_GRAPPLE=1"));
        assertTrue(grappleKit.meetsItems("CROSSBOW=1&MITH_GRAPPLE=1"));

        DrewsHelperPlayerCapability broke = base().build();
        DrewsHelperPlayerCapability withCoins = base().item(ItemID.COINS, 5).build();
        assertFalse(broke.meetsItems("SHANTAY_PASS=1|COINS=5"));
        assertTrue(withCoins.meetsItems("SHANTAY_PASS=1|COINS=5"));
    }

    @Test
    public void quantitiesAreRespected()
    {
        assertFalse(base().item(ItemID.COINS, 19).build().meetsItems("COINS=20"));
        assertTrue(base().item(ItemID.COINS, 20).build().meetsItems("COINS=20"));
        assertTrue(base().item(ItemID.COINS, 5000).build().meetsItems("COINS=3200"));
    }

    @Test
    public void bareItemIdsAreSupported()
    {
        DrewsHelperPlayerCapability capability = base().item(1543, 1).build();
        assertTrue(capability.meetsItems("1543=1"));
        assertFalse(base().build().meetsItems("1543=1"));
    }

    @Test
    public void unrestrictedSatisfiesEverything()
    {
        DrewsHelperPlayerCapability capability = DrewsHelperPlayerCapability.UNRESTRICTED;
        assertTrue(capability.isUnrestricted());
        assertTrue(capability.meetsSkills("Agility=99"));
        assertTrue(capability.meetsItems("CROSSBOW=1&MITH_GRAPPLE=1"));
    }

    @Test
    public void signatureIgnoresCoinChangesWithinATier()
    {
        // Picking up a coin must not rebuild the route, but crossing a threshold must.
        String twentyOne = base().item(ItemID.COINS, 21).build().signature();
        String twentyFive = base().item(ItemID.COINS, 25).build().signature();
        String nineteen = base().item(ItemID.COINS, 19).build().signature();

        assertEquals(twentyOne, twentyFive);
        assertNotEquals(twentyOne, nineteen);
    }

    @Test
    public void signatureTracksLevelsAndHeldItems()
    {
        String plain = base().build().signature();
        String levelled = base().skill("AGILITY", 51).build().signature();
        String armed = base().item(ItemID.DRAGON_AXE, 1).build().signature();

        assertNotEquals(plain, levelled);
        assertNotEquals(plain, armed);
    }

    @Test
    public void trackedItemRequirementsChangeTheSignatureForBareIdsAndQuantities()
    {
        String one = base()
            .trackedItemRequirement("1543=2")
            .item(1543, 1)
            .build()
            .signature();
        String two = base()
            .trackedItemRequirement("1543=2")
            .item(1543, 2)
            .build()
            .signature();

        assertNotEquals(one, two);
    }

    @Test
    public void capabilityFiltersTheLoadedGraph() throws Exception
    {
        // Canoes are always loaded now, so the capability is the only thing gating them.
        DrewsHelperTransportPolicy policy = DrewsHelperTransportPolicy.baselineOnly();

        DrewsHelperTransportGraph unrestricted =
            DrewsHelperTransportGraph.loadDefault(policy, DrewsHelperPlayerCapability.UNRESTRICTED);
        DrewsHelperTransportGraph lowLevelNoAxe = DrewsHelperTransportGraph.loadDefault(
            policy,
            DrewsHelperPlayerCapability.builder().skill("WOODCUTTING", 1).build());

        // Every canoe edge needs Woodcutting and an axe, so none survive.
        assertTrue(lowLevelNoAxe.getEdgeCount() < unrestricted.getEdgeCount());
    }

    @Test
    public void questsGateEdgesAndUnknownNamesArePermissive()
    {
        DrewsHelperTransportEdge glider = new DrewsHelperTransportEdge(
            new net.runelite.api.coords.WorldPoint(2464, 3501, 0),
            new net.runelite.api.coords.WorldPoint(2846, 3497, 0),
            DrewsHelperTransportCategory.GNOME_GLIDER, "Ta Quir Priw",
            6, "", "The Grand Tree", "", "", "");

        assertFalse("unfinished quest must block the edge",
            DrewsHelperPlayerCapability.builder().quest("The Grand Tree", false).build().satisfies(glider));
        assertTrue("finished quest must allow it",
            DrewsHelperPlayerCapability.builder().quest("The Grand Tree", true).build().satisfies(glider));

        // A name we could not resolve is absent from the snapshot and must NOT block the route.
        assertTrue("unresolved quest names must be treated as satisfied",
            DrewsHelperPlayerCapability.builder().build().satisfies(glider));
    }

    @Test
    public void varTermsSupportEqualsGreaterLessAndBitmask()
    {
        DrewsHelperPlayerCapability capability = DrewsHelperPlayerCapability.builder()
            .varbit(100, 5)
            .varbit(4182, 192)
            .build();

        assertTrue(capability.meetsVarbits("100=5"));
        assertFalse(capability.meetsVarbits("100=6"));
        assertTrue(capability.meetsVarbits("100>4"));
        assertFalse(capability.meetsVarbits("100>5"));
        assertTrue(capability.meetsVarbits("100<6"));

        // 192 = 128 | 64, so those bits are set and 32 is not.
        assertTrue(capability.meetsVarbits("4182&128"));
        assertTrue(capability.meetsVarbits("4182&64"));
        assertFalse(capability.meetsVarbits("4182&32"));

        // ';' is AND, and an id we hold no value for is permissive.
        assertTrue(capability.meetsVarbits("100=5;4182&128"));
        assertFalse(capability.meetsVarbits("100=5;4182&32"));
        assertTrue(capability.meetsVarbits("999999=1"));
    }

    @Test
    public void cooldownVarTermsTreatUnknownAsLockedAndExpiredAsUsable()
    {
        DrewsHelperPlayerCapability expired = DrewsHelperPlayerCapability.builder()
            .currentEpochMinute(1_000)
            .varPlayer(892, 969)
            .build();
        DrewsHelperPlayerCapability exactBoundary = DrewsHelperPlayerCapability.builder()
            .currentEpochMinute(1_000)
            .varPlayer(892, 970)
            .build();
        DrewsHelperPlayerCapability active = DrewsHelperPlayerCapability.builder()
            .currentEpochMinute(1_000)
            .varPlayer(892, 980)
            .build();
        DrewsHelperPlayerCapability unknown = DrewsHelperPlayerCapability.builder()
            .currentEpochMinute(1_000)
            .build();

        assertTrue("more than 30 minutes since stored epoch minute means usable",
            expired.meetsVarPlayers("892@30"));
        assertFalse("the handoff rule is strictly greater than the cooldown minutes",
            exactBoundary.meetsVarPlayers("892@30"));
        assertFalse("active cooldown must lock the teleport",
            active.meetsVarPlayers("892@30"));
        assertFalse("unknown cooldown vars must lock the teleport",
            unknown.meetsVarPlayers("892@30"));
        assertTrue("ordinary unknown vars stay permissive",
            unknown.meetsVarPlayers("4560=0"));
    }

    @Test
    public void unmetRequirementsUsePlayerFacingLines()
    {
        DrewsHelperTransportEdge edge = new DrewsHelperTransportEdge(
            new net.runelite.api.coords.WorldPoint(0, 0, 0),
            new net.runelite.api.coords.WorldPoint(10, 0, 0),
            DrewsHelperTransportCategory.GRAPPLE_SHORTCUT,
            "Grapple Broken Raft 17068",
            6,
            "Agility=90;Ranged=70",
            "The Grand Tree;Unknown Quest",
            "CROSSBOW=1&MITH_GRAPPLE=1",
            "100=1;999999=1",
            "892@30"
        );
        DrewsHelperPlayerCapability capability = DrewsHelperPlayerCapability.builder()
            .skill("AGILITY", 89)
            .skill("RANGED", 70)
            .quest("The Grand Tree", false)
            .varbit(100, 0)
            .currentEpochMinute(1_000)
            .varPlayer(892, 980)
            .build();

        List<String> requirements = capability.unmetRequirements(edge);

        assertEquals(Arrays.asList(
            "Agility = 90",
            "Crossbow = 1",
            "Mith grapple = 1",
            "Quest: The Grand Tree",
            "Varbit 100 = 1",
            "Cooldown: VarPlayer 892 ready after 30m"
        ), requirements);
    }

    @Test
    public void unlockStateChangesTheSignature()
    {
        String plain = DrewsHelperPlayerCapability.builder().build().signature();

        assertNotEquals(plain,
            DrewsHelperPlayerCapability.builder().quest("Bone Voyage", true).build().signature());
        assertNotEquals(
            DrewsHelperPlayerCapability.builder().quest("Bone Voyage", false).build().signature(),
            DrewsHelperPlayerCapability.builder().quest("Bone Voyage", true).build().signature());
        assertNotEquals(plain,
            DrewsHelperPlayerCapability.builder().varbit(4182, 1).build().signature());
        assertNotEquals(
            DrewsHelperPlayerCapability.builder().currentEpochMinute(10).build().signature(),
            DrewsHelperPlayerCapability.builder().currentEpochMinute(11).build().signature());
    }
}
