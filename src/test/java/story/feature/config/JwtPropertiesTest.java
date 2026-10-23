package story.feature.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import story.config.properties.JwtProperties;
import story.security.JwtService;

import static org.assertj.core.api.Assertions.*;

class JwtPropertiesTest {
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("test-key-material-only-not-production".getBytes(StandardCharsets.UTF_8));
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Test
    void bindsLocalProfileAndInjectsJwtService() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local", "JWT_SECRET=" + SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JwtProperties.class).hasSingleBean(JwtService.class);
                    assertThat(context.getBean(JwtProperties.class))
                            .isEqualTo(new JwtProperties(SECRET, "storyhub-local", "storyhub-api", 900L, 604800L, 30L));
                    JwtService service = context.getBean(JwtService.class);
                    assertThat(service.isTokenValid(service.generateAccessToken("user-1"), JwtService.TokenType.ACCESS))
                            .isTrue();
                });
    }

    @Test
    void bindsProductionValuesFromEnvironment() {
        runner.withInitializer(context -> {
                    context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                            "jwtTestEnvironment", Map.of(
                                    "JWT_SECRET", SECRET, "JWT_ISSUER", "production-test", "JWT_AUDIENCE", "api-test",
                                    "JWT_ACCESS_TOKEN_TTL_SECONDS", "600", "JWT_REFRESH_TOKEN_TTL_SECONDS", "86400",
                                    "JWT_CLOCK_SKEW_SECONDS", "10")));
                    new ConfigDataApplicationContextInitializer().initialize(context);
                })
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JwtProperties.class).hasSingleBean(JwtService.class);
                    assertThat(context.getBean(JwtProperties.class))
                            .isEqualTo(new JwtProperties(SECRET, "production-test", "api-test", 600L, 86400L, 10L));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.jwt.access-token-ttl-seconds=0", "app.jwt.refresh-token-ttl-seconds=-1",
            "app.jwt.clock-skew-seconds=-1", "app.jwt.issuer=", "app.jwt.audience="
    })
    void rejectsInvalidConfiguration(String invalidProperty) {
        runner.withPropertyValues("app.jwt.secret=" + SECRET, "app.jwt.issuer=test", "app.jwt.audience=test-api",
                        "app.jwt.access-token-ttl-seconds=900", "app.jwt.refresh-token-ttl-seconds=604800",
                        "app.jwt.clock-skew-seconds=30", invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingSecret() {
        runner.withPropertyValues("app.jwt.issuer=test", "app.jwt.audience=test-api",
                        "app.jwt.access-token-ttl-seconds=900", "app.jwt.refresh-token-ttl-seconds=604800",
                        "app.jwt.clock-skew-seconds=30")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid-base64!", "c2hvcnQ="})
    void rejectsMissingInvalidOrShortKeys(String secret) {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new JwtProperties(secret, "test", "api", 900L, 604800L, 30L));
    }

    @Test
    void redactsSecretFromToString() {
        assertThat(new JwtProperties(SECRET, "test", "api", 900L, 604800L, 30L).toString())
                .contains("secret=REDACTED").doesNotContain(SECRET);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtService.class)
    static class BindingConfiguration {}
}
