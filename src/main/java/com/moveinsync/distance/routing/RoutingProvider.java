package com.moveinsync.distance.routing;

import com.moveinsync.distance.domain.Coordinate;

public interface RoutingProvider {
    RouteResult fetchRoute(Coordinate start, Coordinate end);
}
