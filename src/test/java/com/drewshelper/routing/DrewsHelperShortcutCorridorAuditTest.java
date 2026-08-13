package com.drewshelper.routing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DrewsHelperShortcutCorridorAuditTest
{
    private static final String TRANSPORTS_RESOURCE = "/drewshelper-transports.tsv";

    @Test
    public void allShortcutCorridorStepsAreTransportOnlyWalkingBlockers() throws Exception
    {
        ShortcutAudit audit = auditShortcutRows();

        assertEquals(557, audit.agilityRows);
        assertEquals(15, audit.grappleRows);
        assertEquals(481, audit.samePlaneAgilityRows);
        assertEquals(11, audit.samePlaneGrappleRows);
        assertEquals(76, audit.planeChangingAgilityRows);
        assertEquals(4, audit.planeChangingGrappleRows);
        assertEquals(2_892, audit.agilityCorridorSteps);
        assertEquals(89, audit.grappleCorridorSteps);
        assertEquals(
            "every adjacent step in a same-plane agility/grapple corridor must be blocked "
                + "as ordinary walking; otherwise an unqualified route can bypass the "
                + "filtered transport edge",
            0,
            audit.unblockedCorridorSteps
        );
    }

    @Test
    public void zeroCapabilityDoesNotLoadAnyShortcutTransportEdges() throws Exception
    {
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.loadDefault(
            DrewsHelperTransportPolicy.baselineOnly(),
            DrewsHelperPlayerCapability.builder().build()
        );

        for (DrewsHelperTransportEdge edge : graph.allEdges())
        {
            assertFalse(
                "shortcut transport leaked into zero-capability graph: " + edge.getLabel()
                    + " " + edge.getSource() + " -> " + edge.getDestination(),
                edge.getCategory() == DrewsHelperTransportCategory.AGILITY_SHORTCUT
                    || edge.getCategory() == DrewsHelperTransportCategory.GRAPPLE_SHORTCUT
            );
        }
    }

    private static ShortcutAudit auditShortcutRows() throws IOException
    {
        ShortcutAudit audit = new ShortcutAudit();
        try (InputStream in = DrewsHelperTransportGraph.class.getResourceAsStream(TRANSPORTS_RESOURCE))
        {
            if (in == null)
            {
                throw new IOException("Missing " + TRANSPORTS_RESOURCE);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.trim().isEmpty() || line.startsWith("#"))
                    {
                        continue;
                    }

                    String[] columns = line.split("\t", -1);
                    if (columns.length < 4 || !isShortcutCategory(columns[0]))
                    {
                        continue;
                    }

                    audit.recordRow(columns[0]);
                    auditCorridor(columns[0], parse(columns[1]), parse(columns[2]), audit);
                }
            }
        }
        return audit;
    }

    private static boolean isShortcutCategory(String category)
    {
        return "AGILITY_SHORTCUT".equals(category) || "GRAPPLE_SHORTCUT".equals(category);
    }

    private static void auditCorridor(
        String category,
        WorldPoint source,
        WorldPoint destination,
        ShortcutAudit audit
    )
    {
        if (source.getPlane() != destination.getPlane())
        {
            audit.recordPlaneChange(category);
            return;
        }

        int deltaX = destination.getX() - source.getX();
        int deltaY = destination.getY() - source.getY();
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        if (steps == 0)
        {
            return;
        }

        audit.recordSamePlaneRow(category);

        int previousX = source.getX();
        int previousY = source.getY();
        for (int step = 1; step <= steps; step++)
        {
            int nextX = source.getX() + (int) Math.round(deltaX * step / (double) steps);
            int nextY = source.getY() + (int) Math.round(deltaY * step / (double) steps);
            audit.recordCorridorStep(category);
            if (!DrewsHelperTransportGraph.blocksShortcutWalkingStep(
                previousX,
                previousY,
                source.getPlane(),
                nextX - previousX,
                nextY - previousY
            ))
            {
                audit.unblockedCorridorSteps++;
            }
            previousX = nextX;
            previousY = nextY;
        }
    }

    private static WorldPoint parse(String encoded)
    {
        String[] parts = encoded.split(",");
        assertEquals("bad transport point: " + encoded, 3, parts.length);
        return new WorldPoint(
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        );
    }

    private static final class ShortcutAudit
    {
        private int agilityRows;
        private int grappleRows;
        private int samePlaneAgilityRows;
        private int samePlaneGrappleRows;
        private int planeChangingAgilityRows;
        private int planeChangingGrappleRows;
        private int agilityCorridorSteps;
        private int grappleCorridorSteps;
        private int unblockedCorridorSteps;

        private void recordRow(String category)
        {
            if ("AGILITY_SHORTCUT".equals(category))
            {
                agilityRows++;
            }
            else
            {
                grappleRows++;
            }
        }

        private void recordSamePlaneRow(String category)
        {
            if ("AGILITY_SHORTCUT".equals(category))
            {
                samePlaneAgilityRows++;
            }
            else
            {
                samePlaneGrappleRows++;
            }
        }

        private void recordPlaneChange(String category)
        {
            if ("AGILITY_SHORTCUT".equals(category))
            {
                planeChangingAgilityRows++;
            }
            else
            {
                planeChangingGrappleRows++;
            }
        }

        private void recordCorridorStep(String category)
        {
            if ("AGILITY_SHORTCUT".equals(category))
            {
                agilityCorridorSteps++;
            }
            else
            {
                grappleCorridorSteps++;
            }
        }
    }
}
