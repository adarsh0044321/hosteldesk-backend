package com.hosteldesk.backend.service;

import com.hosteldesk.backend.dto.*;
import com.hosteldesk.backend.entity.*;
import com.hosteldesk.backend.exception.BadRequestException;
import com.hosteldesk.backend.exception.ResourceNotFoundException;
import com.hosteldesk.backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class InstituteService {

    private final InstituteRepository instituteRepository;
    private final CampusRepository campusRepository;
    private final HostelRepository hostelRepository;
    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final IssueRepository issueRepository;
    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final Random random = new SecureRandom();

    public InstituteService(InstituteRepository instituteRepository,
                            CampusRepository campusRepository,
                            HostelRepository hostelRepository,
                            BlockRepository blockRepository,
                            RoomRepository roomRepository,
                            DepartmentRepository departmentRepository,
                            UserRepository userRepository,
                            IssueRepository issueRepository,
                            PasswordResetRequestRepository passwordResetRequestRepository,
                            PasswordEncoder passwordEncoder) {
        this.instituteRepository = instituteRepository;
        this.campusRepository = campusRepository;
        this.hostelRepository = hostelRepository;
        this.blockRepository = blockRepository;
        this.roomRepository = roomRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.issueRepository = issueRepository;
        this.passwordResetRequestRepository = passwordResetRequestRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder("HD#");
        for (int i = 0; i < 5; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        sb.append("!");
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public InstituteDashboardDto getDashboard(Long instituteId) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));

        InstituteDashboardDto dto = new InstituteDashboardDto();
        dto.setInstituteCode(institute.getCode());
        dto.setInstituteName(institute.getName());
        dto.setTotalStudents(userRepository.countByInstituteIdAndRole(instituteId, Role.STUDENT));
        dto.setTotalWardens(userRepository.countByInstituteIdAndRole(instituteId, Role.WARDEN));
        dto.setTotalStaff(userRepository.countByInstituteIdAndRole(instituteId, Role.STAFF) +
                userRepository.countByInstituteIdAndRole(instituteId, Role.MAINTENANCE_STAFF));
        dto.setTotalHostels(hostelRepository.countByInstituteId(instituteId));
        dto.setOpenIssues(issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.REPORTED) +
                issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.ASSIGNED) +
                issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.IN_PROGRESS));
        dto.setUrgentIssues(issueRepository.countByInstituteIdAndPriority(instituteId, IssuePriority.P1_URGENT));
        dto.setResolvedIssues(issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.RESOLVED) +
                issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.VERIFIED));
        dto.setPendingVerifications(issueRepository.countByInstituteIdAndStatus(instituteId, IssueStatus.AWAITING_VERIFICATION));
        dto.setPendingPasswordResets(passwordResetRequestRepository.findByInstituteIdAndStatus(instituteId, "PENDING").size());

        List<IssueDto> recent = issueRepository.findByInstituteIdOrderByCreatedAtDesc(instituteId)
                .stream()
                .limit(10)
                .map(IssueDto::fromEntity)
                .collect(Collectors.toList());
        dto.setRecentIssues(recent);

        return dto;
    }

    @Transactional
    public CredentialResponse createWarden(Long instituteId, CreateUserWithTempPasswordRequest request) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));

        String code = institute.getCode();
        String institutionalId = request.getInstitutionalId();
        if (institutionalId == null || institutionalId.trim().isEmpty()) {
            long count = userRepository.countByInstituteIdAndRole(instituteId, Role.WARDEN) + 1;
            institutionalId = code + "-WRD-" + String.format("%03d", count);
            while (userRepository.existsByInstitutionalId(institutionalId)) {
                count++;
                institutionalId = code + "-WRD-" + String.format("%03d", count);
            }
        } else if (userRepository.existsByInstitutionalId(institutionalId)) {
            throw new BadRequestException("Warden ID already registered: " + institutionalId);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        Hostel hostel = null;
        if (request.getHostelId() != null) {
            hostel = hostelRepository.findById(request.getHostelId()).orElse(null);
        }

        String tempPassword = generateTempPassword();

        User warden = new User();
        warden.setFullName(request.getFullName());
        warden.setEmail(request.getEmail());
        warden.setPhone(request.getPhone());
        warden.setInstitutionalId(institutionalId);
        warden.setPasswordHash(passwordEncoder.encode(tempPassword));
        warden.setRole(Role.WARDEN);
        warden.setStatus(AccountStatus.ACTIVE);
        warden.setInstitute(institute);
        warden.setHostel(hostel);
        warden.setNeedsPasswordChange(true);

        User saved = userRepository.save(warden);

        return new CredentialResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getInstitutionalId(),
                saved.getEmail(),
                saved.getRole().name(),
                tempPassword,
                "Warden account created. Please share temporary credentials."
        );
    }

    @Transactional(readOnly = true)
    public List<UserDto> getWardens(Long instituteId) {
        return userRepository.findByInstituteIdAndRole(instituteId, Role.WARDEN)
                .stream().map(UserDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public CredentialResponse createStudent(Long instituteId, CreateUserWithTempPasswordRequest request) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));

        String code = institute.getCode();
        String institutionalId = request.getInstitutionalId();
        if (institutionalId == null || institutionalId.trim().isEmpty()) {
            long count = userRepository.countByInstituteIdAndRole(instituteId, Role.STUDENT) + 1;
            institutionalId = code + "-ST-" + ZonedDateTime.now().getYear() + "-" + String.format("%04d", count);
            while (userRepository.existsByInstitutionalId(institutionalId)) {
                count++;
                institutionalId = code + "-ST-" + ZonedDateTime.now().getYear() + "-" + String.format("%04d", count);
            }
        } else if (userRepository.existsByInstitutionalId(institutionalId)) {
            throw new BadRequestException("Student Roll No / ID already registered: " + institutionalId);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        Hostel hostel = null;
        if (request.getHostelId() != null) {
            hostel = hostelRepository.findById(request.getHostelId()).orElse(null);
        }
        if (hostel == null) {
            List<Hostel> instituteHostels = hostelRepository.findByInstituteId(instituteId);
            if (!instituteHostels.isEmpty()) {
                hostel = instituteHostels.get(0);
            }
        }

        String tempPassword = generateTempPassword();

        User student = new User();
        student.setFullName(request.getFullName());
        student.setEmail(request.getEmail());
        student.setPhone(request.getPhone());
        student.setInstitutionalId(institutionalId);
        student.setPasswordHash(passwordEncoder.encode(tempPassword));
        student.setRole(Role.STUDENT);
        student.setStatus(AccountStatus.ACTIVE);
        student.setInstitute(institute);
        student.setHostel(hostel);
        student.setRoomNumber(request.getRoomNumber());
        student.setNeedsPasswordChange(true);

        User saved = userRepository.save(student);

        return new CredentialResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getInstitutionalId(),
                saved.getEmail(),
                saved.getRole().name(),
                tempPassword,
                "Student account created. Please share temporary credentials."
        );
    }

    @Transactional(readOnly = true)
    public List<UserDto> getStudents(Long instituteId) {
        List<User> students = userRepository.findByInstituteIdAndRole(instituteId, Role.STUDENT);
        List<IssueStatus> activeStatuses = java.util.Arrays.asList(
                IssueStatus.REPORTED, IssueStatus.AI_ANALYZING, IssueStatus.ANALYZED,
                IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS, IssueStatus.AWAITING_VERIFICATION,
                IssueStatus.REOPENED
        );
        return students.stream().map(s -> {
            UserDto dto = UserDto.fromEntity(s);
            dto.setTotalComplaints(issueRepository.countByReportedById(s.getId()));
            dto.setActiveComplaints(issueRepository.countByReportedByIdAndStatusIn(s.getId(), activeStatuses));
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public CredentialResponse createStaff(Long instituteId, CreateUserWithTempPasswordRequest request) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));

        String code = institute.getCode();
        String institutionalId = request.getInstitutionalId();
        if (institutionalId == null || institutionalId.trim().isEmpty()) {
            long count = userRepository.countByInstituteIdAndRole(instituteId, Role.STAFF) + 1;
            institutionalId = code + "-STF-" + String.format("%03d", count);
            while (userRepository.existsByInstitutionalId(institutionalId)) {
                count++;
                institutionalId = code + "-STF-" + String.format("%03d", count);
            }
        } else if (userRepository.existsByInstitutionalId(institutionalId)) {
            throw new BadRequestException("Staff ID already registered: " + institutionalId);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId()).orElse(null);
        }

        Hostel hostel = null;
        if (request.getHostelId() != null) {
            hostel = hostelRepository.findById(request.getHostelId()).orElse(null);
        }

        String tempPassword = generateTempPassword();

        User staff = new User();
        staff.setFullName(request.getFullName());
        staff.setEmail(request.getEmail());
        staff.setPhone(request.getPhone());
        staff.setInstitutionalId(institutionalId);
        staff.setPasswordHash(passwordEncoder.encode(tempPassword));
        staff.setRole(Role.STAFF);
        staff.setStatus(AccountStatus.ACTIVE);
        staff.setInstitute(institute);
        staff.setDepartment(department);
        staff.setHostel(hostel);
        staff.setNeedsPasswordChange(true);

        User saved = userRepository.save(staff);

        return new CredentialResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getInstitutionalId(),
                saved.getEmail(),
                saved.getRole().name(),
                tempPassword,
                "Staff account created. Please share temporary credentials."
        );
    }

    @Transactional(readOnly = true)
    public List<UserDto> getStaff(Long instituteId) {
        List<User> list = userRepository.findByInstituteIdAndRole(instituteId, Role.STAFF);
        list.addAll(userRepository.findByInstituteIdAndRole(instituteId, Role.MAINTENANCE_STAFF));
        return list.stream().map(UserDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public CredentialResponse resetUserPassword(Long instituteId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getInstitute() == null || !user.getInstitute().getId().equals(instituteId)) {
            throw new BadRequestException("User does not belong to this institution.");
        }

        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setNeedsPasswordChange(true);
        userRepository.save(user);

        return new CredentialResponse(
                user.getId(),
                user.getFullName(),
                user.getInstitutionalId(),
                user.getEmail(),
                user.getRole().name(),
                tempPassword,
                "Temporary password generated. User must change password on next login."
        );
    }

    @Transactional(readOnly = true)
    public List<HostelDto> getHostels(Long instituteId) {
        List<Hostel> hostels = hostelRepository.findByInstituteId(instituteId);
        List<IssueStatus> activeStatuses = java.util.Arrays.asList(
                IssueStatus.REPORTED, IssueStatus.AI_ANALYZING, IssueStatus.ANALYZED,
                IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS, IssueStatus.AWAITING_VERIFICATION,
                IssueStatus.REOPENED
        );
        List<HostelDto> dtos = new java.util.ArrayList<>();
        for (Hostel h : hostels) {
            HostelDto dto = HostelDto.fromEntity(h);
            dto.setStudentCount(userRepository.countByHostelIdAndRole(h.getId(), Role.STUDENT));
            java.util.Optional<User> wardenOpt = userRepository.findFirstByHostelIdAndRole(h.getId(), Role.WARDEN);
            if (wardenOpt.isPresent()) {
                dto.setWardenName(wardenOpt.get().getFullName());
                dto.setWardenPhone(wardenOpt.get().getPhone());
            }
            dto.setTotalIssuesCount(issueRepository.countByHostelId(h.getId()));
            dto.setOpenIssuesCount(issueRepository.countByHostelIdAndStatusIn(h.getId(), activeStatuses));
            dto.setResolvedIssuesCount(issueRepository.countByHostelIdAndStatus(h.getId(), IssueStatus.RESOLVED));
            dtos.add(dto);
        }
        return dtos;
    }

    @Transactional
    public HostelDto createHostel(Long instituteId, Hostel hostel) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));
        hostel.setInstitute(institute);
        Hostel saved = hostelRepository.save(hostel);
        return HostelDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CrewWorkloadDto> getCrewWorkloads(Long instituteId) {
        List<Department> departments = departmentRepository.findByInstituteId(instituteId);
        if (departments.isEmpty()) {
            departments = departmentRepository.findAll();
        }
        List<IssueStatus> activeStatuses = java.util.Arrays.asList(
                IssueStatus.REPORTED, IssueStatus.AI_ANALYZING, IssueStatus.ANALYZED,
                IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS, IssueStatus.AWAITING_VERIFICATION,
                IssueStatus.REOPENED
        );
        List<CrewWorkloadDto> list = new java.util.ArrayList<>();
        for (Department dept : departments) {
            CrewWorkloadDto dto = new CrewWorkloadDto();
            dto.setDepartmentId(dept.getId());
            dto.setName(dept.getName());
            dto.setDisplayName(dept.getDisplayName());
            dto.setDescription(dept.getDescription());

            List<User> staff = userRepository.findByInstituteIdAndDepartmentId(instituteId, dept.getId());
            dto.setStaffCount(staff.size());
            dto.setStaffNames(staff.stream().map(User::getFullName).collect(Collectors.toList()));

            dto.setActiveTasks(issueRepository.countByInstituteIdAndAssignedDepartmentIdAndStatusIn(instituteId, dept.getId(), activeStatuses));
            dto.setResolvedTasks(issueRepository.countByInstituteIdAndAssignedDepartmentIdAndStatus(instituteId, dept.getId(), IssueStatus.RESOLVED));
            dto.setTotalTasks(dto.getActiveTasks() + dto.getResolvedTasks());
            list.add(dto);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Department> getDepartments(Long instituteId) {
        return departmentRepository.findByInstituteId(instituteId);
    }

    @Transactional
    public Department createDepartment(Long instituteId, Department department) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));
        department.setInstitute(institute);
        return departmentRepository.save(department);
    }

    @Transactional(readOnly = true)
    public List<PasswordResetRequest> getPasswordResets(Long instituteId) {
        return passwordResetRequestRepository.findByInstituteIdOrderByCreatedAtDesc(instituteId);
    }

    @Transactional
    public PasswordResetRequest assignPasswordReset(Long instituteId, Long requestId, String handlerName, String handlerDepartment) {
        PasswordResetRequest request = passwordResetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

        if (!request.getInstitute().getId().equals(instituteId)) {
            throw new BadRequestException("Request does not belong to this institution.");
        }

        if (handlerName != null && !handlerName.trim().isEmpty()) {
            request.setAssignedHandler(handlerName.trim());
        }
        if (handlerDepartment != null && !handlerDepartment.trim().isEmpty()) {
            request.setAssignedDepartment(handlerDepartment.trim());
        }
        request.setStatus("ASSIGNED");
        return passwordResetRequestRepository.save(request);
    }

    @Transactional
    public CredentialResponse approvePasswordReset(Long instituteId, Long requestId, Long adminUserId) {
        PasswordResetRequest request = passwordResetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

        if (!request.getInstitute().getId().equals(instituteId)) {
            throw new BadRequestException("Request does not belong to this institution.");
        }

        User admin = userRepository.findById(adminUserId).orElse(null);
        User targetUser = request.getUser();

        String tempPassword = generateTempPassword();
        targetUser.setPasswordHash(passwordEncoder.encode(tempPassword));
        targetUser.setNeedsPasswordChange(true);
        userRepository.save(targetUser);

        request.setStatus("APPROVED");
        request.setReviewedBy(admin);
        request.setReviewedAt(ZonedDateTime.now());
        passwordResetRequestRepository.save(request);

        return new CredentialResponse(
                targetUser.getId(),
                targetUser.getFullName(),
                targetUser.getInstitutionalId(),
                targetUser.getEmail(),
                targetUser.getRole().name(),
                tempPassword,
                "Password reset approved. Please share temporary credentials."
        );
    }

    @Transactional
    public void rejectPasswordReset(Long instituteId, Long requestId, Long adminUserId) {
        PasswordResetRequest request = passwordResetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

        if (!request.getInstitute().getId().equals(instituteId)) {
            throw new BadRequestException("Request does not belong to this institution.");
        }

        User admin = userRepository.findById(adminUserId).orElse(null);
        request.setStatus("REJECTED");
        request.setReviewedBy(admin);
        request.setReviewedAt(ZonedDateTime.now());
        passwordResetRequestRepository.save(request);
    }
}
