package story.feature.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.BadCredentialsException;
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

    @Test
    void acceptsActiveSessionOwnedByUser() {
        User user = activeUser();
        UserSession session = activeSession(user);
        when(repository.findById(session.getId())).thenReturn(java.util.Optional.of(session));

        assertDoesNotThrow(() -> service.validateActiveSession(session.getId(), user.getId()));
    }

    @Test
    void rejectsMissingSession() {
        UUID sessionId = UUID.randomUUID();
        when(repository.findById(sessionId)).thenReturn(java.util.Optional.empty());

        assertThrows(BadCredentialsException.class,
            () -> service.validateActiveSession(sessionId, UUID.randomUUID()));
    }

    @ParameterizedTest
    @MethodSource("invalidSessions")
    void rejectsSessionThatIsNotActive(UserSession session) {
        when(repository.findById(session.getId())).thenReturn(java.util.Optional.of(session));

        assertThrows(BadCredentialsException.class,
            () -> service.validateActiveSession(session.getId(), session.getUser().getId()));
    }

    @Test
    void revokesAllActiveSessionsForUser() {
        User user = activeUser();
        UserSession first = activeSession(user);
        UserSession second = activeSession(user);
        when(repository.findAllUnrevokedByUserIdForUpdate(user.getId()))
            .thenReturn(List.of(first, second));

        service.revokeAllSessions(user.getId());

        for (UserSession session : List.of(first, second)) {
            assertEquals(NOW, session.getRevokedAt());
            assertEquals(UserSession.RevokedReason.LOGOUT_ALL, session.getRevokedReason());
        }
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void rejectsMissingUserIdWhenRevokingAllSessions() {
        assertThrows(IllegalArgumentException.class, () -> service.revokeAllSessions(null));
        verifyNoInteractions(repository);
    }

    @Test
    void revokesSessionsWithProvidedSecurityReason() {
        User user = activeUser();
        UserSession session = activeSession(user);
        when(repository.findAllUnrevokedByUserIdForUpdate(user.getId()))
            .thenReturn(List.of(session));

        service.revokeAllSessions(user.getId(), UserSession.RevokedReason.SECURITY_EVENT);

        assertEquals(NOW, session.getRevokedAt());
        assertEquals(UserSession.RevokedReason.SECURITY_EVENT, session.getRevokedReason());
        verify(repository).saveAll(List.of(session));
    }

    private static Stream<UserSession> invalidSessions() {
        User revokedUser = userWithId();
        UserSession revoked = sessionFor(revokedUser);
        revoked.setRevokedAt(NOW.minusSeconds(1));

        User expiredUser = userWithId();
        UserSession expired = sessionFor(expiredUser);
        expired.setExpiresAt(NOW);

        User absoluteExpiredUser = userWithId();
        UserSession absoluteExpired = sessionFor(absoluteExpiredUser);
        absoluteExpired.setAbsoluteExpiresAt(NOW);

        return Stream.of(revoked, expired, absoluteExpired);
    }

    private UserSession activeSession(User user) {
        return sessionFor(user);
    }

    private static UserSession sessionFor(User user) {
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setExpiresAt(NOW.plusSeconds(60));
        session.setAbsoluteExpiresAt(NOW.plusSeconds(120));
        return session;
    }

    private static User userWithId() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(User.Status.ACTIVE);
        return user;
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(User.Status.ACTIVE);
        return user;
    }
}
