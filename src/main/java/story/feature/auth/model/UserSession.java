package story.feature.auth.model;

import jakarta.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import story.feature.user.model.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "user_sessions",
    indexes = {
        @Index(name = "idx_user_sessions_1", columnList = "user_id"),
        @Index(name = "idx_user_sessions_2", columnList = "user_id, revoked_at, expires_at")
    },
    check = @CheckConstraint(
        name = "ck_user_sessions",
        constraint = """
            expires_at <= absolute_expires_at
            AND expires_at > created_at
            AND absolute_expires_at > created_at
            AND (revoked_reason IS NULL OR revoked_at IS NOT NULL)
            """
    )
)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String refreshTokenHash;

    @Column(name = "user_agent", nullable = true, columnDefinition = "text")
    private String userAgent;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = true, columnDefinition = "inet")
    private InetAddress ipAddress;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = true, columnDefinition = "timestamptz")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = true, columnDefinition = "timestamptz")
    private Instant lastUsedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", nullable = true, length = 30)
    private RevokedReason revokedReason;

    @Column(name = "absolute_expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant absoluteExpiresAt;

    public enum RevokedReason {
        LOGOUT, LOGOUT_ALL, REUSE_DETECTED, ACCOUNT_DISABLED, SECURITY_EVENT
    }
}
