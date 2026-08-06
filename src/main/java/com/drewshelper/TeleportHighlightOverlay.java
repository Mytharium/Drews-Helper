package com.drewshelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TeleportHighlightOverlay extends Overlay
{
    private static final Color HIGHLIGHT = new Color(95, 190, 115);
    private static final Color HIGHLIGHT_FILL = new Color(95, 190, 115, 45);
    private static final Color LOCKED = new Color(230, 170, 70);
    private static final Color LOCKED_FILL = new Color(230, 170, 70, 45);
    private static final Stroke HIGHLIGHT_STROKE = new BasicStroke(2);

    private final Client client;
    private final DrewsHelperConfig config;
    private final RouteTransportState routeTransportState;
    private final TeleportAvailabilityService teleportAvailabilityService;
    private final MinigameTeleportUnlockState minigameTeleportUnlockState;

    @Inject
    TeleportHighlightOverlay(
        DrewsHelperPlugin plugin,
        Client client,
        DrewsHelperConfig config,
        RouteTransportState routeTransportState,
        TeleportAvailabilityService teleportAvailabilityService,
        MinigameTeleportUnlockState minigameTeleportUnlockState)
    {
        super(plugin);
        this.client = client;
        this.config = config;
        this.routeTransportState = routeTransportState;
        this.teleportAvailabilityService = teleportAvailabilityService;
        this.minigameTeleportUnlockState = minigameTeleportUnlockState;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.teleportAssistEnabled())
        {
            return null;
        }

        Optional<RouteTransport> routeTransport = getHighlightTransport();
        if (routeTransport.isEmpty() || !teleportAvailabilityService.isMinigameTeleport(routeTransport.get()))
        {
            return null;
        }

        MinigameTeleportStatus status = minigameTeleportUnlockState.getStatus(routeTransport.get());
        boolean minigameInterfaceOpen = MinigameTeleportWidgets.isMinigameInterfaceOpen(client);
        if (!minigameInterfaceOpen
            && status != MinigameTeleportStatus.LOCKED
            && !renderFirstVisibleSpellWidget(graphics))
        {
            renderFirstVisibleMagicTab(graphics);
        }
        renderMinigameDestination(graphics, routeTransport.get(), status);

        return null;
    }

    private Optional<RouteTransport> getHighlightTransport()
    {
        RouteTransportSnapshot snapshot = routeTransportState.getSnapshot();
        Optional<RouteTransport> next = snapshot.getNextTransport();
        if (next.isPresent() && teleportAvailabilityService.isMinigameTeleport(next.get()))
        {
            return next;
        }

        Optional<RouteTransport> available = teleportAvailabilityService.getFirstAvailable(snapshot, config);
        if (available.isPresent() && teleportAvailabilityService.isMinigameTeleport(available.get()))
        {
            return available;
        }

        return Optional.empty();
    }

    private boolean renderFirstVisibleSpellWidget(Graphics2D graphics)
    {
        for (int widgetId : MinigameTeleportWidgets.SPELL_WIDGETS)
        {
            if (renderWidget(graphics, client.getWidget(widgetId), HIGHLIGHT, HIGHLIGHT_FILL))
            {
                return true;
            }
        }
        return false;
    }

    private boolean renderFirstVisibleMagicTab(Graphics2D graphics)
    {
        for (net.runelite.api.widgets.WidgetInfo widgetInfo : MinigameTeleportWidgets.MAGIC_TAB_WIDGETS)
        {
            if (renderWidget(graphics, client.getWidget(widgetInfo), HIGHLIGHT, HIGHLIGHT_FILL))
            {
                return true;
            }
        }
        return false;
    }

    private void renderMinigameDestination(
        Graphics2D graphics,
        RouteTransport routeTransport,
        MinigameTeleportStatus status)
    {
        String destination = MinigameTeleportNames.destinationKey(routeTransport);
        if (destination.isEmpty())
        {
            return;
        }

        Color outline = status == MinigameTeleportStatus.LOCKED ? LOCKED : HIGHLIGHT;
        Color fill = status == MinigameTeleportStatus.LOCKED ? LOCKED_FILL : HIGHLIGHT_FILL;
        Widget bestMatch = null;
        int bestArea = -1;
        for (Widget row : MinigameTeleportWidgets.findVisibleDestinationWidgets(client))
        {
            if (MinigameTeleportWidgets.matchesDestination(row, destination))
            {
                Rectangle bounds = MinigameTeleportWidgets.visibleBounds(row);
                int area = bounds == null ? -1 : bounds.width * bounds.height;
                if (area > bestArea)
                {
                    bestMatch = row;
                    bestArea = area;
                }
            }
        }

        Widget currentGame = MinigameTeleportWidgets.getGroupingCurrentGame(client);
        if (bestMatch != null)
        {
            renderWidget(graphics, bestMatch, outline, fill);
            if (MinigameTeleportWidgets.matchesDestination(currentGame, destination))
            {
                renderWidget(graphics, MinigameTeleportWidgets.getGroupingTeleportButton(client), outline, fill);
            }
            return;
        }

        if (MinigameTeleportWidgets.matchesDestination(currentGame, destination))
        {
            renderWidget(graphics, currentGame, outline, fill);
            renderWidget(graphics, MinigameTeleportWidgets.getGroupingTeleportButton(client), outline, fill);
            return;
        }
    }

    private static boolean renderWidget(Graphics2D graphics, Widget widget, Color outline, Color fill)
    {
        Rectangle bounds = MinigameTeleportWidgets.visibleBounds(widget);
        if (bounds == null)
        {
            return false;
        }

        Rectangle highlightBounds = new Rectangle(bounds);
        highlightBounds.grow(2, 2);

        Stroke previousStroke = graphics.getStroke();
        graphics.setColor(fill);
        graphics.fill(highlightBounds);
        graphics.setColor(outline);
        graphics.setStroke(HIGHLIGHT_STROKE);
        graphics.draw(highlightBounds);
        graphics.setStroke(previousStroke);
        return true;
    }
}
