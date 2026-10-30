package com.moveinsync.distance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.distance.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("E2E API: Ingest trip, calculate distance, verify explainable segments and provider stats")
    void testCalculateTripE2E() throws Exception {
        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");

        TripCalculationRequest request = new TripCalculationRequest();
        request.setTripId("e2e-trip-1");
        request.setTenantId("tenant-e2e");
        request.setRawPings(List.of(
                new GpsPingDto(12.9000, 77.5000, t0, 10.0),
                new GpsPingDto(12.9030, 77.5030, t0.plusSeconds(30), 10.0),
                new GpsPingDto(12.9060, 77.5060, t0.plusSeconds(60), 10.0)
        ));
        request.setOfficeLocation(new OfficeLocationDto("OFF-1", "Tech Park", 12.9352, 77.6245));
        request.setTripStartTime(t0);
        request.setTripEndTime(t0.plusSeconds(60));

        mockMvc.perform(post("/api/v1/trips/calculate")
                        .header("X-Tenant-ID", "tenant-e2e")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value("e2e-trip-1"))
                .andExpect(jsonPath("$.tenantId").value("tenant-e2e"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalDistanceKm").isNumber())
                .andExpect(jsonPath("$.segments", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.segments[0].rule").exists())
                .andExpect(jsonPath("$.segments[0].explanation").exists());
    }

    @Test
    @DisplayName("E2E API: Recompute trip with late pings produces signed delta adjustment")
    void testRecomputeTripE2E() throws Exception {
        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");

        // First, calculate base trip
        TripCalculationRequest baseRequest = new TripCalculationRequest();
        baseRequest.setTripId("e2e-trip-recompute");
        baseRequest.setTenantId("tenant-e2e");
        baseRequest.setRawPings(List.of(
                new GpsPingDto(12.9000, 77.5000, t0, 10.0),
                new GpsPingDto(12.9050, 77.5050, t0.plusSeconds(30), 10.0)
        ));
        mockMvc.perform(post("/api/v1/trips/calculate")
                        .header("X-Tenant-ID", "tenant-e2e")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(baseRequest)))
                .andExpect(status().isOk());

        // Now, recompute with late pings
        RecomputeRequest recomputeRequest = new RecomputeRequest();
        recomputeRequest.setReason("LATE_PINGS_FROM_CAB_CACHE");
        recomputeRequest.setLatePings(List.of(
                new GpsPingDto(12.9000, 77.5000, t0, 10.0),
                new GpsPingDto(12.9050, 77.5050, t0.plusSeconds(30), 10.0),
                new GpsPingDto(12.9090, 77.5090, t0.plusSeconds(60), 10.0) // plausible 74 km/h late ping (+0.62 km)
        ));

        mockMvc.perform(post("/api/v1/trips/e2e-trip-recompute/recompute")
                        .header("X-Tenant-ID", "tenant-e2e")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recomputeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value("e2e-trip-recompute"))
                .andExpect(jsonPath("$.previousVersion").value(1))
                .andExpect(jsonPath("$.newVersion").value(2))
                .andExpect(jsonPath("$.deltaKm").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.formattedDelta").value(startsWith("+")))
                .andExpect(jsonPath("$.adjustmentType").value("CREDIT_NOTE"));
    }

    @Test
    @DisplayName("E2E API: Circuit breaker admin trip, inspect, and reset lifecycle")
    void testAdminCircuitBreakerLifecycle() throws Exception {
        // Inspect circuit breaker
        mockMvc.perform(get("/api/v1/admin/circuit-breaker")
                        .header("X-Tenant-ID", "tenant-admin")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").exists());

        // Trip circuit breaker
        mockMvc.perform(post("/api/v1/admin/circuit-breaker/trip")
                        .header("X-Tenant-ID", "tenant-admin")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CIRCUIT_TRIPPED_OPEN"));

        // Verify state is OPEN
        mockMvc.perform(get("/api/v1/admin/circuit-breaker")
                        .header("X-Tenant-ID", "tenant-admin")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"));

        // Reset circuit breaker
        mockMvc.perform(post("/api/v1/admin/circuit-breaker/reset")
                        .header("X-Tenant-ID", "tenant-admin")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CIRCUIT_RESET_CLOSED"));
    }

    @Test
    @DisplayName("E2E API: X-Idempotency-Key prevents duplicate version creation on network retries")
    void testIdempotencyKeyPreventsDuplicateBilling() throws Exception {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");

        TripCalculationRequest request = new TripCalculationRequest();
        request.setTripId("e2e-trip-idempotent-key");
        request.setTenantId("tenant-e2e");
        request.setRawPings(List.of(
                new GpsPingDto(12.9000, 77.5000, t0, 10.0),
                new GpsPingDto(12.9050, 77.5050, t0.plusSeconds(30), 10.0)
        ));

        // First call with X-Idempotency-Key
        mockMvc.perform(post("/api/v1/trips/calculate")
                        .header("X-Tenant-ID", "tenant-e2e")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .header("X-Idempotency-Key", "idem-key-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Second retry with SAME X-Idempotency-Key must return version 1 (not version 2)
        mockMvc.perform(post("/api/v1/trips/calculate")
                        .header("X-Tenant-ID", "tenant-e2e")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .header("X-Idempotency-Key", "idem-key-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("E2E API: Incremental late ping merges with stored evidence snapshot & Degraded auto-healer reconciles")
    void testIncrementalRecomputeAndAutoHealer() throws Exception {
        Instant t0 = Instant.parse("2026-10-29T11:00:00Z");

        // 1. Trip circuit breaker to OPEN
        mockMvc.perform(post("/api/v1/admin/circuit-breaker/trip")
                        .header("X-Tenant-ID", "tenant-heal")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        // 2. Calculate trip with a real 5-min gap while circuit breaker is OPEN -> DEGRADED
        TripCalculationRequest request = new TripCalculationRequest();
        request.setTripId("e2e-trip-auto-heal");
        request.setTenantId("tenant-heal");
        request.setRawPings(List.of(
                new GpsPingDto(12.9000, 77.5000, t0, 10.0),
                new GpsPingDto(12.9300, 77.5300, t0.plusSeconds(300), 10.0)
        ));

        mockMvc.perform(post("/api/v1/trips/calculate")
                        .header("X-Tenant-ID", "tenant-heal")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DEGRADED"));

        // 3. Reset circuit breaker (Provider recovers)
        mockMvc.perform(post("/api/v1/admin/circuit-breaker/reset")
                        .header("X-Tenant-ID", "tenant-heal")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        // 4. Trigger auto-reconcile endpoint -> heals DEGRADED trip to v2
        mockMvc.perform(post("/api/v1/admin/reconcile-degraded")
                        .header("X-Tenant-ID", "tenant-heal")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciledCount").value(1));

        // 5. Send ONLY 1 new incremental late ping -> merges with stored snapshot to produce v3 CREDIT_NOTE
        RecomputeRequest incrementalRequest = new RecomputeRequest();
        incrementalRequest.setReason("SINGLE_INCREMENTAL_LATE_PING");
        incrementalRequest.setLatePings(List.of(
                new GpsPingDto(12.9350, 77.5350, t0.plusSeconds(330), 10.0)
        ));

        mockMvc.perform(post("/api/v1/trips/e2e-trip-auto-heal/recompute")
                        .header("X-Tenant-ID", "tenant-heal")
                        .header("X-User-Role", "ROLE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incrementalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousVersion").value(2))
                .andExpect(jsonPath("$.newVersion").value(3))
                .andExpect(jsonPath("$.adjustmentType").value("CREDIT_NOTE"));
    }
}
