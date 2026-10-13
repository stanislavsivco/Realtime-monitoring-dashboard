package com.realtime_monitoring_dashboard.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime_monitoring_dashboard.backend.dto.ChangeRoleRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.UserDTO;
import com.realtime_monitoring_dashboard.backend.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User management (admin only)")
public class UserController {

    private final UserService userService;

    @Operation(summary = "List all users")
    @ApiResponse(responseCode = "200", description = "User list returned")
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Change a user's role")
    @ApiResponse(responseCode = "200", description = "Role updated")
    @ApiResponse(responseCode = "404", description = "User with this ID does not exist")
    @PatchMapping("/{id}/role")
    public ResponseEntity<UserDTO> changeRole(
            @Parameter(description = "User ID") @PathVariable @Positive Long id,
            @RequestBody @Valid ChangeRoleRequestDTO request) {
        return ResponseEntity.ok(userService.changeUserRole(id, request.getRole()));
    }
}
