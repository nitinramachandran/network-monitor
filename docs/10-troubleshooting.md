# 10. Troubleshooting

Start here: **UI → Connectivity** shows the state and last error of the scanner, Pi-hole collector and agents. Then find your symptom below.

## First diagnostic commands

Run on the monitor box, in the repository folder:
```
$ docker compose ps                                  # are db, backend, frontend all running?
$ docker compose logs --tail=100 backend             # last backend messages
$ curl -s http://localhost:8080/actuator/health      # {"status":"UP",...}
$ ip -br addr                                        # box addresses
$ ip route | grep default                            # default gateway
$ sudo ss -lntp | grep -E ':(8080|8081|5432|53|80) '   # who is listening where
```

## Install and start-up

| Symptom | Cause and fix |
|---|---|
| `NWM_JWT_SECRET: set a random 32+ char secret in .env` | `.env` missing or the variable empty. Redo guide 5, section 5.2. |
| Build very slow or killed on a Pi | Out of memory. Use a 4 GB+ Pi, close other workloads, add swap (`sudo dphys-swapfile swapoff; sudo sed -i 's/^CONF_SWAPSIZE=.*/CONF_SWAPSIZE=2048/' /etc/dphys-swapfile; sudo dphys-swapfile setup; sudo dphys-swapfile swapon`), and retry. |
| `docker: permission denied` | You are not in the `docker` group, or have not logged out and in since adding yourself. |
| `FATAL: role "nwmonitor" does not exist` / `password authentication failed` | The backend is talking to a database server that does not have these credentials. With Compose this happens if `.env`'s `NWM_DB_PASSWORD` was changed after the first start (the database kept the old one) or a different Postgres owns port 5432. Fix the password (guide 9.7) or, if you have no data to keep, `docker compose down -v` and start again. |
| `port is already allocated` / `address already in use` | Another service uses 8080, 8081 or 5432. Find it with `sudo ss -lntp`, stop it, or change the port mapping in `docker-compose.yml`. |
| Backend keeps restarting | `docker compose logs backend` and read the first error. Common: database not ready yet (wait a minute), wrong DB password, timezone name invalid. |
| Page loads but shows "Cannot reach the monitor service" | Backend down or proxy failing: check `docker compose ps` and the health URL. |
| `403` when creating the root user or using the API from a browser | CORS: the UI was opened from an origin the backend does not allow. Use the bundled UI at `http://<box-ip>:8081`, or add your origin to `NWM_CORS_ORIGINS`. In development restart `npm run dev` after config changes. |
| `409 Setup already completed` | A root user already exists; sign in. To start over see guide 9.9. |
| Signed out after every restart | `NWM_JWT_SECRET` is not set (development) or was changed. |

## Scan finds no devices

| Check | Fix |
|---|---|
| Subnet wrong | Settings → Network access: enter your subnet. **In the gateway setup the auto-detected subnet is the WAN side; enter the LAN subnet (e.g. `192.168.50.0/24`).** |
| Box is not on the same LAN as your devices | Guest Wi-Fi or "AP isolation" puts devices on another segment. Connect the box to the main LAN. |
| Backend not using host networking | On Linux with the supplied compose file it is. Docker Desktop (macOS/Windows) cannot do this; see the README, section 12. |
| ARP table empty | On the box: `cat /proc/net/arp` (Linux) should list neighbours after a scan. If it is empty, the box cannot reach the LAN (cable, VLAN). |
| macOS | Allow your terminal under System Settings → Privacy & Security → **Local Network**. |
| Devices asleep | They appear when they wake or send traffic. Phones in power-save may ignore pings; the scan relies on ARP replies, which they normally answer. |
| Firewall on the box | A firewall must not block outbound ping/ARP. Default `ufw` rules do not. |

Run `docker compose logs backend | grep -i scan` for scan errors.

## A device looks wrong

| Symptom | Explanation |
|---|---|
| A phone shows as "new" again | It rotated its randomized MAC. Disable private Wi-Fi addresses for your network, then trust and label the new entry (forget the old one). |
| Devices behind an extender are missing | Extender in router/repeater mode hides them. Put it in AP/bridge mode (guide 4). |
| Two entries for one device | Wi-Fi and Ethernet have different MACs, or the MAC changed. Forget the stale one. |
| Name shows as a MAC address | No hostname was found; set a **Label**. |
| Device stuck offline though connected | It does not answer ARP/ping while idle; it becomes online again when it generates traffic. Increase *Mark offline after* in settings if it flaps. |

## Web activity is empty or incomplete

| Check | Fix |
|---|---|
| Connectivity shows the collector failing | Read the message. "Pi-hole rejected the password": set the correct password. Connection refused/timeout: wrong URL or Pi-hole not running (`pihole status`). |
| Pi-hole's own Query Log is empty for your device | The device is not using Pi-hole for DNS (router DNS not changed, lease not renewed, DoH/Private DNS, IPv6 DNS). See guide 6, sections 6.3 and 6.4. |
| Every query shows one client IP | Devices ask the router, which forwards to Pi-hole. See guide 6, section 6.6. |
| Lookups appear under no device | The client IP is not a discovered device yet: run a scan; give it a DHCP reservation. |
| Some devices missing | IoT devices that hard-code 8.8.8.8 or use DoH skip Pi-hole. |
| Test works, no data | The source is set to **Disabled** or **Simulated**; set it to Pi-hole and Save. |
| Old data vanished | Retention: lookups older than the configured number of days (default 30) are purged nightly. |

## Data usage is empty or wrong

| Symptom | Cause and fix |
|---|---|
| Empty | No agent reporting. Install it ([guide 7](07-collector-agent.md)); check Connectivity → *Agent: data usage*. |
| Agent runs, nothing arrives | The box cannot see other devices' traffic (setup A). Needs gateway or mirror setup. |
| `report failed ... 401` | Wrong or rotated API key. |
| Only the box itself has usage | Same as above: the box sees only its own traffic. |
| Totals lower than the ISP's | The Python agent drops packets under load; the ISP counts all traffic including unmonitored devices (guest Wi-Fi, devices on the ISP router's Wi-Fi). |
| Day boundary looks off | `NWM_TIMEZONE` is not your timezone. Set it in `.env` and `docker compose up -d`. Past data keeps its original day. |

## Alerts and Telegram

| Symptom | Fix |
|---|---|
| Test message fails with 401/404 | Wrong bot token (copy it again from BotFather). |
| Test fails with "chat not found" | Wrong chat id, or you never pressed **Start** on the bot. For groups the id is negative. |
| No Telegram for new devices | The type is disabled, the severity is below *Notify from*, the cooldown grouped it into an earlier alert, or Telegram is not enabled/saved. |
| Too many alerts | Raise the cooldown, raise the minimum severity, or disable types (guide 8.3). |
| "Internet down" never reached the phone | Expected: the message cannot be sent while the internet is down. |
| No external-intrusion alerts | You are probably behind CGNAT (nothing can reach you), or the agent is not on the WAN side / `PROTECTED_IPS` is not set. |

## Network problems after changing the network

| Symptom | Fix |
|---|---|
| Whole house offline after the gateway setup | Follow the rollback in guide 4, section 4.5. |
| Devices have no DNS after removing Pi-hole | Restore the router's DNS setting. |
| Devices keep old DNS | Renew the lease (reconnect Wi-Fi). |
| New devices get no address | The DHCP server (router, dnsmasq or Pi-hole) is off or its range is exhausted. |

## Still stuck?

Collect this and ask for help (remove secrets first):
```
$ docker compose ps
$ docker compose logs --tail=200 backend
$ uname -a && cat /etc/os-release | head -3
$ ip -br addr && ip route
```
Include which setup (A, B or C) you are using, and what the Connectivity page shows.
