package com.hosteldesk.backend.service;

import com.hosteldesk.backend.dto.*;
import com.hosteldesk.backend.entity.*;
import com.hosteldesk.backend.exception.ForbiddenException;
import com.hosteldesk.backend.exception.InvalidStateTransitionException;
import com.hosteldesk.backend.exception.ResourceNotFoundException;
import com.hosteldesk.backend.repository.*;
import com.hosteldesk.backend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class IssueService {
    private static final Logger log = LoggerFactory.getLogger(IssueService.class);

    private final IssueRepository issueRepository;
    private final IssueAttachmentRepository attachmentRepository;
    private final IssueAiAnalysisRepository aiAnalysisRepository;
    private final IssueActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoutingService routingService;
    private final AiIntegrationService aiIntegrationService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public IssueService(IssueRepository issueRepository,
                        IssueAttachmentRepository attachmentRepository,
                        IssueAiAnalysisRepository aiAnalysisRepository,
                        IssueActivityRepository activityRepository,
                        UserRepository userRepository,
                        DepartmentRepository departmentRepository,
                        RoutingService routingService,
                        AiIntegrationService aiIntegrationService,
                        FileStorageService fileStorageService,
                        NotificationService notificationService) {
        this.issueRepository = issueRepository;
        this.attachmentRepository = attachmentRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.routingService = routingService;
        this.aiIntegrationService = aiIntegrationService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    @Transactional
    public IssueDetailDto createIssue(User student, CreateIssueRequest request, MultipartFile attachment) {
        log.info("Creating issue for student: {} ({})", student.getFullName(), student.getEmail());

        long nextNum = 1000 + issueRepository.count() + 1;
        String ticketNumber = "HD-" + nextNum;

        Issue issue = new Issue();
        issue.setTicketNumber(ticketNumber);
        issue.setReportedBy(student);
        issue.setHostel(student.getHostel());
        issue.setBlockName(request.getBlockName());
        issue.setRoomNumber(request.getRoomNumber());
        issue.setCategory(request.getCategory().toUpperCase());
        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());
        issue.setPriority(request.getPriority() != null ? request.getPriority() : IssuePriority.P3_MEDIUM);
        issue.setStatus(IssueStatus.REPORTED);

        issue = issueRepository.save(issue);

        // Save Attachment if present
        if (attachment != null && !attachment.isEmpty()) {
            String fileUrl = fileStorageService.storeFile(attachment);
            IssueAttachment att = new IssueAttachment(
                    null, issue, fileUrl, attachment.getOriginalFilename(),
                    attachment.getContentType(), attachment.getSize(), "STUDENT_REPORT"
            );
            attachmentRepository.save(att);
            issue.getAttachments().add(att);
        }

        // Initial Activity
        activityRepository.save(new IssueActivity(
                null, issue, student, "REPORTED", "Issue #" + ticketNumber + " submitted by " + student.getFullName()
        ));

        // Trigger AI Analysis
        issue.setStatus(IssueStatus.AI_ANALYZING);
        AiInferenceResponse ai = aiIntegrationService.analyzeIssue(
                issue.getTitle(), issue.getDescription(), issue.getCategory(),
                issue.getBlockName(), issue.getRoomNumber()
        );

        IssueAiAnalysis aiAnalysis = new IssueAiAnalysis(
                null, issue, ai.getCategory(), ai.getPriority(),
                ai.getRecommendedDepartment(), ai.getSummary(),
                ai.getSafetyHazardNote(), ai.getConfidence(), ai.getIsFallback()
        );
        aiAnalysisRepository.save(aiAnalysis);
        issue.setAiAnalysis(aiAnalysis);

        // Apply AI classification recommendations if confident
        if (ai.getConfidence().doubleValue() > 0.80) {
            try {
                issue.setPriority(IssuePriority.valueOf(ai.getPriority()));
            } catch (Exception ignored) {}
        }
        issue.setStatus(IssueStatus.ANALYZED);

        activityRepository.save(new IssueActivity(
                null, issue, null, "ANALYZED",
                String.format("AI classified as %s (%s). Summary: %s", ai.getCategory(), ai.getPriority(), ai.getSummary())
        ));

        // Automated Routing
        Department dept = routingService.resolveDepartmentForCategory(ai.getRecommendedDepartment());
        if (dept != null) {
            issue.setAssignedDepartment(dept);
            issue.setStatus(IssueStatus.ASSIGNED);

            activityRepository.save(new IssueActivity(
                null, issue, null, "ROUTED", "Automatically routed to " + dept.getDisplayName()
            ));
        }

        issue = issueRepository.save(issue);

        // In-app Notifications
        notificationService.createNotification(
                student, "Issue Created", "Your ticket #" + ticketNumber + " has been registered and routed for review.",
                "ISSUE_CREATED", issue
        );

        for (User warden : userRepository.findByRole(Role.WARDEN)) {
            notificationService.createNotification(
                    warden, "New Issue: #" + ticketNumber,
                    String.format("%s in %s Room %s: %s", issue.getCategory(), issue.getBlockName(), issue.getRoomNumber(), issue.getTitle()),
                    "NEW_ISSUE_WARDEN", issue
            );
        }

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueDto> getStudentIssues(Long studentId, IssueStatus status) {
        List<Issue> issues;
        if (status != null) {
            issues = issueRepository.findByReportedByIdAndStatus(studentId, status);
        } else {
            issues = issueRepository.findByReportedByIdOrderByCreatedAtDesc(studentId);
        }
        return issues.stream().map(IssueDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IssueDetailDto getIssueDetail(Long issueId, UserPrincipal principal) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found with id: " + issueId));

        if (principal.getRole() == Role.STUDENT && !issue.getReportedBy().getId().equals(principal.getId())) {
            throw new ForbiddenException("Access Denied: You are not authorized to view another student's complaint.");
        }

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueDto> searchAdminIssues(IssueStatus status, IssuePriority priority, Long departmentId) {
        return issueRepository.searchAdminIssues(status, priority, departmentId)
                .stream().map(IssueDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public IssueDetailDto assignIssue(Long issueId, AssignIssueRequest request, User warden) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found with id: " + issueId));

        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.getDepartmentId()));

        User staff = null;
        if (request.getStaffId() != null) {
            staff = userRepository.findById(request.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + request.getStaffId()));
        }

        issue.setAssignedDepartment(dept);
        if (staff != null) issue.setAssignedStaff(staff);
        if (request.getPriority() != null) issue.setPriority(request.getPriority());
        issue.setStatus(IssueStatus.ASSIGNED);

        String msg = "Assigned to " + dept.getDisplayName() + (staff != null ? " (Tech: " + staff.getFullName() + ")" : "");
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            msg += " — " + request.getNotes();
        }

        activityRepository.save(new IssueActivity(null, issue, warden, "ASSIGNED", msg));
        issue = issueRepository.save(issue);

        if (staff != null) {
            notificationService.createNotification(
                    staff, "New Work Order Assigned", "You have been assigned ticket #" + issue.getTicketNumber(),
                    "ISSUE_ASSIGNED", issue
            );
        }

        notificationService.createNotification(
                issue.getReportedBy(), "Technician Assigned",
                "Your issue #" + issue.getTicketNumber() + " has been assigned to " + dept.getDisplayName(),
                "ISSUE_ASSIGNED", issue
        );

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional
    public IssueDetailDto startWork(Long issueId, User staff) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        if (issue.getStatus() != IssueStatus.ASSIGNED && issue.getStatus() != IssueStatus.REOPENED) {
            throw new InvalidStateTransitionException(issue.getStatus(), IssueStatus.IN_PROGRESS, "Work can only start on ASSIGNED or REOPENED issues.");
        }

        issue.setStatus(IssueStatus.IN_PROGRESS);
        if (issue.getAssignedStaff() == null) {
            issue.setAssignedStaff(staff);
        }

        activityRepository.save(new IssueActivity(null, issue, staff, "IN_PROGRESS", "Tech " + staff.getFullName() + " started work."));
        issue = issueRepository.save(issue);

        notificationService.createNotification(
                issue.getReportedBy(), "Technician On Site",
                "Work has begun on your ticket #" + issue.getTicketNumber(),
                "WORK_STARTED", issue
        );

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional
    public IssueDetailDto updateProgressNote(Long issueId, String note, User staff) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        issue.setTechnicianNotes(note);
        activityRepository.save(new IssueActivity(null, issue, staff, "NOTE_ADDED", "Technician note: " + note));
        issue = issueRepository.save(issue);

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional
    public IssueDetailDto completeWork(Long issueId, String note, MultipartFile proofPhoto, User staff) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        if (issue.getStatus() != IssueStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(issue.getStatus(), IssueStatus.AWAITING_VERIFICATION, "Can only complete issues currently IN_PROGRESS.");
        }

        if (proofPhoto != null && !proofPhoto.isEmpty()) {
            String fileUrl = fileStorageService.storeFile(proofPhoto);
            IssueAttachment att = new IssueAttachment(
                    null, issue, fileUrl, proofPhoto.getOriginalFilename(),
                    proofPhoto.getContentType(), proofPhoto.getSize(), "STAFF_COMPLETION_PROOF"
            );
            attachmentRepository.save(att);
            issue.getAttachments().add(att);
        }

        issue.setTechnicianNotes(note);
        issue.setStatus(IssueStatus.AWAITING_VERIFICATION);

        activityRepository.save(new IssueActivity(
                null, issue, staff, "COMPLETED",
                "Work marked completed by tech " + staff.getFullName() + ": " + note
        ));
        issue = issueRepository.save(issue);

        // Notify student for resolution verification
        notificationService.createNotification(
                issue.getReportedBy(), "Please Verify Resolution",
                "Technician " + staff.getFullName() + " completed repair on #" + issue.getTicketNumber() + ". Please inspect and confirm.",
                "VERIFICATION_REQUEST", issue
        );

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional
    public IssueDetailDto verifyResolution(Long issueId, String satisfactionNote, User student) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        if (!issue.getReportedBy().getId().equals(student.getId())) {
            throw new ForbiddenException("Only the student who reported this issue can verify resolution.");
        }

        if (issue.getStatus() != IssueStatus.AWAITING_VERIFICATION) {
            throw new InvalidStateTransitionException(issue.getStatus(), IssueStatus.RESOLVED, "Issue is not awaiting verification.");
        }

        issue.setStatus(IssueStatus.RESOLVED);
        issue.setResolvedAt(ZonedDateTime.now());
        issue.setVerifiedAt(ZonedDateTime.now());
        issue.setResolutionNotes(satisfactionNote != null ? satisfactionNote : "Resolved and verified by resident.");

        activityRepository.save(new IssueActivity(
                null, issue, student, "VERIFIED", "Resident verified resolution. Issue closed."
        ));
        issue = issueRepository.save(issue);

        if (issue.getAssignedStaff() != null) {
            notificationService.createNotification(
                    issue.getAssignedStaff(), "Ticket Verified by Student",
                    "Student confirmed resolution for #" + issue.getTicketNumber(),
                    "ISSUE_RESOLVED", issue
            );
        }

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional
    public IssueDetailDto reopenIssue(Long issueId, String reason, User student) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        if (!issue.getReportedBy().getId().equals(student.getId())) {
            throw new ForbiddenException("Only the student who reported this issue can reopen it.");
        }

        if (issue.getStatus() != IssueStatus.AWAITING_VERIFICATION && issue.getStatus() != IssueStatus.RESOLVED) {
            throw new InvalidStateTransitionException(issue.getStatus(), IssueStatus.REOPENED, "Cannot reopen issue in state: " + issue.getStatus());
        }

        issue.setStatus(IssueStatus.REOPENED);
        issue.setReopenReason(reason);

        activityRepository.save(new IssueActivity(
                null, issue, student, "REOPENED", "Resident rejected resolution: " + reason
        ));
        issue = issueRepository.save(issue);

        if (issue.getAssignedStaff() != null) {
            notificationService.createNotification(
                    issue.getAssignedStaff(), "Ticket Reopened: #" + issue.getTicketNumber(),
                    "Student indicated problem is still not resolved: " + reason,
                    "ISSUE_REOPENED", issue
            );
        }

        for (User warden : userRepository.findByRole(Role.WARDEN)) {
            notificationService.createNotification(
                    warden, "Alert: Ticket Reopened #" + issue.getTicketNumber(),
                    "Resident in Room " + issue.getRoomNumber() + " reopened issue: " + reason,
                    "ISSUE_REOPENED", issue
            );
        }

        return IssueDetailDto.fromEntity(issue);
    }

    @Transactional(readOnly = true)
    public List<IssueDto> getStaffIssues(Long staffId, String filter) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffId));

        if ("COMPLETED".equalsIgnoreCase(filter)) {
            return issueRepository.findByAssignedStaffIdAndStatus(staffId, IssueStatus.RESOLVED)
                    .stream().map(IssueDto::fromEntity).collect(Collectors.toList());
        } else if ("QUEUE".equalsIgnoreCase(filter)) {
            if (staff.getDepartment() != null) {
                return issueRepository.findAll().stream()
                        .filter(i -> i.getAssignedDepartment() != null &&
                                     i.getAssignedDepartment().getId().equals(staff.getDepartment().getId()) &&
                                     i.getAssignedStaff() == null &&
                                     i.getStatus() != IssueStatus.RESOLVED)
                        .map(IssueDto::fromEntity).collect(Collectors.toList());
            }
            return new ArrayList<>();
        } else {
            // Default: MY_WORK
            List<IssueStatus> activeWork = Arrays.asList(IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS, IssueStatus.REOPENED);
            return issueRepository.findByAssignedStaffIdAndStatusIn(staffId, activeWork)
                    .stream().map(IssueDto::fromEntity).collect(Collectors.toList());
        }
    }
}
