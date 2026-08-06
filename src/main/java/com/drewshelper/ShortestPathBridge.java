package com.drewshelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

@Singleton
final class ShortestPathBridge
{
    private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";
    private static final String PATH_ACTION = "path";
    private static final String TRANSPORTS_ACTION = "transports";
    private static final String START_KEY = "start";
    private static final String TARGET_KEY = "target";
    private static final String CONFIG_KEY = "config";
    private static final String POST_TRANSPORTS_KEY = "postTransports";
    private static final String USE_POH_KEY = "usePoh";
    private static final String USE_POH_MOUNTED_ITEMS_KEY = "usePohMountedItems";
    private static final String USE_POH_PORTALS_KEY = "useTeleportationPortalsPoh";
    private static final String POH_JEWELLERY_BOX_TIER_KEY = "pohJewelleryBoxTier";
    private static final String USE_FAIRY_RINGS_KEY = "useFairyRings";
    private static final String USE_SPIRIT_TREES_KEY = "useSpiritTrees";
    private static final String OBJECT_INFO_KEY = "objectInfo";
    private static final String DISPLAY_INFO_KEY = "displayInfo";

    private final Client client;
    private final EventBus eventBus;

    @Inject
    ShortestPathBridge(Client client, EventBus eventBus)
    {
        this.client = client;
        this.eventBus = eventBus;
    }

    void requestTransportFeed(DrewsHelperConfig config)
    {
        requestPath(config, OptionalInt.empty());
    }

    void requestPath(DrewsHelperConfig config, OptionalInt targetPacked)
    {
        Map<String, Object> data = new HashMap<>();
        data.put(CONFIG_KEY, buildConfigOverride(config));

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null)
        {
            data.put(START_KEY, localPlayer.getWorldLocation());
        }
        if (targetPacked != null && targetPacked.isPresent())
        {
            data.put(TARGET_KEY, targetPacked.getAsInt());
        }

        eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, PATH_ACTION, data));
    }

    Optional<RouteTransportSnapshot> parseTransportMessage(PluginMessage event)
    {
        return parseTransportSnapshot(event);
    }

    OptionalInt parsePathTarget(PluginMessage event)
    {
        return parsePathTargetMessage(event);
    }

    static Map<String, Object> buildConfigOverride(DrewsHelperConfig config)
    {
        Map<String, Object> configOverride = new HashMap<>();
        configOverride.put(POST_TRANSPORTS_KEY, true);

        if (config == null || !config.filterUnavailableTeleports())
        {
            return configOverride;
        }

        JewelleryBoxTier jewelleryBoxTier = config.pohJewelryBoxTier() == null
            ? JewelleryBoxTier.NONE
            : config.pohJewelryBoxTier();

        boolean useOwnedPoh = config.pohMountedGloryUnlocked()
            || config.pohPortalChamberUnlocked()
            || config.pohPortalNexusUnlocked()
            || jewelleryBoxTier != JewelleryBoxTier.NONE;

        configOverride.put(USE_POH_KEY, useOwnedPoh);
        configOverride.put(USE_POH_MOUNTED_ITEMS_KEY, config.pohMountedGloryUnlocked());
        configOverride.put(USE_POH_PORTALS_KEY, config.pohPortalChamberUnlocked() || config.pohPortalNexusUnlocked());
        configOverride.put(POH_JEWELLERY_BOX_TIER_KEY, jewelleryBoxTier.toString());
        configOverride.put(USE_FAIRY_RINGS_KEY, config.fairyRingsUnlocked());
        configOverride.put(USE_SPIRIT_TREES_KEY, config.spiritTreesUnlocked());

        return configOverride;
    }

    static Optional<RouteTransportSnapshot> parseTransportSnapshot(PluginMessage event)
    {
        if (!SHORTEST_PATH_NAMESPACE.equals(event.getNamespace()) || !TRANSPORTS_ACTION.equals(event.getName()))
        {
            return Optional.empty();
        }

        Map<String, Object> data = event.getData();
        if (data == null)
        {
            return Optional.of(RouteTransportSnapshot.EMPTY);
        }

        List<?> objectInfos = listValue(data.get(OBJECT_INFO_KEY));
        List<?> displayInfos = listValue(data.get(DISPLAY_INFO_KEY));
        List<?> destinations = listValue(data.get("destination"));
        int count = Math.max(objectInfos.size(), displayInfos.size());

        List<RouteTransport> transports = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            transports.add(new RouteTransport(
                stringAt(objectInfos, i),
                stringAt(displayInfos, i),
                packedWorldPointAt(destinations, i)));
        }

        return Optional.of(new RouteTransportSnapshot(transports));
    }

    static OptionalInt parsePathTargetMessage(PluginMessage event)
    {
        if (!SHORTEST_PATH_NAMESPACE.equals(event.getNamespace()) || !PATH_ACTION.equals(event.getName()))
        {
            return OptionalInt.empty();
        }

        Map<String, Object> data = event.getData();
        if (data == null)
        {
            return OptionalInt.empty();
        }

        return packedWorldPoint(data.get(TARGET_KEY));
    }

    private static List<?> listValue(Object value)
    {
        if (value instanceof List<?>)
        {
            return (List<?>) value;
        }

        return Collections.emptyList();
    }

    private static String stringAt(List<?> values, int index)
    {
        if (index >= values.size())
        {
            return "";
        }

        Object value = values.get(index);
        return value == null ? "" : String.valueOf(value);
    }

    private static int packedWorldPointAt(List<?> values, int index)
    {
        if (index >= values.size())
        {
            return -1;
        }

        return packedWorldPoint(values.get(index)).orElse(-1);
    }

    private static OptionalInt packedWorldPoint(Object value)
    {
        if (value instanceof Integer)
        {
            int packedPoint = (Integer) value;
            return packedPoint == -1 ? OptionalInt.empty() : OptionalInt.of(packedPoint);
        }

        if (value instanceof WorldPoint)
        {
            return OptionalInt.of(packWorldPoint((WorldPoint) value));
        }

        if (value instanceof Iterable<?>)
        {
            for (Object item : (Iterable<?>) value)
            {
                OptionalInt packedPoint = packedWorldPoint(item);
                if (packedPoint.isPresent())
                {
                    return packedPoint;
                }
            }
        }

        return OptionalInt.empty();
    }

    private static int packWorldPoint(WorldPoint point)
    {
        return (point.getX() & 0x7FFF)
            | ((point.getY() & 0x7FFF) << 15)
            | ((point.getPlane() & 0x3) << 30);
    }
}
