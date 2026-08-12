package com.drewshelper.routing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The ground overlay draws only the leg being walked, so a later leg crossing the current one
 * cannot be mistaken for it underfoot. The truncation has to fail safe: anything it cannot
 * resolve must fall back to the full path, because drawing nothing looks exactly like a route
 * that failed to calculate.
 */
public class DrewsHelperCurrentLegPathTest
{
    private static final WorldPoint A = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint B = new WorldPoint(3201, 3200, 0);
    private static final WorldPoint C = new WorldPoint(3202, 3200, 0);
    private static final WorldPoint D = new WorldPoint(3203, 3200, 0);

    @Test
    public void stopsAtTheFirstWaypointRatherThanTheLastOne()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(A, B, C, D), Arrays.asList(B, D), 3);

        assertEquals(Arrays.asList(A, B), snapshot.getCurrentLegPath());
        assertEquals(Arrays.asList(A, B, C, D), snapshot.getPath());
    }

    @Test
    public void aSingleWaypointRouteIsUnchanged()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(A, B, C), Collections.singletonList(C), 2);

        assertEquals(snapshot.getPath(), snapshot.getCurrentLegPath());
    }

    @Test
    public void fallsBackToTheWholePathWhenTheWaypointIsNotOnIt()
    {
        WorldPoint offPath = new WorldPoint(2500, 2500, 0);
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(A, B, C), Collections.singletonList(offPath), 2);

        assertEquals(snapshot.getPath(), snapshot.getCurrentLegPath());
    }

    @Test
    public void fallsBackWhenThereAreNoDestinations()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(A, B), Collections.emptyList(), 1);

        assertEquals(snapshot.getPath(), snapshot.getCurrentLegPath());
    }

    @Test
    public void anEmptyPathStaysEmpty()
    {
        List<WorldPoint> empty = Collections.emptyList();
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            empty, Collections.singletonList(A), 0);

        assertEquals(empty, snapshot.getCurrentLegPath());
    }

    @Test
    public void aWaypointOnTheFirstTileYieldsThatTileAlone()
    {
        DrewsHelperRouteSnapshot snapshot = DrewsHelperRouteSnapshot.ready(
            Arrays.asList(A, B, C), Arrays.asList(A, C), 2);

        assertEquals(Collections.singletonList(A), snapshot.getCurrentLegPath());
    }
}
