package com.nwmonitor.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "nwm")
public record NwmProperties(String timezone, Security security, Bootstrap bootstrap) {

    public record Security(String jwtSecret, int accessTokenMinutes, int refreshTokenDays, String corsAllowedOrigins) {}

    public record Bootstrap(String rootUsername, String rootPassword) {}

    public ZoneId zone() {
        return timezone == null || timezone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timezone);
    }
}
