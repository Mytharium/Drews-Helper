package com.drewshelper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinigameTeleportUnlockStateTest
{
    @Test
    public void unknownStatusDoesNotPolluteUnlockCache()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();

        state.record("Nightmare Zone", MinigameTeleportStatus.UNKNOWN);

        assertEquals(0, state.getKnownDestinationCount());
        assertEquals(MinigameTeleportStatus.UNKNOWN,
            state.getStatus(new RouteTransport("Minigame Teleport", "Nightmare Zone", -1)));
    }

    @Test
    public void availableAndLockedStatusesAreTrackedSeparately()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();

        state.record("Giants' Foundry", MinigameTeleportStatus.AVAILABLE);
        state.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        assertEquals(2, state.getKnownDestinationCount());
        assertEquals(1, state.getAvailableDestinationCount());
        assertEquals(1, state.getLockedDestinationCount());
        assertEquals(MinigameTeleportStatus.LOCKED,
            state.getStatus(new RouteTransport("Minigame Teleport", "Nightmare Zone", -1)));
    }

    @Test
    public void detectsMinigameRequirementText()
    {
        assertTrue(MinigameTeleportUnlockState.looksLocked(
            "nightmare zone required more quest boss completions"));
        assertTrue(MinigameTeleportUnlockState.looksLocked(
            "sorceress garden speak to osman about sqirk fruit"));
        assertTrue(MinigameTeleportUnlockState.looksLocked(
            "blast furnace requires partial completion of the giant dwarf"));
        assertFalse(MinigameTeleportUnlockState.looksLocked(
            "castle wars west of yanille"));
    }

    @Test
    public void persistsAvailableAndLockedStatuses()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();
        state.record("Giants' Foundry", MinigameTeleportStatus.AVAILABLE);
        state.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        MinigameTeleportUnlockState restored = new MinigameTeleportUnlockState();
        restored.restore(state.snapshotStatuses());

        assertEquals(MinigameTeleportStatus.AVAILABLE,
            restored.getStatus(new RouteTransport("Minigame Teleport", "Giants' Foundry", -1)));
        assertEquals(MinigameTeleportStatus.LOCKED,
            restored.getStatus(new RouteTransport("Minigame Teleport", "Nightmare Zone", -1)));
    }

    @Test
    public void exposesSupportedDestinationTotal()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();

        assertEquals(18, state.getTotalDestinationCount());
    }
}
