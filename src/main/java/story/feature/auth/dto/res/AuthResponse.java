package story.feature.auth.dto.res;

import java.time.Instant;
import java.util.UUID;

import story.feature.user.model.User;

public record AuthResponse(
    UUID id,
    String displayName,
    String email,
    String bio,
    String avatarKey,
    String coverKey,
    User.Role role,
    User.Status status,
    Instant emailVerifiedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
