package com.nwmonitor.collector;

import com.nwmonitor.device.DeviceRepository;
import com.nwmonitor.device.DeviceStatus;
import com.nwmonitor.device.DeviceType;
import com.nwmonitor.settings.Settings;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Generates plausible browsing and traffic for discovered devices so the UI can be explored without real capture. */
@Component
public class SimulatedCollector implements ActivityCollector {
    private static final List<String> DOMAINS = List.of("google.com", "youtube.com", "netflix.com", "wikipedia.org", "github.com",
            "amazon.in", "instagram.com", "whatsapp.net", "hotstar.com", "flipkart.com", "cloudflare.com", "apple.com",
            "spotify.com", "reddit.com", "stackoverflow.com", "ads.tracker-example.net");

    private final DeviceRepository devices;
    private final EventSink sink;

    public SimulatedCollector(DeviceRepository devices, EventSink sink) {
        this.devices = devices;
        this.sink = sink;
    }

    @Override
    public String type() { return "SIMULATED"; }

    @Override
    public String collect(Settings.DnsCollector cfg) {
        var rnd = ThreadLocalRandom.current();
        var dns = new ArrayList<EventSink.DnsEvent>();
        var usage = new ArrayList<EventSink.UsageSample>();
        Instant now = Instant.now();
        for (var d : devices.findAll()) {
            if (d.getStatus() != DeviceStatus.ONLINE || d.getDeviceType() == DeviceType.ROUTER) continue;
            for (int i = rnd.nextInt(0, 6); i > 0; i--) {
                String domain = DOMAINS.get(rnd.nextInt(DOMAINS.size()));
                dns.add(new EventSink.DnsEvent(d.getIp(), d.getMac(), domain, now.minusSeconds(rnd.nextInt(0, cfg.pollSeconds())), domain.startsWith("ads.")));
            }
            long down = rnd.nextLong(50_000, 6_000_000), up = down / rnd.nextLong(8, 30);
            usage.add(new EventSink.UsageSample(d.getIp(), d.getMac(), now, down, up));
        }
        sink.dns(dns);
        sink.usage(usage);
        return dns.size() + " simulated queries, " + usage.size() + " usage samples";
    }
}
