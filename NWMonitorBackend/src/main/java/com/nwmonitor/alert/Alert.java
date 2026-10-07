package com.nwmonitor.alert;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) private AlertType type;
    @Enumerated(EnumType.STRING) private Severity severity;
    private String title;
    private String message;
    private UUID deviceId;
    private String sourceIp;
    private String dedupeKey;
    private int occurrences = 1;
    @Enumerated(EnumType.STRING) private AlertStatus status = AlertStatus.OPEN;
    private Instant createdAt;
    private Instant lastOccurredAt;
    private String acknowledgedBy;
    private Instant acknowledgedAt;

    protected Alert() {}

    public Alert(AlertType type, Severity severity, String title, String message, UUID deviceId, String sourceIp, String dedupeKey) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.deviceId = deviceId;
        this.sourceIp = sourceIp;
        this.dedupeKey = dedupeKey;
        this.createdAt = Instant.now();
        this.lastOccurredAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public AlertType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getDeviceId() { return deviceId; }
    public String getSourceIp() { return sourceIp; }
    public int getOccurrences() { return occurrences; }
    public AlertStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastOccurredAt() { return lastOccurredAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }

    public void repeat() {
        this.occurrences++;
        this.lastOccurredAt = Instant.now();
    }

    public void acknowledge(String by) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedBy = by;
        this.acknowledgedAt = Instant.now();
    }
}
