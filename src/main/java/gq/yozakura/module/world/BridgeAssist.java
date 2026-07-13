package gq.yozakura.module.world;

import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
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
        Chinese = "鎼ˉ杈呭姪";
    }

    @Override
    public void disable() {
        sneakController.disable();
        prePlaceController.reset();
    }

    @EventTarget(Priority.LOW)
    public void onMoveInput(MoveInputEvent event) {
        if (!getState()) {
            return;
        }
        if (!canAssist()) {
            sneakController.clearUnavailableState();
            return;
        }
        sneakController.onMoveInput();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }
        if (!canAssist()) {
            prePlaceController.reset();
            return;
        }
        prePlaceController.onUpdate(event);
    }

    @EventTarget(Priority.LOWEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (Boolean.TRUE.equals(prePlace.getValue()) && canAssist()) {
            prePlaceController.onRightClick();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }
        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (packet.getPlacedBlockDirection() != 255) {
            sneakController.onPlacementPacket();
        }
    }

    private boolean canAssist() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && !mc.thePlayer.capabilities.isFlying;
    }
}
