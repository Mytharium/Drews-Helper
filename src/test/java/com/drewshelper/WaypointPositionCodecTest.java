package com.drewshelper;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WaypointPositionCodecTest
{
    @Test
    public void encodesAndDecodesWaypointPosition()
    {
        WorldPoint point = new WorldPoint(3210, 3424, 0);

        String encoded = WaypointPositionCodec.encode(point);
        WorldPoint decoded = WaypointPositionCodec.decode(encoded);

        assertEquals("3210,3424,0", encoded);
        assertEquals(point, decoded);
    }

    @Test
    public void ignoresInvalidWaypointPosition()
    {
        assertNull(WaypointPositionCodec.decode(null));
        assertNull(WaypointPositionCodec.decode(""));
        assertNull(WaypointPositionCodec.decode("3210,3424"));
        assertNull(WaypointPositionCodec.decode("3210,abc,0"));
    }
}
