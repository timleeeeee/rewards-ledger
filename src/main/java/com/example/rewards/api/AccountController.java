package com.example.rewards.api;

import com.example.rewards.account.AccountService;
import com.example.rewards.auth.AuthRequestAttributes;
import com.example.rewards.ledger.LedgerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@Validated
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public AccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public CreateAccountResponse createAccount(HttpServletRequest request) {
        return accountService.createAccount(AuthRequestAttributes.requireUserId(request));
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable("id") UUID accountId, HttpServletRequest request) {
        return accountService.getAccount(AuthRequestAttributes.requireUserId(request), accountId);
    }

    @GetMapping("/{id}/transactions")
    public TransactionPageResponse getTransactions(
            @PathVariable("id") UUID accountId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request
    ) {
        return ledgerService.getTransactions(AuthRequestAttributes.requireUserId(request), accountId, limit, cursor);
    }
}
