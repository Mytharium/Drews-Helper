package com.drewshelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DrewsHelperPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DrewsHelperPlugin.class);
        RuneLite.main(args);
    }
}
