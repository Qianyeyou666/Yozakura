package gq.yozakura.module.combat.aim;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AimAssistLunarBridgeContractTest {
    @Test
    public void lockOnUsesOnlyTheTwoStageStateAndPlayerGeometry() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");

        assertTrue(aimbot.contains("ADAPTIVE,"));
        assertTrue(aimbot.contains("LOCK_ON;"));
        assertTrue(aimbot.contains("public boolean isLockOn()"));
        assertTrue(aimbot.contains("AimAssistLockOnState lockOnState"));
        assertTrue(aimbot.contains("AimAssistLockOnGeometry.create("));
        assertTrue(aimbot.contains("target instanceof EntityPlayer"));
        assertFalse(aimbot.contains("isSilent()"));
        assertFalse(aimbot.contains("isBlatant()"));
        assertFalse(aimbot.contains("silentController"));
        assertFalse(aimbot.contains("KeepMoveDirection"));
        assertFalse("Lunar must be able to inspect every Aimbot method without Forge camera classes",
                aimbot.contains("EntityViewRenderEvent.CameraSetup"));
    }

    @Test
    public void lunarAndForgeResolveAfterMouseInputAtTheirFinalCameraBoundaries() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        String lunar = source("src/main/java/gq/yozakura/bridge/StandaloneEntityRenderer.java");
        String forge = source("src/main/java/gq/yozakura/module/combat/AimAssistForgeCameraBridge.java");

        assertTrue(aimbot.contains("onRenderTickStart(RenderTickStartEvent event)"));
        assertTrue(forge.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue("Forge's generated ASM handler must be able to access the listener class",
                forge.contains("public final class AimAssistForgeCameraBridge"));
        assertTrue("Legacy Forge camera hooks must use the client Forge event bus directly",
                forge.contains("MinecraftForge.EVENT_BUS.register(bridge)"));
        assertTrue("Forge must retain a render-tick fallback when CameraSetup is suppressed",
                forge.contains("TickEvent.RenderTickEvent")
                        && forge.contains("FMLCommonHandler.instance().bus().register(bridge)"));
        assertTrue(forge.contains("onCameraSetup(EntityViewRenderEvent.CameraSetup event)"));
        assertTrue(lunar.contains("EventManager.call(new RenderTickStartEvent(partialTicks, true));"));
        assertTrue(aimbot.contains("if (!event.isCameraInputComplete())"));
        assertTrue(lunar.indexOf("EventManager.call(new RenderTickStartEvent(partialTicks, true));")
                < lunar.indexOf("StandaloneLivingRendererBridge.install(minecraft);"));
        int lunarHandler = aimbot.indexOf("onRenderTickStart(RenderTickStartEvent event)");
        int lockOnBoundary = aimbot.indexOf("CameraDelta applyLockOnBoundary(float partialTicks)");
        String lunarPath = aimbot.substring(lunarHandler, lockOnBoundary);
        assertTrue("Lunar must drive Adaptive as well as Lock-on",
                lunarPath.contains("applyAdaptiveFrame(partialTicks);"));
    }

    @Test
    public void changedRotationKeepsCameraAndAttackRayOnTheSameAngles() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int method = aimbot.indexOf("private CameraDelta applyCameraRotation(");
        int end = aimbot.indexOf("    private void handleModeChange()", method);
        String apply = aimbot.substring(method, end);

        assertTrue(apply.contains("mc.thePlayer.rotationYaw += yawDelta;"));
        assertTrue(apply.contains("mc.thePlayer.prevRotationYaw += yawDelta;"));
        assertTrue(apply.contains("mc.thePlayer.prevRotationPitch"));
        int mouseOver = apply.indexOf("mc.entityRenderer.getMouseOver(partialTicks);");
        int reach = apply.indexOf("Reach.applyRuntimeMouseOverOverride(partialTicks);");
        assertTrue(mouseOver >= 0 && reach > mouseOver);

        String forge = source("src/main/java/gq/yozakura/module/combat/AimAssistForgeCameraBridge.java");
        assertTrue(forge.contains("event.yaw += delta.getYaw();"));
        assertTrue(forge.contains("event.pitch += delta.getPitch();"));
    }

    @Test
    public void adaptiveOnlyControlsExposeSpeedDelayAndVerticalAim() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");

        assertTrue(aimbot.contains("speed.visibleWhen(() -> !mode.getValue().isLockOn())"));
        assertTrue(aimbot.contains("verticalSpeed.visibleWhen(() -> !mode.getValue().isLockOn())"));
        assertTrue(aimbot.contains("reactionDelay.visibleWhen(() -> !mode.getValue().isLockOn())"));
        assertTrue(aimbot.contains("aimVertical.visibleWhen(() -> !mode.getValue().isLockOn())"));
    }

    @Test
    public void selectorRestrictsLockOnToPlayersAndKeepsAdaptiveTypes() throws IOException {
        String selector = source("src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java");

        assertTrue(selector.contains("LOCK_ON_HEAD"));
        assertTrue(selector.contains("return target instanceof EntityPlayer && settings.targetPlayers;"));
        assertTrue(selector.contains("target instanceof EntityAnimal"));
        assertTrue(selector.contains("findLockOnHeadPoint("));
        assertTrue(selector.contains("isAimPointAllowed(minecraft, preferred"));
    }

    @Test
    public void lifecycleResetClearsLockAndKnockbackState() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int clear = aimbot.indexOf("private void clearTargetState()");
        int end = aimbot.indexOf("    private boolean conditionsMet()", clear);
        String method = aimbot.substring(clear, end);

        assertTrue(method.contains("targetSelector.clear();"));
        assertTrue(method.contains("viewController.releaseTarget();"));
        assertTrue(method.contains("lockOnState.reset();"));
        assertTrue(method.contains("knockbackWindow.reset();"));
    }

    @Test
    public void temporaryInputAndBoundaryFailuresDoNotRestartInitialAiming() throws IOException {
        String aimbot = source("src/main/java/gq/yozakura/module/combat/Aimbot.java");
        int refresh = aimbot.indexOf("private boolean refreshTargetForCurrentInput()");
        int refreshEnd = aimbot.indexOf("    private void refreshTarget(long now)", refresh);
        String refreshMethod = aimbot.substring(refresh, refreshEnd);
        int condition = refreshMethod.indexOf("if (!conditionsMet())");
        int nextCondition = refreshMethod.indexOf("if (isTargetStillEligible())", condition);
        String pausedInput = refreshMethod.substring(condition, nextCondition);

        assertFalse(pausedInput.contains("clearTargetState();"));
        assertTrue(pausedInput.contains("return false;"));

        int boundary = aimbot.indexOf("CameraDelta applyLockOnBoundary(float partialTicks)");
        int boundaryEnd = aimbot.indexOf("    private Vec3 resolveLockOnBoundaryPoint", boundary);
        String boundaryMethod = aimbot.substring(boundary, boundaryEnd);
        int missingPoint = boundaryMethod.indexOf("if (snapPoint == null)");
        int rotation = boundaryMethod.indexOf("ResolvedRotation snap", missingPoint);
        assertFalse(boundaryMethod.substring(missingPoint, rotation).contains("clearTargetState();"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
