package com.drewshelper.routing;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.api.coords.WorldPoint;

public final class DrewsHelperCollisionMap implements DrewsHelperMovementMap
{
    private static final String RESOURCE = "/collision-map.zip";
    private static final String CONFIDENCE_RESOURCE = "/collision-map-confidence.tsv";
    private static final int REGION_SIZE = 64;
    private static final int FLAG_COUNT = 2;
    private static final int NORTH_FLAG = 0;
    private static final int EAST_FLAG = 1;

    private final Map<RegionPosition, byte[]> compressedRegions;
    private final Map<RegionPosition, DrewsHelperDataProvenance> regionProvenance;
    private final DrewsHelperDataProvenance defaultProvenance;
    private final Map<RegionPosition, DrewsHelperFlagMap> loadedRegions = new ConcurrentHashMap<>();

    private DrewsHelperCollisionMap(
        Map<RegionPosition, byte[]> compressedRegions,
        ConfidenceMetadata confidenceMetadata
    )
    {
        this.compressedRegions = compressedRegions;
        regionProvenance = confidenceMetadata.regionProvenance;
        defaultProvenance = confidenceMetadata.defaultProvenance;
    }

    public static DrewsHelperCollisionMap loadDefault() throws IOException
    {
        InputStream stream = DrewsHelperCollisionMap.class.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            throw new IOException("Missing " + RESOURCE);
        }

        InputStream confidenceStream = DrewsHelperCollisionMap.class.getResourceAsStream(CONFIDENCE_RESOURCE);
        return new DrewsHelperCollisionMap(readCompressedRegions(stream), readConfidenceMetadata(confidenceStream));
    }

    public boolean hasRegion(int x, int y)
    {
        return compressedRegions.containsKey(regionPosition(x, y));
    }

    public DrewsHelperDataProvenance provenanceAt(WorldPoint point)
    {
        if (point == null)
        {
            return new DrewsHelperDataProvenance(
                DrewsHelperDataConfidence.CONTRADICTED,
                "missing-world-point"
            );
        }
        return provenanceAt(point.getX(), point.getY(), point.getPlane());
    }

    public DrewsHelperDataProvenance provenanceAt(int x, int y, int plane)
    {
        RegionPosition position = regionPosition(x, y);
        if (!compressedRegions.containsKey(position))
        {
            return new DrewsHelperDataProvenance(
                DrewsHelperDataConfidence.CONTRADICTED,
                "missing-collision-map-region:" + position.name()
            );
        }
        return regionProvenance.getOrDefault(position, defaultProvenance);
    }

    @Override
    public boolean canMoveNorth(int x, int y, int plane)
    {
        return get(x, y, plane, NORTH_FLAG);
    }

    @Override
    public boolean canMoveSouth(int x, int y, int plane)
    {
        return canMoveNorth(x, y - 1, plane);
    }

    @Override
    public boolean canMoveEast(int x, int y, int plane)
    {
        return get(x, y, plane, EAST_FLAG);
    }

    @Override
    public boolean canMoveWest(int x, int y, int plane)
    {
        return canMoveEast(x - 1, y, plane);
    }

    @Override
    public boolean canMoveNorthEast(int x, int y, int plane)
    {
        return canMoveNorth(x, y, plane)
            && canMoveEast(x, y + 1, plane)
            && canMoveEast(x, y, plane)
            && canMoveNorth(x + 1, y, plane);
    }

    @Override
    public boolean canMoveNorthWest(int x, int y, int plane)
    {
        return canMoveNorth(x, y, plane)
            && canMoveWest(x, y + 1, plane)
            && canMoveWest(x, y, plane)
            && canMoveNorth(x - 1, y, plane);
    }

    @Override
    public boolean canMoveSouthEast(int x, int y, int plane)
    {
        return canMoveSouth(x, y, plane)
            && canMoveEast(x, y - 1, plane)
            && canMoveEast(x, y, plane)
            && canMoveSouth(x + 1, y, plane);
    }

    @Override
    public boolean canMoveSouthWest(int x, int y, int plane)
    {
        return canMoveSouth(x, y, plane)
            && canMoveWest(x, y - 1, plane)
            && canMoveWest(x, y, plane)
            && canMoveSouth(x - 1, y, plane);
    }

    private boolean get(int x, int y, int plane, int flag)
    {
        return region(x, y).get(x, y, plane, flag);
    }

    private DrewsHelperFlagMap region(int x, int y)
    {
        RegionPosition position = regionPosition(x, y);
        return loadedRegions.computeIfAbsent(position, this::loadRegion);
    }

    private static RegionPosition regionPosition(int x, int y)
    {
        return new RegionPosition(x / REGION_SIZE, y / REGION_SIZE);
    }

    private DrewsHelperFlagMap loadRegion(RegionPosition position)
    {
        byte[] compressed = compressedRegions.get(position);
        if (compressed == null)
        {
            int minX = position.x * REGION_SIZE;
            int minY = position.y * REGION_SIZE;
            return new DrewsHelperFlagMap(minX, minY, minX + REGION_SIZE - 1, minY + REGION_SIZE - 1, FLAG_COUNT);
        }

        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
        {
            return new DrewsHelperFlagMap(gzip.readAllBytes(), FLAG_COUNT);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    private static Map<RegionPosition, byte[]> readCompressedRegions(InputStream stream) throws IOException
    {
        Map<RegionPosition, byte[]> regions = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(stream))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    continue;
                }

                String[] parts = entry.getName().split("_");
                if (parts.length != 2)
                {
                    throw new IOException("Bad collision-map region name: " + entry.getName());
                }

                RegionPosition position = new RegionPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                regions.put(position, zip.readAllBytes());
            }
        }
        return regions;
    }

    private static ConfidenceMetadata readConfidenceMetadata(InputStream stream) throws IOException
    {
        if (stream == null)
        {
            return new ConfidenceMetadata(
                DrewsHelperDataProvenance.INHERITED,
                Collections.emptyMap()
            );
        }

        DrewsHelperDataProvenance defaultProvenance = DrewsHelperDataProvenance.INHERITED;
        Map<RegionPosition, DrewsHelperDataProvenance> regions = new HashMap<>();
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

                String[] parts = line.split("\t", -1);
                if (parts.length < 2)
                {
                    throw new IOException("Bad collision confidence line " + lineNumber + ": " + line);
                }

                DrewsHelperDataProvenance provenance = new DrewsHelperDataProvenance(
                    DrewsHelperDataConfidence.parse(parts[1], DrewsHelperDataConfidence.INHERITED),
                    parts.length > 2 ? parts[2] : ""
                );
                String region = parts[0].trim();
                if ("*".equals(region) || "default".equalsIgnoreCase(region))
                {
                    defaultProvenance = provenance;
                }
                else
                {
                    regions.put(parseRegionPosition(region, lineNumber), provenance);
                }
            }
        }

        return new ConfidenceMetadata(defaultProvenance, Collections.unmodifiableMap(regions));
    }

    private static RegionPosition parseRegionPosition(String value, int lineNumber) throws IOException
    {
        String[] parts = value.split("_");
        if (parts.length != 2)
        {
            throw new IOException("Bad collision confidence region on line " + lineNumber + ": " + value);
        }

        try
        {
            return new RegionPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        catch (NumberFormatException ex)
        {
            throw new IOException("Bad collision confidence region on line " + lineNumber + ": " + value, ex);
        }
    }

    private static final class RegionPosition
    {
        private final int x;
        private final int y;

        private RegionPosition(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof RegionPosition))
            {
                return false;
            }

            RegionPosition position = (RegionPosition) other;
            return x == position.x && y == position.y;
        }

        @Override
        public int hashCode()
        {
            return x * 31 + y;
        }

        private String name()
        {
            return x + "_" + y;
        }
    }

    private static final class ConfidenceMetadata
    {
        private final DrewsHelperDataProvenance defaultProvenance;
        private final Map<RegionPosition, DrewsHelperDataProvenance> regionProvenance;

        private ConfidenceMetadata(
            DrewsHelperDataProvenance defaultProvenance,
            Map<RegionPosition, DrewsHelperDataProvenance> regionProvenance
        )
        {
            this.defaultProvenance = defaultProvenance == null
                ? DrewsHelperDataProvenance.INHERITED
                : defaultProvenance;
            this.regionProvenance = regionProvenance == null
                ? Collections.emptyMap()
                : regionProvenance;
        }
    }
}
