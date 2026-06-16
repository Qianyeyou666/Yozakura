package gq.yozakura.module.combat;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Random;

public class AutoClicker extends Module {
    private final Numbers<Double> targetCps = new Numbers<Double>("Target CPS", "TargetCPS", 10.0, 1.0, 20.0, 0.5);
    private final Option<Boolean> simulateExhaust = new Option<Boolean>("Simulate exhaust", "SimulateExhaust", true);
    private final Option<Boolean> notUsingItem = new Option<Boolean>("Not using item", "NotUsingItem", false);
    private final Option<Boolean> breakBlocks = new Option<Boolean>("Break blocks", "BreakBlocks", false);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon only", "WeaponOnly", false);
    private final Option<Boolean> disableCreative = new Option<Boolean>("Disable in creative", "DisableCreative", false);
    private final Option<Boolean> inventory = new Option<Boolean>("Inventory", "Inventory", false);
    private final Numbers<Double> inventoryStartDelay =
            new Numbers<Double>("Start delay", "InventoryStartDelay", 100.0, 0.0, 250.0, 10.0);

    private static Field hoveredSlotField;

    private final Random random = new Random();
    private long nextClickTime;
    private long inventoryNextClickTime;
    private int lastAttackTick = Integer.MIN_VALUE;
    private boolean holdingBlockBreak;

    public AutoClicker() {
        super("AutoClicker", Keyboard.KEY_K, ModuleType.Combat, "Click automatically while attack is held");
        this.addValues(targetCps, simulateExhaust, notUsingItem, breakBlocks, weaponOnly,
                disableCreative, inventory, inventoryStartDelay);
        Chinese = "连点器";
    }

    @Override
    public void enable() {
        this.nextClickTime = 0L;
        this.inventoryNextClickTime = 0L;
        this.lastAttackTick = Integer.MIN_VALUE;
        this.holdingBlockBreak = false;
        ensureHoveredSlotField();
    }

    @Override
    public void disable() {
        this.nextClickTime = 0L;
        this.inventoryNextClickTime = 0L;
        this.lastAttackTick = Integer.MIN_VALUE;
        this.holdingBlockBreak = false;
        restoreAttackKey();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        handleInventoryClick();
    }

    @EventTarget
    public void onBridgeTick(gq.yozakura.event.bridge.TickEvent event) {
        if (event.getType() == EventType.PRE) {
            handleAttackClickOnce();
        } else if (event.getType() == EventType.POST) {
            handleInventoryClick();
        }
    }

    private void handleInventoryClick() {
        if (!Boolean.TRUE.equals(inventory.getValue()) || !isInGame()) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiContainer)) {
            this.inventoryNextClickTime = 0L;
            return;
        }
        if (!isMouseButtonDown(0)) {
            this.inventoryNextClickTime = 0L;
            return;
        }

        ensureHoveredSlotField();
        if (hoveredSlotField == null || mc.playerController == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (this.inventoryNextClickTime == 0L) {
            this.inventoryNextClickTime = now + Math.max(0L, inventoryStartDelay.getValue().longValue());
        }

        int clicks = 0;
        while (this.inventoryNextClickTime <= now) {
            clicks++;
            this.inventoryNextClickTime += nextDelay();
        }
        if (clicks <= 0) {
            return;
        }

        GuiContainer gui = (GuiContainer) mc.currentScreen;
        Slot slot = getHoveredSlot(gui);
        if (slot == null || slot.slotNumber < 0) {
            return;
        }

        int mode = GuiScreen.isShiftKeyDown() ? 1 : 0;
        for (int i = 0; i < clicks; i++) {
            mc.playerController.windowClick(gui.inventorySlots.windowId, slot.slotNumber, 0, mode, mc.thePlayer);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        handleAttackClickOnce();
    }

    private void handleAttackClickOnce() {
        if (!isInGame()) {
            handleAttackClick();
            return;
        }
        int tick = mc.thePlayer.ticksExisted;
        if (this.lastAttackTick == tick) {
            return;
        }
        this.lastAttackTick = tick;
        handleAttackClick();
    }

    private void handleAttackClick() {
        if (!isInGame()) {
            releaseBlockBreak();
            return;
        }
        if (KillAura.target != null) {
            releaseBlockBreak();
            return;
        }
        if (!isAttackHeld()) {
            this.nextClickTime = 0L;
            releaseBlockBreak();
            restoreAttackKey();
            return;
        }

        long now = System.currentTimeMillis();
        if (this.nextClickTime == 0L) {
            this.nextClickTime = now + nextDelay();
        }

        int clicks = 0;
        while (this.nextClickTime <= now) {
            clicks++;
            this.nextClickTime += nextDelay();
        }

        if (!canClick()) {
            return;
        }
        if (handleBlockBreaking()) {
            return;
        }
        if (clicks <= 0) {
            return;
        }

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        Backtrack.applyBacktrackHit();
        KeyBinding.setKeyBindState(key, true);
        for (int i = 0; i < clicks; i++) {
            MinecraftAccessor.setLeftClickCounter(mc, 0);
            KeyBinding.onTick(key);
            if (!MinecraftAccessor.clickMouse(mc)) {
                attackHoveredEntity();
            }
        }
    }

    private boolean canClick() {
        if (mc.currentScreen != null || (!mc.inGameHasFocus && !isAttackHeld())) {
            return false;
        }
        if (Boolean.TRUE.equals(notUsingItem.getValue()) && mc.thePlayer.isUsingItem()) {
            return false;
        }
        if (Boolean.TRUE.equals(disableCreative.getValue()) && mc.thePlayer.capabilities.isCreativeMode) {
            return false;
        }
        return !Boolean.TRUE.equals(weaponOnly.getValue()) || CombatUtil.isHoldingWeapon();
    }

    private void attackHoveredEntity() {
        if (mc.objectMouseOver == null) {
            return;
        }
        Entity entity = mc.objectMouseOver.entityHit;
        if (entity == null) {
            return;
        }
        if (!HitSelect.shouldAttack(entity) || !KnockbackDelay.shouldAttack(entity)) {
            return;
        }
        Criticals.tryCritical(false);
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, entity);
        HitSelect.onAttack(entity);
        BlockHit.onAttack(entity);
        WTap.onAttack(entity);
    }

    private boolean handleBlockBreaking() {
        if (!Boolean.TRUE.equals(breakBlocks.getValue())) {
            return releaseBlockBreak();
        }
        if (!mc.thePlayer.capabilities.allowEdit) {
            return releaseBlockBreak();
        }
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return releaseBlockBreak();
        }

        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) {
            return releaseBlockBreak();
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.air || block instanceof BlockLiquid) {
            return releaseBlockBreak();
        }

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        this.holdingBlockBreak = true;
        return true;
    }

    private boolean releaseBlockBreak() {
        if (!this.holdingBlockBreak) {
            return false;
        }
        this.holdingBlockBreak = false;
        restoreAttackKey();
        return true;
    }

    private long nextDelay() {
        int target = Math.max(1, targetCps.getValue().intValue());
        int baseDelay = Math.max(1, 1000 / target);
        int delay;
        if (Boolean.TRUE.equals(simulateExhaust.getValue())) {
            delay = baseDelay + random.nextInt(baseDelay + 1) - baseDelay / 2;
            if (random.nextInt(100) < 15) {
                delay = random.nextBoolean()
                        ? 25 + random.nextInt(16)
                        : baseDelay + 50 + random.nextInt(41);
            }
            if (random.nextInt(100) < 8) {
                delay = delay * (50 + random.nextInt(151)) / 100;
            }
            if (random.nextInt(100) < 10) {
                delay += 10 + random.nextInt(26);
            }
        } else {
            delay = baseDelay + random.nextInt(21) - 10;
        }
        return Math.max(33L, Math.min(180L, delay));
    }

    private boolean isAttackHeld() {
        return mc.gameSettings != null
                && KeyBindUtil.isBindingDown(mc.gameSettings.keyBindAttack);
    }

    private void restoreAttackKey() {
        if (mc.gameSettings == null || mc.gameSettings.keyBindAttack == null) {
            return;
        }
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, KeyBindUtil.isKeyDown(key));
    }

    private static boolean isMouseButtonDown(int button) {
        try {
            return Mouse.isCreated() && Mouse.isButtonDown(button);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureHoveredSlotField() {
        if (hoveredSlotField != null) {
            return;
        }
        for (String name : new String[]{"theSlot", "field_147006_u"}) {
            try {
                hoveredSlotField = GuiContainer.class.getDeclaredField(name);
                hoveredSlotField.setAccessible(true);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static Slot getHoveredSlot(GuiContainer gui) {
        if (hoveredSlotField == null || gui == null) {
            return null;
        }
        try {
            Object value = hoveredSlotField.get(gui);
            return value instanceof Slot ? (Slot) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
