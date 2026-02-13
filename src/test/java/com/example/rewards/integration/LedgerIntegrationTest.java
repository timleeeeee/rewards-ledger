package com.example.rewards.integration;

import com.example.rewards.account.AccountService;
import com.example.rewards.api.AmountRequest;
import com.example.rewards.api.CreateAccountResponse;
import com.example.rewards.api.ReversalRequest;
import com.example.rewards.api.TransactionPageResponse;
import com.example.rewards.api.TransactionResponse;
import com.example.rewards.common.BadRequestException;
import com.example.rewards.common.InsufficientFundsException;
import com.example.rewards.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerIntegrationTest {

    private static final String API_KEY = "integration-api-key";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rewards")
            .withUsername("rewards")
            .withPassword("rewards");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("api.key", () -> API_KEY);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LedgerService ledgerService;

    @LocalServerPort
    private int port;

    @Test
    void earnRetryWithSameIdempotencyKeyReturnsSameTransaction() {
        UUID accountId = accountService.createAccount().id();

        HttpHeaders headers = authHeaders("same-key");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("amount", 100L, "reason", "signup"), headers);

        ResponseEntity<TransactionResponse> first = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                entity,
                TransactionResponse.class
        );

        ResponseEntity<TransactionResponse> second = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                entity,
                TransactionResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void earnRetryWithDifferentPayloadReturnsConflict() {
        UUID accountId = accountService.createAccount().id();

        HttpHeaders headers = authHeaders("dup-key");

        ResponseEntity<String> first = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 100L), headers),
                String.class
        );

        ResponseEntity<String> conflict = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 200L), headers),
                String.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void concurrentSpendsCannotBothSucceedWhenFundsInsufficient() throws Exception {
        CreateAccountResponse account = accountService.createAccount();
        ledgerService.earn(account.id(), new AmountRequest(100L, "seed", "PTS"), "seed-key");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> spendA = () -> trySpend(account.id(), "spend-a");
        Callable<Boolean> spendB = () -> trySpend(account.id(), "spend-b");

        List<Future<Boolean>> futures = executor.invokeAll(List.of(spendA, spendB));
        executor.shutdown();

        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(getFuture(future));
        }

        long successCount = results.stream().filter(Boolean::booleanValue).count();
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void transferWithInsufficientFundsIsAtomicAndCreatesNoEntries() {
        UUID fromAccountId = accountService.createAccount().id();
        UUID toAccountId = accountService.createAccount().id();

        HttpHeaders headers = authHeaders("transfer-no-funds");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                "fromAccountId", fromAccountId,
                "toAccountId", toAccountId,
                "amount", 50L,
                "reason", "atomicity-check"
        ), headers);

        ResponseEntity<String> response = restTemplate.exchange(url("/transfer"), HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        TransactionPageResponse fromHistory = ledgerService.getTransactions(fromAccountId, 50, null);
        TransactionPageResponse toHistory = ledgerService.getTransactions(toAccountId, 50, null);

        assertThat(fromHistory.items()).isEmpty();
        assertThat(toHistory.items()).isEmpty();
    }

    @Test
    void reversalRulesAreEnforced() {
        UUID accountId = accountService.createAccount().id();
        TransactionResponse earn = ledgerService.earn(accountId, new AmountRequest(70L, "seed", "PTS"), "earn-for-reversal");

        TransactionResponse reversal = ledgerService.reversal(
                accountId,
                new ReversalRequest(earn.id(), "reversal-once"),
                "reverse-once"
        );

        assertThat(reversal.referenceTransactionId()).isEqualTo(earn.id());

        assertThrows(BadRequestException.class, () -> ledgerService.reversal(
                accountId,
                new ReversalRequest(earn.id(), "reversal-twice"),
                "reverse-twice"
        ));

        assertThrows(BadRequestException.class, () -> ledgerService.reversal(
                accountId,
                new ReversalRequest(reversal.id(), "reverse-a-reversal"),
                "reverse-reversal"
        ));
    }

    @Test
    void authErrorResponseHasStandardJsonShape() {
        UUID accountId = accountService.createAccount().id();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", "bad-auth");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 10L), headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getBody()).contains("\"message\"");
        assertThat(response.getBody()).contains("\"requestId\"");
        assertThat(response.getBody()).contains("\"timestamp\"");
    }

    @Test
    void requestIdIsEchoedBackInResponseHeader() {
        UUID accountId = accountService.createAccount().id();
        HttpHeaders headers = authHeaders("request-id-test");
        headers.add("X-Request-Id", "custom-request-id-123");

        ResponseEntity<TransactionResponse> response = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 25L), headers),
                TransactionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("custom-request-id-123");
    }

    @Test
    void healthEndpointIncludesDatabaseStatusAndVersion() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/health"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("database");
        assertThat(response.getBody()).contains("version");
    }

    private boolean trySpend(UUID accountId, String key) {
        try {
            ledgerService.spend(accountId, new AmountRequest(80L, "concurrent", "PTS"), key);
            return true;
        } catch (InsufficientFundsException ex) {
            return false;
        }
    }

    private boolean getFuture(Future<Boolean> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InsufficientFundsException) {
                return false;
            }
            throw new RuntimeException(cause);
        }
    }

    private HttpHeaders authHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-API-Key", API_KEY);
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
