package com.nwmonitor.settings;

import com.nwmonitor.alert.TelegramChannel;
import com.nwmonitor.collector.ActivityCollector;
import com.nwmonitor.collector.CollectorScheduler;
import com.nwmonitor.common.ApiException;
import com.nwmonitor.ingest.ApiKeyService;
import com.nwmonitor.scan.NetworkScanner;
import com.nwmonitor.scan.Subnets;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Configuration screens: network access, web-activity source, alerting. Secrets are masked on read. */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final SettingsService settings;
    private final CollectorScheduler collectors;
    private final TelegramChannel telegram;
    private final ApiKeyService apiKeys;
    private final NetworkScanner scanner;

    public SettingsController(SettingsService settings, CollectorScheduler collectors, TelegramChannel telegram, ApiKeyService apiKeys, NetworkScanner scanner) {
        this.settings = settings;
        this.collectors = collectors;
        this.telegram = telegram;
        this.apiKeys = apiKeys;
        this.scanner = scanner;
    }

    public record NetworkView(Settings.Network settings, String detectedSubnet) {}

    @GetMapping("/network")
    public NetworkView network() {
        String detected = Subnets.detectLocal().map(Object::toString).orElse(null);
        return new NetworkView(settings.network(), detected);
    }

    @PutMapping("/network")
    public NetworkView updateNetwork(@Valid @RequestBody Settings.Network n) {
        if (n.subnetCidr() != null && !n.subnetCidr().isBlank()) validate(() -> Subnets.Cidr.parse(n.subnetCidr()));
        if (n.gatewayIp() != null && !n.gatewayIp().isBlank()) validate(() -> Subnets.toLong(n.gatewayIp()));
        settings.put("network", n);
        return network();
    }

    @GetMapping("/dns-collector")
    public Settings.DnsCollector dns() {
        var c = settings.dnsCollector();
        return new Settings.DnsCollector(c.type(), c.url(), c.password() == null || c.password().isBlank() ? "" : SettingsService.MASK, c.pollSeconds());
    }

    @PutMapping("/dns-collector")
    public Settings.DnsCollector updateDns(@Valid @RequestBody Settings.DnsCollector c) {
        if (!"NONE".equals(c.type()) && collectors.find(c.type()) == null) throw ApiException.badRequest("Unknown collector type");
        settings.put("dns-collector", merged(c));
        return dns();
    }

    @PostMapping("/dns-collector/test")
    public Map<String, String> testDns(@Valid @RequestBody Settings.DnsCollector c) {
        ActivityCollector impl = collectors.find(c.type());
        if (impl == null) throw ApiException.badRequest("Choose a collector type first");
        return runTest(() -> impl.test(merged(c)));
    }

    private Settings.DnsCollector merged(Settings.DnsCollector c) {
        return new Settings.DnsCollector(c.type(), c.url(), SettingsService.keepSecret(c.password(), settings.dnsCollector().password()), c.pollSeconds());
    }

    @GetMapping("/alerting")
    public Settings.Alerting alerting() {
        var a = settings.alerting();
        return new Settings.Alerting(a.telegramEnabled(), a.telegramBotToken() == null || a.telegramBotToken().isBlank() ? "" : SettingsService.MASK,
                a.telegramChatId(), a.minNotifySeverity(), a.disabledTypes(), a.cooldownMinutes());
    }

    @PutMapping("/alerting")
    public Settings.Alerting updateAlerting(@Valid @RequestBody Settings.Alerting a) {
        settings.put("alerting", mergedAlerting(a));
        return alerting();
    }

    @PostMapping("/alerting/test-telegram")
    public Map<String, String> testTelegram(@Valid @RequestBody Settings.Alerting a) {
        var m = mergedAlerting(a);
        return runTest(() -> {
            telegram.sendText(m.telegramBotToken(), m.telegramChatId(), "✅ Network Monitor: test notification");
            return "Test message sent";
        });
    }

    private Settings.Alerting mergedAlerting(Settings.Alerting a) {
        return new Settings.Alerting(a.telegramEnabled(), SettingsService.keepSecret(a.telegramBotToken(), settings.alerting().telegramBotToken()),
                a.telegramChatId(), a.minNotifySeverity() == null ? com.nwmonitor.alert.Severity.INFO : a.minNotifySeverity(),
                a.disabledTypes() == null ? java.util.List.of() : a.disabledTypes(), a.cooldownMinutes());
    }

    public record IngestView(boolean keyConfigured) {}
    public record IngestKey(String apiKey) {}

    @GetMapping("/ingest")
    public IngestView ingest() { return new IngestView(apiKeys.isConfigured()); }

    /** Returns the new key once; collector agents must be updated with it. */
    @PostMapping("/ingest/rotate-key")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestKey rotate() { return new IngestKey(apiKeys.rotate()); }

    @PostMapping("/network/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void scan() { Thread.startVirtualThread(scanner::scanNow); }

    private static void validate(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    private interface Test { String run() throws Exception; }

    private static Map<String, String> runTest(Test t) {
        try {
            return Map.of("ok", "true", "message", t.run());
        } catch (Exception e) {
            return Map.of("ok", "false", "message", String.valueOf(e.getMessage()));
        }
    }
}
