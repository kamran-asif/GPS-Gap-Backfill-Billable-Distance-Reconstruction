package com.moveinsync.distance.routing;

import com.moveinsync.distance.domain.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OsrmRoutingProviderTest {

    @Test
    @DisplayName("Should handle network failure gracefully and return RouteResult with error status")
    void shouldHandleConnectionFailureGracefully() {
        // Use non-routable localhost port to ensure instant connection refused
        OsrmRoutingProvider provider = new OsrmRoutingProvider("http://localhost:59999", Duration.ofMillis(200));

        Coordinate start = new Coordinate(12.9352, 77.6245);
        Coordinate end = new Coordinate(12.9250, 77.6100);

        RouteResult result = provider.fetchRoute(start, end);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RouteStatus.PROVIDER_ERROR);
        assertThat(result.getErrorMessage()).contains("OSRM call exception");
    }
}

