package com.drewshelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteStatus;
import com.drewshelper.routing.DrewsHelperTravelEstimate;
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
            .left("Plugin UI")
            .right("Ready")
            .rightColor(MUTED)
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
                .left("Route Steps")
                .right(lastDistance + " tiles")
                .rightColor(ready ? config.pathColor() : MUTED)
                .build());

            addTravelEstimate(lastEstimate, ready);
        }

        String benchmarkSummary = plugin.getRouteBenchmarkSummary();
        if (!benchmarkSummary.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Benchmark")
                .right(benchmarkSummary)
                .rightColor(MUTED)
                .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Waypoints")
            .right(plugin.getPlacedWaypointCount() + "/" + DrewsHelperPlugin.MAX_WAYPOINTS)
            .rightColor(MUTED)
            .build());

        for (int index = 0; index < DrewsHelperPlugin.MAX_WAYPOINTS; index++)
        {
            WorldPoint waypoint = plugin.getWaypoint(index);
            if (waypoint == null)
            {
                continue;
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Waypoint #" + (index + 1))
                .right(waypoint.getX() + ", " + waypoint.getY() + ", " + waypoint.getPlane())
                .rightColor(plugin.getWaypointColor(index))
                .build());
        }

        return super.render(graphics);
    }

    /**
     * ETA, per-waypoint legs and which transport families the route uses. The estimate is
     * recomputed by the plugin every tick, so these count down as you move.
     */
    private void addTravelEstimate(DrewsHelperTravelEstimate estimate, boolean ready)
    {
        if (estimate == null || estimate.isEmpty())
        {
            return;
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("ETA")
            .right(estimate.formatTotal())
            .rightColor(ready ? READY_GREEN : MUTED)
            .build());

        List<Integer> legs = estimate.getLegTicks();
        for (int index = 0; index < legs.size(); index++)
        {
            // Legs are numbered by the waypoint they lead to, not by their position in the
            // route - those differ whenever a slot is empty or a waypoint has been reached.
            int slot = plugin.waypointSlotForLeg(index);
            panelComponent.getChildren().add(LineComponent.builder()
                .left("  -> WP" + (slot + 1))
                .right(DrewsHelperTravelEstimate.formatTicks(legs.get(index)))
                .rightColor(plugin.getWaypointColor(slot))
                .build());
        }

        Map<String, Integer> transports = estimate.getTransportsUsed();
        if (!transports.isEmpty())
        {
            StringBuilder used = new StringBuilder();
            for (Map.Entry<String, Integer> entry : transports.entrySet())
            {
                if (used.length() > 0)
                {
                    used.append(", ");
                }
                used.append(entry.getKey());
                if (entry.getValue() > 1)
                {
                    used.append(" x").append(entry.getValue());
                }
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Using")
                .right(used.toString())
                .rightColor(MUTED)
                .build());
        }
    }

    private static String routeStatusText(DrewsHelperRouteSnapshot route)
    {
        switch (route.getStatus())
        {
            case READY:
                return "Ready";
            case CALCULATING:
            case NO_PATH:
            case ERROR:
                return route.getMessage();
            default:
                return route.getStatus().name();
        }
    }
}
