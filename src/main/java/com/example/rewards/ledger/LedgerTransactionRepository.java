package com.example.rewards.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    @Query(value = """
            select coalesce(sum(
                case
                    when t.direction = 'CREDIT' then t.amount
                    else -t.amount
                end
            ), 0)
            from ledger_transactions t
            where t.account_id = :accountId
            """, nativeQuery = true)
    long calculateBalance(@Param("accountId") UUID accountId);

    @Query("""
            select t from LedgerTransaction t
            where t.accountId = :accountId
            order by t.createdAt desc, t.id desc
            """)
    List<LedgerTransaction> findRecent(@Param("accountId") UUID accountId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select t from LedgerTransaction t
            where t.accountId = :accountId
              and (t.createdAt < :cursorCreatedAt
                or (t.createdAt = :cursorCreatedAt and t.id < :cursorId))
            order by t.createdAt desc, t.id desc
            """)
    List<LedgerTransaction> findRecentAfterCursor(
            @Param("accountId") UUID accountId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            org.springframework.data.domain.Pageable pageable
    );

    boolean existsByReferenceTransactionId(UUID referenceTransactionId);

    Optional<LedgerTransaction> findByIdAndAccountId(UUID id, UUID accountId);
}