package com.judicialflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final org.springframework.core.env.Environment environment;

    public SecurityConfig(org.springframework.core.env.Environment environment) {
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Security filter chain configuration.
     *
     * CSRF DECISION:
     * CSRF is disabled here because JudicialFlow currently operates as a stateless REST API
     * authenticating via HTTP Basic headers rather than browser cookie-backed sessions.
     * When transitioning to a token/session-based React dashboard, either Bearer tokens
     * (which are immune to cross-site request forgery) or CookieCsrfTokenRepository should
     * be configured to protect state-changing requests.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isDev = java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    // Only health check and login are public
                    auth.requestMatchers(
                            "/actuator/health",
                            "/api/v1/auth/login"
                    ).permitAll();

                    // Restrict Swagger/OpenAPI: dev profile permits all, otherwise requires authentication
                    if (isDev) {
                        auth.requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll();
                    } else {
                        auth.requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).authenticated();
                    }

                    // /api/v1/auth/me requires authentication
                    auth.requestMatchers("/api/v1/auth/me").authenticated();

                    // ADMIN only routes: batch triggers, audit logs, user management
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/v1/users/**").hasRole("ADMIN")

                        // Write endpoints (POST, PUT, DELETE, PATCH): REGISTRAR and ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/cases/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cases/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/cases/**").hasAnyRole("ADMIN", "REGISTRAR")

                        .requestMatchers(HttpMethod.POST, "/api/v1/judges/*/leave").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/judges/*/leaves/*").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/judges/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/judges/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/judges/**").hasAnyRole("ADMIN", "REGISTRAR")

                        .requestMatchers(HttpMethod.POST, "/api/v1/courtrooms/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/courtrooms/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/courtrooms/**").hasAnyRole("ADMIN", "REGISTRAR")

                        .requestMatchers(HttpMethod.POST, "/api/v1/scheduling/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/scheduling/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/scheduling/**").hasAnyRole("ADMIN", "REGISTRAR")

                        .requestMatchers(HttpMethod.POST, "/api/v1/estimates/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/dev/**").hasAnyRole("ADMIN", "REGISTRAR")

                        .requestMatchers(HttpMethod.POST, "/api/v1/hearings/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/hearings/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/hearings/**").hasAnyRole("ADMIN", "REGISTRAR")

                        // Read endpoints (GET): JUDGE (read-only), REGISTRAR, ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/cases/**").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/judges/**").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/courtrooms/**").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/priority/**").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/scheduling/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/estimates/**").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/hearings/**").hasAnyRole("ADMIN", "REGISTRAR", "JUDGE")

                        // Lock down all other endpoints
                        .anyRequest().authenticated();
                });

        return http.build();
    }
}
