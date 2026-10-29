package com.moveinsync.distance.dto;

import java.time.Instant;

public class GpsPingDto {
    private double latitude;
    private double longitude;
    private Instant timestamp;
    private Double accuracy;

    public GpsPingDto() {}

    public GpsPingDto(double latitude, double longitude, Instant timestamp, Double accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }
}
