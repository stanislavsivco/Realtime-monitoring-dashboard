package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.dto.AuthResponseDTO;
import com.realtime_monitoring_dashboard.backend.dto.LoginRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.UsernameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.VIEWER)
                .build();

        User saved = userRepository.save(user);
        return buildResponse(saved);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return buildResponse(user);
    }

    private AuthResponseDTO buildResponse(User user) {
        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return AuthResponseDTO.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}