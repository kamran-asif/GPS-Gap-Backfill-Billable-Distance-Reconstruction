package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Line-by-line explainable result for each segment between two consecutive points.
 * Meets the requirement: "every segment in the output carries the rule that produced its distance so a vendor dispute can be answered line by line."
 */
public final class SegmentResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int segmentIndex;
    private final AnchorPoint startPoint;
    private final AnchorPoint endPoint;
    private final long deltaSeconds;
    private final double impliedAerialSpeedKmh;
    private final double aerialDistanceKm;
    private final double finalDistanceKm;
    private final DistanceRule rule;
    private final String explanation;
    private final boolean routed;

    public SegmentResult(int segmentIndex,
                         AnchorPoint startPoint,
                         AnchorPoint endPoint,
                         long deltaSeconds,
                         double aerialDistanceKm,
                         double finalDistanceKm,
                         DistanceRule rule,
                         String explanation,
                         boolean routed) {
        this.segmentIndex = segmentIndex;
        this.startPoint = Objects.requireNonNull(startPoint, "Start point must not be null");
        this.endPoint = Objects.requireNonNull(endPoint, "End point must not be null");
        this.deltaSeconds = deltaSeconds;
        this.aerialDistanceKm = round(aerialDistanceKm, 3);
        this.finalDistanceKm = round(finalDistanceKm, 3);
        this.impliedAerialSpeedKmh = calculateSpeedKmh(this.aerialDistanceKm, deltaSeconds);
        this.rule = Objects.requireNonNull(rule, "DistanceRule must not be null");
        this.explanation = Objects.requireNonNull(explanation, "Explanation must not be null");
        this.routed = routed;
    }

    private static double calculateSpeedKmh(double distanceKm, long seconds) {
        if (seconds <= 0 || distanceKm <= 0.0) return 0.0;
        return round((distanceKm / seconds) * 3600.0, 2);
    }

    private static double round(double val, int scale) {
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public AnchorPoint getStartPoint() {
        return startPoint;
    }

    public AnchorPoint getEndPoint() {
        return endPoint;
    }

    public long getDeltaSeconds() {
        return deltaSeconds;
    }

    public double getImpliedAerialSpeedKmh() {
        return impliedAerialSpeedKmh;
    }

    public double getAerialDistanceKm() {
        return aerialDistanceKm;
    }

    public double getFinalDistanceKm() {
        return finalDistanceKm;
    }

    public DistanceRule getRule() {
        return rule;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean isRouted() {
        return routed;
    }

    @Override
    public String toString() {
        return "Segment #" + segmentIndex + " [" + rule + "]: " + finalDistanceKm + " km (" + deltaSeconds + "s) - " + explanation;
    }
}
