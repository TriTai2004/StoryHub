package story.feature.post.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The ten-image limit and reordering require transaction/service enforcement.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "post_images",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_images_1", columnNames = {"post_id", "sort_order"})
    },
    indexes = {
        @Index(name = "idx_post_images_1", columnList = "post_id")
    },
    check = @CheckConstraint(
        name = "ck_post_images",
        constraint = """
            sort_order BETWEEN 1 AND 10
            """
    )
)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "storage_key", nullable = false, columnDefinition = "text")
    private String storageKey;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;
}
