package com.drewshelper;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
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
    private static final int TRANSPORT_FEED_REFRESH_BURST_TICKS = 10;

    @Inject
    private DrewsHelperConfig config;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DrewsHelperOverlay overlay;

    @Inject
    private TeleportHighlightOverlay teleportHighlightOverlay;

    @Inject
    private RouteTransportState routeTransportState;

    @Inject
    private ShortestPathBridge shortestPathBridge;

    @Inject
    private MinigameTeleportUnlockState minigameTeleportUnlockState;

    @Inject
    private DrewsHelperSessionState sessionState;

    private int gameTicks;
    private int routeRefreshBurstTicks;

    @Override
    protected void startUp()
    {
        gameTicks = 0;
        routeRefreshBurstTicks = 0;
        routeTransportState.update(sessionState.loadRouteSnapshot());
        minigameTeleportUnlockState.restore(sessionState.loadMinigameStatuses());
        overlayManager.remove(teleportHighlightOverlay);
        overlayManager.remove(overlay);
        overlayManager.add(overlay);
        overlayManager.add(teleportHighlightOverlay);
        scheduleRouteRefreshBurst();
        log.debug("Drew's Helper started: {}", getEnabledFeatureSummary());
    }

    @Override
    protected void shutDown()
    {
        sessionState.saveRouteSnapshot(routeTransportState.getSnapshot());
        sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
        overlayManager.remove(teleportHighlightOverlay);
        overlayManager.remove(overlay);
        log.debug("Drew's Helper stopped");
    }

    @Subscribe
    public void onPluginMessage(PluginMessage event)
    {
        shortestPathBridge.parseTransportMessage(event).ifPresent(snapshot ->
        {
            routeTransportState.update(snapshot);
            sessionState.saveRouteSnapshot(snapshot);
        });
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        gameTicks++;
        if (minigameTeleportUnlockState.scanVisibleInterface(client))
        {
            sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
        }
        if (routeRefreshBurstTicks > 0)
        {
            routeRefreshBurstTicks--;
            requestTransportFeedIfEnabled();
            return;
        }
        if (gameTicks % TRANSPORT_FEED_REQUEST_INTERVAL_TICKS == 0)
        {
            requestTransportFeedIfEnabled();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            scheduleRouteRefreshBurst();
            return;
        }

        if (event.getGameState() == GameState.LOGIN_SCREEN
            || event.getGameState() == GameState.HOPPING
            || event.getGameState() == GameState.CONNECTION_LOST)
        {
            sessionState.saveRouteSnapshot(routeTransportState.getSnapshot());
            sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if ("drewshelper".equals(event.getGroup()))
        {
            scheduleRouteRefreshBurst();
        }
    }

    boolean isPathingReplacementEnabled()
    {
        return config.pathingReplacementEnabled();
    }

    private void requestTransportFeedIfEnabled()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (config.pathingReplacementEnabled() || config.teleportAssistEnabled())
        {
            try
            {
                shortestPathBridge.requestTransportFeed(config);
            }
            catch (RuntimeException ex)
            {
                log.warn("Unable to request Shortest Path transport feed", ex);
            }
        }
    }

    private void scheduleRouteRefreshBurst()
    {
        routeRefreshBurstTicks = Math.max(routeRefreshBurstTicks, TRANSPORT_FEED_REFRESH_BURST_TICKS);
        requestTransportFeedIfEnabled();
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
