package com.example.rewards.api;

import com.example.rewards.auth.AuthRequestAttributes;
import com.example.rewards.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            HttpServletRequest request,
            @Valid @RequestBody LogoutRequest body
    ) {
        UUID userId = AuthRequestAttributes.requireUserId(request);
        authService.logout(userId, body);
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpServletRequest request) {
        UUID userId = AuthRequestAttributes.requireUserId(request);
        return authService.me(userId);
    }
}
