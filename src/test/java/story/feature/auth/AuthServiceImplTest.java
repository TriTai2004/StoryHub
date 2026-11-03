package story.feature.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import story.config.CustomUserDetails;
import story.config.properties.CookieProperties;
import story.feature.auth.dto.req.LoginRequest;
import story.feature.auth.mappers.AuthMapper;
import story.feature.auth.repository.UserIdentityRepository;
import story.feature.auth.services.impl.AuthServiceImpl;
import story.feature.auth.services.service.ISessionService;
import story.feature.auth.services.service.ISessionRefreshService;
import story.feature.auth.services.service.ISessionService.CreatedSession;
import story.feature.user.model.User;
import story.feature.user.repository.UserRepository;
import story.security.CookieType;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository repository = mock(UserRepository.class);
    private final UserIdentityRepository identities = mock(UserIdentityRepository.class);
    private final ISessionService sessionService = mock(ISessionService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoginRequest request = new LoginRequest("user@example.com", "password123");
    private final User user = new User();
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        service = new AuthServiceImpl(new AuthMapper(), jwtService, authenticationManager,
            new CookieProperties(true, true, "Lax", "/", 900L, 604800L), repository,
            sessionService, transactionManager, mock(ISessionRefreshService.class), mock(PasswordEncoder.class), identities);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        httpRequest.addHeader("User-Agent", "Test Browser");
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setDisplayName("Test User");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMeReturnsCurrentUserFromDatabase() {
        setCurrentUser();
        user.setEmail("updated@example.com");
        user.setDisplayName("Updated User");
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        var result = service.getMe();

        assertEquals(user.getId(), result.id());
        assertEquals("updated@example.com", result.email());
        assertEquals("Updated User", result.displayName());
        assertEquals(user.getRole(), result.role());
        assertEquals(user.getStatus(), result.status());
        verify(repository).findById(user.getId());
        verifyNoInteractions(authenticationManager, jwtService, sessionService);
    }

    @Test
    void getMeRejectsMissingAuthentication() {
        assertThrows(InsufficientAuthenticationException.class, service::getMe);
        verifyNoInteractions(repository);
    }

    @Test
    void getMeRejectsUnexpectedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("unexpected", null, List.of()));

        assertThrows(InsufficientAuthenticationException.class, service::getMe);
        verifyNoInteractions(repository);
    }

    @Test
    void getMeRejectsMissingUser() {
        setCurrentUser();
        when(repository.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, service::getMe);
        verify(repository).findById(user.getId());
    }

    @Test
    void logoutAllRevokesUserSessionsClearsCookiesAndSecurityContext() {
        setCurrentUser();

        service.logoutAll(response);

        verify(sessionService).revokeAllSessions(user.getId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookies.size());
        assertTrue(setCookies.stream().anyMatch(cookie ->
            cookie.startsWith(CookieType.ACCESS_TOKEN.getName() + "=") && cookie.contains("Max-Age=0")));
        assertTrue(setCookies.stream().anyMatch(cookie ->
            cookie.startsWith(CookieType.REFRESH_TOKEN.getName() + "=") && cookie.contains("Max-Age=0")));
    }

    @Test
    void logoutAllRejectsMissingAuthentication() {
        assertThrows(InsufficientAuthenticationException.class, () -> service.logoutAll(response));

        verifyNoInteractions(sessionService);
        assertTrue(response.getHeaders(HttpHeaders.SET_COOKIE).isEmpty());
    }

    private void setCurrentUser() {
        var principal = new CustomUserDetails(user.getId(), user.getEmail(), null,
            User.Status.ACTIVE, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private void authenticateUser() {
        var principal = new CustomUserDetails(user.getId(), user.getEmail(), null,
            User.Status.ACTIVE, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Test
    void loginReturnsUserAndIssuesCookieWithUserIdToken() {
        authenticateUser();
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        UUID sessionId = UUID.randomUUID();
        when(sessionService.createSession(eq(user), eq("Test Browser"), any())).thenReturn(
            new CreatedSession(sessionId, "refresh-token", Instant.now().plus(Duration.ofDays(7)),
                Instant.now().plus(Duration.ofDays(30))));
        when(jwtService.generateAccessToken(user.getId().toString(), Map.of("sid", sessionId.toString())))
            .thenReturn("access-token");
        doAnswer(invocation -> {
            assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
            return null;
        }).when(transactionManager).commit(any());

        var result = service.login(request, httpRequest, response);

        assertEquals(user.getId(), result.id());
        assertEquals(user.getEmail(), result.email());
        verify(authenticationManager).authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        verify(jwtService).generateAccessToken(user.getId().toString(), Map.of("sid", sessionId.toString()));
        verify(transactionManager).commit(any());
        verify(sessionService).createSession(eq(user), eq("Test Browser"),
            argThat(address -> address.getHostAddress().equals("127.0.0.1")));
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains(CookieType.ACCESS_TOKEN.getName() + "=access-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Max-Age=900"));
        var cookies = response.getHeaders(HttpHeaders.SET_COOKIE).stream().toList();
        assertEquals(2, cookies.size());
        assertTrue(cookies.get(1).contains(CookieType.REFRESH_TOKEN.getName() + "=refresh-token"));
        assertTrue(cookies.get(1).contains("HttpOnly"));
        assertTrue(cookies.get(1).contains("Secure"));
        assertTrue(cookies.get(1).contains("SameSite=Lax"));
        assertTrue(cookies.get(1).contains("Path=/api/v1/auth;"));
    }

    @Test
    void badCredentialsDoNotIssueCookie() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThrows(BadCredentialsException.class, () -> service.login(request, httpRequest, response));

        verifyNoInteractions(repository, jwtService, sessionService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void missingUserDoesNotIssueCookie() {
        authenticateUser();
        when(repository.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.login(request, httpRequest, response));

        verifyNoInteractions(jwtService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @ParameterizedTest
    @EnumSource(value = User.Status.class, names = {"BANNED", "SUSPENDED"})
    void inactiveUserDoesNotIssueCookie(User.Status status) {
        authenticateUser();
        user.setStatus(status);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> service.login(request, httpRequest, response));

        verifyNoInteractions(jwtService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void unexpectedPrincipalIsASystemError() {
        when(authenticationManager.authenticate(any())).thenReturn(
            UsernamePasswordAuthenticationToken.authenticated("unexpected", null, List.of()));

        assertThrows(IllegalStateException.class, () -> service.login(request, httpRequest, response));

        verifyNoInteractions(repository, jwtService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void sessionFailureDoesNotIssueTokensOrCookies() {
        authenticateUser();
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(sessionService.createSession(any(), any(), any())).thenThrow(new IllegalStateException("DB failure"));

        assertThrows(IllegalStateException.class, () -> service.login(request, httpRequest, response));

        verifyNoInteractions(jwtService);
        verify(transactionManager).rollback(any());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void tokenGenerationFailureRollsBackSessionAndDoesNotIssueCookies() {
        prepareSession();
        when(jwtService.generateAccessToken(any(), any())).thenThrow(new IllegalStateException("Signing failed"));

        assertThrows(IllegalStateException.class, () -> service.login(request, httpRequest, response));

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void commitFailureDoesNotIssueCookies() {
        prepareSession();
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        doThrow(new IllegalStateException("Commit failed")).when(transactionManager).commit(any());

        assertThrows(IllegalStateException.class, () -> service.login(request, httpRequest, response));

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void googleRejectsUnverifiedEmail() {
        var principal = googlePrincipal();
        when(principal.getEmailVerified()).thenReturn(false);
        assertThrows(BadCredentialsException.class,
            () -> service.loginGoogle(principal, httpRequest, response));
        verifyNoInteractions(identities, repository, sessionService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void googleDoesNotAutomaticallyLinkExistingEmail() {
        var principal = googlePrincipal();
        when(repository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);
        assertThrows(BadCredentialsException.class,
            () -> service.loginGoogle(principal, httpRequest, response));
        verify(identities, never()).save(any());
        verifyNoInteractions(sessionService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void googleCreatesIdentityAndIssuesCookiesAfterCommit() {
        var principal = googlePrincipal();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            User created = invocation.getArgument(0);
            created.setId(user.getId());
            assertNotNull(created.getUsername());
            assertNull(created.getPasswordHash());
            assertNotNull(created.getEmailVerifiedAt());
            assertEquals(User.Role.USER, created.getRole());
            return created;
        });
        when(sessionService.createSession(any(), any(), any())).thenReturn(
            new CreatedSession(UUID.randomUUID(), "refresh-token", Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200)));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        doAnswer(invocation -> {
            assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
            return null;
        }).when(transactionManager).commit(any());

        service.loginGoogle(principal, httpRequest, response);

        verify(identities).save(argThat(identity ->
            identity.getProvider() == story.feature.auth.model.AuthIdentity.Provider.GOOGLE
                && "google-subject".equals(identity.getProviderSubject())));
        assertEquals(2, response.getHeaders(HttpHeaders.SET_COOKIE).size());
        verify(transactionManager).commit(any());
    }

    @Test
    void googleIdentityFailureRollsBackAndDoesNotIssueCookies() {
        var principal = googlePrincipal();
        when(repository.saveAndFlush(any())).thenReturn(user);
        when(identities.save(any())).thenThrow(new IllegalStateException("Identity persistence failed"));

        assertThrows(IllegalStateException.class,
            () -> service.loginGoogle(principal, httpRequest, response));

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verifyNoInteractions(sessionService, jwtService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void googleRecognizesSubjectAndRejectsDisabledAccount() {
        var principal = googlePrincipal();
        var identity = new story.feature.auth.model.AuthIdentity();
        identity.setUser(user);
        when(identities.findByProviderAndProviderSubject(any(), eq("google-subject")))
            .thenReturn(Optional.of(identity));
        user.setStatus(User.Status.BANNED);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        assertThrows(DisabledException.class,
            () -> service.loginGoogle(principal, httpRequest, response));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(sessionService);
    }

    private org.springframework.security.oauth2.core.oidc.user.OidcUser googlePrincipal() {
        var principal = mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class);
        when(principal.getSubject()).thenReturn("google-subject");
        when(principal.getEmail()).thenReturn("User@Example.com");
        when(principal.getEmailVerified()).thenReturn(true);
        return principal;
    }

    private void prepareSession() {
        authenticateUser();
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(sessionService.createSession(any(), any(), any())).thenReturn(
            new CreatedSession(UUID.randomUUID(), "refresh-token", Instant.now().plus(Duration.ofDays(7)),
                Instant.now().plus(Duration.ofDays(30))));
    }
}
