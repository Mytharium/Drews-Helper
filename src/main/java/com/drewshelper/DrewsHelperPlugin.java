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
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import shortestpath.ShortestPathPlugin;

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
    private static final float PLUGIN_MESSAGE_PRIORITY = 1000.0f;

    @Inject
    private DrewsHelperConfig config;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private EventBus eventBus;

    @Inject
    private ShortestPathPlugin drewShortestPath;

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
    private boolean drewShortestPathStarted;
    private boolean observedActiveShortestPathTarget;
    private OptionalInt lastSyncedShortestPathTarget = OptionalInt.empty();
    private String lastExactLockedRouteRerouteSignature;

    @Override
    protected void startUp()
    {
        startDrewsShortestPathFeature();
        gameTicks = 0;
        routeRefreshBurstTicks = 0;
        observedActiveShortestPathTarget = false;
        lastSyncedShortestPathTarget = sessionState.loadShortestPathTarget();
        clearLockedRouteRerouteState();
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
        syncActiveShortestPathTarget();
        sessionState.saveRouteSnapshot(routeTransportState.getSnapshot());
        sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
        overlayManager.remove(teleportHighlightOverlay);
        overlayManager.remove(overlay);
        stopDrewsShortestPathFeature();
        log.debug("Drew's Helper stopped");
    }

    private void startDrewsShortestPathFeature()
    {
        if (drewShortestPathStarted)
        {
            return;
        }

        eventBus.register(drewShortestPath);
        try
        {
            drewShortestPath.startDrewsHelperFeature();
            drewShortestPathStarted = true;
        }
        catch (RuntimeException ex)
        {
            eventBus.unregister(drewShortestPath);
            throw ex;
        }
    }

    private void stopDrewsShortestPathFeature()
    {
        if (!drewShortestPathStarted)
        {
            return;
        }

        eventBus.unregister(drewShortestPath);
        drewShortestPath.stopDrewsHelperFeature();
        drewShortestPathStarted = false;
    }

    @Subscribe(priority = PLUGIN_MESSAGE_PRIORITY)
    public void onPluginMessage(PluginMessage event)
    {
        if (shortestPathBridge.isPathRequest(event))
        {
            OptionalInt pathTarget = shortestPathBridge.parsePathTarget(event);
            if (pathTarget.isPresent())
            {
                saveShortestPathTarget(pathTarget, shortestPathBridge.isDrewsHelperPathRequest(event));
            }
            applyShortestPathRequestPolicy(event, pathTarget);
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
        syncActiveShortestPathTarget();
        if (minigameTeleportUnlockState.scanVisibleInterface(client))
        {
            sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
            clearLockedRouteRerouteState();
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
            clearLockedRouteRerouteState();
            scheduleRouteRefreshBurst(true);
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
                    shortestPathBridge.requestPath(
                        config,
                        target,
                        blockedTransportKeys,
                        false);
                }
                else
                {
                    shortestPathBridge.requestTransportFeed(
                        config,
                        blockedTransportKeys,
                        false);
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
        if (client.getGameState() != GameState.LOGGED_IN)
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
        if (rerouteSignature.equals(lastExactLockedRouteRerouteSignature))
        {
            return;
        }

        lastExactLockedRouteRerouteSignature = rerouteSignature;
        if (target.isPresent())
        {
            shortestPathBridge.requestPath(config, target, blockedTransportKeys, false);
        }
        else
        {
            shortestPathBridge.requestTransportFeed(config, blockedTransportKeys, false);
        }
    }

    private void saveShortestPathTarget(OptionalInt pathTarget, boolean drewRequest)
    {
        if (!drewRequest && !sessionState.loadShortestPathTarget().equals(pathTarget))
        {
            clearLockedRouteRerouteState();
        }

        sessionState.saveShortestPathTarget(pathTarget.getAsInt());
        lastSyncedShortestPathTarget = pathTarget;
        observedActiveShortestPathTarget = true;
    }

    private void syncActiveShortestPathTarget()
    {
        if (!drewShortestPathStarted)
        {
            return;
        }

        OptionalInt activeTarget = drewShortestPath.getPrimaryTargetPacked();
        if (activeTarget.isPresent())
        {
            if (!activeTarget.equals(lastSyncedShortestPathTarget))
            {
                clearLockedRouteRerouteState();
                sessionState.saveShortestPathTarget(activeTarget.getAsInt());
            }

            lastSyncedShortestPathTarget = activeTarget;
            observedActiveShortestPathTarget = true;
            return;
        }

        if (!observedActiveShortestPathTarget)
        {
            return;
        }

        if (lastSyncedShortestPathTarget.isPresent())
        {
            clearLockedRouteRerouteState();
            sessionState.clearShortestPathTarget();
            sessionState.clearRouteSnapshot();
            routeTransportState.clear();
        }

        lastSyncedShortestPathTarget = OptionalInt.empty();
        observedActiveShortestPathTarget = false;
    }

    private void applyShortestPathRequestPolicy(PluginMessage event, OptionalInt target)
    {
        Set<String> blockedTransportKeys = teleportAvailabilityService.getBlockedTransportKeys(config);

        shortestPathBridge.addConfigOverrideToPathRequest(
            event,
            config,
            blockedTransportKeys,
            false);
    }

    private void scheduleRouteRefreshBurst(boolean replaySavedTarget)
    {
        routeRefreshBurstTicks = Math.max(routeRefreshBurstTicks, TRANSPORT_FEED_REFRESH_BURST_TICKS);
        replaySavedTargetDuringBurst = replaySavedTargetDuringBurst || replaySavedTarget;
        requestTransportFeedIfEnabled(replaySavedTarget);
    }

    private OptionalInt getRouteReplayTarget()
    {
        // Transport destinations are intermediate route steps, not the final path target.
        return sessionState.loadShortestPathTarget();
    }

    private void clearLockedRouteRerouteState()
    {
        lastExactLockedRouteRerouteSignature = "";
    }

    private static String buildRerouteSignature(OptionalInt target, Set<String> blockedTransportKeys)
    {
        List<String> sortedKeys = new ArrayList<>(blockedTransportKeys);
        Collections.sort(sortedKeys);
        return buildTargetSignature(target) + "|" + String.join(",", sortedKeys);
    }

    private static String buildTargetSignature(OptionalInt target)
    {
        return target.isPresent() ? String.valueOf(target.getAsInt()) : "current";
    }

    String getEnabledFeatureSummary()
    {
        return "pathing=" + config.pathingReplacementEnabled()
            + ", teleportAssist=" + config.teleportAssistEnabled()
            + ", filterUnavailableTeleports=" + config.filterUnavailableTeleports()
            + ", cooldownAwareReroute=" + config.cooldownAwareReroute()
            + ", spiritTreesUnlocked=" + config.spiritTreesUnlocked()
            + ", fairyRingsUnlocked=" + config.fairyRingsUnlocked()
            + ", pohMountedGloryUnlocked=" + config.pohMountedGloryUnlocked()
            + ", pohPortalChamberUnlocked=" + config.pohPortalChamberUnlocked()
            + ", pohPortalNexusTier=" + config.pohPortalNexusTier()
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
