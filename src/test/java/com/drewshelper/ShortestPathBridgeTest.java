package com.drewshelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShortestPathBridgeTest
{
    @Test
    public void parsesShortestPathTransportMessage()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("objectInfo", Arrays.asList("Games necklace", "Spirit tree"));
        data.put("displayInfo", Arrays.asList("Burthorpe", "Tree Gnome Village"));

        RouteTransportSnapshot snapshot = ShortestPathBridge.parseTransportSnapshot(
            new PluginMessage("shortestpath", "transports", data)).orElse(RouteTransportSnapshot.EMPTY);

        assertFalse(snapshot.isEmpty());
        assertEquals(2, snapshot.size());
        assertEquals("Games necklace -> Burthorpe", snapshot.getNextTransport().get().toDisplayLine());
    }

    @Test
    public void ignoresOtherPluginMessages()
    {
        assertFalse(ShortestPathBridge.parseTransportSnapshot(
            new PluginMessage("questhelper", "transports", new HashMap<>())).isPresent());
    }

    @Test
    public void stripsRuneLiteColorTags()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("objectInfo", Arrays.asList("<col=ff9040>Varrock Teleport</col>"));
        data.put("displayInfo", Arrays.asList(""));

        RouteTransportSnapshot snapshot = ShortestPathBridge.parseTransportSnapshot(
            new PluginMessage("shortestpath", "transports", data)).orElse(RouteTransportSnapshot.EMPTY);

        assertTrue(snapshot.getNextTransport().isPresent());
        assertEquals("Varrock Teleport", snapshot.getNextTransport().get().toDisplayLine());
    }

    @Test
    public void buildsShortestPathAvailabilityOverrides()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig() {});

        assertEquals(true, overrides.get("postTransports"));
        assertFalse(overrides.containsKey("useTeleportationMinigames"));
        assertEquals(false, overrides.get("usePoh"));
        assertEquals("None", overrides.get("pohJewelleryBoxTier"));
    }

    @Test
    public void omitsAvailabilityOverridesWhenFilteringDisabled()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean filterUnavailableTeleports()
            {
                return false;
            }
        });

        assertEquals(true, overrides.get("postTransports"));
        assertFalse(overrides.containsKey("useTeleportationMinigames"));
        assertFalse(overrides.containsKey("usePoh"));
    }
}
