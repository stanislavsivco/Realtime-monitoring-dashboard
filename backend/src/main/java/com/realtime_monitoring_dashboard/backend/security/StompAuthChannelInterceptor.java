package com.realtime_monitoring_dashboard.backend.security;

import java.security.Principal;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOPIC_PREFIX = "/topic/";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            accessor.setUser(authenticate(accessor));
        } else if (command == StompCommand.SUBSCRIBE) {
            checkSubscription(accessor);
        } else if (command == StompCommand.SEND) {
            throw new AccessDeniedException("Clients are not allowed to send messages");
        }

        return message;
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationCredentialsNotFoundException("Missing bearer token");
        }

        try {
            String username = jwtService.extractUsername(header.substring(BEARER_PREFIX.length()));
            if (username == null) {
                throw new BadCredentialsException("Invalid token");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!username.equals(userDetails.getUsername())) {
                throw new BadCredentialsException("Invalid token");
            }

            return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            throw new BadCredentialsException("Invalid or expired token");
        }
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new AuthenticationCredentialsNotFoundException("Not authenticated");
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(TOPIC_PREFIX) || destination.contains("..")) {
            throw new AccessDeniedException("Subscription destination is not allowed");
        }
    }
}
