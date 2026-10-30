package com.moveinsync.distance.dto;

import java.util.List;

/**
 * Response for bulk/parallel trip calculation.
 */
public record BatchCalculationResponse(int totalTrips,
                                       int completedTrips,
                                       int degradedTrips,
                                       int workerCount,
                                       double totalDistanceKm,
                                       List<TripSummary> trips) {

    public record TripSummary(String tripId, String status, double totalDistanceKm, int version) {
    }
}
