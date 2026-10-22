package story.feature.post.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import story.feature.hashtag.model.Hashtag;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "post_hashtags", indexes = {
    @Index(name = "idx_post_hashtags_1", columnList = "post_id"),
    @Index(name = "idx_post_hashtags_2", columnList = "hashtag_id")
})
@IdClass(PostHashtagId.class)
public class PostHashtag {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;
}
