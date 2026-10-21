# Home Network Monitor: Setup Guide

A step-by-step tutorial series for setting up a home network monitor: every device on your network, the sites each one looks up, data used per device per day, and alerts when a new device joins or something suspicious happens.

The software is in this repository: a Java (Spring Boot) API, a React web UI, and an optional Python traffic agent. These guides cover everything around it: choosing hardware, preparing a Raspberry Pi or PC, placing it in your network, installing, and running it.

## Read in this order

| # | Guide | You will |
|---|---|---|
| 1 | [Overview and planning](01-overview-and-planning.md) | Understand what is and is not possible, and pick your setup |
| 2 | [Set up a Raspberry Pi](02-raspberry-pi.md) | Flash, secure and prepare a Pi as the monitor box |
| 3 | [Set up a PC or mini PC](03-pc-or-mini-pc.md) | Prepare an old PC, mini PC or laptop instead |
| 4 | [Placing the monitor in your network](04-network-placement.md) | Choose where the box sits; configure your ISP router |
| 5 | [Install and configure the application](05-install-and-configure.md) | Deploy with Docker, create the root user, run the first scan |
| 6 | [Web activity with Pi-hole](06-pihole.md) | See which sites each device looks up |
| 7 | [Data usage and intrusion agent](07-collector-agent.md) | Get MB per device per day and inbound-probe alerts |
| 8 | [Alerts and Telegram](08-alerts-and-telegram.md) | Get notified on your phone |
| 9 | [Operations and security](09-operations-and-security.md) | Update, back up, secure, recover a lost password |
| 10 | [Troubleshooting](10-troubleshooting.md) | Fix common problems |
| 11 | [API reference](11-api-reference.md) | Build your own client or collector |

For an AI assistant or a quick checklist, see [`skills.md`](../skills.md) in the repository root. The top-level [README](../README.md) is the short version.

## Choose your path

| Goal | Read |
|---|---|
| "Show me devices and alert on new ones," about an hour | 1, 2 or 3, 5, 8 |
| Plus sites visited per device | add 6 |
| Plus data used per device and external intrusion detection | add 4 (gateway or mirror setups) and 7 |
| Just trying it on my laptop first | The top-level README, sections 4 and 12 |

## Conventions used in these guides

- `$` at the start of a line is a command to type; do not type the `$`.
- `192.168.1.x` addresses are examples. Your router may use `192.168.0.x`, `10.0.0.x` or another range. Substitute your own.
- **monitor box** means the Raspberry Pi or PC that runs the monitor. **ISP router** means the router your internet provider installed.
- Replace `nwmonitor` (hostname) and `pi` (username) with the ones you chose.

## Before you start

Only monitor networks you own or administer. Tell the people who use your network that DNS lookups and data usage are recorded. You are responsible for complying with local rules on monitoring other people's activity.
