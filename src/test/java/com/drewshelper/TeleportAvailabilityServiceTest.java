package com.drewshelper;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeleportAvailabilityServiceTest
{
    private final MinigameTeleportUnlockState minigameTeleportUnlockState = new MinigameTeleportUnlockState();
    private final TeleportAvailabilityService service = new TeleportAvailabilityService(minigameTeleportUnlockState);

    @Test
    public void allowsUnknownMinigameTeleportUntilScanned()
    {
        RouteTransport transport = new RouteTransport("", "Nightmare Zone Minigame Teleport");

        assertTrue(service.isAvailable(transport, new DrewsHelperConfig() {}));
        assertFalse(service.getUnavailableReason(transport, new DrewsHelperConfig() {}).isPresent());
    }

    @Test
    public void marksScannedLockedMinigameTeleportUnavailable()
    {
        RouteTransport transport = new RouteTransport("", "Nightmare Zone Minigame Teleport");

        minigameTeleportUnlockState.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        assertFalse(service.isAvailable(transport, new DrewsHelperConfig() {}));
        assertEquals("Detected locked minigame teleport: Nightmare Zone",
            service.getUnavailableReason(transport, new DrewsHelperConfig() {}).get());
    }

    @Test
    public void allowsScannedAvailableMinigameTeleport()
    {
        RouteTransport transport = new RouteTransport("", "Giant's Foundry Minigame Teleport");

        minigameTeleportUnlockState.record("Giant's Foundry", MinigameTeleportStatus.AVAILABLE);

        assertTrue(service.isAvailable(transport, new DrewsHelperConfig() {}));
    }

    @Test
    public void findsFirstAvailableAfterLockedTransport()
    {
        RouteTransport locked = new RouteTransport("", "Nightmare Zone Minigame Teleport");
        RouteTransport allowed = new RouteTransport("Spirit tree", "Gnome Stronghold");
        RouteTransportSnapshot snapshot = new RouteTransportSnapshot(Arrays.asList(locked, allowed));

        minigameTeleportUnlockState.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        assertEquals(allowed, service.getFirstAvailable(snapshot, new DrewsHelperConfig() {}).get());
        assertEquals(1, service.countUnavailable(snapshot, new DrewsHelperConfig() {}));
        assertEquals(Arrays.asList(locked), service.getUnavailableTransports(snapshot, new DrewsHelperConfig() {}));
    }

    @Test
    public void filteringDisabledTreatsScannedLockedMinigameAsAvailable()
    {
        RouteTransport locked = new RouteTransport("", "Nightmare Zone Minigame Teleport");

        minigameTeleportUnlockState.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        assertTrue(service.isAvailable(locked, new DrewsHelperConfig()
        {
            @Override
            public boolean filterUnavailableTeleports()
            {
                return false;
            }
        }));
    }

    @Test
    public void convertsLockedMinigamesToBlockedTransportKeys()
    {
        minigameTeleportUnlockState.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);
        minigameTeleportUnlockState.record("Giants' Foundry", MinigameTeleportStatus.AVAILABLE);

        assertEquals(Collections.singleton("teleportation_minigames:nightmare_zone"),
            service.getBlockedTransportKeys(new DrewsHelperConfig() {}));
    }

    @Test
    public void doesNotSendBlockedTransportKeysWhenFilteringDisabled()
    {
        minigameTeleportUnlockState.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        assertTrue(service.getBlockedTransportKeys(new DrewsHelperConfig()
        {
            @Override
            public boolean filterUnavailableTeleports()
            {
                return false;
            }
        }).isEmpty());
    }
}
