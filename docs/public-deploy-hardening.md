# Public Deployment Hardening Runbook

Use this checklist before sharing a public demo URL.

## 1) Rotate Demo Secrets

Generate strong replacement values and store them as GitHub Secrets.

PowerShell examples:

```powershell
# 48-byte API key (base64url-like)
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 })) `
  .TrimEnd('=') `
  .Replace('+','-') `
  .Replace('/','_')

# 32-byte DB password
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

Update these GitHub Secrets:
- `API_KEY`
- `SPRING_DATASOURCE_PASSWORD`

## 2) Enforce Public Exposure Rules

At VM/cloud firewall/security-group level:
- allow `22/tcp` from your admin IP (or trusted range)
- allow `80/tcp` and `443/tcp` from internet
- block `8080/tcp` and `5432/tcp` from internet

## 3) Enable HTTPS/TLS

Use your reverse proxy / edge provider to terminate TLS:
- point domain DNS to VM/public endpoint
- issue certificate (Let's Encrypt or managed cert)
- redirect HTTP -> HTTPS

## 4) GitHub Security Controls

Enable in repository settings:
- Secret scanning
- Push protection
- Branch protection on `main` with required checks:
  - `backend-build-and-test`
  - `frontend-build-and-test`
  - `analyze (java-kotlin)`
  - `analyze (javascript-typescript)`

## 5) Demo Environment Discipline

- use demo-only credentials and data
- do not store real customer information
- reset/refresh demo data periodically
