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
    public void preActionsWaitForTheirPublishedRotationGeneration() throws IOException {
        String source = source();
        String pre = method(source, "    private void dispatchPreUpdate() {", "    private void applyLocalViewRotation(");
        String write = writeMethod(source);
        String handler = source.substring(source.indexOf("    private final class PacketBridgeHandler"));

        int beginPre = pre.indexOf("rotationPublication.beginPre();");
        int exposePre = pre.indexOf("activePreUpdate = update;");
        int dispatch = pre.indexOf("EventManager.call(update);");
        int publish = pre.indexOf("rotationPublication.publish(");
        int finishPre = pre.indexOf("activePreUpdate = null;");
        assertTrue("PRE must expose an in-progress generation before listeners can send actions",
                beginPre >= 0 && exposePre > beginPre && dispatch > exposePre);
        assertTrue("The final listener rotation must be published before PRE is released",
                publish > dispatch && finishPre > publish);

        int delay = write.indexOf("shouldDelayUntilRotation(packet, rotation)");
        int markSent = write.indexOf("markSent(packet)");
        assertTrue("Rotation-sensitive actions must be queued before sent-state or Blink handling",
                delay >= 0 && markSent > delay);
        assertTrue("Forge must cover every rotation-sensitive vanilla action packet",
                source.contains("C02PacketUseEntity")
                        && source.contains("C07PacketPlayerDigging")
                        && source.contains("C08PacketPlayerBlockPlacement")
                        && source.contains("C0APacketAnimation"));
        assertTrue("Queued actions must be tied to a rotation generation",
                source.contains("requiredGeneration"));
        assertTrue("A C03 must mark its captured generation sent before eligible actions flush",
                handler.contains("rotationPublication.markSent(rotation)")
                        && handler.contains("flushDelayedPackets(ctx"));
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
