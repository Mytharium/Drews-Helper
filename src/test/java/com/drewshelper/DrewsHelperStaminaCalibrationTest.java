package com.drewshelper;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DrewsHelperStaminaCalibrationTest
{
    @Test
    public void oneUnitDropMeasuresTheIntervalExactly()
    {
        // Duration fell 40 -> 39 over 10 ticks, so one unit is 10 ticks.
        assertEquals(10, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 39, 110, 0));

        // And a one-tick unit is measured just as cleanly.
        assertEquals(1, DrewsHelperPlugin.staminaTicksPerUnit(200, 50, 199, 51, 0));
    }

    @Test
    public void multiUnitDropsAreRejectedRatherThanAveraged()
    {
        // A drop of more than one means ticks went unobserved - lag, a hop, a logout. Averaging
        // would understate the interval, so the previously known value is kept.
        assertEquals(10, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 37, 130, 10));
        assertEquals(0, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 37, 130, 0));
    }

    @Test
    public void nonDecrementsNeverCalibrate()
    {
        // Unchanged, or refilled by drinking another dose.
        assertEquals(7, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 40, 110, 7));
        assertEquals(7, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 200, 110, 7));
    }

    @Test
    public void absurdIntervalsAreRejected()
    {
        // A gap this long means we stopped observing, not that a unit is 500 ticks.
        assertEquals(10, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 39, 600, 10));
        assertEquals(0, DrewsHelperPlugin.staminaTicksPerUnit(40, 100, 39, 100, 0));
    }

    @Test
    public void storedUnitRoundTripsAndRejectsRubbish()
    {
        assertEquals(10, DrewsHelperPlugin.parseStaminaUnit("10"));
        assertEquals(1, DrewsHelperPlugin.parseStaminaUnit(" 1 "));

        // 0 means "not measured yet", which keeps the pre-forecast behaviour.
        assertEquals(0, DrewsHelperPlugin.parseStaminaUnit(null));
        assertEquals(0, DrewsHelperPlugin.parseStaminaUnit(""));
        assertEquals(0, DrewsHelperPlugin.parseStaminaUnit("banana"));
        assertEquals(0, DrewsHelperPlugin.parseStaminaUnit("0"));
        assertEquals(0, DrewsHelperPlugin.parseStaminaUnit("-5"));
        assertEquals(0, DrewsHelperPlugin.parseStaminaUnit("9999"));
    }
}
