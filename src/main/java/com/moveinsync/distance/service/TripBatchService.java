package com.moveinsync.distance.service;

import com.moveinsync.distance.domain.TripDistanceResult;
import com.moveinsync.distance.domain.TripEvidence;
import com.moveinsync.distance.domain.TripStatus;
import com.moveinsync.distance.dto.BatchCalculationResponse;
import com.moveinsync.distance.scaling.ParallelTripProcessor;
import com.moveinsync.distance.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk trip processing entry point (Scaling Level 3: parallel trip processing).
 *
 * <p>Delegates each independent trip to {@link TripDistanceService} through the Spring proxy so
 * per-trip transactions and metrics still apply, while bounded worker threads provide parallelism.
 * The active tenant is propagated into each worker thread to preserve strict tenant isolation.</p>
 */
@Service
public class TripBatchService {
    private static final Logger log = LoggerFactory.getLogger(TripBatchService.class);

    private final TripDistanceService tripService;

    public TripBatchService(TripDistanceService tripService) {
        this.tripService = tripService;
    }

    public BatchCalculationResponse calculateInParallel(List<TripEvidence> evidences, int workers) {
        if (evidences == null || evidences.isEmpty()) {
            return new BatchCalculationResponse(0, 0, 0, workers, 0.0, List.of());
        }

        String activeTenant = TenantContext.getTenantId();
        log.info("Starting parallel trip calculation: trips={}, workers={}, tenant={}",
                evidences.size(), workers, activeTenant);

        List<TripDistanceResult> results;
        try (ParallelTripProcessor processor = new ParallelTripProcessor(workers)) {
            results = processor.processInOrder(evidences, evidence -> {
                if (activeTenant != null && !activeTenant.isBlank()) {
                    TenantContext.setTenantId(activeTenant);
                }
                try {
                    return tripService.calculateAndSave(evidence);
                } finally {
                    TenantContext.clear();
                }
            });
        }

        List<BatchCalculationResponse.TripSummary> summaries = new ArrayList<>(results.size());
        int completed = 0;
        int degraded = 0;
        double totalKm = 0.0;
        for (TripDistanceResult result : results) {
            if (result.getStatus() == TripStatus.DEGRADED) {
                degraded++;
            } else {
                completed++;
            }
            totalKm += result.getTotalDistanceKm();
            summaries.add(new BatchCalculationResponse.TripSummary(
                    result.getTripId(), result.getStatus().name(), result.getTotalDistanceKm(), result.getVersion()));
        }

        log.info("Parallel trip calculation complete: total={}, completed={}, degraded={}, totalKm={}",
                results.size(), completed, degraded, totalKm);

        return new BatchCalculationResponse(results.size(), completed, degraded, workers, totalKm, summaries);
    }
}
