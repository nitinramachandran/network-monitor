package com.nwmonitor.collector;

import com.nwmonitor.activity.ActivityService;
import com.nwmonitor.activity.WebActivity;
import com.nwmonitor.device.DeviceService;
import com.nwmonitor.usage.UsageService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Single entry point for activity/usage from any source (pull collectors and the ingest API alike). */
@Component
public class EventSink {
    public record DnsEvent(String clientIp, String mac, String domain, Instant at, boolean blocked) {}
    public record UsageSample(String ip, String mac, Instant at, long bytesDown, long bytesUp) {}
    public record Result(int accepted, int unmatched) {}

    private final DeviceService devices;
    private final ActivityService activity;
    private final UsageService usage;

    public EventSink(DeviceService devices, ActivityService activity, UsageService usage) {
        this.devices = devices;
        this.activity = activity;
        this.usage = usage;
    }

    public Result dns(List<DnsEvent> events) {
        List<WebActivity> rows = new ArrayList<>();
        int unmatched = 0;
        for (DnsEvent e : events) {
            var d = devices.resolve(e.mac(), e.clientIp());
            if (d.isEmpty() || e.domain() == null || e.domain().isBlank()) { unmatched++; continue; }
            String domain = e.domain().toLowerCase(java.util.Locale.ROOT);
            rows.add(new WebActivity(d.get().getId(), domain.length() > 255 ? domain.substring(0, 255) : domain,
                    e.at() == null ? Instant.now() : e.at(), e.blocked()));
        }
        activity.saveAll(rows);
        return new Result(rows.size(), unmatched);
    }

    public Result usage(List<UsageSample> samples) {
        int ok = 0, unmatched = 0;
        for (UsageSample s : samples) {
            var d = devices.resolve(s.mac(), s.ip());
            if (d.isEmpty()) { unmatched++; continue; }
            usage.add(d.get().getId(), s.at() == null ? Instant.now() : s.at(), s.bytesDown(), s.bytesUp());
            ok++;
        }
        return new Result(ok, unmatched);
    }
}
