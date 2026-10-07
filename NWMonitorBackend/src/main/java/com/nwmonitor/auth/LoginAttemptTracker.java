package com.nwmonitor.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Counts failed logins per client address inside a sliding window. */
@Component
public class LoginAttemptTracker {
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** Records a failure and returns the number of failures from this address inside the window. */
    public int recordFailure(String clientIp) {
        Deque<Instant> q = failures.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant now = Instant.now();
            q.addLast(now);
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) q.pollFirst();
            return q.size();
        }
    }

    public void clear(String clientIp) { failures.remove(clientIp); }
}
