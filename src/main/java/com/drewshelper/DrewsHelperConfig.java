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

    @ConfigItem(keyName = "pathingReplacementEnabled", name = "Drew's Pathing", description = "Own route decisions instead of stock Shortest Path.", section = routingOptions, position = 0)
    default boolean pathingReplacementEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "preferLocalExits", name = "Nearby Exits", description = "Prefer nearby exits before global teleports.", section = routingOptions, position = 1)
    default boolean preferLocalExits()
    {
        return true;
    }

    @ConfigItem(keyName = "questPrepRouting", name = "Quest Prep", description = "Route to prep before quest locations when Quest Helper needs items.", section = routingOptions, position = 2)
    default boolean questPrepRouting()
    {
        return false;
    }

    @ConfigItem(keyName = "questPrepDestination", name = "Quest Prep Destination", description = "Choose where quest prep should route for missing required items.", section = routingOptions, position = 3)
    default QuestPrepDestination questPrepDestination()
    {
        return QuestPrepDestination.GENERAL_STORES;
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

    @ConfigItem(keyName = "cooldownAwareReroute", name = "Reroute Cooldowns", description = "Recalculate when a teleport is cooling down.", section = teleportOptions, position = 2)
    default boolean cooldownAwareReroute()
    {
        return true;
    }

    @ConfigItem(keyName = "spiritTreesUnlocked", name = "Unlocked: Spirit Trees", description = "Allow spirit tree routes.", section = teleportOptions, position = 3)
    default boolean spiritTreesUnlocked()
    {
        return true;
    }

    @ConfigItem(keyName = "fairyRingsUnlocked", name = "Unlocked: Fairy Rings", description = "Allow fairy ring routes.", section = teleportOptions, position = 4)
    default boolean fairyRingsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohMountedGloryUnlocked", name = "Unlocked: POH Mounted Glory", description = "Allow POH mounted glory routes.", section = teleportOptions, position = 5)
    default boolean pohMountedGloryUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohJewelryBoxUnlocked", name = "Unlocked: POH Jewelry Box", description = "Allow POH jewelry box routes.", section = teleportOptions, position = 6)
    default boolean pohJewelryBoxUnlocked()
    {
        return false;
    }
}
