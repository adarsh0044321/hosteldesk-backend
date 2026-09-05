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
        return getWardenDashboard(null);
    }

    @Transactional(readOnly = true)
    public WardenDashboardDto getWardenDashboard(Long instituteId) {
        WardenDashboardDto dto = new WardenDashboardDto();

        List<IssueStatus> activeStatuses = Arrays.asList(
                IssueStatus.REPORTED, IssueStatus.AI_ANALYZING, IssueStatus.ANALYZED,
                IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS, IssueStatus.AWAITING_VERIFICATION,
                IssueStatus.REOPENED
        );

        long totalOpen;
        long inWork;
        long pendingCheck;
        long resolved;
        long urgentP1;

        if (instituteId != null) {
            totalOpen = issueRepository.countByInstituteIdAndStatusIn(instituteId, activeStatuses);
            inWork = issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.IN_PROGRESS);
            pendingCheck = issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.AWAITING_VERIFICATION);
            resolved = issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.RESOLVED);
            urgentP1 = issueRepository.countByInstituteIdAndPriority(instituteId, IssuePriority.P1_URGENT);
        } else {
            totalOpen = issueRepository.countByStatusIn(activeStatuses);
            inWork = issueRepository.countByStatus(IssueStatus.IN_PROGRESS);
            pendingCheck = issueRepository.countByStatus(IssueStatus.AWAITING_VERIFICATION);
            resolved = issueRepository.countByStatus(IssueStatus.RESOLVED);
            urgentP1 = issueRepository.findAll().stream()
                    .filter(i -> i.getPriority() == IssuePriority.P1_URGENT && i.getStatus() != IssueStatus.RESOLVED)
                    .count();
        }

        dto.setTotalOpenCount(totalOpen);
        dto.setUrgentP1Count(urgentP1);
        dto.setInWorkCount(inWork);
        dto.setPendingVerificationCount(pendingCheck);
        dto.setTotalResolvedCount(resolved);

        int health = totalOpen == 0 ? 100 : (int) Math.max(60, 100 - (urgentP1 * 5 + totalOpen * 2));
        dto.setHealthPercentage(health);
        dto.setHealthStatus(urgentP1 > 0 ? "Action Required" : "System Normal");
        dto.setHealthSummary(totalOpen == 0 ? "All facilities systems operating normally." :
                String.format("Active: %d open tickets · %d in-progress · %d urgent", totalOpen, inWork, urgentP1));

        // Attention required
        List<Issue> sourceList = instituteId != null ?
                issueRepository.findByInstituteIdOrderByCreatedAtDesc(instituteId) :
                issueRepository.findAll();

        List<IssueDto> attention = sourceList.stream()
                .filter(i -> (i.getPriority() == IssuePriority.P1_URGENT || i.getStatus() == IssueStatus.REPORTED || i.getStatus() == IssueStatus.REOPENED)
                        && i.getStatus() != IssueStatus.RESOLVED)
                .sorted(Comparator.comparing(Issue::getCreatedAt).reversed())
                .limit(5)
                .map(IssueDto::fromEntity)
                .collect(Collectors.toList());
        dto.setAttentionRequired(attention);

        // Department Workload Matrix
        List<Map<String, Object>> workloads = new ArrayList<>();
        List<Department> depts = instituteId != null ? departmentRepository.findByInstituteId(instituteId) : departmentRepository.findAll();
        if (depts.isEmpty()) {
            depts = departmentRepository.findAll();
        }

        for (Department dept : depts) {
            Map<String, Object> map = new HashMap<>();
            map.put("departmentId", dept.getId());
            map.put("name", dept.getName());
            map.put("displayName", dept.getDisplayName());

            long staffOnDuty = instituteId != null ?
                    userRepository.findByInstituteIdAndDepartmentId(instituteId, dept.getId()).size() :
                    userRepository.findByDepartmentId(dept.getId()).size();

            long activeTasks = instituteId != null ?
                    issueRepository.countByInstituteIdAndAssignedDepartmentIdAndStatusIn(instituteId, dept.getId(), activeStatuses) :
                    issueRepository.countByAssignedDepartmentIdAndStatusIn(dept.getId(), activeStatuses);

            map.put("staffOnDuty", staffOnDuty);
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
