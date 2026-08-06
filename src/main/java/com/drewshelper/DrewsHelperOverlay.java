package com.drewshelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
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
    private static final int PANEL_WIDTH = 320;
    private static final int MAX_TRANSPORT_ROWS = 4;

    private final DrewsHelperConfig config;
    private final RouteTransportState routeTransportState;
    private final TeleportAvailabilityService teleportAvailabilityService;
    private final MinigameTeleportUnlockState minigameTeleportUnlockState;

    @Inject
    DrewsHelperOverlay(
        DrewsHelperPlugin plugin,
        DrewsHelperConfig config,
        RouteTransportState routeTransportState,
        TeleportAvailabilityService teleportAvailabilityService,
        MinigameTeleportUnlockState minigameTeleportUnlockState)
    {
        super(plugin);
        this.config = config;
        this.routeTransportState = routeTransportState;
        this.teleportAvailabilityService = teleportAvailabilityService;
        this.minigameTeleportUnlockState = minigameTeleportUnlockState;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
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
            addMinigameScanLine();
            return super.render(graphics);
        }

        RouteTransport nextTransport = teleportAvailabilityService.getFirstAvailable(snapshot, config).orElse(null);
        RouteTransport lockedTransport = teleportAvailabilityService.getFirstUnavailable(snapshot, config).orElse(null);
        int hiddenCount = teleportAvailabilityService.countUnavailable(snapshot, config);

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Route Feed")
            .right("Active")
            .rightColor(READY_GREEN)
            .build());
        if (nextTransport == null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Next: No unlocked transport")
                .leftColor(WARNING)
                .build());
        }
        else
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Next: " + nextTransport.toDisplayLine())
                .leftColor(READY_GREEN)
                .build());
        }
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Transports")
            .right(String.valueOf(snapshot.size()))
            .rightColor(MUTED)
            .build());
        if (hiddenCount > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Hidden Locked")
                .right(String.valueOf(hiddenCount))
                .rightColor(WARNING)
                .build());
        }
        if (lockedTransport != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Locked: " + lockedTransport.toDisplayLine())
                .leftColor(WARNING)
                .build());
        }
        addMinigameScanLine();

        List<RouteTransport> transports = snapshot.getTransports();
        int rows = Math.min(transports.size(), MAX_TRANSPORT_ROWS);
        for (int i = 0; i < rows; i++)
        {
            RouteTransport transport = transports.get(i);
            boolean locked = !teleportAvailabilityService.isAvailable(transport, config);
            panelComponent.getChildren().add(LineComponent.builder()
                .left((i + 1) + ". " + transport.toDisplayLine())
                .leftColor(locked ? WARNING : (transport.equals(nextTransport) ? READY_GREEN : MUTED))
                .build());
        }

        if (transports.size() > MAX_TRANSPORT_ROWS)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("+ " + (transports.size() - MAX_TRANSPORT_ROWS) + " more")
                .leftColor(MUTED)
                .build());
        }

        return super.render(graphics);
    }

    private void addMinigameScanLine()
    {
        int available = minigameTeleportUnlockState.getAvailableDestinationCount();
        int locked = minigameTeleportUnlockState.getLockedDestinationCount();
        int known = available + locked;
        int total = minigameTeleportUnlockState.getTotalDestinationCount();

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Minigames")
            .right(available + "/" + total + " Unlocked")
            .rightColor(known < total || locked > 0 ? WARNING : READY_GREEN)
            .build());
        if (known > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Stored Scan")
                .right(known + "/" + total)
                .rightColor(known < total ? WARNING : READY_GREEN)
                .build());
        }
    }
}
