package gq.yozakura.module.world;

import gq.yozakura.event.bridge.RotationResolvedEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.util.module.BlockUtil;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.module.RotationUtil;
import gq.yozakura.value.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Generic BridgeAssist technique controller.
 *
 * <p>The complete TellyBridge runtime is deliberately owned by
 * {@link TellyBridgeRuntime}. This controller preserves the original
 * GodBridge right-click preparation path.</p>
 */
final class BridgeAssistBridgeModeController {
    private static final int ROTATION_PRIORITY = 2;
    private static final String ROTATION_SOURCE = "BridgeAssistModes";
    private static final int[] GOD_SEARCH_ORDER = {5, 4, 6, 1, 7, 2, 8, 3};
    private static final EnumFacing[] SUPPORT_FACES = {
            EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    private final Minecraft mc;
    private final Mode<BridgeAssistBridgeModeStateMachine.Mode> modeValue;
    private final BridgeAssistBridgeModeStateMachine stateMachine =
            new BridgeAssistBridgeModeStateMachine();

    private PendingTarget pendingTarget;
    private PreparedTarget preparedTarget;
    private MovingObjectPosition injectedMouseOver;
    private MovingObjectPosition previousMouseOver;

    BridgeAssistBridgeModeController(Minecraft mc,
                                     Mode<BridgeAssistBridgeModeStateMachine.Mode> modeValue) {
        this.mc = mc;
        this.modeValue = modeValue;
    }

    boolean isSpecialMode() {
        return selectedMode() == BridgeAssistBridgeModeStateMachine.Mode.GodBridge;
    }

    boolean onUpdate(UpdateEvent event) {
        clearPreparation();
        BridgeAssistBridgeModeStateMachine.Mode mode = selectedMode();
        if (mode != BridgeAssistBridgeModeStateMachine.Mode.GodBridge) {
            stateMachine.update(mode, frame());
            return false;
        }

        BridgeAssistBridgeModeStateMachine.Plan plan = stateMachine.update(mode, frame());
        if (!plan.isActive()) {
            return true;
        }
        if (!canPrepare()) {
            return true;
        }

        PlacementTarget target = findGodBridgeTarget();
        if (target == null) {
            publishSetupRotation(event, plan);
            return true;
        }
        if (!event.trySetRotation(target.yaw, target.pitch, ROTATION_PRIORITY)) {
            return true;
        }
        pendingTarget = PendingTarget.forPlacement(mc.theWorld, mc.thePlayer.ticksExisted,
                target.support, target.face, target.yaw, target.pitch);
        return true;
    }

    void onRotationResolved(RotationResolvedEvent event) {
        PendingTarget target = pendingTarget;
        pendingTarget = null;
        if (target == null || !matchesResolvedRotation(target, event)) {
            clearPreparation();
            return;
        }

        VisualRotationState.publish(ROTATION_SOURCE, target.yaw, target.pitch, ROTATION_PRIORITY);
        if (!target.hasPlacement()) {
            return;
        }
        MovingObjectPosition hit = resolveTargetHit(target);
        if (hit == null) {
            clearPreparation();
            return;
        }
        preparedTarget = new PreparedTarget(target.world, target.tick, target.support, target.face,
                target.yaw, target.pitch);
        installMouseOver(hit);
    }

    void onRightClick() {
        PreparedTarget target = preparedTarget;
        if (target == null) {
            return;
        }
        MovingObjectPosition hit = resolveTargetHit(target);
        if (hit == null) {
            clearPreparation();
            return;
        }
        installMouseOver(hit);
    }

    void onRightClickCancelled() {
        clearPreparation();
    }

    void onManualPlacement() {
        stateMachine.recordManualPlacement(selectedMode());
    }

    void reset() {
        stateMachine.reset();
        clearPreparation();
    }

    private BridgeAssistBridgeModeStateMachine.Frame frame() {
        boolean backward = mc.thePlayer.movementInput != null
                && mc.thePlayer.movementInput.moveForward < 0.0F;
        boolean useItem = mc.gameSettings != null && mc.gameSettings.keyBindUseItem.isKeyDown();
        return new BridgeAssistBridgeModeStateMachine.Frame(mc.thePlayer.ticksExisted,
                mc.thePlayer.rotationYaw, mc.thePlayer.onGround, backward, useItem);
    }

    private BridgeAssistBridgeModeStateMachine.Mode selectedMode() {
        BridgeAssistBridgeModeStateMachine.Mode mode = modeValue.getValue();
        return mode == null ? BridgeAssistBridgeModeStateMachine.Mode.Legit : mode;
    }

    private boolean canPrepare() {
        return mc.thePlayer != null
                && mc.theWorld != null
                && mc.playerController != null
                && ItemUtil.isBlock(mc.thePlayer.getHeldItem());
    }

    private PlacementTarget findGodBridgeTarget() {
        BlockPos origin = bridgeBase();
        for (int direction : GOD_SEARCH_ORDER) {
            PlacementTarget target = findPlacementAt(origin.add(
                    BridgeAssistBridgeModeStateMachine.offsetX(direction),
                    0,
                    BridgeAssistBridgeModeStateMachine.offsetZ(direction)));
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private BlockPos bridgeBase() {
        return new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY - 1.0D),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
    }

    private PlacementTarget findPlacementAt(BlockPos placedBlock) {
        if (!BlockUtil.isReplaceable(placedBlock)) {
            return null;
        }
        for (EnumFacing face : SUPPORT_FACES) {
            BlockPos support = placedBlock.offset(face.getOpposite());
            if (BlockUtil.isReplaceable(support)) {
                continue;
            }
            PlacementTarget target = rotationForFace(placedBlock, support, face);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private PlacementTarget rotationForFace(BlockPos placedBlock, BlockPos support, EnumFacing face) {
        Vec3 hitPoint = new Vec3(
                support.getX() + 0.5D + face.getFrontOffsetX() * 0.5D,
                support.getY() + 0.5D + face.getFrontOffsetY() * 0.5D,
                support.getZ() + 0.5D + face.getFrontOffsetZ() * 0.5D
        );
        float[] rotations = RotationUtil.getRotations(hitPoint);
        float yaw = RotationUtil.quantizeAngle(rotations[0]);
        float pitch = RotationUtil.quantizeAngle(
                MathHelper.clamp_float(rotations[1], -90.0F, 90.0F));
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch,
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (!matchesHit(hit, support, face)) {
            return null;
        }
        return new PlacementTarget(placedBlock, support, face, yaw, pitch);
    }

    private boolean publishSetupRotation(UpdateEvent event,
                                         BridgeAssistBridgeModeStateMachine.Plan plan) {
        if (plan.getSetupPitch() == 0.0F || !event.trySetRotation(plan.getSetupYaw(),
                plan.getSetupPitch(), ROTATION_PRIORITY)) {
            return false;
        }
        pendingTarget = PendingTarget.forVisual(mc.theWorld, mc.thePlayer.ticksExisted,
                event.getNewYaw(), event.getNewPitch());
        return true;
    }

    private boolean matchesResolvedRotation(PendingTarget target, RotationResolvedEvent event) {
        return event.isRotated()
                && Float.compare(target.yaw, event.getYaw()) == 0
                && Float.compare(target.pitch, event.getPitch()) == 0;
    }

    private MovingObjectPosition resolveTargetHit(Target target) {
        if (mc.theWorld != target.world || mc.thePlayer.ticksExisted != target.tick
                || !canPrepare()) {
            return null;
        }
        if (BlockUtil.isReplaceable(target.support)
                || !BlockUtil.isReplaceable(target.support.offset(target.face))) {
            return null;
        }
        MovingObjectPosition hit = RotationUtil.rayTrace(target.yaw, target.pitch,
                mc.playerController.getBlockReachDistance(), 1.0F);
        return matchesHit(hit, target.support, target.face) ? hit : null;
    }

    private static boolean matchesHit(MovingObjectPosition hit, BlockPos support, EnumFacing face) {
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && support.equals(hit.getBlockPos())
                && face == hit.sideHit;
    }

    private void installMouseOver(MovingObjectPosition hit) {
        if (injectedMouseOver == hit) {
            mc.objectMouseOver = hit;
            return;
        }
        clearInjectedMouseOver();
        previousMouseOver = mc.objectMouseOver;
        injectedMouseOver = hit;
        mc.objectMouseOver = hit;
    }

    private void clearPreparation() {
        pendingTarget = null;
        preparedTarget = null;
        VisualRotationState.clearSource(ROTATION_SOURCE);
        clearInjectedMouseOver();
    }

    private void clearInjectedMouseOver() {
        if (injectedMouseOver != null && mc.objectMouseOver == injectedMouseOver) {
            mc.objectMouseOver = previousMouseOver;
        }
        injectedMouseOver = null;
        previousMouseOver = null;
    }

    private abstract static class Target {
        final World world;
        final int tick;
        final BlockPos support;
        final EnumFacing face;
        final float yaw;
        final float pitch;

        private Target(World world, int tick, BlockPos support, EnumFacing face,
                       float yaw, float pitch) {
            this.world = world;
            this.tick = tick;
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class PlacementTarget {
        final BlockPos placedBlock;
        final BlockPos support;
        final EnumFacing face;
        final float yaw;
        final float pitch;

        private PlacementTarget(BlockPos placedBlock, BlockPos support, EnumFacing face,
                                float yaw, float pitch) {
            this.placedBlock = placedBlock;
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class PendingTarget extends Target {
        private PendingTarget(World world, int tick, BlockPos support, EnumFacing face,
                              float yaw, float pitch) {
            super(world, tick, support, face, yaw, pitch);
        }

        static PendingTarget forPlacement(World world, int tick, BlockPos support, EnumFacing face,
                                          float yaw, float pitch) {
            return new PendingTarget(world, tick, support, face, yaw, pitch);
        }

        static PendingTarget forVisual(World world, int tick, float yaw, float pitch) {
            return new PendingTarget(world, tick, null, null, yaw, pitch);
        }

        boolean hasPlacement() {
            return support != null && face != null;
        }
    }

    private static final class PreparedTarget extends Target {
        private PreparedTarget(World world, int tick, BlockPos support, EnumFacing face,
                               float yaw, float pitch) {
            super(world, tick, support, face, yaw, pitch);
        }
    }
}
