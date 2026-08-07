package com.drewshelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import shortestpath.TeleportationItem;

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
        name = "Transportation",
        description = "Regular travel networks, shortcuts, ships, carts, and pass-through routes.",
        position = 2,
        closedByDefault = true
    )
    String transportationOptions = "transportationOptions";

    @ConfigSection(
        name = "Advanced Transportation",
        description = "Account unlocks, teleports, minigames, and player-owned house routing.",
        position = 3,
        closedByDefault = true
    )
    String advancedTransportationOptions = "advancedTransportationOptions";

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

    @ConfigItem(keyName = "filterUnavailableTeleports", name = "Hide Locked Teleports", description = "Filter teleports when requirements are missing.", section = teleportOptions, position = 1)
    default boolean filterUnavailableTeleports()
    {
        return true;
    }

    @ConfigItem(keyName = "cooldownAwareReroute", name = "Cooldown Reroute", description = "Recalculate when a teleport is cooling down.", section = teleportOptions, position = 2)
    default boolean cooldownAwareReroute()
    {
        return true;
    }

    @ConfigItem(keyName = "useTransports", name = "Unlocked: Gates & Passages", description = "Allow normal pass-through routes such as gates, doors, tunnels, and paid local passages.", section = transportationOptions, position = 0)
    default boolean gatesAndPassagesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useAgilityShortcuts", name = "Unlocked: Agility Shortcuts", description = "Allow agility shortcuts when your skill and quest requirements are met.", section = transportationOptions, position = 1)
    default boolean agilityShortcutsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useGrappleShortcuts", name = "Unlocked: Grapple Shortcuts", description = "Allow crossbow grapple shortcuts when your agility, ranged, and strength requirements are met.", section = transportationOptions, position = 2)
    default boolean grappleShortcutsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useBoats", name = "Unlocked: Boats", description = "Allow small boat routes.", section = transportationOptions, position = 3)
    default boolean boatsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useCanoes", name = "Unlocked: Canoes", description = "Allow canoe routes.", section = transportationOptions, position = 4)
    default boolean canoesUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useCharterShips", name = "Unlocked: Charter Ships", description = "Allow charter ship routes, including routes that require paying the fare.", section = transportationOptions, position = 5)
    default boolean charterShipsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useShips", name = "Unlocked: Passenger Ships", description = "Allow passenger ship routes.", section = transportationOptions, position = 6)
    default boolean passengerShipsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useGnomeGliders", name = "Unlocked: Gnome Gliders", description = "Allow gnome glider routes.", section = transportationOptions, position = 7)
    default boolean gnomeGlidersUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useHotAirBalloons", name = "Unlocked: Hot Air Balloons", description = "Allow hot air balloon routes.", section = transportationOptions, position = 8)
    default boolean hotAirBalloonsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useMagicCarpets", name = "Unlocked: Magic Carpets", description = "Allow magic carpet routes.", section = transportationOptions, position = 9)
    default boolean magicCarpetsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useMagicMushtrees", name = "Unlocked: Magic Mushtrees", description = "Allow Fossil Island magic mushtree routes.", section = transportationOptions, position = 10)
    default boolean magicMushtreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useMinecarts", name = "Unlocked: Minecarts", description = "Allow minecart network routes.", section = transportationOptions, position = 11)
    default boolean minecartsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useQuetzals", name = "Unlocked: Quetzals", description = "Allow quetzal and quetzal whistle routes.", section = transportationOptions, position = 12)
    default boolean quetzalsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "hostedPohTeleports", name = "Use: Hosted POH", description = "Allow routing through advertised player-owned houses when your own account does not have the needed house teleport feature.", section = advancedTransportationOptions, position = 0)
    default boolean hostedPohTeleports()
    {
        return false;
    }

    @ConfigItem(keyName = "spiritTreesUnlocked", name = "Unlocked: Spirit Trees", description = "Allow spirit tree routes.", section = advancedTransportationOptions, position = 1)
    default boolean spiritTreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "fairyRingsUnlocked", name = "Unlocked: Fairy Rings", description = "Allow fairy ring routes.", section = advancedTransportationOptions, position = 2)
    default boolean fairyRingsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "useTeleportationItems", name = "Unlocked: Teleport Items", description = "Select which teleport items Drew may use for routing.", section = advancedTransportationOptions, position = 3)
    default TeleportationItem teleportationItemsUnlocked()
    {
        return TeleportationItem.INVENTORY_NON_CONSUMABLE;
    }

    @ConfigItem(keyName = "useTeleportationLevers", name = "Unlocked: Teleport Levers", description = "Allow teleport lever routes.", section = advancedTransportationOptions, position = 4)
    default boolean teleportationLeversUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useTeleportationPortals", name = "Unlocked: Teleport Portals", description = "Allow fixed teleport portal routes, such as Ferox Enclave portals.", section = advancedTransportationOptions, position = 5)
    default boolean teleportationPortalsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useTeleportationSpells", name = "Unlocked: Teleport Spells", description = "Allow regular spellbook teleport routes.", section = advancedTransportationOptions, position = 6)
    default boolean teleportationSpellsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useTeleportationSpellsHome", name = "Unlocked: Home Teleports", description = "Allow home teleport spell routes.", section = advancedTransportationOptions, position = 7)
    default boolean homeTeleportsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useTeleportationMinigames", name = "Unlocked: Minigame Teleports", description = "Allow minigame, activity, and grouping teleports. Locked scanned destinations are still filtered when Hide Locked Teleports is enabled.", section = advancedTransportationOptions, position = 8)
    default boolean minigameTeleportsUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useWildernessObelisks", name = "Unlocked: Wilderness Obelisks", description = "Allow wilderness obelisk routes.", section = advancedTransportationOptions, position = 9)
    default boolean wildernessObelisksUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "useSeasonalTransports", name = "Unlocked: Seasonal Transports", description = "Allow seasonal or temporary transport routes when the data exists.", section = advancedTransportationOptions, position = 10)
    default boolean seasonalTransportsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohFairyRingUnlocked", name = "Unlocked: POH Fairy Ring", description = "Allow routes through your own player-owned house fairy ring.", section = advancedTransportationOptions, position = 11)
    default boolean pohFairyRingUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohSpiritTreeUnlocked", name = "Unlocked: POH Spirit Tree", description = "Allow routes through your own player-owned house spirit tree.", section = advancedTransportationOptions, position = 12)
    default boolean pohSpiritTreeUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohMountedGloryUnlocked", name = "Unlocked: Mounted Glory", description = "Allow routes through your own player-owned house mounted amulet of glory.", section = advancedTransportationOptions, position = 13)
    default boolean pohMountedGloryUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohPortalChamberUnlocked", name = "Unlocked: Portal Chamber", description = "Allow routes through your own player-owned house portal chamber.", section = advancedTransportationOptions, position = 14)
    default boolean pohPortalChamberUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohPortalNexusUnlocked", name = "Unlocked: Portal Nexus", description = "Allow routes through your own player-owned house portal nexus.", section = advancedTransportationOptions, position = 15)
    default boolean pohPortalNexusUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohJewelryBoxTier", name = "Unlocked: Jewellery Box", description = "Select the highest jewellery box tier unlocked in your own player-owned house.", section = advancedTransportationOptions, position = 16)
    default JewelleryBoxTier pohJewelryBoxTier()
    {
        return JewelleryBoxTier.NONE;
    }

    @ConfigItem(keyName = "pohObeliskUnlocked", name = "Unlocked: POH Obelisk", description = "Allow routes through your own player-owned house wilderness obelisk.", section = advancedTransportationOptions, position = 17)
    default boolean pohObeliskUnlocked()
    {
        return false;
    }
}
