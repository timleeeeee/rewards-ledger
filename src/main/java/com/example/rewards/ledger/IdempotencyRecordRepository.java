package com.example.rewards.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByAccountIdAndOperationAndIdempotencyKey(
            UUID accountId,
            String operation,
            String idempotencyKey
    );
}