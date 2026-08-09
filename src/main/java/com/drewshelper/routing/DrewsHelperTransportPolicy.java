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
 * <p>Only two families remain opt-in, for two different reasons:
 * <ul>
 *   <li>{@code MAGIC_MUSHTREE} - upstream carries no requirement data at all for it, so
 *       there is nothing to verify and the checkbox is the user's attestation.</li>
 *   <li>{@code WILDERNESS} - not a capability question. You can always walk into the
 *       Wilderness; the checkbox asks whether you want to be routed through it.</li>
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
        DrewsHelperTransportCategory.QUETZAL);

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

        public DrewsHelperTransportPolicy build()
        {
            return new DrewsHelperTransportPolicy(enabled);
        }
    }
}
