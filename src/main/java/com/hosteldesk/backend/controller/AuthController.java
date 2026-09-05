package com.hosteldesk.backend.controller;

import com.hosteldesk.backend.dto.LoginRequest;
import com.hosteldesk.backend.dto.LoginResponse;
import com.hosteldesk.backend.dto.RegisterRequest;
import com.hosteldesk.backend.dto.UserDto;
import com.hosteldesk.backend.security.UserPrincipal;
import com.hosteldesk.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserDto userDto = authService.register(registerRequest);
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        UserDto userDto = authService.getCurrentUser(principal);
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        return ResponseEntity.ok(Map.of("message", "If account exists for " + email + ", reset instructions have been dispatched."));
    }
}
