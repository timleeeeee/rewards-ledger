package com.example.rewards.api;

import com.example.rewards.auth.AuthRequestAttributes;
import com.example.rewards.ledger.LedgerService;
import jakarta.servlet.http.HttpServletRequest;
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
            @Valid @RequestBody AmountRequest request,
            HttpServletRequest httpRequest
    ) {
        return ledgerService.earn(AuthRequestAttributes.requireUserId(httpRequest), accountId, request, idempotencyKey);
    }

    @PostMapping("/{id}/spend")
    public TransactionResponse spend(
            @PathVariable("id") UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AmountRequest request,
            HttpServletRequest httpRequest
    ) {
        return ledgerService.spend(AuthRequestAttributes.requireUserId(httpRequest), accountId, request, idempotencyKey);
    }

    @PostMapping("/{id}/reversal")
    public TransactionResponse reversal(
            @PathVariable("id") UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReversalRequest request,
            HttpServletRequest httpRequest
    ) {
        return ledgerService.reversal(AuthRequestAttributes.requireUserId(httpRequest), accountId, request, idempotencyKey);
    }
}
