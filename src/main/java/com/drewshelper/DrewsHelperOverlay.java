package com.drewshelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import com.drewshelper.routing.DrewsHelperRouteSnapshot;
import com.drewshelper.routing.DrewsHelperRouteStatus;
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

        if (route.getStatus() == DrewsHelperRouteStatus.READY)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Walking Distance")
                .right(route.getWalkingDistance() + " tiles")
                .rightColor(config.pathColor())
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
