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
    private static final float PLUGIN_MESSAGE_PRIORITY = 1000.0f;

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
    private String lastExactLockedRouteRerouteSignature;
    private String activeMinigameCategoryFallbackSignature;
    private int lastMinigameFallbackCorrectionTick;

    @Override
    protected void startUp()
    {
        gameTicks = 0;
        routeRefreshBurstTicks = 0;
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
        sessionState.saveRouteSnapshot(routeTransportState.getSnapshot());
        sessionState.saveMinigameStatuses(minigameTeleportUnlockState.snapshotStatuses());
        overlayManager.remove(teleportHighlightOverlay);
        overlayManager.remove(overlay);
        log.debug("Drew's Helper stopped");
    }

    @Subscribe(priority = PLUGIN_MESSAGE_PRIORITY)
    public void onPluginMessage(PluginMessage event)
    {
        OptionalInt pathTarget = shortestPathBridge.parsePathTarget(event);
        if (pathTarget.isPresent())
        {
            saveShortestPathTarget(pathTarget, shortestPathBridge.isDrewsHelperPathRequest(event));
            applyShortestPathRequestPolicy(event, pathTarget);
        }

        shortestPathBridge.parseTransportMessage(event).ifPresent(snapshot ->
        {
            if (shouldSuppressUnavailableTransportSnapshot(snapshot))
            {
                log.debug("Ignoring locked Shortest Path snapshot while Drew minigame fallback is active");
                requestActiveMinigameFallbackCorrection();
                return;
            }

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
                boolean disableMinigameTeleports = isMinigameCategoryFallbackActive(target, blockedTransportKeys);
                if (target.isPresent())
                {
                    shortestPathBridge.requestPath(
                        config,
                        target,
                        blockedTransportKeys,
                        disableMinigameTeleports);
                }
                else
                {
                    shortestPathBridge.requestTransportFeed(
                        config,
                        blockedTransportKeys,
                        disableMinigameTeleports);
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
        if (rerouteSignature.equals(activeMinigameCategoryFallbackSignature))
        {
            return;
        }

        boolean useMinigameCategoryFallback = rerouteSignature.equals(lastExactLockedRouteRerouteSignature);
        if (useMinigameCategoryFallback)
        {
            activeMinigameCategoryFallbackSignature = rerouteSignature;
        }
        else
        {
            lastExactLockedRouteRerouteSignature = rerouteSignature;
        }

        if (target.isPresent())
        {
            shortestPathBridge.requestPath(config, target, blockedTransportKeys, useMinigameCategoryFallback);
        }
        else
        {
            shortestPathBridge.requestTransportFeed(config, blockedTransportKeys, useMinigameCategoryFallback);
        }
    }

    private void requestActiveMinigameFallbackCorrection()
    {
        if (gameTicks == lastMinigameFallbackCorrectionTick)
        {
            return;
        }

        Set<String> blockedTransportKeys = teleportAvailabilityService.getBlockedTransportKeys(config);
        if (blockedTransportKeys.isEmpty())
        {
            return;
        }

        OptionalInt target = getRouteReplayTarget();
        if (!isMinigameCategoryFallbackActive(target, blockedTransportKeys))
        {
            return;
        }

        lastMinigameFallbackCorrectionTick = gameTicks;
        if (target.isPresent())
        {
            shortestPathBridge.requestPath(config, target, blockedTransportKeys, true);
        }
        else
        {
            shortestPathBridge.requestTransportFeed(config, blockedTransportKeys, true);
        }
    }

    private void saveShortestPathTarget(OptionalInt pathTarget, boolean drewRequest)
    {
        if (!drewRequest && !sessionState.loadShortestPathTarget().equals(pathTarget))
        {
            clearLockedRouteRerouteState();
        }

        sessionState.saveShortestPathTarget(pathTarget.getAsInt());
    }

    private void applyShortestPathRequestPolicy(PluginMessage event, OptionalInt target)
    {
        if (!config.filterUnavailableTeleports())
        {
            return;
        }

        Set<String> blockedTransportKeys = teleportAvailabilityService.getBlockedTransportKeys(config);
        if (blockedTransportKeys.isEmpty())
        {
            return;
        }

        boolean disableMinigameTeleports = isMinigameCategoryFallbackActive(target, blockedTransportKeys);
        shortestPathBridge.addConfigOverrideToPathRequest(
            event,
            config,
            blockedTransportKeys,
            disableMinigameTeleports);
    }

    private boolean shouldSuppressUnavailableTransportSnapshot(RouteTransportSnapshot snapshot)
    {
        if (!config.filterUnavailableTeleports()
            || !teleportAvailabilityService.getFirstUnavailable(snapshot, config).isPresent())
        {
            return false;
        }

        Set<String> blockedTransportKeys = teleportAvailabilityService.getBlockedTransportKeys(config);
        if (blockedTransportKeys.isEmpty())
        {
            return false;
        }

        return isMinigameCategoryFallbackActive(getRouteReplayTarget(), blockedTransportKeys);
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

    private boolean isMinigameCategoryFallbackActive(OptionalInt target, Set<String> blockedTransportKeys)
    {
        if (activeMinigameCategoryFallbackSignature.isEmpty())
        {
            return false;
        }

        return !target.isPresent()
            || activeMinigameCategoryFallbackSignature.equals(buildRerouteSignature(target, blockedTransportKeys));
    }

    private void clearLockedRouteRerouteState()
    {
        lastExactLockedRouteRerouteSignature = "";
        activeMinigameCategoryFallbackSignature = "";
        lastMinigameFallbackCorrectionTick = -1;
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
