package story.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import story.feature.auth.services.service.IAuthService;

@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final ObjectProvider<IAuthService> authService;
    private final String frontendOrigin;

    public GoogleLoginSuccessHandler(ObjectProvider<IAuthService> authService,
            @Value("${app.frontend-origin:http://localhost:3000}") String frontendOrigin) {
        this.authService = authService;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                    || !"google".equals(oauth.getAuthorizedClientRegistrationId())
                    || !(oauth.getPrincipal() instanceof OidcUser principal)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Google login");
                return;
            }
            authService.getObject().loginGoogle(principal, request, response);
        } catch (AuthenticationException | DataIntegrityViolationException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google login failed");
            return;
        } finally {
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        response.sendRedirect(frontendOrigin);
    }
}
