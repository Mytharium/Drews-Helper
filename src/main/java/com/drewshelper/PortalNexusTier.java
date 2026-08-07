package com.drewshelper;

public enum PortalNexusTier
{
    NONE("None"),
    MARBLE("Marble"),
    GILDED("Gilded"),
    CRYSTALLINE("Crystalline");

    private final String displayName;

    PortalNexusTier(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
