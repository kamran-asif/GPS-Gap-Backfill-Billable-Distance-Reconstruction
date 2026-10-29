package com.moveinsync.distance.service;

import com.moveinsync.distance.domain.Coordinate;
import com.moveinsync.distance.domain.EngineConfig;
import com.moveinsync.distance.domain.GpsPing;
import com.moveinsync.distance.domain.TripEvidence;
import com.moveinsync.distance.entity.TripEntity;
import com.moveinsync.distance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationTest {

    @Autowired
    private TripDistanceService tripService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant A cannot read or access Tenant B's trips or adjustments")
    void testTenantDataSegregation() {
        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");

        // 1. Ingest trip for Tenant A ("tenant-apple")
        TenantContext.setTenantId("tenant-apple");
        TripEvidence evidenceApple = new TripEvidence(
                "trip-confidential-100",
                "tenant-apple",
                List.of(
                        new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 10.0),
                        new GpsPing(Coordinate.of(12.9050, 77.5050), t0.plusSeconds(30), 10.0)
                ),
                List.of(),
                List.of(),
                null,
                Map.of(),
                t0,
                t0.plusSeconds(30),
                null,
                EngineConfig.defaultConfig()
        );
        tripService.calculateAndSave(evidenceApple);

        // Tenant A can successfully fetch their trip
        Optional<TripEntity> appleTrip = tripService.getTrip("trip-confidential-100", null);
        assertThat(appleTrip).isPresent();
        assertThat(appleTrip.get().getTenantId()).isEqualTo("tenant-apple");

        // 2. Switch context to Tenant B ("tenant-google")
        TenantContext.setTenantId("tenant-google");

        // Tenant B attempts to read Tenant A's trip: MUST BE EMPTY!
        Optional<TripEntity> leakAttempt = tripService.getTrip("trip-confidential-100", null);
        assertThat(leakAttempt).isEmpty();

        // Tenant B attempts to fetch adjustments: MUST BE EMPTY!
        assertThat(tripService.getAdjustments("trip-confidential-100")).isEmpty();
    }
}
