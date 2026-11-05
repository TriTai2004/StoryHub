package story.feature.user.dto.req;

import jakarta.validation.constraints.Size;

/** Null/omitted values keep the current value; an empty string clears a field. */
public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 2000) String bio,
        @Size(max = 2048) String avatarKey,
        @Size(max = 2048) String coverKey) {
}
