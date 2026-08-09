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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperTransportGraph
{
    private static final String RESOURCE = "/drewshelper-transports.tsv";

    /** Parsed once, then filtered per policy/capability. Immutable once assigned. */
    private static volatile List<DrewsHelperTransportEdge> masterEdges;

    private final Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesBySource;
    private final Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesByDestination;
    private final int edgeCount;

    private DrewsHelperTransportGraph(Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesBySource)
    {
        Map<WorldPoint, List<DrewsHelperTransportEdge>> immutableEdges = new HashMap<>();
        Map<WorldPoint, List<DrewsHelperTransportEdge>> incomingEdges = new HashMap<>();
        int count = 0;
        for (Map.Entry<WorldPoint, List<DrewsHelperTransportEdge>> entry : edgesBySource.entrySet())
        {
            List<DrewsHelperTransportEdge> edges = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            immutableEdges.put(entry.getKey(), edges);
            count += edges.size();

            for (DrewsHelperTransportEdge edge : edges)
            {
                incomingEdges.computeIfAbsent(edge.getDestination(), key -> new ArrayList<>()).add(edge);
            }
        }

        this.edgesBySource = Collections.unmodifiableMap(immutableEdges);
        this.edgesByDestination = immutableEdgeMap(incomingEdges);
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

                // -1 keeps trailing empty requirement columns. Reads both the legacy
                // 4-column resource and the current 10-column one.
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
                    column(parts, 9)
                );

                edges.add(edge);
            }
        }
        return edges;
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

    public List<DrewsHelperTransportEdge> edgesFrom(WorldPoint source)
    {
        return edgesBySource.getOrDefault(source, Collections.emptyList());
    }

    public List<DrewsHelperTransportEdge> edgesTo(WorldPoint destination)
    {
        return edgesByDestination.getOrDefault(destination, Collections.emptyList());
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
