package story.feature.post.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Hibernate prevents updates; append-only access and deletion retention still require application/database permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "post_revision_images", uniqueConstraints = {
    @UniqueConstraint(name = "uk_post_revision_images_1", columnNames = {"revision_id", "sort_order"})
}, indexes = {
    @Index(name = "idx_post_revision_images_1", columnList = "revision_id")
})
@Immutable
public class PostRevisionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private PostRevision revision;

    @Column(name = "storage_key_snapshot", nullable = false, columnDefinition = "text")
    private String storageKeySnapshot;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;
}
