package com.nwmonitor.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.nwmonitor.settings.Settings;
import com.nwmonitor.settings.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

/** Pulls the DNS query log from a Pi-hole v6 instance: this is how visited sites are attributed to each device. */
@Component
public class PiHoleCollector implements ActivityCollector {
    private static final String CURSOR_KEY = "pihole-cursor";
    private final RestClient http = RestClient.create();
    private final EventSink sink;
    private final SettingsService settings;

    public PiHoleCollector(EventSink sink, SettingsService settings) {
        this.sink = sink;
        this.settings = settings;
    }

    @Override
    public String type() { return "PIHOLE"; }

    @Override
    public String test(Settings.DnsCollector cfg) {
        String sid = login(cfg);
        logout(cfg, sid);
        return "Authenticated with Pi-hole";
    }

    @Override
    public String collect(Settings.DnsCollector cfg) {
        double cursor = settings.getRaw(CURSOR_KEY).map(Double::parseDouble).orElse(Instant.now().getEpochSecond() - 300.0);
        long until = Instant.now().getEpochSecond() + 1;
        String sid = login(cfg);
        try {
            JsonNode body = http.get()
                    .uri(base(cfg) + "/api/queries?from={from}&until={until}&length=5000", (long) cursor, until)
                    .header("sid", sid).retrieve().body(JsonNode.class);
            var events = new ArrayList<EventSink.DnsEvent>();
            double max = cursor;
            for (JsonNode q : body.path("queries")) {
                double t = q.path("time").asDouble();
                if (t <= cursor) continue;
                max = Math.max(max, t);
                events.add(new EventSink.DnsEvent(q.path("client").path("ip").asText(null), null, q.path("domain").asText(null),
                        Instant.ofEpochMilli((long) (t * 1000)), isBlocked(q.path("status").asText(""))));
            }
            var r = sink.dns(events);
            settings.putRaw(CURSOR_KEY, Double.toString(max));
            return r.accepted() + " queries stored, " + r.unmatched() + " from unknown clients";
        } finally {
            logout(cfg, sid);
        }
    }

    static boolean isBlocked(String status) {
        return status.contains("GRAVITY") || status.contains("REGEX") || status.contains("DENYLIST") || status.contains("BLOCKED");
    }

    private String login(Settings.DnsCollector cfg) {
        JsonNode res = http.post().uri(base(cfg) + "/api/auth").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("password", cfg.password() == null ? "" : cfg.password())).retrieve().body(JsonNode.class);
        String sid = res.path("session").path("sid").asText(null);
        if (sid == null) throw new IllegalStateException("Pi-hole rejected the password");
        return sid;
    }

    private void logout(Settings.DnsCollector cfg, String sid) {
        try {
            http.delete().uri(base(cfg) + "/api/auth").header("sid", sid).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // session expires on its own
        }
    }

    private static String base(Settings.DnsCollector cfg) {
        String u = cfg.url() == null ? "" : cfg.url().trim();
        if (u.isEmpty()) throw new IllegalStateException("Pi-hole URL is not configured");
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
