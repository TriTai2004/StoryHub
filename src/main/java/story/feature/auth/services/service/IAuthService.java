package story.feature.auth.services.service;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import jakarta.servlet.http.HttpServletRequest;
import story.feature.auth.dto.req.LoginRequest;
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.dto.req.ChangePasswordRequest;
import story.feature.auth.dto.res.AuthResponse;

public interface IAuthService {
    
    AuthResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response);
    void refresh(HttpServletRequest request, HttpServletResponse response);
    AuthResponse register(RegisterRequest registerRequest);
    AuthResponse getMe();
    void logout(HttpServletRequest request, HttpServletResponse response);
    AuthResponse loginGoogle(OidcUser principal,
            HttpServletRequest request, HttpServletResponse response);
    void logoutAll(HttpServletResponse response);
    void changePassword(ChangePasswordRequest request, HttpServletResponse response);

}
