package story.feature.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import story.feature.auth.model.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    // Call within a service transaction to serialize refresh-token rotation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session where session.refreshTokenHash = :tokenHash")
    Optional<UserSession> findByRefreshTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // Use the same session lock for rotation and revocation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session where session.id = :sessionId")
    Optional<UserSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select session from UserSession session
        where session.user.id = :userId and session.revokedAt is null
        order by session.id
        """)
    List<UserSession> findAllUnrevokedByUserIdForUpdate(@Param("userId") UUID userId);
}
