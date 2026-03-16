package com.example.rewards.ledger;

import com.example.rewards.account.Account;
import com.example.rewards.account.AccountRepository;
import com.example.rewards.account.AccountStatus;
import com.example.rewards.auth.AccountOwnerRepository;
import com.example.rewards.api.AmountRequest;
import com.example.rewards.api.ReversalRequest;
import com.example.rewards.api.TransactionPageResponse;
import com.example.rewards.api.TransactionResponse;
import com.example.rewards.api.TransferRequest;
import com.example.rewards.common.BadRequestException;
import com.example.rewards.common.CursorUtil;
import com.example.rewards.common.IdempotencyConflictException;
import com.example.rewards.common.InsufficientFundsException;
import com.example.rewards.common.NotFoundException;
import com.example.rewards.common.RequestHashUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LedgerService {

    private static final String DEFAULT_CURRENCY = "PTS";
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final AccountOwnerRepository accountOwnerRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final RequestHashUtil requestHashUtil;

    public LedgerService(
            AccountRepository accountRepository,
            AccountOwnerRepository accountOwnerRepository,
            LedgerTransactionRepository transactionRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            RequestHashUtil requestHashUtil
    ) {
        this.accountRepository = accountRepository;
        this.accountOwnerRepository = accountOwnerRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.requestHashUtil = requestHashUtil;
    }

    @Transactional
    public TransactionResponse earn(UUID userId, UUID accountId, AmountRequest request, String idempotencyKey) {
        assertOwned(userId, accountId);
        String requestHash = requestHashUtil.hash(request);
        OperationResult result = processIdempotent(accountId, "EARN", idempotencyKey, requestHash, () -> {
            Account account = lockActiveAccount(accountId);
            LedgerTransaction tx = buildTransaction(
                    account.getId(),
                    TransactionType.EARN,
                    EntryDirection.CREDIT,
                    request.amount(),
                    normalizeCurrency(request.currency()),
                    idempotencyKey,
                    null,
                    null,
                    request.reason()
            );
            transactionRepository.save(tx);
            return new OperationResult(tx.getId(), null);
        });

        return toResponse(loadTransaction(result.primaryTransactionId()));
    }

    @Transactional
    public TransactionResponse spend(UUID userId, UUID accountId, AmountRequest request, String idempotencyKey) {
        assertOwned(userId, accountId);
        String requestHash = requestHashUtil.hash(request);
        OperationResult result = processIdempotent(accountId, "SPEND", idempotencyKey, requestHash, () -> {
            Account account = lockActiveAccount(accountId);
            long balance = transactionRepository.calculateBalance(account.getId());
            if (balance < request.amount()) {
                throw new InsufficientFundsException("Insufficient balance for spend");
            }

            LedgerTransaction tx = buildTransaction(
                    account.getId(),
                    TransactionType.SPEND,
                    EntryDirection.DEBIT,
                    request.amount(),
                    normalizeCurrency(request.currency()),
                    idempotencyKey,
                    null,
                    null,
                    request.reason()
            );
            transactionRepository.save(tx);
            return new OperationResult(tx.getId(), null);
        });

        return toResponse(loadTransaction(result.primaryTransactionId()));
    }

    @Transactional
    public TransactionResponse reversal(UUID userId, UUID accountId, ReversalRequest request, String idempotencyKey) {
        assertOwned(userId, accountId);
        String requestHash = requestHashUtil.hash(request);
        OperationResult result = processIdempotent(accountId, "REVERSAL", idempotencyKey, requestHash, () -> {
            lockActiveAccount(accountId);
            LedgerTransaction original = transactionRepository.findByIdAndAccountId(request.originalTransactionId(), accountId)
                    .orElseThrow(() -> new NotFoundException("Original transaction not found for account"));

            if (original.getType() == TransactionType.REVERSAL) {
                throw new BadRequestException("Cannot reverse a reversal transaction");
            }

            if (transactionRepository.existsByReferenceTransactionId(original.getId())) {
                throw new BadRequestException("Transaction already reversed");
            }

            EntryDirection reverseDirection = original.getDirection() == EntryDirection.CREDIT
                    ? EntryDirection.DEBIT
                    : EntryDirection.CREDIT;

            LedgerTransaction reversalTx = buildTransaction(
                    accountId,
                    TransactionType.REVERSAL,
                    reverseDirection,
                    original.getAmount(),
                    original.getCurrency(),
                    idempotencyKey,
                    original.getRelatedAccountId(),
                    original.getId(),
                    request.reason()
            );

            transactionRepository.save(reversalTx);
            return new OperationResult(reversalTx.getId(), null);
        });

        return toResponse(loadTransaction(result.primaryTransactionId()));
    }

    @Transactional
    public List<TransactionResponse> transfer(UUID userId, TransferRequest request, String idempotencyKey) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BadRequestException("fromAccountId and toAccountId must be different");
        }
        assertOwned(userId, request.fromAccountId());
        assertOwned(userId, request.toAccountId());

        String requestHash = requestHashUtil.hash(request);
        OperationResult result = processIdempotent(request.fromAccountId(), "TRANSFER", idempotencyKey, requestHash, () -> {
            List<Account> lockedAccounts = lockTwoActiveAccounts(request.fromAccountId(), request.toAccountId());
            Account fromAccount = lockedAccounts.get(0).getId().equals(request.fromAccountId())
                    ? lockedAccounts.get(0)
                    : lockedAccounts.get(1);
            Account toAccount = lockedAccounts.get(0).getId().equals(request.toAccountId())
                    ? lockedAccounts.get(0)
                    : lockedAccounts.get(1);

            long fromBalance = transactionRepository.calculateBalance(fromAccount.getId());
            if (fromBalance < request.amount()) {
                throw new InsufficientFundsException("Insufficient balance for transfer");
            }

            String currency = normalizeCurrency(request.currency());

            LedgerTransaction transferOut = buildTransaction(
                    fromAccount.getId(),
                    TransactionType.TRANSFER_OUT,
                    EntryDirection.DEBIT,
                    request.amount(),
                    currency,
                    idempotencyKey,
                    toAccount.getId(),
                    null,
                    request.reason()
            );

            LedgerTransaction transferIn = buildTransaction(
                    toAccount.getId(),
                    TransactionType.TRANSFER_IN,
                    EntryDirection.CREDIT,
                    request.amount(),
                    currency,
                    idempotencyKey,
                    fromAccount.getId(),
                    null,
                    request.reason()
            );

            transactionRepository.save(transferOut);
            transactionRepository.save(transferIn);

            return new OperationResult(transferOut.getId(), transferIn.getId());
        });

        List<TransactionResponse> responses = new ArrayList<>();
        responses.add(toResponse(loadTransaction(result.primaryTransactionId())));
        if (result.secondaryTransactionId() != null) {
            responses.add(toResponse(loadTransaction(result.secondaryTransactionId())));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse getTransactions(UUID userId, UUID accountId, int limit, String cursor) {
        assertOwned(userId, accountId);

        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        List<LedgerTransaction> txs;

        if (cursor == null || cursor.isBlank()) {
            txs = transactionRepository.findRecent(accountId, PageRequest.of(0, pageSize));
        } else {
            CursorUtil.Cursor decoded = CursorUtil.decode(cursor);
            txs = transactionRepository.findRecentAfterCursor(
                    accountId,
                    decoded.createdAt(),
                    decoded.id(),
                    PageRequest.of(0, pageSize)
            );
        }

        List<TransactionResponse> items = txs.stream().map(this::toResponse).toList();

        String nextCursor = null;
        if (!txs.isEmpty() && txs.size() == pageSize) {
            LedgerTransaction tail = txs.get(txs.size() - 1);
            nextCursor = CursorUtil.encode(tail.getCreatedAt(), tail.getId());
        }

        return new TransactionPageResponse(items, nextCursor);
    }

    private void assertOwned(UUID userId, UUID accountId) {
        if (!accountOwnerRepository.existsByAccountIdAndUserId(accountId, userId)) {
            throw new NotFoundException("Account not found");
        }
    }

    private OperationResult processIdempotent(
            UUID accountId,
            String operation,
            String idempotencyKey,
            String requestHash,
            java.util.function.Supplier<OperationResult> work
    ) {
        IdempotencyRecord existing = idempotencyRecordRepository
                .findByAccountIdAndOperationAndIdempotencyKey(accountId, operation, idempotencyKey)
                .orElse(null);

        if (existing != null) {
            return resolveExisting(existing, requestHash);
        }

        OperationResult result = work.get();

        IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID(),
                accountId,
                operation,
                idempotencyKey,
                requestHash,
                result.primaryTransactionId(),
                result.secondaryTransactionId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        try {
            idempotencyRecordRepository.save(record);
            return result;
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecord concurrentRecord = idempotencyRecordRepository
                    .findByAccountIdAndOperationAndIdempotencyKey(accountId, operation, idempotencyKey)
                    .orElseThrow(() -> ex);
            return resolveExisting(concurrentRecord, requestHash);
        }
    }

    private OperationResult resolveExisting(IdempotencyRecord existing, String requestHash) {
        if (!Objects.equals(existing.getRequestHash(), requestHash)) {
            throw new IdempotencyConflictException("Idempotency key already used with different payload");
        }

        return new OperationResult(existing.getPrimaryTransactionId(), existing.getSecondaryTransactionId());
    }

    private Account lockActiveAccount(UUID accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not ACTIVE");
        }
        return account;
    }

    // Lock accounts in deterministic order to avoid deadlocks when concurrent transfers involve same pair.
    private List<Account> lockTwoActiveAccounts(UUID idA, UUID idB) {
        List<UUID> sorted = List.of(idA, idB).stream().sorted(Comparator.naturalOrder()).toList();
        Account first = lockActiveAccount(sorted.get(0));
        Account second = lockActiveAccount(sorted.get(1));
        return List.of(first, second);
    }

    private LedgerTransaction loadTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    private LedgerTransaction buildTransaction(
            UUID accountId,
            TransactionType type,
            EntryDirection direction,
            long amount,
            String currency,
            String idempotencyKey,
            UUID relatedAccountId,
            UUID referenceTransactionId,
            String reason
    ) {
        return new LedgerTransaction(
                UUID.randomUUID(),
                accountId,
                type,
                direction,
                amount,
                currency,
                idempotencyKey,
                relatedAccountId,
                referenceTransactionId,
                reason,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currency;
    }

    private TransactionResponse toResponse(LedgerTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getAccountId(),
                tx.getType(),
                tx.getDirection(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getIdempotencyKey(),
                tx.getRelatedAccountId(),
                tx.getReferenceTransactionId(),
                tx.getReason(),
                tx.getCreatedAt()
        );
    }
}
