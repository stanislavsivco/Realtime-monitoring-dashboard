package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.realtime_monitoring_dashboard.backend.model.Role;
import com.realtime_monitoring_dashboard.backend.model.User;
import com.realtime_monitoring_dashboard.backend.repository.UserRepository;

class WebSocketSessionMonitorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final WebSocketSessionMonitor monitor = new WebSocketSessionMonitor(registry, userRepository, channel);

    private static User user(String username, int tokenVersion) {
        return User.builder().username(username).password("x").role(Role.VIEWER).tokenVersion(tokenVersion).build();
    }

    @Test
    void noOpenSessionsMeansNoDatabaseQuery() {
        monitor.closeInvalidSessions(NOW);

        verifyNoInteractions(userRepository, channel);
    }

    @Test
    void validSessionStaysOpen() {
        registry.register("s1", new TokenClaims("john", 0, LATER));
        when(userRepository.findByUsernameIn(Set.of("john"))).thenReturn(List.of(user("john", 0)));

        monitor.closeInvalidSessions(NOW);

        verify(channel, never()).send(any(Message.class));
        assertThat(registry.snapshot()).containsKey("s1");
    }

    @Test
    void expiredTokenClosesSessionWithStompError() {
        registry.register("s1", new TokenClaims("john", 0, NOW.minusSeconds(1)));
        when(userRepository.findByUsernameIn(Set.of("john"))).thenReturn(List.of(user("john", 0)));

        monitor.closeInvalidSessions(NOW);

        ArgumentCaptor<Message<?>> sent = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(sent.capture());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sent.getValue());
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getSessionId()).isEqualTo("s1");
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void tokenExpiringExactlyNowClosesSession() {
        registry.register("s1", new TokenClaims("john", 0, NOW));
        when(userRepository.findByUsernameIn(Set.of("john"))).thenReturn(List.of(user("john", 0)));

        monitor.closeInvalidSessions(NOW);

        verify(channel).send(any(Message.class));
    }

    @Test
    void revokedTokenClosesSession() {
        registry.register("s1", new TokenClaims("john", 0, LATER));
        when(userRepository.findByUsernameIn(Set.of("john"))).thenReturn(List.of(user("john", 1)));

        monitor.closeInvalidSessions(NOW);

        verify(channel).send(any(Message.class));
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void deletedUserClosesSession() {
        registry.register("s1", new TokenClaims("ghost", 0, LATER));
        when(userRepository.findByUsernameIn(Set.of("ghost"))).thenReturn(List.of());

        monitor.closeInvalidSessions(NOW);

        verify(channel).send(any(Message.class));
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void onlyInvalidSessionIsClosedAndUsersAreLoadedOnce() {
        registry.register("valid", new TokenClaims("john", 1, LATER));
        registry.register("revoked", new TokenClaims("john", 0, LATER));
        when(userRepository.findByUsernameIn(Set.of("john"))).thenReturn(List.of(user("john", 1)));

        monitor.closeInvalidSessions(NOW);

        verify(userRepository).findByUsernameIn(Set.of("john"));
        assertThat(registry.snapshot()).containsOnlyKeys("valid");
    }

    @Test
    void disconnectRemovesSession() {
        registry.register("s1", new TokenClaims("john", 0, LATER));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("s1");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        monitor.onDisconnect(new SessionDisconnectEvent(this, message, "s1", CloseStatus.NORMAL));

        assertThat(registry.snapshot()).isEmpty();
    }
}
