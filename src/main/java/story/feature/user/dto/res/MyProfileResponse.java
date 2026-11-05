package story.feature.user.dto.res;

import java.time.Instant;
import java.util.UUID;

public record MyProfileResponse(UUID id, String username, String displayName, String email,
        String bio, String avatarUrl, String coverUrl, Instant emailVerifiedAt,
        Instant createdAt, Instant updatedAt) {
}
