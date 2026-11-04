package story.feature.user.mappers;

import java.util.List;
import org.springframework.stereotype.Component;
import story.feature.post.model.Post;
import story.feature.post.model.PostImage;
import story.feature.post.model.PostHashtag;
import story.feature.user.dto.res.*;
import story.feature.user.model.User;

@Component
public class UserMapper {
    public PublicProfileResponse toPublicProfile(User user) {
        return new PublicProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getBio(), user.getAvatarKey(), user.getCoverKey(), user.getCreatedAt());
    }

    public MyProfileResponse toMyProfile(User user) {
        return new MyProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getEmail(), user.getBio(), user.getAvatarKey(), user.getCoverKey(),
                user.getEmailVerifiedAt(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public UserPostResponse toPost(Post post, List<PostImage> images, List<PostHashtag> hashtags) {
        return new UserPostResponse(post.getId(), toPublicProfile(post.getAuthor()), post.getContent(),
                post.getVisibility(), post.getModerationStatus(), images.stream()
                    .map(image -> new UserPostResponse.ImageResponse(
                            image.getId(), image.getStorageKey(), image.getSortOrder())).toList(),
                hashtags.stream().map(link -> new UserPostResponse.HashtagResponse(
                        link.getHashtag().getId(), link.getHashtag().getName())).toList(),
                post.getCreatedAt(), post.getUpdatedAt());
    }
}
