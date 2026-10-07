package com.nwmonitor.usage;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_usage")
public class DailyUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private UUID deviceId;
    private LocalDate usageDate;
    private long bytesDown;
    private long bytesUp;

    protected DailyUsage() {}

    public DailyUsage(UUID deviceId, LocalDate usageDate) {
        this.deviceId = deviceId;
        this.usageDate = usageDate;
    }

    public UUID getDeviceId() { return deviceId; }
    public LocalDate getUsageDate() { return usageDate; }
    public long getBytesDown() { return bytesDown; }
    public long getBytesUp() { return bytesUp; }

    public void add(long down, long up) {
        this.bytesDown += down;
        this.bytesUp += up;
    }
}
