package com.nwmonitor.activity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "web_activity")
public class WebActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private UUID deviceId;
    private String domain;
    private Instant queriedAt;
    private boolean blocked;

    protected WebActivity() {}

    public WebActivity(UUID deviceId, String domain, Instant queriedAt, boolean blocked) {
        this.deviceId = deviceId;
        this.domain = domain;
        this.queriedAt = queriedAt;
        this.blocked = blocked;
    }

    public Long getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public String getDomain() { return domain; }
    public Instant getQueriedAt() { return queriedAt; }
    public boolean isBlocked() { return blocked; }
}
