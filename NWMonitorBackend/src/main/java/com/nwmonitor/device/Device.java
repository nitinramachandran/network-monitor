package com.nwmonitor.device;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {
    @Id private UUID id;
    private String mac;
    private String ip;
    private String hostname;
    private String vendor;
    private String label;
    @Enumerated(EnumType.STRING) private DeviceType deviceType = DeviceType.UNKNOWN;
    private UUID parentId;
    @Enumerated(EnumType.STRING) private DeviceStatus status = DeviceStatus.ONLINE;
    private boolean trusted;
    private boolean randomizedMac;
    private Instant firstSeen;
    private Instant lastSeen;

    protected Device() {}

    public Device(String mac, String ip, String hostname, boolean trusted, Instant now) {
        this.id = UUID.randomUUID();
        this.mac = mac;
        this.ip = ip;
        this.hostname = hostname;
        this.trusted = trusted;
        this.randomizedMac = isLocallyAdministered(mac);
        this.firstSeen = now;
        this.lastSeen = now;
    }

    /** Phones rotate to a random "locally administered" MAC (bit 1 of the first octet) for privacy. */
    static boolean isLocallyAdministered(String mac) {
        return (Integer.parseInt(mac.substring(0, 2), 16) & 0x02) != 0;
    }

    public UUID getId() { return id; }
    public String getMac() { return mac; }
    public String getIp() { return ip; }
    public void setIp(String v) { this.ip = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { this.hostname = v; }
    public String getVendor() { return vendor; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType v) { this.deviceType = v; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID v) { this.parentId = v; }
    public DeviceStatus getStatus() { return status; }
    public void setStatus(DeviceStatus v) { this.status = v; }
    public boolean isTrusted() { return trusted; }
    public void setTrusted(boolean v) { this.trusted = v; }
    public boolean isRandomizedMac() { return randomizedMac; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant v) { this.lastSeen = v; }

    public String displayName() {
        if (label != null && !label.isBlank()) return label;
        if (hostname != null && !hostname.isBlank()) return hostname;
        return mac;
    }
}
