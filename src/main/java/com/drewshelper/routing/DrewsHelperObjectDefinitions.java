package com.drewshelper.routing;

import net.runelite.api.ObjectComposition;

public final class DrewsHelperObjectDefinitions
{
    private DrewsHelperObjectDefinitions()
    {
    }

    public static ObjectComposition active(ObjectComposition composition)
    {
        if (composition == null || composition.getImpostorIds() == null)
        {
            return null;
        }

        try
        {
            return composition.getImpostor();
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    public static ObjectComposition activeOrBase(ObjectComposition composition)
    {
        ObjectComposition active = active(composition);
        return active == null ? composition : active;
    }

    public static String[] activeActions(ObjectComposition composition)
    {
        ObjectComposition active = activeOrBase(composition);
        return active == null ? null : active.getActions();
    }

    public static boolean hasAction(String[] actions, String expected)
    {
        if (actions == null || expected == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (expected.equalsIgnoreCase(plainText(action)))
            {
                return true;
            }
        }
        return false;
    }

    public static String actionTokenList(String[] actions)
    {
        if (actions == null || actions.length == 0)
        {
            return "-";
        }

        StringBuilder builder = new StringBuilder();
        for (String action : actions)
        {
            String token = sanitise(action);
            if ("-".equals(token))
            {
                continue;
            }
            if (builder.length() > 0)
            {
                builder.append('|');
            }
            builder.append(token);
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    public static String sanitise(String raw)
    {
        String cleaned = plainText(raw);
        if (cleaned.isEmpty())
        {
            return "-";
        }
        return cleaned.replaceAll("\\s+", "_");
    }

    static String plainText(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        return raw.replaceAll("<[^>]*>", "").trim();
    }
}
