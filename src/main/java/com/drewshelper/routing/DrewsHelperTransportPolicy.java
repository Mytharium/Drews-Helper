package com.drewshelper.routing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which transport families the router may use.
 *
 * <p>Most families are always enabled and gated purely by
 * {@link DrewsHelperPlayerCapability} instead - the account's real skills, carried items,
 * completed quests and unlock varbits decide whether each individual edge is usable, which
 * is strictly more accurate than a checkbox could be.
 *
 * <p>Only three families remain opt-in, for three different reasons:
 * <ul>
 *   <li>{@code MAGIC_MUSHTREE} - upstream carries no requirement data at all for it, so
 *       there is nothing to verify and the checkbox is the user's attestation.</li>
 *   <li>{@code WILDERNESS} - not a capability question. You can always walk into the
 *       Wilderness; the checkbox asks whether you want to be routed through it.</li>
 *   <li>{@code PLANTED_SPIRIT_TREE} - the base spirit tree network is quest gated and so
 *       fully automatic, but a planted tree is one the player grew. No quest, varbit or
 *       item proves it exists, so this half of the network stays an attestation while the
 *       other half needs no checkbox at all.</li>
 * </ul>
 *
 * <p>The category enum stays package-private, so this exposes named builder methods
 * rather than the categories themselves.
 */
public final class DrewsHelperTransportPolicy
{
    /** Families whose availability is decided per edge by the capability snapshot. */
    private static final EnumSet<DrewsHelperTransportCategory> ALWAYS_ENABLED = EnumSet.of(
        DrewsHelperTransportCategory.BASELINE,
        DrewsHelperTransportCategory.AGILITY_SHORTCUT,
        DrewsHelperTransportCategory.GRAPPLE_SHORTCUT,
        DrewsHelperTransportCategory.CANOE,
        DrewsHelperTransportCategory.GNOME_GLIDER,
        DrewsHelperTransportCategory.HOT_AIR_BALLOON,
        DrewsHelperTransportCategory.QUETZAL,
        // Every spirit tree destination row carries its quest (Tree Gnome Village, plus
        // Song of the Elves / The Path of Glouphrie / Pandemonium for the outliers), and
        // every fairy ring edge carries Fairytale II - Cure a Queen, so both networks are
        // decided entirely by quest state.
        DrewsHelperTransportCategory.SPIRIT_TREE,
        DrewsHelperTransportCategory.FAIRY_RING);

    private final Set<DrewsHelperTransportCategory> enabled;
    private final String signature;

    private DrewsHelperTransportPolicy(Set<DrewsHelperTransportCategory> requested)
    {
        EnumSet<DrewsHelperTransportCategory> resolved = EnumSet.copyOf(ALWAYS_ENABLED);
        resolved.addAll(requested);
        this.enabled = Collections.unmodifiableSet(resolved);

        StringBuilder builder = new StringBuilder();
        for (DrewsHelperTransportCategory category : DrewsHelperTransportCategory.values())
        {
            builder.append(this.enabled.contains(category) ? '1' : '0');
        }
        this.signature = builder.toString();
    }

    public static DrewsHelperTransportPolicy baselineOnly()
    {
        return builder().build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /** Whether routes may enter the Wilderness at all. Read by the router, not just the graph. */
    public boolean allowsWilderness()
    {
        return allows(DrewsHelperTransportCategory.WILDERNESS);
    }

    boolean allows(DrewsHelperTransportCategory category)
    {
        return category != null && enabled.contains(category);
    }

    /**
     * Stable fingerprint of the enabled families. Safe to fold into the route
     * signature and the route engine cache key so a checkbox change rebuilds.
     */
    public String signature()
    {
        return signature;
    }

    public static final class Builder
    {
        private final EnumSet<DrewsHelperTransportCategory> enabled =
            EnumSet.noneOf(DrewsHelperTransportCategory.class);

        private Builder set(DrewsHelperTransportCategory category, boolean value)
        {
            if (value)
            {
                enabled.add(category);
            }
            else
            {
                enabled.remove(category);
            }
            return this;
        }

        public Builder wilderness(boolean value)
        {
            return set(DrewsHelperTransportCategory.WILDERNESS, value);
        }

        public Builder magicMushtrees(boolean value)
        {
            return set(DrewsHelperTransportCategory.MAGIC_MUSHTREE, value);
        }

        public Builder plantedSpiritTrees(boolean value)
        {
            return set(DrewsHelperTransportCategory.PLANTED_SPIRIT_TREE, value);
        }

        public DrewsHelperTransportPolicy build()
        {
            return new DrewsHelperTransportPolicy(enabled);
        }
    }
}
