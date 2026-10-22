package story.feature.report.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import story.feature.comment.model.Comment;
import story.feature.comment.model.CommentRevision;
import story.feature.place.model.Place;
import story.feature.post.model.Post;
import story.feature.post.model.PostRevision;
import story.feature.user.model.User;

/**
 * Revision ownership and atomic evidence capture require a transaction; deletion/retention policy remains undecided.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "reports",
    indexes = {
        @Index(name = "idx_reports_1", columnList = "reporter_id"),
        @Index(name = "idx_reports_2", columnList = "post_id"),
        @Index(name = "idx_reports_3", columnList = "post_revision_id"),
        @Index(name = "idx_reports_4", columnList = "comment_id"),
        @Index(name = "idx_reports_5", columnList = "comment_revision_id"),
        @Index(name = "idx_reports_6", columnList = "place_id"),
        @Index(name = "idx_reports_7", columnList = "reported_user_id"),
        @Index(name = "idx_reports_8", columnList = "assigned_admin_id"),
        @Index(name = "idx_reports_9", columnList = "status, created_at DESC"),
        @Index(name = "idx_reports_10", columnList = "assigned_admin_id, status")
    },
    check = @CheckConstraint(
        name = "ck_reports",
        constraint = """
            (
                target_type = 'POST'
                AND post_id IS NOT NULL
                AND post_revision_id IS NOT NULL
                AND comment_id IS NULL
                AND comment_revision_id IS NULL
                AND place_id IS NULL
                AND reported_user_id IS NULL
            )
            OR (
                target_type = 'COMMENT'
                AND comment_id IS NOT NULL
                AND comment_revision_id IS NOT NULL
                AND post_id IS NULL
                AND post_revision_id IS NULL
                AND place_id IS NULL
                AND reported_user_id IS NULL
            )
            OR (
                target_type = 'PLACE'
                AND place_id IS NOT NULL
                AND post_id IS NULL
                AND post_revision_id IS NULL
                AND comment_id IS NULL
                AND comment_revision_id IS NULL
                AND reported_user_id IS NULL
            )
            OR (
                target_type = 'USER'
                AND reported_user_id IS NOT NULL
                AND post_id IS NULL
                AND post_revision_id IS NULL
                AND comment_id IS NULL
                AND comment_revision_id IS NULL
                AND place_id IS NULL
            )
            """
    )
)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "post_id", nullable = true)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "post_revision_id", nullable = true)
    private PostRevision postRevision;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "comment_id", nullable = true)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "comment_revision_id", nullable = true)
    private CommentRevision commentRevision;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "place_id", nullable = true)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reported_user_id", nullable = true)
    private User reportedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private Reason reason;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidenceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'PENDING'")
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assigned_admin_id", nullable = true)
    private User assignedAdmin;

    @Column(name = "resolution_note", nullable = true, columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolved_at", nullable = true, columnDefinition = "timestamptz")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    @ColumnDefault("now()")
    private Instant updatedAt;

    public enum TargetType {
        POST, COMMENT, PLACE, USER
    }

    public enum Reason {
        SPAM, HARASSMENT, HATE, MISINFORMATION, INAPPROPRIATE, FAKE_PLACE, DUPLICATE_PLACE, OTHER
    }

    public enum Status {
        PENDING, IN_REVIEW, RESOLVED, REJECTED
    }
}
