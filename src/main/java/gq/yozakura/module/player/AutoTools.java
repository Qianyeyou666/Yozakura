package gq.yozakura.module.player;

import com.google.common.collect.Multimap;
import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.AttackEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

import java.util.Collection;

public class AutoTools extends Module {
    private static final int HOTBAR_SIZE = 9;

    private final Option<Boolean> restoreSlot =
            new Option<Boolean>("Restore Slot", "RestoreSlot", true);
    private final Option<Boolean> preserveTools =
            new Option<Boolean>("Preserve Tools", "PreserveTools", true);
    private final Numbers<Integer> minimumDurability =
            new Numbers<Integer>("Minimum Durability", "MinimumDurability", 10, 1, 100, 1);

    private final float[] scoreScratch = new float[HOTBAR_SIZE];
    private final boolean[] usableScratch = new boolean[HOTBAR_SIZE];
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private boolean ownsSlot;
    private boolean suspendedUntilRelease;

    public AutoTools() {
        super("AutoTools", Keyboard.KEY_I, ModuleType.Player,
                "Switch to the best hotbar tool or weapon for the current action");
        minimumDurability.visibleWhen(() -> Boolean.TRUE.equals(preserveTools.getValue()));
        this.addValues(restoreSlot, preserveTools, minimumDurability);
        Chinese = "自动工具";
    }

    @Override
    public void enable() {
        resetSession();
    }

    @Override
    public void disable() {
        restoreOwnedSlot();
        resetSession();
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!getState() || event == null || event.isCancelled() || !isAttackingEntity()) {
            return;
        }
        selectBestWeapon();
    }

    @EventTarget(Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (!getState() || event == null || event.isCancelled() || !isInGame()
                || mc.currentScreen != null || mc.playerController == null || event.getTarget() == null) {
            return;
        }
        selectBestWeapon();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!getState() || event.getType() != EventType.PRE) {
            return;
        }
        if (!isBreakingBlock()) {
            restoreOwnedSlot();
            resetSession();
            return;
        }
        if (suspendedUntilRelease) {
            return;
        }

        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
        Block block = mc.theWorld.getBlockState(blockPos).getBlock();
        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (AutoToolsPolicy.isManualOverride(ownsSlot, currentSlot, selectedSlot)) {
            clearOwnedSlot();
            suspendedUntilRelease = true;
            return;
        }
        int bestSlot = findBestToolSlot(block, currentSlot);
        if (bestSlot == currentSlot) {
            return;
        }

        if (!ownsSlot) {
            originalSlot = currentSlot;
        }
        switchTo(bestSlot);
        selectedSlot = bestSlot;
        ownsSlot = true;
    }

    private boolean isBreakingBlock() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.objectMouseOver.getBlockPos() != null;
    }

    private boolean isAttackingEntity() {
        return isInGame()
                && mc.currentScreen == null
                && mc.playerController != null
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit != null;
    }

    private void selectBestWeapon() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (AutoToolsPolicy.isManualOverride(ownsSlot, currentSlot, selectedSlot)) {
            clearOwnedSlot();
            suspendedUntilRelease = true;
            return;
        }
        if (suspendedUntilRelease) {
            return;
        }
        int bestSlot = findBestWeaponSlot(currentSlot);
        if (bestSlot == currentSlot) {
            return;
        }
        if (!ownsSlot) {
            originalSlot = currentSlot;
        }
        switchTo(bestSlot);
        selectedSlot = bestSlot;
        ownsSlot = true;
    }

    private int findBestWeaponSlot(int currentSlot) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            boolean lowDurability = stack != null && isLowDurability(stack);
            scoreScratch[slot] = stack == null ? 0.0F : weaponScore(stack);
            usableScratch[slot] = !lowDurability || slot == currentSlot;
        }
        return AutoToolsPolicy.bestSlot(currentSlot, scoreScratch, usableScratch);
    }

    private float weaponScore(ItemStack stack) {
        float attackDamage = 0.0F;
        Multimap<String, AttributeModifier> modifiers = stack.getAttributeModifiers();
        Collection<AttributeModifier> damageModifiers = modifiers.get("generic.attackDamage");
        for (AttributeModifier modifier : damageModifiers) {
            attackDamage += (float) modifier.getAmount();
        }
        int sharpnessLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
        return AutoToolsPolicy.combatScore(attackDamage, sharpnessLevel);
    }

    private int findBestToolSlot(Block block, int currentSlot) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null) {
                scoreScratch[slot] = 1.0F;
                usableScratch[slot] = slot == currentSlot;
                continue;
            }
            float strength = stack.getStrVsBlock(block);
            int efficiencyLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            boolean lowDurability = isLowDurability(stack);
            scoreScratch[slot] = AutoToolsPolicy.score(strength, efficiencyLevel, lowDurability);
            usableScratch[slot] = !lowDurability || slot == currentSlot;
        }
        return AutoToolsPolicy.bestSlot(currentSlot, scoreScratch, usableScratch);
    }

    private boolean isLowDurability(ItemStack stack) {
        if (!Boolean.TRUE.equals(preserveTools.getValue()) || !stack.isItemStackDamageable()) {
            return false;
        }
        int remaining = stack.getMaxDamage() - stack.getItemDamage();
        return remaining <= Math.max(1, minimumDurability.getValue());
    }

    private void restoreOwnedSlot() {
        if (!isInGame()) {
            return;
        }
        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (AutoToolsPolicy.shouldRestore(Boolean.TRUE.equals(restoreSlot.getValue()), ownsSlot,
                currentSlot, selectedSlot, originalSlot)) {
            switchTo(originalSlot);
        }
    }

    private void switchTo(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE || mc.thePlayer.inventory.currentItem == slot) {
            return;
        }
        mc.thePlayer.inventory.currentItem = slot;
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
    }

    private void clearOwnedSlot() {
        originalSlot = -1;
        selectedSlot = -1;
        ownsSlot = false;
    }

    private void resetSession() {
        clearOwnedSlot();
        suspendedUntilRelease = false;
    }
}
