package com.drewshelper.routing;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DrewsHelperBrokenRaftRouteTest
{
    @Test
    public void brokenRaftShortcutIsNotPlainWalkingWithoutRequirements() throws Exception
    {
        DrewsHelperPlayerCapability noGrappleKit = DrewsHelperPlayerCapability.builder()
            .skill("AGILITY", 30)
            .skill("RANGED", 21)
            .skill("STRENGTH", 50)
            .build();
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.loadDefault(
            DrewsHelperTransportPolicy.baselineOnly(),
            noGrappleKit
        );
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(
            DrewsHelperCollisionMap.loadDefault(),
            graph
        );

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(3246, 3184, 0),
            Collections.singletonList(new WorldPoint(3260, 3175, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertFalse(
            "route must not walk across the broken raft water strip without requirements: "
                + route.getPath(),
            usesPlainWalkingAcrossBrokenRaftStrip(route.getPath())
        );
    }

    @Test
    public void brokenRaftShortcutStillRoutesAsTransportWithRequirements() throws Exception
    {
        WorldPoint shortcutSource = new WorldPoint(3246, 3179, 0);
        WorldPoint shortcutDestination = new WorldPoint(3259, 3179, 0);
        DrewsHelperPlayerCapability grappleKit = DrewsHelperPlayerCapability.builder()
            .skill("AGILITY", 30)
            .skill("RANGED", 37)
            .skill("STRENGTH", 50)
            .item(ItemID.XBOWS_CROSSBOW_MITHRIL, 1)
            .item(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1)
            .build();
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.loadDefault(
            DrewsHelperTransportPolicy.baselineOnly(),
            grappleKit
        );
        DrewsHelperWalkingRouteEngine engine = new DrewsHelperWalkingRouteEngine(
            DrewsHelperCollisionMap.loadDefault(),
            graph
        );

        DrewsHelperRouteSnapshot route = engine.solve(
            new WorldPoint(3246, 3184, 0),
            Collections.singletonList(new WorldPoint(3260, 3175, 0))
        );

        assertEquals(DrewsHelperRouteStatus.READY, route.getStatus());
        assertRouteContainsStep(route.getPath(), shortcutSource, shortcutDestination);
    }

    private static boolean usesPlainWalkingAcrossBrokenRaftStrip(List<WorldPoint> path)
    {
        for (WorldPoint point : path)
        {
            if (point.getPlane() == 0
                && point.getX() >= 3247
                && point.getX() <= 3258
                && point.getY() >= 3176
                && point.getY() <= 3184)
            {
                return true;
            }
        }
        return false;
    }

    private static void assertRouteContainsStep(List<WorldPoint> path, WorldPoint from, WorldPoint to)
    {
        for (int index = 1; index < path.size(); index++)
        {
            if (from.equals(path.get(index - 1)) && to.equals(path.get(index)))
            {
                return;
            }
        }
        org.junit.Assert.fail("route did not use expected shortcut step " + from + " -> " + to
            + ": " + path);
    }
}
