package com.drewshelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("drewshelper")
public interface DrewsHelperConfig extends Config
{
    @ConfigSection(
        name = "Routing Options",
        description = "Route destination and pathing preferences.",
        position = 0,
        closedByDefault = false
    )
    String routingOptions = "routingOptions";

    @ConfigSection(
        name = "Teleport Options",
        description = "Teleport availability and guidance preferences.",
        position = 1,
        closedByDefault = false
    )
    String teleportOptions = "teleportOptions";

    @ConfigSection(
        name = "Basic Transportation",
        description = "Travel networks and shortcuts that depend on account progress or route preference.",
        position = 2,
        closedByDefault = true
    )
    String transportationOptions = "transportationOptions";

    @ConfigSection(
        name = "Advanced Transportation",
        description = "Account unlocks and player-owned house routing.",
        position = 3,
        closedByDefault = true
    )
    String advancedTransportationOptions = "advancedTransportationOptions";

    @ConfigSection(
        name = "Other Transportation",
        description = "Teleport item families Drew may use when they are available.",
        position = 4,
        closedByDefault = true
    )
    String otherTransportationOptions = "otherTransportationOptions";

    @ConfigItem(keyName = "pathingReplacementEnabled", name = "Drew's Shortest Path", description = "Own route decisions instead of stock Shortest Path, including nearby exits before global teleports.", section = routingOptions, position = 0)
    default boolean pathingReplacementEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "chainQuests", name = "Chain Quests", description = "Start the next Quest Helper quest when the current helper completes.", section = routingOptions, position = 1)
    default boolean chainQuests()
    {
        return false;
    }

    @ConfigItem(keyName = "questPrepRouting", name = "Quest Preparation", description = "Plan and route to quest preparation destinations before quest locations when Quest Helper needs items.", section = routingOptions, position = 2)
    default boolean questPrepRouting()
    {
        return false;
    }

    @ConfigItem(keyName = "questPreparationBank", name = "Use: Bank", description = "Include banks as quest preparation destinations when required items are banked.", section = routingOptions, position = 3)
    default boolean questPreparationBank()
    {
        return false;
    }

    @ConfigItem(keyName = "questPreparationGeneralStores", name = "Use: General Stores", description = "Include general stores as quest preparation destinations when they stock required items.", section = routingOptions, position = 4)
    default boolean questPreparationGeneralStores()
    {
        return false;
    }

    @ConfigItem(keyName = "questPreparationGrandExchange", name = "Use: Grand Exchange", description = "Include the Grand Exchange as a quest preparation destination for tradeable required items.", section = routingOptions, position = 5)
    default boolean questPreparationGrandExchange()
    {
        return false;
    }

    @ConfigItem(keyName = "teleportAssistEnabled", name = "Teleport Highlighter", description = "Highlight the selected route teleport UI.", section = teleportOptions, position = 0)
    default boolean teleportAssistEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "filterUnavailableTeleports", name = "Hide Locked Teleports", description = "Show locked teleport warnings in Drew's overlay.", section = teleportOptions, position = 1)
    default boolean filterUnavailableTeleports()
    {
        return true;
    }

    @ConfigItem(keyName = "cooldownAwareReroute", name = "Cooldown Reroute", description = "Recalculate when a teleport is cooling down.", section = teleportOptions, position = 2)
    default boolean cooldownAwareReroute()
    {
        return true;
    }

    @ConfigItem(keyName = "useAgilityShortcuts", name = "Unlocked: Agility Shortcuts", description = "Allow agility shortcuts when your skill and quest requirements are met.", section = transportationOptions, position = 0)
    default boolean agilityShortcutsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useCanoes", name = "Unlocked: Canoes", description = "Allow canoe routes.", section = transportationOptions, position = 1)
    default boolean canoesUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useQuetzals", name = "Unlocked: Quetzals", description = "Allow quetzal and quetzal whistle routes.", section = transportationOptions, position = 2)
    default boolean quetzalsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useGnomeGliders", name = "Unlocked: Gnome Gliders", description = "Allow gnome glider routes.", section = transportationOptions, position = 3)
    default boolean gnomeGlidersUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useGrappleShortcuts", name = "Unlocked: Grapple Shortcuts", description = "Allow crossbow grapple shortcuts when your agility, ranged, and strength requirements are met.", section = transportationOptions, position = 4)
    default boolean grappleShortcutsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useMagicMushtrees", name = "Unlocked: Magic Mushtrees", description = "Allow Fossil Island magic mushtree routes.", section = transportationOptions, position = 5)
    default boolean magicMushtreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useHotAirBalloons", name = "Unlocked: Hot-Air Balloons", description = "Allow hot-air balloon routes.", section = transportationOptions, position = 6)
    default boolean hotAirBalloonsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "spiritTreesUnlocked", name = "Unlocked: Spirit Trees", description = "Allow spirit tree routes.", section = advancedTransportationOptions, position = 0)
    default boolean spiritTreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "fairyRingsUnlocked", name = "Unlocked: Fairy Rings", description = "Allow fairy ring routes.", section = advancedTransportationOptions, position = 1)
    default boolean fairyRingsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohMountedGloryUnlocked", name = "Unlocked: Mounted Glory", description = "Allow routes through your own player-owned house mounted amulet of glory.", section = advancedTransportationOptions, position = 2)
    default boolean pohMountedGloryUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohPortalChamberUnlocked", name = "Unlocked: Portal Chamber", description = "Allow routes through your own player-owned house portal chamber.", section = advancedTransportationOptions, position = 3)
    default boolean pohPortalChamberUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohPortalNexusTier", name = "Unlocked: Portal Nexus", description = "Select the highest portal nexus tier unlocked in your own player-owned house.", section = advancedTransportationOptions, position = 4)
    default PortalNexusTier pohPortalNexusTier()
    {
        return PortalNexusTier.NONE;
    }

    @ConfigItem(keyName = "pohJewelryBoxTier", name = "Unlocked: Jewelry Box", description = "Select the highest jewelry box tier unlocked in your own player-owned house.", section = advancedTransportationOptions, position = 5)
    default JewelleryBoxTier pohJewelryBoxTier()
    {
        return JewelleryBoxTier.NONE;
    }

    @ConfigItem(keyName = "useStandardTablets", name = "Use: Standard Tablets", description = "Allow standard spellbook teleport tablets when they are available.", section = otherTransportationOptions, position = 0)
    default boolean standardTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useAncientTablets", name = "Use: Ancient Tablets", description = "Allow Ancient Magicks teleport tablets when they are available.", section = otherTransportationOptions, position = 1)
    default boolean ancientTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useLunarTablets", name = "Use: Lunar Tablets", description = "Allow Lunar spellbook teleport tablets when they are available.", section = otherTransportationOptions, position = 2)
    default boolean lunarTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useArceuusTablets", name = "Use: Arceuus Tablets", description = "Allow Arceuus spellbook teleport tablets when they are available.", section = otherTransportationOptions, position = 3)
    default boolean arceuusTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useOtherTablets", name = "Use: Other Tablets", description = "Allow other teleport tablet families when they are available.", section = otherTransportationOptions, position = 4)
    default boolean otherTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useOneUseItems", name = "Use: 1-Use Items", description = "Allow single-use teleport items when they are available.", section = otherTransportationOptions, position = 5)
    default boolean oneUseItemsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useTeleportScrolls", name = "Use: Teleport Scrolls", description = "Allow teleport scrolls when they are available.", section = otherTransportationOptions, position = 6)
    default boolean teleportScrollsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useAchievementDiaryItems", name = "Use: Achievement Diary Items", description = "Allow achievement diary reward teleports when they are available.", section = otherTransportationOptions, position = 7)
    default boolean achievementDiaryItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useCombatAchievementItems", name = "Use: Combat Achievement Items", description = "Allow combat achievement reward teleports when they are available.", section = otherTransportationOptions, position = 8)
    default boolean combatAchievementItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useSkillCapes", name = "Use: Skill Capes", description = "Allow skill cape teleports when they are available.", section = otherTransportationOptions, position = 9)
    default boolean skillCapesEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useQuestRelatedItems", name = "Use: Quest Related Items", description = "Allow quest reward item teleports when they are available.", section = otherTransportationOptions, position = 10)
    default boolean questRelatedItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useOtherItems", name = "Use: Other Items", description = "Allow other teleport item routes when they are available.", section = otherTransportationOptions, position = 11)
    default boolean otherItemsEnabled()
    {
        return true;
    }

    default boolean hostedPohTeleports()
    {
        return false;
    }

    default boolean boatsUnlocked()
    {
        return true;
    }

    default boolean teleportationLeversUnlocked()
    {
        return true;
    }

    default boolean teleportationPortalsUnlocked()
    {
        return true;
    }

    default boolean teleportationSpellsUnlocked()
    {
        return true;
    }

    default boolean homeTeleportsUnlocked()
    {
        return true;
    }

    default boolean minigameTeleportsUnlocked()
    {
        return true;
    }

    default boolean wildernessObelisksUnlocked()
    {
        return false;
    }

    default boolean seasonalTransportsUnlocked()
    {
        return false;
    }

    default boolean pohFairyRingUnlocked()
    {
        return false;
    }

    default boolean pohSpiritTreeUnlocked()
    {
        return false;
    }

    default boolean pohObeliskUnlocked()
    {
        return false;
    }
}
