package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public class FastPlace extends Module {
    private final Numbers<Integer> delay = new Numbers<Integer>("Delay", "Delay", 0, 0, 4, 1);
    private final Option<Boolean> onlyBlocks = new Option<Boolean>("Only Blocks", "OnlyBlocks", true);

    private boolean accessFailureReported;

    public FastPlace() {
        super("FastPlace", Keyboard.KEY_NONE, ModuleType.World, "Make you place the blocks faster");
        this.addValues(delay, onlyBlocks);
        Chinese = "快速放置";
    }

    @Override
    public void enable() {
        accessFailureReported = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!getState() || event.getType() != EventType.PRE || !isInGame()) {
            return;
        }

        boolean restrictToBlocks = !Boolean.FALSE.equals(onlyBlocks.getValue());
        if (!FastPlacePolicy.shouldCapCooldown(
                mc.gameSettings.keyBindUseItem.isKeyDown(), restrictToBlocks, isHoldingBlockItem())) {
            return;
        }

        try {
            MinecraftAccessor.capRightClickDelayTimer(mc, getDelayTicks());
        } catch (IllegalStateException exception) {
            reportAccessFailure(exception);
        }
    }

    private boolean isHoldingBlockItem() {
        ItemStack itemStack = mc.thePlayer.getHeldItem();
        return itemStack != null && itemStack.stackSize > 0 && itemStack.getItem() instanceof ItemBlock;
    }

    private int getDelayTicks() {
        return FastPlacePolicy.normalizeDelayTicks(delay.getValue());
    }

    private void reportAccessFailure(IllegalStateException exception) {
        if (accessFailureReported) {
            return;
        }
        accessFailureReported = true;
        Helper.sendMessage("FastPlace could not update the right-click cooldown: " + exception.getMessage());
    }
}
