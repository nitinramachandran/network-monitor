# 5. Install and configure the application

This guide deploys the monitor on your prepared box (from [guide 2](02-raspberry-pi.md) or [3](03-pc-or-mini-pc.md)) with Docker Compose, creates the root user, and runs the first scan. You should already have:

- A Linux box with a fixed IP, Docker and Docker Compose working, and the repository cloned.
- The box connected to your network as described in [guide 4](04-network-placement.md).

## 5.1 What gets installed

`docker compose` starts three containers:

| Container | Role | Where it listens |
|---|---|---|
| `db` | PostgreSQL 16, data in a Docker volume (`dbdata`) | `127.0.0.1:5432` (this machine only) |
| `backend` | Java API, runs with **host networking** so it can scan your real LAN | `0.0.0.0:8080` |
| `frontend` | nginx serving the web UI and proxying `/api` to the backend | `0.0.0.0:8081` |

All three restart automatically after a reboot or power cut (`restart: unless-stopped`) once Docker itself starts at boot, which it does by default on Debian/Ubuntu/Raspberry Pi OS.

## 5.2 Configure secrets

From the repository folder:
```
$ cd ~/network-monitor
$ cp .env.example .env
$ sed -i "s/^NWM_JWT_SECRET=.*/NWM_JWT_SECRET=$(openssl rand -hex 32)/; s/^NWM_DB_PASSWORD=.*/NWM_DB_PASSWORD=$(openssl rand -hex 16)/" .env
$ chmod 600 .env
$ nano .env                  # check the values; set your timezone
```
The finished `.env` looks like:
```
NWM_JWT_SECRET=3f9c...64 hex characters...
NWM_DB_PASSWORD=a41b...32 hex characters...
NWM_TIMEZONE=Asia/Kolkata
```

| Variable | Meaning |
|---|---|
| `NWM_JWT_SECRET` | Signs login tokens. **Required**; compose refuses to start without it. Keep it private and stable; changing it signs everyone out. |
| `NWM_DB_PASSWORD` | Database password. It is applied **only when the database is first created**. Changing it later in `.env` does not change the existing database (see [guide 9](09-operations-and-security.md)). |
| `NWM_TIMEZONE` | Defines where each "day" of usage begins and ends. Use an IANA name such as `Asia/Kolkata`. |

Never commit `.env` (it is already in `.gitignore`).

Optional: `NWM_ROOT_USERNAME` and `NWM_ROOT_PASSWORD` create the root user automatically at first start (for unattended installs). Otherwise you create it in the browser in step 5.4.

## 5.3 Build and start

```
$ docker compose up -d --build
```
The first build downloads Java and Node toolchains and compiles both applications. Expect **10 to 20 minutes on a Raspberry Pi** and a few minutes on a PC. Watch progress, and wait for the backend to come up:
```
$ docker compose logs -f backend          # Ctrl+C to stop watching
```
Look for a line containing `Started NwMonitorApplication`. Then verify:
```
$ docker compose ps                       # all three "running"
$ curl -s http://localhost:8080/actuator/health
{"status":"UP","groups":["liveness","readiness"]}
```

## 5.4 Create the root user

From **any device on your network**, open `http://<box-ip>:8081` (for example `http://192.168.1.2:8081`).

You will see a **Welcome** screen. Enter:
- **Username**: for example `admin` or your name (letters, digits, `.`, `_`, `-`).
- **Display name**, optional **email**.
- **Password**: at least 10 characters; use a password manager.

Click **Create root account**. This is the only root user the system will ever allow. You are then taken to the login screen; sign in.

> The Welcome screen appears only while no root user exists. If the page shows a login instead, a root user already exists.

## 5.5 Tell the monitor about your network

Open **Settings → Network access**.

| Field | What to enter |
|---|---|
| Subnet (CIDR) | Leave blank to auto-detect (the detected value is shown underneath), or enter yours, e.g. `192.168.1.0/24`. **In Setup B (gateway) you must enter the LAN subnet**, e.g. `192.168.50.0/24`. |
| Router / gateway IP | Your router's address, e.g. `192.168.1.1`. Used for connectivity checks and to tag the router in the device list. |
| Scan automatically | On |
| Scan interval | 60 seconds is a good default |
| Mark offline after | 300 seconds (a device not seen for 5 minutes shows offline) |
| Keep web activity for | 30 days |

Click **Save**.

## 5.6 Run the first scan and set the baseline

1. Go to **Devices** and click **Scan now**. A scan of a /24 takes roughly 10 to 30 seconds; refresh the page.
2. The **first scan establishes the baseline**: every device found is marked **trusted**, and you get one info alert, "Network baseline established". From then on any **new** device raises a warning alert.
3. Run the first scan when **only devices you recognize are connected**. If something unknown is in the baseline, open it and click **Mark untrusted**.

Expected result: your phones, laptops, TV, the router and the monitor box itself appear within a minute or two. Devices that were asleep appear when they next wake.

If the list stays empty, see [Troubleshooting](10-troubleshooting.md#scan-finds-no-devices).

## 5.7 Name your devices

Click each device and set:
- **Label**: a friendly name ("Dad's phone", "Living room TV").
- **Type**: phone, laptop, TV, IoT, router, extender, access point, ...

Set the ISP router and any extenders to the right types. The first scan already labels the device at the gateway address as "ISP Router" when it can.

A device marked with a dice icon uses a **randomized MAC address**. Phones on Wi-Fi with "Private address" enabled do this, and the address can change, which makes the phone look like a new device. If that bothers you, turn off the phone's private Wi-Fi address for your home network.

## 5.8 Check the Connectivity page

Open **Connectivity**. You should see:
- **Internet**: Up
- **Router / gateway**: Reachable
- **Device discovery scan**: Healthy, with the last success time

This page also shows the health of Pi-hole and the agents once configured; check it first whenever data stops arriving.

## 5.9 Day-to-day commands

Run these in the repository folder.

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Live logs | `docker compose logs -f backend` |
| Restart | `docker compose restart` |
| Stop | `docker compose stop` |
| Start | `docker compose start` |
| Update to the latest code | `git pull && docker compose up -d --build` |
| Remove containers but keep data | `docker compose down` |
| **Remove everything including data** | `docker compose down -v` (destroys the database) |

## 5.10 What to configure next

| Want | Go to |
|---|---|
| Phone alerts | [Guide 8](08-alerts-and-telegram.md) |
| Sites visited per device | [Guide 6](06-pihole.md) |
| MB per device per day, intrusion events | [Guide 7](07-collector-agent.md) |
| Backups and security | [Guide 9](09-operations-and-security.md) |

## 5.11 Try the interface with demo data (optional)

To explore the screens before real data flows, choose **Settings → Web activity source → Simulated demo data**. The monitor then generates fake browsing and traffic for devices it has discovered. **Switch it back to Disabled (or Pi-hole) when you are done**, otherwise fake data keeps being added.
