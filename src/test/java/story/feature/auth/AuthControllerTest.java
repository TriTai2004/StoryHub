package story.feature.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import story.config.CustomUserDetailsService;
import story.config.SecurityConfig;
import story.exception.GlobalExceptionHandler;
import story.feature.auth.controllers.AuthController;
import story.feature.auth.dto.req.LoginRequest;
import story.feature.auth.dto.req.ChangePasswordRequest;
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.mappers.AuthMapper;
import story.feature.user.model.User;
import story.exception.AppException;
import story.exception.ErrorCode;
import java.util.UUID;
import story.feature.auth.services.service.IAuthService;
import story.feature.auth.services.service.ISessionService;
import story.security.CookieType;
import story.security.GoogleLoginSuccessHandler;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(AuthControllerTest.TestConfig.class)
class AuthControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private IAuthService authService;
    @Autowired private JwtService jwtService;
    @Autowired private ISessionService sessionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(authService, jwtService, sessionService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void browserCanObtainCsrfCookieThenLogin() throws Exception {
        var bootstrap = mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn().getResponse();
        Cookie token = bootstrap.getCookie("XSRF-TOKEN");
        assertNotNull(token);
        assertFalse(token.isHttpOnly());

        mvc.perform(post("/api/v1/auth/login").servletPath("/api/v1/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(authService).login(eq(new LoginRequest("user@example.com", "password123")), any(), any());
    }

    @Test
    void loginValidatesRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/login").servletPath("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invalid\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void loginIsAccessibleWithoutCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/login").servletPath("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk());
        verify(authService).login(eq(new LoginRequest("user@example.com", "password123")), any(), any());
    }

    @Test
    void refreshIsAccessibleWithoutAccessToken() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").with(csrf())
                .cookie(new Cookie(CookieType.REFRESH_TOKEN.getName(), "refresh-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(authService).refresh(any(), any());
    }

    @Test
    void refreshIsAccessibleWithExpiredAccessToken() throws Exception {
        when(jwtService.validateToken("expired", JwtService.TokenType.ACCESS))
            .thenThrow(new BadCredentialsException("Expired"));
        mvc.perform(post("/api/v1/auth/refresh").with(csrf())
                .cookie(new Cookie(CookieType.ACCESS_TOKEN.getName(), "expired")))
            .andExpect(status().isOk());
        verify(authService).refresh(any(), any());
    }

    @Test
    void refreshRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(CookieType.REFRESH_TOKEN.getName(), "refresh-token")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshAuthenticationFailureReturns401() throws Exception {
        doThrow(new BadCredentialsException("Invalid token")).when(authService).refresh(any(), any());
        mvc.perform(post("/api/v1/auth/refresh").with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void registerReturnsCreatedUserWithoutPassword() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName("New User");
        user.setEmail("new@example.com");
        user.setPasswordHash("secret-hash");
        when(authService.register(any())).thenReturn(new AuthMapper().toAuthResponse(user));

        mvc.perform(post("/api/v1/auth/register").servletPath("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"New User\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("new@example.com"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.password").doesNotExist());
        verify(authService).register(new RegisterRequest( "new@example.com", "password123"));
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register").servletPath("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void registerRejectsPasswordOverBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/v1/auth/register").servletPath("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"email\":\"new@example.com\",\"password\":\"" + "é".repeat(37) + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void registerIsAccessibleWithoutCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/register").servletPath("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isCreated());
        verify(authService).register(new RegisterRequest("new@example.com", "password123"));
    }

    @Test
    void registerDuplicateReturnsConflict() throws Exception {
        when(authService.register(any())).thenThrow(new AppException(ErrorCode.DATA_CONFLICT));
        mvc.perform(post("/api/v1/auth/register").servletPath("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DATA_CONFLICT"));
    }

    @Test
    void getMeReturnsCurrentUserWithoutPasswordOrCsrf() throws Exception {
        User currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        currentUser.setEmail("user@example.com");
        currentUser.setDisplayName("Test User");
        currentUser.setPasswordHash("secret-hash");
        when(authService.getMe()).thenReturn(new AuthMapper().toAuthResponse(currentUser));

        mvc.perform(get("/api/v1/auth/getMe").with(user("user@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(currentUser.getId().toString()))
            .andExpect(jsonPath("$.data.email").value("user@example.com"))
            .andExpect(jsonPath("$.data.displayName").value("Test User"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.password").doesNotExist());
        verify(authService).getMe();
    }

    @Test
    void getMeRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/getMe"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void getMeAuthenticationFailureReturns401() throws Exception {
        when(authService.getMe()).thenThrow(new InsufficientAuthenticationException("User is not authenticated"));

        mvc.perform(get("/api/v1/auth/getMe").with(user("user@example.com")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verify(authService).getMe();
    }

    @Test
    void getMeMissingUserReturns401() throws Exception {
        when(authService.getMe()).thenThrow(new UsernameNotFoundException("User not found"));

        mvc.perform(get("/api/v1/auth/getMe").with(user("user@example.com")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verify(authService).getMe();
    }

    @Test
    void logoutAllRevokesSessionsForAuthenticatedUser() throws Exception {
        mvc.perform(post("/api/v1/auth/logout-all").with(user("user@example.com")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(authService).logoutAll(any());
    }

    @Test
    void logoutAllRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/logout-all").with(csrf()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void logoutAllRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/logout-all").with(user("user@example.com")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    void authenticatedUserCanChangePassword() throws Exception {
        mvc.perform(put("/api/v1/auth/password").with(user("user@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"current-password","newPassword":"new-password","confirmNewPassword":"new-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(authService).changePassword(
            eq(new ChangePasswordRequest("current-password", "new-password", "new-password")), any());
    }

    @Test
    void changePasswordRejectsInvalidConfirmation() throws Exception {
        mvc.perform(put("/api/v1/auth/password").with(user("user@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"current-password","newPassword":"new-password","confirmNewPassword":"different-password"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(authService);
    }

    @Test
    void changePasswordRejectsPasswordOverBcryptByteLimit() throws Exception {
        String password = "é".repeat(37);
        mvc.perform(put("/api/v1/auth/password").with(user("user@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"current-password\",\"newPassword\":\""
                    + password + "\",\"confirmNewPassword\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(authService);
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mvc.perform(put("/api/v1/auth/password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"current-password","newPassword":"new-password","confirmNewPassword":"new-password"}
                    """))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void changePasswordRequiresCsrf() throws Exception {
        mvc.perform(put("/api/v1/auth/password").with(user("user@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"current-password","newPassword":"new-password","confirmNewPassword":"new-password"}
                    """))
            .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AuthController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean IAuthService authService() { return mock(IAuthService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean CustomUserDetailsService userDetailsService() { return mock(CustomUserDetailsService.class); }
        @Bean ISessionService sessionService() { return mock(ISessionService.class); }
        @Bean GoogleLoginSuccessHandler googleLoginSuccessHandler() { return mock(GoogleLoginSuccessHandler.class); }
        @Bean ClientRegistrationRepository clientRegistrationRepository() {
            return mock(ClientRegistrationRepository.class);
        }
    }
}
