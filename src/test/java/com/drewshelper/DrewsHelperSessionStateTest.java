package com.drewshelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DrewsHelperSessionStateTest
{
    @Test
    public void roundTripsSavedRouteSnapshot()
    {
        RouteTransportSnapshot snapshot = new RouteTransportSnapshot(Arrays.asList(
            new RouteTransport("Games necklace", "Burthorpe"),
            new RouteTransport("Minigame Teleport", "Nightmare Zone")));

        RouteTransportSnapshot restored = DrewsHelperSessionState.decodeRouteSnapshot(
            DrewsHelperSessionState.encodeRouteSnapshot(snapshot));

        assertEquals(2, restored.size());
        assertEquals("Games necklace -> Burthorpe", restored.getTransports().get(0).toDisplayLine());
        assertEquals("Minigame Teleport -> Nightmare Zone", restored.getTransports().get(1).toDisplayLine());
    }

    @Test
    public void ignoresInvalidRouteSnapshotRows()
    {
        RouteTransportSnapshot restored = DrewsHelperSessionState.decodeRouteSnapshot("not,a,valid,row");

        assertFalse(restored.getNextTransport().isPresent());
    }

    @Test
    public void roundTripsMinigameStatuses()
    {
        Map<String, MinigameTeleportStatus> statuses = new HashMap<>();
        statuses.put("nightmare zone", MinigameTeleportStatus.LOCKED);
        statuses.put("giants foundry", MinigameTeleportStatus.AVAILABLE);

        Map<String, MinigameTeleportStatus> restored = DrewsHelperSessionState.decodeMinigameStatuses(
            DrewsHelperSessionState.encodeMinigameStatuses(statuses));

        assertEquals(MinigameTeleportStatus.LOCKED, restored.get("nightmare zone"));
        assertEquals(MinigameTeleportStatus.AVAILABLE, restored.get("giants foundry"));
    }
}
