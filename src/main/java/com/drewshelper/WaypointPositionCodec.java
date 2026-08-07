package com.drewshelper;

import net.runelite.api.coords.WorldPoint;

final class WaypointPositionCodec
{
    private WaypointPositionCodec()
    {
    }

    static String encode(WorldPoint point)
    {
        if (point == null)
        {
            return "";
        }
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    static WorldPoint decode(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }

        String[] parts = value.split(",");
        if (parts.length != 3)
        {
            return null;
        }

        try
        {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int plane = Integer.parseInt(parts[2].trim());
            return new WorldPoint(x, y, plane);
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }
}
