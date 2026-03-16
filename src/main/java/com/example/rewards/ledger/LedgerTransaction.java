package com.example.rewards.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryDirection direction;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "related_account_id")
    private UUID relatedAccountId;

    @Column(name = "reference_transaction_id")
    private UUID referenceTransactionId;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(
            UUID id,
            UUID accountId,
            TransactionType type,
            EntryDirection direction,
            long amount,
            String currency,
            String idempotencyKey,
            UUID relatedAccountId,
            UUID referenceTransactionId,
            String reason,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.relatedAccountId = relatedAccountId;
        this.referenceTransactionId = referenceTransactionId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getRelatedAccountId() {
        return relatedAccountId;
    }

    public UUID getReferenceTransactionId() {
        return referenceTransactionId;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}