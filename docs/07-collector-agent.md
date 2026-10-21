# 7. Data usage and intrusion agent

The built-in scanner and Pi-hole cannot tell you **how much data each device uses**, or see **inbound probes**. For that, a small Python **collector agent** (`collector-agent/agent.py`) watches network packets on a machine that can see the traffic and reports totals to the monitor's API.

## 7.1 Before you start

The agent only counts what it can see. You need one of these positions (see [guide 4](04-network-placement.md)):

| Setup | Position | Works? |
|---|---|---|
| **B** Gateway | Box between ISP router and your devices | Yes, sees everything |
| **C** Mirror port | Box on a switch's mirror port | Yes, sees everything on the mirrored switch |
| **A** Box beside the router | Box on a normal router LAN port | **No.** A switch/router only sends the box its own traffic. The agent will report nothing useful. |

If you are in setup A, skip this guide, or move to B or C.

## 7.2 What the agent does

Every `REPORT_SECONDS` (default 30) it sends the monitor:
1. **Usage:** bytes downloaded and uploaded per LAN IP since the last report. The monitor adds them to that device's total for the day. Counts are captured frame sizes (including Ethernet and IP headers), so they are close to, but not identical to, your ISP's meter.
2. **Security events:** unsolicited TCP connection attempts (SYN packets) from **public** internet addresses to your network, one event per source address. A source probing **15 or more different ports** in one interval is reported as a **Port scan** (critical).

## 7.3 Performance expectations

The agent is a Python program (scapy) that inspects every packet. It is intended for typical home use. It becomes CPU-bound on a Raspberry Pi at high throughput, and can then miss packets and undercount. If your connection is very fast (hundreds of Mbps sustained) or the box is slow, prefer your router's own byte accounting and push those totals to the [ingest API](11-api-reference.md#ingest-agents-only). Watch the agent's CPU use (`top`) the first week.

## 7.4 Create an API key

In the monitor UI: **Settings → Collector agents → Generate key**. **Copy the key immediately**; it is shown only once. Anyone holding it can send data to your monitor, so treat it like a password. Generating a new key invalidates the old one (and every agent using it).

## 7.5 Install the agent

On the machine that sees the traffic (usually the monitor box itself):
```
$ sudo apt install -y python3 python3-venv
$ sudo mkdir -p /opt/nwm-agent
$ sudo cp ~/network-monitor/collector-agent/agent.py /opt/nwm-agent/
$ sudo python3 -m venv /opt/nwm-agent/venv
$ sudo /opt/nwm-agent/venv/bin/pip install scapy requests
```
(A virtual environment avoids the "externally managed environment" error on current Debian/Ubuntu.)

## 7.6 Configuration

Create `/etc/nwm-agent.env` (readable by root only):
```
$ sudo tee /etc/nwm-agent.env >/dev/null <<'EOF'
NWM_URL=http://127.0.0.1:8080
NWM_API_KEY=nwm_paste_your_key_here
LAN_PREFIX=192.168.1.
REPORT_SECONDS=30
EOF
$ sudo chmod 600 /etc/nwm-agent.env
```

| Variable | Meaning |
|---|---|
| `NWM_URL` | Monitor API address. `http://127.0.0.1:8080` if the agent runs on the same box; otherwise `http://<box-ip>:8080`. |
| `NWM_API_KEY` | The key from 7.4. |
| `LAN_PREFIX` | The start of your LAN addresses **including the trailing dot**: `192.168.1.` for `192.168.1.0/24`, `192.168.50.` in the gateway setup. Addresses starting with this are "your devices". |
| `IFACE` | Optional. Sniff only this interface, e.g. the LAN side (`eth1`) in the gateway setup, or the mirror-port NIC. Unset means all interfaces. |
| `PROTECTED_IPS` | Optional, comma-separated. Extra destination addresses counted as "your network" for probe detection, normally your **WAN IP**, because inbound probes are addressed to it, not to a LAN address. |
| `REPORT_SECONDS` | Reporting interval. |

**Choosing `IFACE`:**
- Gateway: leave unset (sees LAN and WAN sides; usage is counted once, because NAT'd packets on the WAN side no longer carry LAN addresses), or set `IFACE=eth1` for usage only.
- Mirror port: set `IFACE` to the NIC plugged into the mirror port.

**`PROTECTED_IPS` and the WAN IP:** find the WAN IP in the ISP router's status page (gateway setup: `ip -4 addr show eth0`). If it is a private or carrier-grade-NAT address, no internet source can reach you and there is nothing external to detect. If your ISP changes your public IP, update `PROTECTED_IPS`.

## 7.7 Test it by hand

Stop and run in the foreground first so you can see errors:
```
$ sudo bash -c 'set -a; . /etc/nwm-agent.env; set +a; /opt/nwm-agent/venv/bin/python /opt/nwm-agent/agent.py'
```
Generate traffic (browse or stream something on a phone), wait one reporting interval, then check:
- In the UI, **Connectivity** shows **Agent: data usage** healthy with a recent time.
- **Data usage** lists devices with MB for today.

Press Ctrl+C when satisfied. If nothing arrives, check 7.9.

## 7.8 Run it as a service

```
$ sudo tee /etc/systemd/system/nwm-agent.service >/dev/null <<'EOF'
[Unit]
Description=Network Monitor traffic agent
After=network-online.target
Wants=network-online.target

[Service]
EnvironmentFile=/etc/nwm-agent.env
ExecStart=/opt/nwm-agent/venv/bin/python /opt/nwm-agent/agent.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
$ sudo systemctl daemon-reload
$ sudo systemctl enable --now nwm-agent
$ systemctl status nwm-agent --no-pager
$ journalctl -u nwm-agent -f          # live log
```
It runs as root because capturing packets requires it.

## 7.9 Troubleshooting the agent

| Symptom | Cause / fix |
|---|---|
| `Permission denied` / `Operation not permitted` | Must run as root (the service does). |
| Usage page stays empty | The box does not see other devices' traffic (setup A). Check the topology in guide 4. Run `sudo tcpdump -i eth0 -c 20 'not host <box-ip>'`; if you only see the box's own packets, it cannot see the rest. |
| `report failed: ... 401/403` | Wrong or rotated API key. |
| `report failed: connection refused` | `NWM_URL` wrong, backend down, or the box firewall blocks port 8080 from the agent's machine. |
| Devices missing | Samples whose IP the monitor has not discovered yet are skipped; wait for the next scan, or **Scan now**. |
| Usage looks double | The agent sees the same traffic on two interfaces without NAT in between. Set `IFACE` to one interface. |
| No inbound events | Expected if you are behind CGNAT, or `PROTECTED_IPS` is not set to your WAN IP. The agent ignores probes from private/reserved addresses. |

Testing detection: from a **public** address outside your network (for example, a phone on mobile data) try connecting to your WAN IP on a closed port. Reserved example addresses such as `203.0.113.x` are not treated as public by the agent; the API itself accepts any source when you send events by hand (see below).

## 7.10 Sending your own data (any language)

The agent is just a client of a public API, so you can feed the monitor from a router script, a firewall log shipper or any program. See the [API reference](11-api-reference.md#ingest-agents-only). Example:

```
$ curl -X POST http://192.168.1.2:8080/api/v1/ingest/usage \
    -H "X-API-Key: nwm_your_key" -H "Content-Type: application/json" \
    -d '{"samples":[{"mac":"aa:bb:cc:dd:ee:ff","bytesDown":5242880,"bytesUp":1048576}]}'
```
Send **deltas** (bytes since your last report), not running totals. Identify devices by `mac` (preferred) or `ip`.

Next: [Alerts and Telegram](08-alerts-and-telegram.md).
