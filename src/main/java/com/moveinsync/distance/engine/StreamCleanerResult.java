package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.GpsPing;
import java.util.List;

public final class StreamCleanerResult {
    private final List<GpsPing> cleanedPings;
    private final int duplicateCount;
    private final int lowAccuracyCount;
    private final int teleportCount;

    public StreamCleanerResult(List<GpsPing> cleanedPings, int duplicateCount, int lowAccuracyCount, int teleportCount) {
        this.cleanedPings = cleanedPings != null ? List.copyOf(cleanedPings) : List.of();
        this.duplicateCount = duplicateCount;
        this.lowAccuracyCount = lowAccuracyCount;
        this.teleportCount = teleportCount;
    }

    public List<GpsPing> getCleanedPings() {
        return cleanedPings;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getLowAccuracyCount() {
        return lowAccuracyCount;
    }

    public int getTeleportCount() {
        return teleportCount;
    }

    public int getTotalDropped() {
        return duplicateCount + lowAccuracyCount + teleportCount;
    }
}
