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
    private static final int PANEL_WIDTH = 320;

    private final DrewsHelperConfig config;

    @Inject
    DrewsHelperOverlay(DrewsHelperPlugin plugin, DrewsHelperConfig config)
    {
        super(plugin);
        this.config = config;
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

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Plugin UI")
            .right("Ready")
            .rightColor(MUTED)
            .build());

        return super.render(graphics);
    }
}
