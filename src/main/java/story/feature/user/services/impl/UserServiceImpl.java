package story.feature.user.services.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import story.common.dto.PageResponse;
import story.config.CustomUserDetails;
import story.exception.AppException;
import story.exception.ErrorCode;
import story.feature.post.model.Post;
import story.feature.post.model.PostBookmark;
import story.feature.post.model.PostImage;
import story.feature.post.model.PostHashtag;
import story.feature.post.repository.*;
import story.feature.user.dto.req.UpdateProfileRequest;
import story.feature.user.dto.res.*;
import story.feature.user.mappers.UserMapper;
import story.feature.user.model.User;
import story.feature.user.repository.UserRepository;
import story.feature.user.services.service.IUserService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements IUserService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final PostImageRepository imageRepository;
    private final PostHashtagRepository hashtagRepository;
    private final UserMapper userMapper;

    @Override
    public PublicProfileResponse getPublicProfile(UUID userId) {
        return userMapper.toPublicProfile(publicUser(userId));
    }

    @Override
    public PageResponse<UserPostResponse> getPublicPosts(UUID userId, Pageable pageable) {
        User user = publicUser(userId);
        return mapPosts(postRepository.findByAuthor_IdAndVisibilityAndModerationStatus(user.getId(),
                Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE, validatePageable(pageable, "id")));
    }

    @Override
    public MyProfileResponse getMe() {
        return userMapper.toMyProfile(currentUser());
    }

    @Override
    @Transactional
    public MyProfileResponse updateMe(UpdateProfileRequest request) {
        User user = currentUser();
        if (request.displayName() != null) user.setDisplayName(clean(request.displayName()));
        if (request.bio() != null) user.setBio(clean(request.bio()));
        if (request.avatarKey() != null) user.setAvatarKey(clean(request.avatarKey()));
        if (request.coverKey() != null) user.setCoverKey(clean(request.coverKey()));
        return userMapper.toMyProfile(userRepository.saveAndFlush(user));
    }

    @Override
    public PageResponse<UserPostResponse> getMyPosts(Pageable pageable) {
        return mapPosts(postRepository.findByAuthor_Id(currentUser().getId(), validatePageable(pageable, "id")));
    }

    @Override
    public PageResponse<UserPostResponse> getMyBookmarks(Pageable pageable) {
        return mapPosts(bookmarkRepository.findAccessibleByUserId(currentUser().getId(),
                validatePageable(pageable, "post.id")).map(PostBookmark::getPost));
    }

    private User publicUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            throw new InsufficientAuthenticationException("User is not authenticated");
        }
        return userRepository.findById(principal.getId())
                .filter(user -> user.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new InsufficientAuthenticationException("Account is not active"));
    }

    private Pageable validatePageable(Pageable pageable, String tieBreaker) {
        if (pageable.isUnpaged() || pageable.getPageSize() > 100
                || pageable.getOffset() > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        // Only allow public ordering fields, never arbitrary entity paths.
        for (Sort.Order order : pageable.getSort()) {
            if (!order.getProperty().equals("createdAt") && !order.getProperty().equals(tieBreaker)) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }
        }
        Sort sort = pageable.getSort().isUnsorted()
                ? Sort.by(Sort.Direction.DESC, "createdAt") : pageable.getSort();
        if (sort.getOrderFor(tieBreaker) == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, tieBreaker));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private PageResponse<UserPostResponse> mapPosts(Page<Post> posts) {
        List<UUID> ids = posts.getContent().stream().map(Post::getId).toList();
        Map<UUID, List<PostImage>> images = ids.isEmpty() ? Map.of()
                : imageRepository.findByPost_IdInOrderBySortOrderAsc(ids).stream()
                    .collect(Collectors.groupingBy(image -> image.getPost().getId()));
        Map<UUID, List<PostHashtag>> hashtags = ids.isEmpty() ? Map.of()
                : hashtagRepository.findByPost_IdInOrderByHashtag_NameAscHashtag_IdAsc(ids).stream()
                    .collect(Collectors.groupingBy(link -> link.getPost().getId()));
        return PageResponse.from(posts.map(post -> userMapper.toPost(post,
                images.getOrDefault(post.getId(), List.of()),
                hashtags.getOrDefault(post.getId(), List.of()))));
    }

    private String clean(String value) {
        return value.isBlank() ? null : value.strip();
    }
}
