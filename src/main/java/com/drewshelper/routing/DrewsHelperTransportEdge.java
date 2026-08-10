package com.drewshelper.routing;

import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperTransportEdge
{
    /** Upstream's sentinel for "no Wilderness restriction recorded on this transport". */
    static final int NO_WILDERNESS_LIMIT = -1;

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
    private final int maxWildernessLevel;

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
        this(source, destination, category, label, durationTicks, skills, quests, items,
            varbits, varPlayers, NO_WILDERNESS_LIMIT);
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
        String varPlayers,
        int maxWildernessLevel
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
        this.maxWildernessLevel = maxWildernessLevel;
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
     * Travel time in game ticks, floored at 1. The route search converts this
     * to half-tick cost units so long hops do not price like one footstep.
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

    /**
     * Deepest Wilderness level this transport still works at, or {@link #NO_WILDERNESS_LIMIT}
     * when upstream records no cap. Home teleports carry 20: the game refuses them above
     * level 20 so a player cannot escape a fight instantly.
     */
    public int getMaxWildernessLevel()
    {
        return maxWildernessLevel;
    }

    public String getVarPlayers()
    {
        return varPlayers;
    }

    public boolean isOriginless()
    {
        return DrewsHelperTransportGraph.ANYWHERE.equals(source);
    }
}
