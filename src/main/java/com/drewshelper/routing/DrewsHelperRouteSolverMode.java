package com.drewshelper.routing;

public enum DrewsHelperRouteSolverMode
{
    A_STAR("A*"),
    BFS("BFS");

    private final String displayName;

    DrewsHelperRouteSolverMode(String displayName)
    {
        this.displayName = displayName;
    }

    public DrewsHelperRouteSolverMode opposite()
    {
        return this == A_STAR ? BFS : A_STAR;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
