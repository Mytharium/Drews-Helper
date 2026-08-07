package com.drewshelper.routing;

import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperTransportEdge
{
    private final WorldPoint source;
    private final WorldPoint destination;
    private final DrewsHelperTransportCategory category;
    private final String label;

    DrewsHelperTransportEdge(
        WorldPoint source,
        WorldPoint destination,
        DrewsHelperTransportCategory category,
        String label
    )
    {
        this.source = source;
        this.destination = destination;
        this.category = category;
        this.label = label == null ? "" : label;
    }

    public WorldPoint getSource()
    {
        return source;
    }

    public WorldPoint getDestination()
    {
        return destination;
    }

    DrewsHelperTransportCategory getCategory()
    {
        return category;
    }

    public String getLabel()
    {
        return label;
    }
}
