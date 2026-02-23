package com.example.rewards.api;

import com.example.rewards.auth.AuthRequestAttributes;
import com.example.rewards.ledger.LedgerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TransferController {

    private final LedgerService ledgerService;

    public TransferController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transfer")
    public List<TransactionResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            HttpServletRequest httpRequest
    ) {
        return ledgerService.transfer(AuthRequestAttributes.requireUserId(httpRequest), request, idempotencyKey);
    }
}
