package story.feature.auth;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import story.feature.auth.services.impl.RefreshTokenServiceImpl;
import story.feature.auth.services.service.IRefreshTokenService;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenServiceImplTest {

    private final IRefreshTokenService service = new RefreshTokenServiceImpl();

    @Test
    void generatedTokenContains256BitsAndIsCookieSafe() {
        String token = service.generateToken();

        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, Base64.getUrlDecoder().decode(token).length);
        assertNotEquals(token, service.generateToken());
    }

    @Test
    void hashMatchesKnownSha256Vector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            service.hashToken("abc")
        );
    }

    @Test
    void hashingPreservesExactTokenValue() {
        assertNotEquals(service.hashToken("abc"), service.hashToken(" abc "));
        assertNotEquals(service.hashToken("abc"), service.hashToken("ABC"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingToken(String token) {
        assertThrows(IllegalArgumentException.class, () -> service.hashToken(token));
    }
}
