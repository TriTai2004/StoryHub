package story.feature.auth.services.service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import story.feature.user.model.User;
import story.feature.auth.model.UserSession;

public interface ISessionService {

    CreatedSession createSession(User user, String userAgent, InetAddress ipAddress);

    void revokeSession(String refreshToken);

    void validateActiveSession(UUID sessionId, UUID userId);

    void revokeAllSessions(UUID userId);

    void revokeAllSessions(UUID userId, UserSession.RevokedReason reason);

    /** Internal result for issuing cookies; do not return directly as an API response. */
    record CreatedSession(UUID sessionId, String refreshToken, Instant expiresAt, Instant absoluteExpiresAt) {
        @Override
        public String toString() {
            return "CreatedSession[sessionId=" + sessionId + ", refreshToken=REDACTED, expiresAt="
                + expiresAt + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
        }
    }
}
