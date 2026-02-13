package com.example.rewards.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String operation;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "primary_transaction_id")
    private UUID primaryTransactionId;

    @Column(name = "secondary_transaction_id")
    private UUID secondaryTransactionId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(
            UUID id,
            UUID accountId,
            String operation,
            String idempotencyKey,
            String requestHash,
            UUID primaryTransactionId,
            UUID secondaryTransactionId,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.primaryTransactionId = primaryTransactionId;
        this.secondaryTransactionId = secondaryTransactionId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getPrimaryTransactionId() {
        return primaryTransactionId;
    }

    public UUID getSecondaryTransactionId() {
        return secondaryTransactionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}