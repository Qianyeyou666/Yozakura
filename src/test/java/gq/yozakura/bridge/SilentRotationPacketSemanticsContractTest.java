package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SilentRotationPacketSemanticsContractTest {
    @Test
    public void duplicateSilentRotationUsesVanillaMovementPacketSemantics() throws IOException {
        String source = source();
        String rewrite = method(source,
                "    protected C03PacketPlayer rewritePlayerPacket(",
                "    protected boolean isPostSensitiveAction(");

        assertFalse("Duplicate look must not be hidden by changing the player's yaw",
                source.contains("ROTATION_DEDUPE_STEP")
                        || source.contains("nudgeDuplicateYaw")
                        || source.contains("duplicateYawFlip"));
        assertTrue("A changed silent rotation still needs C05/C06 look publication",
                rewrite.contains("boolean sendLook = shouldSendLook(yaw, pitch);")
                        && rewrite.contains("if (sendLook) {"));
        assertTrue("An unchanged silent rotation must preserve movement without resending look",
                rewrite.contains("new C03PacketPlayer.C04PacketPlayerPosition(")
                        && rewrite.contains("new C03PacketPlayer(packet.isOnGround())"));
        assertTrue("Moving packets must preserve native coordinates and onGround",
                rewrite.contains("packet.getPositionX()")
                        && rewrite.contains("packet.getPositionY()")
                        && rewrite.contains("packet.getPositionZ()")
                        && rewrite.contains("packet.isOnGround()"));
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method markers", begin >= 0 && end > begin);
        return source.substring(begin, end);
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/bridge/BasePacketBridgeHandler.java")),
                StandardCharsets.UTF_8);
    }
}
