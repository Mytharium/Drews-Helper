package com.drewshelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.runelite.api.coords.WorldPoint;
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
        data.put("destination", Arrays.asList(new WorldPoint(2900, 3500, 0), new WorldPoint(2461, 3443, 0)));

        RouteTransportSnapshot snapshot = ShortestPathBridge.parseTransportSnapshot(
            new PluginMessage("shortestpath", "transports", data)).orElse(RouteTransportSnapshot.EMPTY);

        assertFalse(snapshot.isEmpty());
        assertEquals(2, snapshot.size());
        assertEquals("Games necklace -> Burthorpe", snapshot.getNextTransport().get().toDisplayLine());
        assertTrue(snapshot.getLastTransportDestinationPacked().isPresent());
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

    @Test
    public void capturesShortestPathTargetFromQuestHelperMessage()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("target", new WorldPoint(3210, 3424, 0));

        OptionalInt target = ShortestPathBridge.parsePathTargetMessage(
            new PluginMessage("shortestpath", "path", data));

        assertTrue(target.isPresent());
        assertEquals(112200842, target.getAsInt());
    }

    @Test
    public void ignoresShortestPathPathMessageWithoutTarget()
    {
        OptionalInt target = ShortestPathBridge.parsePathTargetMessage(
            new PluginMessage("shortestpath", "path", new HashMap<>()));

        assertFalse(target.isPresent());
    }
}
