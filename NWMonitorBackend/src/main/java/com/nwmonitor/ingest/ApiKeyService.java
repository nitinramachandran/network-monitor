package com.nwmonitor.ingest;

import com.nwmonitor.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Manages the shared secret that collector agents use on /api/v1/ingest/**. Only a SHA-256 hash is stored. */
@Service
public class ApiKeyService {
    private static final String KEY = "ingest-key-hash";
    private final SettingsService settings;

    public ApiKeyService(SettingsService settings) { this.settings = settings; }

    public boolean isConfigured() { return settings.getRaw(KEY).isPresent(); }

    public boolean matches(String presented) {
        if (presented == null || presented.isBlank()) return false;
        return settings.getRaw(KEY).map(stored -> MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), hash(presented).getBytes(StandardCharsets.UTF_8))).orElse(false);
    }

    /** Generates a new key, invalidating the old one. The plaintext is returned once and never stored. */
    public String rotate() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        String key = "nwm_" + HexFormat.of().formatHex(b);
        settings.putRaw(KEY, hash(key));
        return key;
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
