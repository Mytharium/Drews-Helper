package com.drewshelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

@Singleton
final class MinigameTeleportUnlockState
{
    private final Map<String, MinigameTeleportStatus> statuses = new ConcurrentHashMap<>();
    private volatile int lastScanRows;

    @Inject
    MinigameTeleportUnlockState()
    {
    }

    void clear()
    {
        statuses.clear();
        lastScanRows = 0;
    }

    boolean scanVisibleInterface(Client client)
    {
        if (client == null)
        {
            return false;
        }

        int scanned = 0;
        boolean changed = false;
        for (Widget row : MinigameTeleportWidgets.findVisibleDestinationWidgets(client))
        {
            String destination = MinigameTeleportNames.destinationName(row);
            String key = MinigameTeleportNames.normalize(destination);
            if (key.isEmpty())
            {
                continue;
            }

            MinigameTeleportStatus status = inferStatus(row, client);
            changed |= recordNormalized(key, status);
            scanned++;
        }

        if (scanned > 0)
        {
            lastScanRows = scanned;
        }
        return changed;
    }

    MinigameTeleportStatus getStatus(RouteTransport transport)
    {
        String key = MinigameTeleportNames.destinationKey(transport);
        if (key.isEmpty())
        {
            return MinigameTeleportStatus.UNKNOWN;
        }

        MinigameTeleportStatus exact = statuses.get(key);
        if (exact != null)
        {
            return exact;
        }

        for (Map.Entry<String, MinigameTeleportStatus> entry : statuses.entrySet())
        {
            String scannedKey = entry.getKey();
            if (key.contains(scannedKey) || scannedKey.contains(key))
            {
                return entry.getValue();
            }
        }

        return MinigameTeleportStatus.UNKNOWN;
    }

    void record(String destination, MinigameTeleportStatus status)
    {
        String key = MinigameTeleportNames.normalize(destination);
        if (!key.isEmpty() && status != null)
        {
            recordNormalized(key, status);
        }
    }

    int getKnownDestinationCount()
    {
        return statuses.size();
    }

    int getLastScanRows()
    {
        return lastScanRows;
    }

    Map<String, MinigameTeleportStatus> snapshotStatuses()
    {
        return Collections.unmodifiableMap(new HashMap<>(statuses));
    }

    void restore(Map<String, MinigameTeleportStatus> restoredStatuses)
    {
        statuses.clear();
        lastScanRows = 0;
        if (restoredStatuses == null)
        {
            return;
        }

        for (Map.Entry<String, MinigameTeleportStatus> entry : restoredStatuses.entrySet())
        {
            record(entry.getKey(), entry.getValue());
        }
        lastScanRows = statuses.size();
    }

    private boolean recordNormalized(String key, MinigameTeleportStatus status)
    {
        if (key.isEmpty() || status == null)
        {
            return false;
        }

        MinigameTeleportStatus previous = statuses.put(key, status);
        return previous != status;
    }

    private static MinigameTeleportStatus inferStatus(Widget row, Client client)
    {
        if (MinigameTeleportWidgets.isGroupingCurrentGame(row))
        {
            MinigameTeleportStatus groupingStatus = inferGroupingTeleportStatus(client);
            if (groupingStatus != MinigameTeleportStatus.UNKNOWN)
            {
                return groupingStatus;
            }
        }

        return inferStatus(row);
    }

    private static MinigameTeleportStatus inferGroupingTeleportStatus(Client client)
    {
        Widget teleportButton = MinigameTeleportWidgets.getGroupingTeleportButton(client);
        if (!MinigameTeleportWidgets.isVisible(teleportButton))
        {
            return MinigameTeleportStatus.UNKNOWN;
        }

        return inferStatus(teleportButton);
    }

    private static MinigameTeleportStatus inferStatus(Widget row)
    {
        String text = MinigameTeleportNames.normalize(MinigameTeleportNames.allWidgetText(row));
        if (containsAny(text, "locked", "requirement", "requirements", "requires", "not unlocked",
            "not completed", "cannot teleport", "not eligible", "unavailable"))
        {
            return MinigameTeleportStatus.LOCKED;
        }

        if (hasUsableAction(row))
        {
            return MinigameTeleportStatus.AVAILABLE;
        }

        if (hasDisabledTextColor(row))
        {
            return MinigameTeleportStatus.LOCKED;
        }

        return MinigameTeleportStatus.UNKNOWN;
    }

    private static boolean hasUsableAction(Widget widget)
    {
        if (!MinigameTeleportWidgets.isVisible(widget))
        {
            return false;
        }

        String[] actions = widget.getActions();
        if (actions != null && Arrays.stream(actions).anyMatch(MinigameTeleportUnlockState::isUsableAction))
        {
            return true;
        }

        Widget[] children = widget.getNestedChildren();
        if (children == null)
        {
            return false;
        }

        for (Widget child : children)
        {
            if (hasUsableAction(child))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean hasDisabledTextColor(Widget widget)
    {
        if (!MinigameTeleportWidgets.isVisible(widget))
        {
            return false;
        }

        int color = widget.getTextColor() & 0xFFFFFF;
        if (color == 0x666666 || color == 0x808080 || color == 0x8F8F8F || color == 0x7F7F7F)
        {
            return true;
        }

        Widget[] children = widget.getNestedChildren();
        if (children == null)
        {
            return false;
        }

        for (Widget child : children)
        {
            if (hasDisabledTextColor(child))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isUsableAction(String action)
    {
        if (action == null)
        {
            return false;
        }

        String normalized = action.toLowerCase(Locale.ROOT);
        return normalized.contains("teleport")
            || normalized.contains("join");
    }

    private static boolean containsAny(String text, String... needles)
    {
        for (String needle : needles)
        {
            if (text.contains(needle))
            {
                return true;
            }
        }

        return false;
    }
}
