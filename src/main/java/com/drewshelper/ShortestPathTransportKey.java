package com.drewshelper;

import java.util.Locale;
import java.util.Optional;

final class ShortestPathTransportKey
{
    static final String BLOCKED_TRANSPORT_KEYS_CONFIG = "blockedTransportKeys";
    private static final String MINIGAME_TELEPORT_PREFIX = "teleportation_minigames:";

    private ShortestPathTransportKey()
    {
    }

    static String minigameTeleportKey(String normalizedDestination)
    {
        String slug = slug(normalizedDestination);
        return slug.isEmpty() ? "" : MINIGAME_TELEPORT_PREFIX + slug;
    }

    static Optional<String> minigameTeleportKey(RouteTransport transport)
    {
        String destination = MinigameTeleportNames.destinationKey(transport);
        String key = minigameTeleportKey(destination);
        return key.isEmpty() ? Optional.empty() : Optional.of(key);
    }

    static String slug(String text)
    {
        if (text == null)
        {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
            .replace("'", "")
            .replace("\u2019", "")
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }
}
