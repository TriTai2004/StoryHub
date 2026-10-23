package story.feature.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import story.config.properties.CookieProperties;

import static org.assertj.core.api.Assertions.assertThat;

class CookiePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Test
    void bindsLocalProfileAndInjectsOneBean() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CookieProperties.class);
                    var properties = context.getBean(CookieProperties.class);
                    assertThat(properties).isEqualTo(new CookieProperties(true, false, "Lax", "/", 900L, 604800L));
                    assertThat(context.getBean(CookieConsumer.class).properties()).isSameAs(properties);
                });
    }

    @Test
    void bindsProductionProfileFromEnvironmentVariables() {
        runner.withInitializer(context -> {
                    context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                            "cookieTestEnvironment", Map.of(
                                    "COOKIE_HTTP_ONLY", "true",
                                    "COOKIE_SECURE", "true",
                                    "COOKIE_SAME_SITE", "Strict",
                                    "COOKIE_PATH", "/api",
                                    "COOKIE_ACCESS_TOKEN_MAX_AGE", "1200",
                                    "COOKIE_REFRESH_TOKEN_MAX_AGE", "1209600")));
                    new ConfigDataApplicationContextInitializer().initialize(context);
                })
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CookieProperties.class);
                    assertThat(context.getBean(CookieProperties.class))
                            .isEqualTo(new CookieProperties(true, true, "Strict", "/api", 1200L, 1209600L));
                });
    }

    @Test
    void rejectsMissingProductionVariables() {
        runner.withInitializer(context -> {
                    context.getEnvironment().getPropertySources().remove("systemEnvironment");
                })
                .withPropertyValues(
                        "app.cookie.http-only=${COOKIE_HTTP_ONLY}",
                        "app.cookie.secure=${COOKIE_SECURE}",
                        "app.cookie.same-site=${COOKIE_SAME_SITE}",
                        "app.cookie.path=${COOKIE_PATH}",
                        "app.cookie.access-token-max-age=${COOKIE_ACCESS_TOKEN_MAX_AGE}",
                        "app.cookie.refresh-token-max-age=${COOKIE_REFRESH_TOKEN_MAX_AGE}")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.cookie.access-token-max-age=0",
            "app.cookie.refresh-token-max-age=-1",
            "app.cookie.same-site=Invalid",
            "app.cookie.path=relative"
    })
    void rejectsInvalidCookieConfiguration(String invalidProperty) {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local", invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CookieProperties.class)
    static class BindingConfiguration {
        @Bean
        CookieConsumer cookieConsumer(CookieProperties properties) {
            return new CookieConsumer(properties);
        }
    }

    record CookieConsumer(CookieProperties properties) {}
}
