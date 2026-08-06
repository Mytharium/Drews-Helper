package com.drewshelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("drewshelper")
public interface DrewsHelperConfig extends Config
{
    @ConfigItem(keyName = "pathingReplacementEnabled", name = "Use Drew's pathing", description = "Own route decisions instead of stock Shortest Path.", position = 0)
    default boolean pathingReplacementEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "preferLocalExits", name = "Prefer nearby exits", description = "Prefer nearby exits before global teleports.", position = 1)
    default boolean preferLocalExits()
    {
        return true;
    }

    @ConfigItem(keyName = "teleportAssistEnabled", name = "Teleport highlighter", description = "Highlight the selected route teleport UI.", position = 2)
    default boolean teleportAssistEnabled()
    {
        return true;
    }

    @ConfigItem(keyName = "filterUnavailableTeleports", name = "Hide locked teleports", description = "Filter teleports when requirements are missing.", position = 3)
    default boolean filterUnavailableTeleports()
    {
        return true;
    }

    @ConfigItem(keyName = "cooldownAwareReroute", name = "Reroute cooldowns", description = "Recalculate when a teleport is cooling down.", position = 4)
    default boolean cooldownAwareReroute()
    {
        return true;
    }

    @ConfigItem(keyName = "questPrepRouting", name = "Quest prep routing", description = "Route to prep when Quest Helper needs items.", position = 5)
    default boolean questPrepRouting()
    {
        return false;
    }

    @ConfigItem(keyName = "grandExchangePrepRouting", name = "GE for buyables", description = "Route to GE for missing tradeable quest items.", position = 6)
    default boolean grandExchangePrepRouting()
    {
        return false;
    }

    @ConfigItem(keyName = "fairyRingsUnlocked", name = "Fairy rings", description = "Allow fairy ring routes.", position = 7)
    default boolean fairyRingsUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "spiritTreesUnlocked", name = "Spirit trees", description = "Allow spirit tree routes.", position = 8)
    default boolean spiritTreesUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohJewelryBoxUnlocked", name = "POH jewelry box", description = "Allow POH jewelry box routes.", position = 9)
    default boolean pohJewelryBoxUnlocked()
    {
        return false;
    }

    @ConfigItem(keyName = "pohMountedGloryUnlocked", name = "POH mounted glory", description = "Allow POH mounted glory routes.", position = 10)
    default boolean pohMountedGloryUnlocked()
    {
        return false;
    }
}
