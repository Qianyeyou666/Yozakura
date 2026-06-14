package gq.yozakura.module.player;

import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.time.TimerUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class ChestStealer extends Module {
    private final Numbers<Integer> clickDelay = new Numbers<Integer>("Click Delay", "ClickDelay", 80, 0, 1000, 10);
    private final Numbers<Integer> closeDelay = new Numbers<Integer>("Close Delay", "CloseDelay", 120, 0, 1000, 10);
    private final Option<Boolean> nameCheck = new Option<Boolean>("Name Check", "NameCheck", true);
    private final Option<Boolean> smart = new Option<Boolean>("Smart", "Smart", true);
    private final Option<Boolean> autoClose = new Option<Boolean>("Auto Close", "AutoClose", true);

    private final TimerUtil clickTimer = new TimerUtil();
    private final TimerUtil closeTimer = new TimerUtil();

    public ChestStealer() {
        super("ChestStealer", Keyboard.KEY_NONE, ModuleType.Player, "Take useful items from chests");
        addValues(clickDelay, closeDelay, nameCheck, smart, autoClose);
        Chinese = "自动拿箱";
    }

    @Override
    public void enable() {
        clickTimer.reset();
        closeTimer.reset();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isInGame() || !(mc.currentScreen instanceof GuiChest)) {
            return;
        }
        GuiChest chest = (GuiChest) mc.currentScreen;
        if (!(chest.inventorySlots instanceof ContainerChest)) {
            return;
        }
        IInventory inventory = ((ContainerChest) chest.inventorySlots).getLowerChestInventory();
        if (!isAllowedChest(inventory)) {
            return;
        }
        int slot = nextSlot(inventory);
        if (slot == -1) {
            if (Boolean.TRUE.equals(autoClose.getValue()) && closeTimer.hasReached(closeDelay.getValue())) {
                mc.thePlayer.closeScreen();
            }
            return;
        }
        closeTimer.reset();
        if (clickTimer.hasReached(clickDelay.getValue())) {
            mc.playerController.windowClick(chest.inventorySlots.windowId, slot, 0, 1, mc.thePlayer);
            clickTimer.reset();
        }
    }

    private boolean isAllowedChest(IInventory inventory) {
        if (!Boolean.TRUE.equals(nameCheck.getValue())) {
            return true;
        }
        String name = inventory.getDisplayName().getUnformattedText();
        return name != null && (name.contains("Chest") || name.contains("container.chest") || name.contains("箱"));
    }

    private int nextSlot(IInventory inventory) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null && isValid(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isValid(ItemStack stack) {
        if (!Boolean.TRUE.equals(smart.getValue())) {
            return true;
        }
        return stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemTool
                || stack.getItem() instanceof ItemArmor
                || stack.getItem() instanceof ItemBlock
                || stack.getItem() instanceof ItemFood
                || stack.getItem() instanceof ItemPotion
                || stack.getItem() == Items.arrow
                || stack.getItem() == Items.ender_pearl
                || stack.getItem() == Items.golden_apple;
    }
}
