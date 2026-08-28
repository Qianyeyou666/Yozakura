package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FakeLagPacketOrderContractTest {
    @Test
    public void releaseOnAttackKeepsTheVanillaAnimationAndAttackInOneFifoQueue() throws IOException {
        String source = source();
        String queueDecision = method(source,
                "    private boolean shouldQueuePacket(Object packet) {",
                "    private boolean queuePacket(");

        int releaseBegin = queueDecision.indexOf("if (packet instanceof C02PacketUseEntity");
        int releaseEnd = queueDecision.indexOf("if (packet instanceof C03PacketPlayer)", releaseBegin);
        assertTrue("Release On Attack must have a dedicated C02 decision branch",
                releaseBegin >= 0 && releaseEnd > releaseBegin);
        String release = queueDecision.substring(releaseBegin, releaseEnd);

        assertTrue("An attack release still needs to arm the prompt FIFO flush",
                release.contains("schedulePostAttackRelease();"));
        assertTrue("C02 must join the queue so it cannot overtake a preceding C0A animation",
                release.contains("return true;"));
        assertFalse("Bypassing C02 immediately creates C02 -> delayed C0A Post animation order",
                release.contains("return false;"));
        assertTrue("C0A remains an ordered member of the same queue",
                queueDecision.contains("isOrderedActionPacket(packet)")
                        && source.contains("packet instanceof C0APacketAnimation"));
        assertTrue("Queue flushing must dequeue from the FIFO head before writing",
                source.contains("while ((queued = queuedPackets.poll()) != null)"));
    }

    @Test
    public void queueOverflowSerializesTheTriggerPacketBehindEveryPreviouslyQueuedPacket() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private long queueDelay(");
        String writePacket = method(source,
                "    private void writePacketLocked(final QueuedPacket queued) {",
                "    private static final class QueuedPacket {");

        assertTrue("Every dequeue and delivery scheduling decision must share one cross-thread lock",
                source.contains("private final Object deliveryLock")
                        && source.contains("private int pendingDeliveryTasks;")
                        && queuePacket.contains("synchronized (deliveryLock)"));
        assertTrue("Overflow must enqueue the triggering packet behind its flushed predecessors instead of forwarding it",
                queuePacket.contains("releaseQueuedPacketsLocked();")
                        && queuePacket.contains("writePacketLocked(new QueuedPacket")
                        && queuePacket.contains("return true;"));
        assertTrue("A direct write is safe only after every earlier scheduled delivery has drained",
                writePacket.contains("queued.ctx.executor().inEventLoop() && pendingDeliveryTasks == 0")
                        && writePacket.contains("queued.ctx.writeAndFlush(queued.packet, queued.promise);")
                        && writePacket.contains("pendingDeliveryTasks++"));
    }

    @Test
    public void latencyModeCannotLetAnAnimationOrAttackOvertakeItsQueuedPlayerPacket() throws IOException {
        String source = source();
        String queueDecision = method(source,
                "    private boolean shouldQueuePacket(Object packet) {",
                "    private boolean queuePacket(");
        String orderedActions = method(source,
                "    private boolean isOrderedActionPacket(Object packet) {",
                "    private boolean queuePacket(");

        int playerPacket = queueDecision.indexOf("if (packet instanceof C03PacketPlayer)");
        int orderedAction = queueDecision.indexOf("if (isOrderedActionPacket(packet))");
        int latencyMode = queueDecision.indexOf("if (current == LagMode.LATENCY)");

        assertTrue("C03 must queue before deciding whether LATENCY bypasses other traffic",
                playerPacket >= 0 && orderedAction > playerPacket && latencyMode > orderedAction);
        assertTrue("C0A/C02 and other rotation-dependent actions must queue before the LATENCY bypass",
                queueDecision.contains("isOrderedActionPacket(packet)")
                        && source.contains("private boolean isOrderedActionPacket(Object packet)"));
        assertTrue("The ordered action helper must cover attack, animation, and hotbar-slot changes",
                orderedActions.contains("packet instanceof C02PacketUseEntity")
                        && orderedActions.contains("packet instanceof C0APacketAnimation")
                        && orderedActions.contains("packet instanceof C07PacketPlayerDigging")
                        && orderedActions.contains("packet instanceof C08PacketPlayerBlockPlacement")
                        && orderedActions.contains("packet instanceof C09PacketHeldItemChange"));
    }

    @Test
    public void disableAndBurstReleaseCannotRaceAStaleNettyQueueDecision() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private long queueDelay(");
        String handler = method(source,
                "        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {",
                "    }\n}");
        String admission = method(source, "    private void setLagAllowed(boolean allowed) {",
                "    private boolean shouldLagNow()");
        String forward = method(source,
                "    private void forwardPacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private void writePacketLocked(");

        assertTrue("The actual admission decision must be made while the delivery lock is held",
                queuePacket.contains("synchronized (deliveryLock)")
                        && queuePacket.contains("if (!shouldQueuePacket(packet))"));
        assertFalse("A stale outer preflight can enqueue after disable has already flushed the queue",
                handler.contains("shouldQueuePacket(msg)"));
        assertTrue("Every fallback write must reenter the lock so it cannot overtake a just-scheduled release",
                handler.contains("module.forwardPacket(ctx, msg, promise);")
                        && forward.contains("synchronized (deliveryLock)")
                        && forward.contains("writePacketLocked(new QueuedPacket"));
        assertTrue("A normal event-loop fallback must preserve the caller's existing flush rather than forcing one",
                forward.contains("ctx.write(packet, promise);"));
        assertTrue("Turning lag off and draining existing packets must be one atomic state transition",
                admission.contains("synchronized (deliveryLock)")
                        && admission.contains("lagAllowed = allowed;")
                        && admission.contains("releaseQueuedPacketsLocked();"));
    }

    @Test
    public void postAttackAndRepelReleaseSchedulesCannotBeClearedByARacingNettyAttack() throws IOException {
        String source = source();
        String tick = method(source,
                "    public void onTick(TickEvent.ClientTickEvent event) {",
                "    private void setLagAllowed(boolean allowed)");
        String postAttack = method(source, "    private boolean releasePostAttackIfDue(long now) {",
                "    private void scheduleNextBurst(long now)");
        String repel = method(source, "    private boolean releaseRepelBurstIfDue(long now) {",
                "    private boolean releasePostAttackIfDue(long now)");

        assertTrue("Tick must use atomic release helpers instead of clearing shared schedule fields directly",
                tick.contains("releaseRepelBurstIfDue(now)")
                        && tick.contains("releasePostAttackIfDue(now)")
                        && tick.contains("scheduleNextBurst(now)"));
        assertTrue("The attack release check, clear, and queue drain need one delivery-lock transition",
                postAttack.contains("synchronized (deliveryLock)")
                        && postAttack.contains("releaseAfterAttackAt = 0L;")
                        && postAttack.contains("releaseQueuedPacketsLocked();"));
        assertTrue("REPEL burst updates must share that same lock with C02's nextBurstAt update",
                repel.contains("synchronized (deliveryLock)")
                        && repel.contains("nextBurstAt = now + repelBurstInterval();"));
    }

    @Test
    public void teleportBoundaryDropsStaleDelayAndBypassesTheRequiredPositionConfirmation() throws IOException {
        String source = source();
        String queuePacket = method(source,
                "    private boolean queuePacket(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) {",
                "    private long queueDelay(");
        String boundary = method(source,
                "    public void onTeleportBoundary(TeleportBoundaryEvent event) {",
                "    private void setLagAllowed(");

        assertTrue("S08 acceptance must atomically discard pre-teleport packets and arm a confirmation bypass",
                boundary.contains("discardQueuedPacketsLocked();")
                        && boundary.contains("teleportConfirmationPending = true;"));
        assertTrue("The required Vanilla C04/C06 must bypass FakeLag instead of waiting behind a transaction",
                queuePacket.contains("shouldBypassForTeleport(packet)"));
        assertTrue("Only a position-bearing player packet may consume the bypass",
                source.contains("playerPacket.isMoving()")
                        && source.contains("teleportConfirmationPending = false;"));
        assertTrue("A pre-teleport delivery already scheduled on Netty must be invalidated by epoch",
                source.contains("deliveryEpoch++;")
                        && source.contains("queued.epoch != deliveryEpoch"));
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
                "src/main/java/gq/yozakura/module/combat/FakeLag.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
