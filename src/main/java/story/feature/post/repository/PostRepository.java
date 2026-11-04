package story.feature.post.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import story.feature.post.model.Post;

public interface PostRepository extends JpaRepository<Post, UUID> {
    @EntityGraph(attributePaths = "author")
    Page<Post> findByAuthor_Id(UUID authorId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<Post> findByAuthor_IdAndVisibilityAndModerationStatus(UUID authorId,
            Post.Visibility visibility, Post.ModerationStatus moderationStatus, Pageable pageable);
}
