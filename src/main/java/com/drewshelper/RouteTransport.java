package com.drewshelper;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;

final class RouteTransport
{
    private static final int UNDEFINED_WORLD_POINT = -1;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final int MAX_DISPLAY_LENGTH = 48;

    private final String objectInfo;
    private final String displayInfo;
    private final int destinationPacked;

    RouteTransport(String objectInfo, String displayInfo)
    {
        this(objectInfo, displayInfo, UNDEFINED_WORLD_POINT);
    }

    RouteTransport(String objectInfo, String displayInfo, int destinationPacked)
    {
        this.objectInfo = clean(objectInfo);
        this.displayInfo = clean(displayInfo);
        this.destinationPacked = destinationPacked;
    }

    String getObjectInfo()
    {
        return objectInfo;
    }

    String getDisplayInfo()
    {
        return displayInfo;
    }

    OptionalInt getDestinationPacked()
    {
        return destinationPacked == UNDEFINED_WORLD_POINT
            ? OptionalInt.empty()
            : OptionalInt.of(destinationPacked);
    }

    boolean hasInstruction()
    {
        return !objectInfo.isEmpty() || !displayInfo.isEmpty();
    }

    String toDisplayLine()
    {
        if (!objectInfo.isEmpty() && !displayInfo.isEmpty())
        {
            return truncate(objectInfo + " -> " + displayInfo);
        }
        if (!objectInfo.isEmpty())
        {
            return truncate(objectInfo);
        }
        if (!displayInfo.isEmpty())
        {
            return truncate(displayInfo);
        }
        return "Unknown transport";
    }

    String toSearchText()
    {
        return (objectInfo + " " + displayInfo).trim().toLowerCase();
    }

    private static String clean(String value)
    {
        if (value == null)
        {
            return "";
        }

        return HTML_TAG.matcher(value).replaceAll("").trim();
    }

    private static String truncate(String value)
    {
        if (value.length() <= MAX_DISPLAY_LENGTH)
        {
            return value;
        }

        return value.substring(0, MAX_DISPLAY_LENGTH - 3) + "...";
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof RouteTransport))
        {
            return false;
        }
        RouteTransport other = (RouteTransport) obj;
        return destinationPacked == other.destinationPacked
            && objectInfo.equals(other.objectInfo)
            && displayInfo.equals(other.displayInfo);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(objectInfo, displayInfo, destinationPacked);
    }
}
