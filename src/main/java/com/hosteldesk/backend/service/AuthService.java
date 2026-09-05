package com.hosteldesk.backend.service;

import com.hosteldesk.backend.config.AppProperties;
import com.hosteldesk.backend.dto.*;
import com.hosteldesk.backend.entity.AccountStatus;
import com.hosteldesk.backend.entity.Hostel;
import com.hosteldesk.backend.entity.Role;
import com.hosteldesk.backend.entity.User;
import com.hosteldesk.backend.exception.BadRequestException;
import com.hosteldesk.backend.exception.ForbiddenException;
import com.hosteldesk.backend.exception.ResourceNotFoundException;
import com.hosteldesk.backend.repository.HostelRepository;
import com.hosteldesk.backend.repository.UserRepository;
import com.hosteldesk.backend.security.JwtTokenProvider;
import com.hosteldesk.backend.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final HostelRepository hostelRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AppProperties appProperties;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       HostelRepository hostelRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AppProperties appProperties) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.hostelRepository = hostelRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.appProperties = appProperties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getEmailOrInstitutionalId();
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BadRequestException("Email or Institutional ID is required");
        }

        User user = userRepository.findByEmailOrInstitutionalId(identifier, identifier)
                .orElseThrow(() -> new BadRequestException("Invalid email/institutional ID or password"));

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("Account is " + user.getStatus() + ". Please contact hostel administrator.");
        }

        // Strict role segregation: Reject students attempting to log into Admin application
        if ("ADMIN".equalsIgnoreCase(request.getTargetApp()) && user.getRole() == Role.STUDENT) {
            throw new ForbiddenException("Access Denied: Student accounts are not permitted to access the HostelDesk Admin application.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        user.setLastLoginAt(ZonedDateTime.now());
        userRepository.save(user);

        String jwt = tokenProvider.generateToken(authentication);
        return new LoginResponse(jwt, appProperties.getJwt().getExpirationMs() / 1000, UserDto.fromEntity(user));
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }
        if (userRepository.existsByInstitutionalId(request.getInstitutionalId())) {
            throw new BadRequestException("Institutional ID is already registered: " + request.getInstitutionalId());
        }

        Hostel hostel = null;
        if (request.getHostelId() != null) {
            hostel = hostelRepository.findById(request.getHostelId()).orElse(null);
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setInstitutionalId(request.getInstitutionalId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);
        user.setHostel(hostel);
        user.setRoomNumber(request.getRoomNumber());

        User saved = userRepository.save(user);
        return UserDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getId()));
        return UserDto.fromEntity(user);
    }
}
