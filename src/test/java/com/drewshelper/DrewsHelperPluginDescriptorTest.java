package com.drewshelper;

import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class DrewsHelperPluginDescriptorTest
{
    @Test
    public void pluginDescriptorMatchesWaypointRouteMetadata()
    {
        PluginDescriptor descriptor = DrewsHelperPlugin.class.getAnnotation(PluginDescriptor.class);

        assertNotNull(descriptor);
        assertEquals("Drew's Helper", descriptor.name());
        assertEquals("Waypoint placement and route guidance.", descriptor.description());
        assertArrayEquals(new String[] {"ui", "helper", "waypoint", "route", "transport"}, descriptor.tags());
        assertFalse(descriptor.hidden());
    }
}
