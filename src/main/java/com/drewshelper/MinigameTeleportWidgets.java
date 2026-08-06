package com.drewshelper;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

final class MinigameTeleportWidgets
{
    private static final int MAX_DESTINATION_WIDGET_HEIGHT = 80;
    private static final int MAX_DESTINATION_WIDGET_WIDTH = 520;

    static final int[] SPELL_WIDGETS = {
        InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_STANDARD,
        InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_ANCIENT,
        InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_ARCEUUS,
        InterfaceID.MagicSpellbook.TELEPORT_MINIGAME_LUNAR
    };

    static final WidgetInfo[] MAGIC_TAB_WIDGETS = {
        WidgetInfo.FIXED_VIEWPORT_MAGIC_TAB,
        WidgetInfo.FIXED_VIEWPORT_MAGIC_ICON,
        WidgetInfo.RESIZABLE_VIEWPORT_MAGIC_TAB,
        WidgetInfo.RESIZABLE_VIEWPORT_MAGIC_ICON,
        WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_MAGIC_ICON
    };

    private static final int[] INTERFACE_ROOTS = {
        InterfaceID.Minigames.UNIVERSE,
        InterfaceID.Minigames.CONTENT,
        InterfaceID.Minigames.MINIGAMES,
        InterfaceID.Grouping.UNIVERSE,
        InterfaceID.Grouping.CURRENTGAME,
        InterfaceID.Grouping.DROPDOWN_CONTENTS,
        InterfaceID.Grouping.PLAYERLIST,
        InterfaceID.Grouping.TELEPORT
    };

    private static final int[] DESTINATION_ROWS = {
        InterfaceID.Minigames.MINIGAME_1,
        InterfaceID.Minigames.MINIGAME_2,
        InterfaceID.Minigames.MINIGAME_3,
        InterfaceID.Minigames.MINIGAME_4,
        InterfaceID.Minigames.MINIGAME_5,
        InterfaceID.Minigames.MINIGAME_6,
        InterfaceID.Minigames.MINIGAME_7,
        InterfaceID.Minigames.MINIGAME_8,
        InterfaceID.Minigames.MINIGAME_9,
        InterfaceID.Minigames.MINIGAME_10,
        InterfaceID.Minigames.MINIGAME_11,
        InterfaceID.Minigames.MINIGAME_12,
        InterfaceID.Minigames.MINIGAME_13,
        InterfaceID.Minigames.MINIGAME_14,
        InterfaceID.Minigames.MINIGAME_15,
        InterfaceID.Minigames.MINIGAME_16,
        InterfaceID.Minigames.MINIGAME_17,
        InterfaceID.Minigames.MINIGAME_18,
        InterfaceID.Minigames.MINIGAME_19,
        InterfaceID.Minigames.MINIGAME_20,
        InterfaceID.Minigames.MINIGAME_21,
        InterfaceID.Grouping.CURRENTGAME
    };

    static List<Widget> findVisibleDestinationWidgets(Client client)
    {
        if (client == null)
        {
            return Collections.emptyList();
        }

        Set<Widget> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int widgetId : DESTINATION_ROWS)
        {
            collectDestinationCandidates(client.getWidget(widgetId), candidates);
        }
        for (int widgetId : INTERFACE_ROOTS)
        {
            collectDestinationCandidates(client.getWidget(widgetId), candidates);
        }

        if (candidates.isEmpty())
        {
            Widget[] roots = client.getWidgetRoots();
            if (roots != null)
            {
                for (Widget root : roots)
                {
                    collectDestinationCandidates(root, candidates);
                }
            }
        }

        return new ArrayList<>(candidates);
    }

    static Widget getGroupingCurrentGame(Client client)
    {
        return client == null ? null : client.getWidget(InterfaceID.Grouping.CURRENTGAME);
    }

    static Widget getGroupingDropdown(Client client)
    {
        return client == null ? null : client.getWidget(InterfaceID.Grouping.DROPDOWN_TOP);
    }

    static Widget getGroupingTeleportButton(Client client)
    {
        return client == null ? null : client.getWidget(InterfaceID.Grouping.TELEPORT);
    }

    static boolean isGroupingCurrentGame(Widget widget)
    {
        return widget != null && widget.getId() == InterfaceID.Grouping.CURRENTGAME;
    }

    static boolean matchesDestination(Widget widget, String destinationKey)
    {
        if (!isVisible(widget))
        {
            return false;
        }

        String widgetDestination = MinigameTeleportNames.knownDestinationName(widget);
        if (widgetDestination.isEmpty())
        {
            widgetDestination = MinigameTeleportNames.destinationName(widget);
        }

        return MinigameTeleportNames.matchesDestination(
            MinigameTeleportNames.normalize(widgetDestination),
            destinationKey);
    }

    static boolean isVisible(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return false;
        }

        Rectangle bounds = widget.getBounds();
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private static void collectDestinationCandidates(Widget widget, Set<Widget> candidates)
    {
        if (!isVisible(widget))
        {
            return;
        }

        if (isDestinationCandidate(widget))
        {
            candidates.add(widget);
        }

        Widget[] children = widget.getNestedChildren();
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collectDestinationCandidates(child, candidates);
        }
    }

    private static boolean isDestinationCandidate(Widget widget)
    {
        String text = MinigameTeleportNames.allWidgetText(widget);
        if (MinigameTeleportNames.knownDestinationCount(text) != 1)
        {
            return false;
        }

        Rectangle bounds = widget.getBounds();
        return bounds.height <= MAX_DESTINATION_WIDGET_HEIGHT
            && bounds.width <= MAX_DESTINATION_WIDGET_WIDTH;
    }

    private MinigameTeleportWidgets()
    {
    }
}
