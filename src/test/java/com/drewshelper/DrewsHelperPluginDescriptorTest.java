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
    public void pluginDescriptorMatchesUiShellMetadata()
    {
        PluginDescriptor descriptor = DrewsHelperPlugin.class.getAnnotation(PluginDescriptor.class);

        assertNotNull(descriptor);
        assertEquals("Drew's Helper", descriptor.name());
        assertEquals("UI shell for Drew's Helper.", descriptor.description());
        assertArrayEquals(new String[] {"ui", "helper"}, descriptor.tags());
        assertFalse(descriptor.hidden());
    }
}
