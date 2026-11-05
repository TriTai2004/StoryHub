package story.feature.post.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import story.feature.post.model.PostHashtag;
import story.feature.post.model.PostHashtagId;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtagId> {
    @EntityGraph(attributePaths = "hashtag")
    List<PostHashtag> findByPost_IdInOrderByHashtag_NameAscHashtag_IdAsc(Collection<UUID> postIds);
}
