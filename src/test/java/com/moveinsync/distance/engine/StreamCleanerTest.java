package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.Coordinate;
import com.moveinsync.distance.domain.EngineConfig;
import com.moveinsync.distance.domain.GpsPing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamCleanerTest {

    private final EngineConfig config = EngineConfig.defaultConfig();
    private final StreamCleaner cleaner = new StreamCleaner(config);

    @Test
    @DisplayName("Deduplication removes duplicate timestamp pings, retaining the one with higher accuracy")
    void testDeduplicationPrefersHigherAccuracy() {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");

        GpsPing poorAccuracy = new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 45.0);
        GpsPing betterAccuracy = new GpsPing(Coordinate.of(12.9001, 77.5001), t0, 8.0);

        StreamCleanerResult result = cleaner.clean(List.of(poorAccuracy, betterAccuracy));

        assertThat(result.getCleanedPings()).hasSize(1);
        assertThat(result.getCleanedPings().get(0).getAccuracyMeters()).isEqualTo(8.0);
        assertThat(result.getDuplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Out-of-order pings are ordered strictly chronologically")
    void testChronologicalOrdering() {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-29T10:00:10Z");
        Instant t2 = Instant.parse("2026-10-29T10:00:20Z");

        GpsPing p0 = new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 10.0);
        GpsPing p1 = new GpsPing(Coordinate.of(12.9010, 77.5010), t1, 10.0);
        GpsPing p2 = new GpsPing(Coordinate.of(12.9020, 77.5020), t2, 10.0);

        // Feed out of order: p2, p0, p1
        StreamCleanerResult result = cleaner.clean(List.of(p2, p0, p1));

        assertThat(result.getCleanedPings()).containsExactly(p0, p1, p2);
    }

    @Test
    @DisplayName("Drops low-accuracy fixes with horizontal accuracy > 50 meters")
    void testDropLowAccuracy() {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-29T10:00:10Z");

        GpsPing goodPing = new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 15.0);
        GpsPing badPing = new GpsPing(Coordinate.of(12.9010, 77.5010), t1, 120.0); // > 50m threshold

        StreamCleanerResult result = cleaner.clean(List.of(goodPing, badPing));

        assertThat(result.getCleanedPings()).containsExactly(goodPing);
        assertThat(result.getLowAccuracyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Teleport burst rejection: device sends a burst of pings 9 km away for 4 seconds (8,000 km/h implied speed)")
    void testTeleportJumpBurstRejection() {
        // "A device sends a burst of pings 9 km away for 4 seconds (a bad fix).
        // The implied speed is 8,000 km/h, so those points are dropped and the trip distance is unchanged."
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-29T10:00:04Z"); // 4s later, 9 km away
        Instant t2 = Instant.parse("2026-10-29T10:00:05Z"); // burst continuation
        Instant t3 = Instant.parse("2026-10-29T10:00:10Z"); // returns to legitimate route (100m from t0)

        Coordinate legitStart = Coordinate.of(12.9716, 77.5946);
        // Point ~9 km away
        Coordinate badFixLocation = Coordinate.of(13.0526, 77.5946);
        Coordinate legitContinuation = Coordinate.of(12.9725, 77.5955); // 130m away in 10s (~47 km/h)

        GpsPing p0 = new GpsPing(legitStart, t0, 10.0);
        GpsPing pBad1 = new GpsPing(badFixLocation, t1, 10.0);
        GpsPing pBad2 = new GpsPing(badFixLocation, t2, 10.0);
        GpsPing pLegit = new GpsPing(legitContinuation, t3, 10.0);

        StreamCleanerResult result = cleaner.clean(List.of(p0, pBad1, pBad2, pLegit));

        assertThat(result.getCleanedPings()).containsExactly(p0, pLegit);
        assertThat(result.getTeleportCount()).isEqualTo(2);
    }
}
