package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class StandaloneShutdownThreadContractTest {
    @Test
    public void bridgeTeardownRunsOnTheMinecraftThreadBeforeThePumpExits() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");

        assertTrue("The pump must hand Minecraft state cleanup back to the client thread",
                client.contains("shutdownBridgeOnMainThread();")
                        && client.contains("mc.addScheduledTask(new Runnable()"));
        assertTrue("Reinjection must wait until teardown has actually completed",
                client.contains("CountDownLatch") && client.contains("shutdownComplete.await("));
        assertTrue("A failed cross-loader shutdown request must abort reinjection explicitly",
                client.contains("Unable to request shutdown from the previous standalone loader"));
        assertTrue("Reinjection from the Minecraft thread must tear down directly instead of joining a queued task",
                client.contains("shutdownForReinjection")
                        && client.contains("mc.isCallingFromMinecraftThread()")
                        && client.contains("if (!shutdownCompleted)"));
        assertTrue("The pump join budget must exceed the client-thread teardown budget",
                client.contains("BRIDGE_SHUTDOWN_TIMEOUT_MS")
                        && client.contains("PREVIOUS_PUMP_JOIN_TIMEOUT_MS"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
