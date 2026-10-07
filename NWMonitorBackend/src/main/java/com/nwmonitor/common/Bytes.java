package com.nwmonitor.common;

public final class Bytes {
    private Bytes() {}

    /** MB here means 1024*1024 bytes, rounded to 2 decimals. */
    public static double toMb(long bytes) {
        return Math.round(bytes / 1_048_576.0 * 100.0) / 100.0;
    }
}
