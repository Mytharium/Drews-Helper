package com.drewshelper;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("drewshelper")
public interface DrewsHelperConfig extends Config
{
    @ConfigSection(
        name = "Routing Options",
        description = "Core route overlay and destination behaviour.",
        position = 0,
        closedByDefault = false
    )
    String routingOptions = "routingOptions";

    @ConfigSection(
        name = "Teleport Options",
        description = "Teleport guidance settings reserved for future route features.",
        position = 1,
        closedByDefault = false
    )
    String teleportOptions = "teleportOptions";

    @ConfigSection(
        name = "Basic Transportation",
        description = "Unlock-based travel networks. Ordinary click/pay transports are built into routing.",
        position = 2,
        closedByDefault = true
    )
    String transportationOptions = "transportationOptions";

    @ConfigSection(
        name = "Advanced Transportation",
        description = "Account unlocks and player-owned house travel settings.",
        position = 3,
        closedByDefault = true
    )
    String advancedTransportationOptions = "advancedTransportationOptions";

    @ConfigSection(
        name = "Other Transportation",
        description = "Teleport item families and dangerous Wilderness transport preference.",
        position = 4,
        closedByDefault = true
    )
    String otherTransportationOptions = "otherTransportationOptions";

    @ConfigSection(
        name = "Settings",
        description = "Waypoint path colour preferences.",
        position = 5,
        closedByDefault = false
    )
    String waypointSettings = "waypointSettings";

    @ConfigItem(keyName = "pathingReplacementEnabled", name = "Drew's Shortest Path", description = "Enable Drew's waypoint route overlay and path calculation.", section = routingOptions, position = 0)
    default boolean pathingReplacementEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "chainQuests", name = "Chain Quests", description = "Reserved for starting the next Quest Helper quest after the current helper completes.", section = routingOptions, position = 1)
    default boolean chainQuests()
    {
        return false;
    }

    @ConfigItem(keyName = "questPrepRouting", name = "Quest Preparation", description = "Reserved for routing to preparation stops before Quest Helper destinations.", section = routingOptions, position = 2)
    default boolean questPrepRouting()
    {
        return false;
    }

    @ConfigItem(keyName = "questPreparationBank", name = "Use: Bank", description = "Reserved for using banks as quest preparation stops when required items are banked.", section = routingOptions, position = 3)
    default boolean questPreparationBank()
    {
        return false;
    }

    @ConfigItem(keyName = "questPreparationGeneralStores", name = "Use: General Stores", description = "Reserved for using general stores as quest preparation stops.", section = routingOptions, position = 4)
    default boolean questPreparationGeneralStores()
    {
        return false;
    }

    @ConfigItem(keyName = "questPreparationGrandExchange", name = "Use: Grand Exchange", description = "Reserved for using the Grand Exchange as a quest preparation stop.", section = routingOptions, position = 5)
    default boolean questPreparationGrandExchange()
    {
        return false;
    }

    @ConfigItem(keyName = "routeBenchmarkEnabled", name = "Benchmark Movement", description = "Log DREW_ROUTE_BENCH overlay-vs-client movement comparisons while you walk the route.", section = routingOptions, position = 6)
    default boolean routeBenchmarkEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "teleportAssistEnabled", name = "Teleport Highlighter", description = "Reserved for highlighting the selected teleport UI when teleport routing is rebuilt.", section = teleportOptions, position = 0)
    default boolean teleportAssistEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "filterUnavailableTeleports", name = "Hide Locked Teleports", description = "Reserved for hiding known locked teleports once teleport availability scanning is rebuilt.", section = teleportOptions, position = 1)
    default boolean filterUnavailableTeleports()
    {
        return true;
    }

    @ConfigItem(keyName = "cooldownAwareReroute", name = "Cooldown Reroute", description = "Reserved for rerouting around teleport cooldowns once teleport routing is rebuilt.", section = teleportOptions, position = 2)
    default boolean cooldownAwareReroute()
    {
        return true;
    }

    @ConfigItem(keyName = "useAgilityShortcuts", name = "Unlocked: Agility Shortcuts", description = "Allow agility shortcuts once your skill and quest requirements are met.", section = transportationOptions, position = 0)
    default boolean agilityShortcutsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useCanoes", name = "Unlocked: Canoes", description = "Allow canoe routes when you have the axe and Woodcutting level needed.", section = transportationOptions, position = 1)
    default boolean canoesUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useQuetzals", name = "Unlocked: Quetzals", description = "Allow quetzal and quetzal whistle routes once your account has them unlocked.", section = transportationOptions, position = 2)
    default boolean quetzalsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useGnomeGliders", name = "Unlocked: Gnome Gliders", description = "Allow gnome glider routes once your account has them unlocked.", section = transportationOptions, position = 3)
    default boolean gnomeGlidersUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useGrappleShortcuts", name = "Unlocked: Grapple Shortcuts", description = "Allow grapple shortcuts once your Agility, Ranged, and Strength requirements are met.", section = transportationOptions, position = 4)
    default boolean grappleShortcutsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useMagicMushtrees", name = "Unlocked: Magic Mushtrees", description = "Allow Fossil Island magic mushtree routes once your account has them unlocked.", section = transportationOptions, position = 5)
    default boolean magicMushtreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useHotAirBalloons", name = "Unlocked: Hot-Air Balloons", description = "Allow hot-air balloon routes once your account has them unlocked.", section = transportationOptions, position = 6)
    default boolean hotAirBalloonsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "spiritTreesUnlocked", name = "Unlocked: Spirit Trees", description = "Allow spirit tree routes once your account has them unlocked.", section = advancedTransportationOptions, position = 0)
    default boolean spiritTreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "fairyRingsUnlocked", name = "Unlocked: Fairy Rings", description = "Allow fairy ring routes once your account has them unlocked.", section = advancedTransportationOptions, position = 1)
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

    @ConfigItem(keyName = "useStandardTablets", name = "Use: Standard Tablets", description = "Reserved for standard spellbook teleport tablets once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 0)
    default boolean standardTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useAncientTablets", name = "Use: Ancient Tablets", description = "Reserved for Ancient Magicks teleport tablets once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 1)
    default boolean ancientTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useLunarTablets", name = "Use: Lunar Tablets", description = "Reserved for Lunar spellbook teleport tablets once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 2)
    default boolean lunarTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useArceuusTablets", name = "Use: Arceuus Tablets", description = "Reserved for Arceuus spellbook teleport tablets once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 3)
    default boolean arceuusTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useOtherTablets", name = "Use: Other Tablets", description = "Reserved for other teleport tablet families once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 4)
    default boolean otherTabletsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useOneUseItems", name = "Use: 1-Use Items", description = "Reserved for single-use teleport items once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 5)
    default boolean oneUseItemsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useTeleportScrolls", name = "Use: Teleport Scrolls", description = "Reserved for teleport scrolls once item teleport routing is rebuilt.", section = otherTransportationOptions, position = 6)
    default boolean teleportScrollsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "useAchievementDiaryItems", name = "Use: Achievement Diary Items", description = "Allow achievement diary reward teleports once your account has them unlocked.", section = otherTransportationOptions, position = 7)
    default boolean achievementDiaryItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useCombatAchievementItems", name = "Use: Combat Achievement Items", description = "Allow combat achievement reward teleports once your account has them unlocked.", section = otherTransportationOptions, position = 8)
    default boolean combatAchievementItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useSkillCapes", name = "Use: Skill Capes", description = "Allow skill cape teleports once your account has them unlocked.", section = otherTransportationOptions, position = 9)
    default boolean skillCapesEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useQuestRelatedItems", name = "Use: Quest Related Items", description = "Allow quest reward item teleports once your account has them unlocked.", section = otherTransportationOptions, position = 10)
    default boolean questRelatedItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useOtherItems", name = "Use: Other Items", description = "Allow other item-based transport routes once item routing is rebuilt.", section = otherTransportationOptions, position = 11)
    default boolean otherItemsEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "useWildernessTransports", name = "Use: Wilderness Transports", description = "Allow dangerous Wilderness lever and obelisk route edges.", section = otherTransportationOptions, position = 12)
    default boolean wildernessTransportsEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "pathColor", name = "Path Colour", description = "Colour used for Drew's route overlay. Default: Burgundy (#800020).", section = waypointSettings, position = 0)
    default Color pathColor()
    {
        return new Color(0x800020);
    }

    @ConfigItem(keyName = "waypoint1PathColor", name = "Waypoint #1", description = "Marker colour for waypoint #1. Default: Dark Gray (#A9A9A9).", section = waypointSettings, position = 1)
    default Color waypoint1PathColor()
    {
        return new Color(0xA9A9A9);
    }

    @ConfigItem(keyName = "waypoint2PathColor", name = "Waypoint #2", description = "Marker colour for waypoint #2. Default: Blue (#0072B2).", section = waypointSettings, position = 2)
    default Color waypoint2PathColor()
    {
        return new Color(0x0072B2);
    }

    @ConfigItem(keyName = "waypoint3PathColor", name = "Waypoint #3", description = "Marker colour for waypoint #3. Default: Green/Teal (#009E73).", section = waypointSettings, position = 3)
    default Color waypoint3PathColor()
    {
        return new Color(0x009E73);
    }

    @ConfigItem(keyName = "waypoint4PathColor", name = "Waypoint #4", description = "Marker colour for waypoint #4. Default: Magenta/Purple (#CC79A7).", section = waypointSettings, position = 4)
    default Color waypoint4PathColor()
    {
        return new Color(0xCC79A7);
    }

    @ConfigItem(keyName = "waypoint5PathColor", name = "Waypoint #5", description = "Marker colour for waypoint #5. Default: Orange (#E69F00).", section = waypointSettings, position = 5)
    default Color waypoint5PathColor()
    {
        return new Color(0xE69F00);
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
        return wildernessTransportsEnabled();
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
        return wildernessTransportsEnabled();
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
