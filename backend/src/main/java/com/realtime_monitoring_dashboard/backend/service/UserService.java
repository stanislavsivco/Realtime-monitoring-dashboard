package com.realtime_monitoring_dashboard.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtime_monitoring_dashboard.backend.dto.UserDTO;
import com.realtime_monitoring_dashboard.backend.exception.LastAdminException;
import com.realtime_monitoring_dashboard.backend.exception.ResourceNotFoundException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToDTO).toList();
    }

    @Transactional
    public UserDTO changeUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + userId + " does not exist"));

        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            List<User> admins = userRepository.findAllByRoleForUpdate(Role.ADMIN);
            boolean otherAdminExists = admins.stream().anyMatch(admin -> !admin.getId().equals(userId));
            if (!otherAdminExists) {
                throw new LastAdminException("Cannot remove the ADMIN role from the last administrator");
            }
        }

        user.setRole(newRole);
        return mapToDTO(userRepository.save(user));
    }

    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
