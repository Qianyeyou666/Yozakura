package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class StandalonePumpCadenceContractTest {
    @Test
    public void duplicatePumpTicksSkipHookAndPacketPipelineMaintenance() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        int begin = bridge.indexOf("    public void tick(boolean playerTick) {");
        int end = bridge.indexOf("    public void shutdown() {", begin);

        assertTrue("tick must remain an inspectable main-thread boundary", begin >= 0 && end > begin);
        String tick = bridge.substring(begin, end);
        int duplicateTickGuard = tick.indexOf("if (!playerTick) {");
        int installInput = tick.indexOf("MovementInputBridge.install();");
        int installRenderer = tick.indexOf("StandaloneEntityRenderer.install(mc);");
        int injectPacketHandler = tick.indexOf("injectPacketHandler();");

        assertTrue("Duplicate 100 Hz pump ticks must flush pending POST state before returning",
                duplicateTickGuard >= 0
                        && tick.indexOf("dispatchPendingPostUpdate();", duplicateTickGuard) > duplicateTickGuard);
        assertTrue("Movement input wrapping must run only when the Minecraft player tick advances",
                installInput > duplicateTickGuard);
        assertTrue("Renderer hook maintenance must run only when the Minecraft player tick advances",
                installRenderer > duplicateTickGuard);
        assertTrue("Netty pipeline maintenance must run only when the Minecraft player tick advances",
                injectPacketHandler > duplicateTickGuard);
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
