package com.nwmonitor.scan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArpTableReaderTest {
    @Test
    void parsesLinuxProcArp() {
        var m = ArpTableReader.parseProc(List.of(
                "IP address       HW type     Flags       HW address            Mask     Device",
                "192.168.1.1      0x1         0x2         AA:BB:CC:00:11:22     *        eth0",
                "192.168.1.7      0x1         0x0         00:00:00:00:00:00     *        eth0"));
        assertEquals(Map.of("192.168.1.1", "aa:bb:cc:00:11:22"), m);
    }

    @Test
    void parsesMacOsArpAndPadsOctets() {
        var m = ArpTableReader.parseArpAn(List.of(
                "? (192.168.1.9) at f4:8c:eb:ab:76:e6 on en0 ifscope [ethernet]",
                "? (192.168.1.7) at (incomplete) on en0 ifscope [ethernet]",
                "? (192.168.1.20) at a:b:c:d:e:f on en0 ifscope [ethernet]",
                "? (224.0.0.251) at 1:0:5e:0:0:fb on en0 ifscope permanent [ethernet]"));
        assertEquals("f4:8c:eb:ab:76:e6", m.get("192.168.1.9"));
        assertEquals("0a:0b:0c:0d:0e:0f", m.get("192.168.1.20"));
        assertTrue(!m.containsKey("192.168.1.7") && !m.containsKey("224.0.0.251"));
    }

    @Test
    void cidrContainsAndHosts() {
        var c = Subnets.Cidr.parse("192.168.1.77/24");
        assertEquals("192.168.1.0/24", c.toString());
        assertEquals(254, c.hosts().size());
        assertTrue(c.contains("192.168.1.9") && !c.contains("192.168.2.9"));
    }
}
