package com.drewshelper;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DrewsHelperConfigTest
{
    @Test
    public void waypointSettingsSectionSitsBelowOtherTransportation() throws Exception
    {
        Field otherTransportation = DrewsHelperConfig.class.getField("otherTransportationOptions");
        Field waypointSettings = DrewsHelperConfig.class.getField("waypointSettings");

        ConfigSection otherTransportationSection = otherTransportation.getAnnotation(ConfigSection.class);
        ConfigSection waypointSettingsSection = waypointSettings.getAnnotation(ConfigSection.class);

        assertNotNull(otherTransportationSection);
        assertNotNull(waypointSettingsSection);
        assertEquals("Other Transportation", otherTransportationSection.name());
        assertEquals("Settings", waypointSettingsSection.name());

        // Ordering, not absolute numbers - removing a section renumbers everything below it,
        // and this test is about which sits below which.
        assertTrue(waypointSettingsSection.position() > otherTransportationSection.position());
    }

    @Test
    public void basicTransportationSectionIsRemovedAndMushtreesMovedToAdvanced() throws Exception
    {
        // Agility shortcuts, canoes, grapples, gliders, balloons and quetzals are gated by the
        // account's real state now, so their checkboxes are gone and the section with them.
        for (Field field : DrewsHelperConfig.class.getFields())
        {
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null)
            {
                assertFalse("Basic Transportation".equals(section.name()));
            }
        }

        for (String removed : new String[]{
            "useAgilityShortcuts", "useCanoes", "useQuetzals",
            "useGnomeGliders", "useGrappleShortcuts", "useHotAirBalloons"})
        {
            for (Method method : DrewsHelperConfig.class.getMethods())
            {
                ConfigItem item = method.getAnnotation(ConfigItem.class);
                if (item != null)
                {
                    assertFalse(removed + " should no longer be a config item",
                        removed.equals(item.keyName()));
                }
            }
        }

        // Mushtrees survive as an attestation box, now in Advanced Transportation.
        DrewsHelperConfig config = new DrewsHelperConfig() {};
        ConfigItem mushtrees = DrewsHelperConfig.class
            .getMethod("magicMushtreesUnlocked").getAnnotation(ConfigItem.class);

        assertNotNull(mushtrees);
        assertEquals("useMagicMushtrees", mushtrees.keyName());
        assertEquals("advancedTransportationOptions", mushtrees.section());
        assertFalse(config.magicMushtreesUnlocked());
    }

    @Test
    public void waypointPathColorsUseRequestedDefaults()
    {
        DrewsHelperConfig config = new DrewsHelperConfig() {};

        assertEquals("Path Colour must be red", new Color(0xFF0000), config.pathColor());
        assertEquals("Waypoint #1 must be orange", new Color(0xFFA500), config.waypoint1PathColor());
        assertEquals("Waypoint #2 must be yellow", new Color(0xFFFF00), config.waypoint2PathColor());
        assertEquals("Waypoint #3 must be green", new Color(0x008000), config.waypoint3PathColor());
        assertEquals("Waypoint #4 must be blue", new Color(0x0000FF), config.waypoint4PathColor());
        assertEquals("Waypoint #5 must be indigo", new Color(0x4B0082), config.waypoint5PathColor());
    }

    @Test
    public void wildernessTransportsSitsBelowOtherItemsAndDefaultsOff() throws Exception
    {
        DrewsHelperConfig config = new DrewsHelperConfig() {};
        Method method = DrewsHelperConfig.class.getMethod("wildernessTransportsEnabled");
        ConfigItem item = method.getAnnotation(ConfigItem.class);

        assertNotNull(item);
        assertEquals("useWildernessTransports", item.keyName());
        assertEquals("Use: Wilderness Transports", item.name());
        assertEquals("otherTransportationOptions", item.section());
        assertEquals(12, item.position());
        assertFalse(config.wildernessTransportsEnabled());
    }

    @Test
    public void routeDiagnosticsConfigItemIsRemoved() throws Exception
    {
        for (Method method : DrewsHelperConfig.class.getMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null)
            {
                assertFalse("Route Diagnostics".equals(item.name()));
                assertFalse("routeDiagnosticsEnabled".equals(item.keyName()));
            }
        }
    }

    @Test
    public void routeBenchmarkControlDefaultsOffAndSitsBelowPathBoxes() throws Exception
    {
        DrewsHelperConfig config = new DrewsHelperConfig() {};
        Method method = DrewsHelperConfig.class.getMethod("routeBenchmarkEnabled");
        ConfigItem item = method.getAnnotation(ConfigItem.class);

        assertNotNull(item);
        assertEquals("routeBenchmarkEnabled", item.keyName());
        assertEquals("Log Benchmark Movement", item.name());
        assertEquals("waypointSettings", item.section());
        assertEquals(11, item.position());
        assertFalse(config.routeBenchmarkEnabled());
    }

    @Test
    public void routeSegmentValidationControlDefaultsOffAndSitsBelowBenchmark() throws Exception
    {
        DrewsHelperConfig config = new DrewsHelperConfig() {};
        Method method = DrewsHelperConfig.class.getMethod("routeSegmentValidationEnabled");
        ConfigItem item = method.getAnnotation(ConfigItem.class);

        assertNotNull(item);
        assertEquals("routeSegmentValidationEnabled", item.keyName());
        assertEquals("Log Route Segments", item.name());
        assertEquals("waypointSettings", item.section());
        assertEquals(12, item.position());
        assertFalse(config.routeSegmentValidationEnabled());
    }

    @Test
    public void objectStateRecorderControlDefaultsOffAndSitsBelowRouteSegments() throws Exception
    {
        DrewsHelperConfig config = new DrewsHelperConfig() {};
        Method clickMethod = DrewsHelperConfig.class.getMethod("clickPathfindingLoggingEnabled");
        ConfigItem clickItem = clickMethod.getAnnotation(ConfigItem.class);

        assertNotNull(clickItem);
        assertEquals("clickPathfindingLoggingEnabled", clickItem.keyName());
        assertEquals("Log Click Pathfinding", clickItem.name());
        assertEquals("waypointSettings", clickItem.section());
        assertEquals(13, clickItem.position());
        assertFalse(config.clickPathfindingLoggingEnabled());

        Method method = DrewsHelperConfig.class.getMethod("objectStateRecordingEnabled");
        ConfigItem item = method.getAnnotation(ConfigItem.class);

        assertNotNull(item);
        assertEquals("objectStateRecordingEnabled", item.keyName());
        assertEquals("Log Object/Door State", item.name());
        assertEquals("waypointSettings", item.section());
        assertEquals(14, item.position());
        assertFalse(config.objectStateRecordingEnabled());
    }

    @Test
    public void routeSolverConfigAndEtaToggleAreRemoved() throws Exception
    {
        for (Method method : DrewsHelperConfig.class.getMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null)
            {
                assertFalse("etaDebugLogging".equals(item.keyName()));
                assertFalse("Log ETA Accuracy".equals(item.name()));
                assertFalse("routeSolverMode".equals(item.keyName()));
                assertFalse("Route Solver".equals(item.name()));
            }
        }
    }

    @Test
    public void waypointPathColorItemsStayInSettingsSectionOrder() throws Exception
    {
        assertWaypointItem("pathColor", 0);
        assertWaypointItem("waypoint1PathColor", 1);
        assertWaypointItem("waypoint2PathColor", 2);
        assertWaypointItem("waypoint3PathColor", 3);
        assertWaypointItem("waypoint4PathColor", 4);
        assertWaypointItem("waypoint5PathColor", 5);
    }

    private static void assertWaypointItem(String methodName, int position) throws Exception
    {
        Method method = DrewsHelperConfig.class.getMethod(methodName);
        ConfigItem item = method.getAnnotation(ConfigItem.class);

        assertNotNull(item);
        assertEquals("waypointSettings", item.section());
        assertEquals(position, item.position());
    }
}
