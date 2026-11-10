package com.realtime_monitoring_dashboard.backend.config;

import java.nio.charset.StandardCharsets;

import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.security.UsernameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No ADMIN exists and ADMIN_PASSWORD is not set. Skipping admin creation.");
            return;
        }

        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }

        if (adminPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("ADMIN_PASSWORD must not be longer than " + MAX_PASSWORD_BYTES + " bytes");
        }

        String username = UsernameNormalizer.normalize(adminUsername);
        if (username == null || username.isBlank()) {
            log.warn("No ADMIN exists and ADMIN_USERNAME is blank. Skipping admin creation.");
            return;
        }

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.warn("No ADMIN exists, but username '{}' is already taken by another user. Skipping admin creation.", username);
            return;
        }

        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Initial admin '{}' created.", username);
    }
}
