package com.drewshelper.routing;

import net.runelite.api.coords.WorldPoint;

/**
 * The nearest transport edge to an anchor tile whose destination is where the player actually
 * ended up.
 *
 * <p>Transport edges carry no object id, so a traversal can only be attributed positionally: the
 * graph answers "what edges start at this exact tile" and nothing more. Players rarely stand on
 * the exact tile a row was authored against, so an exact-only lookup would report a missing edge
 * for almost every real interaction. This sweeps outward instead and records how far away the
 * match was, leaving the question of how far is "close enough" to be decided later from the
 * offsets actually observed rather than guessed at now.
 */
public final class DrewsHelperTraversalMatch
{
    /**
     * How far the sweep looks, in tiles. This bounds the SEARCH only - it is not a claim that a
     * match at 16 tiles is credible. Nothing is discarded by distance; the offset is recorded as
     * measured so any threshold can be applied to already-collected data.
     */
    public static final int SEARCH_RADIUS = 16;

    private final int offset;
    private final WorldPoint source;
    private final WorldPoint destination;

    private DrewsHelperTraversalMatch(int offset, WorldPoint source, WorldPoint destination)
    {
        this.offset = offset;
        this.source = source;
        this.destination = destination;
    }

    /** Chebyshev tile distance from the anchor to the matched edge's source. 0 means exact. */
    public int getOffset()
    {
        return offset;
    }

    public WorldPoint getSource()
    {
        return source;
    }

    public WorldPoint getDestination()
    {
        return destination;
    }

    /**
     * Returns the closest edge to {@code anchor} that ends at {@code actual}, or null if no edge
     * within {@link #SEARCH_RADIUS} explains the move. Rings are swept outward, so the first hit
     * is the nearest one and no later ring can beat it.
     */
    public static DrewsHelperTraversalMatch nearest(
        DrewsHelperTransportGraph graph,
        WorldPoint anchor,
        WorldPoint actual
    )
    {
        if (graph == null || anchor == null || actual == null)
        {
            return null;
        }

        for (int radius = 0; radius <= SEARCH_RADIUS; radius++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dy = -radius; dy <= radius; dy++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius)
                    {
                        // Interior of the square was covered by a smaller radius already.
                        continue;
                    }

                    WorldPoint candidate = new WorldPoint(
                        anchor.getX() + dx, anchor.getY() + dy, anchor.getPlane());
                    for (DrewsHelperTransportEdge edge : graph.edgesFrom(candidate))
                    {
                        if (actual.equals(edge.getDestination()))
                        {
                            return new DrewsHelperTraversalMatch(
                                radius, candidate, edge.getDestination());
                        }
                    }
                }
            }
        }
        return null;
    }
}
