package story.feature.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import story.config.properties.SessionProperties;
import story.feature.auth.model.UserSession;
import story.feature.auth.model.UsedRefreshToken;
import story.feature.auth.repository.UserSessionRepository;
import story.feature.auth.repository.UsedRefreshTokenRepository;
import story.feature.auth.services.impl.SessionRefreshServiceImpl;
import story.feature.auth.services.service.IRefreshTokenService;
import story.feature.user.model.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionRefreshServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final String TOKEN = "a".repeat(43);
    private static final String NEXT_TOKEN = "b".repeat(43);
    private static final String HASH = "c".repeat(64);
    private static final String NEXT_HASH = "d".repeat(64);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final UsedRefreshTokenRepository usedTokens = mock(UsedRefreshTokenRepository.class);
    private final IRefreshTokenService tokens = mock(IRefreshTokenService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final SessionRefreshServiceImpl service = new SessionRefreshServiceImpl(sessions, usedTokens, tokens,
        new SessionProperties(Duration.ofDays(7), Duration.ofDays(30)), manager,
        Clock.fixed(NOW, ZoneOffset.UTC));
    private UserSession session;

    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(tokens.hashToken(TOKEN)).thenReturn(HASH);
        when(tokens.generateToken()).thenReturn(NEXT_TOKEN);
        when(tokens.hashToken(NEXT_TOKEN)).thenReturn(NEXT_HASH);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(User.Status.ACTIVE);
        session = new UserSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setRefreshTokenHash(HASH);
        session.setExpiresAt(NOW.plus(Duration.ofDays(1)));
        session.setAbsoluteExpiresAt(NOW.plus(Duration.ofDays(30)));
        when(sessions.findByRefreshTokenHashForUpdate(HASH)).thenReturn(Optional.of(session));
    }

    @Test
    void rotatesTokenAndRetainsOldHashUntilItsOriginalExpiry() {
        Instant oldExpiry = session.getExpiresAt();

        var result = service.refreshSession(TOKEN);

        assertEquals(session.getId(), result.sessionId());
        assertEquals(session.getUser().getId(), result.userId());
        assertEquals(NEXT_TOKEN, result.refreshToken());
        assertEquals(NEXT_HASH, session.getRefreshTokenHash());
        assertEquals(NOW, session.getLastUsedAt());
        assertEquals(NOW.plus(Duration.ofDays(7)), result.expiresAt());
        assertFalse(result.toString().contains(NEXT_TOKEN));
        verify(usedTokens).save(argThat(used -> HASH.equals(used.getTokenHash())
            && used.getSession() == session && oldExpiry.equals(used.getExpiresAt())));
        verify(manager).commit(any());
    }

    @Test
    void capsRenewalAtAbsoluteExpiry() {
        session.setAbsoluteExpiresAt(NOW.plus(Duration.ofDays(2)));

        var result = service.refreshSession(TOKEN);

        assertEquals(session.getAbsoluteExpiresAt(), result.expiresAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "absolute-expired", "revoked"})
    void rejectsInvalidSessionWithoutRotation(String condition) {
        switch (condition) {
            case "expired" -> session.setExpiresAt(NOW);
            case "absolute-expired" -> session.setAbsoluteExpiresAt(NOW);
            case "revoked" -> session.setRevokedAt(NOW.minusSeconds(10));
        }

        assertThrows(BadCredentialsException.class, () -> service.refreshSession(TOKEN));

        verify(tokens, never()).generateToken();
        verify(usedTokens, never()).save(any());
    }

    @Test
    void disabledAccountRevocationCommitsBeforeFailure() {
        session.getUser().setStatus(User.Status.SUSPENDED);

        assertThrows(BadCredentialsException.class, () -> service.refreshSession(TOKEN));

        assertEquals(UserSession.RevokedReason.ACCOUNT_DISABLED, session.getRevokedReason());
        assertEquals(NOW, session.getRevokedAt());
        verify(manager).commit(any());
        verify(manager, never()).rollback(any());
        verify(tokens, never()).generateToken();
    }

    @Test
    void reusedTokenRevokesSessionAndCommitsBeforeFailure() {
        prepareReuse();

        assertThrows(BadCredentialsException.class, () -> service.refreshSession(TOKEN));

        assertEquals(UserSession.RevokedReason.REUSE_DETECTED, session.getRevokedReason());
        assertEquals(NOW, session.getRevokedAt());
        verify(sessions).findByIdForUpdate(session.getId());
        verify(manager).commit(any());
        verify(manager, never()).rollback(any());
        verify(tokens, never()).generateToken();
    }

    @Test
    void unknownTokenDoesNotRevokeAnySession() {
        when(sessions.findByRefreshTokenHashForUpdate(HASH)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.refreshSession(TOKEN));

        verify(sessions, never()).save(any());
        verify(tokens, never()).generateToken();
    }

    @Test
    void rechecksHashAfterLockAcquisition() {
        prepareReuse();
        session.setRefreshTokenHash(NEXT_HASH);
        when(sessions.findByRefreshTokenHashForUpdate(HASH)).thenReturn(Optional.of(session));

        assertThrows(BadCredentialsException.class, () -> service.refreshSession(TOKEN));

        assertEquals(UserSession.RevokedReason.REUSE_DETECTED, session.getRevokedReason());
        verify(tokens, never()).generateToken();
    }

    @Test
    void persistenceFailureRollsBackRotation() {
        when(usedTokens.save(any())).thenThrow(new IllegalStateException("DB failure"));

        assertThrows(IllegalStateException.class, () -> service.refreshSession(TOKEN));

        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short", "not/a/token"})
    void rejectsMalformedTokenBeforeDatabaseAccess(String token) {
        assertThrows(BadCredentialsException.class, () -> service.refreshSession(token));
        verifyNoInteractions(sessions, usedTokens);
        verify(manager, never()).getTransaction(any());
    }

    private void prepareReuse() {
        when(sessions.findByRefreshTokenHashForUpdate(HASH)).thenReturn(Optional.empty());
        UsedRefreshToken used = new UsedRefreshToken();
        used.setTokenHash(HASH);
        used.setSession(session);
        used.setExpiresAt(NOW.plusSeconds(3600));
        when(usedTokens.findById(HASH)).thenReturn(Optional.of(used));
        when(sessions.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
    }
}
