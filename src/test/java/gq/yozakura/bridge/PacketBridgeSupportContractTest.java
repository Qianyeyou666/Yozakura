package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PacketBridgeSupportContractTest {
    @Test
    public void repeatedPacketInstancesKeepOneNoEventMarkerPerSend() throws IOException {
        String source = source();

        assertTrue("Identity-based markers need a count because the same packet instance may be sent twice",
                source.contains("IdentityHashMap<Packet<?>, Integer>"));
        assertTrue("Consuming one send must retain any remaining marker count",
                source.contains("count.intValue() > 1") && source.contains("count.intValue() - 1"));
    }

    @Test
    public void bridgeLifecycleCanReleaseUnconsumedPacketMarkers() throws IOException {
        String source = source();

        assertTrue("Disconnect and shutdown paths need an explicit marker clear operation",
                source.contains("public static void clearNoEventPackets()")
                        && source.contains("NO_EVENT_PACKETS.clear();"));
    }

    @Test
    public void noEventMarkerIsCreatedOnTheChannelEventLoopForItsExactWrite() throws IOException {
        String support = source();
        String packetUtil = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/util/module/PacketUtil.java")), StandardCharsets.UTF_8);
        int method = support.indexOf("public static void sendNoEvent(");
        int task = support.indexOf("final Runnable sendTask", method);
        int mark = support.indexOf("markNoEvent(packet);", method);
        int send = support.indexOf("manager.sendPacket(packet", mark);

        assertTrue("Normal writes already queued ahead of a no-event send must consume no marker",
                method >= 0 && task > method && mark > task && send > mark);
        assertTrue("Calls already on Netty must preserve their current-task ordering",
                support.contains("channel.eventLoop().inEventLoop()")
                        && support.contains("sendTask.run();")
                        && support.contains("channel.eventLoop().execute(sendTask);"));
        assertTrue("A closed channel or missing bridge must be rejected before a marker is created",
                support.contains("if (!channel.isOpen() || !hasPacketBridge(channel))"));
        assertTrue("PacketUtil must use the ordered bridge helper instead of marking on the caller thread",
                packetUtil.contains("PacketBridgeSupport.sendNoEvent("));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/PacketBridgeSupport.java")), StandardCharsets.UTF_8);
    }
}
