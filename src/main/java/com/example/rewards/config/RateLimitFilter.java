package com.example.rewards.config;

import com.example.rewards.auth.AuthRequestAttributes;
import com.example.rewards.common.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern ACCOUNT_WRITE_PATH = Pattern.compile("^/accounts/([^/]+)/(earn|spend|reversal)$");
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RateLimitService rateLimitService;
    private final FilterErrorWriter filterErrorWriter;
    private final int loginPerMinute;
    private final int registerPerMinute;
    private final int refreshPerMinute;
    private final int writePerMinute;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            FilterErrorWriter filterErrorWriter,
            @Value("${rate.limit.login.per-minute}") int loginPerMinute,
            @Value("${rate.limit.register.per-minute}") int registerPerMinute,
            @Value("${rate.limit.refresh.per-minute}") int refreshPerMinute,
            @Value("${rate.limit.write.per-minute}") int writePerMinute
    ) {
        this.rateLimitService = rateLimitService;
        this.filterErrorWriter = filterErrorWriter;
        this.loginPerMinute = loginPerMinute;
        this.registerPerMinute = registerPerMinute;
        this.refreshPerMinute = refreshPerMinute;
        this.writePerMinute = writePerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            enforceLimits(request);
            filterChain.doFilter(request, response);
        } catch (TooManyRequestsException ex) {
            filterErrorWriter.write(response, 429, "RATE_LIMITED", ex.getMessage());
        }
    }

    private void enforceLimits(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return;
        }

        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);

        switch (path) {
            case "/auth/login" -> rateLimitService.assertAllowed(
                    "auth:login:" + clientIp,
                    loginPerMinute,
                    ONE_MINUTE,
                    "Too many login attempts. Please retry shortly."
            );
            case "/auth/register" -> rateLimitService.assertAllowed(
                    "auth:register:" + clientIp,
                    registerPerMinute,
                    ONE_MINUTE,
                    "Too many registration attempts. Please retry shortly."
            );
            case "/auth/refresh" -> rateLimitService.assertAllowed(
                    "auth:refresh:" + clientIp,
                    refreshPerMinute,
                    ONE_MINUTE,
                    "Too many token refresh attempts. Please retry shortly."
            );
            default -> applyWriteRateLimit(path, request);
        }
    }

    private void applyWriteRateLimit(String path, HttpServletRequest request) {
        if (!"/transfer".equals(path) && !"/accounts".equals(path) && !ACCOUNT_WRITE_PATH.matcher(path).matches()) {
            return;
        }

        Object userIdAttr = request.getAttribute(AuthRequestAttributes.USER_ID);
        if (!(userIdAttr instanceof UUID userId)) {
            return;
        }

        String scope = deriveWriteScope(path);
        rateLimitService.assertAllowed(
                "write:user:%s:scope:%s".formatted(userId, scope),
                writePerMinute,
                ONE_MINUTE,
                "Too many write requests. Please retry shortly."
        );
    }

    private String deriveWriteScope(String path) {
        if ("/transfer".equals(path)) {
            return "transfer";
        }
        if ("/accounts".equals(path)) {
            return "create-account";
        }
        Matcher matcher = ACCOUNT_WRITE_PATH.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1) + ":" + matcher.group(2);
        }
        return "unknown";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int firstComma = forwardedFor.indexOf(',');
            return (firstComma > 0 ? forwardedFor.substring(0, firstComma) : forwardedFor).trim();
        }
        return request.getRemoteAddr();
    }
}
