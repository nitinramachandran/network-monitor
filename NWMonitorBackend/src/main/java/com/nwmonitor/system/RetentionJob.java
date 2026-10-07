package com.nwmonitor.system;

import com.nwmonitor.activity.WebActivityRepository;
import com.nwmonitor.auth.RefreshTokenRepository;
import com.nwmonitor.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private final WebActivityRepository activity;
    private final RefreshTokenRepository tokens;
    private final SettingsService settings;

    public RetentionJob(WebActivityRepository activity, RefreshTokenRepository tokens, SettingsService settings) {
        this.activity = activity;
        this.tokens = tokens;
        this.settings = settings;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purge() {
        int n = activity.deleteOlderThan(Instant.now().minus(Duration.ofDays(settings.network().activityRetentionDays())));
        tokens.deleteExpired(Instant.now());
        log.info("Retention: removed {} web activity rows", n);
    }
}
