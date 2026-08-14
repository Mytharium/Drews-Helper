package com.drewshelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteStatus;
import com.drewshelper.routing.DrewsHelperTravelEstimate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class DrewsHelperOverlay extends OverlayPanel
{
    private static final Color READY_GREEN = new Color(95, 190, 115);
    private static final Color MUTED = new Color(190, 190, 190);
    private static final int PANEL_WIDTH = 320;
    /**
     * A rule under the title. RuneLite ships no divider component, so it is a run of dashes -
     * built rather than typed so the length is stated instead of counted.
     */
    private static final String DIVIDER = new String(new char[52]).replace('\0', '-');

    private final DrewsHelperPlugin plugin;
    private final DrewsHelperConfig config;

    // Last READY values, shown greyed while a new solve is in flight.
    private int lastDistance = -1;
    private DrewsHelperTravelEstimate lastEstimate = DrewsHelperTravelEstimate.EMPTY;

    @Inject
    DrewsHelperOverlay(DrewsHelperPlugin plugin, DrewsHelperConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.pathingReplacementEnabled()
            && !config.teleportAssistEnabled()
            && plugin.getPlacedWaypointCount() == 0)
        {
            return null;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Drew's Helper")
            .color(READY_GREEN)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left(DIVIDER)
            .build());

        DrewsHelperRouteSnapshot route = plugin.getRouteSnapshot();
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Route")
            .right(routeStatusText(route))
            .rightColor(route.getStatus() == DrewsHelperRouteStatus.READY ? READY_GREEN : MUTED)
            .build());

        // Keep the last known numbers on screen while a new solve runs. They used to vanish,
        // which made a slow solve look like the plugin had died.
        boolean ready = route.getStatus() == DrewsHelperRouteStatus.READY;
        if (ready)
        {
            lastDistance = route.getWalkingDistance();
            lastEstimate = plugin.getTravelEstimate();
        }
        else if (route.getStatus() == DrewsHelperRouteStatus.NO_WAYPOINTS
            || route.getStatus() == DrewsHelperRouteStatus.DISABLED)
        {
            lastDistance = -1;
            lastEstimate = DrewsHelperTravelEstimate.EMPTY;
        }

        if (lastDistance >= 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Route Length")
                .right(lastDistance + " tiles")
                .rightColor(ready ? config.pathColor() : MUTED)
                .build());
        }

        // Deliberately outside the guard. The waypoint rows now carry the grid references,
        // so they have to stay on screen before the first solve finishes - that is exactly
        // when you most want to see where you asked to go.
        addTravelEstimate(lastEstimate, ready);
        addRequirementRows(route);

        String benchmarkSummary = plugin.getRouteBenchmarkSummary();
        if (!benchmarkSummary.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Benchmark")
                .right(benchmarkSummary)
                .rightColor(MUTED)
                .build());
        }

        return super.render(graphics);
    }

    /**
     * ETA, one row per placed waypoint, and which transports the route uses. The estimate is
     * recomputed by the plugin every tick, so these count down as you move.
     */
    private void addTravelEstimate(DrewsHelperTravelEstimate estimate, boolean ready)
    {
        boolean hasEstimate = estimate != null && !estimate.isEmpty();

        if (hasEstimate)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("ETA")
                .right(estimate.formatTotal())
                .rightColor(ready ? READY_GREEN : MUTED)
                .build());
        }

        addWaypointRows(hasEstimate ? estimate : null);

        if (!hasEstimate)
        {
            return;
        }

        Map<String, Integer> transports = estimate.getTransportsUsed();
        if (!transports.isEmpty())
        {
            // One transport per line, and all of them. Comma-joining them into a single
            // right-hand string let the panel wrap mid-name - "Spirit tree: Grand" on one
            // line and "Exchange," on the next - which is unreadable, and the "+N more"
            // collapse hid exactly the shortcuts a long route most needs to show.
            //
            // The name goes on the LEFT so it has the panel's full width before the right
            // column, and the count sits on the right only when it is more than one. The
            // two-space indent matches the "-> #1" waypoint rows above.
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Actions")
                .build());

            // Numbered in route order. getTransportsUsed() is a LinkedHashMap filled as the
            // estimate walks the finished path, so insertion order IS the order you use them.
            // A transport used twice keeps its first position and carries an "x2" instead of
            // appearing again, so the number is the order of first use.
            //
            // The repeat count moved onto the LEFT so the right column is a time everywhere in
            // the panel - the waypoint rows above read the same way, and mixing "x2" and "0:30"
            // in one column makes both harder to scan.
            // Duration, not arrival. Arrival answers "when do I do this" and is 0:00 for a
            // teleport at the start of a route, which reads as "this is free" when the hop
            // actually costs fourteen seconds. The waypoint rows above already give the
            // running clock, so these rows carry the cost instead.
            Map<String, Integer> durations = estimate.getTransportDurations();
            int step = 1;
            for (Map.Entry<String, Integer> entry : transports.entrySet())
            {
                String name = entry.getKey();
                if (entry.getValue() > 1)
                {
                    name = name + " x" + entry.getValue();
                }

                Integer duration = durations.get(entry.getKey());
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  " + step + ". " + name)
                    .right(duration == null ? "" : DrewsHelperTravelEstimate.formatTicks(duration))
                    .rightColor(MUTED)
                    .build());
                step++;
            }
        }
    }

    private void addRequirementRows(DrewsHelperRouteSnapshot route)
    {
        List<String> requirements = route.getRequirements();
        if (requirements.isEmpty())
        {
            return;
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Requirements")
            .build());

        for (String requirement : requirements)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("  " + requirement)
                .right("")
                .rightColor(MUTED)
                .build());
        }
    }

    /**
     * One row per placed waypoint: slot number, grid reference, and the ETA to reach it.
     *
     * <p>The grid reference used to live in a separate block further down the panel, which
     * meant reading a waypoint took two lookups in two places. Merging them costs nothing
     * because both are keyed by the same slot.
     *
     * <p>Driven by the PLACED waypoints rather than by the route legs, so a waypoint stays
     * visible with a blank time while a solve is still running or when no path exists.
     *
     * @param estimate the current estimate, or null when there is nothing to time yet
     */
    private void addWaypointRows(DrewsHelperTravelEstimate estimate)
    {
        // Legs are ordered by the waypoint they lead to, not by their position in the route -
        // those differ whenever a slot is empty or a waypoint has already been reached. Map
        // each leg back to its real slot once, then index by slot.
        Map<Integer, Integer> ticksBySlot = new HashMap<>();
        if (estimate != null)
        {
            List<Integer> legs = estimate.getLegTicks();
            for (int index = 0; index < legs.size(); index++)
            {
                ticksBySlot.put(plugin.waypointSlotForLeg(index), legs.get(index));
            }
        }

        for (int slot = 0; slot < DrewsHelperPlugin.MAX_WAYPOINTS; slot++)
        {
            WorldPoint waypoint = plugin.getWaypoint(slot);
            if (waypoint == null)
            {
                continue;
            }

            Integer ticks = ticksBySlot.get(slot);
            panelComponent.getChildren().add(LineComponent.builder()
                .left("  Waypoint #" + (slot + 1))
                .right(ticks == null ? "" : DrewsHelperTravelEstimate.formatTicks(ticks))
                .rightColor(plugin.getWaypointColor(slot))
                .build());
        }
    }

    private static String routeStatusText(DrewsHelperRouteSnapshot route)
    {
        switch (route.getStatus())
        {
            case READY:
                return "Destination Set";
            case NO_WAYPOINTS:
                return "No Destination Set";
            case CALCULATING:
            case NO_PATH:
            case ERROR:
                return route.getMessage();
            default:
                return route.getStatus().name();
        }
    }
}
