package com.example.rewards.auth;

import com.example.rewards.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class AuthRequestAttributes {

    public static final String USER_ID = "auth.userId";
    public static final String USER_EMAIL = "auth.userEmail";

    private AuthRequestAttributes() {
    }

    public static UUID requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(USER_ID);
        if (userId instanceof UUID value) {
            return value;
        }
        throw new UnauthorizedException("Authentication required");
    }
}
