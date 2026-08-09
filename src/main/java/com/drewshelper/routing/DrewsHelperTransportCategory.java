package com.drewshelper.routing;

enum DrewsHelperTransportCategory
{
    BASELINE,
    WILDERNESS,
    AGILITY_SHORTCUT,
    GRAPPLE_SHORTCUT,
    CANOE,
    GNOME_GLIDER,
    HOT_AIR_BALLOON,
    MAGIC_MUSHTREE,
    QUETZAL,
    SPIRIT_TREE,
    /**
     * Spirit trees the player grew themselves - Port Sarim, Etceteria, Brimhaven, Hosidius,
     * the Farming Guild and the player-owned house. Split out from {@link #SPIRIT_TREE}
     * because nothing in the account state can prove a planted tree exists, so these edges
     * are the one part of the network that still needs the user's word for it.
     */
    PLANTED_SPIRIT_TREE,
    FAIRY_RING
}
