package com.nwmonitor.alert;

import com.nwmonitor.common.PageResponse;
import com.nwmonitor.security.AppPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) { this.service = service; }

    public record AlertView(UUID id, AlertType type, Severity severity, String title, String message, UUID deviceId,
                            String sourceIp, int occurrences, AlertStatus status, Instant createdAt, Instant lastOccurredAt,
                            String acknowledgedBy, Instant acknowledgedAt) {
        static AlertView of(Alert a) {
            return new AlertView(a.getId(), a.getType(), a.getSeverity(), a.getTitle(), a.getMessage(), a.getDeviceId(),
                    a.getSourceIp(), a.getOccurrences(), a.getStatus(), a.getCreatedAt(), a.getLastOccurredAt(),
                    a.getAcknowledgedBy(), a.getAcknowledgedAt());
        }
    }

    @GetMapping
    public PageResponse<AlertView> list(@RequestParam(required = false) AlertStatus status,
                                        @RequestParam(required = false) Severity severity,
                                        @RequestParam(required = false) AlertType type,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(service.search(status, severity, type, page, Math.min(size, 200)).map(AlertView::of));
    }

    @GetMapping("/open-count")
    public Map<String, Long> openCount() { return Map.of("open", service.openCount()); }

    @PostMapping("/{id}/acknowledge")
    public AlertView acknowledge(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal p) {
        return AlertView.of(service.acknowledge(id, p.username()));
    }

    @PostMapping("/acknowledge-all")
    public Map<String, Integer> acknowledgeAll(@AuthenticationPrincipal AppPrincipal p) {
        return Map.of("acknowledged", service.acknowledgeAll(p.username()));
    }
}
