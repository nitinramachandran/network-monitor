# Home Network Monitor

Monitor your home network: see every device, the sites each one looks up, how much data each uses per day, and get alerted when a new device joins or something suspicious happens.

| Folder | What it is |
|---|---|
| `NWMonitorBackend/` | Java 21 / Spring Boot service. A stateless REST API (`/api/v1`) backed by PostgreSQL. API docs at `/swagger-ui.html`. |
| `nw-monitor-frontend/` | React + TypeScript (Vite) web UI. It only talks to the public API, exactly as a future mobile app would. |
| `collector-agent/` | Optional Python agent that counts bytes per device and spots inbound probes, then pushes them to the API. |
| `docker-compose.yml` | Runs database + backend + UI together on a Linux monitor box. |

## Full documentation

This README is the condensed version. The complete step-by-step tutorial series lives in [`docs/`](docs/README.md):

| Guide | Covers |
|---|---|
| [Overview and planning](docs/01-overview-and-planning.md) | What is possible, what is not, choosing a setup |
| [Raspberry Pi setup](docs/02-raspberry-pi.md) · [PC / mini PC setup](docs/03-pc-or-mini-pc.md) | Preparing the always-on monitor box |
| [Network placement](docs/04-network-placement.md) | Where the box sits; ISP router settings; gateway build |
| [Install and configure](docs/05-install-and-configure.md) | Docker deployment, root user, first scan |
| [Pi-hole](docs/06-pihole.md) · [Traffic agent](docs/07-collector-agent.md) · [Alerts](docs/08-alerts-and-telegram.md) | Enabling each feature |
| [Operations and security](docs/09-operations-and-security.md) · [Troubleshooting](docs/10-troubleshooting.md) · [API](docs/11-api-reference.md) | Running it day to day |

[`skills.md`](skills.md) is a guided setup runbook that a person or an AI assistant can follow stage by stage.

## Contents
1. [Read this first: what can and cannot be monitored](#1-read-this-first-what-can-and-cannot-be-monitored)
2. [Architecture](#2-architecture)
3. [Prerequisites](#3-prerequisites)
4. [Quick start for development (any OS)](#4-quick-start-for-development-any-os)
5. [Production setup on an always-on Linux box](#5-production-setup-on-an-always-on-linux-box)
6. [First-run walkthrough](#6-first-run-walkthrough)
7. [Enabling each feature](#7-enabling-each-feature)
8. [Using the app](#8-using-the-app)
9. [Users and roles](#9-users-and-roles)
10. [Configuration reference](#10-configuration-reference)
11. [API for other clients](#11-api-for-other-clients)
12. [Running on macOS](#12-running-on-macos)
13. [Security notes](#13-security-notes)
14. [Troubleshooting](#14-troubleshooting)
15. [Development](#15-development)

---

## 1. Read this first: what can and cannot be monitored

An ISP-supplied router shows no per-device logs, so a **monitor box** on your LAN has to supply the data. Each feature needs a different source:

| Feature | Data source | Works out of the box? |
|---|---|---|
| Device list, online/offline, new-device alerts, IP-conflict alerts | Built-in scanner (ping sweep + ARP table) | Yes, from any machine on the LAN |
| Internet / router up-down alerts | Built-in connectivity monitor | Yes |
| Sites visited per device | DNS query log from **Pi-hole v6** | Needs Pi-hole + your devices using it for DNS |
| MB used per device per day | `collector-agent` (or anything calling `POST /ingest/usage`) | Needs a machine that **sees the traffic** (see 7.3) |
| Inbound / external connection attempts, port scans | `collector-agent` or forwarded router/firewall logs | Needs a vantage point at the WAN edge (see 7.4) |
| Failed-login bursts against this app | Built-in | Yes |

Honest limits:
- **HTTPS hides page paths.** You can see *domains* a device looked up (e.g. `youtube.com`), never the specific page.
- Devices using **DNS-over-HTTPS / "private DNS"** bypass Pi-hole and will not show web activity.
- Behind your ISP's **NAT**, nearly all unsolicited inbound traffic is dropped before reaching your LAN, so "someone from outside trying to connect" can only be seen from the WAN side of a router you control.
- Phones often use a **randomized (private) MAC address**; such devices are marked 🎲 and a phone may appear as a "new device" if it rotates its address.

Stage your rollout: get Stage 1 (devices + alerts) working first, then add the rest.

## 2. Architecture

```
                      ┌─────────────── monitor box (always on, on your LAN) ───────────────┐
 devices ── LAN ──►   │  Scanner (ping + ARP) ─┐                                           │
                      │  Pi-hole DNS log ──────┼─► Backend API (Spring Boot) ─► PostgreSQL │
 collector-agent ───► │  /api/v1/ingest/* ─────┘        ▲   │                              │
 (sees traffic)       │                                 │   └─► Alerts ─► Telegram / app   │
                      │  React UI (nginx) ──────────────┘                                  │
                      └────────────────────────────────────────────────────────────────────┘
```

- The backend is a standalone, stateless service. Auth is JWT (short-lived access token + rotating refresh token).
- Collectors push data through an API-key-protected ingest API, so capture can run on any machine.
- Alert channels are pluggable (`NotificationChannel`); Telegram ships today.

## 3. Prerequisites

| Need | Version | Used for |
|---|---|---|
| Java JDK | 21 or newer | Backend (dev) |
| Maven | 3.8+ | Backend build (dev) |
| Node.js + npm | 20+ | Frontend (dev) |
| PostgreSQL | 14+ (16 recommended) | Database |
| Docker + Docker Compose | recent | Production setup, or an easy Postgres |
| Python 3 + `scapy`, `requests` | 3.9+ | Only for `collector-agent` |

Check what you have: `java -version`, `mvn -v`, `node -v`, `docker -v`.

## 4. Quick start for development (any OS)

### 4.1 Start PostgreSQL

Easiest, with Docker:
```bash
docker run -d --name nwm-db -p 5432:5432 \
  -e POSTGRES_DB=nwmonitor -e POSTGRES_USER=nwmonitor -e POSTGRES_PASSWORD=nwmonitor \
  postgres:16-alpine
```
If port 5432 is already in use (e.g. a local Postgres), use `-p 5433:5432` and set `NWM_DB_URL=jdbc:postgresql://localhost:5433/nwmonitor` when starting the backend.

Already have Postgres installed? Create the role and database instead:
```bash
psql -d postgres -c "CREATE ROLE nwmonitor LOGIN PASSWORD 'nwmonitor';"
psql -d postgres -c "CREATE DATABASE nwmonitor OWNER nwmonitor;"
```
The tables are created automatically on first start (Flyway). An empty database is all that's needed.

### 4.2 Start the backend (port 8080)
```bash
cd NWMonitorBackend
export NWM_JWT_SECRET="$(openssl rand -hex 32)"   # 64 chars; keep it stable between restarts
mvn spring-boot:run
```
Check it is up: `curl http://localhost:8080/actuator/health` → `{"status":"UP",...}`.
API docs: <http://localhost:8080/swagger-ui.html>.

If `NWM_JWT_SECRET` is unset, a random key is used and everyone is signed out on each restart.

### 4.3 Start the frontend (port 5173)
```bash
cd nw-monitor-frontend
npm install
npm run dev
```
Open <http://localhost:5173>. The dev server proxies `/api` to the backend (set `VITE_BACKEND_URL` if the backend is elsewhere), so there is no CORS to configure.

## 5. Production setup on an always-on Linux box

Use a Raspberry Pi 4/5 or a small PC, wired by Ethernet, with a fixed IP.

1. **Reserve an IP** for the box in your router (DHCP reservation), e.g. `192.168.1.2`.
2. **Install Docker** and the compose plugin (<https://docs.docker.com/engine/install/>).
3. **Get the code** onto the box and configure it:
   ```bash
   cd network-monitor
   cp .env.example .env
   ```
   Edit `.env`:
   ```
   NWM_JWT_SECRET=<output of: openssl rand -hex 32>
   NWM_DB_PASSWORD=<a strong password>
   NWM_TIMEZONE=Asia/Kolkata
   ```
   `NWM_JWT_SECRET` is mandatory; compose refuses to start without it.
4. **Start everything:**
   ```bash
   docker compose up -d --build
   ```
   The first build takes several minutes (Maven and npm downloads).
5. Open `http://<box-ip>:8081` from any device on your LAN and continue with the walkthrough below.

Notes:
- The backend uses **host networking** so the scanner can see the real LAN. This works on Linux only (see [Running on macOS](#12-running-on-macos)).
- Update later with `git pull && docker compose up -d --build`. Data lives in the `dbdata` volume.
- Logs: `docker compose logs -f backend`.
- Backup: `docker compose exec db pg_dump -U nwmonitor nwmonitor > backup.sql`.

## 6. First-run walkthrough

1. **Create the root account.** On first visit the UI shows a *Welcome* screen. Choose a username and a password of at least 10 characters. This is the only root user that will ever exist. (For unattended installs set `NWM_ROOT_USERNAME` and `NWM_ROOT_PASSWORD` instead.)
2. **Sign in.** The dashboard will be mostly empty until a scan runs.
3. **Tell the monitor about your network** (Settings → *Network access*):
   - *Subnet*: usually `192.168.1.0/24`. Leave blank to auto-detect (the detected value is shown).
   - *Router / gateway IP*: usually `192.168.1.1`. Find it with `ip route | grep default` (Linux), `route -n get default` (macOS) or `ipconfig` (Windows).
   - Save, then open **Devices → Scan now**.
4. **The baseline.** The *first* scan marks every device it finds as **trusted** and raises one info alert, "Network baseline established". From then on, any device not seen before raises a **New device** alert. If something unknown is in the baseline, open it and click *Mark untrusted*.
5. **Name your devices.** Open each device, give it a label ("Dad's phone"), set its type, and mark the ISP router and any range extenders as `ROUTER` / `EXTENDER` / `ACCESS_POINT`.
6. **Set up alerts** (7.1 below).

## 7. Enabling each feature

### 7.1 Telegram alerts
1. In Telegram, message **@BotFather**, send `/newbot`, follow the prompts, and copy the **bot token**.
2. Open a chat with your new bot and send it any message.
3. Get your chat id: open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and read `"chat":{"id": ...}`.
4. In the app: Settings → *Alerting* → tick *Send alerts to Telegram*, paste token and chat id, click **Send test message**, then **Save**.
5. Choose which alert types you want and the minimum severity to notify. Repeats of the same alert within the cooldown window are grouped into one alert (with a ×N counter) instead of spamming you.

Alerts always appear in the app (Alerts page, red counter in the sidebar) regardless of Telegram. A "no internet" alert cannot reach Telegram while the internet is down; you get the "Internet is back" message afterwards with the outage length.

### 7.2 Sites visited per device (Pi-hole)
1. Install **Pi-hole v6** on the monitor box or another always-on machine: <https://docs.pi-hole.net/main/basic-install/>. Set an admin password.
2. Make devices use Pi-hole for DNS. Either:
   - set the router's **DHCP DNS server** to the Pi-hole IP (many ISP routers lock this), or
   - disable the router's DHCP and enable Pi-hole's DHCP server, or
   - set the DNS server manually on individual devices.
3. In the app: Settings → *Web activity source* → choose **Pi-hole (v6)**, enter its URL (e.g. `http://192.168.1.2`) and password, click **Test connection**, then **Save**.
4. Browse on a phone for a minute; **Web activity** should list the lookups under that device.

To explore the UI without any capture, choose **Simulated demo data** as the source. It generates fake browsing and traffic for discovered devices (remember to switch it to *Disabled* or Pi-hole afterwards, since it adds fake data).

Raw web activity is kept for 30 days by default (Settings → Network access → *Keep web activity for*) and purged nightly.

### 7.3 MB used per device per day (collector agent)
Byte counts require a machine that actually *sees other devices' traffic*. Pi-hole alone cannot provide them. Options:

| Option | How |
|---|---|
| Your own router behind the ISP router (e.g. OpenWrt) | Run the agent on that router or on the box acting as gateway. Cleanest. |
| Managed switch with port mirroring | Mirror the router port to the box's NIC. |
| ARP-spoofing the gateway from the box | Works on plain home networks but is invasive and fragile. Not provided by this project. |

Once the box sees the traffic:
1. In the app: Settings → *Collector agents* → **Generate key**. Copy it immediately (it is shown once).
2. On the machine that sees the traffic:
   ```bash
   cd collector-agent
   pip install scapy requests
   sudo NWM_URL=http://192.168.1.2:8080 \
        NWM_API_KEY=nwm_xxxxxxxx \
        LAN_PREFIX=192.168.1. \
        python3 agent.py
   ```
   `REPORT_SECONDS` (default 30) sets how often it reports. Optional: `IFACE` (sniff one interface) and `PROTECTED_IPS` (your WAN IP, so inbound probes addressed to it are detected). See [docs/07](docs/07-collector-agent.md) for running it as a service.
3. **Data usage** and the dashboard now fill in. Totals are shown in MB (1 MB = 1,048,576 bytes), split into download/upload, per device per day.

Run the agent as a service (systemd) so it survives reboots. Rotating the key invalidates old agents.

You can write your own collector in any language: `POST /api/v1/ingest/usage` with header `X-API-Key`, body `{"samples":[{"mac":"aa:bb:...","bytesDown":123,"bytesUp":45,"timestamp":"2026-01-01T10:00:00Z"}]}` (bytes are **deltas** since the last sample; use `ip` instead of `mac` if you prefer).

### 7.4 Intrusion attempts from outside
Only possible if a component sees WAN-side traffic (e.g. the agent on your own router's WAN side, or router/firewall logs). The agent reports unsolicited inbound TCP SYNs per source as *External connection attempt*, and a source probing 15+ ports in one interval as *Port scan* (critical). Any other tool can report the same via:
```bash
curl -X POST http://192.168.1.2:8080/api/v1/ingest/security-events \
  -H "X-API-Key: nwm_xxxxxxxx" -H "Content-Type: application/json" \
  -d '{"events":[{"type":"INBOUND_CONNECTION_ATTEMPT","sourceIp":"203.0.113.9","destPort":22,"protocol":"TCP"}]}'
```
`type` is `INBOUND_CONNECTION_ATTEMPT` or `PORT_SCAN`.

Without this stage you still get internal protection: new devices, duplicate-IP (possible ARP spoofing) alerts, and repeated-failed-login alerts for the app itself.

## 8. Using the app

| Screen | Purpose |
|---|---|
| **Dashboard** | Devices online, untrusted devices, open alerts, data today, 7-day chart, top devices and domains, internet status banner. |
| **Devices** | All devices with status, IP, MAC; search/filter; *Scan now*; *Trust*. Click one for details, label/type, 14-day usage and web activity by day. |
| **Data usage** | Per-device MB for any day (download, upload, total). |
| **Web activity** | Domains looked up, filter by day, device and text. |
| **Alerts** | Open/acknowledged alerts; acknowledge one or all. |
| **Connectivity** | Internet and router status, and the health of every data source (scanner, Pi-hole, agents) with last success and errors. Check here first if data stops arriving. |
| **Settings** | *Network access*, *Web activity source*, *Alerting*, *Collector agents*. |
| **Users** | (Root only) manage admins. |
| **Account** | Change your own password. |

Alert types: New device, Device IP conflict, External connection attempt, Port scan, Login brute force, Internet down / restored, Collector failure, Baseline established. Any can be switched off in Settings → Alerting.

## 9. Users and roles

- **ROOT**: exactly one, created at first run. Cannot be deleted or disabled. Can add, disable, reset the password of, and remove Admin users.
- **ADMIN**: same access to all monitoring and settings, but cannot manage users.

Five wrong passwords lock an account for 15 minutes, and 5 failed logins from one address in 10 minutes raise a critical alert. Passwords need at least 10 characters. Changing or resetting a password signs that user out everywhere.

## 10. Configuration reference

Backend environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `NWM_DB_URL` | `jdbc:postgresql://localhost:5432/nwmonitor` | JDBC URL |
| `NWM_DB_USER` / `NWM_DB_PASSWORD` | `nwmonitor` / `nwmonitor` | DB credentials |
| `NWM_JWT_SECRET` | random per start | **Set in production**: 32+ characters |
| `NWM_PORT` | `8080` | HTTP port |
| `NWM_TIMEZONE` | server default | Defines "per day" boundaries, e.g. `Asia/Kolkata` |
| `NWM_CORS_ORIGINS` | `http://localhost:5173` | Comma-separated origins allowed to call the API directly (other web apps / dev). The bundled UI does not need this. |
| `NWM_ROOT_USERNAME` / `NWM_ROOT_PASSWORD` | unset | Create the root user headlessly on first start |

Frontend: `VITE_BACKEND_URL` (dev proxy target, default `http://localhost:8080`), `VITE_API_BASE` (override API base path, default `/api/v1`).

All other settings (subnet, scan interval, Pi-hole, Telegram, alert rules) are changed in the UI and stored in the database.

## 11. API for other clients

- Base path `/api/v1`; interactive docs at `/swagger-ui.html`, schema at `/v3/api-docs`.
- `POST /auth/login` returns `accessToken` (30 min) and a single-use `refreshToken` (7 days); call `POST /auth/refresh` to rotate. Send `Authorization: Bearer <accessToken>`.
- Errors are RFC 7807 `application/problem+json` (`detail`, and `fields` for validation).
- Collectors use `X-API-Key` on `/api/v1/ingest/**` only; those keys cannot call any other endpoint.
- Browser apps on another origin must be listed in `NWM_CORS_ORIGINS`; native mobile apps are not subject to CORS.

## 12. Running on macOS

Good for trying Stage 1; not suitable as the permanent monitor.
- `docker compose` does not work as written (host networking is Linux-only). Run the pieces directly as in [section 4](#4-quick-start-for-development-any-os).
- The scanner uses `arp -an`. If it finds nothing, allow your terminal under System Settings → Privacy & Security → **Local Network**.
- A Mac only sees its own traffic, so per-device MB and WAN intrusion detection are not possible from it; Pi-hole in Docker Desktop hides client IPs, so per-device web activity is unreliable.
- Use `caffeinate -s` to stop sleep gaps, and give the Mac a fixed IP.

## 13. Security notes

- Keep the UI/API **on your LAN**. Do not port-forward them to the internet. For remote access use a VPN (WireGuard, Tailscale).
- Set a strong `NWM_JWT_SECRET` and `NWM_DB_PASSWORD`; never commit `.env`.
- Secrets saved in the UI (Pi-hole password, Telegram token) are stored in the database and masked when read back; only a hash of the ingest API key is stored.
- Put the UI behind HTTPS (e.g. a reverse proxy) if the LAN is shared with people you do not trust: the app uses bearer tokens.
- Only monitor networks you own or administer, and tell household members that DNS and usage are recorded.

## 14. Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `FATAL: role "nwmonitor" does not exist` | The DB server on that port has no such role. Create it (4.1) or point `NWM_DB_URL`/`NWM_DB_USER`/`NWM_DB_PASSWORD` elsewhere. Another Postgres may already own port 5432. |
| `403` when creating the root user or calling the API from a browser | CORS: you opened the UI on an origin the backend does not allow. Use the bundled dev proxy / nginx (they strip the `Origin` header), or add the origin to `NWM_CORS_ORIGINS`. Restart `npm run dev` after config changes. |
| `409 Setup already completed` | A root user exists; just sign in. To start over in development, empty the tables: `TRUNCATE refresh_tokens, web_activity, daily_usage, alerts, devices, app_settings, users CASCADE;` (this deletes everything). |
| Scan finds 0 devices | Wrong/undetected subnet (set it in Settings); box not on the same LAN (VLAN/guest Wi-Fi isolation); on macOS grant Local Network permission; in Docker on Linux use the provided host-networked setup. |
| Everyone signed out after restart | `NWM_JWT_SECRET` not set. |
| "Pi-hole rejected the password" / connection errors | Check URL, v6 password, and that the backend can reach Pi-hole. See *Connectivity* for the last error. |
| Web activity empty | Devices are not using Pi-hole for DNS (DoH, IPv6 DNS, or router DNS locked). Check Pi-hole's own query log first. |
| Data usage empty | No agent is reporting; check *Connectivity → Agent: data usage*, the API key, and that the agent machine sees the traffic. |
| Port 8080/5173/8081 busy | Change `NWM_PORT`, the Vite port, or the compose port mapping. |
| Telegram test fails | Wrong token/chat id, or you never messaged the bot first. |

## 15. Development

```bash
cd NWMonitorBackend && mvn test          # backend tests (H2, no Postgres needed)
cd nw-monitor-frontend && npm run typecheck && npm run build
```
- Backend layout (`com.nwmonitor`): `auth`, `user`, `security`, `device`, `scan`, `usage`, `activity`, `alert`, `settings`, `collector`, `ingest`, `system`. Schema changes go in new Flyway files under `src/main/resources/db/migration/` (never edit an applied one).
- Add a web-activity source by implementing `ActivityCollector`; add an alert channel by implementing `NotificationChannel`. Both are picked up as Spring beans.
- Frontend: pages in `src/pages`, typed API client in `src/api`.
