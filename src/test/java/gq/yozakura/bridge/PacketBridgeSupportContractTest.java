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

        assertTrue("Identity-based markers need a FIFO because the same packet instance may be replayed twice",
                source.contains("IdentityHashMap<Packet<?>, Deque<NoEventMarker>>"));
        assertTrue("Consuming one replay must retain any later metadata for that packet identity",
                source.contains("markers.offerLast(marker)") && source.contains("markers.pollFirst()"));
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
        int mark = support.indexOf("storeNoEventMarker(packet, writeId, alreadyBridgeProcessed)", method);
        int send = support.indexOf("channel.writeAndFlush(packet, promise)", mark);

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

    @Test
    public void replayedBlinkPacketsKeepTheirAcceptedWriteIdWithoutReenteringActionBatching() throws IOException {
        String support = source();
        String blink = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/manager/BlinkManager.java")), StandardCharsets.UTF_8);
        String forge = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java")), StandardCharsets.UTF_8);
        String standalone = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);

        assertTrue("No-event replay must carry an optional accepted write id alongside its marker",
                support.contains("class NoEventMarker")
                        && support.contains("storeNoEventMarker(packet, writeId, alreadyBridgeProcessed)")
                        && support.contains("consumeNoEventMarker"));
        assertTrue("Blink must store packet metadata instead of losing the accepted id",
                blink.contains("offerPacket(Packet<?> packet, long writeId)")
                        && blink.contains("offerPacket(Packet<?> packet, ChannelPromise promise, long writeId)")
                        && blink.contains("PacketUtil.sendPacketNoEvent(blinkedPacket.packet, blinkedPacket.promise,")
                        && blink.contains("blinkedPacket.writeId, blinkedPacket.alreadyBridgeProcessed);"));
        assertTrue("A replayed no-event action must not enter the silent-action queue a second time",
                forge.contains("if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))")
                        && standalone.contains(
                                "if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/PacketBridgeSupport.java")), StandardCharsets.UTF_8);
    }
}
