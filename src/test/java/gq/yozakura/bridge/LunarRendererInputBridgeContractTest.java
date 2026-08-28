package gq.yozakura.bridge;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class LunarRendererInputBridgeContractTest {
    @Test
    public void nameTagSpecialsEventsAreRemappedAndDispatched() throws IOException {
        String remapper = source("src/main/java/gq/yozakura/bridge/VanillaRemapClassLoader.java");
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");

        assertTrue("The standalone loader must remap Forge's nested name-tag event family",
                remapper.contains("RenderLivingEvent$Specials")
                        && remapper.contains("RenderLivingEvent$Specials$Pre")
                        && remapper.contains("RenderLivingEvent$Specials$Post"));
        assertTrue("Standalone living renderers must dispatch the cancellable name-tag pre event",
                renderer.contains("dispatchSpecialsPre") && renderer.contains("Specials.Pre"));
        assertTrue("A cancelled name-tag event must suppress the delegate renderer's vanilla label",
                renderer.contains("specialsCancelled") && renderer.contains("suppressNameTag"));
    }

    @Test
    public void reinjectionReplacesRatherThanNestsOldLoaderCs() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");
        String movement = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");

        assertTrue("Renderer wrappers from an old loader must be recognized by class name",
                renderer.contains("getClass().getName()") && renderer.contains("readDelegate(current)"));
        assertTrue("Movement input wrappers from an old loader must be unwrapped before takeover",
                movement.contains("unwrapMovementInput")
                        && movement.contains("getClass().getName()")
                        && movement.contains("readMovementDelegate"));
        assertTrue("The new movement hook must wrap the unwrapped vanilla delegate",
                movement.contains("new HookedMovementInput(delegate)"));
    }

    @Test
    public void playerRendererScopesVisualRotationToTheDelegateCall() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");

        int apply = renderer.indexOf("VisualRotationSnapshot.apply(entity)");
        int visualYaw = renderer.indexOf("rotationSnapshot.resolveEntityYaw(entityYaw, partialTicks)", apply);
        int render = renderer.indexOf("delegate.doRender(entity, x, y, z, visualEntityYaw, partialTicks)", apply);
        int restore = renderer.indexOf("rotationSnapshot.restore(entity)", render);
        assertTrue("Lunar's player renderer must see one coherent temporary yaw/head/body rotation only while rendering",
                apply >= 0 && visualYaw > apply && render > visualYaw && restore > render);
    }

    @Test
    public void playerVisualSnapshotIncludesTheYawFieldsUsedByLunarRenderers() throws IOException {
        String renderer = source("src/main/java/gq/yozakura/bridge/StandaloneLivingRendererBridge.java");
        int snapshotBegin = renderer.indexOf("    private static final class VisualRotationSnapshot {");
        String snapshot = renderer.substring(snapshotBegin);

        assertTrue("The snapshot must save and restore the local player's camera-yaw pair",
                snapshot.contains("entity.prevRotationYaw") && snapshot.contains("entity.rotationYaw"));
        assertTrue("While the delegate renders, camera yaw must match the visual rotation rather than the silent camera",
                snapshot.contains("entity.prevRotationYaw = VisualRotationState.getPrevRotationYawHead();")
                        && snapshot.contains("entity.rotationYaw = VisualRotationState.getRotationYawHead();"));
        assertTrue("The render argument must interpolate the same temporary yaw pair",
                snapshot.contains("MathHelper.wrapAngleTo180_float"));
    }

    @Test
    public void standaloneTickDoesNotPersistVisualHeadOrBodyRotationOutsideTheRendererScope() throws IOException {
        String bridge = source("src/main/java/gq/yozakura/bridge/StandaloneEventBridge.java");
        int preBegin = bridge.indexOf("    private void dispatchPreUpdate() {");
        int preEnd = bridge.indexOf("    private void dispatchPreUpdateBeforePlayerPacket() {", preBegin);
        String pre = bridge.substring(preBegin, preEnd);

        assertFalse("The Lunar renderer owns temporary visual rotation; the tick bridge must not overwrite it persistently",
                pre.contains("syncVisibleRotation();"));
        assertFalse("A permanent head/body synchronization path races the renderer's snapshot restore",
                bridge.contains("private void syncVisibleRotation()"));
    }

    @Test
    public void movementCorrectionDoesNotSuppressVanillaSprintState() throws IOException {
        String movement = source("src/main/java/gq/yozakura/bridge/MovementInputBridge.java");

        assertFalse("Silent movement correction must leave the vanilla sprint key and state machine intact",
                movement.contains("shouldBlockSprintPacket")
                        || movement.contains("suppressSprintKey")
                        || movement.contains("setSprinting(false)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
