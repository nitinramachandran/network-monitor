package com.nwmonitor.scan;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Subnets {
    private Subnets() {}

    public record Cidr(long network, int prefix) {
        public static Cidr parse(String s) {
            String[] p = s.trim().split("/");
            if (p.length != 2) throw new IllegalArgumentException("CIDR expected, e.g. 192.168.1.0/24");
            int prefix = Integer.parseInt(p[1]);
            if (prefix < 16 || prefix > 30) throw new IllegalArgumentException("Prefix must be between /16 and /30");
            long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return new Cidr(toLong(p[0]) & mask, prefix);
        }

        public boolean contains(String ip) {
            try {
                long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
                return (toLong(ip) & mask) == network;
            } catch (RuntimeException e) {
                return false;
            }
        }

        /** Usable host addresses (excludes network and broadcast). */
        public List<String> hosts() {
            long size = 1L << (32 - prefix);
            List<String> out = new ArrayList<>();
            for (long i = 1; i < size - 1; i++) out.add(toIp(network + i));
            return out;
        }

        @Override
        public String toString() { return toIp(network) + "/" + prefix; }
    }

    public static long toLong(String ip) {
        String[] o = ip.trim().split("\\.");
        if (o.length != 4) throw new IllegalArgumentException("Bad IPv4: " + ip);
        long v = 0;
        for (String x : o) {
            int n = Integer.parseInt(x);
            if (n < 0 || n > 255) throw new IllegalArgumentException("Bad IPv4: " + ip);
            v = (v << 8) | n;
        }
        return v;
    }

    public static String toIp(long v) {
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    /**
     * The subnet of the interface that carries the default route (found by "connecting" a UDP socket, which sends
     * nothing), so virtual bridges from Docker/VMs are not picked. Capped at /24 for scan sanity.
     */
    public static Optional<Cidr> detectLocal() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = s.getLocalAddress();
            NetworkInterface ni = NetworkInterface.getByInetAddress(local);
            if (ni != null && local.isSiteLocalAddress()) {
                for (var ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress().equals(local)) {
                        return Optional.of(Cidr.parse(local.getHostAddress() + "/" + Math.max(ia.getNetworkPrefixLength(), 24)));
                    }
                }
            }
        } catch (Exception ignored) {
            // no route: caller asks the user to configure the subnet
        }
        return Optional.empty();
    }
}
