package com.nwmonitor.activity;

import com.nwmonitor.common.NwmProperties;
import com.nwmonitor.common.PageResponse;
import com.nwmonitor.device.Device;
import com.nwmonitor.device.DeviceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityService {
    private final WebActivityRepository repo;
    private final DeviceRepository devices;
    private final NwmProperties props;

    public ActivityService(WebActivityRepository repo, DeviceRepository devices, NwmProperties props) {
        this.repo = repo;
        this.devices = devices;
        this.props = props;
    }

    public record ActivityView(Long id, UUID deviceId, String deviceName, String domain, Instant queriedAt, boolean blocked) {}
    public record TopDomain(String domain, long hits) {}

    @Transactional
    public void saveAll(List<WebActivity> rows) { repo.saveAll(rows); }

    public PageResponse<ActivityView> search(UUID deviceId, LocalDate date, String domain, int page, int size) {
        Specification<WebActivity> spec = Specification.where(null);
        if (deviceId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("deviceId"), deviceId));
        if (date != null) {
            ZoneId z = props.zone();
            Instant from = date.atStartOfDay(z).toInstant(), to = date.plusDays(1).atStartOfDay(z).toInstant();
            spec = spec.and((r, q, cb) -> cb.and(cb.greaterThanOrEqualTo(r.<Instant>get("queriedAt"), from), cb.lessThan(r.<Instant>get("queriedAt"), to)));
        }
        if (domain != null && !domain.isBlank()) {
            String like = "%" + domain.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("domain")), like));
        }
        var result = repo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "queriedAt")));
        Map<UUID, Device> names = devices.findAllById(result.getContent().stream().map(WebActivity::getDeviceId).distinct().toList())
                .stream().collect(Collectors.toMap(Device::getId, Function.identity()));
        return PageResponse.of(result.map(w -> new ActivityView(w.getId(), w.getDeviceId(),
                names.containsKey(w.getDeviceId()) ? names.get(w.getDeviceId()).displayName() : "?", w.getDomain(), w.getQueriedAt(), w.isBlocked())));
    }

    public List<TopDomain> topDomains(UUID deviceId, LocalDate date, int limit) {
        ZoneId z = props.zone();
        Instant from = date.atStartOfDay(z).toInstant(), to = date.plusDays(1).atStartOfDay(z).toInstant();
        var page = PageRequest.of(0, limit);
        var rows = deviceId == null ? repo.topDomains(from, to, page) : repo.topDomainsForDevice(deviceId, from, to, page);
        return rows.stream().map(r -> new TopDomain(r.getDomain(), r.getHits())).toList();
    }
}
