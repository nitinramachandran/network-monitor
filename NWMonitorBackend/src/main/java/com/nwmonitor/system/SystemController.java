package com.nwmonitor.system;

import com.nwmonitor.activity.ActivityService;
import com.nwmonitor.alert.AlertService;
import com.nwmonitor.collector.CollectorStatusRegistry;
import com.nwmonitor.common.Bytes;
import com.nwmonitor.device.DeviceRepository;
import com.nwmonitor.device.DeviceStatus;
import com.nwmonitor.usage.UsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final DeviceRepository devices;
    private final UsageService usage;
    private final ActivityService activity;
    private final AlertService alerts;
    private final ConnectivityMonitor connectivity;
    private final CollectorStatusRegistry statuses;

    public SystemController(DeviceRepository devices, UsageService usage, ActivityService activity, AlertService alerts,
                            ConnectivityMonitor connectivity, CollectorStatusRegistry statuses) {
        this.devices = devices;
        this.usage = usage;
        this.activity = activity;
        this.alerts = alerts;
        this.connectivity = connectivity;
        this.statuses = statuses;
    }

    public record Dashboard(long devicesTotal, long devicesOnline, long devicesUntrusted, long openAlerts,
                            long todayBytesDown, long todayBytesUp, double todayMegabytes,
                            List<UsageService.DeviceUsage> topDevices, List<ActivityService.TopDomain> topDomains,
                            ConnectivityMonitor.State connectivity) {}

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        LocalDate today = usage.today();
        var perDevice = usage.perDevice(today);
        long down = perDevice.stream().mapToLong(UsageService.DeviceUsage::bytesDown).sum();
        long up = perDevice.stream().mapToLong(UsageService.DeviceUsage::bytesUp).sum();
        return new Dashboard(devices.count(), devices.countByStatus(DeviceStatus.ONLINE), devices.countByTrusted(false), alerts.openCount(),
                down, up, Bytes.toMb(down + up), perDevice.stream().limit(5).toList(), activity.topDomains(null, today, 5), connectivity.state());
    }

    public record SystemStatus(ConnectivityMonitor.State connectivity, List<CollectorStatusRegistry.Status> sources) {}

    /** Health of the internet link and every data source: backs the Connectivity screen. */
    @GetMapping("/system/status")
    public SystemStatus status() { return new SystemStatus(connectivity.state(), statuses.all()); }
}
