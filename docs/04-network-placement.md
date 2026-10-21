---
title: "Placing the monitor in your network"
description: "Where to connect the monitor box, how to configure your ISP router, and how to build a gateway setup safely with a rollback plan."
slug: network-placement
series: "Home Network Monitor"
order: 4
tags: [networking, router, nftables]
updated: 2026-10-22
---

# 4. Placing the monitor in your network

Where you connect the monitor box decides what it can see. This guide explains the options, how to configure your ISP router for each, and how to build the "box in front of your devices" (gateway) setup.

## 4.1 Know your starting point

Collect these facts first; you will need them throughout.

| Fact | How to find it | Example |
|---|---|---|
| Router (gateway) IP | Linux: `ip route \| grep default`; macOS: `route -n get default`; Windows: `ipconfig` | `192.168.1.1` |
| Subnet | Usually the router IP with `/24` | `192.168.1.0/24` |
| Router admin URL and password | Printed on the router label, or set by you | `http://192.168.1.1` |
| Does the router allow DHCP DNS and reservations? | Look in its admin pages (see 4.3) | |
| Is your WAN IP public or CGNAT? | Compare the WAN IP shown in the router admin with what <https://ifconfig.me> shows. If they differ, or the router's is in `100.64.0.0/10`, `10.x` or `192.168.x`, you are behind carrier-grade NAT. | |

If you are behind CGNAT, the internet generally **cannot reach your home network at all**, so there are no external connection attempts to detect. That is a safe position, but it means WAN-side intrusion monitoring has nothing to show.

## 4.2 The setups

### Setup A: box beside the router (stages 1 and 2)

```
Internet ── ISP router ──┬── Wi-Fi devices
                         ├── wired devices
                         └── monitor box (Ethernet)
```

Plug the box into a LAN port on the ISP router. Nothing else changes.

| You get | You do not get |
|---|---|
| Device list, online/offline, new-device alerts, connectivity alerts | Data per device |
| Web activity, if devices use the box's Pi-hole for DNS | WAN-side intrusion events |

This is the right starting point for almost everyone.

### Setup A2: box behind your own router

```
Internet ── ISP router (Wi-Fi off) ──WAN── YOUR ROUTER ──┬── Wi-Fi and wired devices
                                                         └── monitor box
```

Use this when the ISP router **locks DHCP and DNS settings**, so you cannot point devices at Pi-hole. A basic router of your own, placed behind the ISP one, gives you that control. You get everything Setup A gives, including web activity, for the devices on your router. The cost is double NAT. Full steps, and what Airtel, Tata Play Fiber and JioFiber users commonly report, are in [guide 12](12-isp-router-guide.md).

### Setup B: box as the gateway (stage 3a)

```
Internet ── ISP router ──(eth0 "WAN")── MONITOR BOX ──(eth1 "LAN")── switch ──┬── Wi-Fi access point
                                                                              └── wired devices
```

The box sits between the ISP router and every device. It sees all traffic, so the agent can count bytes per device, and Pi-hole sees every DNS query. It needs:

- **Two network interfaces.** A Pi has one; add a USB 3 gigabit Ethernet adapter.
- A **Wi-Fi access point** (or a router in AP mode) connected to the box's LAN side. The ISP router's own Wi-Fi is switched off (or kept only for guests/IoT devices that you accept will not be monitored).
- A **DHCP server** on the box (dnsmasq, or Pi-hole's DHCP).

| You get | Trade-offs |
|---|---|
| Everything: devices, web activity, data per device | If the box is off, **the whole house loses internet** |
| WAN-side visibility, if the ISP gives you inbound reachability | Double NAT (ISP router and box) can affect gaming, port forwarding and some VoIP |
| | More setup, more to go wrong. Plan a rollback (4.5). |

### Setup C: mirror port (stage 3b)

```
Internet ── ISP router ── MANAGED SWITCH ──┬── devices / access points
                              │ (mirror port)
                              └── monitor box (second NIC, listens only)
```

A managed switch copies all traffic to one port (called port mirroring or SPAN). The box listens on that port. Nothing is in the data path, so there is **no risk to your internet**. It needs a managed switch that supports mirroring, and the box needs a NIC dedicated to the mirror port (set `IFACE` for the agent). Wi-Fi traffic is visible only if the access point connects through the mirrored switch.

### Setup D: your own router

If you replace the ISP router's routing role with an OpenWrt, pfSense or similar router, you get gateway visibility without a custom Linux gateway. This project does not include a collector for those platforms; use their accounting and push counters to the [ingest API](11-api-reference.md#ingest-agents-only).

### Which one?

| If you want... | Choose |
|---|---|
| Alerts for new devices, simplest and safest | A |
| Sites visited, but the ISP router locks DNS/DHCP | A2 (or B) |
| Plus sites visited | A + Pi-hole ([guide 6](06-pihole.md)) |
| Plus MB per device, accept some network work | B (or C if you own a managed switch) |
| Zero risk of breaking the internet | A or C |

## 4.3 Configuring your ISP router

Provider-specific notes (Airtel, Tata Play Fiber, JioFiber, and how to test what your own unit allows) are in [guide 12](12-isp-router-guide.md). The general steps follow.

Menu names differ between router brands and firmware versions, so look for the equivalent of the items below. Take a screenshot of each page before you change it.

**1. DHCP reservation (all setups).** In *LAN*, *DHCP* or *Address reservation*, bind the monitor box's MAC address to a fixed IP such as `192.168.1.2`. If the router offers no such option, set a static IP on the box instead (guides 2 and 3).

**2. DNS handed to devices (needed for web activity).** Look for *DHCP server → DNS server* (sometimes *Primary DNS* under *LAN* or *DHCP*). Set the **primary and only** DNS to the box's IP. Do not add a second public DNS such as 8.8.8.8: devices will use it and bypass Pi-hole.

If the router **does not let you change DHCP DNS** (common on ISP-supplied routers), you have three options:
1. Turn off the router's DHCP server and let Pi-hole's DHCP serve the network. This makes the box critical: if it is off, new devices get no address.
2. Set DNS manually on each device you care about (Wi-Fi settings → IP/DNS).
3. Use Setup B, where the box provides DHCP and DNS naturally.

**3. IPv6.** Many ISP routers hand out IPv6 DNS servers in addition to IPv4, which lets devices skip Pi-hole. In the router's IPv6/LAN settings, disable IPv6 DNS advertisement (RDNSS), or disable IPv6 on the LAN if you do not need it.

**4. Wi-Fi extenders.** Extenders that create their own network hide the devices behind them. Set extenders to **access point** or **bridge** mode, connected to the main network by Ethernet where possible, so devices keep addresses from the main router. If an extender must stay in repeater mode, devices behind it may appear under the extender's MAC.

**5. Guest network / client isolation.** Guest Wi-Fi and "AP isolation" put devices on a separate segment that the box cannot scan. Devices there will not appear.

**6. Bridge mode (optional, Setup B only).** Some ISPs will put their router into bridge mode on request, so your own router/box receives the public address directly. Not all ISPs allow it and it can disable their TV/phone services. Ask your provider; do not attempt unsupported changes.

## 4.4 Building the gateway (Setup B)

> **Read the whole section and the rollback plan (4.5) first. Do this when nobody is working or studying online, at a time you can spend an hour with the internet possibly down.** The commands below are standard Linux networking, but your hardware differs, so check each result before moving on.

Assumptions: the box runs Debian/Ubuntu/Raspberry Pi OS; the built-in NIC is `eth0` and connects to the ISP router (this is the "WAN" side and keeps getting its address by DHCP); the USB adapter appears as `eth1` and connects to your switch/AP (the "LAN" side). Check names with `ip -br link`; yours may be `enx...` or `enp...`. We use a **new** LAN subnet, `192.168.50.0/24`, so it does not clash with the ISP router's.

### Step 1: Give the LAN interface an address

Raspberry Pi OS (NetworkManager):
```
$ sudo nmcli connection add type ethernet ifname eth1 con-name lan \
    ipv4.method manual ipv4.addresses 192.168.50.1/24 ipv6.method disabled
$ sudo nmcli connection up lan
```
Ubuntu Server (Netplan): add `eth1` to `/etc/netplan/*.yaml`:
```yaml
    eth1:
      dhcp4: false
      addresses: [192.168.50.1/24]
```
then `sudo netplan try`.

Check: `ip -br addr show eth1` shows `192.168.50.1/24`.

### Step 2: Turn on IP forwarding

```
$ echo 'net.ipv4.ip_forward=1' | sudo tee /etc/sysctl.d/99-forward.conf
$ sudo sysctl --system
$ sysctl net.ipv4.ip_forward        # must print 1
```

### Step 3: NAT the LAN out through the ISP router

Install nftables and add a masquerade rule:
```
$ sudo apt install -y nftables
$ sudo tee -a /etc/nftables.conf >/dev/null <<'EOF'

table ip nwm_nat {
  chain postrouting {
    type nat hook postrouting priority srcnat; policy accept;
    oifname "eth0" masquerade
  }
}
EOF
$ sudo nft -c -f /etc/nftables.conf && sudo systemctl enable --now nftables
$ sudo nft list table ip nwm_nat
```
Replace `eth0` with your WAN interface name. If you also use `ufw`, allow forwarding: set `DEFAULT_FORWARD_POLICY="ACCEPT"` in `/etc/default/ufw` and `sudo ufw reload`.

### Step 4: DHCP and DNS for the LAN

**Without Pi-hole**, use dnsmasq:
```
$ sudo apt install -y dnsmasq
$ sudo tee /etc/dnsmasq.d/lan.conf >/dev/null <<'EOF'
interface=eth1
bind-interfaces
dhcp-range=192.168.50.100,192.168.50.250,255.255.255.0,12h
dhcp-option=option:router,192.168.50.1
dhcp-option=option:dns-server,192.168.50.1
server=1.1.1.1
server=8.8.8.8
EOF
$ sudo systemctl restart dnsmasq
```

**With Pi-hole**, do not install dnsmasq (Pi-hole includes its own DNS and DHCP). Instead, in Pi-hole's admin page go to *Settings → DHCP*, enable the DHCP server with range `192.168.50.100`–`192.168.50.250`, router `192.168.50.1`, and in *Settings → DNS → Interface settings* listen on `eth1`. See [guide 6](06-pihole.md).

### Step 5: Test with one device before moving everything

1. Connect a laptop to the switch on the LAN side (or to the AP).
2. It should receive an address like `192.168.50.1xx` with router and DNS `192.168.50.1`.
3. Check internet works: open a web page; `ping 8.8.8.8` and `ping example.com`.
4. On the box, watch it appear: `sudo dnsmasq` logs or Pi-hole's DHCP leases.

### Step 6: Move the rest

1. Connect your access point to the LAN-side switch. Configure it in **AP/bridge mode** (no DHCP, no NAT), with a static address such as `192.168.50.2` for its admin page. Use the Wi-Fi name and password your devices already know, so they reconnect without changes.
2. Turn off Wi-Fi on the ISP router (or leave it for guests/IoT, accepting those are unmonitored).
3. Move wired devices to the LAN-side switch.

### Step 7: Tell the monitor about the new network

The built-in scanner auto-detects the subnet from the interface with the default route, which here is the **WAN** side. So set it manually in the app: *Settings → Network access*:
- Subnet: `192.168.50.0/24`
- Gateway: `192.168.50.1`

For the agent set `LAN_PREFIX=192.168.50.` and (optional) `IFACE=eth1`. See [guide 7](07-collector-agent.md).

## 4.5 Rollback plan

Keep this where you can find it if the internet stops working:

1. Unplug the box from the network.
2. Plug the switch/AP (or a single device) **directly into a LAN port on the ISP router**, and re-enable the router's Wi-Fi if you turned it off.
3. Devices get addresses from the ISP router again and work normally.

Because the ISP router's configuration was never changed in Setup B, this restores service in minutes. Test the rollback once so you know it works.

## 4.6 Summary of settings per setup

| Setting | A | B | C |
|---|---|---|---|
| Box IP | reserved on ISP router (A) or on your router (A2) | LAN side `192.168.50.1` | reserved on ISP router |
| App subnet / gateway | auto or `192.168.1.0/24` / `192.168.1.1` | `192.168.50.0/24` / `192.168.50.1` | as A |
| DNS for devices | box IP via router DHCP (if allowed) | box IP via box's DHCP | as A |
| Agent `LAN_PREFIX` | n/a | `192.168.50.` | `192.168.1.` |
| Agent `IFACE` | n/a | `eth1` (LAN) or leave unset | the mirror-port NIC |

Next: [Install and configure the application](05-install-and-configure.md).
