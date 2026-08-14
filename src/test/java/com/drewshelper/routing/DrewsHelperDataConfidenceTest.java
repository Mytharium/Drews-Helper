package com.drewshelper.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class DrewsHelperDataConfidenceTest
{
    @Test
    public void parserAcceptsTheFourRecorderFirstTiers()
    {
        assertEquals(DrewsHelperDataConfidence.INHERITED,
            DrewsHelperDataConfidence.parse("inherited", DrewsHelperDataConfidence.CONTRADICTED));
        assertEquals(DrewsHelperDataConfidence.INFERRED,
            DrewsHelperDataConfidence.parse("INFERRED", DrewsHelperDataConfidence.CONTRADICTED));
        assertEquals(DrewsHelperDataConfidence.CONFIRMED,
            DrewsHelperDataConfidence.parse("confirmed", DrewsHelperDataConfidence.CONTRADICTED));
        assertEquals(DrewsHelperDataConfidence.CONTRADICTED,
            DrewsHelperDataConfidence.parse("contradicted", DrewsHelperDataConfidence.INHERITED));
    }

    @Test
    public void unknownConfidenceFallsBackInsteadOfBreakingLegacyResources()
    {
        assertEquals(DrewsHelperDataConfidence.INHERITED,
            DrewsHelperDataConfidence.parse("", DrewsHelperDataConfidence.INHERITED));
        assertEquals(DrewsHelperDataConfidence.INFERRED,
            DrewsHelperDataConfidence.parse("CACHE_MAGIC", DrewsHelperDataConfidence.INFERRED));
    }

    @Test
    public void collisionMapCarriesExplicitDefaultConfidence() throws Exception
    {
        DrewsHelperCollisionMap map = DrewsHelperCollisionMap.loadDefault();

        DrewsHelperDataProvenance provenance = map.provenanceAt(new WorldPoint(3209, 3220, 0));

        assertEquals(DrewsHelperDataConfidence.INFERRED, provenance.getConfidence());
        assertEquals("osrs-cache-live:d0188-all-region-rebuild", provenance.getSource());
    }

    @Test
    public void transportRowsCarryInheritedAndConfirmedConfidence() throws Exception
    {
        DrewsHelperTransportGraph graph = DrewsHelperTransportGraph.loadDefault(
            DrewsHelperTransportPolicy.builder()
                .wilderness(true)
                .magicMushtrees(true)
                .build());

        DrewsHelperTransportEdge inherited = edgeFrom(
            graph,
            new WorldPoint(1270, 3002, 0),
            new WorldPoint(1274, 3002, 0),
            "Climb Rock 57604");
        DrewsHelperTransportEdge confirmed = edgeFrom(
            graph,
            new WorldPoint(2935, 3450, 0),
            new WorldPoint(2936, 3450, 0),
            "Open Gate (Taverley wall)");

        assertEquals(DrewsHelperDataConfidence.INHERITED, inherited.getConfidence());
        assertTrue(inherited.getProvenance().startsWith("skretzo:"));
        assertEquals(DrewsHelperDataConfidence.CONFIRMED, confirmed.getConfidence());
        assertEquals("tools/transport-overrides.tsv", confirmed.getProvenance());
    }

    private static DrewsHelperTransportEdge edgeFrom(
        DrewsHelperTransportGraph graph,
        WorldPoint source,
        WorldPoint destination,
        String label
    )
    {
        List<DrewsHelperTransportEdge> edges = graph.edgesFrom(source);
        for (DrewsHelperTransportEdge edge : edges)
        {
            if (destination.equals(edge.getDestination()) && label.equals(edge.getLabel()))
            {
                return edge;
            }
        }
        throw new AssertionError("missing transport edge " + source + " -> " + destination + " " + label);
    }
}
