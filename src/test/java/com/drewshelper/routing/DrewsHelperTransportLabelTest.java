package com.drewshelper.routing;

import static org.junit.Assert.assertEquals;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Pins the HUD label for a transport hop.
 *
 * <p>These strings are what the player reads to decide whether the route is doing something
 * legitimate. "Transport" told them nothing, and a drawn hop was mistaken for a routing failure
 * three separate times before this existed.
 */
public class DrewsHelperTransportLabelTest
{
    private static DrewsHelperTransportEdge edge(DrewsHelperTransportCategory category, String label)
    {
        return new DrewsHelperTransportEdge(
            new WorldPoint(1, 1, 0),
            new WorldPoint(2, 2, 0),
            category,
            label);
    }

    @Test
    public void trailingObjectIdIsStripped()
    {
        // Upstream labels carry the object id: it is meaningless to a player reading a panel.
        assertEquals("Follow Elkoy",
            DrewsHelperTravelEstimate.displayLabel(edge(DrewsHelperTransportCategory.BASELINE, "Follow Elkoy 4968")));
        assertEquals("Squeeze-through Loose Railing",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.BASELINE, "Squeeze-through Loose Railing 2186")));
        assertEquals("Climb-up Ladder",
            DrewsHelperTravelEstimate.displayLabel(edge(DrewsHelperTransportCategory.BASELINE, "Climb-up Ladder 17026")));
    }

    @Test
    public void hubDestinationsLoseTheMenuIndexAndGainTheirNetwork()
    {
        // "1: Tree Gnome Village" is the in-game menu position. On its own the destination name
        // does not say HOW you get there, so the network is prefixed instead.
        assertEquals("Spirit Tree (Tree Gnome Village)",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.SPIRIT_TREE, "1: Tree Gnome Village")));
        assertEquals("Spirit Tree (Your House)",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.PLANTED_SPIRIT_TREE, "C: Your house")));
        assertEquals("Fairy Ring (A I Q)",
            DrewsHelperTravelEstimate.displayLabel(edge(DrewsHelperTransportCategory.FAIRY_RING, "A I Q")));
    }

    @Test
    public void namesAreCapitalisedWithoutShoutingTheJoiningWords()
    {
        // Upstream capitalises the verb but not the noun, so the panel read "Shantay pass".
        assertEquals("Go-through Shantay Pass",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.BASELINE, "Go-through Shantay pass 3")));
        assertEquals("Climb-into Underwall Tunnel",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.AGILITY_SHORTCUT, "Climb-into Underwall tunnel 16527")));

        // ...but blanket capitalisation would wreck these, so joining words stay lower case.
        assertEquals("Spirit Tree (House on the Hill)",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.SPIRIT_TREE, "1. House on the Hill")));
        assertEquals("Spirit Tree (Battlefield of Khazard)",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.SPIRIT_TREE, "3: Battlefield of Khazard")));

        // Deliberate upper case must survive - only the first letter is ever touched.
        assertEquals("Fairy Ring (ZANARIS)",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.FAIRY_RING, "ZANARIS")));
    }

    @Test
    public void agilityShortcutsKeepTheirPlainMenuText()
    {
        assertEquals("Climb Rock",
            DrewsHelperTravelEstimate.displayLabel(
                edge(DrewsHelperTransportCategory.AGILITY_SHORTCUT, "Climb Rock 57604")));
    }

    @Test
    public void aBlankLabelFallsBackToTheFamilyRatherThanShowingNothing()
    {
        assertEquals("Quetzal",
            DrewsHelperTravelEstimate.displayLabel(edge(DrewsHelperTransportCategory.QUETZAL, "")));
        assertEquals("Transport",
            DrewsHelperTravelEstimate.displayLabel(edge(DrewsHelperTransportCategory.BASELINE, null)));
    }

    @Test
    public void aLabelThatIsOnlyAnObjectIdStillYieldsSomethingReadable()
    {
        // Stripping must never leave the player with an empty cell.
        assertEquals("Transport",
            DrewsHelperTravelEstimate.displayLabel(edge(DrewsHelperTransportCategory.BASELINE, "4968")));
    }
}
