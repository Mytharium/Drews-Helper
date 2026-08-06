package com.drewshelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class DrewsHelperOverlay extends OverlayPanel
{
    private static final Color READY_GREEN = new Color(95, 190, 115);
    private static final Color MUTED = new Color(190, 190, 190);
    private static final Color WARNING = new Color(230, 170, 70);

    private final DrewsHelperConfig config;
    private final RouteTransportState routeTransportState;

    @Inject
    DrewsHelperOverlay(DrewsHelperPlugin plugin, DrewsHelperConfig config, RouteTransportState routeTransportState)
    {
        super(plugin);
        this.config = config;
        this.routeTransportState = routeTransportState;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.pathingReplacementEnabled() && !config.teleportAssistEnabled())
        {
            return null;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Drew's Helper")
            .color(READY_GREEN)
            .build());

        RouteTransportSnapshot snapshot = routeTransportState.getSnapshot();
        if (snapshot.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Route Feed")
                .right("Waiting")
                .rightColor(WARNING)
                .build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Shortest Path")
                .right("No transports")
                .rightColor(MUTED)
                .build());
            return super.render(graphics);
        }

        RouteTransport nextTransport = snapshot.getNextTransport().orElse(null);
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Next")
            .right(nextTransport == null ? "Unknown transport" : nextTransport.toDisplayLine())
            .rightColor(READY_GREEN)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Transports")
            .right(String.valueOf(snapshot.size()))
            .rightColor(MUTED)
            .build());

        return super.render(graphics);
    }
}
