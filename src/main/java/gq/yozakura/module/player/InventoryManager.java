package gq.yozakura.module.player;

import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.time.TimerUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class InventoryManager extends Module {
    private enum InventoryMode {
        OPEN,
        SPOOF,
        ALWAYS
    }

    private final Mode<InventoryMode> mode =
            new Mode<InventoryMode>("Mode", "Mode", InventoryMode.values(), InventoryMode.SPOOF);
    private final Numbers<Integer> delay = new Numbers<Integer>("Delay", "Delay", 80, 0, 500, 10);
    private final Option<Boolean> clean = new Option<Boolean>("Clean", "Clean", true);
    private final Option<Boolean> sort = new Option<Boolean>("Sort", "Sort", true);
    private final Option<Boolean> autoArmor = new Option<Boolean>("Auto Armor", "AutoArmor", true);
    private final Option<Boolean> ignoreCustomName = new Option<Boolean>("Ignore Custom Name", "IgnoreCustomName", true);

    private final TimerUtil timer = new TimerUtil();
    private boolean spoofOpen;

    public InventoryManager() {
        super("InventoryManager", Keyboard.KEY_NONE, ModuleType.Player, "Manage inventory items");
        addValues(mode, delay, clean, sort, autoArmor, ignoreCustomName);
        Chinese = "背包整理";
    }

    @Override
    public void enable() {
        spoofOpen = false;
        timer.reset();
    }

    @Override
    public void disable() {
        closeSpoof();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isInGame()) {
            return;
        }
        if (!shouldWork() || !timer.hasReached(delay.getValue())) {
            return;
        }

        if (Boolean.TRUE.equals(autoArmor.getValue()) && equipArmor()) {
            timer.reset();
            return;
        }
        if (Boolean.TRUE.equals(sort.getValue()) && sortHotbar()) {
            timer.reset();
            return;
        }
        if (Boolean.TRUE.equals(clean.getValue()) && dropTrash()) {
            timer.reset();
            return;
        }
        closeSpoof();
    }

    private boolean shouldWork() {
        InventoryMode current = mode.getValue() == null ? InventoryMode.SPOOF : mode.getValue();
        if (current == InventoryMode.OPEN) {
            return mc.currentScreen instanceof GuiInventory;
        }
        if (current == InventoryMode.SPOOF) {
            openSpoof();
            return true;
        }
        return true;
    }

    private boolean equipArmor() {
        int[] best = bestArmorSlots();
        for (int type = 0; type < best.length; type++) {
            int slot = best[type];
            int armorSlot = 5 + type;
            ItemStack equippedStack = mc.thePlayer.inventoryContainer.getSlot(armorSlot).getStack();
            if (equippedStack != null && shouldIgnore(equippedStack)) {
                continue;
            }
            boolean equipped = equippedStack != null;
            InventorySelection.ArmorAction action = InventorySelection.chooseArmorAction(slot, armorSlot, equipped);
            if (action == InventorySelection.ArmorAction.UNEQUIP_CURRENT) {
                click(armorSlot, 0, 1);
                return true;
            }
            if (action == InventorySelection.ArmorAction.EQUIP_BEST) {
                click(slot, 0, 1);
                return true;
            }
        }
        return false;
    }

    private boolean sortHotbar() {
        int sword = bestSlot(ItemSword.class, 36);
        if (sword != -1 && sword != 36) {
            swapToHotbar(sword, 0);
            return true;
        }
        int bow = bestSlot(ItemBow.class, 37);
        if (bow != -1 && bow != 37) {
            swapToHotbar(bow, 1);
            return true;
        }
        int pickaxe = bestSlot(ItemPickaxe.class, 38);
        if (pickaxe != -1 && pickaxe != 38) {
            swapToHotbar(pickaxe, 2);
            return true;
        }
        int axe = bestSlot(ItemAxe.class, 39);
        if (axe != -1 && axe != 39) {
            swapToHotbar(axe, 3);
            return true;
        }
        int blocks = bestBlockSlot(40);
        if (blocks != -1 && blocks != 40) {
            swapToHotbar(blocks, 4);
            return true;
        }
        return false;
    }

    private boolean dropTrash() {
        int[] bestArmor = bestArmorSlots();
        int bestSword = bestSlot(ItemSword.class, 36);
        int bestBow = bestSlot(ItemBow.class, 37);
        int bestPickaxe = bestSlot(ItemPickaxe.class, 38);
        int bestAxe = bestSlot(ItemAxe.class, 39);
        int bestSpade = bestSlot(ItemSpade.class, -1);
        int bestBlock = bestBlockSlot(40);

        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack == null || shouldIgnore(stack)) {
                continue;
            }
            Item item = stack.getItem();
            boolean keep = isUseful(stack)
                    || item instanceof ItemSword && slot == bestSword
                    || item instanceof ItemBow && slot == bestBow
                    || item instanceof ItemPickaxe && slot == bestPickaxe
                    || item instanceof ItemAxe && slot == bestAxe
                    || item instanceof ItemSpade && slot == bestSpade
                    || item instanceof ItemBlock && slot == bestBlock
                    || item instanceof ItemArmor && contains(bestArmor, slot);
            if (!keep) {
                click(slot, 1, 4);
                return true;
            }
        }
        return false;
    }

    private int[] bestArmorSlots() {
        int[] slots = new int[4];
        float[] scores = new float[4];
        int[] durabilities = new int[4];
        Arrays.fill(slots, -1);
        Arrays.fill(scores, -1.0F);
        Arrays.fill(durabilities, -1);
        for (int slot = 5; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack == null || shouldIgnore(stack) || !(stack.getItem() instanceof ItemArmor)) {
                continue;
            }
            ItemArmor armor = (ItemArmor) stack.getItem();
            float score = armor.damageReduceAmount
                    + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 0.75F;
            int durability = remainingDurability(stack);
            int armorSlot = 5 + armor.armorType;
            if (InventorySelection.isBetterCandidate(score, durability, slot,
                    scores[armor.armorType], durabilities[armor.armorType], slots[armor.armorType], armorSlot)) {
                scores[armor.armorType] = score;
                durabilities[armor.armorType] = durability;
                slots[armor.armorType] = slot;
            }
        }
        return slots;
    }

    private int bestSlot(Class<?> type, int preferredSlot) {
        int best = -1;
        float score = -1.0F;
        int durability = -1;
        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack == null || shouldIgnore(stack) || !type.isInstance(stack.getItem())) {
                continue;
            }
            float current = itemScore(stack);
            int currentDurability = remainingDurability(stack);
            if (InventorySelection.isBetterCandidate(current, currentDurability, slot,
                    score, durability, best, preferredSlot)) {
                score = current;
                durability = currentDurability;
                best = slot;
            }
        }
        return best;
    }

    private int bestBlockSlot(int preferredSlot) {
        int best = -1;
        int size = -1;
        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() instanceof ItemBlock
                    && InventorySelection.isBetterCandidate(stack.stackSize, 0, slot,
                    size, 0, best, preferredSlot)) {
                size = stack.stackSize;
                best = slot;
            }
        }
        return best;
    }

    private float itemScore(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemSword) {
            return ((ItemSword) item).getDamageVsEntity()
                    + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25F;
        }
        if (item instanceof ItemBow) {
            return 1.0F + EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
        }
        return stack.getMaxDamage() <= 0 ? stack.stackSize : stack.getMaxDamage() - stack.getItemDamage();
    }

    private boolean isUseful(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ItemFood
                || item instanceof ItemPotion
                || item == Items.ender_pearl
                || item == Items.golden_apple
                || item == Items.arrow
                || item == Items.water_bucket
                || item == Items.lava_bucket;
    }

    private boolean shouldIgnore(ItemStack stack) {
        return Boolean.TRUE.equals(ignoreCustomName.getValue()) && stack.hasDisplayName();
    }

    private int remainingDurability(ItemStack stack) {
        return stack.getMaxDamage() <= 0 ? 0 : stack.getMaxDamage() - stack.getItemDamage();
    }

    private boolean contains(int[] values, int wanted) {
        for (int value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }

    private void swapToHotbar(int slot, int hotbarSlot) {
        click(slot, hotbarSlot, 2);
    }

    private void click(int slot, int button, int mode) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, button, mode, mc.thePlayer);
    }

    private void openSpoof() {
        if (!spoofOpen && !(mc.currentScreen instanceof GuiInventory)) {
            mc.thePlayer.sendQueue.addToSendQueue(new C16PacketClientStatus(
                    C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            spoofOpen = true;
        }
    }

    private void closeSpoof() {
        if (spoofOpen && isInGame()) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0DPacketCloseWindow(mc.thePlayer.inventoryContainer.windowId));
            spoofOpen = false;
        }
    }
}
