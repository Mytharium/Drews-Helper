package com.drewshelper;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
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
    private TeleportAvailabilityService teleportAvailabilityService;

    @Inject
    private DrewsHelperSessionState sessionState;

    private int gameTicks;
    private int routeRefreshBurstTicks;
    private boolean replaySavedTargetDuringBurst;
    private String lastLockedRouteRerouteSignature;

    @Override
    protected void startUp()
    {
        gameTicks = 0;
        routeRefreshBurstTicks = 0;
        lastLockedRouteRerouteSignature = "";
        routeTransportState.update(sessionState.loadRouteSnapshot());
        minigameTeleportUnlockState.restore(sessionState.loadMinigameStatuses());
        overlayManager.remove(teleportHighlightOverlay);
        overlayManager.remove(overlay);
        overlayManager.add(overlay);
        overlayManager.add(teleportHighlightOverlay);
        scheduleRouteRefreshBurst(true);
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
        OptionalInt pathTarget = shortestPathBridge.parsePathTarget(event);
        if (pathTarget.isPresent())
        {
            sessionState.saveShortestPathTarget(pathTarget.getAsInt());
        }

        shortestPathBridge.parseTransportMessage(event).ifPresent(snapshot ->
        {
            routeTransportState.update(snapshot);
            sessionState.saveRouteSnapshot(snapshot);
            requestLockedRouteReroute(snapshot);
        });
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        gameTicks++;
        if (minigameTeleportUnlockState.scanVisibleInterface(client))
        {
            sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
            lastLockedRouteRerouteSignature = "";
            scheduleRouteRefreshBurst(true);
            return;
        }
        if (routeRefreshBurstTicks > 0)
        {
            routeRefreshBurstTicks--;
            requestTransportFeedIfEnabled(replaySavedTargetDuringBurst);
            if (routeRefreshBurstTicks == 0)
            {
                replaySavedTargetDuringBurst = false;
            }
            return;
        }
        if (gameTicks % TRANSPORT_FEED_REQUEST_INTERVAL_TICKS == 0)
        {
            requestTransportFeedIfEnabled(false);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            scheduleRouteRefreshBurst(true);
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
            lastLockedRouteRerouteSignature = "";
            scheduleRouteRefreshBurst(false);
        }
    }

    boolean isPathingReplacementEnabled()
    {
        return config.pathingReplacementEnabled();
    }

    private void requestTransportFeedIfEnabled(boolean includeSavedTarget)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (config.pathingReplacementEnabled() || config.teleportAssistEnabled())
        {
            try
            {
                OptionalInt target = includeSavedTarget ? getRouteReplayTarget() : OptionalInt.empty();
                Set<String> blockedTransportKeys = teleportAvailabilityService.getBlockedTransportKeys(config);
                if (target.isPresent())
                {
                    shortestPathBridge.requestPath(config, target, blockedTransportKeys);
                }
                else
                {
                    shortestPathBridge.requestTransportFeed(config, blockedTransportKeys);
                }
            }
            catch (RuntimeException ex)
            {
                log.warn("Unable to request Shortest Path transport feed", ex);
            }
        }
    }

    private void requestLockedRouteReroute(RouteTransportSnapshot snapshot)
    {
        if (client.getGameState() != GameState.LOGGED_IN
            || !config.filterUnavailableTeleports()
            || !teleportAvailabilityService.getFirstUnavailable(snapshot, config).isPresent())
        {
            return;
        }

        Set<String> blockedTransportKeys = teleportAvailabilityService.getBlockedTransportKeys(config);
        if (blockedTransportKeys.isEmpty())
        {
            return;
        }

        OptionalInt target = getRouteReplayTarget();
        String rerouteSignature = buildRerouteSignature(target, blockedTransportKeys);
        if (rerouteSignature.equals(lastLockedRouteRerouteSignature))
        {
            return;
        }

        lastLockedRouteRerouteSignature = rerouteSignature;
        if (target.isPresent())
        {
            shortestPathBridge.requestPath(config, target, blockedTransportKeys);
        }
        else
        {
            shortestPathBridge.requestTransportFeed(config, blockedTransportKeys);
        }
    }

    private void scheduleRouteRefreshBurst(boolean replaySavedTarget)
    {
        routeRefreshBurstTicks = Math.max(routeRefreshBurstTicks, TRANSPORT_FEED_REFRESH_BURST_TICKS);
        replaySavedTargetDuringBurst = replaySavedTargetDuringBurst || replaySavedTarget;
        requestTransportFeedIfEnabled(replaySavedTarget);
    }

    private OptionalInt getRouteReplayTarget()
    {
        OptionalInt savedTarget = sessionState.loadShortestPathTarget();
        if (savedTarget.isPresent())
        {
            return savedTarget;
        }

        return routeTransportState.getSnapshot().getLastTransportDestinationPacked();
    }

    private static String buildRerouteSignature(OptionalInt target, Set<String> blockedTransportKeys)
    {
        List<String> sortedKeys = new ArrayList<>(blockedTransportKeys);
        Collections.sort(sortedKeys);
        return (target.isPresent() ? String.valueOf(target.getAsInt()) : "current")
            + "|" + String.join(",", sortedKeys);
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
