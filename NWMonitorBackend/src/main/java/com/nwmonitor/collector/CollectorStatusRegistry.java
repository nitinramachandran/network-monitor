package com.nwmonitor.collector;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory health of every data source (scanner, DNS collector, ingest agents) for the Connectivity screen. */
@Component
public class CollectorStatusRegistry {
    public record Status(String name, Instant lastSuccess, Instant lastFailure, String lastMessage, int consecutiveFailures) {}

    private final Map<String, Status> statuses = new ConcurrentHashMap<>();

    public void success(String name, String message) {
        statuses.merge(name, new Status(name, Instant.now(), null, message, 0),
                (old, n) -> new Status(name, n.lastSuccess(), old.lastFailure(), message, 0));
    }

    /** @return consecutive failure count after this failure. */
    public int failure(String name, String message) {
        return statuses.merge(name, new Status(name, null, Instant.now(), message, 1),
                (old, n) -> new Status(name, old.lastSuccess(), n.lastFailure(), message, old.consecutiveFailures() + 1)).consecutiveFailures();
    }

    public List<Status> all() { return statuses.values().stream().sorted(java.util.Comparator.comparing(Status::name)).toList(); }
}
