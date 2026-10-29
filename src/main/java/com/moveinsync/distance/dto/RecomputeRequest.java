package com.moveinsync.distance.dto;

import java.util.List;

public class RecomputeRequest {
    private List<GpsPingDto> latePings;
    private List<MilestoneDto> updatedMilestones;
    private String reason;

    public RecomputeRequest() {}

    public List<GpsPingDto> getLatePings() {
        return latePings;
    }

    public void setLatePings(List<GpsPingDto> latePings) {
        this.latePings = latePings;
    }

    public List<MilestoneDto> getUpdatedMilestones() {
        return updatedMilestones;
    }

    public void setUpdatedMilestones(List<MilestoneDto> updatedMilestones) {
        this.updatedMilestones = updatedMilestones;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
