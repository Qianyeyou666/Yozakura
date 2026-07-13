package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BacktrackPacketOrderContractTest {
    @Test
    public void inboundQueueOverflowFlushesInOrderAndForwardsTheTriggerPacket() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacketIfDelayable(ChannelHandlerContext ctx, Object packet) {",
                "    private void forwardPacket(");
        String firePacket = method(source,
                "    private void firePacketLocked(final QueuedPacket queued) {",
                "    private static final class TrackedBox");
        String handler = method(source,
                "        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {",
                "    }\n}");

        assertTrue("Overflow must release the existing inbound FIFO before scheduling its trigger packet behind it",
                queuePacket.contains("releaseQueuedPacketsLocked();")
                        && queuePacket.contains("firePacketLocked(new QueuedPacket")
                        && queuePacket.contains("return true;"));
        assertTrue("The inbound handler must make the final queue decision under the module lock",
                handler.contains("module.queuePacketIfDelayable(ctx, msg)"));
        assertTrue("A queue flush from channelRead must fire delayed packets through the locked FIFO",
                firePacket.contains("queued.ctx.executor().inEventLoop()")
                        && firePacket.contains("queued.ctx.fireChannelRead(queued.packet);")
                        && firePacket.contains("return;"));
    }

    @Test
    public void inboundAdmissionDisableAndFallbackForwardingShareOneFifoLock() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacketIfDelayable(ChannelHandlerContext ctx, Object packet) {",
                "    private void forwardPacket(");
        String releaseDue = method(source, "    private void releaseDuePackets() {",
                "    private void releaseQueuedPackets()");
        String forward = method(source,
                "    private void forwardPacket(ChannelHandlerContext ctx, Object packet) {",
                "    private void firePacketLocked(");
        String handler = method(source,
                "        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {",
                "    }\n}");

        assertTrue("The incoming packet queue must use a shared delivery lock and a visible admission gate",
                source.contains("private final Object deliveryLock = new Object();")
                        && source.contains("private volatile boolean acceptingPackets;")
                        && queuePacket.contains("synchronized (deliveryLock)")
                        && queuePacket.contains("isDelayablePacket((Packet) packet)"));
        assertTrue("Tick-side release must drain under the same lock as Netty admission",
                releaseDue.contains("synchronized (deliveryLock)"));
        assertTrue("A non-delayed inbound packet must still serialize behind a scheduled release",
                handler.contains("module.forwardPacket(ctx, msg);")
                        && forward.contains("synchronized (deliveryLock)")
                        && forward.contains("firePacketLocked(new QueuedPacket"));
        assertFalse("A lock-free channelRead fallback can overtake a packet that the main thread just released",
                handler.contains("super.channelRead(ctx, msg);"));
    }

    @Test
    public void reconnectReleasesAndRemovesTheOldInboundHandlerBeforeInstallingTheNewOne() throws IOException {
        String source = source();
        String inject = method(source, "    private void injectHandler() {", "    private void removeHandler() {");

        int changedChannel = inject.indexOf("if (channel != null && channel != current)");
        int release = inject.indexOf("releaseQueuedPackets();", changedChannel);
        int remove = inject.indexOf("removeHandler();", release);
        int install = inject.indexOf("PacketPipelineAnchors.installDelayHandler", remove);
        assertTrue("An old channel must not retain a Backtrack handler after reconnect",
                changedChannel >= 0 && release > changedChannel && remove > release && install > remove);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/Backtrack.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
