package story.feature.post.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import story.feature.post.model.PostBookmark;
import story.feature.post.model.PostBookmarkId;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, PostBookmarkId> {
    // A bookmark never grants access to another user's private or moderated post.
    @EntityGraph(attributePaths = {"post", "post.author"})
    @Query("""
        select b from PostBookmark b
        where b.user.id = :userId
          and (b.post.author.id = :userId or
               (b.post.visibility = story.feature.post.model.Post$Visibility.PUBLIC
                and b.post.moderationStatus = story.feature.post.model.Post$ModerationStatus.VISIBLE
                and b.post.author.status = story.feature.user.model.User$Status.ACTIVE))
        """)
    Page<PostBookmark> findAccessibleByUserId(@Param("userId") UUID userId, Pageable pageable);
}
