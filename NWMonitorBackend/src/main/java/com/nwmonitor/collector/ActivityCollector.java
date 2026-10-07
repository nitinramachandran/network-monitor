package com.nwmonitor.collector;

import com.nwmonitor.settings.Settings;

/** A pull-based source of DNS/web activity. Implement and register as a bean to add another (AdGuard Home, router API, ...). */
public interface ActivityCollector {
    /** Matches Settings.DnsCollector#type. */
    String type();

    /** Fetches new events and stores them; returns a human-readable summary. */
    String collect(Settings.DnsCollector cfg) throws Exception;

    /** Verifies connectivity/credentials without storing anything; throws on failure. */
    default String test(Settings.DnsCollector cfg) throws Exception { return "OK"; }
}
