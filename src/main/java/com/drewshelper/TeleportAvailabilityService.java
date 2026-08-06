package com.drewshelper;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class TeleportAvailabilityService
{
    private final MinigameTeleportUnlockState minigameTeleportUnlockState;

    @Inject
    TeleportAvailabilityService(MinigameTeleportUnlockState minigameTeleportUnlockState)
    {
        this.minigameTeleportUnlockState = minigameTeleportUnlockState;
    }

    boolean isAvailable(RouteTransport transport, DrewsHelperConfig config)
    {
        return getUnavailableReason(transport, config).isEmpty();
    }

    Optional<String> getUnavailableReason(RouteTransport transport, DrewsHelperConfig config)
    {
        if (transport == null || config == null || !config.filterUnavailableTeleports())
        {
            return Optional.empty();
        }

        if (isMinigameTeleport(transport)
            && minigameTeleportUnlockState.getStatus(transport) == MinigameTeleportStatus.LOCKED)
        {
            return Optional.of("Detected locked minigame teleport: "
                + MinigameTeleportNames.destinationName(transport));
        }

        return Optional.empty();
    }

    Optional<RouteTransport> getFirstAvailable(RouteTransportSnapshot snapshot, DrewsHelperConfig config)
    {
        return snapshot.getTransports().stream()
            .filter(RouteTransport::hasInstruction)
            .filter(transport -> isAvailable(transport, config))
            .findFirst();
    }

    Optional<RouteTransport> getFirstUnavailable(RouteTransportSnapshot snapshot, DrewsHelperConfig config)
    {
        return snapshot.getTransports().stream()
            .filter(RouteTransport::hasInstruction)
            .filter(transport -> !isAvailable(transport, config))
            .findFirst();
    }

    int countUnavailable(RouteTransportSnapshot snapshot, DrewsHelperConfig config)
    {
        return (int) snapshot.getTransports().stream()
            .filter(RouteTransport::hasInstruction)
            .filter(transport -> !isAvailable(transport, config))
            .count();
    }

    boolean isMinigameTeleport(RouteTransport transport)
    {
        String text = transport.toSearchText();
        return text.contains("minigame teleport") || text.contains("grouping teleport");
    }
}
