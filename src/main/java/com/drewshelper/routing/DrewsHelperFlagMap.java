package com.drewshelper.routing;

import java.nio.ByteBuffer;
import java.util.BitSet;

final class DrewsHelperFlagMap
{
    private static final int PLANE_COUNT = 4;

    private final BitSet flags;
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int width;
    private final int height;
    private final int flagCount;

    DrewsHelperFlagMap(int minX, int minY, int maxX, int maxY, int flagCount)
    {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.flagCount = flagCount;
        width = maxX - minX + 1;
        height = maxY - minY + 1;
        flags = new BitSet(width * height * PLANE_COUNT * flagCount);
    }

    DrewsHelperFlagMap(byte[] bytes, int flagCount)
    {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        minX = buffer.getInt();
        minY = buffer.getInt();
        maxX = buffer.getInt();
        maxY = buffer.getInt();
        this.flagCount = flagCount;
        width = maxX - minX + 1;
        height = maxY - minY + 1;
        flags = BitSet.valueOf(buffer);
    }

    boolean get(int x, int y, int plane, int flag)
    {
        if (x < minX || x > maxX || y < minY || y > maxY || plane < 0 || plane >= PLANE_COUNT)
        {
            return false;
        }

        return flags.get(index(x, y, plane, flag));
    }

    private int index(int x, int y, int plane, int flag)
    {
        if (flag < 0 || flag >= flagCount)
        {
            throw new IndexOutOfBoundsException("Unsupported movement flag " + flag);
        }

        return (plane * width * height + (y - minY) * width + (x - minX)) * flagCount + flag;
    }
}
