# 2. Set up a Raspberry Pi as the monitor box

This guide takes a Raspberry Pi from a box of parts to a secure, updated, always-on machine ready for the monitor. It assumes no prior Pi experience. The next guide after this is [Placing the monitor in your network](04-network-placement.md), then [Install](05-install-and-configure.md).

## 2.1 What to buy

| Item | Recommendation | Why |
|---|---|---|
| Board | Raspberry Pi 4 or Pi 5, **4 GB or more** | Java and Postgres need memory; 2 GB is tight |
| Power supply | The official supply for your model | Cheap chargers cause under-voltage and random crashes |
| Storage | USB 3 SSD (best) **or** an A2-rated, 32 GB+ high-endurance microSD | The database writes constantly; cheap SD cards wear out |
| Case | One with passive or active cooling | The Pi throttles when hot |
| Cable | Ethernet cable | Wired is more reliable than Wi-Fi |
| Extras for later | USB 3 gigabit Ethernet adapter | Only for the gateway setup (stage 3a) |

You also need a computer to prepare the storage, and a card reader if you use microSD.

## 2.2 Flash the operating system

1. On your computer, install **Raspberry Pi Imager** from <https://www.raspberrypi.com/software/>.
2. Insert the microSD card (or connect the SSD).
3. In Imager:
   - **Device:** your Pi model.
   - **Operating System:** *Raspberry Pi OS (other)* → **Raspberry Pi OS Lite (64-bit)**. Lite has no desktop, which saves memory. Do **not** choose a 32-bit image.
   - **Storage:** your card or SSD. Double-check you picked the right drive; it will be erased.
4. Click **Next**, then **Edit Settings** (OS customisation) and set:
   - **Hostname:** `nwmonitor`
   - **Username and password:** choose your own (do not keep a default such as `pi`/`raspberry`). Use a strong password.
   - **Wireless LAN:** leave off (use Ethernet).
   - **Locale / timezone:** your timezone, e.g. `Asia/Kolkata`.
   - **Services → Enable SSH:** choose *Use public-key authentication* if you have a key (recommended), otherwise *password authentication*.
5. Save, confirm, and **Write**. Wait for it to finish and verify.

## 2.3 First boot

1. Put the card in the Pi, plug the **Ethernet cable into a LAN port on your ISP router**, then power it on. Wait about 2 minutes.
2. Find the Pi on your network. Any of these:
   - `$ ping nwmonitor.local`
   - Look at the connected-devices list in your router admin page for `nwmonitor`.
3. Connect over SSH from your computer:
   ```
   $ ssh youruser@nwmonitor.local
   ```
   Accept the host fingerprint when asked. If `.local` names do not resolve on your computer, use the IP address from the router's device list instead.

## 2.4 Update and set the timezone

```
$ sudo apt update && sudo apt full-upgrade -y
$ sudo reboot
```
Reconnect after about a minute, then:
```
$ sudo timedatectl set-timezone Asia/Kolkata
$ timedatectl
```
The monitor uses the timezone to decide where one "day" of data usage ends, so set it correctly. The output should show your zone and `System clock synchronized: yes`.

## 2.5 Give the Pi a fixed IP address

Everything depends on the monitor box always having the same address. The easiest and most reliable method is a **DHCP reservation** in your router:

1. On the Pi, note its MAC address and current IP:
   ```
   $ ip -br link show eth0
   $ ip -br addr show eth0
   ```
2. In your router admin page, find *DHCP*, *LAN*, *Address reservation* or *Static lease* (menu names vary by router). Bind the Pi's MAC to an address outside the automatic pool if possible, e.g. `192.168.1.2`.
3. Reboot the Pi (`sudo reboot`) and confirm with `ip -br addr show eth0`.

If your ISP router does not allow reservations, set a static address on the Pi instead (choose an address outside the router's DHCP range so it cannot clash):
```
$ nmcli -f NAME,DEVICE connection show          # note the wired connection name
$ sudo nmcli connection modify "Wired connection 1" \
    ipv4.method manual \
    ipv4.addresses 192.168.1.2/24 \
    ipv4.gateway 192.168.1.1 \
    ipv4.dns "1.1.1.1 8.8.8.8"
$ sudo nmcli connection up "Wired connection 1"
```
Use your own connection name, subnet and gateway. If you later install Pi-hole on this box, it will manage DNS; see [guide 6](06-pihole.md).

## 2.6 Basic hardening

```
$ sudo apt install -y ufw unattended-upgrades
```

**Firewall.** Allow SSH and the monitor's ports from your LAN only. Replace the subnet with yours:
```
$ sudo ufw default deny incoming
$ sudo ufw default allow outgoing
$ sudo ufw allow from 192.168.1.0/24 to any port 22 proto tcp     # SSH
$ sudo ufw allow from 192.168.1.0/24 to any port 8080 proto tcp   # API (needed by the agent)
$ sudo ufw allow from 192.168.1.0/24 to any port 8081 proto tcp   # web UI
$ sudo ufw allow from 192.168.1.0/24 to any port 53               # DNS, only if you install Pi-hole
$ sudo ufw allow from 192.168.1.0/24 to any port 80 proto tcp     # Pi-hole admin, only if installed
$ sudo ufw enable
```
Docker's published ports can bypass `ufw`. The monitor's compose file binds Postgres to localhost only, and the UI on 8081 is intended for your LAN. Never forward ports from the internet to this box.

**SSH keys.** If you used a password, set up key login and then disable password login:
```
# on your computer
$ ssh-copy-id youruser@nwmonitor.local
# on the Pi, once key login works
$ sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
$ sudo systemctl restart ssh
```
Keep your current session open while testing a second one, so you cannot lock yourself out.

**Automatic security updates:**
```
$ sudo dpkg-reconfigure -plow unattended-upgrades
```

## 2.7 Install Docker and Git

```
$ sudo apt install -y git curl
$ curl -fsSL https://get.docker.com -o get-docker.sh
$ less get-docker.sh          # optional: read what it does
$ sudo sh get-docker.sh
$ sudo usermod -aG docker $USER
```
Log out and back in (so the group change applies), then check:
```
$ docker run --rm hello-world
$ docker compose version
```
Both must work. (Alternatively follow Docker's official apt-repository instructions for Debian at <https://docs.docker.com/engine/install/debian/>.)

**Limit Docker log size** so logs never fill the card:
```
$ echo '{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }' | sudo tee /etc/docker/daemon.json
$ sudo systemctl restart docker
```

## 2.8 Keep the SD card healthy

- Prefer a USB SSD. Imager can write to it the same way; the Pi 4/5 boots from USB by default on current firmware.
- Avoid cutting power. Shut down with `sudo shutdown -h now` before unplugging.
- Watch for under-voltage and heat:
  ```
  $ vcgencmd get_throttled     # 0x0 is healthy; anything else means power or heat problems
  $ vcgencmd measure_temp
  ```
- Back up the database regularly (see [guide 9](09-operations-and-security.md)).

## 2.9 Get the code

```
$ git clone https://github.com/nitinramachandran/network-monitor.git
$ cd network-monitor
```

## Checklist before moving on

- [ ] Pi has a fixed IP address (note it: ______)
- [ ] `ssh` works with your own user
- [ ] Timezone is correct
- [ ] `docker run --rm hello-world` and `docker compose version` work
- [ ] Repository cloned

Next: [Placing the monitor in your network](04-network-placement.md).
