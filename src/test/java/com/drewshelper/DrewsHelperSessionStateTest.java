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
            new RouteTransport("Games necklace", "Burthorpe", 114691924),
            new RouteTransport("Minigame Teleport", "Nightmare Zone", 111555555)));

        RouteTransportSnapshot restored = DrewsHelperSessionState.decodeRouteSnapshot(
            DrewsHelperSessionState.encodeRouteSnapshot(snapshot));

        assertEquals(2, restored.size());
        assertEquals("Games necklace -> Burthorpe", restored.getTransports().get(0).toDisplayLine());
        assertEquals("Minigame Teleport -> Nightmare Zone", restored.getTransports().get(1).toDisplayLine());
        assertEquals(111555555, restored.getLastTransportDestinationPacked().getAsInt());
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

    @Test
    public void decodesOldTwoColumnRouteSnapshots()
    {
        String oldSnapshot = "R2FtZXMgbmVja2xhY2U,QnVydGhvcnBl";

        RouteTransportSnapshot restored = DrewsHelperSessionState.decodeRouteSnapshot(oldSnapshot);

        assertEquals(1, restored.size());
        assertFalse(restored.getLastTransportDestinationPacked().isPresent());
    }

    @Test
    public void roundTripsShortestPathTarget()
    {
        String encoded = DrewsHelperSessionState.encodeShortestPathTarget(112187530);

        assertEquals(112187530, DrewsHelperSessionState.decodeShortestPathTarget(encoded).getAsInt());
        assertFalse(DrewsHelperSessionState.decodeShortestPathTarget("").isPresent());
        assertFalse(DrewsHelperSessionState.decodeShortestPathTarget("bad").isPresent());
    }
}
