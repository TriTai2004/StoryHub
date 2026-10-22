package story.feature.hashtag.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Case-insensitive uniqueness requires PostgreSQL lower(...) indexes in a migration.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "hashtags",
    check = @CheckConstraint(
        name = "ck_hashtags",
        constraint = """
            name ~ '^[A-Za-z0-9_]+$'
            """
    )
)
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;
}
