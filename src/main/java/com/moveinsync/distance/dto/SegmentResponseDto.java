package com.moveinsync.distance.dto;

import java.time.Instant;

public class SegmentResponseDto {
    private int segmentIndex;
    private double startLatitude;
    private double startLongitude;
    private Instant startTime;
    private String startType;
    private String startDescription;
    private double endLatitude;
    private double endLongitude;
    private Instant endTime;
    private String endType;
    private String endDescription;
    private long deltaSeconds;
    private double impliedAerialSpeedKmh;
    private double aerialDistanceKm;
    private double finalDistanceKm;
    private String rule;
    private String explanation;
    private boolean routed;

    public SegmentResponseDto() {}

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public double getStartLatitude() {
        return startLatitude;
    }

    public void setStartLatitude(double startLatitude) {
        this.startLatitude = startLatitude;
    }

    public double getStartLongitude() {
        return startLongitude;
    }

    public void setStartLongitude(double startLongitude) {
        this.startLongitude = startLongitude;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public String getStartType() {
        return startType;
    }

    public void setStartType(String startType) {
        this.startType = startType;
    }

    public String getStartDescription() {
        return startDescription;
    }

    public void setStartDescription(String startDescription) {
        this.startDescription = startDescription;
    }

    public double getEndLatitude() {
        return endLatitude;
    }

    public void setEndLatitude(double endLatitude) {
        this.endLatitude = endLatitude;
    }

    public double getEndLongitude() {
        return endLongitude;
    }

    public void setEndLongitude(double endLongitude) {
        this.endLongitude = endLongitude;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getEndType() {
        return endType;
    }

    public void setEndType(String endType) {
        this.endType = endType;
    }

    public String getEndDescription() {
        return endDescription;
    }

    public void setEndDescription(String endDescription) {
        this.endDescription = endDescription;
    }

    public long getDeltaSeconds() {
        return deltaSeconds;
    }

    public void setDeltaSeconds(long deltaSeconds) {
        this.deltaSeconds = deltaSeconds;
    }

    public double getImpliedAerialSpeedKmh() {
        return impliedAerialSpeedKmh;
    }

    public void setImpliedAerialSpeedKmh(double impliedAerialSpeedKmh) {
        this.impliedAerialSpeedKmh = impliedAerialSpeedKmh;
    }

    public double getAerialDistanceKm() {
        return aerialDistanceKm;
    }

    public void setAerialDistanceKm(double aerialDistanceKm) {
        this.aerialDistanceKm = aerialDistanceKm;
    }

    public double getFinalDistanceKm() {
        return finalDistanceKm;
    }

    public void setFinalDistanceKm(double finalDistanceKm) {
        this.finalDistanceKm = finalDistanceKm;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isRouted() {
        return routed;
    }

    public void setRouted(boolean routed) {
        this.routed = routed;
    }
}
