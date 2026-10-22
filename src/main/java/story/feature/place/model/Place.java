package story.feature.place.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import story.feature.user.model.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "places",
    indexes = {
        @Index(name = "idx_places_1", columnList = "created_by")
    },
    check = @CheckConstraint(
        name = "ck_places",
        constraint = """
            latitude BETWEEN -90 AND 90
            AND longitude BETWEEN -180 AND 180
            AND (
                (latitude IS NULL AND longitude IS NULL)
                OR (latitude IS NOT NULL AND longitude IS NOT NULL)
            )
            """
    )
)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", nullable = true, columnDefinition = "text")
    private String address;

    @Column(name = "latitude", nullable = true, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = true, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    @ColumnDefault("now()")
    private Instant updatedAt;

    public enum Status {
        ACTIVE, HIDDEN
    }
}
