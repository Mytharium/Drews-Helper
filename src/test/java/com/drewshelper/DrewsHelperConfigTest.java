package com.drewshelper;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        assertEquals(4, otherTransportationSection.position());
        assertEquals("Settings", waypointSettingsSection.name());
        assertEquals(5, waypointSettingsSection.position());
    }

    @Test
    public void waypointPathColorsUseRequestedDefaults()
    {
        DrewsHelperConfig config = new DrewsHelperConfig() {};

        assertEquals(new Color(0x800020), config.pathColor());
        assertEquals(new Color(0xA9A9A9), config.waypoint1PathColor());
        assertEquals(new Color(0x0072B2), config.waypoint2PathColor());
        assertEquals(new Color(0x009E73), config.waypoint3PathColor());
        assertEquals(new Color(0xCC79A7), config.waypoint4PathColor());
        assertEquals(new Color(0xE69F00), config.waypoint5PathColor());
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
