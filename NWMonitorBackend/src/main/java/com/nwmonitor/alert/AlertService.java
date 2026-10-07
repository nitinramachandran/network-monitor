package com.nwmonitor.alert;

import com.nwmonitor.common.ApiException;
import com.nwmonitor.settings.Settings;
import com.nwmonitor.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private final AlertRepository repo;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final List<NotificationChannel> channels;

    public AlertService(AlertRepository repo, SettingsService settings, ApplicationEventPublisher events, List<NotificationChannel> channels) {
        this.repo = repo;
        this.settings = settings;
        this.events = events;
        this.channels = channels;
    }

    /**
     * Raises an alert. Repeats of the same dedupeKey inside the cooldown window bump the counter of the
     * existing open alert instead of creating (and notifying) a new one.
     */
    @Transactional
    public Alert raise(AlertType type, Severity severity, String title, String message, UUID deviceId, String sourceIp, String dedupeKey) {
        Settings.Alerting cfg = settings.alerting();
        if (cfg.disabledTypes() != null && cfg.disabledTypes().contains(type)) return null;
        if (dedupeKey != null) {
            Instant since = Instant.now().minus(Duration.ofMinutes(cfg.cooldownMinutes()));
            var existing = repo.findFirstByDedupeKeyAndStatusAndLastOccurredAtAfterOrderByLastOccurredAtDesc(dedupeKey, AlertStatus.OPEN, since);
            if (existing.isPresent()) {
                existing.get().repeat();
                return existing.get();
            }
        }
        Alert a = repo.save(new Alert(type, severity, title, message, deviceId, sourceIp, dedupeKey));
        events.publishEvent(new AlertCreatedEvent(a));
        return a;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void dispatch(AlertCreatedEvent e) {
        Settings.Alerting cfg = settings.alerting();
        if (e.alert().getSeverity().ordinal() < cfg.minNotifySeverity().ordinal()) return;
        for (NotificationChannel c : channels) {
            try {
                c.send(e.alert());
            } catch (Exception ex) {
                log.warn("Notification via {} failed: {}", c.name(), ex.getMessage());
            }
        }
    }

    public Page<Alert> search(AlertStatus status, Severity severity, AlertType type, int page, int size) {
        Specification<Alert> spec = Specification.where(null);
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (severity != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("severity"), severity));
        if (type != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        return repo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastOccurredAt")));
    }

    public long openCount() { return repo.countByStatus(AlertStatus.OPEN); }

    @Transactional
    public Alert acknowledge(UUID id, String by) {
        Alert a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Alert"));
        a.acknowledge(by);
        return a;
    }

    @Transactional
    public int acknowledgeAll(String by) { return repo.acknowledgeAll(by, Instant.now()); }
}
