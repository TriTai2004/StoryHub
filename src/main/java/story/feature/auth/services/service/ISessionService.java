package story.feature.auth.services.service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import story.feature.user.model.User;

public interface ISessionService {

    CreatedSession createSession(User user, String userAgent, InetAddress ipAddress);

    /** Internal result for issuing cookies; do not return directly as an API response. */
    record CreatedSession(UUID sessionId, String refreshToken, Instant expiresAt, Instant absoluteExpiresAt) {
        @Override
        public String toString() {
            return "CreatedSession[sessionId=" + sessionId + ", refreshToken=REDACTED, expiresAt="
                + expiresAt + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
        }
    }
}
