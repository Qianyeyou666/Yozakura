package gq.yozakura.module.render;

import gq.yozakura.event.bridge.RenderTickEndEvent;
import gq.yozakura.event.bridge.RenderTickStartEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.world.BridgeAssist;
import net.minecraft.client.entity.EntityPlayerSP;
import org.lwjgl.input.Keyboard;

/** Allows the camera to rotate independently while the player's real facing stays fixed. */
public final class FreeLook extends Module {
    private static FreeLook activeInstance;

    private final FreeLookCameraState cameraState = new FreeLookCameraState();
    private int previousPerspective;
    private boolean renderRotationApplied;

    public FreeLook() {
        super("FreeLook", Keyboard.KEY_LMENU, ModuleType.Render,
                "Hold the bind to look around without changing player direction");
        Chinese = "自由视角";
        setBindMode(BindMode.HOLD);
    }

    @EventTarget(Priority.LOWEST)
    public void onRenderTickStart(RenderTickStartEvent event) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) {
            restoreSession();
            return;
        }
        if (isTellyControlActive()) {
            suspendForExternalCameraOwner();
            return;
        }

        if (!cameraState.isActive()) {
            previousPerspective = mc.gameSettings.thirdPersonView;
            cameraState.begin(player.rotationYaw, player.rotationPitch);
            activeInstance = this;
        }

        FreeLookCameraState.Frame frame = renderRotationApplied
                ? cameraState.currentFrame()
                : cameraState.captureInput(player.rotationYaw, player.rotationPitch);
        applyPlayerRotation(player, frame.getCameraYaw(), frame.getCameraPitch());
        mc.gameSettings.thirdPersonView = 1;
        renderRotationApplied = true;
    }

    @EventTarget(Priority.HIGHEST)
    public void onRenderTickEnd(RenderTickEndEvent event) {
        restorePlayerFacing();
    }

    @Override
    public void disable() {
        restoreSession();
    }

    private void restorePlayerFacing() {
        if (!cameraState.isActive() || !renderRotationApplied || mc.thePlayer == null) {
            renderRotationApplied = false;
            return;
        }
        FreeLookCameraState.Restore restore = cameraState.restore(previousPerspective);
        applyPlayerRotation(mc.thePlayer, restore.getYaw(), restore.getPitch());
        renderRotationApplied = false;
    }

    /** Restores gameplay facing after vanilla consumed the camera and before custom world overlays run. */
    public static void restorePlayerFacingForOverlays() {
        FreeLook instance = activeInstance;
        if (instance != null) {
            instance.restorePlayerFacing();
        }
    }

    private boolean isTellyControlActive() {
        Module module = ModuleManager.getModule("BridgeAssist");
        return module instanceof BridgeAssist && ((BridgeAssist) module).isTellyControlActive();
    }

    private void suspendForExternalCameraOwner() {
        if (cameraState.isActive()) {
            FreeLookCameraState.Restore restore = cameraState.end(previousPerspective);
            if (mc.gameSettings != null) {
                mc.gameSettings.thirdPersonView = restore.getPerspective();
            }
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
        renderRotationApplied = false;
    }

    private void restoreSession() {
        if (cameraState.isActive()) {
            FreeLookCameraState.Restore restore = cameraState.end(previousPerspective);
            if (mc.thePlayer != null) {
                applyPlayerRotation(mc.thePlayer, restore.getYaw(), restore.getPitch());
            }
            if (mc.gameSettings != null) {
                mc.gameSettings.thirdPersonView = restore.getPerspective();
            }
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
        renderRotationApplied = false;
    }

    private static void applyPlayerRotation(EntityPlayerSP player, float yaw, float pitch) {
        player.rotationYaw = yaw;
        player.prevRotationYaw = yaw;
        player.rotationPitch = pitch;
        player.prevRotationPitch = pitch;
    }
}
