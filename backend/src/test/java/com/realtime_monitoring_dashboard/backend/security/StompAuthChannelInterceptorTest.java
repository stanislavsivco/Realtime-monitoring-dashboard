package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";
    private static final String OTHER_SECRET = "another-secret-that-is-long-enough-hs256";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);
    private final UserDetailsService userDetailsService = username -> {
        if (!"john".equals(username)) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return User.withUsername("john").password("x").roles("VIEWER").build();
    };
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwtService, userDetailsService);
    private final MessageChannel channel = (message, timeout) -> true;

    private static StompHeaderAccessor accessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor connectWith(String authorization) {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        return accessor;
    }

    private static StompHeaderAccessor subscribe(String destination, boolean authenticated) {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(new UsernamePasswordAuthenticationToken("john", null, List.of()));
        }
        return accessor;
    }

    @Test
    void connectWithValidTokenSetsAuthenticatedUser() {
        StompHeaderAccessor accessor = connectWith("Bearer " + jwtService.generateToken("john", "VIEWER"));

        interceptor.preSend(message(accessor), channel);

        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        UsernamePasswordAuthenticationToken user = (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertThat(user.getName()).isEqualTo("john");
        assertThat(user.isAuthenticated()).isTrue();
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_VIEWER");
    }

    @Test
    void stompCommandIsAuthenticatedLikeConnect() {
        StompHeaderAccessor accessor = accessor(StompCommand.STOMP);
        accessor.addNativeHeader("Authorization", "Bearer " + jwtService.generateToken("john", "VIEWER"));

        interceptor.preSend(message(accessor), channel);

        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        StompHeaderAccessor accessor = connectWith(null);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer", "Basic am9objpwYXNz", "bearer abc"})
    void connectWithoutBearerTokenIsRejected(String authorization) {
        StompHeaderAccessor accessor = connectWith(authorization);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer ", "Bearer not-a-jwt", "Bearer a.b.c"})
    void connectWithMalformedTokenIsRejected(String authorization) {
        StompHeaderAccessor accessor = connectWith(authorization);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void connectWithTokenSignedByAnotherKeyIsRejected() {
        String foreign = new JwtService(OTHER_SECRET, 60_000).generateToken("john", "ADMIN");

        assertThatThrownBy(() -> interceptor.preSend(message(connectWith("Bearer " + foreign)), channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectWithExpiredTokenIsRejected() {
        Date past = new Date(System.currentTimeMillis() - 120_000);
        String expired = Jwts.builder()
                .subject("john")
                .issuedAt(past)
                .expiration(new Date(past.getTime() + 1_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> interceptor.preSend(message(connectWith("Bearer " + expired)), channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectForDeletedUserIsRejected() {
        String token = jwtService.generateToken("ghost", "VIEWER");

        assertThatThrownBy(() -> interceptor.preSend(message(connectWith("Bearer " + token)), channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void subscribeToTopicByAuthenticatedUserIsAllowed() {
        Message<byte[]> message = message(subscribe("/topic/metrics", true));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribeWithoutAuthenticationIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/metrics", false)), channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/anything", "/topic", "/topicx/metrics", "/topic/../app/x", "/user/queue/x"})
    void subscribeOutsideTopicsIsRejected(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe(destination, true)), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeWithoutDestinationIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe(null, true)), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sendFromClientIsAlwaysRejected() {
        StompHeaderAccessor accessor = accessor(StompCommand.SEND);
        accessor.setDestination("/topic/alerts");
        accessor.setUser(new UsernamePasswordAuthenticationToken("john", null, List.of()));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void disconnectAndUnsubscribePassThrough() {
        Message<byte[]> disconnect = message(accessor(StompCommand.DISCONNECT));
        Message<byte[]> unsubscribe = message(accessor(StompCommand.UNSUBSCRIBE));

        assertThat(interceptor.preSend(disconnect, channel)).isSameAs(disconnect);
        assertThat(interceptor.preSend(unsubscribe, channel)).isSameAs(unsubscribe);
    }

    @Test
    void messageWithoutStompHeadersPassesThrough() {
        Message<String> plain = MessageBuilder.withPayload("heartbeat").build();

        assertThat(interceptor.preSend(plain, channel)).isSameAs(plain);
    }
}
