package com.example.rewards.account;

import com.example.rewards.auth.AccountOwner;
import com.example.rewards.auth.AccountOwnerRepository;
import com.example.rewards.api.AccountResponse;
import com.example.rewards.api.CreateAccountResponse;
import com.example.rewards.common.NotFoundException;
import com.example.rewards.ledger.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountOwnerRepository accountOwnerRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    public AccountService(
            AccountRepository accountRepository,
            AccountOwnerRepository accountOwnerRepository,
            LedgerTransactionRepository ledgerTransactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountOwnerRepository = accountOwnerRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
    }

    @Transactional
    public CreateAccountResponse createAccount(UUID ownerUserId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Account account = new Account(
                UUID.randomUUID(),
                AccountStatus.ACTIVE,
                now
        );

        accountRepository.save(account);
        accountOwnerRepository.save(new AccountOwner(account.getId(), ownerUserId, now));
        return new CreateAccountResponse(account.getId(), account.getStatus(), account.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID ownerUserId, UUID accountId) {
        assertOwned(ownerUserId, accountId);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        long balance = ledgerTransactionRepository.calculateBalance(accountId);
        return new AccountResponse(account.getId(), account.getStatus(), balance, account.getCreatedAt());
    }

    private void assertOwned(UUID ownerUserId, UUID accountId) {
        if (!accountOwnerRepository.existsByAccountIdAndUserId(accountId, ownerUserId)) {
            throw new NotFoundException("Account not found");
        }
    }
}
