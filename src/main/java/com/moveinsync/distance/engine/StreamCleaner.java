package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.Coordinate;
import com.moveinsync.distance.domain.EngineConfig;
import com.moveinsync.distance.domain.GpsPing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Stage I: Ingest the Trip Evidence & Clean the Stream
 * - Removes duplicate pings
 * - Orders by timestamp strictly ascending
 * - Drops low-accuracy fixes (> 50m)
 * - Drops "teleport" jumps whose implied speed is above configurable limit (120 km/h)
 */
public final class StreamCleaner {
    private static final Logger log = LoggerFactory.getLogger(StreamCleaner.class);

    private final EngineConfig config;

    public StreamCleaner(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "EngineConfig must not be null");
    }

    public StreamCleanerResult clean(List<GpsPing> rawPings) {
        if (rawPings == null || rawPings.isEmpty()) {
            return new StreamCleanerResult(List.of(), 0, 0, 0);
        }

        int duplicateCount = 0;
        int lowAccuracyCount = 0;
        int teleportCount = 0;

        // Step 1: Deduplication by timestamp (preferring better accuracy)
        // Group pings by timestamp
        Map<java.time.Instant, GpsPing> deduplicatedMap = new HashMap<>(rawPings.size());
        for (GpsPing ping : rawPings) {
            if (ping == null || ping.getCoordinate() == null || ping.getTimestamp() == null) {
                continue;
            }
            GpsPing existing = deduplicatedMap.get(ping.getTimestamp());
            if (existing == null) {
                deduplicatedMap.put(ping.getTimestamp(), ping);
            } else {
                duplicateCount++;
                // If same coordinates, count as duplicate; keep whichever has better accuracy
                Double accExisting = existing.getAccuracyMeters();
                Double accNew = ping.getAccuracyMeters();
                if (accNew != null && (accExisting == null || accNew < accExisting)) {
                    deduplicatedMap.put(ping.getTimestamp(), ping);
                }
            }
        }

        // Step 2: Sort chronologically ascending
        List<GpsPing> sortedPings = new ArrayList<>(deduplicatedMap.values());
        sortedPings.sort(Comparator.comparing(GpsPing::getTimestamp));

        // Step 3 & 4: Drop low-accuracy fixes & filter out teleport jumps
        List<GpsPing> cleaned = new ArrayList<>(sortedPings.size());
        GpsPing lastAccepted = null;

        for (GpsPing ping : sortedPings) {
            // Drop low accuracy
            if (!ping.hasValidAccuracy(config.getMaxAccuracyMeters())) {
                lowAccuracyCount++;
                continue;
            }

            if (lastAccepted == null) {
                cleaned.add(ping);
                lastAccepted = ping;
                continue;
            }

            // Check implied speed from last accepted ping
            long deltaSeconds = Duration.between(lastAccepted.getTimestamp(), ping.getTimestamp()).getSeconds();
            if (deltaSeconds <= 0) {
                // Same second duplicate that slipped through or negative timestamp jump
                duplicateCount++;
                continue;
            }

            double distanceKm = lastAccepted.getCoordinate().haversineDistanceKm(ping.getCoordinate());
            double speedKmh = (distanceKm / deltaSeconds) * 3600.0;

            if (speedKmh > config.getTeleportSpeedLimitKmh()) {
                // Teleport jump detected (e.g. 8,000 km/h burst from a bad fix)
                teleportCount++;
                log.debug("Teleport ping rejected: distance={}km, delta={}s, impliedSpeed={}km/h > maxLimit={}km/h",
                        String.format(Locale.US, "%.3f", distanceKm), deltaSeconds,
                        String.format(Locale.US, "%.1f", speedKmh), config.getTeleportSpeedLimitKmh());
                continue;
            }

            cleaned.add(ping);
            lastAccepted = ping;
        }

        return new StreamCleanerResult(cleaned, duplicateCount, lowAccuracyCount, teleportCount);
    }
}
