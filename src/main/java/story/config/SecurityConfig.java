package story.config;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import story.security.JwtAuthenticationFilter;
import story.security.JwtService;
import story.feature.auth.services.service.ISessionService;
import story.security.GoogleLoginSuccessHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

	@Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
			ISessionService sessionService) {

        return new JwtAuthenticationFilter(jwtService, userDetailsService, sessionService);
    }

	@Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);

        registration.setEnabled(false);
        return registration;
    }

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
            GoogleLoginSuccessHandler googleLoginSuccessHandler) throws Exception {
		
		Set<String> csrfIgnoredPahts = Set.of(
			"/api/v1/auth/register",
			"/api/v1/auth/login"
		);
		
		http
			.cors(Customizer.withDefaults())

			.csrf(csrf -> csrf
					.csrfTokenRepository(
							CookieCsrfTokenRepository.withHttpOnlyFalse())
					.spa()
					.ignoringRequestMatchers(req -> "POST".equals(req.getMethod())
						&& csrfIgnoredPahts.contains(req.getServletPath())))

			.sessionManagement(session -> session
					.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			.authorizeHttpRequests(auth -> auth
					.requestMatchers(
							"/swagger-ui/**",
							"/swagger-ui.html",
							"/v3/api-docs/**")
					.permitAll()

					.requestMatchers(
							HttpMethod.POST,
							"/api/v1/auth/login",
							"/api/v1/auth/refresh",
							"/api/v1/auth/register")
					.permitAll()

                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                    .requestMatchers("/oauth2/authorization/google", "/login/oauth2/code/google").permitAll()

					.requestMatchers("/api/admin/**")
					.hasRole("ADMIN")

					.anyRequest().authenticated())

			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
            .oauth2Login(oauth -> oauth
                .securityContextRepository(new NullSecurityContextRepository())
                .successHandler(googleLoginSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    if (request.getSession(false) != null) {
                        request.getSession(false).invalidate();
                    }
                    SecurityContextHolder.clearContext();
                    response.sendError(401, "Google login failed");
                }))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> response.sendError(401)))


			.addFilterBefore(
                    jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class
            )

			.requestCache(AbstractHttpConfigurer::disable);

		return http.build();

	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.frontend-origin:http://localhost:3000}") String frontendOrigin) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(frontendOrigin));
		configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN"));
		configuration.setAllowCredentials(true);
		configuration.validateAllowCredentials();

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
