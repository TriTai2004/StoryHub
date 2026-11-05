package story.feature.user;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import story.config.CustomUserDetailsService;
import story.config.SecurityConfig;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import story.exception.GlobalExceptionHandler;
import story.feature.auth.services.service.ISessionService;
import story.feature.user.controllers.UserController;
import story.feature.user.dto.req.UpdateProfileRequest;
import story.feature.user.mappers.UserMapper;
import story.feature.user.model.User;
import story.feature.user.services.service.IUserService;
import story.security.GoogleLoginSuccessHandler;
import story.security.JwtService;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(UserControllerTest.TestConfig.class)
class UserControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired IUserService service;
    MockMvc mvc;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void coverUploadBindsFileAndReturnsUrl() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cover.png", "image/png", new byte[] {1});
        User profile = new User();
        profile.setCoverUrl("https://example.com/cover.png");
        when(service.updateCover(any())).thenReturn(new UserMapper().toMyProfile(profile));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PATCH, "/api/v1/users/me/cover")
                .file(file).with(user("alice")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coverUrl").value("https://example.com/cover.png"))
            .andExpect(jsonPath("$.data.coverKey").doesNotExist());
        verify(service).updateCover(any());
    }

    @Test
    void publicProfileDoesNotExposeAccountInformation() throws Exception {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setEmail("private@example.com");
        user.setPasswordHash("secret");
        when(service.getPublicProfile(USER_ID)).thenReturn(new UserMapper().toPublicProfile(user));
        mvc.perform(get("/api/v1/users/{userId}", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("alice"))
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.role").doesNotExist())
            .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void publicRoutesRejectInvalidUserIds() throws Exception {
        mvc.perform(get("/api/v1/users/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/not-a-uuid/posts")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void publicPostsAllowAnonymousAccess() throws Exception {
        mvc.perform(get("/api/v1/users/{userId}/posts", USER_ID)).andExpect(status().isOk());
        verify(service).getPublicPosts(USER_ID, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @Test
    void allMeRoutesRequireAuthentication() throws Exception {
        for (String path : new String[] {"/me", "/me/posts", "/me/bookmarks"}) {
            mvc.perform(get("/api/v1/users" + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(patch("/api/v1/users/me").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void meRoutesTakePrecedenceOverUserIdRoutes() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/posts").with(user("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/bookmarks").with(user("alice"))).andExpect(status().isOk());
        verify(service).getMe();
        verify(service).getMyPosts(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        verify(service).getMyBookmarks(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "post.id")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void patchRequiresCsrf() throws Exception {
        mvc.perform(patch("/api/v1/users/me").with(user("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Hello\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void patchBindsOnlyEditableFields() throws Exception {
        mvc.perform(patch("/api/v1/users/me").with(user("alice")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"bio":"Hello","role":"ADMIN","status":"ACTIVE","email":"other@example.com",
                     "id":"00000000-0000-0000-0000-000000000001","username":"other","passwordHash":"evil"}
                    """))
            .andExpect(status().isOk());
        verify(service).updateMe(new UpdateProfileRequest(null, "Hello", null, null));
    }

    @Test
    void patchRejectsOversizedFields() throws Exception {
        mvc.perform(patch("/api/v1/users/me").with(user("alice")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + "a".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void pageableBindsPageSizeAndSort() throws Exception {
        mvc.perform(get("/api/v1/users/{userId}/posts?page=2&size=10&sort=createdAt,asc", USER_ID))
            .andExpect(status().isOk());
        verify(service).getPublicPosts(USER_ID, PageRequest.of(2, 10, Sort.by("createdAt").ascending()));
    }

    @Test
    void pageableUsesSpringFallbackForInvalidPage() throws Exception {
        mvc.perform(get("/api/v1/users/me/posts?page=-1&size=50").with(user("alice")))
            .andExpect(status().isOk());
        verify(service).getMyPosts(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @Test
    void cannotPatchAnotherUser() throws Exception {
        mvc.perform(patch("/api/v1/users/{userId}", USER_ID).with(user("bob")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableSpringDataWebSupport
    @Import({SecurityConfig.class, UserController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean IUserService userService() { return mock(IUserService.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean CustomUserDetailsService userDetailsService() { return mock(CustomUserDetailsService.class); }
        @Bean ISessionService sessionService() { return mock(ISessionService.class); }
        @Bean GoogleLoginSuccessHandler googleLoginSuccessHandler() { return mock(GoogleLoginSuccessHandler.class); }
        @Bean ClientRegistrationRepository clientRegistrationRepository() { return mock(ClientRegistrationRepository.class); }
    }
}
