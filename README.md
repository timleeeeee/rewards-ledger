# Rewards Ledger Platform

Production-style full-stack portfolio project for a rewards and points ledger.

## Architecture
- `backend`: Java 21 + Spring Boot 3.5 API (append-only ledger, idempotency, concurrency safety)
- `frontend`: React + TypeScript (Wallet + Ops/Debug demo console)
- `postgres`: PostgreSQL 16 with Flyway-managed migrations

## Tech
- Java 21, Spring Boot, JPA, Flyway, PostgreSQL
- React, TypeScript, Vite, Vitest
- Docker, Docker Compose
- GitHub Actions (CI + optional VM deploy)

## Backend API (summary)
- `POST /accounts`
- `GET /accounts/{id}`
- `POST /accounts/{id}/earn`
- `POST /accounts/{id}/spend`
- `POST /accounts/{id}/reversal`
- `POST /transfer`
- `GET /accounts/{id}/transactions?limit=...&cursor=...`
- `GET /health`

Write endpoints require:
- `X-API-Key`
- `Idempotency-Key`

## Frontend Demo Scope
Two tabs are included:

1. Wallet
- create/select account
- view balance and paginated transaction history
- earn, spend, transfer actions

2. Ops / Debug
- reversal by original transaction ID
- idempotency replay demo (same key twice)
- insufficient funds demo (structured error)
- request trace panel (`X-Request-Id`, status, payload)
- health panel (`/health` status/version/db)

## Local Run (full stack)
1. Create env file:
```bash
copy .env.example .env
```

2. Start all services:
```bash
docker compose up -d --build --wait
```

3. Open frontend:
- `http://localhost:3000`

4. Optional backend health check:
```bash
curl http://localhost:8080/health
```

5. Stop services:
```bash
docker compose down
```

Flyway migrations run automatically on backend startup.

## Frontend Dev Mode (optional)
```bash
cd frontend
npm ci
npm run dev
```
Vite dev server runs on `http://localhost:5173` and proxies `/api` to backend `http://localhost:8080`.

## Tests
Backend:
```bash
gradlew.bat test
```

Frontend:
```bash
cd frontend
npm run test
npm run build
```

## CI
Workflow: `.github/workflows/ci.yml`
- Backend job: Gradle test + bootJar
- Frontend job: npm ci + test + build

## Optional VM Deploy (SSH)
Files:
- `deploy.sh`
- `.github/workflows/deploy.yml`

Deploy workflow:
- manual trigger (`workflow_dispatch`)
- rsync repo to VM
- write `.env` from GitHub Secrets
- run idempotent rollout (`docker compose up -d --build --remove-orphans`)

### Required GitHub Secrets for Deploy
- `VM_SSH_HOST`
- `VM_SSH_PORT`
- `VM_SSH_USER`
- `VM_SSH_PRIVATE_KEY`
- `VM_APP_DIR`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `API_KEY`
- `SERVER_PORT`
- `FRONTEND_PORT`
- `FRONTEND_API_BASE_URL`
- `FRONTEND_API_KEY`

## Environment Variables (`.env.example`)
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `API_KEY`
- `SERVER_PORT`
- `APP_VERSION`
- `FRONTEND_PORT`
- `FRONTEND_API_BASE_URL`
- `FRONTEND_API_KEY`

## Observability
- `X-Request-Id` is accepted/returned by backend
- request IDs are logged per request
- `/health` returns application + database status

## Correctness Highlights
- append-only ledger as source of truth
- idempotency replay/conflict protection
- pessimistic locking to prevent double-spend
- atomic transfer write behavior
- reversal validation rules
