# 9. Operations and security

Keeping the monitor healthy, backed up and safe. Run commands in the repository folder on the monitor box unless stated otherwise.

## 9.1 Routine checks

| How often | Check |
|---|---|
| Whenever data seems stale | UI → **Connectivity** (every source shows last success and last error) |
| Weekly | Alerts page: acknowledge or investigate open items |
| Monthly | Update (9.2), test a backup restore (9.3), review the device list for devices you no longer own |
| After an outage | `docker compose ps` to confirm all three containers are running |

Resource use: `docker stats --no-stream` (expect the backend around 300 to 600 MB). Disk: `df -h` and `docker system df`.

## 9.2 Updating

```
$ cd ~/network-monitor
$ git pull
$ docker compose up -d --build
```
Database changes are applied automatically at start-up. Back up first (9.3) before large upgrades. To remove old images afterwards: `docker image prune -f`.

Update the operating system too: `sudo apt update && sudo apt full-upgrade -y` and reboot occasionally. Pi-hole updates with `pihole -up`.

## 9.3 Backup and restore

What matters is the **database** (devices, labels, history, users, settings) and your **`.env`** file.

**Back up:**
```
$ mkdir -p ~/backups
$ docker compose exec -T db pg_dump -U nwmonitor nwmonitor | gzip > ~/backups/nwm-$(date +%F).sql.gz
$ cp .env ~/backups/env-$(date +%F)       # contains secrets; store safely
```
Copy `~/backups` off the box (another computer, cloud storage you trust). A backup on the same SD card does not protect against card failure.

**Automate daily at 03:30** (keeping 14 days):
```
$ crontab -e
30 3 * * * cd /home/youruser/network-monitor && docker compose exec -T db pg_dump -U nwmonitor nwmonitor | gzip > /home/youruser/backups/nwm-$(date +\%F).sql.gz && find /home/youruser/backups -name 'nwm-*.sql.gz' -mtime +14 -delete
```
(Use absolute paths; `%` must be escaped as `\%` in crontab.)

**Restore** into a fresh install:
```
$ docker compose up -d db
$ docker compose stop backend
$ docker compose exec -T db psql -U nwmonitor -d postgres -c "DROP DATABASE IF EXISTS nwmonitor;" -c "CREATE DATABASE nwmonitor OWNER nwmonitor;"
$ gunzip -c ~/backups/nwm-2026-10-01.sql.gz | docker compose exec -T db psql -U nwmonitor -d nwmonitor
$ docker compose up -d
```
Use the same `NWM_JWT_SECRET` and `NWM_DB_PASSWORD` as before.

## 9.4 Logs

```
$ docker compose logs --tail=200 backend
$ docker compose logs -f db
$ journalctl -u nwm-agent -f          # agent, if installed
```
Docker log size is capped if you applied the setting in guide 2 or 3.

## 9.5 Security checklist

- [ ] The monitor is reachable **only from your LAN**. Do not forward ports 8080, 8081, 53, 80 or 22 from the internet.
- [ ] Strong, unique passwords for the root and admin accounts; strong `NWM_JWT_SECRET` and `NWM_DB_PASSWORD` in `.env` (file mode `600`).
- [ ] SSH uses keys; password login disabled; `ufw` allows your LAN only.
- [ ] Unattended security updates enabled on the box.
- [ ] The ISP router's admin password changed from the default.
- [ ] The ingest API key is known only to your agents; rotate it if a machine is lost (**Settings → Collector agents → Rotate key**).
- [ ] Household members know that DNS lookups and data usage are recorded.

**The API uses plain HTTP on your LAN.** On a trusted home LAN that is usually acceptable; logins and tokens travel unencrypted across it. If others you do not trust share the network, put an HTTPS reverse proxy (for example Caddy or nginx with a certificate) in front of port 8081.

**Account protections built in:** passwords are stored hashed (bcrypt); five wrong passwords lock the account for 15 minutes; repeated failures from one address raise a critical alert; refresh tokens rotate on each use; changing or resetting a password signs that user out everywhere.

## 9.6 Remote access

Do not expose the monitor to the internet. To reach it away from home, use a VPN into your home:
- **Tailscale** (easiest): install on the box and on your phone/laptop, then open `http://<box-tailscale-ip>:8081`.
- **WireGuard** on the box or router, if you prefer to self-host.

Telegram alerts work from anywhere without a VPN.

## 9.7 Changing secrets

| Secret | How |
|---|---|
| **Root/admin password you know** | UI → Account → Change password |
| **`NWM_JWT_SECRET`** | Edit `.env`, then `docker compose up -d`. Everyone is signed out and must log in again. |
| **Database password** | Both the database and `.env` must change: `docker compose exec db psql -U nwmonitor -d nwmonitor -c "ALTER USER nwmonitor PASSWORD 'newpassword';"`, then put the same value in `.env` and run `docker compose up -d`. |
| **Ingest API key** | UI → Settings → Collector agents → Rotate key; update each agent's `/etc/nwm-agent.env`. |
| **Telegram token** | Revoke in @BotFather (`/revoke`), then enter the new token in Settings → Alerting. |

## 9.8 Recover a lost root password

There is no "forgot password" screen by design. Anyone with shell access to the box can reset it directly in the database. Do this on the monitor box.

1. Create a bcrypt hash of the new password (needs `apache2-utils`):
   ```
   $ sudo apt install -y apache2-utils
   $ HASH=$(htpasswd -bnBC 12 "" 'ChooseANewStrongPassword' | tr -d ':\n')
   ```
   (Typing the password on the command line stores it in shell history; clear it afterwards with `history -c` or use a space-prefixed command.)
2. Write it to the root user and clear any lockout and old sessions:
   ```
   $ docker compose exec db psql -U nwmonitor -d nwmonitor \
       -c "UPDATE users SET password_hash='$HASH', failed_logins=0, locked_until=NULL WHERE role='ROOT';" \
       -c "UPDATE refresh_tokens SET revoked=true;"
   ```
3. Sign in with the new password, then change it again through the UI.

Root can reset any admin's password from **Users**, so this is only for the root account itself.

## 9.9 Start again from scratch

To delete **all data** (users, devices, history, settings) while keeping the installation:
```
$ docker compose exec db psql -U nwmonitor -d nwmonitor -c \
  "TRUNCATE refresh_tokens, web_activity, daily_usage, alerts, devices, app_settings, users CASCADE;"
```
The next visit shows the Welcome screen to create a new root user.

## 9.10 Uninstall

```
$ docker compose down -v               # removes containers and the database volume
$ sudo systemctl disable --now nwm-agent && sudo rm /etc/systemd/system/nwm-agent.service /etc/nwm-agent.env
$ sudo rm -rf /opt/nwm-agent
$ pihole uninstall                      # if you installed Pi-hole
```
Then point the router's DNS back to its default (or to your ISP / public resolvers), otherwise devices will lose DNS when the box is gone. If you built the gateway setup, follow the rollback in [guide 4, section 4.5](04-network-placement.md#45-rollback-plan) first.
