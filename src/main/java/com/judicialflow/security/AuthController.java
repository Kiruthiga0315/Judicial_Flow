package com.judicialflow.security;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@lombok.RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @Data
    @Builder
    public static class AuthStatusResponse {
        private boolean authenticated;
        private String username;
        private List<String> roles;
        private UUID judgeId;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthStatusResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(AuthStatusResponse.builder()
                    .authenticated(false)
                    .build());
        }

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        UUID judgeId = userRepository.findByUsername(auth.getName())
                .map(User::getJudgeId)
                .orElse(null);

        return ResponseEntity.ok(AuthStatusResponse.builder()
                .authenticated(true)
                .username(auth.getName())
                .roles(roles)
                .judgeId(judgeId)
                .build());
    }

    @org.springframework.web.bind.annotation.PostMapping("/login")
    public ResponseEntity<AuthStatusResponse> login() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(AuthStatusResponse.builder()
                    .authenticated(false)
                    .build());
        }

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(AuthStatusResponse.builder()
                .authenticated(true)
                .username(auth.getName())
                .roles(roles)
                .build());
    }
}
