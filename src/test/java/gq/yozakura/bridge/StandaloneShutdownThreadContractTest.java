package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void tickSchedulingFailureNeverRunsTheBridgeFromThePumpThread() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        int begin = client.indexOf("    private void queueTick() {");
        int end = client.indexOf("    private void runTick() {", begin);

        assertTrue("queueTick must remain a separately inspectable thread boundary", begin >= 0 && end > begin);
        String queueTick = client.substring(begin, end);
        int catchStart = queueTick.indexOf("        } catch (Throwable throwable) {");
        int catchEnd = queueTick.indexOf("        }\n", catchStart + 1);
        assertTrue("queueTick must handle scheduling failure explicitly", catchStart >= 0 && catchEnd > catchStart);
        String schedulingFailure = queueTick.substring(catchStart, catchEnd);
        assertFalse("A failed addScheduledTask must stop the standalone bridge rather than run it on the daemon pump",
                schedulingFailure.contains("runTick();"));
        assertTrue("The scheduling failure must be surfaced and stop both pump liveness flags",
                queueTick.contains("stopForTickSchedulingFailure(throwable);")
                        && client.contains("private void stopForTickSchedulingFailure(Throwable throwable)"));
    }

    @Test
    public void mainThreadTickFailureStopsAndTearsDownTheBridgeInsteadOfRetrying() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        int begin = client.indexOf("    private void runTick() {");
        int end = client.indexOf("    private void handleKeys() {", begin);

        assertTrue("runTick must remain a separately inspectable client-thread boundary", begin >= 0 && end > begin);
        String runTick = client.substring(begin, end);
        assertTrue("A runtime bridge failure must be terminal rather than retried by the pump",
                runTick.contains("stopForTickFailure(throwable);"));
        assertTrue("A terminal tick failure must restore hooks while still on the Minecraft thread",
                client.contains("private void stopForTickFailure(Throwable throwable)")
                        && client.contains("bridge.shutdown();"));
    }

    @Test
    public void unschedulableMinecraftThreadWorkFailsClosedInsteadOfLeavingHooksForReinjection() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");

        assertTrue("A scheduler failure must become a cross-loader terminal marker",
                client.contains("FAILED_INSTANCE_PROPERTY")
                        && client.contains("System.setProperty(FAILED_INSTANCE_PROPERTY, instanceId);"));
        assertTrue("A later injection must reject an unsafe process that still owns standalone hooks",
                client.contains("throwIfPreviousStandaloneBridgeFailed();"));
        assertTrue("The pump must not retry addScheduledTask after the scheduling path already failed",
                client.contains("if (!terminalBridgeFailure)")
                        && client.contains("if (!shutdownCompleted)"));
        assertTrue("A teardown error on the Minecraft thread must use the same fail-closed record",
                client.contains("recordTerminalBridgeFailure(\"Standalone bridge emergency teardown failed\""));
        int shutdownBegin = client.indexOf("    public static void shutdownForReinjection() {");
        int shutdownEnd = client.indexOf("    public static boolean isShutdownComplete()", shutdownBegin);
        assertTrue("Direct Minecraft-thread reinjection teardown must also record an unrecoverable cleanup failure",
                shutdownBegin >= 0 && shutdownEnd > shutdownBegin
                        && client.substring(shutdownBegin, shutdownEnd)
                        .contains("recordTerminalBridgeFailure(\"Standalone direct teardown failed\""));
    }

    @Test
    public void uninjectAndSameLoaderReinjectionWaitForThePriorBridgeToStop() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        int uninjectBegin = client.indexOf("    public static void unInject() {");
        int uninjectEnd = client.indexOf("    public static void shutdownForReinjection() {", uninjectBegin);
        int requestBegin = client.indexOf("    private void requestStandaloneShutdown(ClassLoader loader) {");
        int requestEnd = client.indexOf("    private boolean isStandaloneShutdownComplete", requestBegin);

        assertTrue("unInject must begin the same owned teardown path instead of only clearing a liveness flag",
                uninjectBegin >= 0 && uninjectEnd > uninjectBegin
                        && client.substring(uninjectBegin, uninjectEnd).contains("shutdownForReinjection();"));
        assertTrue("A same-loader pump must be asked to shut down and joined before another instance can take ownership",
                requestBegin >= 0 && requestEnd > requestBegin
                        && client.substring(requestBegin, requestEnd)
                        .contains("if (loader == StandaloneClient.class.getClassLoader())")
                        && client.substring(requestBegin, requestEnd).contains("shutdownForReinjection();"));
    }

    @Test
    public void reinjectionRejectsAnOrphanedBridgeOwnerBeforeInstallingAnotherPacketHook() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        int constructor = client.indexOf("    public StandaloneClient() {");
        int stopExisting = client.indexOf("stopExistingStandalonePumps();", constructor);
        int ownerGuard = client.indexOf("throwIfPreviousStandaloneBridgeIsStillActive();", stopExisting);
        int auth = client.indexOf("YozakuraAuthGate.verifyOrThrow(\"standalone\");", ownerGuard);

        assertTrue("A stale process-wide owner must abort reinjection instead of adding a second bridge",
                constructor >= 0 && stopExisting > constructor && ownerGuard > stopExisting && auth > ownerGuard);
        assertTrue("The owner guard must consult the JVM-wide active bridge marker",
                client.contains("private static void throwIfPreviousStandaloneBridgeIsStillActive()")
                        && client.contains("if (isBridgeOwnerActive())"));
    }

    @Test
    public void initializationRollbackKeepsOwnershipUntilTheStartedPumpHasTornDown() throws IOException {
        String client = source("src/main/java/gq/yozakura/core/StandaloneClient.java");
        int rollbackBegin = client.indexOf("    private void rollbackFailedInitialization() {");
        int rollbackEnd = client.indexOf("    private void clearActiveInstanceIfOwner() {", rollbackBegin);

        assertTrue("Rollback must distinguish work that has not started a pump from a live bridge that still owns hooks",
                rollbackBegin >= 0 && rollbackEnd > rollbackBegin
                        && client.substring(rollbackBegin, rollbackEnd).contains("if (!pumpStarted)"));
        assertTrue("A rollback already on the Minecraft thread must tear down synchronously before releasing ownership",
                client.substring(rollbackBegin, rollbackEnd).contains("mc.isCallingFromMinecraftThread()")
                        && client.substring(rollbackBegin, rollbackEnd).contains("bridge.shutdown();"));
        assertTrue("An unsuccessful synchronous rollback must preserve a process-wide terminal failure marker",
                client.substring(rollbackBegin, rollbackEnd)
                        .contains("recordTerminalBridgeFailure(\"Standalone initialization rollback failed\""));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
