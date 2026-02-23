package com.example.rewards.auth;

import com.example.rewards.api.AuthResponse;
import com.example.rewards.api.AuthUserResponse;
import com.example.rewards.api.LoginRequest;
import com.example.rewards.api.LogoutRequest;
import com.example.rewards.api.RefreshTokenRequest;
import com.example.rewards.api.RegisterRequest;
import com.example.rewards.common.ConflictException;
import com.example.rewards.common.UnauthorizedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final TokenHashService tokenHashService;
    private final long refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AppUserRepository appUserRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            TokenHashService tokenHashService,
            @Value("${auth.jwt.refresh-token-days}") long refreshTokenDays
    ) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.tokenHashService = tokenHashService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Email is already registered");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AppUser user = new AppUser(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                now
        );
        try {
            appUserRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Email is already registered");
        }

        SessionIssue issue = createSession(user, now);
        refreshTokenSessionRepository.save(issue.session());
        return toAuthResponse(user, issue);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SessionIssue issue = createSession(user, now);
        refreshTokenSessionRepository.save(issue.session());
        return toAuthResponse(user, issue);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawRefreshToken = request.refreshToken().trim();
        String hash = tokenHashService.sha256Hex(rawRefreshToken);

        RefreshTokenSession currentSession = refreshTokenSessionRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        AppUser user = appUserRepository.findById(currentSession.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found for token"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (currentSession.isRevoked()) {
            revokeAllActiveUserSessions(user.getId(), now);
            throw new UnauthorizedException("Refresh token reuse detected");
        }

        if (currentSession.isExpired(now)) {
            currentSession.markRevoked(now);
            refreshTokenSessionRepository.save(currentSession);
            throw new UnauthorizedException("Refresh token expired");
        }

        SessionIssue issue = createSession(user, now);
        currentSession.markRotated(issue.session().getId(), now);
        refreshTokenSessionRepository.save(currentSession);
        refreshTokenSessionRepository.save(issue.session());
        return toAuthResponse(user, issue);
    }

    @Transactional
    public void logout(UUID userId, LogoutRequest request) {
        String hash = tokenHashService.sha256Hex(request.refreshToken().trim());
        refreshTokenSessionRepository.findByTokenHash(hash).ifPresent(session -> {
            if (session.getUserId().equals(userId) && !session.isRevoked()) {
                session.markRevoked(OffsetDateTime.now(ZoneOffset.UTC));
                refreshTokenSessionRepository.save(session);
            }
        });
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return toUserResponse(user);
    }

    private SessionIssue createSession(AppUser user, OffsetDateTime now) {
        String refreshToken = generateRefreshToken();
        String tokenHash = tokenHashService.sha256Hex(refreshToken);
        RefreshTokenSession session = new RefreshTokenSession(
                UUID.randomUUID(),
                user.getId(),
                tokenHash,
                now.plusDays(refreshTokenDays),
                null,
                null,
                now,
                null
        );

        AccessTokenResult accessToken = authTokenService.issueAccessToken(user);
        return new SessionIssue(session, refreshToken, accessToken);
    }

    private void revokeAllActiveUserSessions(UUID userId, OffsetDateTime now) {
        List<RefreshTokenSession> activeSessions = refreshTokenSessionRepository.findByUserIdAndRevokedAtIsNull(userId);
        for (RefreshTokenSession activeSession : activeSessions) {
            activeSession.markRevoked(now);
        }
        refreshTokenSessionRepository.saveAll(activeSessions);
    }

    private AuthResponse toAuthResponse(AppUser user, SessionIssue issue) {
        return new AuthResponse(
                issue.accessToken().token(),
                issue.accessToken().expiresInSeconds(),
                issue.rawRefreshToken(),
                toUserResponse(user)
        );
    }

    private AuthUserResponse toUserResponse(AppUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record SessionIssue(
            RefreshTokenSession session,
            String rawRefreshToken,
            AccessTokenResult accessToken
    ) {
    }
}
