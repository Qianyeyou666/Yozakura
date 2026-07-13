package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;

public class PacketNoEventLifecycleContractTest {
    @Test
    public void directReceiveNoEventDoesNotLeakPacketsIntoAnUnusedSkipList() throws IOException {
        String packetUtil = source("src/main/java/gq/yozakura/util/module/PacketUtil.java");
        String forgeBridge = source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java");
        String standaloneBridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertFalse("Direct processPacket calls bypass channelRead, so a skip list only leaks packet instances",
                packetUtil.contains("skipReceiveEvent"));
        assertFalse("The Forge bridge must not share an unsynchronized receive marker list",
                forgeBridge.contains("skipReceiveEvent"));
        assertFalse("The standalone bridge must not share an unsynchronized receive marker list",
                standaloneBridge.contains("skipReceiveEvent"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
