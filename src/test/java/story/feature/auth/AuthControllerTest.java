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
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.mappers.AuthMapper;
import story.feature.user.model.User;
import story.exception.AppException;
import story.exception.ErrorCode;
import java.util.UUID;
import story.feature.auth.services.service.IAuthService;
import story.security.CookieType;
import story.security.JwtService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(AuthControllerTest.TestConfig.class)
class AuthControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private IAuthService authService;
    @Autowired private JwtService jwtService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(authService, jwtService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void browserCanObtainCsrfCookieThenLogin() throws Exception {
        var bootstrap = mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn().getResponse();
        Cookie token = bootstrap.getCookie("XSRF-TOKEN");
        assertNotNull(token);
        assertFalse(token.isHttpOnly());

        mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(authService).login(eq(new LoginRequest("user@example.com", "password123")), any(), any());
    }

    @Test
    void loginValidatesRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invalid\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void loginRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshIsAccessibleWithoutAccessToken() throws Exception {
        mvc.perform(post("/api/auth/refresh").with(csrf())
                .cookie(new Cookie(CookieType.REFRESH_TOKEN.getName(), "refresh-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(authService).refresh(any(), any());
    }

    @Test
    void refreshIsAccessibleWithExpiredAccessToken() throws Exception {
        when(jwtService.validateToken("expired", JwtService.TokenType.ACCESS))
            .thenThrow(new BadCredentialsException("Expired"));
        mvc.perform(post("/api/auth/refresh").with(csrf())
                .cookie(new Cookie(CookieType.ACCESS_TOKEN.getName(), "expired")))
            .andExpect(status().isOk());
        verify(authService).refresh(any(), any());
    }

    @Test
    void refreshRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(CookieType.REFRESH_TOKEN.getName(), "refresh-token")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshAuthenticationFailureReturns401() throws Exception {
        doThrow(new BadCredentialsException("Invalid token")).when(authService).refresh(any(), any());
        mvc.perform(post("/api/auth/refresh").with(csrf()))
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

        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"New User\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("new@example.com"))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.password").doesNotExist());
        verify(authService).register(new RegisterRequest( "new@example.com", "password123"));
    }

    @Test
    void registerRejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(authService);
    }

    @Test
    void registerRejectsPasswordOverBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"email\":\"new@example.com\",\"password\":\"" + "é".repeat(37) + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void registerRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void registerDuplicateReturnsConflict() throws Exception {
        when(authService.register(any())).thenThrow(new AppException(ErrorCode.DATA_CONFLICT));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"User\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DATA_CONFLICT"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AuthController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean IAuthService authService() { return mock(IAuthService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean CustomUserDetailsService userDetailsService() { return mock(CustomUserDetailsService.class); }
    }
}
