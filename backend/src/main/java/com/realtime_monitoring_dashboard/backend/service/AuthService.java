package com.realtime_monitoring_dashboard.backend.service;

import com.realtime_monitoring_dashboard.backend.dto.AuthResponseDTO;
import com.realtime_monitoring_dashboard.backend.dto.LoginRequestDTO;
import com.realtime_monitoring_dashboard.backend.dto.RegisterRequestDTO;
import com.realtime_monitoring_dashboard.backend.exception.UsernameAlreadyExistsException;
import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;
import com.realtime_monitoring_dashboard.backend.security.JwtService;
import com.realtime_monitoring_dashboard.backend.security.UsernameNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        String username = UsernameNormalizer.normalize(request.getUsername());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw usernameTaken(username);
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.VIEWER)
                .build();

        try {
            User saved = userRepository.saveAndFlush(user);
            return buildResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw usernameTaken(username);
        }
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        String username = UsernameNormalizer.normalize(request.getUsername());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.getPassword()));

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return buildResponse(user);
    }

    private UsernameAlreadyExistsException usernameTaken(String username) {
        return new UsernameAlreadyExistsException("Username '" + username + "' is already taken");
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
