package story.config.properties;

import java.util.Base64;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @Positive Long accessTokenTtlSeconds,
        @NotNull @Positive Long refreshTokenTtlSeconds,
        @NotNull @PositiveOrZero Long clockSkewSeconds) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret is required");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be valid Base64");
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 decoded bytes");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=REDACTED, issuer=" + issuer + ", audience=" + audience
                + ", accessTokenTtlSeconds=" + accessTokenTtlSeconds
                + ", refreshTokenTtlSeconds=" + refreshTokenTtlSeconds
                + ", clockSkewSeconds=" + clockSkewSeconds + "]";
    }
}
