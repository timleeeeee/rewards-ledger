# Rewards Ledger Service

Production-style Java Spring Boot service implementing an append-only Rewards & Points ledger with PostgreSQL.

## Stack
- Java 21
- Spring Boot 3.5.x
- PostgreSQL
- Flyway
- JUnit 5 + Testcontainers
- Docker / Docker Compose
- GitHub Actions

## Features
- Append-only transactions (`ledger_transactions`) as source of truth
- Derived balance via transaction aggregation
- Strict idempotency for write operations using `Idempotency-Key`
- Concurrency-safe spend/transfer using pessimistic account row locks
- API key auth for write endpoints (`X-API-Key`)
- Correlation/request ID (`X-Request-Id`) in logs and response headers
- Cursor-based transaction pagination
- Consistent JSON error format
- Health endpoint with app status, app version, and DB status

## API
- `POST /accounts`
- `GET /accounts/{id}`
- `POST /accounts/{id}/earn`
- `POST /accounts/{id}/spend`
- `POST /accounts/{id}/reversal`
- `POST /transfer`
- `GET /accounts/{id}/transactions?limit=50&cursor=...`
- `GET /health`

Write endpoints require headers:
- `X-API-Key`
- `Idempotency-Key`

## Local Run With Docker Compose
1. Create local env file:
```bash
copy .env.example .env
```

2. Start services:
```bash
docker compose up -d --build
```

3. Verify health:
```bash
curl http://localhost:8080/health
```

4. Stop services:
```bash
docker compose down
```

Flyway migrations run automatically on application startup.

## Local Run Without Docker
1. Start PostgreSQL:
```bash
docker run --name rewards-postgres -e POSTGRES_DB=rewards -e POSTGRES_USER=rewards -e POSTGRES_PASSWORD=rewards -p 5432:5432 -d postgres:16-alpine
```

2. Set environment variables:
```bash
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/rewards
set SPRING_DATASOURCE_USERNAME=rewards
set SPRING_DATASOURCE_PASSWORD=rewards
set API_KEY=dev-api-key
set APP_VERSION=0.0.1
set SERVER_PORT=8080
```

3. Run application:
```bash
gradlew.bat bootRun
```

## Running Tests
```bash
gradlew.bat test
```

Test strategy in CI/local is Testcontainers with PostgreSQL (single DB strategy). Docker must be running for integration tests to execute.

## CI Overview
Workflow: `.github/workflows/ci.yml`
- Checkout repository
- Setup Java 21 (Temurin) with Gradle dependency cache
- Run test suite (`./gradlew test`)
- Build artifact (`./gradlew bootJar`)

Pipeline fails immediately on test failures.

## Optional VM Deploy (SSH)
Files:
- `deploy.sh`
- `.github/workflows/deploy.yml`

Deployment workflow behavior:
- Trigger manually via `workflow_dispatch`
- Syncs project files to VM using SSH/rsync
- Writes runtime `.env` from GitHub Secrets
- Executes `deploy.sh` on VM
- `deploy.sh` runs idempotent deployment:
  - `docker compose pull || true`
  - `docker compose up -d --build --remove-orphans`
  - safe image prune

### Required GitHub Secrets For Deploy
- `VM_SSH_HOST`
- `VM_SSH_PORT` (example: `22`)
- `VM_SSH_USER`
- `VM_SSH_PRIVATE_KEY`
- `VM_APP_DIR` (example: `/opt/rewards-ledger`)
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `API_KEY`
- `SERVER_PORT`

## Observability
- Request IDs are logged and returned via `X-Request-Id`
- Logs are console-friendly for `docker logs`
- `/health` returns status + version + database status

## Notes on Correctness
- Transactions are never updated/deleted.
- Account row lock (`PESSIMISTIC_WRITE`) serializes competing balance-changing operations.
- Idempotency replay returns existing transaction result if payload hash matches.
- Reusing an idempotency key with different payload returns `409 IDEMPOTENCY_CONFLICT`.
- Transfer is atomic: both OUT and IN entries are persisted in one DB transaction.
