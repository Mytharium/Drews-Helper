package com.drewshelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
final class DrewsHelperSessionState
{
    private static final String CONFIG_GROUP = "drewshelper";
    private static final String LAST_ROUTE_KEY = "lastRouteSnapshot";
    private static final String MINIGAME_STATUS_KEY = "minigameTeleportStatuses";
    private static final int MAX_SAVED_TRANSPORTS = 32;

    private final ConfigManager configManager;

    @Inject
    DrewsHelperSessionState(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    RouteTransportSnapshot loadRouteSnapshot()
    {
        return decodeRouteSnapshot(configManager.getConfiguration(CONFIG_GROUP, LAST_ROUTE_KEY));
    }

    void saveRouteSnapshot(RouteTransportSnapshot snapshot)
    {
        if (snapshot == null || snapshot.isEmpty())
        {
            return;
        }

        configManager.setConfiguration(CONFIG_GROUP, LAST_ROUTE_KEY, encodeRouteSnapshot(snapshot));
    }

    Map<String, MinigameTeleportStatus> loadMinigameStatuses()
    {
        return decodeMinigameStatuses(configManager.getConfiguration(CONFIG_GROUP, MINIGAME_STATUS_KEY));
    }

    void saveMinigameStatuses(Map<String, MinigameTeleportStatus> statuses)
    {
        if (statuses == null || statuses.isEmpty())
        {
            return;
        }

        configManager.setConfiguration(CONFIG_GROUP, MINIGAME_STATUS_KEY, encodeMinigameStatuses(statuses));
    }

    static String encodeRouteSnapshot(RouteTransportSnapshot snapshot)
    {
        StringJoiner rows = new StringJoiner("\n");
        List<RouteTransport> transports = snapshot == null
            ? Collections.emptyList()
            : snapshot.getTransports();
        int saved = 0;
        for (RouteTransport transport : transports)
        {
            if (!transport.hasInstruction())
            {
                continue;
            }

            rows.add(encode(transport.getObjectInfo()) + "," + encode(transport.getDisplayInfo()));
            saved++;
            if (saved >= MAX_SAVED_TRANSPORTS)
            {
                break;
            }
        }

        return rows.toString();
    }

    static RouteTransportSnapshot decodeRouteSnapshot(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return RouteTransportSnapshot.EMPTY;
        }

        List<RouteTransport> transports = new ArrayList<>();
        String[] rows = value.split("\\n");
        for (String row : rows)
        {
            String[] columns = row.split(",", -1);
            if (columns.length != 2)
            {
                continue;
            }

            try
            {
                transports.add(new RouteTransport(decode(columns[0]), decode(columns[1])));
            }
            catch (IllegalArgumentException ex)
            {
                log.debug("Ignoring invalid saved Drew's Helper route row", ex);
            }
        }

        return transports.isEmpty() ? RouteTransportSnapshot.EMPTY : new RouteTransportSnapshot(transports);
    }

    static String encodeMinigameStatuses(Map<String, MinigameTeleportStatus> statuses)
    {
        StringJoiner rows = new StringJoiner("\n");
        statuses.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> rows.add(encode(entry.getKey()) + "," + entry.getValue().name()));
        return rows.toString();
    }

    static Map<String, MinigameTeleportStatus> decodeMinigameStatuses(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<String, MinigameTeleportStatus> statuses = new HashMap<>();
        String[] rows = value.split("\\n");
        for (String row : rows)
        {
            String[] columns = row.split(",", -1);
            if (columns.length != 2)
            {
                continue;
            }

            try
            {
                statuses.put(decode(columns[0]), MinigameTeleportStatus.valueOf(columns[1]));
            }
            catch (IllegalArgumentException ex)
            {
                log.debug("Ignoring invalid saved minigame status row", ex);
            }
        }

        return statuses;
    }

    private static String encode(String value)
    {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value)
    {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
