package story.feature.auth.services.service;

public interface IRefreshTokenService {

    /** Returns a random token for the client; persist only its hash. */
    String generateToken();

    /** Returns the SHA-256 hash as 64 lowercase hexadecimal characters. */
    String hashToken(String token);
}
