package story.feature.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import story.feature.auth.model.UsedRefreshToken;

public interface UsedRefreshTokenRepository extends JpaRepository<UsedRefreshToken, String> {
    // The primary key is tokenHash: findById(hash) and existsById(hash)
    // are inherited and can be used to detect refresh-token reuse.
}
