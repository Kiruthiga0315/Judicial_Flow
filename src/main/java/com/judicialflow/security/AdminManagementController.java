package com.judicialflow.security;

import com.judicialflow.audit.AuditFilterCriteria;
import com.judicialflow.audit.AuditService;
import com.judicialflow.common.AuditLogRepository;
import com.judicialflow.common.models.AuditLogEntry;
import com.judicialflow.ingestion.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Management", description = "Admin-only operations for audit logs and user management")
public class AdminManagementController {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private UserRole role;
        private String email;
    }

    @Data
    @Builder
    public static class UserDto {
        private UUID id;
        private String username;
        private UserRole role;
        private String email;
        private boolean enabled;
        private LocalDateTime createdAt;
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get system audit logs (legacy)")
    public ResponseEntity<List<AuditLogEntry>> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAll());
    }

    @GetMapping("/audit")
    @Operation(summary = "Query persisted audit log", description = "ADMIN-only paginated query for audit trail with filters, sorted newest first.")
    public ResponseEntity<PageResponse<AuditLogEntry>> queryAuditLog(
            @ModelAttribute AuditFilterCriteria criteria,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogEntry> page = auditService.findAuditLogs(criteria, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @GetMapping("/users")
    @Operation(summary = "List all registered users")
    public ResponseEntity<List<UserDto>> listUsers() {
        List<UserDto> users = userRepository.findAll().stream()
                .map(u -> UserDto.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .role(u.getRole())
                        .email(u.getEmail())
                        .enabled(u.isEnabled())
                        .createdAt(u.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    @Operation(summary = "Create a new user")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .email(request.getEmail())
                .enabled(true)
                .build();
        user = userRepository.save(user);

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();

        // Audit user creation (excluding password hash)
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("id", user.getId().toString());
        afterState.put("username", user.getUsername());
        afterState.put("role", user.getRole().name());
        afterState.put("email", user.getEmail());
        afterState.put("enabled", user.isEnabled());

        auditService.log(
                "User",
                user.getId().toString(),
                "CREATE",
                "USER_CREATED",
                null,
                afterState,
                "Created user " + user.getUsername() + " with role " + user.getRole()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
    }
}
