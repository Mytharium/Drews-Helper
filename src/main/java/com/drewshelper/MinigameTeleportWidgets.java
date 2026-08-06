package com.drewshelper;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

final class MinigameTeleportWidgets
{
    private static final int MINIGAMES_GROUP = WidgetInfo.TO_GROUP(InterfaceID.Minigames.UNIVERSE);
    private static final int GROUPING_GROUP = WidgetInfo.TO_GROUP(InterfaceID.Grouping.UNIVERSE);
    private static final int MAX_HIGHLIGHT_WIDGET_HEIGHT = 140;
    private static final int MAX_HIGHLIGHT_WIDGET_WIDTH = 900;
    private static final int MIN_VISIBLE_WIDGET_SIZE = 4;

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
        return findVisibleKnownDestinationWidgets(client, true);
    }

    static List<Widget> findVisibleScanWidgets(Client client)
    {
        return findVisibleKnownDestinationWidgets(client, true);
    }

    private static List<Widget> findVisibleKnownDestinationWidgets(Client client, boolean singleDestinationOnly)
    {
        if (client == null)
        {
            return Collections.emptyList();
        }

        Set<Widget> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int widgetId : DESTINATION_ROWS)
        {
            collectDestinationCandidates(client.getWidget(widgetId), candidates, visited, singleDestinationOnly);
        }
        for (int widgetId : INTERFACE_ROOTS)
        {
            collectDestinationCandidates(client.getWidget(widgetId), candidates, visited, singleDestinationOnly);
        }

        Widget[] roots = client.getWidgetRoots();
        if (roots != null)
        {
            for (Widget root : roots)
            {
                if (isMinigameInterfaceWidget(root))
                {
                    collectDestinationCandidates(root, candidates, visited, singleDestinationOnly);
                }
            }
        }

        List<Widget> widgets = new ArrayList<>(candidates);
        widgets.sort(Comparator.comparingInt(MinigameTeleportWidgets::area));
        return widgets;
    }

    static Widget getGroupingCurrentGame(Client client)
    {
        return client == null ? null : client.getWidget(InterfaceID.Grouping.CURRENTGAME);
    }

    static Widget getGroupingTeleportButton(Client client)
    {
        return client == null ? null : client.getWidget(InterfaceID.Grouping.TELEPORT);
    }

    static boolean isMinigameInterfaceOpen(Client client)
    {
        if (client == null)
        {
            return false;
        }

        for (int widgetId : INTERFACE_ROOTS)
        {
            Widget widget = client.getWidget(widgetId);
            if (isVisible(widget))
            {
                return true;
            }
        }

        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return false;
        }

        for (Widget root : roots)
        {
            if (isMinigameInterfaceWidget(root) && isVisible(root))
            {
                return true;
            }
        }

        return false;
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
        Rectangle bounds = visibleBounds(widget);
        return bounds != null
            && bounds.width >= MIN_VISIBLE_WIDGET_SIZE
            && bounds.height >= MIN_VISIBLE_WIDGET_SIZE;
    }

    static Rectangle visibleBounds(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }

        Rectangle bounds = widget.getBounds();
        if (!hasArea(bounds))
        {
            return null;
        }

        Rectangle visible = new Rectangle(bounds);
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Widget parent = widget.getParent();
        while (parent != null && visited.add(parent))
        {
            if (parent.isHidden())
            {
                return null;
            }

            Rectangle parentBounds = parent.getBounds();
            if (hasArea(parentBounds))
            {
                visible = visible.intersection(parentBounds);
                if (!hasArea(visible))
                {
                    return null;
                }
            }

            parent = parent.getParent();
        }

        return visible;
    }

    static List<Widget> getAllChildren(Widget widget)
    {
        if (widget == null)
        {
            return Collections.emptyList();
        }

        List<Widget> children = new ArrayList<>();
        addChildren(children, widget.getChildren());
        addChildren(children, widget.getDynamicChildren());
        addChildren(children, widget.getStaticChildren());
        addChildren(children, widget.getNestedChildren());
        return children;
    }

    private static void collectDestinationCandidates(
        Widget widget,
        Set<Widget> candidates,
        Set<Widget> visited,
        boolean singleDestinationOnly)
    {
        if (!isVisible(widget) || !visited.add(widget))
        {
            return;
        }

        if (isDestinationCandidate(widget, singleDestinationOnly))
        {
            candidates.add(widget);
        }

        for (Widget child : getAllChildren(widget))
        {
            collectDestinationCandidates(child, candidates, visited, singleDestinationOnly);
        }
    }

    private static void addChildren(List<Widget> children, Widget[] childArray)
    {
        if (childArray == null)
        {
            return;
        }

        for (Widget child : childArray)
        {
            if (child != null && !children.contains(child))
            {
                children.add(child);
            }
        }
    }

    private static boolean isDestinationCandidate(Widget widget, boolean singleDestinationOnly)
    {
        String text = MinigameTeleportNames.allWidgetText(widget);
        int destinationCount = MinigameTeleportNames.knownDestinationCount(text);
        if (destinationCount == 0 || (singleDestinationOnly && destinationCount != 1))
        {
            return false;
        }

        Rectangle bounds = visibleBounds(widget);
        return bounds != null
            && (!singleDestinationOnly
                || (bounds.height <= MAX_HIGHLIGHT_WIDGET_HEIGHT
                    && bounds.width <= MAX_HIGHLIGHT_WIDGET_WIDTH));
    }

    private static int area(Widget widget)
    {
        Rectangle bounds = visibleBounds(widget);
        if (bounds == null)
        {
            return Integer.MAX_VALUE;
        }

        return bounds.width * bounds.height;
    }

    private static boolean hasArea(Rectangle bounds)
    {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private static boolean isMinigameInterfaceWidget(Widget widget)
    {
        if (widget == null)
        {
            return false;
        }

        int group = WidgetInfo.TO_GROUP(widget.getId());
        return group == MINIGAMES_GROUP || group == GROUPING_GROUP;
    }

    private MinigameTeleportWidgets()
    {
    }
}
