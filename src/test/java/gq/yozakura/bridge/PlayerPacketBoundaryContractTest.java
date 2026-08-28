package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Ensures one successful, accepted movement boundary is published per client tick. */
public class PlayerPacketBoundaryContractTest {
    @Test
    public void forgeAndStandalonePublishOnlyTheTickGateBoundary() throws IOException {
        assertCanonicalBoundaryPublication(combinedForgeSource(), forgeSource());
        assertCanonicalBoundaryPublication(combinedStandaloneSource(), standaloneSource());
    }

    @Test
    public void projectGeneratedPlayerPacketsAreMarkedBeforeTheyReachTheBridge() throws IOException {
        String criticals = source("src/main/java/gq/yozakura/module/combat/Criticals.java");
        String serverFucker = source("src/main/java/gq/yozakura/module/world/FuckServer.java");

        assertTrue(criticals.contains("PacketBridgeSupport.markNonCanonicalPlayerPacket(first)"));
        assertTrue(criticals.contains("PacketBridgeSupport.markNonCanonicalPlayerPacket(second)"));
        assertTrue(serverFucker.contains("PacketBridgeSupport.markNonCanonicalPlayerPacket(packet)"));
    }

    private static void assertCanonicalBoundaryPublication(String combined, String bridgeSource) {
        assertTrue(combined.contains("import gq.yozakura.event.bridge.PlayerPacketBoundaryEvent;")
                || combined.contains("gq.yozakura.event.bridge.PlayerPacketBoundaryEvent"));
        assertTrue(combined.contains(
                "PacketBridgeSupport.consumeNonCanonicalPlayerPacket(packet)"));
        assertTrue(combined.contains(
                "boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket("));
        assertTrue(combined.contains(
                "reportPlayerPacketBoundary(promise, writeId, playerTickAdvanced,"));
        assertTrue(combined.contains("PacketWriteDisposition.isServerVisibleSuccess(future)"));
        assertTrue(combined.contains("writeId == PacketAcceptedEvent.NO_WRITE_ID"));
        assertTrue(combined.contains(
                "new PlayerPacketBoundaryEvent(writeId, serverYaw, serverPitch, rotated)"));
        assertTrue(combined.contains("boolean boundaryRotated = isRotationActive(snapshot);"));
    }

    private static String findWritePlayerPacketMethod(String combined, String bridgeSource) {
        int begin = bridgeSource.indexOf(
                "        protected void writePlayerPacketInternal(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        if (begin < 0) {
            begin = bridgeSource.indexOf(
                    "        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        }
        int end = bridgeSource.indexOf(
                "        protected void logActionQueue(", begin);
        if (end < 0) {
            end = bridgeSource.indexOf(
                    "        private C03PacketPlayer rewritePlayerPacket(", begin);
        }
        if (begin >= 0 && end > begin) {
            return bridgeSource.substring(begin, end);
        }
        begin = combined.indexOf(
                "    protected void writePlayerPacketCommon(ChannelHandlerContext ctx, C03PacketPlayer packet,");
        end = combined.indexOf(
                "    void markNextPlayerPacketTick(", begin);
        if (begin >= 0 && end > begin) {
            return combined.substring(begin, end);
        }
        return combined;
    }

    private static String findReportPlayerPacketBoundaryMethod(String combined) {
        int begin = combined.indexOf(
                "    protected void reportPlayerPacketBoundary(ChannelPromise promise, final long writeId,");
        if (begin < 0) {
            begin = combined.indexOf(
                    "        private void reportPlayerPacketBoundary(ChannelPromise promise, final long writeId,");
        }
        if (begin < 0) {
            return combined;
        }
        int end = combined.indexOf(
                "    public void handlerRemoved(", begin);
        if (end < 0) {
            end = combined.indexOf(
                    "    protected void observePacketWrite(", begin);
        }
        if (end < 0) {
            end = combined.indexOf(
                    "    @Override", begin + 1);
        }
        if (end < 0) {
            end = combined.indexOf(
                    "        @Override", begin + 1);
        }
        if (begin >= 0 && end > begin) {
            return combined.substring(begin, end);
        }
        return combined.substring(begin, Math.min(begin + 500, combined.length()));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String baseSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String forgeSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String standaloneSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String combinedForgeSource() throws IOException {
        return baseSource() + "\n" + forgeSource();
    }

    private static String combinedStandaloneSource() throws IOException {
        return baseSource() + "\n" + standaloneSource();
    }
}
