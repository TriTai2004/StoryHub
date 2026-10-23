package story.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        @NotNull Boolean httpOnly,
        @NotNull Boolean secure,
        @NotBlank @Pattern(regexp = "Strict|Lax|None") String sameSite,
        @NotBlank @Pattern(regexp = "/.*") String path,
        @NotNull @Positive Long accessTokenMaxAge,
        @NotNull @Positive Long refreshTokenMaxAge) {
}
