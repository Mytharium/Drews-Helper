package com.drewshelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        Map<String, MinigameTeleportStatus> scannedDestinations = new HashMap<>();
        for (Widget row : MinigameTeleportWidgets.findVisibleScanWidgets(client))
        {
            String text = MinigameTeleportNames.allWidgetText(row);
            List<String> destinations = MinigameTeleportNames.knownDestinationNames(text);
            if (destinations.size() != 1)
            {
                continue;
            }

            String key = MinigameTeleportNames.normalize(destinations.get(0));
            if (key.isEmpty())
            {
                continue;
            }

            MinigameTeleportStatus status = inferStatus(row, client);
            scannedDestinations.merge(key, status, MinigameTeleportUnlockState::mergeStatus);
        }

        boolean changed = false;
        for (Map.Entry<String, MinigameTeleportStatus> entry : scannedDestinations.entrySet())
        {
            changed |= recordNormalized(entry.getKey(), entry.getValue());
        }

        lastScanRows = scannedDestinations.size();
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

    int getAvailableDestinationCount()
    {
        return countStatus(MinigameTeleportStatus.AVAILABLE);
    }

    int getLockedDestinationCount()
    {
        return countStatus(MinigameTeleportStatus.LOCKED);
    }

    int getTotalDestinationCount()
    {
        return MinigameTeleportNames.totalDestinationCount();
    }

    int getLastScanRows()
    {
        return lastScanRows;
    }

    Map<String, MinigameTeleportStatus> snapshotStatuses()
    {
        Map<String, MinigameTeleportStatus> knownStatuses = new HashMap<>();
        for (Map.Entry<String, MinigameTeleportStatus> entry : statuses.entrySet())
        {
            if (entry.getValue() != MinigameTeleportStatus.UNKNOWN)
            {
                knownStatuses.put(entry.getKey(), entry.getValue());
            }
        }

        return Collections.unmodifiableMap(knownStatuses);
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
            if (entry.getValue() != MinigameTeleportStatus.UNKNOWN)
            {
                record(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean recordNormalized(String key, MinigameTeleportStatus status)
    {
        if (key.isEmpty() || status == null || status == MinigameTeleportStatus.UNKNOWN)
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
        if (looksLocked(text))
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

        return MinigameTeleportStatus.AVAILABLE;
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

        for (Widget child : MinigameTeleportWidgets.getAllChildren(widget))
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

        for (Widget child : MinigameTeleportWidgets.getAllChildren(widget))
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

    private static MinigameTeleportStatus mergeStatus(
        MinigameTeleportStatus current,
        MinigameTeleportStatus candidate)
    {
        if (current == MinigameTeleportStatus.LOCKED || candidate == MinigameTeleportStatus.LOCKED)
        {
            return MinigameTeleportStatus.LOCKED;
        }

        if (current == MinigameTeleportStatus.AVAILABLE || candidate == MinigameTeleportStatus.AVAILABLE)
        {
            return MinigameTeleportStatus.AVAILABLE;
        }

        return MinigameTeleportStatus.UNKNOWN;
    }

    static boolean looksLocked(String normalizedText)
    {
        return containsAny(normalizedText, "locked", "requirement", "requirements", "requires", "required",
            "not unlocked", "not completed", "cannot teleport", "not eligible", "unavailable",
            "completion", "completions", "speak to", "quest boss", "combat level");
    }

    private int countStatus(MinigameTeleportStatus status)
    {
        int count = 0;
        for (MinigameTeleportStatus savedStatus : statuses.values())
        {
            if (savedStatus == status)
            {
                count++;
            }
        }

        return count;
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
