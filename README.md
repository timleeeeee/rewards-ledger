# Rewards Ledger Demo

Full-stack portfolio demo for a rewards and points ledger.
Live Demo: `https://rewards-ledger-demo.xyz`

## Architecture
- `backend`: Java 21 + Spring Boot 3.5 API (append-only ledger, idempotency, concurrency safety, auth + ownership)
- `frontend`: React + TypeScript (Wallet + Ops/Debug demo console, Nginx reverse proxy for `/api`)
- `postgres`: PostgreSQL 16 with Flyway-managed migrations

## Tech
- Java 21, Spring Boot, JPA, Flyway, PostgreSQL
- JWT access tokens + refresh rotation, BCrypt password hashing
- React, TypeScript, Vite, Vitest
- Docker, Docker Compose
- GitHub Actions (CI + optional VM deploy)

## Prerequisites
- Docker + Docker Compose
- Java 21 (optional, for backend tests)
- Node.js 20+ (optional, for frontend dev mode)

## Backend API (summary)
Auth:
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`

Ledger:
- `POST /accounts`
- `GET /accounts/{id}`
- `POST /accounts/{id}/earn`
- `POST /accounts/{id}/spend`
- `POST /accounts/{id}/reversal`
- `POST /transfer`
- `GET /accounts/{id}/transactions?limit=...&cursor=...`
- `GET /health`

Headers:
- protected routes require `Authorization: Bearer <accessToken>`
- write routes require `Idempotency-Key`
- backend write routes also require `X-API-Key` (frontend proxy injects server-side)

## Frontend Demo Scope
Two tabs are included:

1. Wallet
- register/login and logout flow
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
# Windows
copy .env.example .env
# macOS/Linux
cp .env.example .env
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
# Windows
gradlew.bat test
# macOS/Linux
./gradlew test
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

## Security Automation
- CodeQL: `.github/workflows/codeql.yml`
- Dependabot updates: `.github/dependabot.yml`

## Optional VM Deploy (SSH)
Files:
- `deploy.sh`
- `.github/workflows/deploy.yml`

Deploy workflow:
- manual trigger (`workflow_dispatch`)
- rsync repo to VM
- write `.env` from GitHub Secrets
- run idempotent rollout (`docker compose up -d --build --remove-orphans`)

## Required GitHub Secrets for Deploy
- `VM_SSH_HOST`
- `VM_SSH_PORT`
- `VM_SSH_USER`
- `VM_SSH_PRIVATE_KEY`
- `VM_APP_DIR`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `API_KEY`
- `AUTH_JWT_SECRET`
- `AUTH_JWT_ACCESS_MINUTES`
- `AUTH_JWT_REFRESH_DAYS`
- `RATE_LIMIT_LOGIN_PER_MINUTE`
- `RATE_LIMIT_REGISTER_PER_MINUTE`
- `RATE_LIMIT_REFRESH_PER_MINUTE`
- `RATE_LIMIT_WRITE_PER_MINUTE`
- `SERVER_PORT`
- `FRONTEND_PORT`
- `FRONTEND_API_BASE_URL`

## Environment Variables (`.env.example`)
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `API_KEY`
- `AUTH_JWT_SECRET`
- `AUTH_JWT_ACCESS_MINUTES`
- `AUTH_JWT_REFRESH_DAYS`
- `RATE_LIMIT_LOGIN_PER_MINUTE`
- `RATE_LIMIT_REGISTER_PER_MINUTE`
- `RATE_LIMIT_REFRESH_PER_MINUTE`
- `RATE_LIMIT_WRITE_PER_MINUTE`
- `SERVER_PORT`
- `APP_VERSION`
- `FRONTEND_PORT`
- `FRONTEND_API_BASE_URL`

## Security Notes (Demo Deployment)
- frontend does not embed write API secrets in browser code
- write routes remain API-key protected at backend and are throttled
- full auth on protected routes (`Authorization: Bearer`)
- refresh tokens are rotated and stored hashed at rest
- ownership isolation is enforced (no cross-user account access)
- backend rate limiting includes auth/login and write routes
- backend port is bound to localhost in compose (`127.0.0.1:${SERVER_PORT}`)
- PostgreSQL is internal-only (no published host port)
- max write amount guardrail is enforced (`<= 1,000,000`)
- keep `.env` and secrets out of git; rotate keys if exposed
- Public deploy: HTTPS enabled, only 80/443 exposed, backend/db private, secrets managed via GitHub Actions/VM env.

## Observability
- `X-Request-Id` is accepted/returned by backend
- request IDs are logged per request
- `/health` returns application + database status

## Correctness Coverage
- append-only ledger as source of truth
- idempotency replay/conflict protection
- pessimistic locking to prevent double-spend
- atomic transfer write behavior
- reversal validation rules
