package com.drewshelper.routing;

public enum DrewsHelperDataConfidence
{
    INHERITED,
    INFERRED,
    CONFIRMED,
    CONTRADICTED;

    public static DrewsHelperDataConfidence parse(String value, DrewsHelperDataConfidence fallback)
    {
        if (value == null)
        {
            return fallback;
        }

        String normalized = value.trim()
            .toUpperCase()
            .replace('-', '_')
            .replace(' ', '_');
        if (normalized.isEmpty())
        {
            return fallback;
        }

        try
        {
            return valueOf(normalized);
        }
        catch (IllegalArgumentException ignored)
        {
            return fallback;
        }
    }
}
