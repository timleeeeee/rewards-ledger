package com.example.rewards.api;

import com.example.rewards.ledger.LedgerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/{id}/earn")
    public TransactionResponse earn(
            @PathVariable("id") UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AmountRequest request
    ) {
        return ledgerService.earn(accountId, request, idempotencyKey);
    }

    @PostMapping("/{id}/spend")
    public TransactionResponse spend(
            @PathVariable("id") UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AmountRequest request
    ) {
        return ledgerService.spend(accountId, request, idempotencyKey);
    }

    @PostMapping("/{id}/reversal")
    public TransactionResponse reversal(
            @PathVariable("id") UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReversalRequest request
    ) {
        return ledgerService.reversal(accountId, request, idempotencyKey);
    }
}