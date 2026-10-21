# 6. Web activity with Pi-hole

Every website visit starts with a DNS lookup ("what is the address of youtube.com?"). Pi-hole is a DNS server that logs each lookup and which device asked. The monitor reads that log and shows the domains each device looked up. Pi-hole also blocks many ad and tracker domains as a bonus.

**What you will see:** domains, with time and device. **What you will not see:** page paths, search terms, or anything inside HTTPS. Devices that bypass your DNS (DNS-over-HTTPS, "Private DNS", VPNs) will not appear.

## 6.1 Requirements

- A monitor box with a fixed IP (the same box is fine).
- **Pi-hole v6.** The monitor's collector uses the v6 API.
- Ability to make devices use the box for DNS (see [guide 4, section 4.3](04-network-placement.md#43-configuring-your-isp-router)).
- Ports: Pi-hole uses 53 (DNS) and 80/443 (admin page). The monitor uses 8080 and 8081, so there is no clash.
- On Ubuntu, free port 53 first ([guide 3, section 3.9](03-pc-or-mini-pc.md#39-port-53-on-ubuntu-only-if-you-will-install-pi-hole)).

## 6.2 Install Pi-hole

On the monitor box:
```
$ curl -sSL https://install.pi-hole.net | sudo bash
```
(You may prefer to download and read the script first; see <https://docs.pi-hole.net/main/basic-install/>.)

The installer asks a few questions:
- **Interface:** choose the wired one (e.g. `eth0`). In the gateway setup, choose the **LAN** interface (`eth1`).
- **Upstream DNS provider:** any you trust (Cloudflare, Quad9, Google). This is where Pi-hole forwards lookups it does not block.
- **Blocklists:** accept the default.
- **Query logging:** **enable**, and choose to show everything. The monitor needs the log.

At the end it shows an admin password; if you missed it, set your own:
```
$ sudo pihole setpassword
```
Open `http://<box-ip>/admin` and log in to confirm it works.

## 6.3 Make devices use Pi-hole

Do this in the order below and test with **one device first**.

1. **Test one device manually.** On a laptop, set DNS to the box IP, then:
   ```
   $ nslookup example.com 192.168.1.2
   ```
   You should get an answer. Refresh Pi-hole's **Query Log**: the laptop's lookup should be listed with its IP.
2. **Set the router's DHCP DNS** to the box IP as the only DNS (see guide 4, 4.3). If the router will not allow it, use one of the alternatives there (Pi-hole as DHCP server, per-device DNS, or the gateway setup).
3. **Disable IPv6 DNS advertisement** on the router if you can, otherwise devices may use the router's IPv6 DNS and skip Pi-hole.
4. **Renew leases.** Devices pick up new DNS only when they renew: reconnect Wi-Fi, toggle airplane mode on phones, or reboot devices.
5. Verify in Pi-hole's **Query Log** that your phone, laptop and TV appear as **different client IPs**. If every query shows a single IP (the router), see 6.6.

## 6.4 Stop devices bypassing Pi-hole

| Device | What to turn off |
|---|---|
| Android | Settings → Network → **Private DNS** → Off (or Automatic) |
| iPhone | iCloud Private Relay off for your Wi-Fi network; avoid DNS-over-HTTPS profiles |
| Chrome / Edge | Settings → Privacy → **Use secure DNS** → Off |
| Firefox | Settings → Privacy → **DNS over HTTPS** → Off |
| Smart TVs and IoT | Some hard-code 8.8.8.8; they will not appear in web activity |

You cannot block these bypasses completely from the Pi-hole alone; in the gateway setup you can additionally redirect all outbound port 53 traffic to the box.

## 6.5 Connect the monitor to Pi-hole

In the monitor UI: **Settings → Web activity source**.

| Field | Value |
|---|---|
| Source | **Pi-hole (v6)** |
| Pi-hole URL | `http://127.0.0.1` if Pi-hole is on the **same box** (the backend uses host networking), otherwise `http://<pihole-ip>` |
| Pi-hole password | The admin password from 6.2 |
| Poll interval | 30 seconds |

1. Click **Test connection**. You should see "Authenticated with Pi-hole".
2. Click **Save**.
3. Browse something on a phone for a minute, then open **Web activity** in the monitor. The lookups should appear under that phone's name.
4. Open **Connectivity**: the **Web activity collector** row should be Healthy with a recent success time.

How it works: every 30 seconds the monitor asks Pi-hole for queries newer than the last one it saw, matches each client IP to a known device, and stores the domain. The first poll after setup starts from about five minutes ago. Queries from IPs the monitor has not discovered yet are skipped (the scan will discover them).

## 6.6 Fixing "everything comes from one IP"

If Pi-hole's Query Log shows only the router's IP as the client, devices are asking the router, and the router is forwarding to Pi-hole. The monitor then cannot tell devices apart. Fix by making devices ask Pi-hole **directly** (router DHCP hands out the box's IP as DNS, not "the router itself as DNS"). Some routers always advertise themselves as the DNS server; in that case use the alternatives in guide 4 (Pi-hole DHCP, or the gateway setup).

## 6.7 Good to know

- **IP changes:** a device is matched by its IP at the time of the lookup. If your router reassigns IPs, a short window of mismatches is possible. Add **DHCP reservations** for the devices you care most about.
- **Retention:** the monitor keeps lookups for the number of days you set (30 by default) and purges nightly. Pi-hole keeps its own history separately.
- **If Pi-hole goes down**, DNS breaks for every device that depends on it. Keep it on a stable box; some people add a second DNS server in the router, but that lets devices bypass Pi-hole, so decide which matters more.
- **Blocked** lookups are marked in the monitor with a red "Blocked" badge.

Next: [Data usage and intrusion agent](07-collector-agent.md) or [Alerts and Telegram](08-alerts-and-telegram.md).
