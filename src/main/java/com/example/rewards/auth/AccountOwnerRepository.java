package com.example.rewards.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountOwnerRepository extends JpaRepository<AccountOwner, UUID> {
    boolean existsByAccountIdAndUserId(UUID accountId, UUID userId);
    List<AccountOwner> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
