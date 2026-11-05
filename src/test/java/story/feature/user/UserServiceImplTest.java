package story.feature.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import story.config.CustomUserDetails;
import story.exception.AppException;
import story.exception.ErrorCode;
import story.feature.post.model.Post;
import story.feature.post.model.PostBookmark;
import story.feature.post.repository.*;
import story.feature.upload.services.service.MediaStorageService;
import story.feature.user.dto.req.UpdateProfileRequest;
import story.feature.user.mappers.UserMapper;
import story.feature.user.model.User;
import story.feature.user.repository.UserRepository;
import story.feature.user.services.impl.UserServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {
    UserRepository users = mock(UserRepository.class);
    PostRepository posts = mock(PostRepository.class);
    PostBookmarkRepository bookmarks = mock(PostBookmarkRepository.class);
    PostImageRepository images = mock(PostImageRepository.class);
    PostHashtagRepository hashtags = mock(PostHashtagRepository.class);
    MediaStorageService mediaStorageService = mock(MediaStorageService.class);
    UserServiceImpl service = new UserServiceImpl(users, posts, bookmarks, images, hashtags, new UserMapper(), mediaStorageService);
    User current;

    @BeforeEach
    void setup() {
        current = new User();
        current.setId(UUID.randomUUID());
        current.setUsername("alice");
        current.setEmail("alice@example.com");
        current.setBio("Old bio");
        current.setAvatarKey("old-avatar");
        current.setAvatarUrl("https://example.com/avatar.jpg");
        current.setCoverUrl("https://example.com/cover.jpg");
        current.setPasswordHash("hash");
        var principal = new CustomUserDetails(current.getId(), current.getEmail(), "hash", User.Status.ACTIVE, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        lenient().when(users.findById(current.getId())).thenReturn(Optional.of(current));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void coverDeletesOldImageOnlyAfterCommitAndReturnsUrl() {
        current.setCoverKey("old-cover");
        var file = new org.springframework.mock.web.MockMultipartFile("file", new byte[] {1});
        when(mediaStorageService.upload(file)).thenReturn(
                new story.feature.upload.services.dto.res.UploadResult("new-cover", "https://example.com/new.jpg", 1L, 1, 1));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service.updateCover(file);
            assertEquals("https://example.com/new.jpg", response.coverUrl());
            assertEquals("new-cover", current.getCoverKey());
            assertEquals("old-avatar", current.getAvatarKey());
            verify(mediaStorageService, never()).delete(anyString());
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(mediaStorageService).delete("old-cover");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void coverRollbackDeletesNewImageAndKeepsOldImage() {
        current.setCoverKey("old-cover");
        var file = new org.springframework.mock.web.MockMultipartFile("file", new byte[] {1});
        when(mediaStorageService.upload(file)).thenReturn(
                new story.feature.upload.services.dto.res.UploadResult("new-cover", "https://example.com/new.jpg", 1L, 1, 1));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateCover(file);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(mediaStorageService).delete("new-cover");
            verify(mediaStorageService, never()).delete("old-cover");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void patchUpdatesOnlyProvidedFieldsOfAuthenticatedUser() {
        when(users.saveAndFlush(current)).thenReturn(current);
        var result = service.updateMe(new UpdateProfileRequest(" Alice ", ""));
        assertEquals("Alice", result.displayName());
        assertNull(result.bio());
        assertEquals("https://example.com/avatar.jpg", result.avatarUrl());
        assertEquals("https://example.com/cover.jpg", result.coverUrl());
        assertEquals(current.getId(), result.id());
        assertEquals("alice@example.com", result.email());
        assertEquals("hash", current.getPasswordHash());
        assertEquals(User.Role.USER, current.getRole());
        verify(users).saveAndFlush(current);
    }

    @Test
    void missingOrInactiveUsersAreNotPublic() {
        UUID missingId = UUID.randomUUID();
        when(users.findById(missingId)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(AppException.class, () -> service.getPublicProfile(missingId)).getErrorCode());
        for (User.Status status : List.of(User.Status.BANNED, User.Status.SUSPENDED)) {
            current.setStatus(status);
            when(users.findById(current.getId())).thenReturn(Optional.of(current));
            assertThrows(AppException.class, () -> service.getPublicProfile(current.getId()));
            assertThrows(AppException.class, () -> service.getPublicPosts(current.getId(), PageRequest.of(0, 20)));
        }
        verifyNoInteractions(posts);
    }

    @Test
    void privateOperationsRejectInactiveAccount() {
        current.setStatus(User.Status.BANNED);
        assertThrows(InsufficientAuthenticationException.class, service::getMe);
        assertThrows(InsufficientAuthenticationException.class, () -> service.getMyPosts(PageRequest.of(0, 20)));
        assertThrows(InsufficientAuthenticationException.class, () -> service.getMyBookmarks(PageRequest.of(0, 20)));
        assertThrows(InsufficientAuthenticationException.class,
                () -> service.updateMe(new UpdateProfileRequest("Name", null)));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(posts, bookmarks);
    }

    @Test
    void privateOperationsRejectMissingPrincipal() {
        SecurityContextHolder.clearContext();
        assertThrows(InsufficientAuthenticationException.class, service::getMe);
    }

    @Test
    void publicPostsAlwaysUseVisibilityAndModerationFilters() {
        when(users.findById(current.getId())).thenReturn(Optional.of(current));
        when(posts.findByAuthor_IdAndVisibilityAndModerationStatus(eq(current.getId()),
                eq(Post.Visibility.PUBLIC), eq(Post.ModerationStatus.VISIBLE), any()))
            .thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.getPublicPosts(current.getId(), PageRequest.of(0, 20)).content().isEmpty());
        verify(posts).findByAuthor_IdAndVisibilityAndModerationStatus(eq(current.getId()),
                eq(Post.Visibility.PUBLIC), eq(Post.ModerationStatus.VISIBLE), any());
        verifyNoInteractions(images);
    }

    @Test
    void ownPostsIncludePrivateAndHiddenAndLoadImagesInBatch() {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setAuthor(current);
        post.setVisibility(Post.Visibility.PRIVATE);
        post.setModerationStatus(Post.ModerationStatus.HIDDEN);
        when(posts.findByAuthor_Id(eq(current.getId()), any())).thenReturn(new PageImpl<>(List.of(post)));
        when(images.findByPost_IdInOrderBySortOrderAsc(List.of(post.getId()))).thenReturn(List.of());
        var response = service.getMyPosts(PageRequest.of(0, 20));
        assertEquals(Post.Visibility.PRIVATE, response.content().getFirst().visibility());
        assertEquals(Post.ModerationStatus.HIDDEN, response.content().getFirst().moderationStatus());
        verify(images).findByPost_IdInOrderBySortOrderAsc(List.of(post.getId()));
    }

    @Test
    void bookmarksUseAuthenticatedUserAndStableBookmarkOrdering() {
        when(bookmarks.findAccessibleByUserId(eq(current.getId()), any())).thenReturn(new PageImpl<PostBookmark>(List.of()));
        service.getMyBookmarks(PageRequest.of(2, 10));
        verify(bookmarks).findAccessibleByUserId(eq(current.getId()), argThat((Pageable page) ->
                page.getPageNumber() == 2 && page.getPageSize() == 10
                && page.getSort().getOrderFor("createdAt").isDescending()
                && page.getSort().getOrderFor("post.id").isDescending()));
    }

    @Test
    void mapsHashtagsToTheirPostAndReturnsEmptyListForUntaggedPost() {
        Post tagged = new Post();
        tagged.setId(UUID.randomUUID());
        tagged.setAuthor(current);
        Post untagged = new Post();
        untagged.setId(UUID.randomUUID());
        untagged.setAuthor(current);
        var tag = new story.feature.hashtag.model.Hashtag();
        tag.setId(UUID.randomUUID());
        tag.setName("travel");
        var link = new story.feature.post.model.PostHashtag();
        link.setPost(tagged);
        link.setHashtag(tag);
        var ids = List.of(tagged.getId(), untagged.getId());
        when(posts.findByAuthor_Id(eq(current.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(tagged, untagged)));
        when(hashtags.findByPost_IdInOrderByHashtag_NameAscHashtag_IdAsc(ids)).thenReturn(List.of(link));
        var result = service.getMyPosts(PageRequest.of(0, 20));
        assertEquals(tag.getId(), result.content().getFirst().hashtags().getFirst().id());
        assertEquals("travel", result.content().getFirst().hashtags().getFirst().name());
        assertTrue(result.content().get(1).hashtags().isEmpty());
        verify(hashtags).findByPost_IdInOrderByHashtag_NameAscHashtag_IdAsc(ids);
    }

    @Test
    void rejectsExcessiveOffset() {
        assertThrows(AppException.class, () -> service.getMyPosts(PageRequest.of(Integer.MAX_VALUE, 100)));
        verifyNoInteractions(posts);
    }

    @Test
    void rejectsSortingByPrivateEntityFields() {
        var pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("author.passwordHash"));
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.getMyPosts(pageable)).getErrorCode());
        assertThrows(AppException.class, () -> service.getMyBookmarks(pageable));
        verifyNoInteractions(posts, bookmarks);
    }

    @Test
    void preservesRequestedSortAndAddsStableTieBreaker() {
        when(posts.findByAuthor_Id(eq(current.getId()), any())).thenReturn(new PageImpl<>(List.of()));
        service.getMyPosts(PageRequest.of(1, 10, org.springframework.data.domain.Sort.by("createdAt").ascending()));
        verify(posts).findByAuthor_Id(eq(current.getId()), argThat(page ->
                page.getPageNumber() == 1 && page.getPageSize() == 10
                && page.getSort().getOrderFor("createdAt").isAscending()
                && page.getSort().getOrderFor("id").isDescending()));
    }
}
