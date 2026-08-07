package com.drewshelper.routing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DrewsHelperCollisionMap implements DrewsHelperMovementMap
{
    private static final String RESOURCE = "/collision-map.zip";
    private static final int REGION_SIZE = 64;
    private static final int FLAG_COUNT = 2;
    private static final int NORTH_FLAG = 0;
    private static final int EAST_FLAG = 1;

    private final Map<RegionPosition, byte[]> compressedRegions;
    private final Map<RegionPosition, DrewsHelperFlagMap> loadedRegions = new ConcurrentHashMap<>();

    private DrewsHelperCollisionMap(Map<RegionPosition, byte[]> compressedRegions)
    {
        this.compressedRegions = compressedRegions;
    }

    public static DrewsHelperCollisionMap loadDefault() throws IOException
    {
        InputStream stream = DrewsHelperCollisionMap.class.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            throw new IOException("Missing " + RESOURCE);
        }

        return new DrewsHelperCollisionMap(readCompressedRegions(stream));
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
        RegionPosition position = new RegionPosition(x / REGION_SIZE, y / REGION_SIZE);
        return loadedRegions.computeIfAbsent(position, this::loadRegion);
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
    }
}
