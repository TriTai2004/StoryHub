package story.feature.auth.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import story.feature.auth.model.AuthIdentity;

public interface UserIdentityRepository extends JpaRepository<AuthIdentity, UUID>{
    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthIdentity.Provider provider, String providerSubject);
    
}
