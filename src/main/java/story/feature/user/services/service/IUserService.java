package story.feature.user.services.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import story.common.dto.PageResponse;
import story.feature.user.dto.req.UpdateProfileRequest;
import story.feature.user.dto.res.*;

public interface IUserService {
    PublicProfileResponse getPublicProfile(UUID userId);
    PageResponse<UserPostResponse> getPublicPosts(UUID userId, Pageable pageable);
    MyProfileResponse getMe();
    MyProfileResponse updateMe(UpdateProfileRequest request);
    PageResponse<UserPostResponse> getMyPosts(Pageable pageable);
    PageResponse<UserPostResponse> getMyBookmarks(Pageable pageable);
}
