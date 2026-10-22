package story.feature.post.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import story.feature.user.model.User;

/**
 * Hibernate prevents updates; append-only access and deletion retention still require application/database permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "post_revisions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_post_revisions_1", columnNames = {"post_id", "version_no"})
}, indexes = {
    @Index(name = "idx_post_revisions_1", columnList = "post_id"),
    @Index(name = "idx_post_revisions_2", columnList = "editor_id")
})
@Immutable
public class PostRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "editor_id", nullable = false)
    private User editor;

    @Column(name = "content_snapshot", nullable = false, columnDefinition = "text")
    private String contentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_snapshot", nullable = false, length = 10)
    private VisibilitySnapshot visibilitySnapshot;

    @Column(name = "place_id_snapshot", nullable = true)
    private UUID placeIdSnapshot;

    @Column(name = "place_name_snapshot", nullable = true, columnDefinition = "text")
    private String placeNameSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hashtags_snapshot", nullable = false, columnDefinition = "jsonb")
    @ColumnDefault("'[]'::jsonb")
    private List<String> hashtagsSnapshot = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    public enum VisibilitySnapshot {
        PUBLIC, PRIVATE
    }
}
