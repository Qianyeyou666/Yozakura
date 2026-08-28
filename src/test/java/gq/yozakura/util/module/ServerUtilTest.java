package gq.yozakura.util.module;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerUtilTest {
    @Test
    public void recognizesHypixelHostsWithPortsAndSubdomains() {
        assertTrue(ServerUtil.isHypixelAddress("mc.hypixel.net"));
        assertTrue(ServerUtil.isHypixelAddress("proxy.hypixel.net:25565"));
        assertTrue(ServerUtil.isHypixelAddress("HYPIXEL.NET."));
    }

    @Test
    public void rejectsLookalikeAndUnrelatedHosts() {
        assertFalse(ServerUtil.isHypixelAddress("hypixel.net.example.com"));
        assertFalse(ServerUtil.isHypixelAddress("nothypixel.net"));
        assertFalse(ServerUtil.isHypixelAddress(null));
    }
}
