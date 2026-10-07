package com.nwmonitor.collector;

import com.nwmonitor.alert.AlertService;
import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import com.nwmonitor.settings.Settings;
import com.nwmonitor.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Runs whichever ActivityCollector is selected in settings, at the configured interval. */
@Component
public class CollectorScheduler {
    private static final Logger log = LoggerFactory.getLogger(CollectorScheduler.class);
    public static final String STATUS_NAME = "dns-collector";

    private final SettingsService settings;
    private final List<ActivityCollector> collectors;
    private final CollectorStatusRegistry status;
    private final AlertService alerts;
    private volatile Instant lastRun = Instant.EPOCH;

    public CollectorScheduler(SettingsService settings, List<ActivityCollector> collectors, CollectorStatusRegistry status, AlertService alerts) {
        this.settings = settings;
        this.collectors = collectors;
        this.status = status;
        this.alerts = alerts;
    }

    public ActivityCollector find(String type) {
        return collectors.stream().filter(c -> c.type().equals(type)).findFirst().orElse(null);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    void tick() {
        Settings.DnsCollector cfg = settings.dnsCollector();
        ActivityCollector c = find(cfg.type());
        if (c == null || Instant.now().isBefore(lastRun.plusSeconds(cfg.pollSeconds()))) return;
        lastRun = Instant.now();
        try {
            status.success(STATUS_NAME, c.type() + ": " + c.collect(cfg));
        } catch (Exception e) {
            log.warn("{} collector failed: {}", c.type(), e.getMessage());
            int failures = status.failure(STATUS_NAME, c.type() + ": " + e.getMessage());
            if (failures >= 3) {
                alerts.raise(AlertType.COLLECTOR_FAILURE, Severity.WARNING, "Web activity collector is failing",
                        c.type() + " has failed " + failures + " times in a row: " + e.getMessage(), null, null, "collector-failure");
            }
        }
    }
}
