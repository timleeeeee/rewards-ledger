package com.example.rewards.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {
    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);
    List<RefreshTokenSession> findByUserIdAndRevokedAtIsNull(UUID userId);
}
