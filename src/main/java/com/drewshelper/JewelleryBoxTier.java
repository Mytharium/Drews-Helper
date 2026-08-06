package com.drewshelper;

public enum JewelleryBoxTier
{
    NONE("None"),
    BASIC("Basic"),
    FANCY("Fancy"),
    ORNATE("Ornate");

    private final String displayName;

    JewelleryBoxTier(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
