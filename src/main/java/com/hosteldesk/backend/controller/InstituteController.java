package com.hosteldesk.backend.controller;

import com.hosteldesk.backend.dto.*;
import com.hosteldesk.backend.entity.Department;
import com.hosteldesk.backend.entity.Hostel;
import com.hosteldesk.backend.entity.PasswordResetRequest;
import com.hosteldesk.backend.exception.ForbiddenException;
import com.hosteldesk.backend.security.UserPrincipal;
import com.hosteldesk.backend.service.InstituteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/institute")
@PreAuthorize("hasAnyRole('INSTITUTE_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
public class InstituteController {

    private final InstituteService instituteService;

    public InstituteController(InstituteService instituteService) {
        this.instituteService = instituteService;
    }

    private Long requireInstituteId(UserPrincipal principal) {
        if (principal == null || principal.getInstituteId() == null) {
            throw new ForbiddenException("No institution association found in session.");
        }
        return principal.getInstituteId();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<InstituteDashboardDto> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getDashboard(instituteId));
    }

    @PostMapping("/wardens")
    public ResponseEntity<CredentialResponse> createWarden(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserWithTempPasswordRequest request) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.createWarden(instituteId, request));
    }

    @GetMapping("/wardens")
    public ResponseEntity<List<UserDto>> getWardens(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getWardens(instituteId));
    }

    @PostMapping("/students")
    public ResponseEntity<CredentialResponse> createStudent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserWithTempPasswordRequest request) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.createStudent(instituteId, request));
    }

    @GetMapping("/students")
    public ResponseEntity<List<UserDto>> getStudents(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getStudents(instituteId));
    }

    @PostMapping("/staff")
    public ResponseEntity<CredentialResponse> createStaff(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserWithTempPasswordRequest request) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.createStaff(instituteId, request));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<UserDto>> getStaff(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getStaff(instituteId));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<CredentialResponse> resetUserPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long userId) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.resetUserPassword(instituteId, userId));
    }

    @GetMapping("/hostels")
    public ResponseEntity<List<Hostel>> getHostels(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getHostels(instituteId));
    }

    @PostMapping("/hostels")
    public ResponseEntity<Hostel> createHostel(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Hostel hostel) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.createHostel(instituteId, hostel));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getDepartments(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getDepartments(instituteId));
    }

    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Department department) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.createDepartment(instituteId, department));
    }

    @GetMapping("/password-resets")
    public ResponseEntity<List<PasswordResetRequest>> getPasswordResets(@AuthenticationPrincipal UserPrincipal principal) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.getPasswordResets(instituteId));
    }

    @PostMapping("/password-resets/{id}/approve")
    public ResponseEntity<CredentialResponse> approvePasswordReset(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long requestId) {
        Long instituteId = requireInstituteId(principal);
        return ResponseEntity.ok(instituteService.approvePasswordReset(instituteId, requestId, principal.getId()));
    }

    @PostMapping("/password-resets/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectPasswordReset(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long requestId) {
        Long instituteId = requireInstituteId(principal);
        instituteService.rejectPasswordReset(instituteId, requestId, principal.getId());
        return ResponseEntity.ok(Map.of("message", "Password reset request rejected."));
    }
}
