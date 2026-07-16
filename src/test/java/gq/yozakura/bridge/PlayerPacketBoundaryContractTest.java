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
        assertCanonicalBoundaryPublication(source("src/main/java/gq/yozakura/bridge/YozakuraEventBridge.java"));
        assertCanonicalBoundaryPublication(source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java"));
    }

    @Test
    public void projectGeneratedPlayerPacketsAreMarkedBeforeTheyReachTheBridge() throws IOException {
        String criticals = source("src/main/java/gq/yozakura/module/combat/Criticals.java");
        String serverFucker = source("src/main/java/gq/yozakura/module/world/FuckServer.java");

        assertTrue(criticals.contains("PacketBridgeSupport.markNonCanonicalPlayerPacket(first)"));
        assertTrue(criticals.contains("PacketBridgeSupport.markNonCanonicalPlayerPacket(second)"));
        assertTrue(serverFucker.contains("PacketBridgeSupport.markNonCanonicalPlayerPacket(packet)"));
    }

    private static void assertCanonicalBoundaryPublication(String source) {
        String playerPacket = method(source,
                "        private void writePlayerPacket(ChannelHandlerContext ctx, C03PacketPlayer packet,",
                "        private C03PacketPlayer rewritePlayerPacket(");
        String reporter = method(source,
                "        private void reportPlayerPacketBoundary(ChannelPromise promise, final long writeId,",
                "        @Override\n        public void handlerRemoved");

        assertTrue(source.contains("import gq.yozakura.event.bridge.PlayerPacketBoundaryEvent;"));
        assertTrue(source.contains(
                "PacketBridgeSupport.consumeNonCanonicalPlayerPacket(packet)"));
        assertTrue(playerPacket.contains(
                "boolean playerTickAdvanced = playerPacketTickGate.consumeNextCanonicalPlayerPacket("));
        assertTrue(playerPacket.contains(
                "reportPlayerPacketBoundary(promise, writeId, playerTickAdvanced);"));
        assertTrue(reporter.contains("future.isSuccess()"));
        assertTrue(reporter.contains("writeId == PacketAcceptedEvent.NO_WRITE_ID"));
        assertTrue(reporter.contains("new PlayerPacketBoundaryEvent(writeId)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }
}
