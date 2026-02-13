package com.example.rewards.account;

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
    private final LedgerTransactionRepository ledgerTransactionRepository;

    public AccountService(AccountRepository accountRepository, LedgerTransactionRepository ledgerTransactionRepository) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
    }

    @Transactional
    public CreateAccountResponse createAccount() {
        Account account = new Account(
                UUID.randomUUID(),
                AccountStatus.ACTIVE,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        accountRepository.save(account);
        return new CreateAccountResponse(account.getId(), account.getStatus(), account.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        long balance = ledgerTransactionRepository.calculateBalance(accountId);
        return new AccountResponse(account.getId(), account.getStatus(), balance, account.getCreatedAt());
    }
}