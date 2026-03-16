package com.example.rewards.integration;

import com.example.rewards.api.AmountRequest;
import com.example.rewards.api.AuthResponse;
import com.example.rewards.api.CreateAccountResponse;
import com.example.rewards.api.ReversalRequest;
import com.example.rewards.api.TransactionPageResponse;
import com.example.rewards.api.TransactionResponse;
import com.example.rewards.auth.AppUserRepository;
import com.example.rewards.auth.RefreshTokenSessionRepository;
import com.example.rewards.auth.TokenHashService;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerIntegrationTest {

    private static final String API_KEY = "integration-api-key";
    private static final String PASSWORD = "Password123!";

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
        registry.add("auth.jwt.secret", () -> "integration-jwt-secret-at-least-32-bytes-long");
        registry.add("rate.limit.login.per-minute", () -> 3);
        registry.add("rate.limit.register.per-minute", () -> 100);
        registry.add("rate.limit.refresh.per-minute", () -> 100);
        registry.add("rate.limit.write.per-minute", () -> 200);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Autowired
    private TokenHashService tokenHashService;

    @LocalServerPort
    private int port;

    @Test
    void earnRetryWithSameIdempotencyKeyReturnsSameTransaction() {
        AuthResponse auth = registerUser();
        UUID accountId = createAccount(auth.accessToken());

        HttpHeaders headers = writeHeaders(auth.accessToken(), "same-key");
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
        AuthResponse auth = registerUser();
        UUID accountId = createAccount(auth.accessToken());

        HttpHeaders headers = writeHeaders(auth.accessToken(), "dup-key");

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
        AuthResponse auth = registerUser();
        UUID accountId = createAccount(auth.accessToken());
        ledgerService.earn(auth.user().id(), accountId, new AmountRequest(100L, "seed", "PTS"), "seed-key");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> spendA = () -> trySpend(auth.user().id(), accountId, "spend-a");
        Callable<Boolean> spendB = () -> trySpend(auth.user().id(), accountId, "spend-b");

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
        AuthResponse auth = registerUser();
        UUID fromAccountId = createAccount(auth.accessToken());
        UUID toAccountId = createAccount(auth.accessToken());

        HttpHeaders headers = writeHeaders(auth.accessToken(), "transfer-no-funds");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                "fromAccountId", fromAccountId,
                "toAccountId", toAccountId,
                "amount", 50L,
                "reason", "atomicity-check"
        ), headers);

        ResponseEntity<String> response = restTemplate.exchange(url("/transfer"), HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        TransactionPageResponse fromHistory = ledgerService.getTransactions(auth.user().id(), fromAccountId, 50, null);
        TransactionPageResponse toHistory = ledgerService.getTransactions(auth.user().id(), toAccountId, 50, null);

        assertThat(fromHistory.items()).isEmpty();
        assertThat(toHistory.items()).isEmpty();
    }

    @Test
    void reversalRulesAreEnforced() {
        AuthResponse auth = registerUser();
        UUID accountId = createAccount(auth.accessToken());

        TransactionResponse earn = ledgerService.earn(
                auth.user().id(),
                accountId,
                new AmountRequest(70L, "seed", "PTS"),
                "earn-for-reversal"
        );

        TransactionResponse reversal = ledgerService.reversal(
                auth.user().id(),
                accountId,
                new ReversalRequest(earn.id(), "reversal-once"),
                "reverse-once"
        );

        assertThat(reversal.referenceTransactionId()).isEqualTo(earn.id());

        assertThrows(BadRequestException.class, () -> ledgerService.reversal(
                auth.user().id(),
                accountId,
                new ReversalRequest(earn.id(), "reversal-twice"),
                "reverse-twice"
        ));

        assertThrows(BadRequestException.class, () -> ledgerService.reversal(
                auth.user().id(),
                accountId,
                new ReversalRequest(reversal.id(), "reverse-a-reversal"),
                "reverse-reversal"
        ));
    }

    @Test
    void protectedEndpointWithoutAuthReturnsStandardJsonShape() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/accounts"),
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getBody()).contains("\"message\"");
        assertThat(response.getBody()).contains("\"requestId\"");
        assertThat(response.getBody()).contains("\"timestamp\"");
    }

    @Test
    void writeEndpointWithoutApiKeyReturnsUnauthorizedJsonShape() {
        AuthResponse auth = registerUser();
        UUID accountId = createAccount(auth.accessToken());

        HttpHeaders headers = bearerHeaders(auth.accessToken());
        headers.add("Idempotency-Key", "no-api-key");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 10L), headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getBody()).contains("\"requestId\"");
    }

    @Test
    void ownershipIsolationIsEnforcedAcrossUsers() {
        AuthResponse owner = registerUser();
        UUID accountId = createAccount(owner.accessToken());

        AuthResponse attacker = registerUser();

        ResponseEntity<String> readAttempt = restTemplate.exchange(
                url("/accounts/" + accountId),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(attacker.accessToken())),
                String.class
        );
        assertThat(readAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readAttempt.getBody()).contains("\"code\":\"NOT_FOUND\"");

        ResponseEntity<String> writeAttempt = restTemplate.exchange(
                url("/accounts/" + accountId + "/earn"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 10L), writeHeaders(attacker.accessToken(), "attacker-earn")),
                String.class
        );
        assertThat(writeAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(writeAttempt.getBody()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void refreshTokenRotationWorksAndReuseIsRejected() {
        AuthResponse auth = registerUser();
        String oldRefresh = auth.refreshToken();

        ResponseEntity<AuthResponse> rotated = restTemplate.postForEntity(
                url("/auth/refresh"),
                Map.of("refreshToken", oldRefresh),
                AuthResponse.class
        );
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rotated.getBody()).isNotNull();
        String newRefresh = rotated.getBody().refreshToken();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        ResponseEntity<String> replay = restTemplate.postForEntity(
                url("/auth/refresh"),
                Map.of("refreshToken", oldRefresh),
                String.class
        );
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void passwordAndRefreshTokenAreStoredHashed() {
        AuthResponse auth = registerUser();

        var storedUser = appUserRepository.findById(auth.user().id()).orElseThrow();
        assertThat(storedUser.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(storedUser.getPasswordHash()).startsWith("$2");

        String rawRefreshToken = auth.refreshToken();
        assertThat(refreshTokenSessionRepository.findByTokenHash(rawRefreshToken)).isEmpty();

        String hashedRefreshToken = tokenHashService.sha256Hex(rawRefreshToken);
        assertThat(refreshTokenSessionRepository.findByTokenHash(hashedRefreshToken)).isPresent();
    }

    @Test
    void loginRateLimitReturns429() {
        AuthResponse auth = registerUser();

        ResponseEntity<String> last = null;
        for (int i = 0; i < 4; i++) {
            last = restTemplate.postForEntity(
                    url("/auth/login"),
                    Map.of("email", auth.user().email(), "password", "wrong-password"),
                    String.class
            );
        }

        assertThat(last).isNotNull();
        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getBody()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void requestIdIsEchoedBackInResponseHeader() {
        AuthResponse auth = registerUser();
        UUID accountId = createAccount(auth.accessToken());

        HttpHeaders headers = writeHeaders(auth.accessToken(), "request-id-test");
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

    private boolean trySpend(UUID userId, UUID accountId, String key) {
        try {
            ledgerService.spend(userId, accountId, new AmountRequest(80L, "concurrent", "PTS"), key);
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

    private AuthResponse registerUser() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/auth/register"),
                Map.of("email", email, "password", PASSWORD),
                AuthResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private UUID createAccount(String accessToken) {
        HttpHeaders headers = bearerHeaders(accessToken);
        ResponseEntity<CreateAccountResponse> response = restTemplate.exchange(
                url("/accounts"),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                CreateAccountResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().id();
    }

    private HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return headers;
    }

    private HttpHeaders writeHeaders(String accessToken, String idempotencyKey) {
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.add("X-API-Key", API_KEY);
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
