package gq.vapulite.Vapu.modules.world;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class BridgeAssist extends Module {
    private final Numbers<Double> edgeDistance = new Numbers<Double>("Edge Distance", "EdgeDistance", 0.28, 0.05, 0.75, 0.01);
    private final Numbers<Double> slowPercent = new Numbers<Double>("Slow Percent", "SlowPercent", 55.0, 10.0, 100.0, 1.0);
    private final Numbers<Double> predictTicks = new Numbers<Double>("Prediction", "Prediction", 2.0, 0.0, 5.0, 1.0);
    private final Option<Boolean> onlyBlocks = new Option<Boolean>("Only Blocks", "OnlyBlocks", true);
    private final Option<Boolean> holdSneak = new Option<Boolean>("Hold Sneak", "HoldSneak", true);
    private final Option<Boolean> motionGuard = new Option<Boolean>("Motion Guard", "MotionGuard", true);
    private final Option<Boolean> groundOnly = new Option<Boolean>("Ground Only", "GroundOnly", true);

    private boolean holdingSneak;

    public BridgeAssist() {
        super("BridgeAssist", Keyboard.KEY_NONE, ModuleType.World, "Assist safe edge movement while bridging");
        this.addValues(edgeDistance, slowPercent, predictTicks, onlyBlocks, holdSneak, motionGuard, groundOnly);
        Chinese = "搭桥辅助";
    }

    @Override
    public void disable() {
        releaseSneak();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!canAssist()) {
            releaseSneak();
            return;
        }

        boolean unsafe = isNearUnsafeEdge();
        setSneak(Boolean.TRUE.equals(holdSneak.getValue()) && unsafe);
        if (unsafe && Boolean.TRUE.equals(motionGuard.getValue())) {
            double scale = Math.max(0.1D, Math.min(1.0D, slowPercent.getValue() / 100.0D));
            mc.thePlayer.motionX *= scale;
            mc.thePlayer.motionZ *= scale;
        }
    }

    private boolean canAssist() {
        if (!isInGame() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) {
            return false;
        }
        if (Boolean.TRUE.equals(groundOnly.getValue()) && !mc.thePlayer.onGround) {
            return false;
        }
        return !Boolean.TRUE.equals(onlyBlocks.getValue()) || isHoldingBlock();
    }

    private boolean isHoldingBlock() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    private boolean isNearUnsafeEdge() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double guard = edgeDistance.getValue();
        double prediction = predictTicks.getValue();
        double xOffset = clamp(mc.thePlayer.motionX * prediction, -guard, guard);
        double zOffset = clamp(mc.thePlayer.motionZ * prediction, -guard, guard);
        double y = box.minY - 0.05D;

        return !hasSupportAt(box.minX + xOffset, y, box.minZ + zOffset)
                || !hasSupportAt(box.minX + xOffset, y, box.maxZ + zOffset)
                || !hasSupportAt(box.maxX + xOffset, y, box.minZ + zOffset)
                || !hasSupportAt(box.maxX + xOffset, y, box.maxZ + zOffset);
    }

    private boolean hasSupportAt(double x, double y, double z) {
        BlockPos pos = new BlockPos(x, y, z);
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block != null
                && !(block instanceof BlockAir)
                && !(block instanceof BlockLiquid)
                && !block.isReplaceable(mc.theWorld, pos)
                && block.getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) != null;
    }

    private void setSneak(boolean sneak) {
        if (sneak) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            holdingSneak = true;
        } else {
            releaseSneak();
        }
    }

    private void releaseSneak() {
        if (!holdingSneak) {
            return;
        }
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        KeyBinding.setKeyBindState(key, key > 0 && Keyboard.isKeyDown(key));
        holdingSneak = false;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
