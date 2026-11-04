package story.feature.user;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import story.feature.post.model.Post;
import story.feature.post.model.PostBookmark;
import story.feature.post.repository.PostRepository;
import story.feature.post.repository.PostBookmarkRepository;
import story.feature.user.model.User;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against the configured PostgreSQL schema; fixtures are rolled back after each test. */
@EnabledIfEnvironmentVariable(named = "STORYHUB_DB_TESTS", matches = "true")
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPostRepositoryTest {
    @Autowired EntityManager em;
    @Autowired PostRepository posts;
    @Autowired PostBookmarkRepository bookmarks;
    @Autowired story.feature.post.repository.PostHashtagRepository hashtags;

    @Test
    void hashtagsAreFetchedOnlyForRequestedPostsWithNamesLoaded() {
        User author = user();
        Post included = post(author, Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE);
        Post excluded = post(author, Post.Visibility.PRIVATE, Post.ModerationStatus.VISIBLE);
        var tag = new story.feature.hashtag.model.Hashtag();
        tag.setName("test_" + UUID.randomUUID().toString().replace("-", ""));
        em.persist(tag);
        for (Post post : List.of(included, excluded)) {
            var link = new story.feature.post.model.PostHashtag();
            link.setPost(post);
            link.setHashtag(tag);
            em.persist(link);
        }
        em.flush();
        em.clear();
        var result = hashtags.findByPost_IdInOrderByHashtag_NameAscHashtag_IdAsc(List.of(included.getId()));
        em.clear();
        assertEquals(1, result.size());
        assertEquals(included.getId(), result.getFirst().getPost().getId());
        assertEquals(tag.getName(), result.getFirst().getHashtag().getName());
    }

    @Test
    void bookmarksEnforceCurrentVisibilityOwnershipAndAccountStatusBeforePagination() {
        User viewer = user();
        User other = user();
        User banned = user();
        banned.setStatus(User.Status.BANNED);
        Post publicPost = post(other, Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE);
        Post privatePost = post(other, Post.Visibility.PRIVATE, Post.ModerationStatus.VISIBLE);
        Post hiddenPost = post(other, Post.Visibility.PUBLIC, Post.ModerationStatus.HIDDEN);
        Post bannedPost = post(banned, Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE);
        Post ownPost = post(viewer, Post.Visibility.PRIVATE, Post.ModerationStatus.HIDDEN);
        for (Post post : List.of(publicPost, privatePost, hiddenPost, bannedPost, ownPost)) bookmark(viewer, post);
        // Another user's bookmark must not appear in the viewer's collection.
        bookmark(other, post(other, Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE));
        em.flush();
        em.clear();
        var sort = Sort.by(Sort.Direction.DESC, "createdAt", "post.id");
        var first = bookmarks.findAccessibleByUserId(viewer.getId(), PageRequest.of(0, 1, sort));
        var second = bookmarks.findAccessibleByUserId(viewer.getId(), PageRequest.of(1, 1, sort));
        assertEquals(2, first.getTotalElements());
        assertEquals(2, second.getTotalElements());
        var ids = List.of(first.getContent().getFirst().getPost().getId(), second.getContent().getFirst().getPost().getId());
        assertTrue(ids.contains(publicPost.getId()));
        assertTrue(ids.contains(ownPost.getId()));
    }

    @Test
    void publicPostsExcludePrivateAndHiddenButOwnerQueryIncludesThem() {
        User author = user();
        Post visible = post(author, Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE);
        post(author, Post.Visibility.PRIVATE, Post.ModerationStatus.VISIBLE);
        post(author, Post.Visibility.PUBLIC, Post.ModerationStatus.HIDDEN);
        post(user(), Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE);
        em.flush();
        em.clear();
        var page = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = posts.findByAuthor_IdAndVisibilityAndModerationStatus(author.getId(),
                Post.Visibility.PUBLIC, Post.ModerationStatus.VISIBLE, page);
        assertEquals(List.of(visible.getId()), result.map(Post::getId).getContent());
        assertEquals(3, posts.findByAuthor_Id(author.getId(), page).getTotalElements());
    }

    private User user() {
        User user = new User();
        user.setUsername("test_" + UUID.randomUUID().toString().replace("-", ""));
        user.setEmail(user.getUsername() + "@example.com");
        em.persist(user);
        return user;
    }

    private Post post(User author, Post.Visibility visibility, Post.ModerationStatus status) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent("Repository test");
        post.setVisibility(visibility);
        post.setModerationStatus(status);
        em.persist(post);
        return post;
    }

    private void bookmark(User user, Post post) {
        PostBookmark bookmark = new PostBookmark();
        bookmark.setUser(user);
        bookmark.setPost(post);
        em.persist(bookmark);
    }
}
