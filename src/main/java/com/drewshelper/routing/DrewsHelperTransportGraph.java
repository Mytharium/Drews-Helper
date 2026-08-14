package com.drewshelper.routing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperTransportGraph
{
    private static final String RESOURCE = "/drewshelper-transports.tsv";
    static final WorldPoint ANYWHERE = new WorldPoint(-1, -1, 0);

    /** Parsed once, then filtered per policy/capability. Immutable once assigned. */
    private static volatile List<DrewsHelperTransportEdge> masterEdges;
    /**
     * Adjacent walking edges covered by shortcut transports. These are blocked as ordinary
     * walking so an agility/grapple route can only cross them through the gated transport row.
     */
    private static volatile Set<Long> shortcutWalkingBlockers;

    private final Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesBySource;
    private final Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesByDestination;
    private final List<DrewsHelperTransportEdge> originlessEdges;
    private final int edgeCount;

    private DrewsHelperTransportGraph(Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesBySource)
    {
        Map<WorldPoint, List<DrewsHelperTransportEdge>> immutableEdges = new HashMap<>();
        Map<WorldPoint, List<DrewsHelperTransportEdge>> incomingEdges = new HashMap<>();
        List<DrewsHelperTransportEdge> originless = new ArrayList<>();
        int count = 0;
        for (Map.Entry<WorldPoint, List<DrewsHelperTransportEdge>> entry : edgesBySource.entrySet())
        {
            List<DrewsHelperTransportEdge> edges = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            immutableEdges.put(entry.getKey(), edges);
            count += edges.size();

            for (DrewsHelperTransportEdge edge : edges)
            {
                incomingEdges.computeIfAbsent(edge.getDestination(), key -> new ArrayList<>()).add(edge);
                if (edge.isOriginless())
                {
                    originless.add(edge);
                }
            }
        }

        this.edgesBySource = Collections.unmodifiableMap(immutableEdges);
        this.edgesByDestination = immutableEdgeMap(incomingEdges);
        this.originlessEdges = Collections.unmodifiableList(originless);
        this.edgeCount = count;
    }

    public static DrewsHelperTransportGraph empty()
    {
        return new DrewsHelperTransportGraph(Collections.emptyMap());
    }

    public static DrewsHelperTransportGraph of(Collection<DrewsHelperTransportEdge> edges)
    {
        Map<WorldPoint, List<DrewsHelperTransportEdge>> bySource = new HashMap<>();
        for (DrewsHelperTransportEdge edge : edges)
        {
            if (edge.getSource() == null || edge.getDestination() == null)
            {
                continue;
            }
            bySource.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
        }
        return new DrewsHelperTransportGraph(bySource);
    }

    public static DrewsHelperTransportGraph loadDefault(boolean includeWildernessTransports) throws IOException
    {
        return loadDefault(DrewsHelperTransportPolicy.builder()
            .wilderness(includeWildernessTransports)
            .build());
    }

    public static DrewsHelperTransportGraph loadDefault(DrewsHelperTransportPolicy policy) throws IOException
    {
        return loadDefault(policy, DrewsHelperPlayerCapability.UNRESTRICTED);
    }

    /**
     * Loads the transport graph keeping only edges that pass both gates: the family is enabled
     * by the policy, and this account currently meets the edge's skill and item requirements.
     */
    public static DrewsHelperTransportGraph loadDefault(
        DrewsHelperTransportPolicy policy,
        DrewsHelperPlayerCapability capability
    ) throws IOException
    {
        DrewsHelperTransportPolicy effectivePolicy = policy == null
            ? DrewsHelperTransportPolicy.baselineOnly()
            : policy;
        DrewsHelperPlayerCapability effectiveCapability = capability == null
            ? DrewsHelperPlayerCapability.UNRESTRICTED
            : capability;

        // The resource is parsed once and then filtered in memory. Re-reading and re-parsing
        // 7,000+ rows on every checkbox toggle or inventory change was the cost that made
        // toggling feel slow.
        List<DrewsHelperTransportEdge> edges = new ArrayList<>();
        for (DrewsHelperTransportEdge edge : masterEdges())
        {
            if (!effectivePolicy.allows(edge.getCategory()))
            {
                continue;
            }
            if (!effectiveCapability.satisfies(edge))
            {
                continue;
            }
            edges.add(edge);
        }
        return of(edges);
    }

    /**
     * Quest names referenced anywhere in the resource. The plugin resolves these against
     * RuneLite's Quest enum and snapshots only their completion state, so no quest list is
     * hardcoded here and an upstream data change needs no code change.
     */
    public static Set<String> requiredQuestNames() throws IOException
    {
        Set<String> names = new TreeSet<>();
        for (DrewsHelperTransportEdge edge : masterEdges())
        {
            addTerms(names, edge.getQuests());
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Distinct item requirement expressions referenced anywhere in the resource.
     *
     * <p>The capability snapshots these expressions into its route-cache signature, so a
     * capability change that flips any edge's item gate rebuilds the filtered graph.
     */
    public static Set<String> requiredItemRequirements() throws IOException
    {
        Set<String> requirements = new TreeSet<>();
        for (DrewsHelperTransportEdge edge : masterEdges())
        {
            String items = edge.getItems();
            if (items != null && !items.trim().isEmpty())
            {
                requirements.add(items.trim());
            }
        }
        return Collections.unmodifiableSet(requirements);
    }

    /** Varbit ids referenced anywhere in the resource. */
    public static Set<Integer> requiredVarbitIds() throws IOException
    {
        return varIds(true);
    }

    /** VarPlayer ids referenced anywhere in the resource. */
    public static Set<Integer> requiredVarPlayerIds() throws IOException
    {
        return varIds(false);
    }

    private static Set<Integer> varIds(boolean varbits) throws IOException
    {
        Set<String> terms = new TreeSet<>();
        for (DrewsHelperTransportEdge edge : masterEdges())
        {
            addTerms(terms, varbits ? edge.getVarbits() : edge.getVarPlayers());
        }

        Set<Integer> ids = new TreeSet<>();
        for (String term : terms)
        {
            // Terms look like id=value, id>value, id<value or id&mask - take the leading id.
            int end = 0;
            while (end < term.length() && Character.isDigit(term.charAt(end)))
            {
                end++;
            }
            if (end > 0)
            {
                try
                {
                    ids.add(Integer.valueOf(term.substring(0, end)));
                }
                catch (NumberFormatException ignored)
                {
                    // Not an id we can use; the capability treats unknown terms as satisfied.
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    private static void addTerms(Set<String> into, String requirement)
    {
        if (requirement == null || requirement.isEmpty())
        {
            return;
        }
        for (String term : requirement.split(";"))
        {
            String trimmed = term.trim();
            if (!trimmed.isEmpty())
            {
                into.add(trimmed);
            }
        }
    }

    static boolean blocksShortcutWalkingStep(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null || from.getPlane() != to.getPlane())
        {
            return false;
        }
        return blocksShortcutWalkingStep(
            from.getX(), from.getY(), from.getPlane(),
            to.getX() - from.getX(), to.getY() - from.getY()
        );
    }

    static boolean blocksShortcutWalkingStep(int x, int y, int plane, int moveX, int moveY)
    {
        int dx = Math.abs(moveX);
        int dy = Math.abs(moveY);
        if (dx > 1 || dy > 1 || (dx == 0 && dy == 0))
        {
            return false;
        }

        try
        {
            return shortcutWalkingBlockers().contains(adjacentEdgeKey(
                x, y, x + moveX, y + moveY, plane
            ));
        }
        catch (IOException ex)
        {
            return false;
        }
    }

    private static Set<Long> shortcutWalkingBlockers() throws IOException
    {
        Set<Long> cached = shortcutWalkingBlockers;
        if (cached != null)
        {
            return cached;
        }

        synchronized (DrewsHelperTransportGraph.class)
        {
            if (shortcutWalkingBlockers == null)
            {
                shortcutWalkingBlockers = Collections.unmodifiableSet(buildShortcutWalkingBlockers());
            }
            return shortcutWalkingBlockers;
        }
    }

    private static Set<Long> buildShortcutWalkingBlockers() throws IOException
    {
        Set<Long> blockers = new HashSet<>();
        for (DrewsHelperTransportEdge edge : masterEdges())
        {
            if (!isShortcutCorridor(edge))
            {
                continue;
            }
            addShortcutCorridor(blockers, edge.getSource(), edge.getDestination());
        }
        return blockers;
    }

    private static boolean isShortcutCorridor(DrewsHelperTransportEdge edge)
    {
        DrewsHelperTransportCategory category = edge.getCategory();
        return category == DrewsHelperTransportCategory.AGILITY_SHORTCUT
            || category == DrewsHelperTransportCategory.GRAPPLE_SHORTCUT;
    }

    private static void addShortcutCorridor(Set<Long> blockers, WorldPoint source, WorldPoint destination)
    {
        if (source == null || destination == null || source.equals(ANYWHERE)
            || destination.equals(ANYWHERE) || source.getPlane() != destination.getPlane())
        {
            return;
        }

        int deltaX = destination.getX() - source.getX();
        int deltaY = destination.getY() - source.getY();
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        if (steps == 0)
        {
            return;
        }

        int previousX = source.getX();
        int previousY = source.getY();
        for (int step = 1; step <= steps; step++)
        {
            int nextX = source.getX() + (int) Math.round(deltaX * step / (double) steps);
            int nextY = source.getY() + (int) Math.round(deltaY * step / (double) steps);
            addAdjacentEdgeKey(blockers, previousX, previousY, nextX, nextY, source.getPlane());
            previousX = nextX;
            previousY = nextY;
        }
    }

    private static void addAdjacentEdgeKey(
        Set<Long> blockers,
        int fromX,
        int fromY,
        int toX,
        int toY,
        int plane
    )
    {
        if (Math.abs(toX - fromX) > 1 || Math.abs(toY - fromY) > 1
            || (fromX == toX && fromY == toY))
        {
            return;
        }
        blockers.add(adjacentEdgeKey(fromX, fromY, toX, toY, plane));
    }

    private static long adjacentEdgeKey(WorldPoint a, WorldPoint b)
    {
        return adjacentEdgeKey(a.getX(), a.getY(), b.getX(), b.getY(), a.getPlane());
    }

    private static long adjacentEdgeKey(int ax, int ay, int bx, int by, int plane)
    {
        if (by < ay || (by == ay && bx < ax))
        {
            int swapX = ax;
            int swapY = ay;
            ax = bx;
            ay = by;
            bx = swapX;
            by = swapY;
        }

        long key = plane & 0x3L;
        key = (key << 15) | (ax & 0x7FFFL);
        key = (key << 15) | (ay & 0x7FFFL);
        key = (key << 2) | ((bx - ax + 1) & 0x3L);
        key = (key << 2) | ((by - ay + 1) & 0x3L);
        return key;
    }

    /** Every edge in the resource, unfiltered. Parsed on first use and then reused. */
    private static List<DrewsHelperTransportEdge> masterEdges() throws IOException
    {
        List<DrewsHelperTransportEdge> cached = masterEdges;
        if (cached != null)
        {
            return cached;
        }

        synchronized (DrewsHelperTransportGraph.class)
        {
            if (masterEdges == null)
            {
                masterEdges = Collections.unmodifiableList(parseResource());
            }
            return masterEdges;
        }
    }

    private static List<DrewsHelperTransportEdge> parseResource() throws IOException
    {
        InputStream stream = DrewsHelperTransportGraph.class.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            throw new IOException("Missing " + RESOURCE);
        }

        List<DrewsHelperTransportEdge> edges = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;
                if (line.trim().isEmpty() || line.startsWith("#"))
                {
                    continue;
                }

                // -1 keeps trailing empty requirement columns. Reads the legacy 4-column,
                // 10-column, and 11-column resources as well as the current confidence-tagged one.
                String[] parts = line.split("\t", -1);
                if (parts.length < 4)
                {
                    throw new IOException("Bad transport line " + lineNumber + ": " + line);
                }

                // An unknown family means the resource is ahead of this code. Skip the
                // row rather than failing the whole load.
                DrewsHelperTransportCategory category = parseCategory(parts[0]);
                if (category == null)
                {
                    continue;
                }

                DrewsHelperTransportEdge edge = new DrewsHelperTransportEdge(
                    parseWorldPoint(parts[1], lineNumber),
                    parseWorldPoint(parts[2], lineNumber),
                    category,
                    parts[3],
                    parseDuration(column(parts, 4)),
                    column(parts, 5),
                    column(parts, 6),
                    column(parts, 7),
                    column(parts, 8),
                    column(parts, 9),
                    parseWildernessLevel(column(parts, 10)),
                    DrewsHelperDataConfidence.parse(column(parts, 11), DrewsHelperDataConfidence.INHERITED),
                    column(parts, 12)
                );

                edges.add(edge);
            }
        }
        return edges;
    }

    /**
     * Wilderness cap for a row, defaulting to no cap.
     *
     * <p>Absent for every resource written before the column existed, and blank for every
     * transport upstream records no limit on, so both have to mean the same thing.
     */
    private static int parseWildernessLevel(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return DrewsHelperTransportEdge.NO_WILDERNESS_LIMIT;
        }
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException ignored)
        {
            return DrewsHelperTransportEdge.NO_WILDERNESS_LIMIT;
        }
    }

    private static DrewsHelperTransportCategory parseCategory(String value)
    {
        try
        {
            return DrewsHelperTransportCategory.valueOf(value.trim());
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }

    private static String column(String[] parts, int index)
    {
        return index < parts.length ? parts[index] : "";
    }

    private static int parseDuration(String value)
    {
        try
        {
            return Math.max(1, Integer.parseInt(value.trim()));
        }
        catch (NumberFormatException ex)
        {
            return 1;
        }
    }

    private static boolean isNonAdjacentHop(WorldPoint from, WorldPoint to)
    {
        return from.getPlane() != to.getPlane()
            || Math.abs(to.getX() - from.getX()) > 1
            || Math.abs(to.getY() - from.getY()) > 1;
    }

    public List<DrewsHelperTransportEdge> edgesFrom(WorldPoint source)
    {
        return edgesBySource.getOrDefault(source, Collections.emptyList());
    }

    public List<DrewsHelperTransportEdge> edgesTo(WorldPoint destination)
    {
        return edgesByDestination.getOrDefault(destination, Collections.emptyList());
    }

    public List<DrewsHelperTransportEdge> originlessEdges()
    {
        return originlessEdges;
    }

    public DrewsHelperTransportEdge findTransport(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null)
        {
            return null;
        }

        for (DrewsHelperTransportEdge edge : edgesFrom(from))
        {
            if (to.equals(edge.getDestination()))
            {
                return edge;
            }
        }

        if (isNonAdjacentHop(from, to))
        {
            for (DrewsHelperTransportEdge edge : originlessEdges)
            {
                if (to.equals(edge.getDestination()))
                {
                    return edge;
                }
            }
        }
        return null;
    }

    public boolean isEmpty()
    {
        return edgeCount == 0;
    }

    public int getEdgeCount()
    {
        return edgeCount;
    }

    List<DrewsHelperTransportEdge> allEdges()
    {
        List<DrewsHelperTransportEdge> all = new ArrayList<>(edgeCount);
        for (List<DrewsHelperTransportEdge> edges : edgesBySource.values())
        {
            all.addAll(edges);
        }
        return Collections.unmodifiableList(all);
    }

    private static Map<WorldPoint, List<DrewsHelperTransportEdge>> immutableEdgeMap(
        Map<WorldPoint, List<DrewsHelperTransportEdge>> mutableEdges
    )
    {
        Map<WorldPoint, List<DrewsHelperTransportEdge>> immutableEdges = new HashMap<>();
        for (Map.Entry<WorldPoint, List<DrewsHelperTransportEdge>> entry : mutableEdges.entrySet())
        {
            immutableEdges.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutableEdges);
    }

    private static WorldPoint parseWorldPoint(String encoded, int lineNumber) throws IOException
    {
        String[] parts = encoded.split(",");
        if (parts.length != 3)
        {
            throw new IOException("Bad point on transport line " + lineNumber + ": " + encoded);
        }

        try
        {
            return new WorldPoint(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
        catch (NumberFormatException ex)
        {
            throw new IOException("Bad point on transport line " + lineNumber + ": " + encoded, ex);
        }
    }
}
