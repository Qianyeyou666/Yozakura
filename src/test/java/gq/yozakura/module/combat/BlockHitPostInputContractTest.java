package gq.yozakura.module.combat;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The automatic sword-use transition must occur after the current input
 * phase, so a release cannot share that phase with a left-click attack.
 */
public class BlockHitPostInputContractTest {
    @Test
    public void blockHitAppliesUseAndReleaseOnlyFromThePostUpdatePhase() throws IOException {
        String source = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String update = method(source, "public void onUpdate(UpdateEvent event)",
                "public void onPacketAccepted(PacketAcceptedEvent event)");
        String postActions = method(source, "private void finishPostInputCycle()",
                "private boolean hasExternalUseOwner()");

        int post = update.indexOf("event.getType() == EventType.POST");
        int release = postActions.indexOf("controller.consumeReleaseRequest()");
        int use = postActions.indexOf("controller.consumeUseRequest()");

        assertTrue("BlockHit must have an explicit post-input action phase", post >= 0);
        assertTrue("The post-input phase must own release", release >= 0);
        assertTrue("The post-input phase must own re-block", use >= 0);
        assertTrue(update.contains("finishPostInputCycle();"));
        assertFalse("PRE must not mutate a virtual use binding", update.contains("KeyBinding.setKeyBindState"));
    }

    @Test
    public void blockHitUsesPlayerControllersVanillaSwordActionsWithoutRawPackets() throws IOException {
        String action = source("src/main/java/gq/yozakura/module/combat/BlockHitVanillaUseAction.java");

        assertTrue(action.contains("mc.playerController.sendUseItem("));
        assertTrue(action.contains("mc.playerController.onStoppedUsingItem("));
        assertFalse(action.contains("KeyBinding.setKeyBindState"));
        assertFalse(action.contains("new C07PacketPlayerDigging"));
        assertFalse(action.contains("new C08PacketPlayerBlockPlacement"));
    }

    @Test
    public void releaseWaitsForTheOwnedUseWriteAndCancellationDoesNotReleaseEarly() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String controller = source("src/main/java/gq/yozakura/module/combat/BlockHitController.java");
        String useAction = source("src/main/java/gq/yozakura/module/combat/BlockHitVanillaUseAction.java");
        String cancel = method(blockHit, "private void cancelCycle()", "private void resetState()");

        assertTrue(blockHit.contains("event.getWriteId()"));
        assertTrue(blockHit.contains("controller.confirmUseWritten(write.cycleId)"));
        assertTrue(blockHit.contains("useAction.completeOwnedUseWrite(event.getWriteId(), event.isSuccess())"));
        assertTrue(blockHit.contains("attackAcceptedInCurrentInputWindow"));
        assertTrue(useAction.contains("claimOwnedUseWrite(long writeId)"));
        assertTrue(useAction.contains("cycleForOwnedUseWrite(long writeId)"));
        assertTrue(useAction.contains("ownedUseWriteSucceeded"));
        assertTrue(controller.contains("requestedUseActive && useWriteConfirmed"));
        assertFalse(cancel.contains("useAction.releaseUse()"));
    }

    @Test
    public void disablingBlockHitClosesItsOwnedVanillaUseCycleBeforeItUnregisters() throws IOException {
        String blockHit = source("src/main/java/gq/yozakura/module/combat/BlockHit.java");
        String disabledRelease = source("src/main/java/gq/yozakura/module/combat/BlockHitDisabledUseRelease.java");
        String disabled = method(blockHit, "public void onDisabled()", "@EventTarget(Priority.HIGHEST)");

        assertTrue(disabled.contains("long cycleId = useAction.getActiveUseCycleId();"));
        assertTrue(disabled.contains("useAction.isUseWriteSucceeded(cycleId)"));
        assertTrue(disabled.contains("new BlockHitDisabledUseRelease"));
        assertTrue(disabledRelease.contains("private volatile boolean useWriteSucceeded;"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String method(String source, String beginMarker, String endMarker) {
        int begin = source.indexOf(beginMarker);
        int end = source.indexOf(endMarker, begin + beginMarker.length());
        assertTrue("Expected method marker: " + beginMarker, begin >= 0);
        assertTrue("Expected end marker: " + endMarker, end > begin);
        return source.substring(begin, end);
    }
}
