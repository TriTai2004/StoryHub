package story.feature.moderation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import story.feature.report.model.Report;
import story.feature.user.model.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_sanctions", indexes = {
    @Index(name = "idx_user_sanctions_1", columnList = "user_id"),
    @Index(name = "idx_user_sanctions_2", columnList = "issued_by"),
    @Index(name = "idx_user_sanctions_3", columnList = "related_report_id"),
    @Index(name = "idx_user_sanctions_4", columnList = "revoked_by")
})
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false)
    private User issuedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_report_id", nullable = true)
    private Report relatedReport;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "starts_at", nullable = false, columnDefinition = "timestamptz")
    @ColumnDefault("now()")
    private Instant startsAt = Instant.now();

    @Column(name = "ends_at", nullable = true, columnDefinition = "timestamptz")
    private Instant endsAt;

    @Column(name = "revoked_at", nullable = true, columnDefinition = "timestamptz")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "revoked_by", nullable = true)
    private User revokedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    public enum Type {
        WARNING, SUSPENSION, BAN
    }
}
