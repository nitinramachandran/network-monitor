#!/usr/bin/env python3
"""
Traffic collector agent: counts bytes per device and reports them to the Network Monitor API.

Needs to SEE the traffic: run it on the router/gateway, on a box that is the LAN's default gateway,
or on a box that receives a mirrored port. Requires: pip install scapy requests ; run as root.

  NWM_URL=http://192.168.1.2:8080 NWM_API_KEY=nwm_... LAN_PREFIX=192.168.1. python3 agent.py

Inbound-attempt detection: SYNs from outside the LAN to LAN hosts are counted per source and
reported as INBOUND_CONNECTION_ATTEMPT; a source probing >= 15 distinct ports is reported as PORT_SCAN.
"""
import os, time, threading, collections
import requests
from scapy.all import sniff, IP, TCP

URL = os.environ["NWM_URL"].rstrip("/") + "/api/v1/ingest"
HEAD = {"X-API-Key": os.environ["NWM_API_KEY"]}
LAN = os.environ.get("LAN_PREFIX", "192.168.1.")
INTERVAL = int(os.environ.get("REPORT_SECONDS", "30"))
SCAN_PORTS = 15

lock = threading.Lock()
usage = collections.defaultdict(lambda: [0, 0])          # lan ip -> [down, up]
probes = collections.defaultdict(lambda: collections.defaultdict(int))  # src ip -> {dport: syns}


def is_lan(ip): return ip.startswith(LAN)


def on_packet(p):
    if IP not in p: return
    src, dst, size = p[IP].src, p[IP].dst, len(p)
    with lock:
        if is_lan(dst) and not is_lan(src): usage[dst][0] += size
        elif is_lan(src) and not is_lan(dst): usage[src][1] += size
        if TCP in p and is_lan(dst) and not is_lan(src) and p[TCP].flags == "S":
            probes[src][p[TCP].dport] += 1


def report():
    while True:
        time.sleep(INTERVAL)
        with lock:
            u, pr = dict(usage), {k: dict(v) for k, v in probes.items()}
            usage.clear(); probes.clear()
        now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        try:
            if u:
                requests.post(f"{URL}/usage", headers=HEAD, timeout=10, json={"samples": [
                    {"ip": ip, "timestamp": now, "bytesDown": d, "bytesUp": up} for ip, (d, up) in u.items()]})
            events = []
            for src, ports in pr.items():
                kind = "PORT_SCAN" if len(ports) >= SCAN_PORTS else "INBOUND_CONNECTION_ATTEMPT"
                port = max(ports, key=ports.get)
                events.append({"type": kind, "sourceIp": src, "destPort": port, "protocol": "TCP",
                               "detail": f"{sum(ports.values())} SYNs to {len(ports)} port(s) in {INTERVAL}s"})
            if events:
                requests.post(f"{URL}/security-events", headers=HEAD, timeout=10, json={"events": events})
        except requests.RequestException as e:
            print("report failed:", e)


threading.Thread(target=report, daemon=True).start()
sniff(prn=on_packet, store=False, filter="ip")
