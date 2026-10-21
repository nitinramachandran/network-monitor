---
title: "ISP router guide: Airtel, Tata Play Fiber and JioFiber"
description: "How to test what your provider router allows, what users commonly report for Airtel, Tata Play Fiber and JioFiber, and workarounds when it is locked."
slug: isp-router-guide
series: "Home Network Monitor"
order: 12
tags: [isp, router, airtel, tata-play-fiber, jiofiber, india]
updated: 2026-10-22
---

# 12. ISP router guide: Airtel, Tata Play Fiber and JioFiber

Home fibre providers in India supply their own router or ONT (optical network terminal), and what you are allowed to change on it varies a lot. This guide helps you find out what **your** unit allows, and gives a ranked set of workarounds when it is locked down.

> **How reliable is this guide?** There is no official provider documentation for most of these settings. What follows combines general networking knowledge with **reports from users on public forums** (linked at the end). Router models, firmware versions and provider policies differ by city and change over time, and providers can update routers remotely. Treat every provider-specific statement as "commonly reported, verify on your unit". The test procedure in section 12.2 tells you the truth about your own router in about ten minutes.

## 12.1 Identify your router

1. Read the label on the router or ONT: model name, admin address and (usually) the admin username and password. Do not assume defaults; many units have a unique password printed on the label.
2. Find the address from a connected computer: `ip route | grep default` (Linux), `route -n get default` (macOS) or `ipconfig` (Windows). It is often `192.168.1.1`, but check.
3. Log in from a browser at that address. Note the model and firmware version; they decide what is possible.
4. Take a screenshot of each page you change, so you can undo it.

Some providers also manage the router remotely (the standard mechanism is called TR-069). Where that is the case, a setting you change can be reset later by the provider.

**Do not factory-reset a provider router** unless the provider told you to. A reset can remove provisioning that your connection depends on and may need a technician visit to restore.

## 12.2 Test what your router allows

Go through this list on your router's admin pages. The answers decide which setup in [guide 4](04-network-placement.md) you can use.

| # | Test | Where to look | Why it matters |
|---|---|---|---|
| 1 | Can you set a **DHCP DNS server** (the DNS that the router hands to devices)? | LAN, DHCP, or DNS settings | If yes: Pi-hole works with the simplest setup |
| 2 | Is the DHCP section editable, or **greyed out**? | LAN, DHCP | Greyed out means you cannot change DNS there or turn DHCP off |
| 3 | Can you **reserve an address** for a device (static lease)? | DHCP, address reservation | Gives the monitor box a fixed IP |
| 4 | Is there a **bridge mode** / "ONT only" option? | WAN, Advanced, Network | Lets your own router take over |
| 5 | Can you **turn off IPv6 DNS advertisement**, or IPv6 entirely? | IPv6, LAN | Stops devices skipping Pi-hole through IPv6 |
| 6 | What is the **WAN IP** shown, and is it the same as <https://ifconfig.me>? | Status or WAN page | If they differ, you are behind carrier-grade NAT (CGNAT): nothing from the internet can reach you |

Record the results; the next section uses them.

## 12.3 What users commonly report, by provider

These are **reports, not guarantees**. Use them to know what to try first.

### Airtel (Xstream Fiber)
- **DHCP and DNS settings are frequently reported as locked or greyed out** on Airtel-supplied routers, and users say the unit is controlled by the provider remotely. If tests 1 and 2 fail, plan for a workaround rather than fighting the firmware.
- Users report that **bridge mode is available only if support enables it**, after which you connect your own router and log in with details the provider gives you. This is not something to attempt without the provider's confirmation, and it may affect any bundled services.
- Unit models vary by region and over time; read your label.

### Tata Play Fiber
- Some users report that **custom DNS and LAN address settings can be changed** on their unit. Others say the options depend on the ONT model and firmware, and that firmware may not be updated automatically (a faulty unit can be swapped for one with a newer firmware).
- There is little reliable information about bridge mode or DHCP reservation. Ask support, and rely on test 4 above.
- Some providers bind your connection to the registered router's MAC address. If you put your own router directly on the provider line, you may need to clone that MAC. Ask the provider.

### JioFiber
- Users report that **DNS can be changed on the Jio router** but that many other settings are hidden in its web panel.
- **Bridge mode is generally reported as not user-accessible.** One widely shared method (enabling a bridge on a LAN port with a specific VLAN ID, then tagging your own router's WAN port with the same ID) worked for some people, while others reported that **the connection dropped the moment they enabled it**. Do not try this while anyone depends on the connection and without a way back (see the rollback in [guide 4, section 4.5](04-network-placement.md#45-rollback-plan)). Using your own router behind the Jio router (below) avoids the risk entirely.
- Users report that Jio uses **CGNAT**: many customers share a public address, so inbound connections and port forwarding are not possible. See test 6.

### Other providers (ACT, BSNL, regional ISPs, and so on)
Behaviour varies by model. Apply the test in 12.2 and use the same workarounds. If you confirm exact steps for your provider and model, please contribute them (section 12.8).

## 12.4 Pick your path

Based on your test results:

| Your router... | Choose |
|---|---|
| Lets you set DHCP DNS (tests 1 and 3 pass) | **Setup A** in guide 4 plus [Pi-hole](06-pihole.md). Simplest. |
| Locks DHCP/DNS (tests 1 or 2 fail) and you want web activity | **Setup A2** below (your own router behind the provider's), or **Setup B** (box as gateway) |
| Locks DHCP/DNS and you only want device alerts | **Setup A** works unchanged: new-device and connectivity alerts need no DNS change |
| You want data usage per device | **Setup B** or **C** in guide 4 |
| Bridge mode is available and supported by the provider | Use your own router in router mode; optional |

A locked provider router does **not** stop device discovery. The built-in scanner reads the LAN directly, so Stage 1 of the install works with any router.

## 12.5 Setup A2: monitor behind your own router

When the provider router will not let you change DNS or DHCP, put **your own consumer router** behind it and make that the router your devices use.

```
Internet ── provider router (Wi-Fi OFF) ──WAN── YOUR ROUTER ──┬── Wi-Fi devices
                                                              ├── wired devices
                                                              └── monitor box
```

**What you need:** a basic router that lets you set DHCP DNS and address reservations (most retail routers do), and an Ethernet cable.

**Steps**
1. Connect a LAN port on the provider router to the **WAN/Internet** port of your router.
2. On your router, use **router mode** (the default) with a LAN subnet **different from the provider's**: if the provider uses `192.168.1.0/24`, use `192.168.50.0/24` for yours. (Two routers on the same subnet cause conflicts.)
3. Set your router's **DHCP DNS** to the monitor box's IP (once Pi-hole is installed; until then leave defaults).
4. Reserve a fixed address for the monitor box on **your** router, for example `192.168.50.2`.
5. Move your devices to your router's Wi-Fi and LAN ports, using the Wi-Fi name and password they already know.
6. Switch off the provider router's Wi-Fi (or keep it only for guests or devices you do not need to monitor).
7. Plug the monitor box into your router, then follow [guide 5](05-install-and-configure.md). In the app set the subnet to `192.168.50.0/24` and the gateway to your router's LAN address, e.g. `192.168.50.1`.

**What you get:** everything from Setup A (devices, alerts, and Pi-hole web activity, since you control DHCP DNS). Only devices on **your** router are monitored; anything left on the provider's network is invisible to the scanner.

**Trade-offs:** double NAT (provider router and yours). This is fine for browsing and streaming but can affect game consoles, voice calls and port forwarding. If your provider uses CGNAT these were already limited. Your router's firmware becomes the network's weak point, so keep it updated.

## 12.6 Other workarounds when the router is locked

| Option | How | Notes |
|---|---|---|
| **Per-device DNS** | Set the DNS server to the box's IP in each device's network settings | Works for the few devices you care about; each device must be done by hand |
| **Pi-hole as DHCP server** | Turn off the provider's DHCP and enable DHCP in Pi-hole | Only possible if the provider's DHCP is not greyed out. Makes the box critical: if it is off, new devices get no address. |
| **Box as gateway** (Setup B) | Build it behind the provider router; guide 4, section 4.4 | Works whatever the provider locks, because the provider router is not reconfigured. Brings the biggest visibility, and the biggest risk. |
| **Ask the provider** | Request DNS/DHCP changes or bridge mode via support | Outcomes vary; changes may be reverted by remote management |

## 12.7 IPv6, extenders and mesh

- Many provider routers advertise **IPv6 DNS** to devices in addition to IPv4, so devices can bypass Pi-hole. Turn off IPv6 DNS advertisement or IPv6 on the LAN if the router allows (test 5). If it does not, web activity will miss some devices; Setup A2 gives you that control on your own router.
- **Extenders and mesh nodes** supplied by providers often run their own network. Devices behind them can appear under one address or not at all. Use access-point (bridge) mode, ideally wired, so devices keep addresses from the main router. See [guide 4, section 4.3](04-network-placement.md#43-configuring-your-isp-router).
- **Guest networks** are usually isolated, so devices there are not visible to the scanner.

## 12.8 Help improve this guide

Provider firmware changes, so confirmed details from real units are valuable. When you have tested your router, record it in this format and send it as an issue or pull request to the project:

| Field | Example |
|---|---|
| Provider and plan | (name) |
| Router/ONT model and firmware | (from the label and status page) |
| Date tested | 2026-10-22 |
| Test 1: DHCP DNS editable | yes / no / greyed out |
| Test 3: address reservation | yes / no |
| Test 4: bridge mode | yes / no / needs support |
| Test 6: CGNAT | yes / no |
| What worked | (steps) |
| What did not | (what happened) |

Do **not** include passwords, serial numbers, MAC addresses or your public IP.

## 12.9 Sources

Community reports used for the provider notes above (retrieved October 2026). They are user discussions, not official documentation, and may be out of date:

- [Airtel router LAN DHCP greyed out?](https://techenclave.com/t/airtel-router-lan-dhcp-greyed-out/275596) (TechEnclave)
- [Are Tata Play and Jio Fiber routers as locked as Airtel Fiber's?](https://techenclave.com/t/are-tata-play-and-jio-fiber-routers-as-locked-as-airtel-fibers/274781) (TechEnclave)
- [Can Jio Fiber modem be made into a dumb modem (bridge mode)?](https://techenclave.com/t/can-jio-fiber-modem-be-made-into-a-dumb-modem-bridge-mode/244299) (TechEnclave)
- [How to connect to Jio Fiber ONT router in bridge mode?](https://forum.openwrt.org/t/how-to-connect-to-jio-fiber-ont-router-in-bridge-mode/99331) (OpenWrt forum)

Next: back to [Placing the monitor in your network](04-network-placement.md) or on to [Install and configure](05-install-and-configure.md).
