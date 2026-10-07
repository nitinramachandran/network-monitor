package com.nwmonitor.usage;

import com.nwmonitor.common.Bytes;
import com.nwmonitor.common.NwmProperties;
import com.nwmonitor.device.Device;
import com.nwmonitor.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UsageService {
    private final DailyUsageRepository repo;
    private final DeviceRepository devices;
    private final NwmProperties props;
    private final Object lock = new Object();

    public UsageService(DailyUsageRepository repo, DeviceRepository devices, NwmProperties props) {
        this.repo = repo;
        this.devices = devices;
        this.props = props;
    }

    public LocalDate today() { return LocalDate.now(props.zone()); }

    /** Adds byte deltas to the device's bucket for the day containing {@code at}. */
    @Transactional
    public void add(UUID deviceId, Instant at, long down, long up) {
        if (down <= 0 && up <= 0) return;
        LocalDate day = at.atZone(props.zone()).toLocalDate();
        synchronized (lock) {
            DailyUsage u = repo.findByDeviceIdAndUsageDate(deviceId, day).orElseGet(() -> repo.save(new DailyUsage(deviceId, day)));
            u.add(Math.max(0, down), Math.max(0, up));
        }
    }

    public record DayUsage(LocalDate date, long bytesDown, long bytesUp, long bytesTotal, double megabytes) {
        static DayUsage of(LocalDate d, long down, long up) {
            return new DayUsage(d, down, up, down + up, Bytes.toMb(down + up));
        }
    }

    public record DeviceUsage(UUID deviceId, String deviceName, String ip, long bytesDown, long bytesUp, long bytesTotal, double megabytes) {}

    public List<DayUsage> deviceDays(UUID deviceId, LocalDate from, LocalDate to) {
        return repo.findByDeviceIdAndUsageDateBetweenOrderByUsageDate(deviceId, from, to).stream()
                .map(u -> DayUsage.of(u.getUsageDate(), u.getBytesDown(), u.getBytesUp())).toList();
    }

    public List<DayUsage> totalsPerDay(LocalDate from, LocalDate to) {
        return repo.totalsPerDay(from, to).stream().map(t -> DayUsage.of(t.getUsageDate(), t.getBytesDown(), t.getBytesUp())).toList();
    }

    /** Per-device usage for one day, heaviest first. */
    public List<DeviceUsage> perDevice(LocalDate day) {
        List<DailyUsage> rows = repo.findByUsageDate(day);
        Map<UUID, Device> byId = devices.findAllById(rows.stream().map(DailyUsage::getDeviceId).toList()).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));
        return rows.stream().map(u -> {
            Device d = byId.get(u.getDeviceId());
            long total = u.getBytesDown() + u.getBytesUp();
            return new DeviceUsage(u.getDeviceId(), d == null ? "?" : d.displayName(), d == null ? null : d.getIp(),
                    u.getBytesDown(), u.getBytesUp(), total, Bytes.toMb(total));
        }).sorted(Comparator.comparingLong(DeviceUsage::bytesTotal).reversed()).toList();
    }
}
