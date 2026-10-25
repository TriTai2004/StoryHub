package story.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
    @DefaultValue("7d") Duration refreshTokenTtl,
    @DefaultValue("30d") Duration absoluteTtl
) {
    public SessionProperties {
        if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        if (absoluteTtl == null || absoluteTtl.isZero() || absoluteTtl.isNegative()) {
            throw new IllegalArgumentException("Absolute session TTL must be positive");
        }
        if (refreshTokenTtl.compareTo(absoluteTtl) > 0) {
            throw new IllegalArgumentException("Refresh token TTL must not exceed absolute session TTL");
        }
    }
}
