package com.moveinsync.distance.config;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.security.TenantContext;
import com.moveinsync.distance.service.TripDistanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Seeds realistic sample billing data on local application startup (non-test profile)
 * so the H2 Console (/h2-console) and GET endpoints immediately display versioned trips,
 * explainable segments, and signed billing adjustments (CREDIT_NOTE).
 */
@Component
@Profile("!test")
public class DemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final TripDistanceService tripService;

    public DemoDataInitializer(TripDistanceService tripService) {
        this.tripService = tripService;
    }

    @Override
    public void run(String... args) {
        String tenantId = "moveinsync-client-1";
        String tripId = "TRIP-BLR-1001";

        TenantContext.setTenantId(tenantId);
        try {
            if (tripService.getTrip(tripId, null).isPresent()) {
                return;
            }

            Instant t0 = Instant.parse("2026-10-29T18:00:00Z");
            List<GpsPing> initialPings = List.of(
                    new GpsPing(Coordinate.of(12.9352, 77.6245), t0, 10.0),
                    new GpsPing(Coordinate.of(12.9250, 77.6100), t0.plusSeconds(300), 12.0),
                    new GpsPing(Coordinate.of(12.8500, 77.5500), t0.plusSeconds(1620), 8.0)
            );

            List<TripMilestone> milestones = List.of(
                    new TripMilestone(
                            MilestoneType.DROP,
                            "EMP-RAJESH-1",
                            Coordinate.of(12.8800, 77.5800),
                            t0.plusSeconds(900),
                            RiderType.PLANNED,
                            MilestoneStatus.COMPLETED
                    )
            );

            OfficeLocation office = new OfficeLocation(
                    "OFF-BLR-ECOSPACE",
                    "Ecospace Tech Park",
                    Coordinate.of(12.9352, 77.6245)
            );

            TripEvidence initialEvidence = new TripEvidence(
                    tripId,
                    tenantId,
                    initialPings,
                    milestones,
                    List.of(),
                    office,
                    Map.of(),
                    t0,
                    t0.plusSeconds(1620),
                    Coordinate.of(12.9350, 77.6243),
                    EngineConfig.defaultConfig()
            );

            // 1. Seed Version 1 (Initial Invoice)
            tripService.calculateAndSave(initialEvidence, "demo-seed-v1");

            // 2. Seed Version 2 (Incremental Post-Billing Late Ping -> CREDIT_NOTE)
            List<GpsPing> incrementalLatePing = List.of(
                    new GpsPing(Coordinate.of(12.8350, 77.5350), t0.plusSeconds(1920), 9.0)
            );
            tripService.recomputeTripWithLatePings(
                    tripId,
                    incrementalLatePing,
                    List.of(),
                    "LATE_PINGS_UPLOADED_AFTER_BILLING"
            );

            log.info("Seeded production demo trip {} (v1 + v2 CREDIT_NOTE) for tenant {}", tripId, tenantId);
        } finally {
            TenantContext.clear();
        }
    }
}

