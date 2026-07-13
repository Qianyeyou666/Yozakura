package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YozakuraEventBridgePacketLifecycleContractTest {
    @Test
    public void droppedOutboundPacketsAlwaysCompleteTheirPromise() throws IOException {
        String source = source();

        assertTrue("Blocked sprint packets must complete the caller promise",
                source.contains("completeDroppedWrite(promise);"));
        assertTrue("The dropped-write helper must complete without throwing on an already-finished promise",
                source.contains("promise.trySuccess();"));
    }

    @Test
    public void noEventPlayerPacketsStillPassThroughSilentRotationRewrite() throws IOException {
        String write = writeMethod(source());
        int noEventBranch = write.indexOf("consumeNoEvent(packet)");
        int packetEventDispatch = write.indexOf("new PacketEvent(EventType.SEND, packet)");
        String noEventSection = write.substring(noEventBranch, packetEventDispatch);

        assertTrue("The no-event marker must be tracked separately from packet processing",
                noEventSection.contains("skipPacketEvent"));
        assertFalse("No-event packets must not bypass rotation rewrite and packet lifecycle handling",
                noEventSection.contains("super.write(ctx, msg, promise)"));
    }

    @Test
    public void forcedLockViewRotationUpdatesTheLocalCamera() throws IOException {
        String source = source();

        assertTrue("The Forge PRE bridge must apply a forced RotationManager view update",
                source.contains("applyLocalViewRotation(update);"));
        assertTrue("Lock View is identified by RotationManager's forced-rotation state",
                source.contains("YozakuraRuntime.rotationManager.isRotated()"));
        assertTrue("Lock View must update both camera axes",
                source.contains("mc.thePlayer.rotationYaw = update.getNewYaw();")
                        && source.contains("mc.thePlayer.rotationPitch = update.getNewPitch();"));
    }

    @Test
    public void leavingTheWorldClearsPendingRotationExitState() throws IOException {
        String source = source();
        int tickStart = source.indexOf("    public void onClientTick(TickEvent.ClientTickEvent event) {");
        int tickEnd = source.indexOf("    @SubscribeEvent", tickStart + 1);
        String tick = source.substring(tickStart, tickEnd);
        String clearState = method(source, "    private void clearBridgeState() {", "    private void dispatchPreUpdate()");

        assertTrue("Auth loss and world disconnect must not carry a return rotation into the next server",
                count(tick, "clearBridgeState();") >= 2
                        && clearState.contains("RotationExitState.clear();"));
    }

    @Test
    public void nettyUsesOneImmutableRotationSnapshotPerPlayerPacket() throws IOException {
        String source = source();
        String write = writeMethod(source);

        assertTrue("Forge PRE must publish rotation through one immutable snapshot",
                source.contains("ForgeRotationPublication"));
        assertTrue("Each C03 write must capture one publication snapshot",
                write.contains("ForgeRotationPublication.Snapshot rotation = rotationPublication.snapshot();"));
        assertFalse("The Netty handler must not stitch together mutable RotationState fields",
                write.contains("RotationState.isActived()")
                        || write.contains("RotationState.getRotationYawHead()")
                        || write.contains("RotationState.getRotationPitch()"));
        assertTrue("The rewrite helper must consume the captured snapshot",
                source.contains("rewritePlayerPacket(C03PacketPlayer packet,")
                        && source.contains("ForgeRotationPublication.Snapshot rotation)"));
    }

    @Test
    public void postSensitiveActionsStayBeforeC03WithoutGivingUpSilentRotation() throws IOException {
        String source = source();
        String pre = method(source, "    private void dispatchPreUpdate() {", "    private void applyLocalViewRotation(");
        String write = writeMethod(source);
        String handler = source.substring(source.indexOf("    private final class PacketBridgeHandler"));
        String playerPacket = method(source,
                "        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet, ChannelPromise promise,",
                "        private C03PacketPlayer rewritePlayerPacket(");

        int beginPre = pre.indexOf("rotationPublication.beginPre();");
        int exposePre = pre.indexOf("activePreUpdate = update;");
        int dispatch = pre.indexOf("EventManager.call(update);");
        int publish = pre.indexOf("rotationPublication.publish(");
        int finishPre = pre.indexOf("activePreUpdate = null;");
        assertTrue("PRE must expose an in-progress generation before listeners can send actions",
                beginPre >= 0 && exposePre > beginPre && dispatch > exposePre);
        assertTrue("The final listener rotation must be published before PRE is released",
                publish > dispatch && finishPre > publish);

        assertTrue("Post-sensitive actions must enter a two-tick batch before Blink or raw Netty handling",
                write.contains("if (isPostSensitiveAction(packet))")
                        && write.contains("queueCurrentActionPacket(packet, promise);"));
        String actionClassifier = method(source,
                "        private boolean isPostSensitiveAction(Packet<?> packet) {",
                "        private void onRotationPublished(");
        assertTrue("Forge must batch only actions whose server interpretation depends on silent yaw",
                actionClassifier.contains("C02PacketUseEntity")
                        && actionClassifier.contains("C07PacketPlayerDigging")
                        && actionClassifier.contains("C08PacketPlayerBlockPlacement")
                        && actionClassifier.contains("C09PacketHeldItemChange")
                        && actionClassifier.contains("C0APacketAnimation")
                        && actionClassifier.contains("C0EPacketClickWindow"));
        assertFalse("Sprint/sneak transitions are movement state, not delayed combat actions",
                actionClassifier.contains("C0BPacketEntityAction"));
        assertFalse("Ability transitions must reach the server in their native movement order",
                actionClassifier.contains("C13PacketPlayerAbilities"));
        assertTrue("The close-window companion packet must stay behind an already queued inventory click",
                write.contains("packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow")
                        && source.contains("currentClickWindowPackets > 0")
                        && source.contains("readyClickWindowPackets > 0"));
        assertTrue("The packet handler must hold separate ready and current action batches",
                handler.contains("OutboundActionBatchQueue<DelayedPacket>"));

        int readyFlush = playerPacket.indexOf("flushReadyActionPackets(ctx);");
        int tickAdvance = playerPacket.indexOf("boolean playerTickAdvanced = playerPacketTickGate.consumeNextPlayerPacket();");
        int silentWrite = playerPacket.indexOf("super.write(ctx, rewritten, promise);");
        int markSent = playerPacket.indexOf("rotationPublication.markSent(rotation);");
        int promoteCurrent = playerPacket.indexOf("promoteCurrentActionPackets();");
        assertTrue("A prior silent action batch must leave before the following C03",
                tickAdvance >= 0 && readyFlush > tickAdvance && silentWrite > readyFlush);
        assertTrue("The current action batch becomes ready only after its C03 has published the rotation",
                markSent > silentWrite && promoteCurrent > markSent);

        int currentFlush = playerPacket.indexOf("flushCurrentActionPackets(ctx);");
        int normalWrite = playerPacket.indexOf("super.write(ctx, packet, promise);");
        assertTrue("Non-silent AutoClicker actions must be emitted before the native C03",
                currentFlush >= 0 && normalWrite > currentFlush);
        assertTrue("Extra same-tick C03 packets must not release actions after an earlier movement packet",
                playerPacket.contains("boolean playerTickAdvanced = playerPacketTickGate.consumeNextPlayerPacket()")
                        && playerPacket.contains("if (playerTickAdvanced && !rotation.isActive())"));
        assertTrue("Forge PRE must enqueue the exact published generation before the native player packet is sent",
                source.contains("handler.markNextPlayerPacketTick(published.getGeneration());"));
    }

    @Test
    public void delayedPromisesAreFailedOnDisconnectAndBlinkBuffersRewrittenC03() throws IOException {
        String source = source();
        String handler = source.substring(source.indexOf("    private final class PacketBridgeHandler"));

        int rewrite = handler.indexOf("C03PacketPlayer rewritten = rewritePlayerPacket(");
        int blinkRewritten = handler.indexOf("offerPacket(rewritten)", rewrite);
        assertTrue("Blink must buffer the silent-rotation C03, not the original packet",
                rewrite >= 0 && blinkRewritten > rewrite);
        assertTrue("Handler removal must fail queued promises",
                source.contains("handlerRemoved(ChannelHandlerContext ctx)")
                        && source.contains("failDelayedPackets(new ClosedChannelException())"));
        assertTrue("Channel disconnect must fail queued promises",
                source.contains("channelInactive(ChannelHandlerContext ctx)"));
        assertTrue("Promise cleanup must use non-throwing completion",
                source.contains("delayed.promise.tryFailure(cause);"));
    }

    private static String writeMethod(String source) {
        int begin = source.indexOf("        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        int finish = source.indexOf("        @Override", begin + 1);
        return source.substring(begin, finish);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String method(String source, String beginNeedle, String endNeedle) {
        int begin = source.indexOf(beginNeedle);
        int finish = source.indexOf(endNeedle, begin + beginNeedle.length());
        return source.substring(begin, finish);
    }

    private static int count(String source, String needle) {
        int matches = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            matches++;
            index += needle.length();
        }
        return matches;
    }
}
