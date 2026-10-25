package story.feature.auth.services.impl;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import story.config.properties.SessionProperties;
import story.feature.auth.model.UserSession;
import story.feature.auth.repository.UserSessionRepository;
import story.feature.auth.services.service.IRefreshTokenService;
import story.feature.auth.services.service.ISessionService;
import story.feature.user.model.User;

@Service
public class SessionServiceImpl implements ISessionService {

    private final UserSessionRepository sessionRepository;
    private final IRefreshTokenService refreshTokenService;
    private final SessionProperties properties;
    private final Clock clock;

    @Autowired
    public SessionServiceImpl(UserSessionRepository sessionRepository,
            IRefreshTokenService refreshTokenService, SessionProperties properties) {
        this(sessionRepository, refreshTokenService, properties, Clock.systemUTC());
    }

    public SessionServiceImpl(UserSessionRepository sessionRepository,
            IRefreshTokenService refreshTokenService, SessionProperties properties, Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public CreatedSession createSession(User user, String userAgent, InetAddress ipAddress) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A persisted user is required to create a session");
        }
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new DisabledException("Account is not active");
        }

        Instant now = clock.instant();
        String refreshToken = refreshTokenService.generateToken();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setRefreshTokenHash(refreshTokenService.hashToken(refreshToken));
        session.setUserAgent(userAgent);
        session.setIpAddress(ipAddress);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        session.setAbsoluteExpiresAt(now.plus(properties.absoluteTtl()));

        UserSession saved = sessionRepository.save(session);
        return new CreatedSession(saved.getId(), refreshToken,
            saved.getExpiresAt(), saved.getAbsoluteExpiresAt());
    }
}
