package com.drewshelper;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
    name = "Drew's Helper",
    description = "Pathing and teleport helper for RuneLite.",
    tags = {"pathing", "route", "teleport", "quest", "helper"}
)
public class DrewsHelperPlugin extends Plugin
{
    @Inject
    private DrewsHelperConfig config;

    @Override
    protected void startUp()
    {
        log.debug("Drew's Helper started: {}", getEnabledFeatureSummary());
    }

    @Override
    protected void shutDown()
    {
        log.debug("Drew's Helper stopped");
    }

    boolean isPathingReplacementEnabled()
    {
        return config.pathingReplacementEnabled();
    }

    String getEnabledFeatureSummary()
    {
        return "pathing=" + config.pathingReplacementEnabled()
            + ", teleportAssist=" + config.teleportAssistEnabled()
            + ", filterUnavailableTeleports=" + config.filterUnavailableTeleports()
            + ", cooldownAwareReroute=" + config.cooldownAwareReroute()
            + ", preferLocalExits=" + config.preferLocalExits()
            + ", questPrepRouting=" + config.questPrepRouting()
            + ", questPrepDestination=" + config.questPrepDestination();
    }

    @Provides
    DrewsHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }
}
