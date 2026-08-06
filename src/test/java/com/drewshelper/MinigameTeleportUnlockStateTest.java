package com.drewshelper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MinigameTeleportUnlockStateTest
{
    @Test
    public void matchesRouteLabelToScannedDestination()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();
        RouteTransport transport = new RouteTransport("", "Nightmare Zone Minigame Teleport");

        state.record("Nightmare Zone", MinigameTeleportStatus.LOCKED);

        assertEquals(MinigameTeleportStatus.LOCKED, state.getStatus(transport));
    }

    @Test
    public void normalizesRatPitsRouteVariants()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();
        RouteTransport transport = new RouteTransport("", "Rat Pits Minigame Teleport: 1. Ardougne");

        state.record("Rat Pits Ardougne", MinigameTeleportStatus.AVAILABLE);

        assertEquals(MinigameTeleportStatus.AVAILABLE, state.getStatus(transport));
    }

    @Test
    public void normalizesGiantsFoundryApostropheVariants()
    {
        MinigameTeleportUnlockState state = new MinigameTeleportUnlockState();
        RouteTransport transport = new RouteTransport("", "Giant's Foundry Minigame Teleport");

        state.record("Giants' Foundry", MinigameTeleportStatus.AVAILABLE);

        assertEquals(MinigameTeleportStatus.AVAILABLE, state.getStatus(transport));
    }
}
