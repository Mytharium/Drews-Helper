package com.drewshelper.routing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * An immutable snapshot of the account state that decides which transport edges are usable.
 *
     * <p>Only the things that change while you play are here: real (unboosted) skill levels,
     * carried/equipped items, quest completion, unlock vars, cooldown vars and run-energy inputs.
 *
 * <p>Built on the client thread by the plugin and handed to the route solver, which runs on a
 * background thread. This type holds plain values only - no client access - so it is safe to
 * read from either thread once constructed.
 */
public final class DrewsHelperPlayerCapability
{
    /** Coin thresholds that appear in the transport resource, used to keep the signature stable. */
    private static final int[] COIN_TIERS =
        {0, 20, 30, 200, 400, 800, 1500, 1600, 1800, 2000, 2200, 2500, 3000, 3200};

    /** Used when the account cannot be read (logged out). Permissive, so routing still works. */
    public static final DrewsHelperPlayerCapability UNRESTRICTED =
        new DrewsHelperPlayerCapability(true, Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            0, 10_000, true, false, false, 0, 0, 0, 0);

    /** Graceful's set bonus caps the restoration bonus at 30% (20% from pieces, 10% for the set). */
    public static final int MAX_GRACEFUL_RESTORE_PERCENT = 30;

    private final boolean unrestricted;
    private final Map<String, Integer> skillLevels;
    private final Map<Integer, Integer> itemCounts;

    // Unlock state. Only the quests and var ids the transport data actually references are
    // snapshotted - the graph derives that list, so there is no hardcoded id table here.
    private final Map<String, Boolean> questsFinished;
    private final Map<Integer, Integer> varbits;
    private final Map<Integer, Integer> varPlayers;

    // Energy inputs. Carried here so the travel-time work does not need to reopen this type.
    private final int weightKg;
    private final int energyUnits;
    private final boolean running;
    private final boolean staminaActive;
    private final boolean ringOfEndurance;
    private final int gracefulRestorePercent;
    private final int autoRunThresholdPercent;

    // Ticks of stamina potion left. Deliberately NOT in the signature: it changes every tick
    // while a dose is up, so folding it in would rebuild the route constantly - the same
    // reason current energy is kept out.
    private final int staminaTicksRemaining;
    private final long currentEpochMinute;

    private final String signature;

    private DrewsHelperPlayerCapability(
        boolean unrestricted,
        Map<String, Integer> skillLevels,
        Map<Integer, Integer> itemCounts,
        Map<String, Boolean> questsFinished,
        Map<Integer, Integer> varbits,
        Map<Integer, Integer> varPlayers,
        int weightKg,
        int energyUnits,
        boolean running,
        boolean staminaActive,
        boolean ringOfEndurance,
        int gracefulRestorePercent,
        int autoRunThresholdPercent,
        int staminaTicksRemaining,
        long currentEpochMinute
    )
    {
        this.unrestricted = unrestricted;
        this.skillLevels = Collections.unmodifiableMap(new TreeMap<>(skillLevels));
        this.itemCounts = Collections.unmodifiableMap(new HashMap<>(itemCounts));
        this.questsFinished = Collections.unmodifiableMap(new TreeMap<>(questsFinished));
        this.varbits = Collections.unmodifiableMap(new TreeMap<>(varbits));
        this.varPlayers = Collections.unmodifiableMap(new TreeMap<>(varPlayers));
        this.weightKg = weightKg;
        this.energyUnits = energyUnits;
        this.running = running;
        this.staminaActive = staminaActive;
        this.ringOfEndurance = ringOfEndurance;
        this.gracefulRestorePercent =
            Math.max(0, Math.min(MAX_GRACEFUL_RESTORE_PERCENT, gracefulRestorePercent));
        this.autoRunThresholdPercent = Math.max(0, Math.min(100, autoRunThresholdPercent));
        this.staminaTicksRemaining = Math.max(0, staminaTicksRemaining);
        this.currentEpochMinute = Math.max(0, currentEpochMinute);
        this.signature = buildSignature();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public boolean isUnrestricted()
    {
        return unrestricted;
    }

    public int getWeightKg()
    {
        return weightKg;
    }

    public int getEnergyUnits()
    {
        return energyUnits;
    }

    public boolean isRunning()
    {
        return running;
    }

    public boolean isStaminaActive()
    {
        return staminaActive;
    }

    public boolean hasRingOfEndurance()
    {
        return ringOfEndurance;
    }

    /**
     * Run-energy restoration bonus from graceful, 0-30.
     *
     * <p>Not all-or-nothing: hood 3, top 4, legs 4, gloves 3, boots 3, cape 3 — 20 in total —
     * and wearing the complete set adds a further 10.
     */
    public int getGracefulRestorePercent()
    {
        return gracefulRestorePercent;
    }

    /**
     * Energy percentage at which the client turns run back on by itself, or 0 if that setting
     * is off.
     *
     * <p>This matters because "run is off" is usually a transient state, not a decision. Most
     * players set the re-enable threshold to 1%, so the toggle flips off the instant they hit
     * empty and flips back on one tick later. Treating run-off as "walks the whole way" would
     * make the ETA badly pessimistic for exactly those accounts, so the simulation re-enables
     * running itself once energy climbs back to this threshold.
     */
    public int getAutoRunThresholdPercent()
    {
        return autoRunThresholdPercent;
    }

    /**
     * Ticks of stamina potion left, or 0 when there is none or the duration is not yet known.
     *
     * <p>0 with {@link #isStaminaActive()} true means "assume it lasts the whole route" - the
     * behaviour before the duration could be read.
     */
    public int getStaminaTicksRemaining()
    {
        return staminaTicksRemaining;
    }

    public int getSkillLevel(String skillName)
    {
        Integer level = skillLevels.get(skillName.toUpperCase());
        return level == null ? 0 : level;
    }

    /**
     * Whether this account can currently use the edge.
     */
    boolean satisfies(DrewsHelperTransportEdge edge)
    {
        if (unrestricted)
        {
            return true;
        }
        return meetsSkills(edge.getSkills())
            && meetsItems(edge.getItems())
            && meetsQuests(edge.getQuests())
            && meetsVars(edge.getVarbits(), varbits)
            && meetsVars(edge.getVarPlayers(), varPlayers);
    }

    /**
     * Quest requirements are ';'-separated names, all of which must be complete.
     *
     * <p>A name this account has no entry for is treated as <b>satisfied</b>. That is
     * deliberate: an unresolvable name means our data disagrees with the client's quest list
     * (upstream has at least one typo), and a data problem must never silently delete a route
     * the player can actually use. The plugin logs unresolved names once at snapshot time.
     */
    boolean meetsQuests(String requirement)
    {
        if (unrestricted || requirement == null || requirement.isEmpty())
        {
            return true;
        }
        for (String term : requirement.split(";"))
        {
            String name = term.trim();
            if (name.isEmpty())
            {
                continue;
            }
            Boolean finished = questsFinished.get(name);
            if (finished != null && !finished)
            {
                return false;
            }
        }
        return true;
    }

    boolean meetsVarbits(String requirement)
    {
        return meetsVars(requirement, varbits);
    }

    boolean meetsVarPlayers(String requirement)
    {
        return meetsVars(requirement, varPlayers);
    }

    /**
     * Varbit and varplayer requirements: ';'-separated terms, all of which must hold.
     *
     * <p>Four normal forms appear in the data — {@code id=value}, {@code id&gt;value},
     * {@code id&lt;value} and {@code id&amp;mask}, the last being a bit test rather than a
     * comparison. Home teleport cooldowns add {@code id@minutes}, where the stored value is an
     * epoch-minute timestamp. An id we hold no value for is treated as satisfied for ordinary
     * vars, same reasoning as quests; for cooldown terms, unknown means locked.
     */
    boolean meetsVars(String requirement, Map<Integer, Integer> values)
    {
        if (unrestricted || requirement == null || requirement.isEmpty())
        {
            return true;
        }
        for (String term : requirement.split(";"))
        {
            String trimmed = term.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            if (!meetsVarTerm(trimmed, values))
            {
                return false;
            }
        }
        return true;
    }

    private boolean meetsVarTerm(String term, Map<Integer, Integer> values)
    {
        int split = indexOfOperator(term);
        if (split < 0)
        {
            // No operator at all - nothing meaningful to test.
            return true;
        }

        char operator = term.charAt(split);
        Integer id = parseBoxed(term.substring(0, split));
        Integer operand = parseBoxed(term.substring(split + 1));
        if (id == null || operand == null)
        {
            return true;
        }

        Integer actual = values.get(id);
        if (actual == null)
        {
            return operator != '@';
        }

        switch (operator)
        {
            case '=':
                return actual == operand.intValue();
            case '>':
                return actual > operand;
            case '<':
                return actual < operand;
            case '&':
                return (actual & operand) != 0;
            case '@':
                return currentEpochMinute - actual > operand;
            default:
                return true;
        }
    }

    private static int indexOfOperator(String term)
    {
        for (int i = 0; i < term.length(); i++)
        {
            char c = term.charAt(i);
            if (c == '=' || c == '>' || c == '<' || c == '&' || c == '@')
            {
                return i;
            }
        }
        return -1;
    }

    private static Integer parseBoxed(String value)
    {
        try
        {
            return Integer.valueOf(value.trim());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    boolean meetsSkills(String requirement)
    {
        if (unrestricted || requirement == null || requirement.isEmpty())
        {
            return true;
        }
        for (String term : requirement.split(";"))
        {
            String trimmed = term.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            int split = trimmed.indexOf('=');
            if (split <= 0)
            {
                continue;
            }
            int needed = parseInt(trimmed.substring(split + 1), 0);
            if (getSkillLevel(trimmed.substring(0, split)) < needed)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Item requirements are '|'-separated alternatives, each a '&amp;'-separated list of
     * "SYMBOL=quantity" or "itemId=quantity" terms. Any one satisfied alternative is enough.
     */
    boolean meetsItems(String requirement)
    {
        if (unrestricted || requirement == null || requirement.isEmpty())
        {
            return true;
        }
        for (String alternative : requirement.split("\\|"))
        {
            if (meetsAllTerms(alternative))
            {
                return true;
            }
        }
        return false;
    }

    private boolean meetsAllTerms(String alternative)
    {
        boolean sawTerm = false;
        for (String term : alternative.split("&"))
        {
            String trimmed = term.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            sawTerm = true;

            int split = trimmed.indexOf('=');
            String symbol = split > 0 ? trimmed.substring(0, split) : trimmed;
            // A quantity of 0 upstream means "needed but not consumed", so still require one.
            int needed = Math.max(1, split > 0 ? parseInt(trimmed.substring(split + 1), 1) : 1);

            if (countOf(symbol) < needed)
            {
                return false;
            }
        }
        return sawTerm;
    }

    private int countOf(String symbol)
    {
        DrewsHelperItemVariation variation = DrewsHelperItemVariation.bySymbol(symbol);
        if (variation != null)
        {
            int total = 0;
            for (int itemId : variation.getItemIds())
            {
                total += countOfId(itemId);
            }
            return total;
        }

        // Not a known symbol - the data also carries bare item ids.
        int itemId = parseInt(symbol, -1);
        return itemId < 0 ? 0 : countOfId(itemId);
    }

    private int countOfId(int itemId)
    {
        Integer count = itemCounts.get(itemId);
        return count == null ? 0 : count;
    }

    /**
     * Stable fingerprint for the route and engine cache keys.
     *
     * <p>Deliberately coarse on items: it records whether each symbol is held at all, and
     * buckets coins to the thresholds the data actually uses. Encoding an exact coin count
     * would rebuild the route every time you picked up a coin.
     */
    public String signature()
    {
        return signature;
    }

    private String buildSignature()
    {
        if (unrestricted)
        {
            return "unrestricted";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet())
        {
            builder.append(entry.getKey().charAt(0)).append(entry.getValue()).append('.');
        }
        builder.append('|');
        for (DrewsHelperItemVariation variation : DrewsHelperItemVariation.values())
        {
            if (variation == DrewsHelperItemVariation.COINS)
            {
                continue;
            }
            builder.append(countOf(variation.name()) > 0 ? '1' : '0');
        }
        builder.append('|').append(coinTier());

        // Unlock state moves rarely but must invalidate the route when it does - finishing a
        // quest can open a whole transport network. TreeMap iteration keeps this stable.
        builder.append('|');
        for (Map.Entry<String, Boolean> entry : questsFinished.entrySet())
        {
            builder.append(entry.getValue() ? '1' : '0');
        }
        builder.append('|');
        for (Map.Entry<Integer, Integer> entry : varbits.entrySet())
        {
            builder.append(entry.getKey()).append(':').append(entry.getValue()).append('.');
        }
        builder.append('|');
        for (Map.Entry<Integer, Integer> entry : varPlayers.entrySet())
        {
            builder.append(entry.getKey()).append(':').append(entry.getValue()).append('.');
        }
        builder.append('|').append(currentEpochMinute);
        return builder.toString();
    }

    private int coinTier()
    {
        int coins = countOf(DrewsHelperItemVariation.COINS.name());
        int tier = 0;
        for (int i = 0; i < COIN_TIERS.length; i++)
        {
            if (coins >= COIN_TIERS[i])
            {
                tier = i;
            }
        }
        return tier;
    }

    private static int parseInt(String value, int fallback)
    {
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    public static final class Builder
    {
        private final Map<String, Integer> skillLevels = new HashMap<>();
        private final Map<Integer, Integer> itemCounts = new HashMap<>();
        private final Map<String, Boolean> questsFinished = new HashMap<>();
        private final Map<Integer, Integer> varbits = new HashMap<>();
        private final Map<Integer, Integer> varPlayers = new HashMap<>();
        private int weightKg;
        private int energyUnits = 10_000;
        // Defaults to running: that is the prior behaviour, and it keeps a capability built
        // without explicit run state from silently producing a walking-speed estimate.
        private boolean running = true;
        private boolean staminaActive;
        private boolean ringOfEndurance;
        private int gracefulRestorePercent;
        private int autoRunThresholdPercent;
        private int staminaTicksRemaining;
        private long currentEpochMinute;

        public Builder skill(String skillName, int level)
        {
            if (skillName != null)
            {
                skillLevels.put(skillName.toUpperCase(), level);
            }
            return this;
        }

        public Builder item(int itemId, int quantity)
        {
            if (quantity > 0)
            {
                itemCounts.merge(itemId, quantity, Integer::sum);
            }
            return this;
        }

        /** Records a quest the transport data references and whether it is complete. */
        public Builder quest(String questName, boolean finished)
        {
            if (questName != null && !questName.isEmpty())
            {
                questsFinished.put(questName, finished);
            }
            return this;
        }

        public Builder varbit(int id, int value)
        {
            varbits.put(id, value);
            return this;
        }

        public Builder varPlayer(int id, int value)
        {
            varPlayers.put(id, value);
            return this;
        }

        public Builder weightKg(int value)
        {
            this.weightKg = value;
            return this;
        }

        public Builder energyUnits(int value)
        {
            this.energyUnits = value;
            return this;
        }

        public Builder running(boolean value)
        {
            this.running = value;
            return this;
        }

        public Builder staminaActive(boolean value)
        {
            this.staminaActive = value;
            return this;
        }

        public Builder ringOfEndurance(boolean value)
        {
            this.ringOfEndurance = value;
            return this;
        }

        /** Convenience for the complete set. Equivalent to gracefulRestorePercent(30). */
        public Builder fullGraceful(boolean value)
        {
            this.gracefulRestorePercent = value ? MAX_GRACEFUL_RESTORE_PERCENT : 0;
            return this;
        }

        public Builder gracefulRestorePercent(int value)
        {
            this.gracefulRestorePercent = value;
            return this;
        }

        /** Ticks of stamina left; 0 means unknown, which keeps the potion up for the whole route. */
        public Builder staminaTicksRemaining(int value)
        {
            this.staminaTicksRemaining = value;
            return this;
        }

        /** 0 disables it; otherwise the energy percentage that flips run back on. */
        public Builder autoRunThresholdPercent(int value)
        {
            this.autoRunThresholdPercent = value;
            return this;
        }

        /** Current UTC epoch minute, used to evaluate teleport cooldown terms like "892@30". */
        public Builder currentEpochMinute(long value)
        {
            this.currentEpochMinute = value;
            return this;
        }

        public DrewsHelperPlayerCapability build()
        {
            return new DrewsHelperPlayerCapability(false, skillLevels, itemCounts,
                questsFinished, varbits, varPlayers,
                weightKg, energyUnits, running, staminaActive, ringOfEndurance,
                gracefulRestorePercent, autoRunThresholdPercent, staminaTicksRemaining,
                currentEpochMinute);
        }
    }
}
