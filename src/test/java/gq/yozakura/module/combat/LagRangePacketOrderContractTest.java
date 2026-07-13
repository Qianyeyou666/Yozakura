package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LagRangePacketOrderContractTest {
    @Test
    public void queueOverflowCannotLetAnAttackOvertakeTheQueuedAnimation() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacketIfLagging(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private void forwardPacket(");
        String writePacket = method(source,
                "    private void writePacketLocked(final QueuedPacket queued) {",
                "    private int maxQueuedPackets()");

        assertTrue("LagRange delays both vanilla animation and attack packets in one FIFO queue",
                source.contains("packet instanceof C02PacketUseEntity")
                        && source.contains("packet instanceof C0APacketAnimation"));
        assertTrue("Overflow must release the existing FIFO before scheduling its trigger packet behind it",
                queuePacket.contains("flushLagLocked();")
                        && queuePacket.contains("writePacketLocked(new QueuedPacket"));
        assertTrue("A flush performed inside the Netty write callback must write immediately in order",
                writePacket.contains("queued.ctx.executor().inEventLoop()")
                        && writePacket.contains("queued.ctx.writeAndFlush(queued.packet, queued.promise);")
                        && writePacket.contains("return;"));
    }

    @Test
    public void queueAdmissionAndReleaseAreAtomicAcrossNettyAndTheMinecraftThread() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacketIfLagging(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private void forwardPacket(");
        String flush = method(source, "    private void flushLag() {", "    private void releaseExpiredPackets() {");
        String release = method(source, "    private void releaseExpiredPackets() {", "    private boolean isQueueablePacket(");
        String eligibility = method(source, "    private boolean isQueueablePacket(Object packet) {",
                "    private boolean queuePacketIfLagging(");
        String handler = method(source,
                "        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {",
                "    }\n}");
        String writePacket = method(source,
                "    private void writePacketLocked(final QueuedPacket queued) {",
                "    private int maxQueuedPackets()");
        String forward = method(source,
                "    private void forwardPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private void writePacketLocked(");

        assertTrue("The packet queue needs one lock shared by admission, draining, and reset paths",
                source.contains("private final Object deliveryLock = new Object();")
                        && queuePacket.contains("synchronized (deliveryLock)")
                        && flush.contains("synchronized (deliveryLock)")
                        && release.contains("synchronized (deliveryLock)"));
        assertTrue("Lag state must be rechecked while the admission lock is held",
                source.contains("private volatile boolean lagging;")
                        && queuePacket.contains("isQueueablePacket(packet)")
                        && eligibility.contains("!lagging"));
        assertTrue("Every bypass packet must serialize through the delivery lock after a scheduled release",
                handler.contains("module.forwardPacket(ctx, msg, promise);")
                        && forward.contains("synchronized (deliveryLock)")
                        && forward.contains("writePacketLocked(new QueuedPacket"));
        assertTrue("A normal event-loop fallback must retain the caller's existing flush behavior",
                forward.contains("ctx.write(packet, promise);"));
        assertFalse("A lock-free fallback can overtake a main-thread release scheduled just after its check",
                handler.contains("super.write(ctx, msg, promise);"));
        assertTrue("Cross-thread releases must retain FIFO until their executor tasks complete",
                source.contains("private int pendingDeliveryTasks;")
                        && writePacket.contains("pendingDeliveryTasks++")
                        && writePacket.contains("pendingDeliveryTasks--"));
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
                "src/main/java/gq/yozakura/module/combat/LagRange.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
