package com.hosteldesk.backend.service;

import com.hosteldesk.backend.dto.InsightDto;
import com.hosteldesk.backend.dto.IssueDto;
import com.hosteldesk.backend.dto.StudentDashboardDto;
import com.hosteldesk.backend.dto.WardenDashboardDto;
import com.hosteldesk.backend.entity.*;
import com.hosteldesk.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final IssueRepository issueRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final InfrastructureInsightRepository insightRepository;
    private final NotificationService notificationService;

    public AnalyticsService(IssueRepository issueRepository,
                            DepartmentRepository departmentRepository,
                            UserRepository userRepository,
                            InfrastructureInsightRepository insightRepository,
                            NotificationService notificationService) {
        this.issueRepository = issueRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.insightRepository = insightRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public StudentDashboardDto getStudentDashboard(User student) {
        StudentDashboardDto dto = new StudentDashboardDto();
        dto.setStudentName(student.getFullName());
        dto.setInstitutionalId(student.getInstitutionalId());
        dto.setHostelName(student.getHostel() != null ? student.getHostel().getName() : "Hostel Residence");
        dto.setBlockName("Block B");
        dto.setRoomNumber(student.getRoomNumber() != null ? student.getRoomNumber() : "204");

        List<Issue> activeIssues = issueRepository.findByReportedByIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .filter(i -> i.getStatus() != IssueStatus.RESOLVED)
                .collect(Collectors.toList());

        long resolvedCount = issueRepository.findByReportedByIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .filter(i -> i.getStatus() == IssueStatus.RESOLVED)
                .count();

        long pendingVerificationCount = activeIssues.stream()
                .filter(i -> i.getStatus() == IssueStatus.AWAITING_VERIFICATION)
                .count();

        dto.setActiveRequestsCount(activeIssues.size());
        dto.setPendingVerificationCount((int) pendingVerificationCount);
        dto.setResolvedCount((int) resolvedCount);
        dto.setActiveIssues(activeIssues.stream().map(IssueDto::fromEntity).collect(Collectors.toList()));
        dto.setRecentNotifications(notificationService.getUserNotifications(student.getId()).stream().limit(5).collect(Collectors.toList()));

        return dto;
    }

    @Transactional(readOnly = true)
    public WardenDashboardDto getWardenDashboard() {
        WardenDashboardDto dto = new WardenDashboardDto();

        List<IssueStatus> activeStatuses = Arrays.asList(
                IssueStatus.REPORTED, IssueStatus.AI_ANALYZING, IssueStatus.ANALYZED,
                IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS, IssueStatus.AWAITING_VERIFICATION,
                IssueStatus.REOPENED
        );

        long totalOpen = issueRepository.countByStatusIn(activeStatuses);
        long inWork = issueRepository.countByStatus(IssueStatus.IN_PROGRESS);
        long pendingCheck = issueRepository.countByStatus(IssueStatus.AWAITING_VERIFICATION);
        long resolved = issueRepository.countByStatus(IssueStatus.RESOLVED);

        long urgentP1 = issueRepository.findAll().stream()
                .filter(i -> i.getPriority() == IssuePriority.P1_URGENT && i.getStatus() != IssueStatus.RESOLVED)
                .count();

        dto.setTotalOpenCount(totalOpen);
        dto.setUrgentP1Count(urgentP1);
        dto.setInWorkCount(inWork);
        dto.setPendingVerificationCount(pendingCheck);
        dto.setTotalResolvedCount(resolved);

        // Calculate health percentage (ratio of operational integrity)
        int health = (int) Math.max(70, Math.min(100, 100 - (urgentP1 * 3 + totalOpen)));
        dto.setHealthPercentage(health > 0 ? health : 88);
        dto.setHealthStatus(urgentP1 > 5 ? "Critical Follow-up" : "Normal Ops");

        // Attention required: top P1 issues or unassigned
        List<IssueDto> attention = issueRepository.findAll().stream()
                .filter(i -> (i.getPriority() == IssuePriority.P1_URGENT || i.getStatus() == IssueStatus.REPORTED || i.getStatus() == IssueStatus.REOPENED)
                        && i.getStatus() != IssueStatus.RESOLVED)
                .sorted(Comparator.comparing(Issue::getCreatedAt).reversed())
                .limit(5)
                .map(IssueDto::fromEntity)
                .collect(Collectors.toList());
        dto.setAttentionRequired(attention);

        // Department Workload Matrix
        List<Map<String, Object>> workloads = new ArrayList<>();
        for (Department dept : departmentRepository.findAll()) {
            Map<String, Object> map = new HashMap<>();
            map.put("departmentId", dept.getId());
            map.put("name", dept.getName());
            map.put("displayName", dept.getDisplayName());

            long staffOnDuty = userRepository.findByDepartmentId(dept.getId()).size();
            long activeTasks = issueRepository.countByAssignedDepartmentIdAndStatusIn(dept.getId(), activeStatuses);

            map.put("staffOnDuty", staffOnDuty > 0 ? staffOnDuty : 2);
            map.put("activeTasks", activeTasks);
            workloads.add(map);
        }
        dto.setDepartmentWorkloads(workloads);

        // Recurring Insights
        List<InsightDto> insights = insightRepository.findByOrderByCreatedAtDesc().stream()
                .map(InsightDto::fromEntity)
                .collect(Collectors.toList());
        dto.setRecurringInsights(insights);

        return dto;
    }
}
