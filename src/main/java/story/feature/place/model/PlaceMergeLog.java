package story.feature.place.model;

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
@Table(name = "place_merge_logs", indexes = {
    @Index(name = "idx_place_merge_logs_1", columnList = "target_place_id"),
    @Index(name = "idx_place_merge_logs_2", columnList = "merged_by")
})
@Immutable
public class PlaceMergeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_place_id", nullable = true)
    private UUID sourcePlaceId;

    @Column(name = "source_name_snapshot", nullable = false, columnDefinition = "text")
    private String sourceNameSnapshot;

    @Column(name = "source_address_snapshot", nullable = true, columnDefinition = "text")
    private String sourceAddressSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_place_id", nullable = false)
    private Place targetPlace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merged_by", nullable = false)
    private User mergedBy;

    @Column(name = "reason", nullable = true, columnDefinition = "text")
    private String reason;

    @Column(name = "moved_posts_count", nullable = false)
    @ColumnDefault("0")
    private Integer movedPostsCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;
}
