package story.feature.post.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import story.feature.post.model.PostImage;

public interface PostImageRepository extends JpaRepository<PostImage, UUID> {
    List<PostImage> findByPost_IdInOrderBySortOrderAsc(Collection<UUID> postIds);
}
