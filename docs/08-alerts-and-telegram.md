---
title: "Alerts and Telegram"
description: "Get phone notifications for new devices and suspicious activity, and tune alert noise."
slug: alerts-and-telegram
series: "Home Network Monitor"
order: 8
tags: [telegram, alerts]
updated: 2026-10-22
---

# 8. Alerts and Telegram

Alerts always appear inside the monitor (the **Alerts** page, with a red counter in the sidebar). Telegram adds instant phone notifications. This guide sets up Telegram, explains each alert, and shows how to tune the noise.

## 8.1 Set up Telegram notifications

### Create a bot

1. In Telegram, open a chat with **@BotFather** (the official bot creator).
2. Send `/newbot` and follow the prompts: a display name, then a username ending in `bot`.
3. BotFather replies with a **bot token** that looks like `123456789:AAH...`. Keep it secret; anyone with it can send messages as your bot.

### Find your chat id

1. Open a chat with your new bot and press **Start** (or send any message). A bot cannot message you until you do this.
2. In a browser, open (replace `<TOKEN>`):
   ```
   https://api.telegram.org/bot<TOKEN>/getUpdates
   ```
3. Find `"chat":{"id":123456789,...` and note the number. That is your **chat id**.

To send alerts to a **group** instead: add the bot to the group, send a message in the group, reload the `getUpdates` URL and use the group's chat id (a **negative** number, e.g. `-1001234567890`).

If `getUpdates` shows an empty result, send the bot another message and reload.

### Enter it in the monitor

1. **Settings → Alerting**.
2. Tick **Send alerts to Telegram**.
3. Paste the **Bot token** and **Chat id**.
4. Choose **Notify from severity** (see 8.3).
5. Click **Send test message**. You should receive "Network Monitor: test notification" within seconds. If it fails, the message under the button explains why.
6. Click **Save** in the *What to alert on* card below (it saves the whole page).

The token is stored in the database and shown masked afterwards; leave the masked value alone when you edit other fields.

The box needs outbound internet access to `api.telegram.org`.

## 8.2 What each alert means

| Alert | Severity | Raised when | What to do |
|---|---|---|---|
| **New device** | Warning | A device with an unknown MAC appears after the baseline | Open the device. If it is yours, label it and click **Trust**. If not, change your Wi-Fi password, check the router's connected-clients list, and look for the device's IP and MAC. |
| **Device IP conflict** | Critical | Two active devices claim the same IP | Often a misconfigured static IP. If one device is unknown, it may be ARP spoofing; investigate immediately. |
| **External connection attempt** | Warning | The agent (or your script) reports an inbound connection attempt from a public address | Usually internet background noise. Make sure no unnecessary ports are forwarded on the router. |
| **Port scan** | Critical | One source probed 15 or more ports in an interval | Check your router for forwarded ports/DMZ; consider blocking the source at the router. |
| **Login brute force** | Critical | Five failed logins to this app from one address in 10 minutes | Find out who is at that address. Change passwords if unsure. The account locks for 15 minutes after five failures. |
| **Internet down** | Critical | Internet unreachable for two consecutive checks (about a minute) | See if the router is up; call the ISP. The message says whether the router also failed. Telegram cannot deliver this while the internet is down; you get "Internet is back" afterwards. |
| **Internet restored** | Info | Connectivity returned | Shows the approximate outage length. |
| **Collector failure** | Warning | Pi-hole polling failed three times in a row | Open **Connectivity**; check Pi-hole is running and the password unchanged. |
| **Baseline established** | Info | The first scan trusted the devices it found | Review the device list. |

## 8.3 Tuning

**Notify from severity**
- *Info and above*: everything.
- *Warning and above*: skips baseline and "internet restored" messages.
- *Critical only*: only the serious ones (IP conflicts, port scans, brute force, internet down).

The Alerts page always shows everything regardless of this setting.

**Switch off alert types** you do not want by clearing their checkbox under *What to alert on*. A disabled type is not created at all.

**Cooldown ("group repeats for N minutes").** If the same alert (same type and source) fires again within the cooldown while the earlier one is still open, the existing alert's counter increases (×N) and you are **not** notified again. Raise it to reduce noise from repeated probes; lower it if you want each recurrence to notify.

**Acknowledging.** On the Alerts page, **Acknowledge** closes an alert and records who did. **Acknowledge all** clears every open alert. Acknowledged alerts stay in the history (filter: *Acknowledged* or *All*). After you acknowledge an alert, the same event can raise a fresh one.

## 8.4 Tips for fewer false alarms

- Run the first scan when only known devices are connected.
- Turn off **Private Wi-Fi address** on family phones for your home network, so they keep one MAC.
- Give frequently reconnecting devices a DHCP reservation.
- Mark guests' devices as trusted when you recognize them; forget them later from the device page.

## 8.5 Other notification channels

Alerts are sent through a pluggable interface. Developers can add e-mail, push (for example Firebase for a mobile app) or other channels by implementing `NotificationChannel` in the backend; every new alert is passed to each implemented channel. See the top-level README, "Development".
