package com.drewshelper.routing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Item requirement symbols used by the transport resource.
 *
 * <p>Upstream writes item requirements symbolically ("AXE=1", "CROSSBOW=1&amp;MITH_GRAPPLE=1")
 * rather than by id, because most of them accept any tier. Only the 17 symbols that actually
 * appear in the generated resource are listed here; anything else in the data is a literal
 * item id and is handled by the parser directly.
 */
enum DrewsHelperItemVariation
{
    COINS(ItemID.COINS),

    AXE(
        ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE,
        ItemID.MITHRIL_AXE, ItemID.ADAMANT_AXE, ItemID.RUNE_AXE, ItemID.DRAGON_AXE,
        ItemID.CRYSTAL_AXE, ItemID.TRAIL_GILDED_AXE, ItemID.INFERNAL_AXE, ItemID._3A_AXE),

    PICKAXE(
        ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_PICKAXE, ItemID.BLACK_PICKAXE,
        ItemID.MITHRIL_PICKAXE, ItemID.ADAMANT_PICKAXE, ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE,
        ItemID.CRYSTAL_PICKAXE, ItemID.TRAIL_GILDED_PICKAXE, ItemID._3A_PICKAXE,
        ItemID.DRAGON_PICKAXE_PRETTY, ItemID.ZALCANO_PICKAXE,
        ItemID.TRAILBLAZER_PICKAXE_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL,
        ItemID.INFERNAL_PICKAXE),

    MACHETE(
        ItemID.MACHETTE, ItemID.MACHETTE_OPAL, ItemID.MACHETTE_JADE, ItemID.MACHETTE_REDTOPAZ),

    CROSSBOW(
        ItemID.CROSSBOW, ItemID.PHOENIX_CROSSBOW, ItemID.DTTD_BONE_CROSSBOW, ItemID.HUNTING_CROSSBOW,
        ItemID.XBOWS_CROSSBOW_BRONZE, ItemID.XBOWS_CROSSBOW_IRON, ItemID.XBOWS_CROSSBOW_STEEL,
        ItemID.XBOWS_CROSSBOW_MITHRIL, ItemID.XBOWS_CROSSBOW_ADAMANTITE, ItemID.XBOWS_CROSSBOW_RUNITE,
        ItemID.XBOWS_CROSSBOW_DRAGON, ItemID.DRAGONHUNTER_XBOW,
        ItemID.BARROWS_KARIL_WEAPON, ItemID.BARROWS_KARIL_WEAPON_BROKEN,
        ItemID.BARROWS_KARIL_WEAPON_25, ItemID.BARROWS_KARIL_WEAPON_50,
        ItemID.BARROWS_KARIL_WEAPON_75, ItemID.BARROWS_KARIL_WEAPON_100,
        ItemID.ACB, ItemID.ZARYTE_XBOW),

    MITH_GRAPPLE(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE),

    CLIMBING_BOOTS(ItemID.DEATH_CLIMBINGBOOTS, ItemID.CLIMBING_BOOTS_G),

    BROWN_APRON(
        ItemID.BROWN_APRON, ItemID.GOLDEN_APRON, ItemID.SKILLCAPE_CRAFTING,
        ItemID.SKILLCAPE_CRAFTING_TRIMMED, ItemID.SKILLCAPE_CRAFTING_HOOD),

    ROPE(ItemID.ROPE),
    SHANTAY_PASS(ItemID.SHANTAY_PASS),
    SKAVID_MAP(ItemID.SKAVIDMAP),
    GLOWING_FUNGUS(ItemID.GLOWING_FUNGUS),
    DUSTY_KEY(ItemID.DUSTY_KEY),
    MAZE_KEY(ItemID.MELZARKEY),
    ECTO_TOKEN(ItemID.ECTOTOKEN),
    MAX_CAPE(ItemID.SKILLCAPE_MAX, ItemID.SKILLCAPE_MAX_WORN),
    MAX_HOOD(ItemID.SKILLCAPE_MAX_HOOD);

    private static final Map<String, DrewsHelperItemVariation> BY_NAME;

    static
    {
        Map<String, DrewsHelperItemVariation> byName = new HashMap<>();
        for (DrewsHelperItemVariation variation : values())
        {
            byName.put(variation.name(), variation);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
    }

    private final int[] itemIds;

    DrewsHelperItemVariation(int... itemIds)
    {
        this.itemIds = itemIds;
    }

    int[] getItemIds()
    {
        return itemIds;
    }

    static DrewsHelperItemVariation bySymbol(String symbol)
    {
        return symbol == null ? null : BY_NAME.get(symbol.trim());
    }
}
