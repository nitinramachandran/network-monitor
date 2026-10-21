---
name: network-monitor-setup
description: Guide a person through setting up the Home Network Monitor suite from scratch: choosing a setup, preparing a Raspberry Pi or PC, placing it in the network, installing with Docker Compose, creating the root user, and enabling Pi-hole web activity, the traffic agent and Telegram alerts. Use when someone wants to monitor devices, data usage, browsing or intrusions on a home network with this project.
---

# Network Monitor: setup skill

A runbook for getting this project working on someone's home network. It can be followed by a person directly, or used by an AI assistant to guide one step at a time. Full detail for every step is in `docs/` (start at `docs/README.md`); this file is the decision logic and the checkpoints.

## Ground rules for whoever is guiding

1. **Go one stage at a time and verify each checkpoint before moving on.** Never queue up the next stage while the previous one is unproven.
2. **Ask before assuming.** Hardware, router model, ISP and skill level change the path. Use the intake questions below.
3. **Be honest about limits** (section "Limits to state up front"). Do not promise page-level browsing history, external-attack detection behind CGNAT, or per-device data without a traffic-visible position.
4. **Never ask the user to paste secrets** (JWT secret, DB password, API keys, Telegram token, router password) into a chat. They type them into their own `.env`/UI. If logs are shared, remove secrets first.
5. **Warn before anything that can take the internet down** (gateway setup, disabling router DHCP, changing router DNS). Confirm a rollback exists and the timing is acceptable.
6. Only monitor networks the user owns or administers, and recommend telling household members.

## Limits to state up front

- Only **domains** looked up are visible, never page paths or content (HTTPS).
- Devices using DNS-over-HTTPS / Private DNS / VPN bypass Pi-hole.
- **MB per device** requires a box that sees the traffic: gateway or switch mirror port. A box simply plugged into the router cannot.
- **External intrusion** visibility requires a WAN-side position, and does not exist behind carrier-grade NAT (nothing can reach the home network anyway).
- Phones with randomized MACs may appear as new devices.
- It observes and alerts; it does not block.

## Intake questions

Ask these first and record the answers.

1. What do you want: (a) devices + new-device alerts, (b) also sites visited, (c) also MB per device and external intrusion?
2. What will be the monitor box: Raspberry Pi (which model, how much RAM) or a PC/mini PC/laptop? Wired Ethernet available?
3. Which ISP/router, and can you log into its admin page? Does it allow DHCP DNS change and address reservation?
4. Comfort with terminal/SSH (none, some, strong)?
5. For (c): willing to restructure the network (box as gateway + access point) or own a managed switch with port mirroring?
6. Do you want phone alerts via Telegram?

## Choose the setup

| Wants | Setup | Stages to run |
|---|---|---|
| (a) | A: box beside router | 0, 1, 2 |
| (b) | A + Pi-hole | 0, 1, 2, 3 |
| (c) with willingness to restructure | B: box as gateway | 0, 1 (gateway variant), 2, 3, 4 |
| (c) with managed switch | C: mirror port | 0, 1, 2, 3, 4 |
| Just evaluating on a laptop | see README section 12 (macOS) or run dev mode | none of the box stages |

If they want (c) but cannot restructure and have no managed switch, explain that per-device data is not possible and offer (b).

## Stage 0: Prepare the box

Docs: `docs/02-raspberry-pi.md` or `docs/03-pc-or-mini-pc.md`.

Do, in order:
1. Raspberry Pi: Raspberry Pi OS Lite **64-bit** via Raspberry Pi Imager with hostname, user, SSH and timezone set; or PC: Ubuntu Server 24.04 / Debian 12 with OpenSSH.
2. Connect by Ethernet to a LAN port on the ISP router; SSH in.
3. `sudo apt update && sudo apt full-upgrade -y`; reboot; `sudo timedatectl set-timezone <Zone>`.
4. Give the box a fixed IP (router DHCP reservation preferred; otherwise static config).
5. Install `ufw` and unattended-upgrades; allow 22, 8080, 8081 (and 53, 80 for Pi-hole) from the LAN subnet only; SSH keys.
6. Install Docker (`get.docker.com`), add the user to the `docker` group, re-login; install git.
7. `git clone https://github.com/nitinramachandran/network-monitor.git`.

**Checkpoint 0 (all must pass):**
```
ssh user@<box-ip>                    # works
ip -br addr                          # shows the fixed IP
timedatectl                          # correct zone, clock synchronized: yes
docker run --rm hello-world          # works without sudo
docker compose version               # prints a version
```
Pi extra: `vcgencmd get_throttled` should print `0x0`.

## Stage 1: Install the application

Docs: `docs/05-install-and-configure.md`.

1. In the repo: `cp .env.example .env`, then generate secrets:
   `sed -i "s/^NWM_JWT_SECRET=.*/NWM_JWT_SECRET=$(openssl rand -hex 32)/; s/^NWM_DB_PASSWORD=.*/NWM_DB_PASSWORD=$(openssl rand -hex 16)/" .env && chmod 600 .env`
   Set `NWM_TIMEZONE` in `.env`.
2. `docker compose up -d --build` (10 to 20 minutes on a Pi).
3. Wait for `docker compose logs backend` to show `Started NwMonitorApplication`.

**Checkpoint 1:**
```
docker compose ps                                    # db, backend, frontend running
curl -s http://localhost:8080/actuator/health        # {"status":"UP",...}
```
From another device: `http://<box-ip>:8081` shows the **Welcome** screen.

Gateway variant (setup B only): before this stage, build the gateway per `docs/04-network-placement.md` section 4.4 (LAN interface address, IP forwarding, NAT, DHCP/DNS), test with one device, keep the rollback plan (4.5), and remember the app subnet must be the **LAN** subnet.

## Stage 2: First run, baseline and naming

1. Welcome screen: create the **root** user (username, display name, password of 10+ characters). There is only ever one root.
2. Sign in. **Settings → Network access:** set subnet (blank to auto-detect; in setup B enter the LAN subnet, e.g. `192.168.50.0/24`) and gateway IP; Save.
3. Ask the user to make sure only recognised devices are connected, then **Devices → Scan now**. The first scan trusts everything found and raises one "baseline established" alert.
4. Open each device: set a label and type; mark router/extenders accordingly. Mark anything unrecognised **untrusted**.

**Checkpoint 2:**
- Devices list shows the expected phones, laptops, TV, router and the monitor box.
- **Connectivity** shows Internet: Up, Router: Reachable, Device discovery scan: Healthy.
- Bring a spare device onto the network: a **New device** warning alert appears within about a scan interval (60 s by default).

If the list is empty, use the "Scan finds no devices" table in `docs/10-troubleshooting.md` (wrong subnet is the top cause; in setup B the auto-detected subnet is the WAN side).

## Stage 3: Pi-hole (sites visited)

Skip unless the user wants web activity. Docs: `docs/06-pihole.md`, router steps in `docs/04-network-placement.md` section 4.3.

1. On the box: `curl -sSL https://install.pi-hole.net | sudo bash` (Pi-hole **v6**; choose the wired interface; enable query logging). Ubuntu: free port 53 first (docs/03 section 3.9). `sudo pihole setpassword`.
2. Test one device manually: `nslookup example.com <box-ip>` and see it in Pi-hole's Query Log with the device's own IP.
3. Set the router's DHCP DNS to the box IP **only** (no secondary public DNS). If locked: Pi-hole DHCP (box becomes critical), per-device DNS, or setup B. Disable IPv6 DNS advertisement if possible.
4. Renew leases (reconnect Wi-Fi). Turn off Private DNS / secure DNS on devices.
5. In the app: **Settings → Web activity source → Pi-hole (v6)**, URL `http://127.0.0.1` (same box) or the Pi-hole IP, password, **Test connection**, Save.

**Checkpoint 3:** browse on a phone for a minute; **Web activity** lists those domains under the phone; Connectivity shows *Web activity collector* healthy. If Pi-hole's log shows one client IP for everything, fix per docs/06 section 6.6.

## Stage 4: Traffic agent (MB per device, inbound probes)

Skip unless the user wants it and the box is in setup B or C. If the box is in setup A, stop and explain that it cannot see other devices' traffic.

Docs: `docs/07-collector-agent.md`.

1. **Settings → Collector agents → Generate key**; the user copies it immediately.
2. On the box: python3 venv at `/opt/nwm-agent`, `pip install scapy requests`, copy `collector-agent/agent.py`.
3. `/etc/nwm-agent.env` (mode 600) with `NWM_URL=http://127.0.0.1:8080`, `NWM_API_KEY=<key>`, `LAN_PREFIX=<prefix with trailing dot>`; optional `IFACE` (LAN or mirror NIC) and `PROTECTED_IPS=<WAN IP>`.
4. Run it in the foreground first; then install the systemd unit and enable it.

**Checkpoint 4:** after streaming something on a phone for a minute, **Data usage** shows MB for that device today; Connectivity shows *Agent: data usage* healthy. If only the box itself has usage, the box cannot see the other traffic (wrong position).

## Stage 5: Telegram alerts

Docs: `docs/08-alerts-and-telegram.md`.

1. @BotFather `/newbot` → token. Press **Start** on the bot, open `https://api.telegram.org/bot<TOKEN>/getUpdates` (in the user's own browser) → chat id (negative for groups).
2. **Settings → Alerting:** enable Telegram, enter token and chat id, choose severity threshold, **Send test message**, Save.

**Checkpoint 5:** the test message arrives; a spare device joining produces a Telegram message.

## Stage 6: Harden and maintain

Docs: `docs/09-operations-and-security.md`.

- Nothing from the internet forwarded to the box; remote access via a VPN (Tailscale/WireGuard) only.
- Daily database backup cron, copied off the box; test a restore once.
- Know the update command: `git pull && docker compose up -d --build`.
- Know the recovery path for a lost root password (docs/09 section 9.8).

## Decision tree for failures

| Symptom | First action |
|---|---|
| Anything data-related is stale | Open **Connectivity**; read each source's last error |
| Containers not running | `docker compose ps`; `docker compose logs --tail=100 backend` |
| `role "nwmonitor" does not exist` / password auth failed | DB credentials mismatch (changed `.env` after first start, or another Postgres on the port): docs/10 install table |
| `403` creating root / calling API from a browser | CORS origin not allowed: use `http://<box-ip>:8081` |
| No devices | Subnet wrong (gateway setup: enter LAN subnet) / isolated segment / host networking missing |
| No web activity | Device not using Pi-hole DNS (DoH, IPv6 DNS, router DNS locked) |
| No data usage | Position cannot see traffic, wrong API key, wrong `LAN_PREFIX` |
| No Telegram | Never pressed Start on the bot, wrong chat id, severity/cooldown/type settings |
| House offline after gateway change | Execute the rollback (docs/04 section 4.5) immediately |

## Final handover checklist

- [ ] Box has a fixed IP; auto-starts after power loss (BIOS setting on PCs); Docker enabled at boot
- [ ] Root account created; password stored in a password manager
- [ ] Devices labelled; unknown devices handled
- [ ] Alerts reach the phone (if wanted); thresholds tuned
- [ ] Pi-hole and/or agent verified with a real device (if used)
- [ ] Backups scheduled; `.env` stored safely off the box
- [ ] User knows where the docs are, how to update, and the rollback steps

## Reference

| Topic | File |
|---|---|
| Index and reading order | `docs/README.md` |
| Planning and limits | `docs/01-overview-and-planning.md` |
| Raspberry Pi preparation | `docs/02-raspberry-pi.md` |
| PC / mini PC preparation | `docs/03-pc-or-mini-pc.md` |
| Network placement and ISP router | `docs/04-network-placement.md` |
| Install and first run | `docs/05-install-and-configure.md` |
| Pi-hole | `docs/06-pihole.md` |
| Traffic and intrusion agent | `docs/07-collector-agent.md` |
| Alerts and Telegram | `docs/08-alerts-and-telegram.md` |
| Operations and security | `docs/09-operations-and-security.md` |
| Troubleshooting | `docs/10-troubleshooting.md` |
| API | `docs/11-api-reference.md` |
