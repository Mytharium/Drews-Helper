package com.drewshelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

@Singleton
final class ShortestPathBridge
{
    private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";
    private static final String PATH_ACTION = "path";
    private static final String TRANSPORTS_ACTION = "transports";
    private static final String CONFIG_KEY = "config";
    private static final String POST_TRANSPORTS_KEY = "postTransports";
    private static final String OBJECT_INFO_KEY = "objectInfo";
    private static final String DISPLAY_INFO_KEY = "displayInfo";

    private final EventBus eventBus;

    @Inject
    ShortestPathBridge(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    void requestTransportFeed()
    {
        Map<String, Object> configOverride = new HashMap<>();
        configOverride.put(POST_TRANSPORTS_KEY, true);

        Map<String, Object> data = new HashMap<>();
        data.put(CONFIG_KEY, configOverride);

        eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, PATH_ACTION, data));
    }

    Optional<RouteTransportSnapshot> parseTransportMessage(PluginMessage event)
    {
        return parseTransportSnapshot(event);
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
        int count = Math.max(objectInfos.size(), displayInfos.size());

        List<RouteTransport> transports = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            transports.add(new RouteTransport(stringAt(objectInfos, i), stringAt(displayInfos, i)));
        }

        return Optional.of(new RouteTransportSnapshot(transports));
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
}
