package com.drewshelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.widgets.Widget;

final class MinigameTeleportNames
{
    private static final String[] KNOWN_DESTINATIONS = {
        "Barbarian Assault",
        "Blast Furnace",
        "Burthorpe Games Room",
        "Castle Wars",
        "Clan Wars",
        "Fishing Trawler",
        "Giants' Foundry",
        "Guardians of the Rift",
        "Last Man Standing",
        "Mage Training Arena",
        "Nightmare Zone",
        "Pest Control",
        "Rat Pits",
        "Shades of Mort'ton",
        "Soul Wars",
        "Tithe Farm",
        "Trouble Brewing",
        "TzHaar Fight Pit"
    };

    private MinigameTeleportNames()
    {
    }

    static String destinationName(RouteTransport routeTransport)
    {
        if (routeTransport == null)
        {
            return "";
        }

        String label = !routeTransport.getDisplayInfo().isEmpty()
            ? routeTransport.getDisplayInfo()
            : routeTransport.getObjectInfo();

        return label
            .replace("Minigame Teleport", "")
            .replace("Grouping Teleport", "")
            .replaceAll(":\\s*\\d+\\.\\s*", " ")
            .trim();
    }

    static String destinationKey(RouteTransport routeTransport)
    {
        return normalize(destinationName(routeTransport));
    }

    static String destinationName(Widget widget)
    {
        String knownDestination = knownDestinationName(widget);
        if (!knownDestination.isEmpty())
        {
            return knownDestination;
        }

        List<String> texts = new ArrayList<>();
        collectText(widget, texts, Collections.newSetFromMap(new IdentityHashMap<>()));
        for (String text : texts)
        {
            String clean = clean(text);
            if (!clean.isEmpty() && !looksLikeStatusText(clean))
            {
                return clean;
            }
        }

        return "";
    }

    static String allWidgetText(Widget widget)
    {
        List<String> texts = new ArrayList<>();
        collectText(widget, texts, Collections.newSetFromMap(new IdentityHashMap<>()));
        return String.join(" ", texts);
    }

    static String normalize(String text)
    {
        return clean(text)
            .toLowerCase(Locale.ROOT)
            .replace("'", "")
            .replace("\u2019", "")
            .replace("minigame teleport", "")
            .replace("grouping teleport", "")
            .replaceAll(":\\s*\\d+\\.\\s*", " ")
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    static String knownDestinationName(Widget widget)
    {
        return knownDestinationName(allWidgetText(widget));
    }

    static String knownDestinationName(String text)
    {
        List<String> destinations = knownDestinationNames(text);
        return destinations.isEmpty() ? "" : destinations.get(0);
    }

    static List<String> knownDestinationNames(String text)
    {
        String normalized = normalize(text);
        if (normalized.isEmpty())
        {
            return Collections.emptyList();
        }

        List<String> matches = new ArrayList<>();
        for (String destination : KNOWN_DESTINATIONS)
        {
            String key = normalize(destination);
            if (matchesDestination(normalized, key))
            {
                matches.add(destination);
            }
        }

        return matches;
    }

    static boolean matchesDestination(String normalizedA, String normalizedB)
    {
        if (normalizedA == null || normalizedB == null)
        {
            return false;
        }

        String first = normalizedA.trim();
        String second = normalizedB.trim();
        return !first.isEmpty()
            && !second.isEmpty()
            && (first.equals(second) || first.contains(second) || second.contains(first));
    }

    static int knownDestinationCount(String text)
    {
        String normalized = normalize(text);
        if (normalized.isEmpty())
        {
            return 0;
        }

        return knownDestinationNames(normalized).size();
    }

    static int totalDestinationCount()
    {
        return KNOWN_DESTINATIONS.length;
    }

    private static void collectText(Widget widget, List<String> texts, Set<Widget> visited)
    {
        if (!MinigameTeleportWidgets.isVisible(widget) || !visited.add(widget))
        {
            return;
        }

        String text = clean(widget.getText());
        if (!text.isEmpty())
        {
            texts.add(text);
        }

        String name = clean(widget.getName());
        if (!name.isEmpty())
        {
            texts.add(name);
        }

        for (Widget child : MinigameTeleportWidgets.getAllChildren(widget))
        {
            collectText(child, texts, visited);
        }
    }

    private static String clean(String text)
    {
        if (text == null)
        {
            return "";
        }

        return text
            .replaceAll("<[^>]+>", "")
            .replace('\u00a0', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean looksLikeStatusText(String text)
    {
        String normalized = normalize(text);
        return normalized.equals("teleport")
            || normalized.equals("join")
            || normalized.equals("select")
            || normalized.equals("locked")
            || normalized.equals("requirements not met");
    }
}
