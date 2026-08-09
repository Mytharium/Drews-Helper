package com.drewshelper.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Travel time for a route, simulated tick by tick against the OSRS run-energy rules.
 *
 * <p>Energy cannot live inside A* - what a tile costs depends on how much energy you have when
 * you reach it, which depends on the whole path taken to get there. So the search picks the
 * route with fixed costs and this walks the finished path forward to produce the real time.
 *
 * <p>Per game tick, from the wiki:
 * <ul>
 *   <li>running drains {@code floor(floor(60 + 67 * clamp(weight,0,64) / 64) * (1 - agility/300))},
 *       times 0.3 with a stamina potion, or times 0.85 with a charged ring of endurance — the
 *       two do not stack</li>
 *   <li>any non-running tick restores {@code floor(agility/10) + 15}, raised by the graceful
 *       restoration percentage</li>
 * </ul>
 * Running covers two tiles per tick and burns; walking covers one and refills. So a long route is
 * really a run/walk duty cycle averaging well under two tiles per tick.
 */
public final class DrewsHelperTravelEstimate
{
    private static final int MAX_ENERGY = 10_000;
    private static final double SECONDS_PER_TICK = 0.6;

    public static final DrewsHelperTravelEstimate EMPTY =
        new DrewsHelperTravelEstimate(0, Collections.emptyList(), Collections.emptyMap(), 0);

    private final int totalTicks;
    private final List<Integer> legTicks;
    private final Map<String, Integer> transportsUsed;
    private final int walkedTiles;

    private DrewsHelperTravelEstimate(
        int totalTicks,
        List<Integer> legTicks,
        Map<String, Integer> transportsUsed,
        int walkedTiles
    )
    {
        this.totalTicks = totalTicks;
        this.legTicks = Collections.unmodifiableList(new ArrayList<>(legTicks));
        this.transportsUsed = Collections.unmodifiableMap(new LinkedHashMap<>(transportsUsed));
        this.walkedTiles = walkedTiles;
    }

    public int getTotalTicks()
    {
        return totalTicks;
    }

    /** Ticks to reach each waypoint in order, cumulative from the start of the route. */
    public List<Integer> getLegTicks()
    {
        return legTicks;
    }

    /** Transport family to the number of times the route uses it, in first-use order. */
    public Map<String, Integer> getTransportsUsed()
    {
        return transportsUsed;
    }

    public int getWalkedTiles()
    {
        return walkedTiles;
    }

    public boolean isEmpty()
    {
        return totalTicks <= 0;
    }

    /** Ticks as "m:ss". */
    public static String formatTicks(int ticks)
    {
        int totalSeconds = (int) Math.round(ticks * SECONDS_PER_TICK);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    public String formatTotal()
    {
        return formatTicks(totalTicks);
    }

    /**
     * One-line dump of every input the energy model reads, plus the two rates it derives from
     * them. This is what makes a wrong ETA diagnosable: if the forecast misses, the answer is
     * almost always a misread input rather than the simulation itself.
     */
    public static String describeEnergyModel(DrewsHelperPlayerCapability capability)
    {
        if (capability == null)
        {
            return "capability=none";
        }

        return "agility=" + capability.getSkillLevel("AGILITY")
            + " weight=" + capability.getWeightKg() + "kg"
            + " energy=" + (capability.getEnergyUnits() / 100) + "%"
            + " run=" + (capability.isRunning() ? "on" : "off")
            + " autoRunAt=" + capability.getAutoRunThresholdPercent() + "%"
            + " stamina=" + capability.isStaminaActive()
            + " staminaTicks=" + capability.getStaminaTicksRemaining()
            + " ring=" + capability.hasRingOfEndurance()
            + " graceful=" + capability.getGracefulRestorePercent() + "%"
            + " drain=" + drainPerTick(capability) + "/tick"
            + " regen=" + regenPerTick(capability) + "/tick";
    }

    static int drainPerTick(DrewsHelperPlayerCapability capability)
    {
        return drainPerTick(capability, capability.isStaminaActive());
    }

    /**
     * @param staminaActiveNow whether the potion is still up at the tick being simulated - a dose
     *     runs out partway through a long route, and drain triples the moment it does
     */
    static int drainPerTick(DrewsHelperPlayerCapability capability, boolean staminaActiveNow)
    {
        int weight = Math.max(0, Math.min(64, capability.getWeightKg()));
        int agility = capability.getSkillLevel("AGILITY");

        int base = (int) Math.floor(60 + (67.0 * weight) / 64.0);
        int drain = (int) Math.floor(base * (1.0 - agility / 300.0));

        // These do NOT stack. The wiki is explicit: the ring's passive 15% does not combine
        // with the stamina potion's drain reduction. Applying both was over-crediting by 15%.
        if (staminaActiveNow)
        {
            drain = (int) Math.floor(0.3 * drain);
        }
        else if (capability.hasRingOfEndurance())
        {
            drain = (int) Math.floor(0.85 * drain);
        }
        return Math.max(1, drain);
    }

    static int regenPerTick(DrewsHelperPlayerCapability capability)
    {
        int agility = capability.getSkillLevel("AGILITY");
        int gain = (agility / 10) + 15;

        // Graceful is not all-or-nothing: the six pieces total 20% and the complete set adds
        // another 10%, so this is a percentage rather than a flat multiplier.
        int restorePercent = capability.getGracefulRestorePercent();
        if (restorePercent > 0)
        {
            gain = (int) Math.floor((gain * (100.0 + restorePercent)) / 100.0);
        }
        return Math.max(1, gain);
    }

    /**
     * Simulates the finished path. Waypoints split the route into legs; every consecutive pair of
     * path points is either a transport hop (looked up in the graph) or one walked tile.
     */
    public static DrewsHelperTravelEstimate estimate(
        List<WorldPoint> path,
        List<WorldPoint> waypoints,
        DrewsHelperTransportGraph graph,
        DrewsHelperPlayerCapability capability
    )
    {
        if (path == null || path.size() < 2 || capability == null)
        {
            return EMPTY;
        }

        DrewsHelperTransportGraph transportGraph = graph == null ? DrewsHelperTransportGraph.empty() : graph;
        List<WorldPoint> legTargets = waypoints == null ? Collections.emptyList() : waypoints;

        // A dose lasts two minutes. Assuming it covers the whole journey makes any route longer
        // than the dose optimistic, so drain is picked per tick from whether it has expired yet.
        int drainWithStamina = drainPerTick(capability, true);
        int drainWithoutStamina = drainPerTick(capability, false);
        int staminaTicks = capability.getStaminaTicksRemaining();
        boolean staminaKnown = capability.isStaminaActive() && staminaTicks > 0;
        int drain = capability.isStaminaActive() ? drainWithStamina : drainWithoutStamina;
        int regen = regenPerTick(capability);
        int energy = Math.max(0, Math.min(MAX_ENERGY, capability.getEnergyUnits()));

        // Run is a live state, not a constant. Someone with the re-enable threshold set is only
        // walking until energy climbs back over it, so holding isRunning() fixed for the whole
        // route would forecast a walk that never actually happens.
        boolean runEnabled = capability.isRunning();
        int autoRunUnits = capability.getAutoRunThresholdPercent() * (MAX_ENERGY / 100);

        int ticks = 0;
        int walkedTiles = 0;
        int nextLeg = 0;
        List<Integer> legTicks = new ArrayList<>();
        Map<String, Integer> transportsUsed = new LinkedHashMap<>();

        int index = 0;
        while (index < path.size() - 1)
        {
            WorldPoint from = path.get(index);
            WorldPoint to = path.get(index + 1);

            if (!runEnabled && autoRunUnits > 0 && energy >= autoRunUnits)
            {
                runEnabled = true;
            }

            // Only steps down once, when the dose expires. Without a known duration this holds
            // the starting value, which is exactly the previous behaviour.
            if (staminaKnown)
            {
                drain = ticks < staminaTicks ? drainWithStamina : drainWithoutStamina;
            }

            DrewsHelperTransportEdge edge = findTransport(transportGraph, from, to);
            if (edge != null)
            {
                ticks += edge.getDurationTicks();
                // Not running for the duration of the hop, so energy comes back.
                energy = Math.min(MAX_ENERGY, energy + regen * edge.getDurationTicks());
                transportsUsed.merge(familyLabel(edge), 1, Integer::sum);
                index++;
            }
            else if (runEnabled && energy >= drain)
            {
                // Running: two tiles this tick, if there are two left before the next transport.
                int moved = 1;
                if (index + 2 < path.size() && findTransport(transportGraph, to, path.get(index + 2)) == null)
                {
                    moved = 2;
                }
                index += moved;
                walkedTiles += moved;
                energy = Math.max(0, energy - drain);
                ticks++;
            }
            else
            {
                // Out of energy: walking one tile, which costs nothing and refills.
                index++;
                walkedTiles++;
                energy = Math.min(MAX_ENERGY, energy + regen);
                ticks++;
            }

            while (nextLeg < legTargets.size() && reachedLeg(path, index, legTargets.get(nextLeg)))
            {
                legTicks.add(ticks);
                nextLeg++;
            }
        }

        while (legTicks.size() < legTargets.size())
        {
            legTicks.add(ticks);
        }

        return new DrewsHelperTravelEstimate(ticks, legTicks, transportsUsed, walkedTiles);
    }

    private static boolean reachedLeg(List<WorldPoint> path, int index, WorldPoint legTarget)
    {
        return legTarget != null && index < path.size() && legTarget.equals(path.get(index));
    }

    private static DrewsHelperTransportEdge findTransport(
        DrewsHelperTransportGraph graph,
        WorldPoint from,
        WorldPoint to
    )
    {
        for (DrewsHelperTransportEdge edge : graph.edgesFrom(from))
        {
            if (to.equals(edge.getDestination()))
            {
                return edge;
            }
        }
        return null;
    }

    private static String familyLabel(DrewsHelperTransportEdge edge)
    {
        switch (edge.getCategory())
        {
            case AGILITY_SHORTCUT:
                return "Agility";
            case GRAPPLE_SHORTCUT:
                return "Grapple";
            case CANOE:
                return "Canoe";
            case GNOME_GLIDER:
                return "Glider";
            case HOT_AIR_BALLOON:
                return "Balloon";
            case MAGIC_MUSHTREE:
                return "Mushtree";
            case QUETZAL:
                return "Quetzal";
            case WILDERNESS:
                return "Wilderness";
            default:
                return "Transport";
        }
    }
}
