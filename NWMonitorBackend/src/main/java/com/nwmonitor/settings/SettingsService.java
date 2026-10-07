package com.nwmonitor.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Service
public class SettingsService {
    public static final String MASK = "********";
    private final AppSettingRepository repo;
    private final ObjectMapper mapper;

    public SettingsService(AppSettingRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public <T> T get(String key, Class<T> type, Supplier<T> defaults) {
        return repo.findById(key).map(s -> read(s.getValue(), type)).orElseGet(defaults);
    }

    public Optional<String> getRaw(String key) { return repo.findById(key).map(AppSetting::getValue); }

    @Transactional
    public void putRaw(String key, String value) {
        repo.findById(key).ifPresentOrElse(s -> s.setValue(value), () -> repo.save(new AppSetting(key, value)));
    }

    @Transactional
    public void put(String key, Object value) {
        try {
            putRaw(key, mapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Settings.Network network() { return get("network", Settings.Network.class, Settings.Network::defaults); }
    public Settings.DnsCollector dnsCollector() { return get("dns-collector", Settings.DnsCollector.class, Settings.DnsCollector::defaults); }
    public Settings.Alerting alerting() { return get("alerting", Settings.Alerting.class, Settings.Alerting::defaults); }

    /** Keeps the stored secret when the client sends back the mask or nothing. */
    public static String keepSecret(String incoming, String existing) {
        return incoming == null || incoming.equals(MASK) ? existing : incoming;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt setting", e);
        }
    }
}
