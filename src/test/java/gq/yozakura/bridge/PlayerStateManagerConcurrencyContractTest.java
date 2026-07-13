package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerStateManagerConcurrencyContractTest {
    @Test
    public void nettyPublishesOneAtomicActionStateToTheClientThread() throws IOException {
        String source = source("src/main/java/gq/yozakura/manager/PlayerStateManager.java");

        assertTrue("Packet callbacks and module reads need a linearizable state publication",
                source.contains("AtomicInteger stateMask"));
        assertTrue("Consumers must read the published mask through accessors",
                source.contains("boolean isDigging()")
                        && source.contains("boolean isPlacing()"));
        assertFalse("Independent public booleans can expose a mixed packet state",
                source.contains("public boolean attacking")
                        || source.contains("public boolean digging")
                        || source.contains("public boolean placing")
                        || source.contains("public boolean swapping")
                        || source.contains("public boolean swinging"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
