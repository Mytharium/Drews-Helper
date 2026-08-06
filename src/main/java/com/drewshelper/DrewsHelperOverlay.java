package com.drewshelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
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
        List<RouteTransport> transports = instructionTransports(snapshot);
        if (snapshot.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Route Step")
                .right("0/0")
                .rightColor(WARNING)
                .build());
            addMinigameScanLine();
            addLockedRoutesLine(snapshot);
            return super.render(graphics);
        }

        int currentStep = transports.isEmpty() ? 0 : 1;

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Current Route Step")
            .right(currentStep + "/" + transports.size())
            .rightColor(WARNING)
            .build());
        for (int i = 0; i < transports.size(); i++)
        {
            RouteTransport transport = transports.get(i);
            boolean locked = !teleportAvailabilityService.isAvailable(transport, config);
            panelComponent.getChildren().add(LineComponent.builder()
                .left((i + 1) + ". " + transport.toDisplayLine())
                .leftColor(locked ? WARNING : (i == 0 ? READY_GREEN : MUTED))
                .build());
        }

        addMinigameScanLine();
        addLockedRoutesLine(snapshot);

        return super.render(graphics);
    }

    private List<RouteTransport> instructionTransports(RouteTransportSnapshot snapshot)
    {
        List<RouteTransport> transports = new ArrayList<>();
        for (RouteTransport transport : snapshot.getTransports())
        {
            if (transport.hasInstruction())
            {
                transports.add(transport);
            }
        }

        return transports;
    }

    private void addMinigameScanLine()
    {
        int available = minigameTeleportUnlockState.getAvailableDestinationCount();
        int locked = minigameTeleportUnlockState.getLockedDestinationCount();
        int known = available + locked;
        int total = minigameTeleportUnlockState.getTotalDestinationCount();

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Minigame Teleports")
            .right(available + "/" + total + " Unlocked")
            .rightColor(known < total || locked > 0 ? WARNING : READY_GREEN)
            .build());
    }

    private void addLockedRoutesLine(RouteTransportSnapshot snapshot)
    {
        if (!config.filterUnavailableTeleports())
        {
            return;
        }

        List<RouteTransport> lockedRoutes = teleportAvailabilityService.getUnavailableTransports(snapshot, config);
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Locked Routes")
            .right(lockedRoutes.isEmpty() ? "None" : String.valueOf(lockedRoutes.size()))
            .rightColor(lockedRoutes.isEmpty() ? MUTED : WARNING)
            .build());

        for (int i = 0; i < lockedRoutes.size(); i++)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left((i + 1) + ". " + lockedRoutes.get(i).toDisplayLine())
                .leftColor(WARNING)
                .build());
        }
    }
}
