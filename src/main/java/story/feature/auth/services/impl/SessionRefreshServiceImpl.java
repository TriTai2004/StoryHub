package story.feature.auth.services.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import story.config.properties.SessionProperties;
import story.feature.auth.model.UsedRefreshToken;
import story.feature.auth.model.UserSession;
import story.feature.auth.model.UserSession.RevokedReason;
import story.feature.auth.repository.UsedRefreshTokenRepository;
import story.feature.auth.repository.UserSessionRepository;
import story.feature.auth.services.service.IRefreshTokenService;
import story.feature.auth.services.service.ISessionRefreshService;
import story.feature.user.model.User;

@Service
public class SessionRefreshServiceImpl implements ISessionRefreshService {
    private final UserSessionRepository sessions;
    private final UsedRefreshTokenRepository usedTokens;
    private final IRefreshTokenService tokens;
    private final SessionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    @Autowired
    public SessionRefreshServiceImpl(UserSessionRepository sessions, UsedRefreshTokenRepository usedTokens,
            IRefreshTokenService tokens, SessionProperties properties, PlatformTransactionManager manager) {
        this(sessions, usedTokens, tokens, properties, manager, Clock.systemUTC());
    }

    public SessionRefreshServiceImpl(UserSessionRepository sessions, UsedRefreshTokenRepository usedTokens,
            IRefreshTokenService tokens, SessionProperties properties, PlatformTransactionManager manager, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions);
        this.usedTokens = Objects.requireNonNull(usedTokens);
        this.tokens = Objects.requireNonNull(tokens);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        transaction = new TransactionTemplate(Objects.requireNonNull(manager));
        // Revocation must survive even if a caller later rolls back its transaction.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // A waiter must see the previous request's committed used-token record.
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public RefreshedSession refreshSession(String refreshToken) {
        return refreshSession(refreshToken, Function.identity());
    }

    @Override
    public <T> T refreshSession(String refreshToken, Function<RefreshedSession, T> prepareResult) {
        Objects.requireNonNull(prepareResult, "prepareResult");
        if (refreshToken == null || !refreshToken.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }
        String hash = tokens.hashToken(refreshToken);
        T result = transaction.execute(status -> {
            RefreshedSession session = rotate(hash);
            return session == null ? null : Objects.requireNonNull(prepareResult.apply(session), "Prepared result");
        });
        // Return failure only after any security revocation has committed.
        if (result == null) {
            throw invalidToken();
        }
        return result;
    }

    private RefreshedSession rotate(String hash) {
        UserSession session = sessions.findByRefreshTokenHashForUpdate(hash).orElse(null);
        Instant now = clock.instant();
        if (session == null || !hash.equals(session.getRefreshTokenHash())) {
            revokeReusedToken(hash);
            return null;
        }
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)
                || !session.getAbsoluteExpiresAt().isAfter(now)) {
            return null;
        }
        if (session.getUser().getStatus() != User.Status.ACTIVE) {
            revoke(session, now, RevokedReason.ACCOUNT_DISABLED);
            return null;
        }

        String newToken = tokens.generateToken();
        String newHash = tokens.hashToken(newToken);
        UsedRefreshToken used = new UsedRefreshToken();
        used.setTokenHash(hash);
        used.setSession(session);
        used.setExpiresAt(session.getExpiresAt());
        usedTokens.save(used);

        Instant nextExpiry = now.plus(properties.refreshTokenTtl());
        session.setRefreshTokenHash(newHash);
        session.setLastUsedAt(now);
        session.setExpiresAt(nextExpiry.isAfter(session.getAbsoluteExpiresAt())
            ? session.getAbsoluteExpiresAt() : nextExpiry);
        sessions.save(session);

        return new RefreshedSession(session.getUser().getId(), session.getId(), newToken,
            session.getExpiresAt(), session.getAbsoluteExpiresAt());
    }

    private void revokeReusedToken(String hash) {
        UsedRefreshToken used = usedTokens.findById(hash).orElse(null);
        if (used == null) {
            return;
        }
        UserSession session = sessions.findByIdForUpdate(used.getSession().getId()).orElse(null);
        Instant now = clock.instant();
        if (session != null && session.getRevokedAt() == null && used.getExpiresAt().isAfter(now)) {
            revoke(session, now, RevokedReason.REUSE_DETECTED);
        }
    }

    private void revoke(UserSession session, Instant now, RevokedReason reason) {
        session.setRevokedAt(now);
        session.setRevokedReason(reason);
        sessions.save(session);
    }

    private BadCredentialsException invalidToken() {
        return new BadCredentialsException("Invalid or expired refresh token");
    }
}
