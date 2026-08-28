package gq.yozakura.module.world;

import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.LoadWorldEvent;
import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.RenderTickStartEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.RightClickResolvedEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.SafeWalkEvent;
import gq.yozakura.event.bridge.SneakInputEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public class BridgeAssist extends Module {
    private final Mode<BridgeAssistBridgeModeStateMachine.Mode> bridgeMode =
            new Mode<BridgeAssistBridgeModeStateMachine.Mode>("Bridge Mode", "BridgeMode",
                    BridgeAssistBridgeModeStateMachine.Mode.values(),
                    BridgeAssistBridgeModeStateMachine.Mode.Legit);
    private final Option<Boolean> tellyAutoSwap =
            new Option<Boolean>("Telly Auto Swap", "TellyAutoSwap", true);
    private final Option<Boolean> tellyDisableSafeWalk =
            new Option<Boolean>("Telly Disable SafeWalk", "TellyDisableSafeWalk", true);
    private final Option<Boolean> tellyShowActivationHitbox =
            new Option<Boolean>("Telly Activation Hitbox", "TellyActivationHitbox", false);
    private final Numbers<Double> edgeOffset =
            new Numbers<Double>("Edge Tolerance (blocks)", "EdgeOffset", 0.0D, 0.0D, 0.3D, 0.01D);
    private final Numbers<Double> unsneakDelay =
            new Numbers<Double>("Release Delay (ms)", "UnsneakDelay", 50.0D, 10.0D, 300.0D, 1.0D);
    private final Option<Boolean> holdingBlocks =
            new Option<Boolean>("Only With Blocks", "HoldingBlocks", false);
    private final Option<Boolean> advancedOptions =
            new Option<Boolean>("Advanced Options", "AdvancedOptions", false);
    private final Option<Boolean> prePlace =
            new Option<Boolean>("Pre-place Rotation", "PrePlace", false);
    private final Numbers<Double> sneakOnJump =
            new Numbers<Double>("Jump Sneak Hold (ms)", "SneakOnJump", 0.0D, 0.0D, 500.0D, 1.0D);
    private final Option<Boolean> sneakKeyPressed =
            new Option<Boolean>("Require Sneak Key", "SneakKeyPressed", false);
    private final Option<Boolean> lookingDown =
            new Option<Boolean>("Require Looking Down", "LookingDown", false);
    private final Option<Boolean> notMovingForward =
            new Option<Boolean>("Disable While Moving Forward", "NotMovingForward", false);

    private final BridgeAssistSneakController sneakController;
    private final BridgeAssistPrePlaceController prePlaceController;
    private final BridgeAssistBridgeModeController bridgeModeController;
    private final TellyBridgeRuntime tellyRuntime;

    public BridgeAssist() {
        super("BridgeAssist", Keyboard.KEY_NONE, ModuleType.World, "Assist edge sneaking while bridging");
        configureOptionVisibility();
        this.addValues(bridgeMode, tellyAutoSwap, tellyDisableSafeWalk, tellyShowActivationHitbox,
                edgeOffset, unsneakDelay, holdingBlocks, advancedOptions, prePlace, sneakOnJump,
                sneakKeyPressed, lookingDown, notMovingForward);
        sneakController = new BridgeAssistSneakController(mc, edgeOffset, unsneakDelay, sneakOnJump,
                sneakKeyPressed, holdingBlocks, lookingDown, notMovingForward);
        prePlaceController = new BridgeAssistPrePlaceController(mc, prePlace, lookingDown, notMovingForward);
        bridgeModeController = new BridgeAssistBridgeModeController(mc, bridgeMode);
        tellyRuntime = new TellyBridgeRuntime(mc, bridgeMode, tellyAutoSwap,
                tellyDisableSafeWalk, tellyShowActivationHitbox);
        Chinese = "搭路辅助";
    }

    @Override
    public void enable() {
        sneakController.clearUnavailableState();
        prePlaceController.reset();
        bridgeModeController.reset();
        tellyRuntime.enable();
    }

    @Override
    public void disable() {
        sneakController.disable();
        prePlaceController.reset();
        bridgeModeController.reset();
        tellyRuntime.reset();
    }

    @EventTarget(Priority.HIGHEST)
    public void onSneakInput(SneakInputEvent event) {
        if (getState() && isTellyBridgeMode()) {
            sneakController.clearUnavailableState();
            tellyRuntime.onSneakInput(event);
            return;
        }
        if (!getState() || bridgeModeController.isSpecialMode()) {
            sneakController.clearUnavailableState();
            return;
        }
        if (!canAssist()) {
            sneakController.clearUnavailableState();
            return;
        }
        sneakController.onSneakInput(event);
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!getState()) {
            return;
        }
        tellyRuntime.observeModeSelection();
        if (isTellyBridgeMode()) {
            sneakController.clearUnavailableState();
            prePlaceController.reset();
            bridgeModeController.reset();
            tellyRuntime.onUpdate(event);
            return;
        }
        if (!canAssist()) {
            sneakController.clearUnavailableState();
            prePlaceController.reset();
            bridgeModeController.reset();
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (bridgeModeController.onUpdate(event)) {
            prePlaceController.reset();
            return;
        }
        prePlaceController.onUpdate(event);
    }

    @EventTarget(Priority.HIGHEST)
    public void onWorldLoad(LoadWorldEvent event) {
        tellyRuntime.onWorldJoin();
    }

    @EventTarget(Priority.LOWEST)
    public void onRotationResolved(RotationResolvedEvent event) {
        if (!getState() || !canAssist()) {
            prePlaceController.reset();
            bridgeModeController.reset();
            return;
        }
        if (isTellyBridgeMode()) {
            prePlaceController.reset();
            bridgeModeController.reset();
            tellyRuntime.onRotationResolved(event);
            return;
        }
        if (bridgeModeController.isSpecialMode()) {
            prePlaceController.reset();
            bridgeModeController.onRotationResolved(event);
            return;
        }
        prePlaceController.onRotationResolved(event);
    }

    @EventTarget(Priority.LOWEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (event.isCancelled()) {
            prePlaceController.reset();
            bridgeModeController.onRightClickCancelled();
            tellyRuntime.onRightClickCancelled();
            return;
        }
        if (isTellyBridgeMode()) {
            prePlaceController.reset();
            bridgeModeController.onRightClickCancelled();
            if (getState()) {
                tellyRuntime.onRightClick(event);
            }
            return;
        }
        if (bridgeModeController.isSpecialMode()) {
            prePlaceController.reset();
            if (getState() && canAssist()) {
                bridgeModeController.onRightClick();
            } else {
                bridgeModeController.onRightClickCancelled();
            }
            return;
        }
        if (getState() && Boolean.TRUE.equals(prePlace.getValue()) && canAssist()) {
            prePlaceController.onRightClick();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRightClickResolved(RightClickResolvedEvent event) {
        if (event.isCancelled()) {
            prePlaceController.reset();
            bridgeModeController.onRightClickCancelled();
        }
    }

    @EventTarget
    public void onPacketAccepted(PacketAcceptedEvent event) {
        if (!getState() || !canPreservePlacementOrder()) {
            return;
        }
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        event.requestOriginalPacketOrder();
        if (isTellyBridgeMode()) {
            tellyRuntime.onPacketAccepted(event);
            return;
        }
    }

    @EventTarget
    public void onPacketWritten(PacketWriteEvent event) {
        if (!getState() || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (isTellyBridgeMode()) {
            tellyRuntime.onPacketWritten(event);
            return;
        }
        if (packet.getPlacedBlockDirection() != 255) {
            if (getState() && event.isSuccess() && canPreservePlacementOrder()
                    && !isTellyBridgeMode()) {
                bridgeModeController.onManualPlacement();
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (getState()) {
            tellyRuntime.onPacket(event);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (getState() && isTellyBridgeMode() && tellyRuntime.ownsAttackPath()) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onSafeWalk(SafeWalkEvent event) {
        if (getState()) {
            tellyRuntime.onSafeWalk(event);
        }
    }

    @EventTarget
    public void onRenderTickStart(RenderTickStartEvent event) {
        if (getState()) {
            tellyRuntime.onRenderTick();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (getState()) {
            tellyRuntime.onRender2D();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (getState()) {
            tellyRuntime.onRender3D();
        }
    }

    /** True while Telly mode owns scripted movement/camera-sensitive rotation state. */
    public boolean isTellyControlActive() {
        return getState() && isTellyBridgeMode();
    }

    private boolean canAssist() {
        return canPreservePlacementOrder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isRiding()
                && !isInsideWeb();
    }

    private boolean canPreservePlacementOrder() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && !mc.thePlayer.capabilities.isFlying;
    }

    private boolean isInsideWeb() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX - 1.0E-7D);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY - 1.0E-7D);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ - 1.0E-7D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.web) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void configureOptionVisibility() {
        tellyAutoSwap.visibleWhen(this::isTellyBridgeMode);
        tellyDisableSafeWalk.visibleWhen(this::isTellyBridgeMode);
        tellyShowActivationHitbox.visibleWhen(this::isTellyBridgeMode);
        prePlace.visibleWhen(this::showAdvancedOptions);
        sneakOnJump.visibleWhen(this::showAdvancedOptions);
        sneakKeyPressed.visibleWhen(this::showAdvancedOptions);
        lookingDown.visibleWhen(this::showAdvancedOptions);
        notMovingForward.visibleWhen(this::showAdvancedOptions);
    }

    private boolean isTellyBridgeMode() {
        return bridgeMode.getValue() == BridgeAssistBridgeModeStateMachine.Mode.TellyBridge;
    }

    private boolean showAdvancedOptions() {
        return Boolean.TRUE.equals(advancedOptions.getValue())
                || Boolean.TRUE.equals(prePlace.getValue())
                || sneakOnJump.getValue() > 0.0D
                || Boolean.TRUE.equals(sneakKeyPressed.getValue())
                || Boolean.TRUE.equals(lookingDown.getValue())
                || Boolean.TRUE.equals(notMovingForward.getValue());
    }
}
