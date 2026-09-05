package com.hosteldesk.backend.controller;

import com.hosteldesk.backend.dto.*;
import com.hosteldesk.backend.entity.Department;
import com.hosteldesk.backend.entity.IssuePriority;
import com.hosteldesk.backend.entity.IssueStatus;
import com.hosteldesk.backend.entity.Role;
import com.hosteldesk.backend.entity.User;
import com.hosteldesk.backend.exception.ResourceNotFoundException;
import com.hosteldesk.backend.repository.DepartmentRepository;
import com.hosteldesk.backend.repository.UserRepository;
import com.hosteldesk.backend.security.UserPrincipal;
import com.hosteldesk.backend.service.AnalyticsService;
import com.hosteldesk.backend.service.IssueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class AdminIssueController {

    private final IssueService issueService;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public AdminIssueController(IssueService issueService,
                                AnalyticsService analyticsService,
                                UserRepository userRepository,
                                DepartmentRepository departmentRepository) {
        this.issueService = issueService;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<WardenDashboardDto> getDashboard() {
        return ResponseEntity.ok(analyticsService.getWardenDashboard());
    }

    @GetMapping("/issues")
    public ResponseEntity<List<IssueDto>> getIssues(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "departmentId", required = false) Long departmentId) {

        IssueStatus issueStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                issueStatus = IssueStatus.valueOf(status.toUpperCase());
            } catch (Exception ignored) {}
        }

        IssuePriority issuePriority = null;
        if (priority != null && !priority.isEmpty()) {
            try {
                issuePriority = IssuePriority.valueOf(priority.toUpperCase());
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(issueService.searchAdminIssues(issueStatus, issuePriority, departmentId));
    }

    @GetMapping("/issues/{id}")
    public ResponseEntity<IssueDetailDto> getIssueDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(issueService.getIssueDetail(id, principal));
    }

    @PostMapping("/issues/{id}/assign")
    public ResponseEntity<IssueDetailDto> assignIssue(
            @PathVariable("id") Long id,
            @Valid @RequestBody AssignIssueRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        User warden = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Warden not found"));

        return ResponseEntity.ok(issueService.assignIssue(id, request, warden));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<UserDto>> getStaffList() {
        List<UserDto> staff = userRepository.findByRole(Role.MAINTENANCE_STAFF)
                .stream().map(UserDto::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(staff);
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getDepartments() {
        return ResponseEntity.ok(departmentRepository.findAll());
    }
}
