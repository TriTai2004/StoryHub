package story.feature.post.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import story.feature.user.model.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "post_bookmarks", indexes = {
    @Index(name = "idx_post_bookmarks_1", columnList = "user_id"),
    @Index(name = "idx_post_bookmarks_2", columnList = "post_id")
})
@IdClass(PostBookmarkId.class)
public class PostBookmark {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;
}
