package com.nwmonitor.device;

import com.nwmonitor.activity.ActivityService;
import com.nwmonitor.common.PageResponse;
import com.nwmonitor.usage.UsageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService devices;
    private final UsageService usage;
    private final ActivityService activity;

    public DeviceController(DeviceService devices, UsageService usage, ActivityService activity) {
        this.devices = devices;
        this.usage = usage;
        this.activity = activity;
    }

    public record DeviceView(UUID id, String mac, String ip, String hostname, String vendor, String label, String name,
                             DeviceType deviceType, UUID parentId, DeviceStatus status, boolean trusted, boolean randomizedMac,
                             Instant firstSeen, Instant lastSeen) {
        public static DeviceView of(Device d) {
            return new DeviceView(d.getId(), d.getMac(), d.getIp(), d.getHostname(), d.getVendor(), d.getLabel(), d.displayName(),
                    d.getDeviceType(), d.getParentId(), d.getStatus(), d.isTrusted(), d.isRandomizedMac(), d.getFirstSeen(), d.getLastSeen());
        }
    }

    @GetMapping
    public List<DeviceView> list(@RequestParam(required = false) DeviceStatus status,
                                 @RequestParam(required = false) Boolean trusted,
                                 @RequestParam(required = false) String q) {
        return devices.search(status, trusted, q).stream().map(DeviceView::of).toList();
    }

    @GetMapping("/{id}")
    public DeviceView get(@PathVariable UUID id) { return DeviceView.of(devices.get(id)); }

    @PatchMapping("/{id}")
    public DeviceView update(@PathVariable UUID id, @RequestBody DeviceService.Patch patch) {
        return DeviceView.of(devices.update(id, patch));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(@PathVariable UUID id) { devices.forget(id); }

    @GetMapping("/{id}/usage")
    public List<UsageService.DayUsage> usage(@PathVariable UUID id,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        devices.get(id);
        return usage.deviceDays(id, from, to);
    }

    @GetMapping("/{id}/activity")
    public PageResponse<ActivityService.ActivityView> activity(@PathVariable UUID id,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(required = false) String domain,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        devices.get(id);
        return activity.search(id, date, domain, page, Math.min(size, 500));
    }
}
