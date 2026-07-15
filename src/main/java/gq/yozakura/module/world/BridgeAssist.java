package gq.yozakura.module.world;

import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.RightClickResolvedEvent;
import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.SneakInputEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import org.lwjgl.input.Keyboard;

public class BridgeAssist extends Module {
    private final Option<Boolean> prePlace = new Option<Boolean>("Pre Place", "PrePlace", false);
    private final Numbers<Double> edgeOffset =
            new Numbers<Double>("Edge Offset", "EdgeOffset", 0.0D, 0.0D, 0.3D, 0.01D);
    private final Numbers<Double> unsneakDelay =
            new Numbers<Double>("Unsneak Delay", "UnsneakDelay", 50.0D, 50.0D, 300.0D, 5.0D);
    private final Numbers<Double> sneakOnJump =
            new Numbers<Double>("Sneak On Jump", "SneakOnJump", 0.0D, 0.0D, 500.0D, 5.0D);
    private final Option<Boolean> sneakKeyPressed =
            new Option<Boolean>("Sneak Key Pressed", "SneakKeyPressed", false);
    private final Option<Boolean> holdingBlocks =
            new Option<Boolean>("Holding Blocks", "HoldingBlocks", false);
    private final Option<Boolean> lookingDown =
            new Option<Boolean>("Looking Down", "LookingDown", false);
    private final Option<Boolean> notMovingForward =
            new Option<Boolean>("Not Moving Forward", "NotMovingForward", false);

    private final BridgeAssistSneakController sneakController;
    private final BridgeAssistPrePlaceController prePlaceController;

    public BridgeAssist() {
        super("BridgeAssist", Keyboard.KEY_NONE, ModuleType.World, "Assist edge sneaking while bridging");
        this.addValues(prePlace, edgeOffset, unsneakDelay, sneakOnJump,
                sneakKeyPressed, holdingBlocks, lookingDown, notMovingForward);
        sneakController = new BridgeAssistSneakController(mc, edgeOffset, unsneakDelay, sneakOnJump,
                sneakKeyPressed, holdingBlocks, lookingDown, notMovingForward);
        prePlaceController = new BridgeAssistPrePlaceController(mc, prePlace, lookingDown, notMovingForward);
        Chinese = "搭路辅助";
    }

    @Override
    public void enable() {
        sneakController.clearUnavailableState();
        prePlaceController.reset();
    }

    @Override
    public void disable() {
        sneakController.disable();
        prePlaceController.reset();
    }

    @EventTarget(Priority.HIGH)
    public void onSneakInput(SneakInputEvent event) {
        if (!getState()) {
            return;
        }
        if (!canAssist()) {
            sneakController.clearUnavailableState();
            return;
        }
        sneakController.onSneakInput(event);
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }
        if (!canAssist()) {
            sneakController.clearUnavailableState();
            prePlaceController.reset();
            return;
        }
        prePlaceController.onUpdate(event);
    }

    @EventTarget(Priority.LOWEST)
    public void onRotationResolved(RotationResolvedEvent event) {
        if (!getState() || !canAssist()) {
            prePlaceController.reset();
            return;
        }
        prePlaceController.onRotationResolved(event);
    }

    @EventTarget(Priority.LOWEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (event.isCancelled()) {
            prePlaceController.reset();
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
        }
    }

    @EventTarget
    public void onPacketAccepted(PacketAcceptedEvent event) {
        if (!getState() || !canAssist()) {
            return;
        }
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        event.requestOriginalPacketOrder();
        if (packet.getPlacedBlockDirection() != 255) {
            sneakController.onPlacementPacketAccepted(event.getWriteId());
        }
    }

    @EventTarget
    public void onPacketWritten(PacketWriteEvent event) {
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (packet.getPlacedBlockDirection() != 255) {
            sneakController.onPlacementPacketCompleted(event.getWriteId(), event.isSuccess());
        }
    }

    private boolean canAssist() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && !mc.thePlayer.capabilities.isFlying;
    }
}
