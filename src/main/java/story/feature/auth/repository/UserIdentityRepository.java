package story.feature.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import story.feature.auth.model.AuthIdentity;

public interface UserIdentityRepository extends JpaRepository<AuthIdentity, UUID>{
    
}
