package gq.yozakura.module.world;

import gq.yozakura.event.bridge.SneakInputEvent;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;

import java.util.List;

/** Maintains BridgeAssist's edge prediction and submits one sneak intent per input frame. */
final class BridgeAssistSneakController {
    private static final float MIN_LOOK_DOWN_PITCH = 70.0F;

    private final Minecraft mc;
    private final Numbers<Double> edgeOffset;
    private final Numbers<Double> unsneakDelay;
    private final Numbers<Double> sneakOnJump;
    private final Option<Boolean> sneakKeyPressed;
    private final Option<Boolean> holdingBlocks;
    private final Option<Boolean> lookingDown;
    private final Option<Boolean> notMovingForward;
    private final BridgeAssistSneakStateMachine stateMachine = new BridgeAssistSneakStateMachine();

    BridgeAssistSneakController(Minecraft mc, Numbers<Double> edgeOffset, Numbers<Double> unsneakDelay,
                                Numbers<Double> sneakOnJump, Option<Boolean> sneakKeyPressed,
                                Option<Boolean> holdingBlocks, Option<Boolean> lookingDown,
                                Option<Boolean> notMovingForward) {
        this.mc = mc;
        this.edgeOffset = edgeOffset;
        this.unsneakDelay = unsneakDelay;
        this.sneakOnJump = sneakOnJump;
        this.sneakKeyPressed = sneakKeyPressed;
        this.holdingBlocks = holdingBlocks;
        this.lookingDown = lookingDown;
        this.notMovingForward = notMovingForward;
    }

    void onSneakInput(SneakInputEvent event) {
        float forward = event.getRawForward();
        float strafe = event.getRawStrafe();
        if (shouldClearSneak(forward)) {
            reset();
            return;
        }

        EdgeProbe probe = probeEdge();
        BridgeAssistSneakStateMachine.Frame frame = new BridgeAssistSneakStateMachine.Frame(
                event.getTick(),
                true,
                forward != 0.0F || strafe != 0.0F,
                event.isPhysicalSneak(),
                Boolean.TRUE.equals(sneakKeyPressed.getValue()),
                event.isJump(),
                mc.thePlayer.onGround,
                probe == EdgeProbe.EDGE,
                false,
                false,
                false,
                BridgeAssistMovementPrediction.ticksFromMillis(unsneakDelay.getValue()),
                BridgeAssistMovementPrediction.ticksFromMillis(sneakOnJump.getValue())
        );
        BridgeAssistSneakStateMachine.Decision decision = stateMachine.update(frame);
        event.requestSneak(toIntent(decision), 0);
    }

    void clearUnavailableState() {
        reset();
    }

    void disable() {
        reset();
    }

    private void reset() {
        stateMachine.reset();
    }

    private boolean shouldClearSneak(float forward) {
        return forward > 0.0F
                || Boolean.TRUE.equals(lookingDown.getValue()) && mc.thePlayer.rotationPitch < MIN_LOOK_DOWN_PITCH
                || Boolean.TRUE.equals(holdingBlocks.getValue()) && !ItemUtil.isBlock(mc.thePlayer.getHeldItem());
    }

    private EdgeProbe probeEdge() {
        AxisAlignedBB checkBox = mc.thePlayer.getEntityBoundingBox()
                .contract(0.2D, 0.0D, 0.2D)
                .offset(mc.thePlayer.motionX, -1.0D, mc.thePlayer.motionZ);
        List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, checkBox);
        return collisions.isEmpty() ? EdgeProbe.EDGE : EdgeProbe.SUPPORTED;
    }

    private SneakInputEvent.SneakIntent toIntent(BridgeAssistSneakStateMachine.Decision decision) {
        if (decision == BridgeAssistSneakStateMachine.Decision.FORCE_ON) {
            return SneakInputEvent.SneakIntent.FORCE_ON;
        }
        if (decision == BridgeAssistSneakStateMachine.Decision.FORCE_OFF) {
            return SneakInputEvent.SneakIntent.FORCE_OFF;
        }
        return SneakInputEvent.SneakIntent.KEEP;
    }

    private enum EdgeProbe {
        SUPPORTED,
        EDGE
    }
}
