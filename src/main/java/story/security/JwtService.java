package story.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import story.config.properties.JwtProperties;

@Service
public class JwtService {
    private static final String TOKEN_TYPE = "token_type";
    private static final Set<String> RESERVED_CLAIMS = Set.of(
            "iss", "sub", "aud", "exp", "nbf", "iat", "jti", TOKEN_TYPE);

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        var key = new SecretKeySpec(Base64.getDecoder().decode(properties.secret()), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();

        var timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(properties.clockSkewSeconds()));
        timestampValidator.setClock(clock);
        timestampValidator.setAllowEmptyExpiryClaim(false);
        timestampValidator.setAllowEmptyNotBeforeClaim(false);

        var jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                this::validateRequiredClaims));
        this.decoder = jwtDecoder;
    }

    public String generateAccessToken(String subject) {
        return generateAccessToken(subject, Map.of());
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, TokenType.ACCESS, properties.accessTokenTtlSeconds());
    }

    public String generateRefreshToken(String subject) {
        return generateRefreshToken(subject, Map.of());
    }

    public String generateRefreshToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, TokenType.REFRESH, properties.refreshTokenTtlSeconds());
    }

    public Jwt validateToken(String token, TokenType expectedType) {
        Objects.requireNonNull(expectedType, "expectedType");
        if (!StringUtils.hasText(token)) {
            throw new BadCredentialsException("Invalid or expired JWT");
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (!expectedType.value.equals(jwt.getClaimAsString(TOKEN_TYPE))) {
                throw new BadCredentialsException("Invalid or expired JWT");
            }
            return jwt;
        } catch (JwtException exception) {
            throw new BadCredentialsException("Invalid or expired JWT", exception);
        }
    }

    public boolean isTokenValid(String token, TokenType expectedType) {
        try {
            validateToken(token, expectedType);
            return true;
        } catch (BadCredentialsException exception) {
            return false;
        }
    }

    public String extractSubject(String token, TokenType expectedType) {
        return validateToken(token, expectedType).getSubject();
    }

    public Map<String, Object> extractClaims(String token, TokenType expectedType) {
        return validateToken(token, expectedType).getClaims();
    }

    public Instant extractExpiration(String token, TokenType expectedType) {
        return validateToken(token, expectedType).getExpiresAt();
    }

    private String generateToken(String subject, Map<String, Object> claims, TokenType type, long ttlSeconds) {
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        Map<String, Object> customClaims = Map.copyOf(Objects.requireNonNull(claims, "claims"));
        if (customClaims.keySet().stream().anyMatch(RESERVED_CLAIMS::contains)) {
            throw new IllegalArgumentException("Custom claims must not override reserved JWT claims");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet payload = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE, type.value)
                .claims(values -> values.putAll(customClaims))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, payload)).getTokenValue();
    }

    private OAuth2TokenValidatorResult validateRequiredClaims(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        Instant notBefore = jwt.getNotBefore();
        Object subject = jwt.getClaims().get("sub");
        Object id = jwt.getClaims().get("jti");
        Object type = jwt.getClaims().get(TOKEN_TYPE);
        boolean valid = subject instanceof String value && StringUtils.hasText(value)
                && id instanceof String identifier && StringUtils.hasText(identifier)
                && (TokenType.ACCESS.value.equals(type) || TokenType.REFRESH.value.equals(type))
                && issuedAt != null && expiresAt != null && notBefore != null
                && expiresAt.isAfter(issuedAt) && expiresAt.isAfter(notBefore)
                && !issuedAt.isAfter(clock.instant().plusSeconds(properties.clockSkewSeconds()));
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid JWT claims", null));
    }

    public enum TokenType {
        ACCESS("access"), REFRESH("refresh");

        private final String value;

        TokenType(String value) {
            this.value = value;
        }
    }
}
