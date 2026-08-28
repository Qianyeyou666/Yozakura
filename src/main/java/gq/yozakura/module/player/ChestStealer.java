package gq.yozakura.module.player;

import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.time.TimerUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
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
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChestStealer extends Module {
    private static final int MAX_INSTANT_CLICKS = 108;

    private enum ChestMode {
        NORMAL,
        INSTANT
    }

    private final Mode<ChestMode> mode =
            new Mode<ChestMode>("Mode", "Mode", ChestMode.values(), ChestMode.NORMAL);
    private final Numbers<Integer> clickDelay = new Numbers<Integer>("Click Delay", "ClickDelay", 80, 0, 1000, 10);
    private final Numbers<Integer> delayJitter = new Numbers<Integer>("Delay Jitter", "DelayJitter", 20, 0, 250, 5);
    private final Numbers<Integer> closeDelay = new Numbers<Integer>("Close Delay", "CloseDelay", 120, 0, 1000, 10);
    private final Option<Boolean> nameCheck = new Option<Boolean>("Name Check", "NameCheck", true);
    private final Option<Boolean> smart = new Option<Boolean>("Smart", "Smart", true);
    private final Option<Boolean> randomOrder = new Option<Boolean>("Random Order", "RandomOrder", true);
    private final Option<Boolean> autoClose = new Option<Boolean>("Auto Close", "AutoClose", true);

    private final TimerUtil clickTimer = new TimerUtil();
    private final TimerUtil closeTimer = new TimerUtil();

    private int activeWindowId = -1;
    private int instantExecutedWindowId = -1;
    private long nextClickDelay;

    public ChestStealer() {
        super("ChestStealer", Keyboard.KEY_NONE, ModuleType.Player, "Take useful items from chests");
        addValues(mode, clickDelay, delayJitter, closeDelay, nameCheck, smart, randomOrder, autoClose);
        Chinese = "自动拿箱";
    }

    @Override
    public void enable() {
        resetWindowState(-1);
    }

    @Override
    public void disable() {
        resetWindowState(-1);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (!isInGame()) {
            resetWindowState(-1);
            return;
        }
        if (!(mc.currentScreen instanceof GuiChest)) {
            resetWindowState(-1);
            return;
        }

        GuiChest chest = (GuiChest) mc.currentScreen;
        if (!(chest.inventorySlots instanceof ContainerChest) || mc.playerController == null) {
            return;
        }
        int windowId = chest.inventorySlots.windowId;
        IInventory inventory = ((ContainerChest) chest.inventorySlots).getLowerChestInventory();
        String title = chestTitle(inventory);
        if (activeWindowId != windowId) {
            resetWindowState(windowId);
        }

        if (!isAllowedChest(title)) {
            return;
        }
        if (currentMode() == ChestMode.INSTANT && instantExecutedWindowId != windowId) {
            instantExecutedWindowId = windowId;
            stealInstant(windowId, inventory);
            return;
        }
        int slot = nextSlot(inventory);
        if (slot < 0) {
            if (Boolean.TRUE.equals(autoClose.getValue()) && closeTimer.hasReached(closeDelay.getValue())) {
                mc.thePlayer.closeScreen();
                resetWindowState(-1);
            }
            return;
        }

        closeTimer.reset();
        if (!clickTimer.hasReached(nextClickDelay)) {
            return;
        }
        mc.playerController.windowClick(windowId, slot, 0, 1, mc.thePlayer);
        clickTimer.reset();
        scheduleNextClick();
    }

    private void resetWindowState(int windowId) {
        activeWindowId = windowId;
        instantExecutedWindowId = -1;
        clickTimer.reset();
        closeTimer.reset();
        scheduleNextClick();
    }

    private ChestMode currentMode() {
        return mode.getValue() == null ? ChestMode.NORMAL : mode.getValue();
    }

    private void stealInstant(int windowId, IInventory inventory) {
        for (int click = 0; click < MAX_INSTANT_CLICKS; click++) {
            if (!(mc.currentScreen instanceof GuiChest)
                    || mc.playerController == null
                    || mc.thePlayer == null
                    || mc.thePlayer.openContainer == null
                    || mc.thePlayer.openContainer.windowId != windowId) {
                return;
            }
            int slot = nextSlot(inventory);
            if (slot < 0) {
                if (Boolean.TRUE.equals(autoClose.getValue())) {
                    mc.thePlayer.closeScreen();
                    resetWindowState(-1);
                }
                return;
            }
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || !canTransfer(stack) || !isValid(stack)) {
                return;
            }
            mc.playerController.windowClick(windowId, slot, 0, 1, mc.thePlayer);
        }
    }

    private void scheduleNextClick() {
        nextClickDelay = ChestStealerPolicy.nextDelay(clickDelay.getValue(), delayJitter.getValue(),
                ThreadLocalRandom.current().nextDouble());
    }

    private String chestTitle(IInventory inventory) {
        if (inventory == null || inventory.getDisplayName() == null) {
            return null;
        }
        return inventory.getDisplayName().getUnformattedText();
    }

    private boolean isAllowedChest(String title) {
        return ServerContainerPolicy.canStealFrom(title, !Boolean.TRUE.equals(nameCheck.getValue()));
    }

    private int nextSlot(IInventory inventory) {
        int slotCount = inventory.getSizeInventory();
        if (slotCount <= 0) {
            return -1;
        }
        int start = Boolean.TRUE.equals(randomOrder.getValue())
                ? ThreadLocalRandom.current().nextInt(slotCount)
                : 0;
        for (int step = 0; step < slotCount; step++) {
            int slot = ChestStealerPolicy.slotAt(step, start, slotCount);
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null && canTransfer(stack) && isValid(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean canTransfer(ItemStack candidate) {
        boolean hasEmptySlot = false;
        boolean hasCompatiblePartialStack = false;
        for (ItemStack owned : mc.thePlayer.inventory.mainInventory) {
            if (owned == null) {
                hasEmptySlot = true;
                continue;
            }
            if (owned.stackSize < owned.getMaxStackSize()
                    && owned.isItemEqual(candidate)
                    && ItemStack.areItemStackTagsEqual(owned, candidate)) {
                hasCompatiblePartialStack = true;
            }
        }
        return ChestStealerPolicy.canTransfer(hasEmptySlot, hasCompatiblePartialStack);
    }

    private boolean isValid(ItemStack stack) {
        if (!Boolean.TRUE.equals(smart.getValue())) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof ItemSword || item instanceof ItemBow
                || item instanceof ItemPickaxe || item instanceof ItemAxe || item instanceof ItemSpade
                || item instanceof ItemArmor) {
            return isEquipmentUpgrade(stack);
        }
        if (item instanceof ItemPotion) {
            return isUsefulPotion(stack, (ItemPotion) item);
        }
        return item instanceof ItemBlock
                || item instanceof ItemFood
                || item == Items.arrow
                || item == Items.ender_pearl
                || item == Items.golden_apple
                || item == Items.water_bucket
                || item == Items.lava_bucket;
    }

    private boolean isUsefulPotion(ItemStack stack, ItemPotion potionItem) {
        List<PotionEffect> effects = potionItem.getEffects(stack);
        if (effects == null || effects.isEmpty()) {
            return false;
        }
        boolean hasUsefulEffect = false;
        boolean hasHarmfulEffect = false;
        for (PotionEffect effect : effects) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion == null) {
                continue;
            }
            if (potion.isBadEffect()) {
                hasHarmfulEffect = true;
            } else {
                hasUsefulEffect = true;
            }
        }
        return ChestStealerPolicy.shouldTakePotion(hasUsefulEffect, hasHarmfulEffect);
    }

    private boolean isEquipmentUpgrade(ItemStack candidate) {
        float candidateScore = equipmentScore(candidate);
        float ownedScore = Float.NEGATIVE_INFINITY;
        boolean hasOwnedItem = false;
        for (ItemStack owned : mc.thePlayer.inventory.mainInventory) {
            if (owned == null || !sameEquipmentType(candidate, owned)) {
                continue;
            }
            hasOwnedItem = true;
            ownedScore = Math.max(ownedScore, equipmentScore(owned));
        }
        for (ItemStack owned : mc.thePlayer.inventory.armorInventory) {
            if (owned == null || !sameEquipmentType(candidate, owned)) {
                continue;
            }
            hasOwnedItem = true;
            ownedScore = Math.max(ownedScore, equipmentScore(owned));
        }
        return ChestStealerPolicy.shouldTakeEquipment(candidateScore, ownedScore, hasOwnedItem);
    }

    private boolean sameEquipmentType(ItemStack first, ItemStack second) {
        Item firstItem = first.getItem();
        Item secondItem = second.getItem();
        if (firstItem instanceof ItemArmor && secondItem instanceof ItemArmor) {
            return ((ItemArmor) firstItem).armorType == ((ItemArmor) secondItem).armorType;
        }
        return firstItem instanceof ItemSword && secondItem instanceof ItemSword
                || firstItem instanceof ItemBow && secondItem instanceof ItemBow
                || firstItem instanceof ItemPickaxe && secondItem instanceof ItemPickaxe
                || firstItem instanceof ItemAxe && secondItem instanceof ItemAxe
                || firstItem instanceof ItemSpade && secondItem instanceof ItemSpade;
    }

    private float equipmentScore(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemSword) {
            return ((ItemSword) item).getDamageVsEntity()
                    + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25F;
        }
        if (item instanceof ItemBow) {
            return 1.0F + EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
        }
        if (item instanceof ItemArmor) {
            return ((ItemArmor) item).damageReduceAmount
                    + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 0.75F;
        }
        float baseEfficiency;
        if (item instanceof ItemPickaxe) {
            baseEfficiency = ((ItemPickaxe) item).getToolMaterial().getEfficiencyOnProperMaterial();
        } else if (item instanceof ItemAxe) {
            baseEfficiency = ((ItemAxe) item).getToolMaterial().getEfficiencyOnProperMaterial();
        } else if (item instanceof ItemSpade) {
            baseEfficiency = ((ItemSpade) item).getToolMaterial().getEfficiencyOnProperMaterial();
        } else {
            return 0.0F;
        }
        int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
        return InventorySelection.toolScore(baseEfficiency, efficiency);
    }
}
