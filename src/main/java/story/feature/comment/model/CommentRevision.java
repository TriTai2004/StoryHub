package story.feature.comment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import story.feature.user.model.User;

/**
 * Hibernate prevents updates; append-only access and deletion retention still require application/database permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "comment_revisions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_comment_revisions_1", columnNames = {"comment_id", "version_no"})
}, indexes = {
    @Index(name = "idx_comment_revisions_1", columnList = "comment_id"),
    @Index(name = "idx_comment_revisions_2", columnList = "editor_id")
})
@Immutable
public class CommentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "editor_id", nullable = false)
    private User editor;

    @Column(name = "content_snapshot", nullable = false, columnDefinition = "text")
    private String contentSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;
}
