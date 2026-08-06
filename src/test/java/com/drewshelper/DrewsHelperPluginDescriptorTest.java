package com.drewshelper;

import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    }
}
