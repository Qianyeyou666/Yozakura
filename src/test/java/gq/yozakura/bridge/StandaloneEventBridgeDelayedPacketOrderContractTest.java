package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class StandaloneEventBridgeDelayedPacketOrderContractTest {
    @Test
    public void writesSilentRotationC03BeforeFlushingDelayedCombatPackets() throws IOException {
        String write = packetBridgeWriteMethod(source());
        int rewrittenC03Write = write.indexOf("super.write(ctx, rewritten, promise);");
        int delayedPacketFlush = write.indexOf("flushDelayedPackets(ctx);");

        assertTrue("The silent C03 path must still write a rewritten player packet", rewrittenC03Write >= 0);
        assertTrue("Delayed combat packets must be flushed by the packet bridge", delayedPacketFlush >= 0);
        assertTrue("A delayed C02/C07/C08/C0A must leave only after its silent C03 reaches the pipeline",
                rewrittenC03Write < delayedPacketFlush);
    }

    @Test
    public void buffersSensitiveActionsWhileThePreRotationOutcomeIsStillUnknown() throws IOException {
        String source = source();
        int begin = source.indexOf("        private boolean shouldDelayUntilRotation(Packet<?> packet) {");
        int finish = source.indexOf("        private boolean isRotationSensitiveAction", begin);
        String gate = source.substring(begin, finish);

        assertTrue("Only combat actions should be buffered during PRE dispatch",
                gate.contains("if (!isRotationSensitiveAction(packet))"));
        assertTrue("A C02 emitted before a later rotation listener runs must wait for the final PRE outcome",
                gate.contains("if (activePreUpdate != null)"));
    }

    private static String packetBridgeWriteMethod(String source) {
        int begin = source.indexOf("        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {");
        int finish = source.indexOf("        @Override", begin + 1);
        return source.substring(begin, finish);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java")), StandardCharsets.UTF_8);
    }
}
