---
title: "Set up a PC, mini PC or laptop as the monitor box"
description: "Prepare an old PC, mini PC or laptop running Ubuntu or Debian as an always-on network monitor."
slug: pc-or-mini-pc-setup
series: "Home Network Monitor"
order: 3
tags: [linux, ubuntu, docker]
updated: 2026-10-22
---

# 3. Set up a PC, mini PC or laptop as the monitor box

An old desktop, a small fanless mini PC or a retired laptop makes an excellent monitor box: more power than a Pi, and an SSD makes it durable. This guide prepares one running Linux. If you are using a Raspberry Pi, see [guide 2](02-raspberry-pi.md).

> **Operating system:** use Linux (Ubuntu Server 24.04 LTS or Debian 12). The provided Docker Compose setup relies on host networking, which only works on Linux. macOS and Windows can run the application for evaluation but are not suitable permanent monitors; see the README, section 12.

## 3.1 Requirements

| | Minimum | Recommended |
|---|---|---|
| CPU | 64-bit x86 dual core | Any recent mini PC |
| RAM | 4 GB | 8 GB |
| Storage | 32 GB | SSD, 64 GB+ |
| Network | Wired Ethernet | Wired Ethernet; a second NIC for the gateway setup |

A laptop gives you a built-in battery (a mini UPS) but needs the lid settings below.

## 3.2 Install Linux

1. On another computer, download **Ubuntu Server 24.04 LTS** (<https://ubuntu.com/download/server>) or **Debian 12** (<https://www.debian.org/distrib/>).
2. Write the ISO to a USB stick with **Balena Etcher** (<https://etcher.balena.io>) or Rufus.
3. Plug the PC into your router with Ethernet, boot from the USB stick (usually F12, F10, Esc or Del at startup), and run the installer:
   - Use the **entire disk** (this erases it).
   - Create your user with a strong password.
   - Tick **Install OpenSSH server**.
   - Do not install extra "snaps" or featured server packages.
4. Reboot, remove the USB stick, and log in. Find the IP address:
   ```
   $ ip -br addr
   ```
5. From your own computer, continue over SSH: `ssh youruser@<ip>`.

## 3.3 Update and set the timezone

```
$ sudo apt update && sudo apt full-upgrade -y
$ sudo timedatectl set-timezone Asia/Kolkata
$ timedatectl
$ sudo reboot
```
The timezone decides where each day's data usage starts and ends, so set it correctly.

## 3.4 Fixed IP address

Create a **DHCP reservation** for the machine in your router (preferred): note its MAC with `ip -br link`, then bind it to a fixed address such as `192.168.1.2` in the router's DHCP or LAN settings. Reboot and confirm with `ip -br addr`.

If you cannot reserve in the router, set a static address in the OS. Ubuntu Server uses Netplan; create `/etc/netplan/01-static.yaml` (use your interface name from `ip -br link`, for example `enp3s0`, and your own addresses):
```yaml
network:
  version: 2
  ethernets:
    enp3s0:
      dhcp4: false
      addresses: [192.168.1.2/24]
      routes:
        - to: default
          via: 192.168.1.1
      nameservers:
        addresses: [1.1.1.1, 8.8.8.8]
```
```
$ sudo chmod 600 /etc/netplan/01-static.yaml
$ sudo netplan try        # reverts automatically if you lose connectivity
```
Choose an address outside the router's DHCP pool so it cannot clash.

## 3.5 Make it run unattended

**Power loss:** in the BIOS/UEFI setting called *Restore on AC power loss* or *After power failure*, choose *Power on* so the box returns by itself after an outage.

**Laptop lid:** stop the machine sleeping when the lid is closed. Edit `/etc/systemd/logind.conf`:
```
HandleLidSwitch=ignore
HandleLidSwitchExternalPower=ignore
HandleLidSwitchDocked=ignore
```
Then `sudo systemctl restart systemd-logind` (this may end your SSH session; reconnect). Also disable suspend:
```
$ sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
```

**Time sync** should be on by default; check `timedatectl` shows `System clock synchronized: yes`.

## 3.6 Basic hardening

```
$ sudo apt install -y ufw unattended-upgrades
$ sudo ufw default deny incoming
$ sudo ufw default allow outgoing
$ sudo ufw allow from 192.168.1.0/24 to any port 22 proto tcp
$ sudo ufw allow from 192.168.1.0/24 to any port 8080 proto tcp   # API (needed by the agent)
$ sudo ufw allow from 192.168.1.0/24 to any port 8081 proto tcp   # web UI
$ sudo ufw allow from 192.168.1.0/24 to any port 53               # only if installing Pi-hole
$ sudo ufw allow from 192.168.1.0/24 to any port 80 proto tcp     # only if installing Pi-hole
$ sudo ufw enable
$ sudo dpkg-reconfigure -plow unattended-upgrades
```
Replace `192.168.1.0/24` with your subnet. Use SSH keys and turn off password login as shown in [guide 2, section 2.6](02-raspberry-pi.md#26-basic-hardening). Never forward ports from the internet to this machine.

## 3.7 Install Docker and Git

```
$ sudo apt install -y git curl
$ curl -fsSL https://get.docker.com -o get-docker.sh
$ sudo sh get-docker.sh
$ sudo usermod -aG docker $USER
```
Log out and in again, then verify:
```
$ docker run --rm hello-world
$ docker compose version
```
Limit log growth:
```
$ echo '{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }' | sudo tee /etc/docker/daemon.json
$ sudo systemctl restart docker
```

## 3.8 Get the code

```
$ git clone https://github.com/nitinramachandran/network-monitor.git
$ cd network-monitor
```

## 3.9 Port 53 on Ubuntu (only if you will install Pi-hole)

Ubuntu runs a small DNS stub (`systemd-resolved`) on port 53 that can stop Pi-hole starting. Check:
```
$ sudo ss -lntup | grep ':53 '
```
If `systemd-resolved` holds it, free the port before installing Pi-hole:
```
$ sudo mkdir -p /etc/systemd/resolved.conf.d
$ printf '[Resolve]\nDNSStubListener=no\n' | sudo tee /etc/systemd/resolved.conf.d/nostub.conf
$ sudo ln -sf /run/systemd/resolve/resolv.conf /etc/resolv.conf
$ sudo systemctl restart systemd-resolved
```
Debian 12 does not run this stub by default.

## Checklist before moving on

- [ ] Machine has a fixed IP (note it: ______) and a wired connection
- [ ] SSH works; password login disabled once key login works
- [ ] Timezone correct; clock synchronized
- [ ] Powers back on after an outage; will not sleep (laptops)
- [ ] `docker run --rm hello-world` and `docker compose version` work
- [ ] Repository cloned

Next: [Placing the monitor in your network](04-network-placement.md).
