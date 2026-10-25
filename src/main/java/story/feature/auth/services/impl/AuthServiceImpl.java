package story.feature.auth.services.impl;

import java.util.UUID;
import java.util.Map;
import java.util.Objects;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import story.config.CustomUserDetails;
import story.config.properties.CookieProperties;
import story.feature.auth.dto.req.LoginRequest;
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.dto.res.AuthResponse;
import story.feature.auth.mappers.AuthMapper;
import story.feature.auth.services.service.IAuthService;
import story.feature.auth.services.service.ISessionService;
import story.feature.auth.services.service.ISessionRefreshService;
import story.feature.user.model.User;
import story.feature.user.repository.UserRepository;
import story.security.CookieType;
import story.security.JwtService;

@Service 
@RequiredArgsConstructor 
public class AuthServiceImpl implements IAuthService {

    private final AuthMapper authMapper;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CookieProperties cookieProperties;
    private final UserRepository userRepository;
    private final ISessionService sessionService;
    private final PlatformTransactionManager transactionManager;
    private final ISessionRefreshService sessionRefreshService;

    @Override
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        TokenCookies cookies = sessionRefreshService.refreshSession(refreshToken, session -> {
            String accessToken = jwtService.generateAccessToken(session.userId().toString(),
                Map.of("sid", session.sessionId().toString()));
            return new TokenCookies(createAccessTokenCookie(accessToken),
                createRefreshTokenCookie(session.refreshToken(), session.expiresAt()));
        });

        // Rotation and response preparation have committed before either header is written.
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.accessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.refreshCookie().toString());
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String token = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (CookieType.REFRESH_TOKEN.getName().equals(cookie.getName())) {
                    if (token != null || cookie.getValue() == null || cookie.getValue().isBlank()) {
                        throw new BadCredentialsException("Invalid refresh token cookie");
                    }
                    token = cookie.getValue();
                }
            }
        }
        if (token == null) {
            throw new BadCredentialsException("Missing refresh token cookie");
        }
        return token;
    }

    private record TokenCookies(ResponseCookie accessCookie, ResponseCookie refreshCookie) {
        @Override
        public String toString() {
            return "TokenCookies[REDACTED]";
        }
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        Authentication authenticated = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.email(), loginRequest.password())
        );

        if (!(authenticated.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException("Authentication principal must be CustomUserDetails");
        }

        InetAddress ipAddress = resolveRemoteAddress(request);
        // Commit the session before exposing either token to the client.
        LoginResult result = Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status -> {
            User user = findActiveUser(userDetails.getId());
            AuthResponse authResponse = authMapper.toAuthResponse(user);
            var session = sessionService.createSession(user, request.getHeader("User-Agent"), ipAddress);
            String token = jwtService.generateAccessToken(user.getId().toString(),
                Map.of("sid", session.sessionId().toString()));
            return new LoginResult(authResponse, createAccessTokenCookie(token),
                createRefreshTokenCookie(session.refreshToken(), session.expiresAt()));
        }));

        response.addHeader(HttpHeaders.SET_COOKIE, result.accessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return result.authResponse();
    }

    private InetAddress resolveRemoteAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return null;
        }
        try {
            // Use the servlet peer address, not untrusted forwarding headers.
            return InetAddress.getByName(remoteAddress);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Unable to resolve servlet remote address", exception);
        }
    }

    private ResponseCookie createRefreshTokenCookie(String token, Instant expiresAt) {
        long remainingSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (remainingSeconds <= 0) {
            throw new IllegalStateException("New session has already expired");
        }
        return ResponseCookie.from(CookieType.REFRESH_TOKEN.getName(), token)
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite(cookieProperties.sameSite())
            .path("/api/auth")
            .maxAge(Math.min(cookieProperties.refreshTokenMaxAge(), remainingSeconds))
            .build();
    }

    private record LoginResult(AuthResponse authResponse, ResponseCookie accessCookie, ResponseCookie refreshCookie) {
        @Override
        public String toString() {
            return "LoginResult[cookies=REDACTED]";
        }
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
        return user;
    }

    private ResponseCookie createAccessTokenCookie(String token) {
        return ResponseCookie.from(CookieType.ACCESS_TOKEN.getName(), token)
            .httpOnly(cookieProperties.httpOnly())
            .secure(cookieProperties.secure())
            .sameSite(cookieProperties.sameSite())
            .path(cookieProperties.path())
            .maxAge(cookieProperties.accessTokenMaxAge())
            .build();

    }

    @Override
    public AuthResponse register(RegisterRequest registerRequest) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'register'");
    }

    @Override
    public AuthResponse getMe() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMe'");
    }
    
}

