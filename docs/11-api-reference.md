---
title: "API reference"
description: "REST API for building your own client or collector: authentication, endpoints and ingest formats."
slug: api-reference
series: "Home Network Monitor"
order: 11
tags: [api, rest, openapi]
updated: 2026-10-22
---

# 11. API reference

The backend is a standalone REST API. Anything the web UI does, a mobile app or script can do. Base path: `/api/v1`.

- Interactive documentation (Swagger UI): `http://<box-ip>:8080/swagger-ui.html`
- Machine-readable schema (OpenAPI): `http://<box-ip>:8080/v3/api-docs`
- Health: `GET /actuator/health` (no authentication)

## Authentication

There are two kinds of caller:

| Caller | Credential | Reaches |
|---|---|---|
| People and apps | `Authorization: Bearer <accessToken>` | Everything under `/api/v1` **except** `/ingest/**` |
| Collector agents | `X-API-Key: <key>` | **Only** `/api/v1/ingest/**` |

### Log in
```
$ curl -s -X POST http://192.168.1.2:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"your-password"}'
```
Response:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "q2n...",
  "expiresInSeconds": 1800,
  "user": { "id": "…", "username": "admin", "role": "ROOT", "displayName": "…", "enabled": true }
}
```
- The access token lasts 30 minutes. The refresh token lasts 7 days and is **single-use**: each call to `/auth/refresh` returns a new pair.
- Use the access token on every request:
```
$ curl -s http://192.168.1.2:8080/api/v1/devices -H "Authorization: Bearer eyJ..."
```

## Errors

Errors use RFC 7807 `application/problem+json`:
```json
{ "status": 400, "title": "Bad Request", "detail": "Validation failed", "fields": { "password": "size must be between 10 and 128" } }
```
Common statuses: `400` invalid input, `401` missing/expired credential, `403` not allowed (for example an Admin calling a Root-only endpoint, or a blocked CORS origin), `404` not found, `409` conflict (for example a duplicate username), `429` account temporarily locked.

## Roles

| Role | Access |
|---|---|
| `ROOT` | Everything, including creating, changing and deleting users |
| `ADMIN` | Everything except user management (can list users) |

## Endpoints

Dates are `yyyy-MM-dd` (server timezone); timestamps are ISO-8601 UTC. Paged lists return `{ items, page, size, totalItems, totalPages }`.

### Setup and authentication

| Method | Path | Notes |
|---|---|---|
| GET | `/setup/status` | Public. `{ "setupRequired": true }` until the root user exists |
| POST | `/setup/root` | Public, works once. Body: `username`, `displayName`, `email?`, `password` (≥ 10 chars) |
| POST | `/auth/login` | Body: `username`, `password` |
| POST | `/auth/refresh` | Body: `refreshToken` |
| POST | `/auth/logout` | Body: `refreshToken` |
| GET | `/auth/me` | Current user |
| POST | `/auth/change-password` | Body: `currentPassword`, `newPassword` |

### Users

| Method | Path | Notes |
|---|---|---|
| GET | `/users` | Both roles |
| POST | `/users` | **Root only.** Creates an Admin |
| PATCH | `/users/{id}` | **Root only.** `displayName`, `email`, `enabled`. The root cannot be disabled |
| POST | `/users/{id}/reset-password` | **Root only.** Body: `newPassword` |
| DELETE | `/users/{id}` | **Root only.** The root cannot be removed |

### Devices

| Method | Path | Notes |
|---|---|---|
| GET | `/devices` | Query: `status` (`ONLINE`/`OFFLINE`), `trusted` (bool), `q` (name, IP, MAC) |
| GET | `/devices/{id}` | |
| PATCH | `/devices/{id}` | Body (any of): `label`, `deviceType`, `trusted`, `parentId`, `clearParent` |
| DELETE | `/devices/{id}` | Forgets the device and its history |
| GET | `/devices/{id}/usage` | Query: `from`, `to` (dates) |
| GET | `/devices/{id}/activity` | Query: `date`, `domain`, `page`, `size` |

Device types: `ROUTER`, `EXTENDER`, `ACCESS_POINT`, `PHONE`, `TABLET`, `LAPTOP`, `DESKTOP`, `TV`, `CONSOLE`, `IOT`, `PRINTER`, `UNKNOWN`.

### Usage and web activity

| Method | Path | Notes |
|---|---|---|
| GET | `/usage/daily` | Query: `date` (default today). Per-device `bytesDown`, `bytesUp`, `bytesTotal`, `megabytes` (1 MB = 1,048,576 bytes) |
| GET | `/usage/totals` | Query: `from`, `to`. Whole-network totals per day |
| GET | `/activity` | Query: `deviceId`, `date`, `domain` (contains), `page`, `size` (max 500) |
| GET | `/activity/top-domains` | Query: `deviceId?`, `date?`, `limit` (max 100) |

### Alerts

| Method | Path | Notes |
|---|---|---|
| GET | `/alerts` | Query: `status` (`OPEN`/`ACKNOWLEDGED`), `severity` (`INFO`/`WARNING`/`CRITICAL`), `type`, `page`, `size` |
| GET | `/alerts/open-count` | `{ "open": 3 }` (good for a badge) |
| POST | `/alerts/{id}/acknowledge` | |
| POST | `/alerts/acknowledge-all` | |

Alert types: `NEW_DEVICE`, `DEVICE_IP_CONFLICT`, `EXTERNAL_CONNECTION_ATTEMPT`, `PORT_SCAN`, `LOGIN_BRUTE_FORCE`, `INTERNET_DOWN`, `INTERNET_RESTORED`, `COLLECTOR_FAILURE`, `BASELINE_ESTABLISHED`.

### Overview and health

| Method | Path | Notes |
|---|---|---|
| GET | `/dashboard` | Counts, today's totals, top devices and domains, internet state |
| GET | `/system/status` | Internet/gateway state and the health of each data source |

### Settings

Secrets are masked (`********`) when read; send the mask back unchanged to keep the stored value.

| Method | Path | Notes |
|---|---|---|
| GET / PUT | `/settings/network` | `subnetCidr`, `gatewayIp`, `scanEnabled`, `scanIntervalSeconds` (30–3600), `offlineAfterSeconds` (60–86400), `activityRetentionDays` (1–365) |
| POST | `/settings/network/scan` | Starts a scan now (202) |
| GET / PUT | `/settings/dns-collector` | `type` (`NONE`/`PIHOLE`/`SIMULATED`), `url`, `password`, `pollSeconds` |
| POST | `/settings/dns-collector/test` | Same body; returns `{ ok, message }` |
| GET / PUT | `/settings/alerting` | `telegramEnabled`, `telegramBotToken`, `telegramChatId`, `minNotifySeverity`, `disabledTypes`, `cooldownMinutes` |
| POST | `/settings/alerting/test-telegram` | Same body; sends a test message |
| GET | `/settings/ingest` | `{ "keyConfigured": true }` |
| POST | `/settings/ingest/rotate-key` | Returns the new key **once** |

## Ingest (agents only)

Authenticate with the header `X-API-Key: <key>`. Batches are limited (5,000 items for usage and DNS, 1,000 for security events).

### POST /ingest/usage
Identify each device by `mac` (preferred) or `ip`. Send byte **deltas** since your last report.
```json
{ "samples": [
  { "mac": "aa:bb:cc:dd:ee:ff", "bytesDown": 5242880, "bytesUp": 1048576, "timestamp": "2026-10-21T10:00:00Z" }
] }
```
Response: `{ "accepted": 1, "unmatched": 0 }` (`unmatched` = devices not known yet; they are skipped).

### POST /ingest/dns
```json
{ "events": [
  { "clientIp": "192.168.1.23", "domain": "example.com", "timestamp": "2026-10-21T10:00:03Z", "blocked": false }
] }
```
Use `mac` instead of `clientIp` if you have it.

### POST /ingest/security-events
```json
{ "events": [
  { "type": "INBOUND_CONNECTION_ATTEMPT", "sourceIp": "198.51.100.7", "destPort": 22, "protocol": "TCP", "detail": "optional text" },
  { "type": "PORT_SCAN", "sourceIp": "198.51.100.7", "severity": "CRITICAL" }
] }
```
`type` is `INBOUND_CONNECTION_ATTEMPT` or `PORT_SCAN`; `severity` is optional. Repeats from the same source inside the cooldown window increment one alert's counter.

## Writing a client

- Store the refresh token securely (Keychain/Keystore on mobile). When a call returns `401`, call `/auth/refresh` once, retry, and send the user to login if it fails.
- Poll `/alerts/open-count` for badges; push notifications are a backend extension point (`NotificationChannel`).
- Browser apps on another origin must be listed in the backend's `NWM_CORS_ORIGINS`; native apps are not subject to CORS.
