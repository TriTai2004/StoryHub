package story.feature.user.dto.res;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import story.feature.post.model.Post;

public record UserPostResponse(UUID id, PublicProfileResponse author, String content,
        Post.Visibility visibility, Post.ModerationStatus moderationStatus,
        List<ImageResponse> images, List<HashtagResponse> hashtags, Instant createdAt, Instant updatedAt) {
    public record ImageResponse(UUID id, String storageKey, Short sortOrder) {}
    public record HashtagResponse(UUID id, String name) {}
}
