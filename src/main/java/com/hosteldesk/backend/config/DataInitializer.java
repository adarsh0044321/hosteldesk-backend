package com.hosteldesk.backend.config;

import com.hosteldesk.backend.entity.*;
import com.hosteldesk.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final HostelRepository hostelRepository;
    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final UserRepository userRepository;
    private final IssueRepository issueRepository;
    private final IssueAiAnalysisRepository aiAnalysisRepository;
    private final IssueActivityRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final InfrastructureInsightRepository insightRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(HostelRepository hostelRepository,
                           BlockRepository blockRepository,
                           RoomRepository roomRepository,
                           DepartmentRepository departmentRepository,
                           RoutingRuleRepository routingRuleRepository,
                           UserRepository userRepository,
                           IssueRepository issueRepository,
                           IssueAiAnalysisRepository aiAnalysisRepository,
                           IssueActivityRepository activityRepository,
                           NotificationRepository notificationRepository,
                           InfrastructureInsightRepository insightRepository,
                           PasswordEncoder passwordEncoder) {
        this.hostelRepository = hostelRepository;
        this.blockRepository = blockRepository;
        this.roomRepository = roomRepository;
        this.departmentRepository = departmentRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.userRepository = userRepository;
        this.issueRepository = issueRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.activityRepository = activityRepository;
        this.notificationRepository = notificationRepository;
        this.insightRepository = insightRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking and initializing database seed data...");

        // 1. Hostels
        Hostel tagoreHall = hostelRepository.findByName("Tagore Hall")
                .orElseGet(() -> hostelRepository.save(new Hostel(null, "Tagore Hall", "North Campus", "Primary undergraduate residence", true)));
        Hostel shastriHall = hostelRepository.findByName("Shastri Hall")
                .orElseGet(() -> hostelRepository.save(new Hostel(null, "Shastri Hall", "North Campus", "Postgraduate residence", true)));

        // 2. Blocks
        Block blockA = blockRepository.findByHostelIdAndName(tagoreHall.getId(), "Block A")
                .orElseGet(() -> blockRepository.save(new Block(null, tagoreHall, "Block A")));
        Block blockB = blockRepository.findByHostelIdAndName(tagoreHall.getId(), "Block B")
                .orElseGet(() -> blockRepository.save(new Block(null, tagoreHall, "Block B")));

        // 3. Rooms
        roomRepository.findByBlockIdAndRoomNumber(blockB.getId(), "204")
                .orElseGet(() -> roomRepository.save(new Room(null, blockB, "204", 2)));

        // 4. Departments
        Department plumbing = departmentRepository.findByName("PLUMBING")
                .orElseGet(() -> departmentRepository.save(new Department(null, "PLUMBING", "Plumbing & Water Supply", "Water leaks and sanitation", true)));
        Department electrical = departmentRepository.findByName("ELECTRICAL")
                .orElseGet(() -> departmentRepository.save(new Department(null, "ELECTRICAL", "Electrical & Power Operations", "Power and wiring safety", true)));
        Department carpentry = departmentRepository.findByName("CARPENTRY")
                .orElseGet(() -> departmentRepository.save(new Department(null, "CARPENTRY", "Carpentry & Furniture", "Locks and furniture", true)));
        Department cleaning = departmentRepository.findByName("CLEANING")
                .orElseGet(() -> departmentRepository.save(new Department(null, "CLEANING", "Housekeeping & Sanitation", "Corridor and washroom sanitation", true)));
        Department internet = departmentRepository.findByName("INTERNET")
                .orElseGet(() -> departmentRepository.save(new Department(null, "INTERNET", "IT & Campus Network", "Wi-Fi and LAN connectivity", true)));
        Department civil = departmentRepository.findByName("CIVIL")
                .orElseGet(() -> departmentRepository.save(new Department(null, "CIVIL", "Civil Infrastructure", "Masonry and dampness", true)));
        Department general = departmentRepository.findByName("GENERAL")
                .orElseGet(() -> departmentRepository.save(new Department(null, "GENERAL", "General Operations & Warden Desk", "General hostel complaints", true)));

        // 5. Routing Rules
        createRoutingRuleIfNotExists("PLUMBING", plumbing, "P2_HIGH");
        createRoutingRuleIfNotExists("ELECTRICAL", electrical, "P1_URGENT");
        createRoutingRuleIfNotExists("CARPENTRY", carpentry, "P3_MEDIUM");
        createRoutingRuleIfNotExists("CLEANING", cleaning, "P3_MEDIUM");
        createRoutingRuleIfNotExists("INTERNET", internet, "P3_MEDIUM");
        createRoutingRuleIfNotExists("CIVIL", civil, "P3_MEDIUM");
        createRoutingRuleIfNotExists("GENERAL", general, "P3_MEDIUM");

        // 6. Users
        User student = userRepository.findByEmail("aarav@campus.edu")
                .orElseGet(() -> userRepository.save(new User(
                        null, "Aarav Patel", "aarav@campus.edu", "+91 98765 43210", "ST-8819",
                        passwordEncoder.encode("student123"), Role.STUDENT, AccountStatus.ACTIVE,
                        tagoreHall, null, "204"
                )));

        User warden = userRepository.findByEmail("warden.sharma@campus.edu")
                .orElseGet(() -> userRepository.save(new User(
                        null, "Warden R. Sharma", "warden.sharma@campus.edu", "+91 98765 00001", "WR-1001",
                        passwordEncoder.encode("warden123"), Role.WARDEN, AccountStatus.ACTIVE,
                        tagoreHall, null, null
                )));

        User staff = userRepository.findByEmail("suresh@campus.edu")
                .orElseGet(() -> userRepository.save(new User(
                        null, "Suresh Kumar", "suresh@campus.edu", "+91 98765 11112", "STF-201",
                        passwordEncoder.encode("staff123"), Role.MAINTENANCE_STAFF, AccountStatus.ACTIVE,
                        tagoreHall, plumbing, null
                )));

        userRepository.findByEmail("admin@campus.edu")
                .orElseGet(() -> userRepository.save(new User(
                        null, "System Administrator", "admin@campus.edu", "+91 98765 99999", "ADM-001",
                        passwordEncoder.encode("admin123"), Role.ADMIN, AccountStatus.ACTIVE,
                        null, null, null
                )));

        // 7. Seed Initial Sample Issues for realistic demo experience
        if (issueRepository.count() == 0) {
            log.info("Seeding initial reference issues (#HD-1042, #HD-1038, #HD-4819)...");

            // Issue 1: HD-1042 (Awaiting student verification)
            Issue issue1 = new Issue();
            issue1.setTicketNumber("HD-1042");
            issue1.setReportedBy(student);
            issue1.setHostel(tagoreHall);
            issue1.setBlockName("Block B");
            issue1.setRoomNumber("204");
            issue1.setCategory("PLUMBING");
            issue1.setTitle("Bathroom ceiling water leakage");
            issue1.setDescription("There is water leaking heavily from the bathroom ceiling near the light fixture. Plaster is damp.");
            issue1.setPriority(IssuePriority.P1_URGENT);
            issue1.setStatus(IssueStatus.AWAITING_VERIFICATION);
            issue1.setAssignedDepartment(plumbing);
            issue1.setAssignedStaff(staff);
            issue1.setTechnicianNotes("Replaced faulty drainage seal on upper floor connection. Tested water flow for 15 minutes, sealed and dry.");
            issue1.setCreatedAt(ZonedDateTime.now().minusHours(3));
            issue1 = issueRepository.save(issue1);

            // AI Analysis for Issue 1
            IssueAiAnalysis ai1 = new IssueAiAnalysis(
                    null, issue1, "PLUMBING", "P1_URGENT", "PLUMBING",
                    "Water leakage detected near electrical fixture, poses potential hazard.",
                    "Water dripping near ceiling light fitting", new BigDecimal("0.940"), false
            );
            aiAnalysisRepository.save(ai1);

            // Activities for Issue 1
            activityRepository.save(new IssueActivity(null, issue1, student, "REPORTED", "Issue submitted by Aarav Patel"));
            activityRepository.save(new IssueActivity(null, issue1, null, "ANALYZED", "AI classified as PLUMBING (P1 Urgent)"));
            activityRepository.save(new IssueActivity(null, issue1, warden, "ASSIGNED", "Assigned to Tech Suresh Kumar (Plumbing)"));
            activityRepository.save(new IssueActivity(null, issue1, staff, "IN_PROGRESS", "Work started on ceiling pipe joint"));
            activityRepository.save(new IssueActivity(null, issue1, staff, "COMPLETED", "Work completed. Submitted for student verification."));

            // Issue 2: HD-1038 (In Progress)
            Issue issue2 = new Issue();
            issue2.setTicketNumber("HD-1038");
            issue2.setReportedBy(student);
            issue2.setHostel(tagoreHall);
            issue2.setBlockName("Block B");
            issue2.setRoomNumber("204");
            issue2.setCategory("ELECTRICAL");
            issue2.setTitle("Ceiling fan regulator sparking");
            issue2.setDescription("The speed regulator sparks when rotated to speed 3 and smells faintly burnt.");
            issue2.setPriority(IssuePriority.P2_HIGH);
            issue2.setStatus(IssueStatus.IN_PROGRESS);
            issue2.setAssignedDepartment(electrical);
            issue2.setTechnicianNotes("Regulator switch ordered from stores.");
            issue2.setCreatedAt(ZonedDateTime.now().minusDays(1));
            issue2 = issueRepository.save(issue2);

            activityRepository.save(new IssueActivity(null, issue2, student, "REPORTED", "Reported by Aarav Patel"));
            activityRepository.save(new IssueActivity(null, issue2, warden, "ASSIGNED", "Assigned to Electrical Department"));

            // Notifications
            notificationRepository.save(new Notification(
                    null, student, "Repair Ready for Verification",
                    "Caretaker Suresh Kumar finished work on #HD-1042. Please check your ceiling and confirm.",
                    "VERIFICATION_REQUEST", issue1, false
            ));
            notificationRepository.save(new Notification(
                    null, student, "Part Ordered for #HD-1038",
                    "Replacement fan regulator switch has been requisitioned from central stores.",
                    "WORK_PROGRESS", issue2, true
            ));

            // 8. Seed Recurring Insight
            insightRepository.save(new InfrastructureInsight(
                    null, tagoreHall, "Block B", "PLUMBING", 7, 14,
                    "7 plumbing complaints in 14 days across Rooms 201-206",
                    "Shared vertical drainage stack line pressure drop",
                    "Inspect vertical riser shaft above 2nd floor corridor",
                    ZonedDateTime.now().minusDays(1)
            ));
        }

        log.info("Database seed initialization completed successfully.");
    }

    private void createRoutingRuleIfNotExists(String category, Department dept, String priority) {
        if (routingRuleRepository.findByCategoryAndActiveTrue(category).isEmpty()) {
            routingRuleRepository.save(new RoutingRule(null, category, dept, priority, true));
        }
    }
}
