package com.drewshelper;

import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import shortestpath.ShortestPathPlugin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DrewsHelperPluginDescriptorTest
{
    @Test
    public void pluginDescriptorMatchesPluginHubMetadata()
    {
        PluginDescriptor descriptor = DrewsHelperPlugin.class.getAnnotation(PluginDescriptor.class);

        assertNotNull(descriptor);
        assertEquals("Drew's Helper", descriptor.name());
        assertEquals("Pathing and teleport helper for RuneLite.", descriptor.description());
        assertArrayEquals(new String[] {"pathing", "route", "teleport", "quest", "helper"}, descriptor.tags());
        assertFalse(descriptor.hidden());
    }

    @Test
    public void internalRouteEngineIsHidden()
    {
        PluginDescriptor descriptor = ShortestPathPlugin.class.getAnnotation(PluginDescriptor.class);

        assertNotNull(descriptor);
        assertTrue(descriptor.hidden());
    }
}
