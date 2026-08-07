package com.drewshelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DrewsHelperPluginTest
{
    private static final String[] REQUIRED_DEV_ARGS = {"--developer-mode", "--debug"};

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DrewsHelperPlugin.class);
        RuneLite.main(withRequiredDevArgs(args));
    }

    private static String[] withRequiredDevArgs(String[] args)
    {
        List<String> runeliteArgs = new ArrayList<>(Arrays.asList(args));
        for (String requiredArg : REQUIRED_DEV_ARGS)
        {
            if (!runeliteArgs.contains(requiredArg))
            {
                runeliteArgs.add(requiredArg);
            }
        }
        return runeliteArgs.toArray(new String[0]);
    }
}
