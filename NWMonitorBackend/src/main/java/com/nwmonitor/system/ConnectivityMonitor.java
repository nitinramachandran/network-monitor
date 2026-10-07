package com.nwmonitor.system;

import com.nwmonitor.alert.AlertService;
import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import com.nwmonitor.settings.SettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

/** Watches that the LAN gateway and the internet stay reachable; alerts on outage and on recovery. */
@Component
public class ConnectivityMonitor {
    public record State(boolean internetUp, Boolean gatewayUp, Instant downSince, Instant checkedAt) {}

    private final SettingsService settings;
    private final AlertService alerts;
    private volatile State state = new State(true, null, null, null);
    private int failures;

    public ConnectivityMonitor(SettingsService settings, AlertService alerts) {
        this.settings = settings;
        this.alerts = alerts;
    }

    public State state() { return state; }

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    void check() {
        boolean net = tcp("1.1.1.1", 443) || tcp("8.8.8.8", 53);
        String gw = settings.network().gatewayIp();
        Boolean gwUp = gw == null || gw.isBlank() ? null : reachable(gw);
        Instant now = Instant.now();
        State prev = state;
        if (net) {
            if (!prev.internetUp()) {
                long mins = Duration.between(prev.downSince(), now).toMinutes();
                alerts.raise(AlertType.INTERNET_RESTORED, Severity.INFO, "Internet is back", "Connection restored after about " + Math.max(mins, 1) + " min.", null, null, null);
            }
            failures = 0;
            state = new State(true, gwUp, null, now);
        } else {
            failures++;
            Instant since = prev.internetUp() ? now : prev.downSince();
            if (failures == 2) {
                alerts.raise(AlertType.INTERNET_DOWN, Severity.CRITICAL, "Internet connection is down",
                        gwUp != null && !gwUp ? "The router/gateway is also unreachable." : "The router responds but the internet does not (ISP outage?).",
                        null, null, "internet-down");
            }
            state = new State(failures < 2, gwUp, failures < 2 ? null : since, now);
        }
    }

    private static boolean tcp(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean reachable(String host) {
        try {
            return InetAddress.getByName(host).isReachable(2000);
        } catch (Exception e) {
            return false;
        }
    }
}
