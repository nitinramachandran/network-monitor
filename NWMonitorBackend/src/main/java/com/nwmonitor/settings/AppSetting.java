package com.nwmonitor.settings;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSetting {
    @Id @Column(name = "setting_key") private String key;
    @Column(name = "setting_value", columnDefinition = "TEXT") private String value;
    private Instant updatedAt;

    protected AppSetting() {}

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public String getValue() { return value; }
    public void setValue(String v) { this.value = v; this.updatedAt = Instant.now(); }
}
