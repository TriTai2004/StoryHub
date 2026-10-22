package story.feature.user.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Case-insensitive uniqueness requires PostgreSQL lower(...) indexes in a migration.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 40)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "email", nullable = true, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = true, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "bio", nullable = true, columnDefinition = "text")
    private String bio;

    @Column(name = "avatar_key", nullable = true, columnDefinition = "text")
    private String avatarKey;

    @Column(name = "cover_key", nullable = true, columnDefinition = "text")
    private String coverKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @ColumnDefault("'USER'")
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private Status status = Status.ACTIVE;

    @Column(name = "email_verified_at", nullable = true, columnDefinition = "timestamptz")
    private Instant emailVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    @ColumnDefault("now()")
    private Instant updatedAt;

    public enum Role {
        USER, ADMIN
    }

    public enum Status {
        ACTIVE, SUSPENDED, BANNED
    }
}
