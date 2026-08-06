package com.drewshelper;

import javax.inject.Singleton;

@Singleton
final class RouteTransportState
{
    private volatile RouteTransportSnapshot snapshot = RouteTransportSnapshot.EMPTY;

    RouteTransportSnapshot getSnapshot()
    {
        return snapshot;
    }

    void update(RouteTransportSnapshot snapshot)
    {
        this.snapshot = snapshot == null ? RouteTransportSnapshot.EMPTY : snapshot;
    }

    void clear()
    {
        snapshot = RouteTransportSnapshot.EMPTY;
    }
}
