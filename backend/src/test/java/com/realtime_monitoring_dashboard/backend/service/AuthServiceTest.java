package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.realtime_monitoring_dashboard.backend.dto.AuthResponseDTO;
import com.realtime_monitoring_dashboard.backend.dto.LoginRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.UsernameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerStoresLowercaseUsernameWithViewerRole() {
        when(userRepository.existsByUsernameIgnoreCase("john")).thenReturn(false);
        when(passwordEncoder.encode("securepass1")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken("john", "VIEWER")).thenReturn("token");

        AuthResponseDTO response = authService.register(new RegisterRequestDTO("John", "securepass1"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("john");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.VIEWER);
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getToken()).isEqualTo("token");
    }

    @Test
    void registerRejectsUsernameThatDiffersOnlyInCase() {
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequestDTO("ADMIN", "securepass1")))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessage("Username 'admin' is already taken");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerTranslatesConcurrentDuplicateIntoConflict() {
        when(userRepository.existsByUsernameIgnoreCase("john")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> authService.register(new RegisterRequestDTO("john", "securepass1")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void loginNormalizesUsernameBeforeAuthentication() {
        User user = User.builder().id(1L).username("john").password("hashed").role(Role.VIEWER).build();
        when(userRepository.findByUsernameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("john", "VIEWER")).thenReturn("token");

        AuthResponseDTO response = authService.login(new LoginRequestDTO("  JOHN ", "securepass1"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("john");
        assertThat(response.getUsername()).isEqualTo("john");
    }

    @Test
    void userToStringDoesNotExposePassword() {
        User user = User.builder().id(1L).username("john").password("secret-hash").role(Role.VIEWER).build();

        assertThat(user.toString()).doesNotContain("secret-hash");
    }
}
