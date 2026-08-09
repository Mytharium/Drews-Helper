package com.drewshelper.routing;

import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperTransportEdge
{
    private final WorldPoint source;
    private final WorldPoint destination;
    private final DrewsHelperTransportCategory category;
    private final String label;
    private final int durationTicks;
    private final String skills;
    private final String quests;
    private final String items;
    private final String varbits;
    private final String varPlayers;

    DrewsHelperTransportEdge(
        WorldPoint source,
        WorldPoint destination,
        DrewsHelperTransportCategory category,
        String label
    )
    {
        this(source, destination, category, label, 1, "", "", "", "", "");
    }

    DrewsHelperTransportEdge(
        WorldPoint source,
        WorldPoint destination,
        DrewsHelperTransportCategory category,
        String label,
        int durationTicks,
        String skills,
        String quests,
        String items,
        String varbits,
        String varPlayers
    )
    {
        this.source = source;
        this.destination = destination;
        this.category = category;
        this.label = label == null ? "" : label;
        this.durationTicks = Math.max(1, durationTicks);
        this.skills = skills == null ? "" : skills;
        this.quests = quests == null ? "" : quests;
        this.items = items == null ? "" : items;
        this.varbits = varbits == null ? "" : varbits;
        this.varPlayers = varPlayers == null ? "" : varPlayers;
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

    /**
     * Travel time in game ticks, floored at 1. Not yet used for route costing -
     * the search still prices every edge at one step.
     */
    public int getDurationTicks()
    {
        return durationTicks;
    }

    /** Skill requirements as "Name=level" pairs joined by ';', empty when none. */
    public String getSkills()
    {
        return skills;
    }

    public String getQuests()
    {
        return quests;
    }

    public String getItems()
    {
        return items;
    }

    public String getVarbits()
    {
        return varbits;
    }

    public String getVarPlayers()
    {
        return varPlayers;
    }
}
