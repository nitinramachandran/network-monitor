package com.nwmonitor.scan;

import com.nwmonitor.device.DeviceService;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the OS neighbour table: /proc/net/arp on Linux, `arp -an` elsewhere (macOS). Returns ip -> normalized mac. */
@Component
public class ArpTableReader {
    private static final Pattern ARP_AN = Pattern.compile("\\((\\d+\\.\\d+\\.\\d+\\.\\d+)\\) at ([0-9a-fA-F:]+) ");

    public Map<String, String> read() {
        try {
            Path proc = Path.of("/proc/net/arp");
            return Files.isReadable(proc) ? readProc(proc) : readArpCommand();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Map.of();
        }
    }

    private Map<String, String> readProc(Path proc) throws IOException {
        return parseProc(Files.readAllLines(proc));
    }

    /** Linux /proc/net/arp: header line, then "IP type flags MAC mask device"; flags 0x0 = incomplete. */
    static Map<String, String> parseProc(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
            String[] c = line.trim().split("\\s+");
            if (c.length >= 4 && !"0x0".equals(c[2])) put(out, c[0], c[3]);
        }
        return out;
    }

    private Map<String, String> readArpCommand() throws IOException, InterruptedException {
        Process p = new ProcessBuilder("arp", "-an").redirectErrorStream(true).start();
        List<String> lines;
        try (var r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            lines = r.lines().toList();
        }
        p.waitFor();
        return parseArpAn(lines);
    }

    /** `arp -an` (macOS/BSD): "? (192.168.1.9) at f4:8c:eb:ab:76:e6 on en0 ..."; "(incomplete)" entries are skipped. */
    static Map<String, String> parseArpAn(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher m = ARP_AN.matcher(line);
            if (m.find()) put(out, m.group(1), padOctets(m.group(2)));
        }
        return out;
    }

    /** macOS prints octets without leading zeros ("a:b:c"). */
    private static String padOctets(String mac) {
        StringBuilder sb = new StringBuilder();
        for (String part : mac.split(":")) sb.append(sb.isEmpty() ? "" : ":").append(part.length() == 1 ? "0" + part : part);
        return sb.toString();
    }

    private static void put(Map<String, String> out, String ip, String rawMac) {
        try {
            String mac = DeviceService.normalizeMac(rawMac);
            if (mac.equals("00:00:00:00:00:00") || mac.equals("ff:ff:ff:ff:ff:ff") || mac.startsWith("01:00:5e")) return;
            out.put(ip, mac);
        } catch (IllegalArgumentException ignored) {
            // incomplete entry
        }
    }
}
