package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.module.PacketUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

public class SpeedMine extends Module {
    private enum MiningMode {
        Packet,
        Vanilla
    }

    private final Mode<MiningMode> mode =
            new Mode<MiningMode>("Mode", "Mode", MiningMode.values(), MiningMode.Packet);
    private final Numbers<Double> speed =
            new Numbers<Double>("Speed", "Speed", 2.0D, 1.0D, 5.0D, 0.1D);
    private final Option<Boolean> zeroHitDelay =
            new Option<Boolean>("Zero Hit Delay", "ZeroHitDelay", true);
    private final Option<Boolean> fastFinish =
            new Option<Boolean>("Fast Finish", "FastFinish", true);
    private final Numbers<Double> finishThreshold =
            new Numbers<Double>("Finish Threshold", "FinishThreshold", 0.70D, 0.50D, 0.95D, 0.01D);
    private final SpeedMineDigSession digSession = new SpeedMineDigSession();

    public SpeedMine() {
        super("SpeedMine", Keyboard.KEY_NONE, ModuleType.World, "Accelerate vanilla block breaking progress");
        finishThreshold.visibleWhen(() -> Boolean.TRUE.equals(fastFinish.getValue()));
        this.addValues(mode, speed, zeroHitDelay, fastFinish, finishThreshold);
        Chinese = "快速挖掘";
    }

    @Override
    public void enable() {
        digSession.reset();
    }

    @Override
    public void disable() {
        digSession.reset();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!getState() || event.getType() != EventType.SEND || event.isCancelled()
                || !(event.getPacket() instanceof C07PacketPlayerDigging)) {
            return;
        }
        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
        BlockPos position = packet.getPosition();
        if (packet.getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
            digSession.start(position.getX(), position.getY(), position.getZ(), packet.getFacing().ordinal());
        } else if (packet.getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
            digSession.abort(position.getX(), position.getY(), position.getZ());
        } else if (packet.getStatus() == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
            digSession.reset();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!getState() || event.getType() != EventType.PRE || !isInGame() || mc.playerController == null) {
            return;
        }
        if (Boolean.TRUE.equals(zeroHitDelay.getValue())) {
            MinecraftAccessor.setBlockHitDelay(mc.playerController, 0);
        }
        accelerateMiningProgress();
    }

    private void accelerateMiningProgress() {
        boolean hittingBlock = MinecraftAccessor.isHittingBlock(mc.playerController);
        BlockPos currentBlock = MinecraftAccessor.getCurrentBlock(mc.playerController);
        if (!hittingBlock || currentBlock == null) {
            return;
        }

        IBlockState state = mc.theWorld.getBlockState(currentBlock);
        Block block = state.getBlock();
        float relativeHardness = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, currentBlock);
        float currentDamage = MinecraftAccessor.getCurrentBlockDamage(mc.playerController);
        float acceleratedDamage = Math.min(1.0F, currentDamage
                + SpeedMinePolicy.extraDamage(true, relativeHardness, speed.getValue()));
        if (Boolean.TRUE.equals(fastFinish.getValue())
                && SpeedMinePolicy.shouldFinish(true, acceleratedDamage,
                SpeedMinePolicy.normalizeFinishThreshold(finishThreshold.getValue()), relativeHardness)) {
            if (mode.getValue() == MiningMode.Packet && finishWithPacket(currentBlock)) {
                return;
            }
            acceleratedDamage = 1.0F;
        }
        MinecraftAccessor.setCurrentBlockDamage(mc.playerController, acceleratedDamage);
    }

    private boolean finishWithPacket(BlockPos currentBlock) {
        SpeedMineDigSession.Target target = digSession.finish(
                currentBlock.getX(), currentBlock.getY(), currentBlock.getZ());
        if (target == null) {
            return false;
        }
        EnumFacing[] facings = EnumFacing.values();
        if (target.facingOrdinal < 0 || target.facingOrdinal >= facings.length) {
            return false;
        }
        BlockPos targetPos = new BlockPos(target.x, target.y, target.z);
        MinecraftAccessor.setCurrentBlockDamage(mc.playerController, 1.0F);
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, targetPos, facings[target.facingOrdinal]));
        return true;
    }
}
