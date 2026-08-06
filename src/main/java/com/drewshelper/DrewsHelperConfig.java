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

    @ConfigItem(keyName = "hostedPohTeleports", name = "Use: Hosted POH", description = "Allow routing through advertised player-owned houses when your own account does not have the needed house teleport feature.", section = teleportOptions, position = 3)
    default boolean hostedPohTeleports()
    {
        return false;
    }

    @ConfigItem(keyName = "spiritTreesUnlocked", name = "Unlocked: Spirit Trees", description = "Allow spirit tree routes.", section = teleportOptions, position = 4)
    default boolean spiritTreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "fairyRingsUnlocked", name = "Unlocked: Fairy Rings", description = "Allow fairy ring routes.", section = teleportOptions, position = 5)
    default boolean fairyRingsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohMountedGloryUnlocked", name = "Unlocked: Mounted Glory", description = "Allow routes through your own player-owned house mounted amulet of glory.", section = teleportOptions, position = 6)
    default boolean pohMountedGloryUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohPortalChamberUnlocked", name = "Unlocked: Portal Chamber", description = "Allow routes through your own player-owned house portal chamber.", section = teleportOptions, position = 7)
    default boolean pohPortalChamberUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohPortalNexusUnlocked", name = "Unlocked: Portal Nexus", description = "Allow routes through your own player-owned house portal nexus.", section = teleportOptions, position = 8)
    default boolean pohPortalNexusUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohJewelryBoxTier", name = "Unlocked: Jewellery Box", description = "Select the highest jewellery box tier unlocked in your own player-owned house.", section = teleportOptions, position = 9)
    default JewelleryBoxTier pohJewelryBoxTier()
    {
        return JewelleryBoxTier.NONE;
    }
}
