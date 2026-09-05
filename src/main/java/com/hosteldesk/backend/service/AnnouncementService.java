package com.hosteldesk.backend.service;

import com.hosteldesk.backend.dto.AnnouncementDto;
import com.hosteldesk.backend.dto.CreateAnnouncementRequest;
import com.hosteldesk.backend.entity.*;
import com.hosteldesk.backend.exception.BadRequestException;
import com.hosteldesk.backend.exception.ResourceNotFoundException;
import com.hosteldesk.backend.repository.AnnouncementRepository;
import com.hosteldesk.backend.repository.HostelRepository;
import com.hosteldesk.backend.repository.InstituteRepository;
import com.hosteldesk.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final InstituteRepository instituteRepository;
    private final HostelRepository hostelRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               InstituteRepository instituteRepository,
                               HostelRepository hostelRepository,
                               UserRepository userRepository,
                               NotificationService notificationService) {
        this.announcementRepository = announcementRepository;
        this.instituteRepository = instituteRepository;
        this.hostelRepository = hostelRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> getStudentAnnouncements(Long instituteId, Long hostelId) {
        if (instituteId == null) {
            return java.util.Collections.emptyList();
        }
        return announcementRepository.findActiveForStudent(instituteId, hostelId, ZonedDateTime.now())
                .stream()
                .map(AnnouncementDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> getInstituteAnnouncements(Long instituteId) {
        if (instituteId == null) {
            return java.util.Collections.emptyList();
        }
        return announcementRepository.findActiveForInstitute(instituteId, ZonedDateTime.now())
                .stream()
                .map(AnnouncementDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public AnnouncementDto createAnnouncement(Long instituteId, User author, CreateAnnouncementRequest request) {
        if (instituteId == null) {
            throw new BadRequestException("Institute ID is required to post an announcement");
        }

        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute not found: " + instituteId));

        Hostel hostel = null;
        if (request.getHostelId() != null) {
            hostel = hostelRepository.findById(request.getHostelId()).orElse(null);
        } else if (author.getRole() == Role.WARDEN && author.getHostel() != null) {
            hostel = author.getHostel();
        }

        ZonedDateTime expiresAt = null;
        if (request.getDurationHours() != null && request.getDurationHours() > 0) {
            expiresAt = ZonedDateTime.now().plusHours(request.getDurationHours());
        }

        String authorRole = author.getRole() == Role.WARDEN ? "WARDEN" : "INSTITUTE_ADMIN";

        Announcement announcement = new Announcement();
        announcement.setInstitute(institute);
        announcement.setHostel(hostel);
        announcement.setAuthor(author);
        announcement.setAuthorName(author.getFullName());
        announcement.setAuthorRole(authorRole);
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent().trim());
        announcement.setPinned(request.getPinned() != null ? request.getPinned() : false);
        announcement.setExpiresAt(expiresAt);

        announcement = announcementRepository.save(announcement);

        // Notify students
        List<User> targetStudents;
        if (hostel != null) {
            targetStudents = userRepository.findByHostelIdAndRole(hostel.getId(), Role.STUDENT);
        } else {
            targetStudents = userRepository.findByInstituteIdAndRole(instituteId, Role.STUDENT);
        }

        String notifPrefix = author.getRole() == Role.WARDEN ? "Hostel Notice: " : "Campus Bulletin: ";
        for (User student : targetStudents) {
            notificationService.createNotification(
                    student,
                    notifPrefix + announcement.getTitle(),
                    announcement.getContent().length() > 120 ? announcement.getContent().substring(0, 117) + "..." : announcement.getContent(),
                    "ANNOUNCEMENT",
                    null
            );
        }

        return AnnouncementDto.fromEntity(announcement);
    }
}
