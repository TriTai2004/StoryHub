package story.feature.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.servlet.http.Cookie;
import story.config.CustomUserDetails;
import story.config.CustomUserDetailsService;
import story.feature.auth.services.service.ISessionService;
import story.feature.user.model.User;
import story.security.CookieType;
import story.security.JwtAuthenticationFilter;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final ISessionService sessionService = mock(ISessionService.class);
    private final JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(jwtService, userDetailsService, sessionService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAccessTokenForActiveSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(jwtService.validateToken("access-token", JwtService.TokenType.ACCESS))
            .thenReturn(jwt(userId, sessionId));
        when(userDetailsService.loadUserByUserId(userId)).thenReturn(activeUserDetails(userId));

        filter.doFilter(requestWithAccessToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(sessionService).validateActiveSession(sessionId, userId);
    }

    @Test
    void doesNotAuthenticateAccessTokenForRevokedSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(jwtService.validateToken("access-token", JwtService.TokenType.ACCESS))
            .thenReturn(jwt(userId, sessionId));
        doThrow(new BadCredentialsException("Session is expired or revoked"))
            .when(sessionService).validateActiveSession(sessionId, userId);

        filter.doFilter(requestWithAccessToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    private MockHttpServletRequest requestWithAccessToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieType.ACCESS_TOKEN.getName(), "access-token"));
        return request;
    }

    private Jwt jwt(UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        return new Jwt("access-token", now, now.plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString(), "sid", sessionId.toString()));
    }

    private CustomUserDetails activeUserDetails(UUID userId) {
        return new CustomUserDetails(userId, "user@example.com", "password",
            User.Status.ACTIVE, List.of());
    }
}
