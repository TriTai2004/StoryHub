package story.feature.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.DisabledException;

import story.config.properties.SessionProperties;
import story.feature.auth.model.UserSession;
import story.feature.auth.repository.UserSessionRepository;
import story.feature.auth.services.impl.SessionServiceImpl;
import story.feature.auth.services.service.IRefreshTokenService;
import story.feature.user.model.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private final UserSessionRepository repository = mock(UserSessionRepository.class);
    private final IRefreshTokenService tokens = mock(IRefreshTokenService.class);
    private final SessionServiceImpl service = new SessionServiceImpl(repository, tokens,
        new SessionProperties(Duration.ofDays(7), Duration.ofDays(30)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsSessionWithHashAndReturnsRawTokenOnlyInResult() {
        User user = activeUser();
        UUID sessionId = UUID.randomUUID();
        when(tokens.generateToken()).thenReturn("raw-token");
        when(tokens.hashToken("raw-token")).thenReturn("a".repeat(64));
        when(repository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            assertSame(user, session.getUser());
            assertEquals("a".repeat(64), session.getRefreshTokenHash());
            assertEquals("Browser", session.getUserAgent());
            assertNull(session.getIpAddress());
            assertNull(session.getRevokedAt());
            assertNull(session.getRevokedReason());
            assertEquals(NOW, session.getLastUsedAt());
            session.setId(sessionId);
            return session;
        });

        var result = service.createSession(user, "Browser", null);

        assertEquals(sessionId, result.sessionId());
        assertEquals("raw-token", result.refreshToken());
        assertEquals(NOW.plus(Duration.ofDays(7)), result.expiresAt());
        assertEquals(NOW.plus(Duration.ofDays(30)), result.absoluteExpiresAt());
        assertFalse(result.toString().contains("raw-token"));
        verify(repository).save(any(UserSession.class));
    }

    @ParameterizedTest
    @EnumSource(value = User.Status.class, names = {"BANNED", "SUSPENDED"})
    void rejectsInactiveUserBeforeGeneratingToken(User.Status status) {
        User user = activeUser();
        user.setStatus(status);

        assertThrows(DisabledException.class, () -> service.createSession(user, null, null));
        verifyNoInteractions(tokens, repository);
    }

    @Test
    void rejectsMissingUserOrId() {
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.createSession(new User(), null, null));
        verifyNoInteractions(tokens, repository);
    }

    @Test
    void rejectsInvalidSessionDurations() {
        assertThrows(IllegalArgumentException.class,
            () -> new SessionProperties(Duration.ZERO, Duration.ofDays(30)));
        assertThrows(IllegalArgumentException.class,
            () -> new SessionProperties(Duration.ofDays(7), Duration.ofDays(-1)));
        assertThrows(IllegalArgumentException.class,
            () -> new SessionProperties(Duration.ofDays(31), Duration.ofDays(30)));
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(User.Status.ACTIVE);
        return user;
    }
}
