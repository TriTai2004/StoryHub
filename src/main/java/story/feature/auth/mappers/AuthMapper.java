package story.feature.auth.mappers;

import java.util.Objects;

import org.springframework.stereotype.Component;

import story.feature.auth.dto.res.AuthResponse;
import story.feature.user.model.User;

@Component
public class AuthMapper {

    public AuthResponse toAuthResponse(User user) {
        Objects.requireNonNull(user, "User must not be null");

        return new AuthResponse(
            user.getId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getBio(),
            user.getAvatarKey(),
            user.getCoverKey(),
            user.getRole(),
            user.getStatus(),
            user.getEmailVerifiedAt(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
} 

