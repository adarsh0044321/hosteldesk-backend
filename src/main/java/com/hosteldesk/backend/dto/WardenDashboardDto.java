package com.hosteldesk.backend.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WardenDashboardDto {
    private long totalOpenCount;
    private long urgentP1Count;
    private long inWorkCount;
    private long pendingVerificationCount;
    private long totalResolvedCount;

    private int healthPercentage = 91;
    private String healthStatus = "Normal Ops";
    private String healthSummary = "Active concerns: 3 plumbing · 2 electrical · 0 security";

    private List<IssueDto> attentionRequired = new ArrayList<>();
    private List<Map<String, Object>> departmentWorkloads = new ArrayList<>();
    private List<InsightDto> recurringInsights = new ArrayList<>();

    public WardenDashboardDto() {}

    public long getTotalOpenCount() { return totalOpenCount; }
    public void setTotalOpenCount(long totalOpenCount) { this.totalOpenCount = totalOpenCount; }

    public long getUrgentP1Count() { return urgentP1Count; }
    public void setUrgentP1Count(long urgentP1Count) { this.urgentP1Count = urgentP1Count; }

    public long getInWorkCount() { return inWorkCount; }
    public void setInWorkCount(long inWorkCount) { this.inWorkCount = inWorkCount; }

    public long getPendingVerificationCount() { return pendingVerificationCount; }
    public void setPendingVerificationCount(long pendingVerificationCount) { this.pendingVerificationCount = pendingVerificationCount; }

    public long getTotalResolvedCount() { return totalResolvedCount; }
    public void setTotalResolvedCount(long totalResolvedCount) { this.totalResolvedCount = totalResolvedCount; }

    public int getHealthPercentage() { return healthPercentage; }
    public void setHealthPercentage(int healthPercentage) { this.healthPercentage = healthPercentage; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public String getHealthSummary() { return healthSummary; }
    public void setHealthSummary(String healthSummary) { this.healthSummary = healthSummary; }

    public List<IssueDto> getAttentionRequired() { return attentionRequired; }
    public void setAttentionRequired(List<IssueDto> attentionRequired) { this.attentionRequired = attentionRequired; }

    public List<Map<String, Object>> getDepartmentWorkloads() { return departmentWorkloads; }
    public void setDepartmentWorkloads(List<Map<String, Object>> departmentWorkloads) { this.departmentWorkloads = departmentWorkloads; }

    public List<InsightDto> getRecurringInsights() { return recurringInsights; }
    public void setRecurringInsights(List<InsightDto> recurringInsights) { this.recurringInsights = recurringInsights; }
}
