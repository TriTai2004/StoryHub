package story.feature.auth.services.service;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import story.feature.auth.dto.req.LoginRequest;
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.dto.res.AuthResponse;

public interface IAuthService {
    
    AuthResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response);
    void refresh(HttpServletRequest request, HttpServletResponse response);
    AuthResponse register(RegisterRequest registerRequest);
    AuthResponse getMe();

}
