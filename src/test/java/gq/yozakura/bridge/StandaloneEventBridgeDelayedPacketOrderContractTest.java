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
        String playerPacket = playerPacketWriteMethod(source());
        int readyActionFlush = playerPacket.indexOf("flushReadyActionPackets(ctx);");
        int rewrittenC03Write = playerPacket.indexOf("super.write(ctx, rewritten, promise);");
        int promoteCurrentActions = playerPacket.indexOf("promoteCurrentActionPackets();");

        assertTrue("Actions prepared by the preceding silent tick must be written before its next C03",
                readyActionFlush >= 0 && rewrittenC03Write > readyActionFlush);
        assertTrue("Current-tick actions must wait for the following C03 after the silent rotation is written",
                promoteCurrentActions > rewrittenC03Write);
    }

    @Test
    public void doesNotReleaseASilentActionBatchForAnExtraPlayerPacketInTheSameTick() throws IOException {
        String playerPacket = playerPacketWriteMethod(source());
        int tickAdvance = playerPacket.indexOf("boolean playerTickAdvanced = playerPacketTickGate.consumeNextPlayerPacket();");
        int readyActionFlush = playerPacket.indexOf("flushReadyActionPackets(ctx);");

        assertTrue("A queued silent action batch must wait for a new player tick, not merely any C03 packet",
                tickAdvance >= 0 && readyActionFlush > tickAdvance);
    }

    @Test
    public void marksOnlyTheNativePlayerTickBeforeMovementProcessingInsteadOfEveryInputRefresh()
            throws IOException {
        String bridge = source();
        String preDispatch = method(bridge,
                "    private void dispatchPreUpdateBeforePlayerPacket() {",
                "    private void markNextPlayerPacketTick() {");
        String input = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/MovementInputBridge.java")), StandardCharsets.UTF_8);
        int dispatchPre = preDispatch.indexOf("dispatchPreUpdate();");
        int markPlayerPacket = preDispatch.indexOf("markNextPlayerPacketTick();");

        assertTrue("The one tick marker must be emitted immediately after the one PRE dispatch for that tick",
                dispatchPre >= 0 && markPlayerPacket > dispatchPre);
        assertFalse("A generic movement-input callback also runs for bridge-side input resets and cannot mark C03",
                bridge.contains("MovementInputBridge.setBeforePlayerPacketHook("));
        assertFalse("MovementInputBridge must remain input-only and must not own packet-tick callbacks",
                input.contains("beforePlayerPacketHook") || input.contains("setBeforePlayerPacketHook("));
    }

    @Test
    public void batchesEveryGrimPostSensitiveActionPacketBeforeAPlayerPacket() throws IOException {
        String source = source();
        String actionClassifier = method(source,
                "        private boolean isPostSensitiveAction(Packet<?> packet) {",
                "        private void queueCurrentActionPacket(");

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
        assertTrue("Every Post-sensitive packet must enter the current tick batch before it can reach Netty",
                packetBridgeWriteMethod(source).contains("if (isPostSensitiveAction(packet))")
                        && packetBridgeWriteMethod(source).contains(
                                "queueCurrentActionPacket(packet, promise, packetPendingPost);"));
        assertTrue("Closing an inventory must follow an already queued click-window packet without batching alone",
                packetBridgeWriteMethod(source).contains("packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow")
                        && source.contains("currentClickWindowPackets > 0")
                        && source.contains("readyClickWindowPackets > 0"));
        assertFalse("An isolated close-window packet must keep its native order with unrelated packets",
                actionClassifier.contains("C0DPacketCloseWindow"));
    }

    private static String packetBridgeWriteMethod(String source) {
        int begin = source.indexOf("        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        int finish = source.indexOf("        @Override", begin + 1);
        return source.substring(begin, finish);
    }

    private static String playerPacketWriteMethod(String source) {
        return method(source,
                "        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet,",
                "        private C03PacketPlayer rewritePlayerPacket(");
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
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);
    }
}
