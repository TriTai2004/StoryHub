package story.feature.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import story.config.properties.JwtProperties;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static story.security.JwtService.TokenType.*;

class JwtServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("test-only-key-material-not-for-production".repeat(2).getBytes(StandardCharsets.UTF_8));
    private final JwtProperties properties = new JwtProperties(SECRET, "storyhub-test", "storyhub-api", 900L, 604800L, 30L);
    private JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void generatesAndVerifiesAccessTokenWithClaims() {
        String token = service.generateAccessToken("user-1", Map.of("roles", List.of("USER")));
        Jwt jwt = service.validateToken(token, ACCESS);

        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals("storyhub-test", jwt.getClaimAsString("iss"));
        assertEquals(List.of("storyhub-api"), jwt.getAudience());
        assertEquals("user-1", service.extractSubject(token, ACCESS));
        assertEquals(List.of("USER"), service.extractClaims(token, ACCESS).get("roles"));
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW, jwt.getNotBefore());
        assertEquals(NOW.plusSeconds(900), service.extractExpiration(token, ACCESS));
        assertNotNull(jwt.getId());
        assertTrue(service.isTokenValid(token, ACCESS));
    }

    @Test
    void generatesRefreshTokenWithItsOwnLifetimeAndSessionClaim() {
        String token = service.generateRefreshToken("user-1", Map.of("sid", "session-1"));
        Jwt jwt = service.validateToken(token, REFRESH);
        assertEquals(NOW.plusSeconds(604800), jwt.getExpiresAt());
        assertEquals("refresh", jwt.getClaimAsString("token_type"));
        assertEquals("session-1", jwt.getClaimAsString("sid"));
    }

    @Test
    void neverAcceptsAccessAndRefreshTokensInterchangeably() {
        assertFalse(service.isTokenValid(service.generateRefreshToken("user-1"), ACCESS));
        assertFalse(service.isTokenValid(service.generateAccessToken("user-1"), REFRESH));
    }

    @Test
    void generatesUniqueTokenIds() {
        Jwt first = service.validateToken(service.generateAccessToken("user-1"), ACCESS);
        Jwt second = service.validateToken(service.generateAccessToken("user-1"), ACCESS);
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void verifiesUsingTheDefaultSystemClockConstructor() {
        JwtService realtime = new JwtService(properties);
        assertTrue(realtime.isTokenValid(realtime.generateAccessToken("user-1"), ACCESS));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "a.b.c"})
    void rejectsMissingOrMalformedTokens(String token) {
        assertFalse(service.isTokenValid(token, ACCESS));
        assertThrows(BadCredentialsException.class, () -> service.validateToken(token, ACCESS));
        assertThrows(BadCredentialsException.class, () -> service.extractSubject(token, ACCESS));
        assertThrows(BadCredentialsException.class, () -> service.extractClaims(token, ACCESS));
        assertThrows(BadCredentialsException.class, () -> service.extractExpiration(token, ACCESS));
    }

    @Test
    void rejectsTamperedSignature() {
        String[] parts = service.generateAccessToken("user-1").split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        assertFalse(service.isTokenValid(tampered, ACCESS));
    }

    @Test
    void rejectsUnsignedTokens() {
        String payload = service.generateAccessToken("user-1").split("\\.")[1];
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        assertFalse(service.isTokenValid(header + "." + payload + ".", ACCESS));
    }

    @Test
    void rejectsWrongAlgorithmEvenWithTheCorrectKey() {
        assertFalse(service.isTokenValid(sign(validClaims(), MacAlgorithm.HS384, SECRET), ACCESS));
    }

    @Test
    void rejectsWrongSigningKey() {
        String otherKey = Base64.getEncoder().encodeToString("different-test-key".repeat(4).getBytes(StandardCharsets.UTF_8));
        assertFalse(service.isTokenValid(sign(validClaims(), MacAlgorithm.HS256, otherKey), ACCESS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "nbf", "exp", "jti", "token_type"})
    void rejectsMissingRequiredClaims(String claim) {
        Map<String, Object> claims = validClaims();
        claims.remove(claim);
        assertFalse(service.isTokenValid(sign(claims, MacAlgorithm.HS256, SECRET), ACCESS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "token_type"})
    void rejectsWrongIssuerAudienceOrTokenType(String claim) {
        Map<String, Object> claims = validClaims();
        claims.put(claim, claim.equals("aud") ? List.of("wrong-audience") : "wrong-value");
        assertFalse(service.isTokenValid(sign(claims, MacAlgorithm.HS256, SECRET), ACCESS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "jti"})
    void rejectsBlankIdentityClaims(String claim) {
        Map<String, Object> claims = validClaims();
        claims.put(claim, " ");
        assertFalse(service.isTokenValid(sign(claims, MacAlgorithm.HS256, SECRET), ACCESS));
    }

    @Test
    void rejectsExpiredTokenBeyondConfiguredClockSkew() {
        var reader = new JwtService(properties, Clock.fixed(NOW.plusSeconds(931), ZoneOffset.UTC));
        assertFalse(reader.isTokenValid(service.generateAccessToken("user-1"), ACCESS));
    }

    @Test
    void acceptsTimestampWithinConfiguredClockSkew() {
        var reader = new JwtService(properties, Clock.fixed(NOW.plusSeconds(910), ZoneOffset.UTC));
        assertTrue(reader.isTokenValid(service.generateAccessToken("user-1"), ACCESS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nbf", "iat"})
    void rejectsFutureTimestampsBeyondClockSkew(String claim) {
        Map<String, Object> claims = validClaims();
        claims.put(claim, NOW.plusSeconds(60));
        assertFalse(service.isTokenValid(sign(claims, MacAlgorithm.HS256, SECRET), ACCESS));
    }

    @Test
    void rejectsExpirationBeforeIssuance() {
        Map<String, Object> claims = validClaims();
        claims.put("exp", NOW.minusSeconds(1));
        assertFalse(service.isTokenValid(sign(claims, MacAlgorithm.HS256, SECRET), ACCESS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "nbf", "exp", "jti", "token_type"})
    void preventsCustomClaimsFromOverridingReservedClaims(String claim) {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateAccessToken("user-1", Map.of(claim, "override")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankSubjects(String subject) {
        assertThrows(IllegalArgumentException.class, () -> service.generateAccessToken(subject));
    }

    private Map<String, Object> validClaims() {
        return new HashMap<>(Map.of("iss", "storyhub-test", "aud", List.of("storyhub-api"),
                "sub", "user-1", "iat", NOW, "nbf", NOW, "exp", NOW.plusSeconds(900),
                "jti", "test-id", "token_type", "access"));
    }

    private String sign(Map<String, Object> claims, MacAlgorithm algorithm, String secret) {
        var payload = new JWTClaimsSet.Builder();
        claims.forEach((name, value) -> payload.claim(name, value instanceof Instant instant ? Date.from(instant) : value));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.parse(algorithm.getName()))
                .type(JOSEObjectType.JWT).build(), payload.build());
        try {
            jwt.sign(new MACSigner(Base64.getDecoder().decode(secret)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new AssertionError("Could not sign test token", exception);
        }
    }
}
