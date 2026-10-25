package story.feature.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import story.config.properties.CookieProperties;
import story.config.properties.SessionProperties;
import story.feature.auth.mappers.AuthMapper;
import story.feature.auth.model.UserSession;
import story.feature.auth.repository.UserSessionRepository;
import story.feature.auth.repository.UsedRefreshTokenRepository;
import story.feature.auth.services.impl.AuthServiceImpl;
import story.feature.auth.services.impl.SessionRefreshServiceImpl;
import story.feature.auth.services.service.IRefreshTokenService;
import story.feature.auth.services.service.ISessionService;
import story.feature.user.model.User;
import story.feature.user.repository.UserRepository;
import story.security.CookieType;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthRefreshTest {
    private static final String OLD_TOKEN = "a".repeat(43);
    private static final String NEW_TOKEN = "b".repeat(43);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final UsedRefreshTokenRepository usedTokens = mock(UsedRefreshTokenRepository.class);
    private final IRefreshTokenService tokens = mock(IRefreshTokenService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final JwtService jwt = mock(JwtService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final UserSession session = new UserSession();
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        var rotation = new SessionRefreshServiceImpl(sessions, usedTokens, tokens,
            new SessionProperties(Duration.ofDays(7), Duration.ofDays(30)), manager);
        service = new AuthServiceImpl(new AuthMapper(), jwt, mock(AuthenticationManager.class),
            new CookieProperties(true, true, "Lax", "/", 900L, 604800L),
            mock(UserRepository.class), mock(ISessionService.class), manager, rotation);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        User user = new User();
        user.setId(UUID.randomUUID());
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setRefreshTokenHash("old-hash");
        session.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        session.setAbsoluteExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        when(tokens.hashToken(OLD_TOKEN)).thenReturn("old-hash");
        when(tokens.generateToken()).thenReturn(NEW_TOKEN);
        when(tokens.hashToken(NEW_TOKEN)).thenReturn("new-hash");
        when(sessions.findByRefreshTokenHashForUpdate("old-hash")).thenReturn(Optional.of(session));
        when(jwt.generateAccessToken(any(), any())).thenReturn("new-access");
        request.setCookies(new Cookie(CookieType.REFRESH_TOKEN.getName(), OLD_TOKEN));
    }

    @Test
    void refreshWithoutAccessCookieRotatesAndWritesCookiesAfterCommit() {
        doAnswer(invocation -> {
            assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
            return null;
        }).when(manager).commit(any());

        service.refresh(request, response);

        verify(jwt).generateAccessToken(session.getUser().getId().toString(),
            Map.of("sid", session.getId().toString()));
        verify(manager).commit(any());
        assertEquals("new-hash", session.getRefreshTokenHash());
        var cookies = response.getHeaders(HttpHeaders.SET_COOKIE).stream().toList();
        assertEquals(2, cookies.size());
        assertTrue(cookies.get(0).contains(CookieType.ACCESS_TOKEN.getName() + "=new-access"));
        assertTrue(cookies.get(1).contains(CookieType.REFRESH_TOKEN.getName() + "=" + NEW_TOKEN));
        assertTrue(cookies.get(1).contains("HttpOnly"));
        assertTrue(cookies.get(1).contains("Secure"));
        assertTrue(cookies.get(1).contains("SameSite=Lax"));
        assertTrue(cookies.get(1).contains("Path=/api/auth"));
    }

    @Test
    void missingRefreshCookieFailsBeforeDatabaseAccess() {
        request.setCookies(new Cookie("unrelated", "value"));

        assertThrows(BadCredentialsException.class, () -> service.refresh(request, response));

        verifyNoInteractions(sessions, usedTokens, jwt);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void duplicateRefreshCookiesAreRejected() {
        request.setCookies(new Cookie(CookieType.REFRESH_TOKEN.getName(), OLD_TOKEN),
            new Cookie(CookieType.REFRESH_TOKEN.getName(), NEW_TOKEN));

        assertThrows(BadCredentialsException.class, () -> service.refresh(request, response));

        verifyNoInteractions(sessions, usedTokens, jwt);
    }

    @Test
    void expiredSessionDoesNotIssueTokensOrCookies() {
        session.setExpiresAt(Instant.now().minusSeconds(1));

        assertThrows(BadCredentialsException.class, () -> service.refresh(request, response));

        verifyNoInteractions(jwt);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void jwtFailureRollsBackRotationAndDoesNotWriteCookies() {
        when(jwt.generateAccessToken(any(), any())).thenThrow(new IllegalStateException("Signing failed"));

        assertThrows(IllegalStateException.class, () -> service.refresh(request, response));

        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void commitFailureDoesNotWriteCookies() {
        doThrow(new IllegalStateException("Commit failed")).when(manager).commit(any());

        assertThrows(IllegalStateException.class, () -> service.refresh(request, response));

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }
}
