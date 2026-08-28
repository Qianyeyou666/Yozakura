package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DroppedPacketBoundaryContractTest {
    @Test
    public void falseSuccessfulDropsCannotPublishCanonicalBoundaries() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java");
        String disposition = source("src/main/java/gq/yozakura/bridge/PacketWriteDisposition.java");
        String blink = source("src/main/java/gq/yozakura/manager/BlinkManager.java");
        String fakeLag = source("src/main/java/gq/yozakura/module/combat/FakeLag.java");
        String lagRange = source("src/main/java/gq/yozakura/module/combat/LagRange.java");
        String boundary = method(bridge,
                "    protected void reportPlayerPacketBoundary(ChannelPromise promise, final long writeId,",
                "    @Override\n    public void handlerRemoved");
        String writeObserver = method(bridge,
                "    protected void observePacketWrite(ChannelHandlerContext ctx, final Packet<?> packet,",
                "    protected void reportPacketWrite");

        assertTrue(disposition.contains("DROPPED_WRITES.put(promise, Boolean.TRUE);"));
        assertTrue(disposition.indexOf("DROPPED_WRITES.put(promise, Boolean.TRUE);")
                < disposition.indexOf("promise.trySuccess();"));
        assertTrue(boundary.contains("PacketWriteDisposition.isServerVisibleSuccess(future)"));
        assertFalse(boundary.contains("if (!future.isSuccess()"));
        assertTrue(writeObserver.contains("PacketWriteDisposition.isServerVisibleSuccess(future)"));

        assertTrue(bridge.contains("PacketWriteDisposition.completeDropped(promise);"));
        assertTrue(blink.contains("PacketWriteDisposition.completeDropped(packet.promise);"));
        assertTrue(fakeLag.contains("PacketWriteDisposition.completeDropped(queued.promise);"));
        assertTrue(lagRange.contains("PacketWriteDisposition.completeDropped(queued.promise);"));
        assertFalse("No packet-delay drop path may directly claim success anymore",
                blink.contains("packet.promise.trySuccess();")
                        || fakeLag.contains("queued.promise.trySuccess();")
                        || lagRange.contains("queued.promise.trySuccess();"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String method(String source, String beginNeedle, String endNeedle) {
        int begin = source.indexOf(beginNeedle);
        int end = source.indexOf(endNeedle, begin + beginNeedle.length());
        return source.substring(begin, end);
    }
}
