package com.moveinsync.distance.dto;

public class OfficeLocationDto {
    private String officeId;
    private String name;
    private double latitude;
    private double longitude;

    public OfficeLocationDto() {}

    public OfficeLocationDto(String officeId, String name, double latitude, double longitude) {
        this.officeId = officeId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getOfficeId() {
        return officeId;
    }

    public void setOfficeId(String officeId) {
        this.officeId = officeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
