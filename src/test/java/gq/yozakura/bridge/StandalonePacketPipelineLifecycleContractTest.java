package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StandalonePacketPipelineLifecycleContractTest {
    @Test
    public void standaloneBridgeAnchorsEveryDelayHandlerInOneDeterministicOrder() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        String fakeLag = source("src/main/java/gq/yozakura/module/combat/FakeLag.java");
        String lagRange = source("src/main/java/gq/yozakura/module/combat/LagRange.java");
        String backtrack = source("src/main/java/gq/yozakura/module/combat/Backtrack.java");

        assertTrue("The standalone bridge must install through the shared named anchor",
                bridge.contains("PacketPipelineAnchors.installStandaloneBridge("));
        assertTrue("FakeLag must remain before either Yozakura bridge when it buffers outbound writes",
                fakeLag.contains("PacketPipelineAnchors.installDelayHandler("));
        assertTrue("LagRange must remain before either Yozakura bridge when it buffers outbound writes",
                lagRange.contains("PacketPipelineAnchors.installDelayHandler("));
        assertTrue("Backtrack must remain before the bridge so released inbound packets are observed once",
                backtrack.contains("PacketPipelineAnchors.installDelayHandler("));
        assertFalse("Delay handlers must not compete to insert directly before packet_handler",
                fakeLag.contains("addBefore(\"packet_handler\"")
                        || lagRange.contains("addBefore(\"packet_handler\"")
                        || backtrack.contains("addBefore(\"packet_handler\""));
    }

    @Test
    public void delayHandlersCanUseEitherForgeOrStandaloneBridgeAsTheirAnchor() throws IOException {
        String anchors = source("src/main/java/gq/yozakura/bridge/PacketPipelineAnchors.java");

        assertTrue("The helper must recognize Forge's bridge for modules shared with Forge",
                anchors.contains("yozakura_event_bridge"));
        assertTrue("The helper must recognize the standalone bridge",
                anchors.contains("yozakura_standalone_event_bridge"));
        assertTrue("Existing handlers must be normalized when the standalone bridge appears late",
                anchors.contains("normalizeDelayedHandlersBefore("));
    }

    @Test
    public void aClosedOrRemovedChannelStopsStandaloneTicksAndWritesBeforeTheNextWorldTick() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");

        assertTrue("Netty lifecycle callbacks must publish transport loss to the main bridge",
                bridge.contains("onPacketBridgeTerminated(ctx.channel());"));
        assertTrue("Tick dispatch must stop before world events when its channel has terminated",
                bridge.contains("if (stopForTerminatedPacketBridge())"));
        assertTrue("Writes in the disconnect window must fail instead of traversing the old pipeline",
                bridge.contains("if (packetBridgeTerminated || !ctx.channel().isActive())"));
        assertTrue("A channel loss must be published across the Netty/main-thread boundary",
                bridge.contains("private volatile boolean packetBridgeTerminated;"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
