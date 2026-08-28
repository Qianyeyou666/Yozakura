package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class StandaloneEventBridgeDelayedPacketOrderContractTest {
    @Test
    public void releasesReadyActionsBeforeTheNextSilentPlayerPacket() throws IOException {
        String combined = combinedSource();
        String writeMethod = findWritePlayerPacketCommonMethod(combined);
        int readyActionFlush = writeMethod.indexOf("flushReadyActionPackets(ctx);");
        int rewrittenC03Write = writeMethod.indexOf("super.write(ctx, rewritten, promise);");
        int promoteCurrentActions = writeMethod.indexOf("promoteCurrentActionPackets();");

        assertTrue("Actions prepared by the preceding silent tick must be written before its next C03",
                readyActionFlush >= 0 && rewrittenC03Write > readyActionFlush);
        assertTrue("Current-tick actions must wait for the following C03 after the silent rotation is written",
                promoteCurrentActions > rewrittenC03Write);
    }

    @Test
    public void doesNotReleaseASilentActionBatchForAnExtraPlayerPacketInTheSameTick() throws IOException {
        String combined = combinedSource();
        String writeMethod = findWritePlayerPacketCommonMethod(combined);
        int tickAdvance = writeMethod.indexOf(
                "boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket(");
        int readyActionFlush = writeMethod.indexOf("flushReadyActionPackets(ctx);");

        assertTrue("A queued silent action batch must wait for a new player tick, not merely any C03 packet",
                tickAdvance >= 0 && readyActionFlush > tickAdvance);
    }

    @Test
    public void marksOnlyTheNativePlayerTickAfterMovementProcessingInsteadOfDuringPreDispatch()
            throws IOException {
        String bridge = standaloneSource();
        String preDispatch = method(bridge,
                "    private void dispatchPreUpdateBeforePlayerPacket() {",
                "    private void markNextPlayerPacketTick() {");
        String input = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/MovementInputBridge.java")), StandardCharsets.UTF_8);
        int physicsRotation = input.indexOf("applyRotationForPhysics(input);");
        int nativeBoundaryMarker = input.indexOf("Runnable postInputHook = afterMoveInputHook;");

        assertFalse("PRE runs before MoveInput/Strafe/LivingUpdate listeners; it cannot consume the native C03 boundary",
                preDispatch.contains("markNextPlayerPacketTick();"));
        assertTrue("The native C03 marker must run after all bridge-side input events and physics preparation",
                physicsRotation >= 0 && nativeBoundaryMarker > physicsRotation);
        assertTrue("Standalone must own the post-input native-packet boundary explicitly",
                bridge.contains("MovementInputBridge.setAfterMoveInputHook("));
        assertFalse("The obsolete packet hook name must not return because it misdescribes the input boundary",
                input.contains("beforePlayerPacketHook") || input.contains("setBeforePlayerPacketHook("));
    }

    @Test
    public void replaysAnEarlyPlayerTickMarkerWhenTheNettyHandlerContextArrives() throws IOException {
        String combined = combinedSource();
        String marker = method(combined,
                "    void markNextPlayerPacketTick(final long generation) {",
                "    protected synchronized void storePendingPlayerPacketGeneration(long generation) {");

        assertTrue("A marker emitted while Netty is installing the handler must be retained",
                combined.contains("pendingPlayerPacketGeneration"));
        assertTrue("handlerAdded must replay a retained marker before the first native C03 can pass",
                combined.contains("drainPendingPlayerPacketTick(ctx);"));
        assertTrue("A new marker must be retained before it attempts to access the handler context",
                marker.indexOf("storePendingPlayerPacketGeneration(generation);")
                        < marker.indexOf("final ChannelHandlerContext ctx = current;"));
    }

    @Test
    public void batchesEveryGrimPostSensitiveActionPacketBeforeAPlayerPacket() throws IOException {
        String combined = combinedSource();
        String actionClassifier = method(combined,
                "    protected boolean isPostSensitiveAction(Packet<?> packet) {",
                "    protected void queueCurrentActionPacket(");

        assertTrue("Animation and attack must be queued as one FIFO action batch",
                actionClassifier.contains("C0APacketAnimation")
                        && actionClassifier.contains("C02PacketUseEntity"));
        assertTrue("Hotbar changes that accompany an action must stay in its FIFO batch",
                actionClassifier.contains("C09PacketHeldItemChange"));
        assertTrue("Digging and placement/use packets are also Post-sensitive in 1.8",
                actionClassifier.contains("C07PacketPlayerDigging")
                        && actionClassifier.contains("C08PacketPlayerBlockPlacement"));
        assertTrue("Inventory click actions must keep their FIFO packet order",
                actionClassifier.contains("C0EPacketClickWindow"));
        assertFalse("Sprint/sneak state must preserve its native relation to C03 for server movement simulation",
                actionClassifier.contains("C0BPacketEntityAction"));
        assertFalse("Ability state changes must not be delayed behind a movement packet",
                actionClassifier.contains("C13PacketPlayerAbilities"));
        assertTrue("The standalone bridge must default every normal action to its vanilla write position",
                packetBridgeWriteMethod(combined).contains(
                                "boolean preserveOriginalPacketOrder = true;")
                        && packetBridgeWriteMethod(combined).contains(
                                "afterCurrentRotation = accepted.isAfterCurrentRotationRequired();")
                        && packetBridgeWriteMethod(combined).contains(
                                "preserveOriginalPacketOrder = !afterCurrentRotation")
                        && packetBridgeWriteMethod(combined).contains(
                                "if (!skipPacketEvent && !preserveOriginalPacketOrder && isPostSensitiveAction(packet))")
                        && packetBridgeWriteMethod(combined).contains(
                                "queueCurrentActionPacket(packet, promise, writeId);"));
        assertTrue("Closing an inventory must follow an already queued click-window packet without batching alone",
                packetBridgeWriteMethod(combined).contains("packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow")
                        && combined.contains("currentClickWindowPackets > 0")
                        && combined.contains("readyClickWindowPackets > 0"));
        assertFalse("An isolated close-window packet must keep its native order with unrelated packets",
                actionClassifier.contains("C0DPacketCloseWindow"));
    }

    private static String packetBridgeWriteMethod(String source) {
        int begin = source.indexOf("    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        if (begin < 0) {
            begin = source.indexOf("        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        }
        int finish = source.indexOf("    protected void observePacketWrite(", begin);
        if (finish < 0) {
            finish = source.indexOf("    @Override", begin + 1);
        }
        if (finish < 0) {
            finish = source.indexOf("        @Override", begin + 1);
        }
        if (begin < 0 || finish <= begin) {
            return "";
        }
        return source.substring(begin, finish);
    }

    private static String findWritePlayerPacketCommonMethod(String source) {
        int begin = source.indexOf(
                "    protected void writePlayerPacketCommon(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        if (begin < 0) {
            begin = source.indexOf(
                    "        protected void writePlayerPacketInternal(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        }
        if (begin < 0) {
            begin = source.indexOf(
                    "        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        }
        if (begin < 0) {
            return source;
        }
        int end = source.indexOf(
                "    void markNextPlayerPacketTick(", begin);
        if (end < 0) {
            end = source.indexOf(
                    "        protected void logActionQueue(", begin);
        }
        if (end < 0) {
            end = source.indexOf(
                    "        @Override", begin);
        }
        if (end < 0 || end <= begin) {
            end = Math.min(begin + 600, source.length());
        }
        return source.substring(begin, end);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }

    private static String standaloneSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);
    }

    private static String baseSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")), StandardCharsets.UTF_8);
    }

    private static String combinedSource() throws IOException {
        return baseSource() + "\n" + standaloneSource();
    }
}
