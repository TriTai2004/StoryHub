package story.feature.notification.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import story.feature.comment.model.Comment;
import story.feature.post.model.Post;
import story.feature.report.model.Report;
import story.feature.user.model.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_1", columnList = "recipient_id"),
    @Index(name = "idx_notifications_2", columnList = "actor_id"),
    @Index(name = "idx_notifications_3", columnList = "post_id"),
    @Index(name = "idx_notifications_4", columnList = "comment_id"),
    @Index(name = "idx_notifications_5", columnList = "report_id"),
    @Index(name = "idx_notifications_6", columnList = "recipient_id, is_read, created_at DESC"),
    @Index(name = "idx_notifications_7", columnList = "recipient_id, created_at DESC")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id", nullable = true)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private Type type;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "post_id", nullable = true)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "comment_id", nullable = true)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "report_id", nullable = true)
    private Report report;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = true, columnDefinition = "text")
    private String body;

    @Column(name = "is_read", nullable = false)
    @ColumnDefault("false")
    private Boolean isRead = false;

    @Column(name = "read_at", nullable = true, columnDefinition = "timestamptz")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    public enum Type {
        POST_LIKED, POST_COMMENTED, COMMENT_REPLIED, REPORT_RESOLVED, CONTENT_HIDDEN, CONTENT_RESTORED, ACCOUNT_WARNED, ACCOUNT_SUSPENDED
    }
}
