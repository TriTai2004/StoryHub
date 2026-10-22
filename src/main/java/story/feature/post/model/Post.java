package story.feature.post.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import story.feature.place.model.Place;
import story.feature.user.model.User;

/**
 * Current revision ownership, content/image presence and the partial public-feed index require service/DDL enforcement.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_posts_1", columnList = "author_id"),
    @Index(name = "idx_posts_2", columnList = "place_id"),
    @Index(name = "idx_posts_3", columnList = "current_revision_id"),
    @Index(name = "idx_posts_4", columnList = "hidden_by"),
    @Index(name = "idx_posts_5", columnList = "author_id, created_at DESC"),
    @Index(name = "idx_posts_6", columnList = "place_id, created_at DESC")
})
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "place_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Place place;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    @ColumnDefault("'PUBLIC'")
    private Visibility visibility = Visibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 10)
    @ColumnDefault("'VISIBLE'")
    private ModerationStatus moderationStatus = ModerationStatus.VISIBLE;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "current_revision_id", nullable = true)
    private PostRevision currentRevision;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "hidden_by", nullable = true)
    private User hiddenBy;

    @Column(name = "hidden_at", nullable = true, columnDefinition = "timestamptz")
    private Instant hiddenAt;

    @Column(name = "hidden_reason", nullable = true, columnDefinition = "text")
    private String hiddenReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    @ColumnDefault("now()")
    private Instant updatedAt;

    public enum Visibility {
        PUBLIC, PRIVATE
    }

    public enum ModerationStatus {
        VISIBLE, HIDDEN
    }
}
