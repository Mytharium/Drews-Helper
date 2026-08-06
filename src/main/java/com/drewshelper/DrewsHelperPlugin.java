package com.drewshelper;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Drew's Helper",
    description = "Pathing and teleport helper for RuneLite.",
    tags = {"pathing", "route", "teleport", "quest", "helper"}
)
public class DrewsHelperPlugin extends Plugin
{
    private static final int TRANSPORT_FEED_REQUEST_INTERVAL_TICKS = 100;

    @Inject
    private DrewsHelperConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DrewsHelperOverlay overlay;

    @Inject
    private RouteTransportState routeTransportState;

    @Inject
    private ShortestPathBridge shortestPathBridge;

    private int gameTicks;

    @Override
    protected void startUp()
    {
        gameTicks = 0;
        routeTransportState.clear();
        overlayManager.add(overlay);
        requestTransportFeedIfEnabled();
        log.debug("Drew's Helper started: {}", getEnabledFeatureSummary());
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        routeTransportState.clear();
        log.debug("Drew's Helper stopped");
    }

    @Subscribe
    public void onPluginMessage(PluginMessage event)
    {
        shortestPathBridge.parseTransportMessage(event).ifPresent(routeTransportState::update);
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        gameTicks++;
        if (gameTicks % TRANSPORT_FEED_REQUEST_INTERVAL_TICKS == 0)
        {
            requestTransportFeedIfEnabled();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if ("drewshelper".equals(event.getGroup()))
        {
            requestTransportFeedIfEnabled();
        }
    }

    boolean isPathingReplacementEnabled()
    {
        return config.pathingReplacementEnabled();
    }

    private void requestTransportFeedIfEnabled()
    {
        if (config.pathingReplacementEnabled() || config.teleportAssistEnabled())
        {
            shortestPathBridge.requestTransportFeed();
        }
    }

    String getEnabledFeatureSummary()
    {
        return "pathing=" + config.pathingReplacementEnabled()
            + ", teleportAssist=" + config.teleportAssistEnabled()
            + ", filterUnavailableTeleports=" + config.filterUnavailableTeleports()
            + ", cooldownAwareReroute=" + config.cooldownAwareReroute()
            + ", hostedPohTeleports=" + config.hostedPohTeleports()
            + ", spiritTreesUnlocked=" + config.spiritTreesUnlocked()
            + ", fairyRingsUnlocked=" + config.fairyRingsUnlocked()
            + ", pohMountedGloryUnlocked=" + config.pohMountedGloryUnlocked()
            + ", pohPortalChamberUnlocked=" + config.pohPortalChamberUnlocked()
            + ", pohPortalNexusUnlocked=" + config.pohPortalNexusUnlocked()
            + ", pohJewelryBoxTier=" + config.pohJewelryBoxTier()
            + ", questPrepRouting=" + config.questPrepRouting()
            + ", questPreparationBank=" + config.questPreparationBank()
            + ", questPreparationGeneralStores=" + config.questPreparationGeneralStores()
            + ", questPreparationGrandExchange=" + config.questPreparationGrandExchange()
            + ", chainQuests=" + config.chainQuests();
    }

    @Provides
    DrewsHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }
}
