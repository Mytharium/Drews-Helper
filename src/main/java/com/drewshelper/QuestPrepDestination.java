package com.drewshelper;

public enum QuestPrepDestination
{
    BANK("Bank"),
    GENERAL_STORES("General Stores"),
    GRAND_EXCHANGE("Grand Exchange");

    private final String displayName;

    QuestPrepDestination(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
