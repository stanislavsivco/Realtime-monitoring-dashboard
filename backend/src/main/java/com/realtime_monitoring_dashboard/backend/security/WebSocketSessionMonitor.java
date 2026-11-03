package com.realtime_monitoring_dashboard.backend.security;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSocketSessionMonitor {

    private static final int CHECK_INTERVAL_MS = 30_000;

    private final WebSocketSessionRegistry sessionRegistry;
    private final UserRepository userRepository;
    private final MessageChannel clientOutboundChannel;

    public WebSocketSessionMonitor(WebSocketSessionRegistry sessionRegistry,
                                   UserRepository userRepository,
                                   @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @Scheduled(fixedRate = CHECK_INTERVAL_MS)
    public void closeInvalidSessions() {
        closeInvalidSessions(Instant.now());
    }

    void closeInvalidSessions(Instant now) {
        Map<String, TokenClaims> sessions = sessionRegistry.snapshot();
        if (sessions.isEmpty()) {
            return;
        }

        Set<String> usernames = sessions.values().stream().map(TokenClaims::username).collect(Collectors.toSet());
        Map<String, Integer> currentVersions = userRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(User::getUsername, User::getTokenVersion));

        sessions.forEach((sessionId, claims) -> {
            boolean expired = !claims.expiresAt().isAfter(now);
            boolean revoked = !claims.tokenVersion().equals(currentVersions.get(claims.username()));
            if (expired || revoked) {
                close(sessionId, claims);
            }
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessionRegistry.remove(event.getSessionId());
    }

    private void close(String sessionId, TokenClaims claims) {
        log.info("Closing WebSocket session of user '{}': token expired or revoked", claims.username());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setSessionId(sessionId);
        accessor.setMessage("Token expired or revoked");
        clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        sessionRegistry.remove(sessionId);
    }
}
