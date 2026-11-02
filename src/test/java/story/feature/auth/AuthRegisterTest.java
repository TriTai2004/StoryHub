package story.feature.auth;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import story.config.CustomUserDetailsService;
import story.config.properties.CookieProperties;
import story.exception.AppException;
import story.exception.ErrorCode;
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.mappers.AuthMapper;
import story.feature.auth.repository.UserIdentityRepository;
import story.feature.auth.services.impl.AuthServiceImpl;
import story.feature.auth.services.service.ISessionService;
import story.feature.auth.services.service.ISessionRefreshService;
import story.feature.user.model.User;
import story.feature.user.repository.UserRepository;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthRegisterTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private final JwtService jwt = mock(JwtService.class);
    private final ISessionService sessions = mock(ISessionService.class);
    private final AuthServiceImpl service = new AuthServiceImpl(new AuthMapper(), jwt,
        mock(AuthenticationManager.class), new CookieProperties(true, true, "Lax", "/", 900L, 604800L),
        users, sessions, mock(PlatformTransactionManager.class), mock(ISessionRefreshService.class), encoder,  mock(UserIdentityRepository.class));

    @Test
    void registersUserWithHashedPasswordWithoutUsernameOrSession() {
        RegisterRequest request = new RegisterRequest("New@Example.com", " password123 ");
        UUID id = UUID.randomUUID();
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertNull(user.getUsername());
            assertNull(user.getEmailVerifiedAt());
            assertEquals(User.Role.USER, user.getRole());
            assertEquals(User.Status.ACTIVE, user.getStatus());
            assertNotEquals(request.password(), user.getPasswordHash());
            assertTrue(encoder.matches(request.password(), user.getPasswordHash()));
            user.setId(id);
            return user;
        });

        var result = service.register(request);

        assertEquals(id, result.id());
        assertEquals("New User", result.displayName());
        assertEquals("new@example.com", result.email());
        verify(users).existsByEmailIgnoreCase("new@example.com");
        verifyNoInteractions(jwt, sessions);
    }

    @Test
    void duplicateEmailFailsBeforeHashingOrSaving() {
        when(users.existsByEmailIgnoreCase("new@example.com")).thenReturn(true);

        AppException error = assertThrows(AppException.class,
            () -> service.register(new RegisterRequest( "NEW@example.com", "password123")));

        assertEquals(ErrorCode.DATA_CONFLICT, error.getErrorCode());
        verifyNoInteractions(encoder, jwt, sessions);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void concurrentUniqueConstraintFailurePropagatesToGlobalHandler() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.register(new RegisterRequest("new@example.com", "password123")));

        verifyNoInteractions(jwt, sessions);
    }

    @Test
    void loginLookupAcceptsEmailWithDifferentCase() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("new@example.com");
        user.setPasswordHash("hash");
        when(users.findByEmailIgnoreCase("NEW@example.com")).thenReturn(Optional.of(user));

        var details = new CustomUserDetailsService(users).loadUserByUsername("NEW@example.com");

        assertEquals("new@example.com", details.getUsername());
        assertEquals("hash", details.getPassword());
    }
}
