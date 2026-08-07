package com.drewshelper.routing;

interface DrewsHelperMovementMap
{
    boolean canMoveNorth(int x, int y, int plane);

    boolean canMoveSouth(int x, int y, int plane);

    boolean canMoveEast(int x, int y, int plane);

    boolean canMoveWest(int x, int y, int plane);

    boolean canMoveNorthEast(int x, int y, int plane);

    boolean canMoveNorthWest(int x, int y, int plane);

    boolean canMoveSouthEast(int x, int y, int plane);

    boolean canMoveSouthWest(int x, int y, int plane);
}
