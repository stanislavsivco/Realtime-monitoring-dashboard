package com.realtime_monitoring_dashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realtime_monitoring_dashboard.backend.dto.UserDTO;
import com.realtime_monitoring_dashboard.backend.exception.LastAdminException;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static User user(Long id, String username, Role role) {
        return User.builder().id(id).username(username).password("hashed").role(role).build();
    }

    @Test
    void changeRolePromotesViewerToAdmin() {
        User viewer = user(2L, "john", Role.VIEWER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDTO result = userService.changeUserRole(2L, Role.ADMIN);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).findAllByRoleForUpdate(any(Role.class));
    }

    @Test
    void changeRoleDemotesAdminWhenAnotherAdminExists() {
        User admin = user(1L, "admin", Role.ADMIN);
        User otherAdmin = user(3L, "boss", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAllByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(admin, otherAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDTO result = userService.changeUserRole(1L, Role.VIEWER);

        assertThat(result.getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    void changeRoleRejectsDemotingLastAdmin() {
        User admin = user(1L, "admin", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAllByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> userService.changeUserRole(1L, Role.VIEWER))
                .isInstanceOf(LastAdminException.class);

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeRoleKeepingAdminDoesNotCheckOtherAdmins() {
        User admin = user(1L, "admin", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDTO result = userService.changeUserRole(1L, Role.ADMIN);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).findAllByRoleForUpdate(any(Role.class));
    }

    @Test
    void changeRoleForUnknownUserThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeUserRole(99L, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
