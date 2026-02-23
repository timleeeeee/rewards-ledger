package com.example.rewards.auth;

import com.example.rewards.common.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class AuthTokenService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final String configuredSecret;
    private final long accessTokenMinutes;

    private SecretKey secretKey;

    public AuthTokenService(
            @Value("${auth.jwt.secret}") String configuredSecret,
            @Value("${auth.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        this.configuredSecret = configuredSecret;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(configuredSecret);
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException("secret too short");
            }
        } catch (RuntimeException ignored) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public AccessTokenResult issueAccessToken(AppUser user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(accessTokenMinutes);
        long expiresIn = expiresAt.toEpochSecond() - now.toEpochSecond();

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(expiresAt.toInstant()))
                .signWith(secretKey)
                .compact();

        return new AccessTokenResult(token, expiresAt, expiresIn);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!ACCESS_TOKEN_TYPE.equals(type)) {
            throw new UnauthorizedException("Invalid token type");
        }

        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        if (subject == null || email == null) {
            throw new UnauthorizedException("Invalid access token");
        }

        try {
            return new AuthenticatedUser(UUID.fromString(subject), email);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid access token subject");
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired access token");
        }
    }

}
