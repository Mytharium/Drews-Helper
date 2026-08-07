package com.drewshelper;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Drew's Helper",
    description = "UI shell for Drew's Helper.",
    tags = {"ui", "helper"}
)
public class DrewsHelperPlugin extends Plugin
{
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DrewsHelperOverlay overlay;

    @Override
    protected void startUp()
    {
        overlayManager.remove(overlay);
        overlayManager.add(overlay);
        log.debug("Drew's Helper UI shell started");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        log.debug("Drew's Helper UI shell stopped");
    }

    @Provides
    DrewsHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DrewsHelperConfig.class);
    }
}
