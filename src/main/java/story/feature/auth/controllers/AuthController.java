package story.feature.auth.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import story.common.dto.ApiResponse;
import story.feature.auth.dto.req.LoginRequest;
import story.feature.auth.dto.req.RegisterRequest;
import story.feature.auth.dto.req.ChangePasswordRequest;
import story.feature.auth.dto.res.AuthResponse;
import story.feature.auth.services.service.IAuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final IAuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(body)));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Void>> csrf(CsrfToken csrfToken) {
        // Materialize the deferred token so the browser receives XSRF-TOKEN.
        csrfToken.getToken();
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(body, request, response)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
        authService.refresh(request, response);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/getMe")
    public ResponseEntity<ApiResponse<AuthResponse>> getMe() {

        return ResponseEntity.ok(ApiResponse.success(authService.getMe()));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletResponse response) {
        authService.logoutAll(response);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletResponse response) {
        authService.changePassword(body, response);
        return ResponseEntity.ok(ApiResponse.empty());
    }

}
