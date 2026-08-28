package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;

public class VelocityReducePacketCompatibilityContractTest {
    @Test
    public void reduceDoesNotRewriteAcceptedLocalS12Motion() throws IOException {
        String source = source();
        String packetHandler = between(source,
                "    public void onPacket(PacketEvent event) {",
                "    @EventTarget(Priority.LOWEST)");

        assertFalse("Reduce must leave accepted S12 motion untouched so client and server simulate the same knockback",
                packetHandler.contains("scaleVelocityPacket("));
    }

    @Test
    public void modernBridgeDoesNotRouteVelocityThroughIncomingPacketMutation() throws IOException {
        String source = modernSource();
        String incomingHandler = methodBody(source,
                "    private static boolean onIncoming(Object packet, ChannelHandlerContext ctx) {");

        assertFalse("The modern bridge must not route Velocity through an inbound packet-rewrite handler",
                incomingHandler.contains("handleVelocity(packet, player)"));
        assertFalse("The modern bridge must not invoke packet-motion scaling for Velocity",
                source.contains("scaleMotionPacket(packet,"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/module/combat/Velocity.java")), StandardCharsets.UTF_8);
    }

    private static String modernSource() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/modern/ModernPacketBridge.java")), StandardCharsets.UTF_8);
    }

    private static String between(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        return source.substring(begin, end);
    }

    private static String methodBody(String source, String beginMarker) {
        int methodStart = source.indexOf(beginMarker);
        if (methodStart < 0) {
            throw new AssertionError("Missing method: " + beginMarker);
        }
        int bodyStart = source.indexOf('{', methodStart);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(methodStart, index + 1);
            }
        }
        throw new AssertionError("Unclosed method body: " + beginMarker);
    }
}
