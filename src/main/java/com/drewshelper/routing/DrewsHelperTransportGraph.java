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
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperTransportGraph
{
    private static final String RESOURCE = "/drewshelper-transports.tsv";

    private final Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesBySource;
    private final int edgeCount;

    private DrewsHelperTransportGraph(Map<WorldPoint, List<DrewsHelperTransportEdge>> edgesBySource)
    {
        Map<WorldPoint, List<DrewsHelperTransportEdge>> immutableEdges = new HashMap<>();
        int count = 0;
        for (Map.Entry<WorldPoint, List<DrewsHelperTransportEdge>> entry : edgesBySource.entrySet())
        {
            List<DrewsHelperTransportEdge> edges = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            immutableEdges.put(entry.getKey(), edges);
            count += edges.size();
        }

        this.edgesBySource = Collections.unmodifiableMap(immutableEdges);
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

                String[] parts = line.split("\t", 4);
                if (parts.length < 4)
                {
                    throw new IOException("Bad transport line " + lineNumber + ": " + line);
                }

                DrewsHelperTransportCategory category = DrewsHelperTransportCategory.valueOf(parts[0]);
                if (category == DrewsHelperTransportCategory.WILDERNESS && !includeWildernessTransports)
                {
                    continue;
                }

                edges.add(new DrewsHelperTransportEdge(
                    parseWorldPoint(parts[1], lineNumber),
                    parseWorldPoint(parts[2], lineNumber),
                    category,
                    parts[3]
                ));
            }
        }
        return of(edges);
    }

    public List<DrewsHelperTransportEdge> edgesFrom(WorldPoint source)
    {
        return edgesBySource.getOrDefault(source, Collections.emptyList());
    }

    public boolean isEmpty()
    {
        return edgeCount == 0;
    }

    public int getEdgeCount()
    {
        return edgeCount;
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
