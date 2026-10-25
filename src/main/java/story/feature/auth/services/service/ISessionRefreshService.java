package story.feature.auth.services.service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

public interface ISessionRefreshService {

    RefreshedSession refreshSession(String refreshToken);

    /** Prepare the response inside the rotation transaction; write HTTP headers only after this returns. */
    <T> T refreshSession(String refreshToken, Function<RefreshedSession, T> prepareResult);

    /** Internal result for issuing new tokens; do not expose directly as an API response. */
    record RefreshedSession(UUID userId, UUID sessionId, String refreshToken,
            Instant expiresAt, Instant absoluteExpiresAt) {
        @Override
        public String toString() {
            return "RefreshedSession[userId=" + userId + ", sessionId=" + sessionId
                + ", refreshToken=REDACTED, expiresAt=" + expiresAt
                + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
        }
    }
}
