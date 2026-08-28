package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class StandalonePumpPerformanceContractTest {
    private static final String PATH = "src/main/java/gq/yozakura/core/StandaloneClient.java";

    @Test
    public void pumpMatchesMinecraftTickCadenceAndSkipsExpensiveDuplicateWork() throws IOException {
        String source = read();
        String pump = method(source, "private void startMainThreadPump()", "private void stopExistingStandalonePumps()");
        String tick = method(source, "private void runTick()", "private void stopForTickSchedulingFailure");

        assertTrue("the standalone pump must not enqueue work at 100 Hz for a 20 TPS game loop",
                pump.contains("sleep(50L);"));
        int duplicateReturn = tick.indexOf("if (!playerTick) {");
        int auth = tick.indexOf("tokenAuthBridge.tick();");
        int keys = tick.indexOf("handleKeys();");
        int mouse = tick.indexOf("handleMouseButtons();");
        int bridge = tick.indexOf("bridge.tick(true);");

        assertTrue("duplicate callbacks must take an explicit lightweight path", duplicateReturn >= 0);
        assertTrue("authentication checks must execute only after the duplicate-tick guard", auth > duplicateReturn);
        assertTrue("256-key polling must execute only after the duplicate-tick guard", keys > duplicateReturn);
        assertTrue("mouse polling must execute only after the duplicate-tick guard", mouse > duplicateReturn);
        assertTrue("the full bridge tick must execute only after the duplicate-tick guard", bridge > duplicateReturn);
        assertTrue("the duplicate path must preserve pending POST dispatch through the bridge", 
                tick.indexOf("bridge.tick(false);", duplicateReturn) > duplicateReturn);
    }

    private static String read() throws IOException {
        return new String(Files.readAllBytes(Paths.get(PATH)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("missing method " + startMarker, start >= 0 && end > start);
        return source.substring(start, end);
    }
}
