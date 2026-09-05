package com.hosteldesk.backend.dto;

import com.hosteldesk.backend.entity.IssueActivity;
import java.time.ZonedDateTime;

public class ActivityDto {
    private Long id;
    private Long issueId;
    private Long performedById;
    private String performedByName;
    private String action;
    private String message;
    private ZonedDateTime createdAt;

    public ActivityDto() {}

    public static ActivityDto fromEntity(IssueActivity activity) {
        if (activity == null) return null;
        ActivityDto dto = new ActivityDto();
        dto.setId(activity.getId());
        dto.setIssueId(activity.getIssue().getId());
        if (activity.getPerformedBy() != null) {
            dto.setPerformedById(activity.getPerformedBy().getId());
            dto.setPerformedByName(activity.getPerformedBy().getFullName());
        } else {
            dto.setPerformedByName("System / AI Routing");
        }
        dto.setAction(activity.getAction());
        dto.setMessage(activity.getMessage());
        dto.setCreatedAt(activity.getCreatedAt());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }

    public Long getPerformedById() { return performedById; }
    public void setPerformedById(Long performedById) { this.performedById = performedById; }

    public String getPerformedByName() { return performedByName; }
    public void setPerformedByName(String performedByName) { this.performedByName = performedByName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
