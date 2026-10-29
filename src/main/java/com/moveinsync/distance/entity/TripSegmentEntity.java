package com.moveinsync.distance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_segments", indexes = {
        @Index(name = "idx_segment_trip_version", columnList = "trip_id, tenant_id, version")
})
public class TripSegmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private String tripId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private int version;

    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    @Column(name = "start_lat", nullable = false)
    private double startLatitude;

    @Column(name = "start_lon", nullable = false)
    private double startLongitude;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "start_type", nullable = false)
    private String startType;

    @Column(name = "start_desc")
    private String startDescription;

    @Column(name = "end_lat", nullable = false)
    private double endLatitude;

    @Column(name = "end_lon", nullable = false)
    private double endLongitude;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "end_type", nullable = false)
    private String endType;

    @Column(name = "end_desc")
    private String endDescription;

    @Column(name = "delta_seconds", nullable = false)
    private long deltaSeconds;

    @Column(name = "implied_aerial_speed_kmh", nullable = false)
    private double impliedAerialSpeedKmh;

    @Column(name = "aerial_distance_km", nullable = false)
    private double aerialDistanceKm;

    @Column(name = "final_distance_km", nullable = false)
    private double finalDistanceKm;

    @Column(name = "rule_applied", nullable = false)
    private String rule;

    @Column(name = "explanation", length = 1000, nullable = false)
    private String explanation;

    @Column(nullable = false)
    private boolean routed;

    public TripSegmentEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

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
