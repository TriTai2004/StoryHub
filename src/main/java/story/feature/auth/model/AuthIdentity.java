package story.feature.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import story.feature.user.model.User;

/**
 * Uniqueness of (user_id, provider) is intentionally deferred until the provider policy is decided.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auth_identities", uniqueConstraints = {
    @UniqueConstraint(name = "uk_auth_identities_1", columnNames = {"provider", "provider_subject"})
}, indexes = {
    @Index(name = "idx_auth_identities_1", columnList = "user_id")
})
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    public enum Provider {
        LOCAL, GOOGLE
    }
}
