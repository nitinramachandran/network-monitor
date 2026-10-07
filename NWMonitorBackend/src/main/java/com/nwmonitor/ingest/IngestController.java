package com.nwmonitor.ingest;

import com.nwmonitor.alert.AlertService;
import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import com.nwmonitor.collector.CollectorStatusRegistry;
import com.nwmonitor.collector.EventSink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Push API for collector agents (authenticated with X-API-Key). Lets capture run on any machine with
 * visibility of the traffic (router, Pi, firewall) while this service stays a pure API.
 */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {
    private final EventSink sink;
    private final AlertService alerts;
    private final CollectorStatusRegistry status;

    public IngestController(EventSink sink, AlertService alerts, CollectorStatusRegistry status) {
        this.sink = sink;
        this.alerts = alerts;
        this.status = status;
    }

    public record UsageItem(String ip, String mac, Instant timestamp, long bytesDown, long bytesUp) {}
    public record UsageBatch(@NotEmpty @Size(max = 5000) List<@Valid UsageItem> samples) {}
    public record DnsItem(String clientIp, String mac, String domain, Instant timestamp, boolean blocked) {}
    public record DnsBatch(@NotEmpty @Size(max = 5000) List<@Valid DnsItem> events) {}

    public enum SecurityEventType { INBOUND_CONNECTION_ATTEMPT, PORT_SCAN }

    public record SecurityItem(@NotNull SecurityEventType type, String sourceIp, Integer destPort, String protocol, Integer count, Severity severity, String detail) {}
    public record SecurityBatch(@NotEmpty @Size(max = 1000) List<@Valid SecurityItem> events) {}

    @PostMapping("/usage")
    public EventSink.Result usage(@Valid @RequestBody UsageBatch b) {
        var r = sink.usage(b.samples().stream().map(s -> new EventSink.UsageSample(s.ip(), s.mac(), s.timestamp(), s.bytesDown(), s.bytesUp())).toList());
        status.success("ingest-usage", r.accepted() + " samples");
        return r;
    }

    @PostMapping("/dns")
    public EventSink.Result dns(@Valid @RequestBody DnsBatch b) {
        var r = sink.dns(b.events().stream().map(e -> new EventSink.DnsEvent(e.clientIp(), e.mac(), e.domain(), e.timestamp(), e.blocked())).toList());
        status.success("ingest-dns", r.accepted() + " events");
        return r;
    }

    @PostMapping("/security-events")
    public java.util.Map<String, Integer> security(@Valid @RequestBody SecurityBatch b) {
        int raised = 0;
        for (SecurityItem e : b.events()) {
            AlertType type = e.type() == SecurityEventType.PORT_SCAN ? AlertType.PORT_SCAN : AlertType.EXTERNAL_CONNECTION_ATTEMPT;
            String src = e.sourceIp() == null ? "unknown" : e.sourceIp();
            String title = type == AlertType.PORT_SCAN ? "Port scan detected" : "External connection attempt";
            String msg = "%s from %s%s%s%s".formatted(title, src,
                    e.destPort() == null ? "" : " to port " + e.destPort(),
                    e.protocol() == null ? "" : " (" + e.protocol() + ")",
                    e.detail() == null ? "" : ". " + e.detail());
            Severity sev = e.severity() != null ? e.severity() : (type == AlertType.PORT_SCAN ? Severity.CRITICAL : Severity.WARNING);
            if (alerts.raise(type, sev, title, msg, null, e.sourceIp(), type + ":" + src) != null) raised++;
        }
        status.success("ingest-security", b.events().size() + " events");
        return java.util.Map.of("received", b.events().size());
    }
}
