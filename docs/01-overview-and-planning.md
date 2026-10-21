# 1. Overview and planning

Read this before buying or installing anything. It explains what the system can see, what it cannot, and how to choose a setup that matches what you want.

## What the system does

| Capability | What you get |
|---|---|
| Device inventory | Every device on the LAN with name, IP, MAC address, online/offline status, first and last seen |
| New-device alert | An alert (and optional Telegram message) when an unknown device appears |
| Web activity | The domains each device looked up, by day, searchable |
| Data usage | MB downloaded, uploaded and total per device per day |
| Intrusion alerts | IP-address conflicts (possible ARP spoofing), repeated failed logins to the app, inbound connection attempts and port scans from outside |
| Connectivity watch | Alerts when the internet or router goes down, and when it returns |
| Multi-user | One Root user and any number of Admin users |

## How it sees your network

An ISP router shows almost nothing about individual devices. The monitor works by combining several sources running on a **monitor box** that is always on:

```
Devices on your LAN
     │
     ├── ARP / ping scan ────────────────► Device list, new-device alerts      (built in)
     ├── DNS lookups (via Pi-hole) ──────► Web activity per device             (needs Pi-hole)
     ├── Packet counting (agent) ────────► MB per device, inbound probes       (needs the box to see traffic)
     └── Reachability checks ────────────► Internet / router up-down alerts    (built in)
```

| Feature | Source | Network position required |
|---|---|---|
| Device list and alerts | Built-in scanner | Box on the same LAN |
| Internet/router status | Built-in | Box on the same LAN |
| Web activity | Pi-hole DNS log | Devices must use the box for DNS |
| Data usage per device | Collector agent | Box must **see all devices' traffic** (gateway or mirrored port) |
| Inbound attempts / port scans | Collector agent or forwarded logs | Position on the **WAN side** of a router you control |

## What it cannot do

Be clear about these limits before you start:

1. **HTTPS hides page paths.** You can see that a phone looked up `youtube.com`, not which video.
2. **DNS can be bypassed.** Devices using DNS-over-HTTPS or "Private DNS" (common in Chrome, Firefox and Android) skip Pi-hole and will not appear in web activity unless you disable that setting on the device.
3. **NAT hides most outside attacks.** Your ISP's NAT drops nearly all unsolicited inbound traffic before it reaches your home. The system can only report probes it can actually observe, so external-intrusion detection needs the box on the WAN side of a router you control.
4. **Randomized MAC addresses.** Modern phones use a different (private) MAC per Wi-Fi network, and some rotate it. Such a device may appear as new after it rotates. These are marked with a dice icon in the UI.
5. **It does not block anything.** It observes and alerts. (Pi-hole can block ad/tracker domains, which is separate.)
6. **Wi-Fi extenders in router mode hide devices.** Devices behind an extender running its own network appear as one device or are invisible to scans. Use access-point or bridge mode for extenders (see guide 4).

## Choose your setup

Pick the **lowest stage that satisfies you**. You can always add the next one later.

| Stage | Gives you | Effort | Changes your network? |
|---|---|---|---|
| **1. Box beside the router** | Devices, new-device and connectivity alerts | About 1 hour | No |
| **2. + Pi-hole** | Web activity per device | +30 min | Router DNS setting |
| **3a. + Box as gateway** | Data per device, WAN-side intrusion events | Half a day | Yes: new topology, possible second router/AP |
| **3b. + Mirror port** | Data per device | 1 to 2 hours | Needs a managed switch |

Most households are well served by Stage 1 or 2. Stage 3 is for people comfortable with networking; it adds a failure point (if the box is down, so is the internet in 3a).

## What you need

**Hardware (one of):**

| Option | Spec | Notes |
|---|---|---|
| Raspberry Pi 4 or 5 | 4 GB RAM or more, 64-bit OS, quality PSU, case, 32 GB+ A2-rated microSD **or** USB SSD | Low power, silent. See [guide 2](02-raspberry-pi.md). |
| Mini PC / old PC / laptop | 64-bit x86, 4 GB RAM, wired Ethernet | See [guide 3](03-pc-or-mini-pc.md). |

Plus: an **Ethernet cable** (a wired connection is strongly recommended over Wi-Fi), and for Stage 3a a **second network interface** (a USB 3 gigabit Ethernet adapter works) and a Wi-Fi access point.

**Resource use:** the stack (Postgres + Java backend + web UI) uses roughly 600 to 900 MB RAM at idle. Pi-hole adds little. 4 GB RAM is comfortable; 2 GB is possible but tight.

**Accounts and access:**
- Access to your ISP router's admin page (address and password are usually printed on the router).
- Optional: a Telegram account for phone alerts.

**Skills:** you should be comfortable typing commands in a terminal and logging in over SSH. The guides spell out each step.

## Time estimate

| Task | Time |
|---|---|
| Prepare the Pi/PC | 30 to 60 min |
| Install and first scan | 30 min (first Docker build takes 10 to 20 min on a Pi) |
| Pi-hole | 30 min |
| Agent / gateway | 2 to 4 hours |

## Next

- Raspberry Pi: [guide 2](02-raspberry-pi.md)
- PC or mini PC: [guide 3](03-pc-or-mini-pc.md)
