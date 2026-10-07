package com.nwmonitor.scan;

import com.nwmonitor.alert.AlertService;
import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import com.nwmonitor.collector.CollectorStatusRegistry;
import com.nwmonitor.device.Device;
import com.nwmonitor.device.DeviceRepository;
import com.nwmonitor.device.DeviceService;
import com.nwmonitor.device.DeviceType;
import com.nwmonitor.settings.Settings;
import com.nwmonitor.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Discovers devices: ping sweep to populate the ARP table, then read it. */
@Service
public class NetworkScanner {
    private static final Logger log = LoggerFactory.getLogger(NetworkScanner.class);
    public static final String STATUS_NAME = "network-scan";

    private final SettingsService settings;
    private final ArpTableReader arp;
    private final DeviceService devices;
    private final DeviceRepository deviceRepo;
    private final AlertService alerts;
    private final CollectorStatusRegistry status;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Instant lastRun = Instant.EPOCH;

    public NetworkScanner(SettingsService settings, ArpTableReader arp, DeviceService devices, DeviceRepository deviceRepo,
                          AlertService alerts, CollectorStatusRegistry status) {
        this.settings = settings;
        this.arp = arp;
        this.devices = devices;
        this.deviceRepo = deviceRepo;
        this.alerts = alerts;
        this.status = status;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    void tick() {
        Settings.Network cfg = settings.network();
        if (cfg.scanEnabled() && Instant.now().isAfter(lastRun.plusSeconds(cfg.scanIntervalSeconds()))) scanNow();
    }

    public Subnets.Cidr effectiveSubnet() {
        String configured = settings.network().subnetCidr();
        if (configured != null && !configured.isBlank()) return Subnets.Cidr.parse(configured);
        return Subnets.detectLocal().orElseThrow(() -> new IllegalStateException("Could not detect the local subnet; set it in Network settings"));
    }

    /** @return false if a scan was already running. */
    public boolean scanNow() {
        if (!running.compareAndSet(false, true)) return false;
        lastRun = Instant.now();
        try {
            Subnets.Cidr cidr = effectiveSubnet();
            sweep(cidr.hosts());
            Map<String, String> table = arp.read();
            boolean baseline = deviceRepo.count() == 0;
            int found = 0;
            for (var e : table.entrySet()) {
                if (!cidr.contains(e.getKey())) continue;
                Device d = devices.observe(e.getValue(), e.getKey(), null, baseline);
                if (d.getHostname() == null) resolveHostname(d);
                found++;
            }
            if (baseline && found > 0) {
                markGateway(cidr);
                alerts.raise(AlertType.BASELINE_ESTABLISHED, Severity.INFO, "Network baseline established",
                        found + " devices were found on the first scan and marked as trusted. Anything new from now on raises an alert.",
                        null, null, null);
            }
            int offline = devices.markStaleOffline();
            status.success(STATUS_NAME, found + " devices seen, " + offline + " went offline");
            return true;
        } catch (Exception e) {
            log.warn("Network scan failed: {}", e.getMessage());
            status.failure(STATUS_NAME, e.getMessage());
            return true;
        } finally {
            running.set(false);
        }
    }

    private void sweep(List<String> hosts) throws InterruptedException {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore limit = new Semaphore(128);
            for (String h : hosts) {
                limit.acquire();
                pool.submit(() -> {
                    try {
                        InetAddress.getByName(h).isReachable(700);
                    } catch (Exception ignored) {
                        // unreachable hosts are expected
                    } finally {
                        limit.release();
                    }
                });
            }
        }
    }

    private void resolveHostname(Device d) {
        if (d.getIp() == null) return;
        try {
            String name = CompletableFuture.supplyAsync(() -> {
                try {
                    return InetAddress.getByName(d.getIp()).getCanonicalHostName();
                } catch (Exception e) {
                    return d.getIp();
                }
            }).get(1500, TimeUnit.MILLISECONDS);
            if (!name.equals(d.getIp())) devices.observe(d.getMac(), d.getIp(), name, false);
        } catch (Exception ignored) {
            // hostname is best effort
        }
    }

    /** On the baseline scan, tag the configured/guessed gateway as the router. */
    private void markGateway(Subnets.Cidr cidr) {
        String gw = settings.network().gatewayIp();
        if (gw == null || gw.isBlank()) gw = Subnets.toIp(cidr.network() + 1);
        final String gateway = gw;
        deviceRepo.findFirstByIpOrderByLastSeenDesc(gateway).ifPresent(d -> {
            d.setDeviceType(DeviceType.ROUTER);
            if (d.getLabel() == null) d.setLabel("ISP Router");
            deviceRepo.save(d);
        });
    }
}
