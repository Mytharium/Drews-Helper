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
        assertEquals(true, overrides.get("useTransports"));
        assertEquals(true, overrides.get("useBoats"));
        assertEquals(true, overrides.get("useCharterShips"));
        assertEquals(true, overrides.get("useShips"));
        assertEquals(true, overrides.get("useMagicCarpets"));
        assertEquals(true, overrides.get("useMinecarts"));
        assertEquals(true, overrides.get("useTeleportationLevers"));
        assertEquals(true, overrides.get("useTeleportationPortals"));
        assertEquals(true, overrides.get("useTeleportationSpells"));
        assertEquals(true, overrides.get("useTeleportationSpellsHome"));
        assertEquals(true, overrides.get("useTeleportationMinigames"));
        assertEquals("Inventory (perm)", overrides.get("useTeleportationItems"));
        assertEquals(false, overrides.get("usePoh"));
        assertEquals("None", overrides.get("pohJewelleryBoxTier"));
        assertEquals(false, overrides.get("useFairyRings"));
        assertEquals(true, overrides.get("useSpiritTrees"));
        assertEquals(false, overrides.get("useWildernessObelisks"));
    }

    @Test
    public void keepsManualTransportOverridesWhenFilteringDisabled()
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
        assertEquals(true, overrides.get("useTeleportationMinigames"));
        assertEquals(false, overrides.get("usePoh"));
        assertEquals(false, overrides.get("useFairyRings"));
        assertEquals(true, overrides.get("useSpiritTrees"));
        assertFalse(overrides.containsKey("blockedTransportKeys"));
    }

    @Test
    public void sendsWholeCategoryUnlocksToShortestPath()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean spiritTreesUnlocked()
            {
                return false;
            }

            @Override
            public boolean fairyRingsUnlocked()
            {
                return true;
            }
        });

        assertEquals(false, overrides.get("useSpiritTrees"));
        assertEquals(true, overrides.get("useFairyRings"));
    }

    @Test
    public void keepsBaselineTransportsEnabledWithoutFrontendToggles()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean hotAirBalloonsUnlocked()
            {
                return true;
            }

            @Override
            public boolean quetzalsUnlocked()
            {
                return false;
            }
        });

        assertEquals(true, overrides.get("useTransports"));
        assertEquals(true, overrides.get("useBoats"));
        assertEquals(true, overrides.get("useCharterShips"));
        assertEquals(true, overrides.get("useShips"));
        assertEquals(true, overrides.get("useMagicCarpets"));
        assertEquals(true, overrides.get("useMinecarts"));
        assertEquals(true, overrides.get("useHotAirBalloons"));
        assertEquals(false, overrides.get("useQuetzals"));
    }

    @Test
    public void sendsDrewTransportationMenuTogglesToShortestPath()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean hotAirBalloonsUnlocked()
            {
                return true;
            }

            @Override
            public boolean quetzalsUnlocked()
            {
                return false;
            }
        });

        assertEquals(true, overrides.get("useHotAirBalloons"));
        assertEquals(false, overrides.get("useQuetzals"));
    }

    @Test
    public void sendsAdvancedTransportationMenuTogglesToShortestPath()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean pohMountedGloryUnlocked()
            {
                return true;
            }

            @Override
            public boolean pohPortalChamberUnlocked()
            {
                return true;
            }

            @Override
            public PortalNexusTier pohPortalNexusTier()
            {
                return PortalNexusTier.GILDED;
            }
        });

        assertEquals(true, overrides.get("useTeleportationSpells"));
        assertEquals(true, overrides.get("useTeleportationMinigames"));
        assertEquals(true, overrides.get("usePoh"));
        assertEquals(true, overrides.get("usePohMountedItems"));
        assertEquals(true, overrides.get("useTeleportationPortalsPoh"));
        assertEquals(false, overrides.get("usePohFairyRing"));
        assertEquals(false, overrides.get("usePohObelisk"));
    }

    @Test
    public void sendsOtherTransportationMenuTogglesToShortestPath()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean standardTabletsEnabled()
            {
                return true;
            }
        });

        assertEquals("Inventory", overrides.get("useTeleportationItems"));

        overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean achievementDiaryItemsEnabled()
            {
                return false;
            }

            @Override
            public boolean combatAchievementItemsEnabled()
            {
                return false;
            }

            @Override
            public boolean skillCapesEnabled()
            {
                return false;
            }

            @Override
            public boolean questRelatedItemsEnabled()
            {
                return false;
            }

            @Override
            public boolean otherItemsEnabled()
            {
                return false;
            }
        });

        assertEquals("None", overrides.get("useTeleportationItems"));
    }

    @Test
    public void sendsBlockedTransportKeysWhenFilteringEnabled()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig() {},
            Arrays.asList("teleportation_minigames:nightmare_zone", "", "teleportation_minigames:blast_furnace"));

        assertEquals(Arrays.asList(
            "teleportation_minigames:blast_furnace",
            "teleportation_minigames:nightmare_zone"), overrides.get("blockedTransportKeys"));
    }

    @Test
    public void canDisableMinigameTeleportCategoryForFallbackReroute()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig() {},
            Arrays.asList("teleportation_minigames:nightmare_zone"), true);

        assertEquals(false, overrides.get("useTeleportationMinigames"));
        assertEquals(Arrays.asList("teleportation_minigames:nightmare_zone"), overrides.get("blockedTransportKeys"));
    }

    @Test
    public void addsAvailabilityOverridesToExistingPathRequest()
    {
        Map<String, Object> existingConfig = new HashMap<>();
        existingConfig.put("customShortestPathSetting", 7);
        Map<String, Object> data = new HashMap<>();
        data.put("target", 112200842);
        data.put("config", existingConfig);

        boolean changed = ShortestPathBridge.addConfigOverrideToPathRequest(
            new PluginMessage("shortestpath", "path", data),
            new DrewsHelperConfig() {},
            Arrays.asList("teleportation_minigames:nightmare_zone"),
            true);

        assertTrue(changed);
        Map<?, ?> mergedConfig = (Map<?, ?>) data.get("config");
        assertEquals(7, mergedConfig.get("customShortestPathSetting"));
        assertEquals(true, mergedConfig.get("postTransports"));
        assertEquals(false, mergedConfig.get("useTeleportationMinigames"));
        assertEquals(Arrays.asList("teleportation_minigames:nightmare_zone"), mergedConfig.get("blockedTransportKeys"));
    }

    @Test
    public void ignoresNonPathRequestWhenAddingOverrides()
    {
        Map<String, Object> data = new HashMap<>();

        boolean changed = ShortestPathBridge.addConfigOverrideToPathRequest(
            new PluginMessage("shortestpath", "transports", data),
            new DrewsHelperConfig() {},
            Arrays.asList("teleportation_minigames:nightmare_zone"),
            true);

        assertFalse(changed);
        assertFalse(data.containsKey("config"));
    }

    @Test
    public void detectsDrewsHelperPathRequestMarker()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("drewsHelperRequest", true);

        assertTrue(ShortestPathBridge.isDrewsHelperPathRequestMessage(
            new PluginMessage("shortestpath", "path", data)));
    }

    @Test
    public void keepsBlockedTransportKeysWhenFilteringDisabled()
    {
        Map<String, Object> overrides = ShortestPathBridge.buildConfigOverride(new DrewsHelperConfig()
        {
            @Override
            public boolean filterUnavailableTeleports()
            {
                return false;
            }
        }, Arrays.asList("teleportation_minigames:nightmare_zone"));

        assertEquals(Arrays.asList("teleportation_minigames:nightmare_zone"), overrides.get("blockedTransportKeys"));
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

    @Test
    public void detectsConfigOnlyShortestPathPathRequest()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("config", new HashMap<>());

        assertTrue(ShortestPathBridge.isPathRequestMessage(
            new PluginMessage("shortestpath", "path", data)));
    }

    @Test
    public void addsFallbackOverridesToConfigOnlyPathRequest()
    {
        Map<String, Object> existingConfig = new HashMap<>();
        existingConfig.put("postTransports", true);
        Map<String, Object> data = new HashMap<>();
        data.put("config", existingConfig);

        boolean changed = ShortestPathBridge.addConfigOverrideToPathRequest(
            new PluginMessage("shortestpath", "path", data),
            new DrewsHelperConfig() {},
            Arrays.asList("teleportation_minigames:nightmare_zone"),
            true);

        assertTrue(changed);
        Map<?, ?> mergedConfig = (Map<?, ?>) data.get("config");
        assertEquals(true, mergedConfig.get("postTransports"));
        assertEquals(false, mergedConfig.get("useTeleportationMinigames"));
        assertEquals(Arrays.asList("teleportation_minigames:nightmare_zone"), mergedConfig.get("blockedTransportKeys"));
    }
}
