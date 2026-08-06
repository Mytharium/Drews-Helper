package com.drewshelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

final class RouteTransportSnapshot
{
    static final RouteTransportSnapshot EMPTY = new RouteTransportSnapshot(Collections.emptyList());

    private final List<RouteTransport> transports;

    RouteTransportSnapshot(List<RouteTransport> transports)
    {
        this.transports = Collections.unmodifiableList(new ArrayList<>(transports));
    }

    boolean isEmpty()
    {
        return transports.isEmpty();
    }

    int size()
    {
        return transports.size();
    }

    List<RouteTransport> getTransports()
    {
        return transports;
    }

    Optional<RouteTransport> getNextTransport()
    {
        return transports.stream().filter(RouteTransport::hasInstruction).findFirst();
    }

    OptionalInt getLastTransportDestinationPacked()
    {
        for (int i = transports.size() - 1; i >= 0; i--)
        {
            OptionalInt destination = transports.get(i).getDestinationPacked();
            if (destination.isPresent())
            {
                return destination;
            }
        }

        return OptionalInt.empty();
    }
}
