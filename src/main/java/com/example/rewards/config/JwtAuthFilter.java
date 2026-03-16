package com.example.rewards.config;

import com.example.rewards.auth.AuthRequestAttributes;
import com.example.rewards.auth.AuthTokenService;
import com.example.rewards.auth.AuthenticatedUser;
import com.example.rewards.common.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;
    private final FilterErrorWriter filterErrorWriter;

    public JwtAuthFilter(AuthTokenService authTokenService, FilterErrorWriter filterErrorWriter) {
        this.authTokenService = authTokenService;
        this.filterErrorWriter = filterErrorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            AuthenticatedUser authenticatedUser = authenticateFromHeader(request);
            if (authenticatedUser != null) {
                request.setAttribute(AuthRequestAttributes.USER_ID, authenticatedUser.userId());
                request.setAttribute(AuthRequestAttributes.USER_EMAIL, authenticatedUser.email());
            }

            if (requiresAuthentication(request) && authenticatedUser == null) {
                throw new UnauthorizedException("Authentication required");
            }

            filterChain.doFilter(request, response);
        } catch (UnauthorizedException ex) {
            filterErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
        }
    }

    private AuthenticatedUser authenticateFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }

        if (!authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid authorization scheme");
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("Missing access token");
        }

        return authTokenService.parseAccessToken(token);
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        if ("/health".equals(path) || "/actuator/health".equals(path)) {
            return false;
        }

        if ("/auth/login".equals(path) || "/auth/register".equals(path) || "/auth/refresh".equals(path)) {
            return false;
        }

        return true;
    }
}
