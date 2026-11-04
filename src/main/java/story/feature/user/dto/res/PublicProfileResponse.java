package story.feature.user.dto.res;

import java.time.Instant;
import java.util.UUID;

public record PublicProfileResponse(UUID id, String username, String displayName,
        String bio, String avatarKey, String coverKey, Instant createdAt) {
}
