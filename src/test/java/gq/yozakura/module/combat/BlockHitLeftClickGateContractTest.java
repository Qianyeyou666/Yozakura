package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** All BlockHit modes are active only while the physical attack binding is down. */
public class BlockHitLeftClickGateContractTest {
    @Test
    public void preUpdateStopsEveryModeWhenLeftClickIsReleased() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String update = methodBody(source, "    public void onUpdate(UpdateEvent event) {");

        assertTrue("PRE must have one shared physical left-click gate",
                update.contains("if (!isPhysicalAttackDown())"));
        assertTrue("The shared gate must stop Helper-owned use input",
                update.contains("stopHelper(true)"));
        assertTrue("The shared gate must cancel packet-driven block cycles",
                update.contains("cancelCycle()"));
        assertTrue("The shared gate must discard attacks accepted before release",
                update.contains("discardPendingPackets()"));
    }

    @Test
    public void packetDrivenModesCannotArmWithoutPhysicalLeftClick() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String arm = methodBody(source, "    private void armUseForAttack(C02PacketUseEntity packet) {");

        assertTrue("Manual, Predict, Auto and Lag share the same physical input guard",
                arm.contains("!isPhysicalAttackDown()"));
    }

    @Test
    public void postUpdateCannotStartOrKeepUseAfterLeftClickRelease() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String post = methodBody(source, "    private void finishPostInputCycle() {");

        assertTrue("POST must cancel a pending cycle after physical left-click release",
                post.contains("!isPhysicalAttackDown()"));
        assertTrue("POST use start must still require physical left-click",
                post.contains("isPhysicalAttackDown() && controller.consumeUseRequest()"));
    }

    @Test
    public void helperAndRenderPoseAlsoRequireLeftClick() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String helper = methodBody(source, "    private void handleHelperTick(boolean activationAllowed) {");
        String ready = methodBody(source, "    private boolean isHelperReady() {");
        String blocking = methodBody(source, "    public static boolean isBlockingActive() {");

        assertTrue("Threat prediction cannot bypass the left-click requirement",
                helper.contains("!isPhysicalAttackDown()"));
        assertTrue("Forced render pose must end immediately after left-click release",
                ready.contains("isPhysicalAttackDown()"));
        assertTrue("The public blocking state must not report active without left click",
                blocking.contains("isPhysicalAttackDown()"));
    }

    @Test
    public void obsoletePerModeMouseOptionIsRemoved() throws IOException {
        String settings = source("src/main/java/gq/yozakura/module/combat/BlockHitSettings.java");
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");

        assertFalse("Left click is now a mandatory invariant, not an optional mode setting",
                settings.contains("Require Mouse Down"));
        assertFalse("Target matching must not depend on an obsolete configurable gate",
                blockHit.contains("settings.requireMouseDown"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
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
