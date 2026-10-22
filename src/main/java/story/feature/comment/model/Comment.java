package story.feature.comment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import story.feature.post.model.Post;
import story.feature.user.model.User;

/**
 * Parent must belong to the same post and be a root comment; revision ownership requires service/DDL enforcement.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "comments",
    indexes = {
        @Index(name = "idx_comments_1", columnList = "post_id"),
        @Index(name = "idx_comments_2", columnList = "author_id"),
        @Index(name = "idx_comments_3", columnList = "parent_id"),
        @Index(name = "idx_comments_4", columnList = "current_revision_id"),
        @Index(name = "idx_comments_5", columnList = "hidden_by"),
        @Index(name = "idx_comments_6", columnList = "post_id, parent_id, created_at")
    },
    check = @CheckConstraint(
        name = "ck_comments",
        constraint = """
            parent_id IS NULL
            OR parent_id <> id
            """
    )
)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_id", nullable = true)
    private Comment parent;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 10)
    @ColumnDefault("'VISIBLE'")
    private ModerationStatus moderationStatus = ModerationStatus.VISIBLE;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "current_revision_id", nullable = true)
    private CommentRevision currentRevision;

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

    public enum ModerationStatus {
        VISIBLE, HIDDEN
    }
}
