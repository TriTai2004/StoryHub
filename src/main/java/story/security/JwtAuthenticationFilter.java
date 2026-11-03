package story.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import story.config.CustomUserDetails;
import story.config.CustomUserDetailsService;
import story.feature.auth.services.service.ISessionService;
import story.security.JwtService.TokenType;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ISessionService sessionService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            ISessionService sessionService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = extractAccessToken(request);

            if (token != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                var jwt = jwtService.validateToken(token, TokenType.ACCESS);

                UUID userId = UUID.fromString(jwt.getSubject());

                String sid = jwt.getClaimAsString("sid");

                if (sid == null || sid.isBlank()) {
                    throw new BadCredentialsException("Missing session ID");
                }

                UUID sessionId = UUID.fromString(sid);
                sessionService.validateActiveSession(sessionId, userId);

                CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUserId(userId);

                if (userDetails.isEnabled()
                        && userDetails.isAccountNonLocked()
                        && userDetails.isAccountNonExpired()
                        && userDetails.isCredentialsNonExpired()) {

                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

        } catch (AuthenticationException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractAccessToken(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (CookieType.ACCESS_TOKEN.getName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
