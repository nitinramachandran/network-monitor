package com.nwmonitor.settings;

import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/** Typed settings documents; each is stored as one JSON row in app_settings. */
public final class Settings {
    private Settings() {}

    /** subnetCidr / gatewayIp blank = auto-detect from this machine's network interface. */
    public record Network(String subnetCidr, String gatewayIp,
                          boolean scanEnabled,
                          @Min(30) @Max(3600) int scanIntervalSeconds,
                          @Min(60) @Max(86400) int offlineAfterSeconds,
                          @Min(1) @Max(365) int activityRetentionDays) {
        public static Network defaults() { return new Network("", "", true, 60, 300, 30); }
    }

    /** type: NONE | PIHOLE | SIMULATED. */
    public record DnsCollector(String type, String url, String password, @Min(10) @Max(3600) int pollSeconds) {
        public static DnsCollector defaults() { return new DnsCollector("NONE", "http://pi.hole", "", 30); }
    }

    public record Alerting(boolean telegramEnabled, String telegramBotToken, String telegramChatId,
                           Severity minNotifySeverity, List<AlertType> disabledTypes,
                           @Min(1) @Max(1440) int cooldownMinutes) {
        public static Alerting defaults() {
            return new Alerting(false, "", "", Severity.INFO, List.of(), 15);
        }
    }
}
