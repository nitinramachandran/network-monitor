package com.nwmonitor.device;

import com.nwmonitor.alert.AlertService;
import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import com.nwmonitor.common.ApiException;
import com.nwmonitor.settings.SettingsService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceService {
    private final DeviceRepository repo;
    private final AlertService alerts;
    private final SettingsService settings;

    public DeviceService(DeviceRepository repo, AlertService alerts, SettingsService settings) {
        this.repo = repo;
        this.alerts = alerts;
        this.settings = settings;
    }

    public static String normalizeMac(String raw) {
        String hex = raw.replaceAll("[^0-9A-Fa-f]", "").toLowerCase(Locale.ROOT);
        if (hex.length() != 12) throw new IllegalArgumentException("Bad MAC: " + raw);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) sb.append(i > 0 ? ":" : "").append(hex, i, i + 2);
        return sb.toString();
    }

    /**
     * Records that a device was seen on the LAN. Unknown MACs become untrusted devices and raise NEW_DEVICE,
     * unless {@code baseline} is set (very first scan), in which case they are trusted silently.
     */
    @Transactional
    public Device observe(String mac, String ip, String hostname, boolean baseline) {
        Instant now = Instant.now();
        Optional<Device> existing = repo.findByMac(mac);
        if (existing.isEmpty()) {
            Device d = repo.save(new Device(mac, ip, hostname, baseline, now));
            if (!baseline) {
                alerts.raise(AlertType.NEW_DEVICE, Severity.WARNING, "New device joined the network",
                        "%s (%s, MAC %s)%s".formatted(d.displayName(), ip, mac,
                                d.isRandomizedMac() ? " uses a randomized MAC address (typical for phones with private Wi-Fi addresses)." : ""),
                        d.getId(), ip, "new-device:" + mac);
            }
            return d;
        }
        Device d = existing.get();
        if (ip != null && !ip.equals(d.getIp())) checkIpConflict(d, ip, now);
        if (ip != null) d.setIp(ip);
        if (hostname != null && !hostname.isBlank()) d.setHostname(hostname);
        d.setStatus(DeviceStatus.ONLINE);
        d.setLastSeen(now);
        return d;
    }

    /** Two live devices claiming the same IP can mean a misconfiguration or ARP spoofing. */
    private void checkIpConflict(Device claimant, String ip, Instant now) {
        int window = settings.network().offlineAfterSeconds();
        for (Device other : repo.findByIpAndStatusAndMacNot(ip, DeviceStatus.ONLINE, claimant.getMac())) {
            if (other.getLastSeen().isAfter(now.minusSeconds(window))) {
                alerts.raise(AlertType.DEVICE_IP_CONFLICT, Severity.CRITICAL, "IP address conflict",
                        "%s (%s) and %s (%s) both claim %s. This can indicate ARP spoofing.".formatted(
                                claimant.displayName(), claimant.getMac(), other.displayName(), other.getMac(), ip),
                        claimant.getId(), ip, "ip-conflict:" + ip);
            }
        }
    }

    @Transactional
    public int markStaleOffline() {
        Instant cutoff = Instant.now().minusSeconds(settings.network().offlineAfterSeconds());
        List<Device> stale = repo.findByStatusAndLastSeenBefore(DeviceStatus.ONLINE, cutoff);
        stale.forEach(d -> d.setStatus(DeviceStatus.OFFLINE));
        return stale.size();
    }

    public Optional<Device> resolve(String mac, String ip) {
        if (mac != null && !mac.isBlank()) return repo.findByMac(normalizeMac(mac));
        if (ip != null && !ip.isBlank()) return repo.findFirstByIpOrderByLastSeenDesc(ip);
        return Optional.empty();
    }

    public Device get(UUID id) { return repo.findById(id).orElseThrow(() -> ApiException.notFound("Device")); }

    public List<Device> search(DeviceStatus status, Boolean trusted, String q) {
        Specification<Device> spec = Specification.where(null);
        if (status != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (trusted != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("trusted"), trusted));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((r, cq, cb) -> cb.or(
                    cb.like(cb.lower(cb.coalesce(r.<String>get("label"), "")), like),
                    cb.like(cb.lower(cb.coalesce(r.<String>get("hostname"), "")), like),
                    cb.like(cb.lower(cb.coalesce(r.<String>get("ip"), "")), like),
                    cb.like(r.get("mac"), like)));
        }
        return repo.findAll(spec, org.springframework.data.domain.Sort.by("status").descending().and(org.springframework.data.domain.Sort.by("lastSeen").descending()));
    }

    public record Patch(String label, DeviceType deviceType, Boolean trusted, UUID parentId, Boolean clearParent) {}

    @Transactional
    public Device update(UUID id, Patch p) {
        Device d = get(id);
        if (p.label() != null) d.setLabel(p.label().isBlank() ? null : p.label().trim());
        if (p.deviceType() != null) d.setDeviceType(p.deviceType());
        if (p.trusted() != null) d.setTrusted(p.trusted());
        if (Boolean.TRUE.equals(p.clearParent())) d.setParentId(null);
        else if (p.parentId() != null) {
            if (p.parentId().equals(id)) throw ApiException.badRequest("A device cannot be its own parent");
            get(p.parentId());
            d.setParentId(p.parentId());
        }
        return d;
    }

    @Transactional
    public void forget(UUID id) { repo.delete(get(id)); }
}
