package com.moveinsync.distance.service;

import com.moveinsync.distance.domain.TripDistanceResult;
import com.moveinsync.distance.domain.TripEvidence;

public interface TripCalculationExecutor {
    TripDistanceResult calculateAndSave(TripEvidence evidence, String idempotencyKey);
}

