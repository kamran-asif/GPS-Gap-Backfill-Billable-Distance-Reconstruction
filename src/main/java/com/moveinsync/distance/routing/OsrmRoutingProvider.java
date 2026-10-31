package com.moveinsync.distance.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.distance.domain.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Real production road-network routing provider using Open Source Routing Machine (OSRM).
 * Calls OSRM REST API /route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false.
 */
public class OsrmRoutingProvider implements RoutingProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRoutingProvider.class);

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OsrmRoutingProvider(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public OsrmRoutingProvider(String baseUrl) {
        this(baseUrl, Duration.ofMillis(1500));
    }

    @Override
    public RouteResult fetchRoute(Coordinate start, Coordinate end) {
        // OSRM coordinates are formatted as {longitude},{latitude}
        String url = String.format(Locale.US,
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                baseUrl,
                start.getLongitude(), start.getLatitude(),
                end.getLongitude(), end.getLatitude());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OSRM routing returned HTTP {}: {}", response.statusCode(), response.body());
                return RouteResult.providerError("OSRM HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String code = root.path("code").asText("");
            if (!"Ok".equalsIgnoreCase(code)) {
                log.warn("OSRM routing response code not Ok: {}", code);
                return RouteResult.providerError("OSRM response code: " + code);
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return RouteResult.providerError("No routes found by OSRM");
            }

            JsonNode primaryRoute = routes.get(0);
            double distanceMeters = primaryRoute.path("distance").asDouble();
            double durationSeconds = primaryRoute.path("duration").asDouble();

            double distanceKm = distanceMeters / 1000.0;
            return RouteResult.success(distanceKm, Math.round(durationSeconds));

        } catch (Exception e) {
            log.warn("OSRM routing call failed for {} -> {}: {}", start, end, e.getMessage());
            return RouteResult.providerError("OSRM call exception: " + e.getMessage());
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}

