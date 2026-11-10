package com.realtime_monitoring_dashboard.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminSeeder adminSeeder;

    private void configure(String username, String password) {
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", username);
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", password);
    }

    @Test
    void createsAdminWithNormalizedUsernameAndHashedPassword() {
        configure("  Admin ", "strong-password");
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(false);
        when(passwordEncoder.encode("strong-password")).thenReturn("hashed");

        adminSeeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shortPasswordStopsStartupWithoutCreatingAdmin() {
        configure("admin", "short");
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> adminSeeder.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8 characters");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void passwordOverBcryptLimitStopsStartupWithoutCreatingAdmin() {
        configure("admin", "č".repeat(40));
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> adminSeeder.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 bytes");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void existingAdminSkipsAllChecks() {
        configure("admin", "short");
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        adminSeeder.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void missingPasswordSkipsCreation() {
        configure("admin", "");
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        adminSeeder.run();

        verify(userRepository, never()).save(any(User.class));
    }
}
