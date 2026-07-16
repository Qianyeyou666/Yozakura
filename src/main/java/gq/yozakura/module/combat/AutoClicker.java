package gq.yozakura.module.combat;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Slot;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

public class AutoClicker extends Module {
    private final Numbers<Double> minCps = new Numbers<Double>("Min CPS", "MinCPS", 8.0D, 1.0D, 20.0D, 0.5D);
    private final Numbers<Double> maxCps = new Numbers<Double>("Max CPS", "MaxCPS", 12.0D, 1.0D, 20.0D, 0.5D);
    private final Option<Boolean> smoothRhythm =
            new Option<Boolean>("Smooth Rhythm", "SimulateExhaust", true);
    private final Option<Boolean> notUsingItem =
            new Option<Boolean>("Pause While Using", "NotUsingItem", true);
    private final Option<Boolean> breakBlocks = new Option<Boolean>("Break Blocks", "BreakBlocks", false);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon Only", "WeaponOnly", false);
    private final Option<Boolean> disableCreative =
            new Option<Boolean>("Disable In Creative", "DisableCreative", false);
    private final Option<Boolean> inventory = new Option<Boolean>("Inventory Clicking", "Inventory", false);
    private final Numbers<Double> inventoryStartDelay =
            new Numbers<Double>("Inventory Start Delay (ms)", "InventoryStartDelay",
                    100.0D, 0.0D, 250.0D, 10.0D);

    private static Field hoveredSlotField;

    private final AutoClickController clickController = new AutoClickController();
    private long inventoryNextClickTime;
    private int inventoryIntervalIndex;
    private int lastAttackTick = Integer.MIN_VALUE;

    public AutoClicker() {
        super("AutoClicker", Keyboard.KEY_K, ModuleType.Combat,
                "Click automatically while the physical left mouse button is held");
        addValues(minCps, maxCps, smoothRhythm, notUsingItem, breakBlocks, weaponOnly,
                disableCreative, inventory, inventoryStartDelay);
        Chinese = "连点器";
    }

    @Override
    public void enable() {
        resetState();
        ensureHoveredSlotField();
    }

    @Override
    public void disable() {
        resetState();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            handleInventoryClick();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            handleAttackClickOnce();
        }
    }

    @EventTarget
    public void onBridgeTick(gq.yozakura.event.bridge.TickEvent event) {
        if (event.getType() == EventType.POST) {
            handleInventoryClick();
        }
    }

    private void handleAttackClickOnce() {
        if (!isInGame()) {
            clickController.reset();
            lastAttackTick = Integer.MIN_VALUE;
            return;
        }
        int tick = mc.thePlayer.ticksExisted;
        if (lastAttackTick == tick) {
            return;
        }
        lastAttackTick = tick;
        handleAttackClick();
    }

    private void handleAttackClick() {
        boolean leftButtonDown = isMouseButtonDown(0);
        if (!leftButtonDown || KillAura.target != null) {
            clickController.reset();
            return;
        }

        boolean allowed = canClick();
        if (isPointingAtBlock() && !Boolean.TRUE.equals(breakBlocks.getValue())) {
            clickController.reset();
            return;
        }

        long now = monotonicTimeMillis();
        if (clickController.shouldClick(now, true, allowed,
                minCps.getValue(), maxCps.getValue(), Boolean.TRUE.equals(smoothRhythm.getValue()))) {
            performLeftClick();
        }
    }

    private boolean canClick() {
        if (!isInGame() || mc.currentScreen != null || !mc.inGameHasFocus) {
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

    private void performLeftClick() {
        Backtrack.applyBacktrackHit();
        Entity target = hoveredEntity();
        if (target != null && (!HitSelect.shouldAttack(target) || !KnockbackDelay.shouldAttack(target))) {
            return;
        }

        if (target != null) {
            Criticals.tryCritical(false);
        }

        MinecraftAccessor.setLeftClickCounter(mc, 0);
        if (!MinecraftAccessor.clickMouse(mc)) {
            clickController.reset();
            return;
        }

        if (target != null) {
            HitSelect.onAttack(target);
            WTap.onAttack(target);
        }
    }

    private void handleInventoryClick() {
        if (!Boolean.TRUE.equals(inventory.getValue()) || !isInGame()) {
            resetInventoryClickState();
            return;
        }
        if (!(mc.currentScreen instanceof GuiContainer) || !isMouseButtonDown(0)) {
            resetInventoryClickState();
            return;
        }

        ensureHoveredSlotField();
        if (hoveredSlotField == null || mc.playerController == null) {
            return;
        }

        long now = monotonicTimeMillis();
        if (inventoryNextClickTime == 0L) {
            inventoryNextClickTime = now + Math.max(0L, inventoryStartDelay.getValue().longValue());
        }
        if (inventoryNextClickTime > now) {
            return;
        }

        GuiContainer gui = (GuiContainer) mc.currentScreen;
        Slot slot = getHoveredSlot(gui);
        if (slot == null || slot.slotNumber < 0) {
            return;
        }

        int mode = GuiScreen.isShiftKeyDown() ? 1 : 0;
        mc.playerController.windowClick(gui.inventorySlots.windowId, slot.slotNumber, 0, mode, mc.thePlayer);
        inventoryNextClickTime = now + AutoClickController.calculateDelay(
                minCps.getValue(), maxCps.getValue(), Boolean.TRUE.equals(smoothRhythm.getValue()),
                inventoryIntervalIndex++
        );
    }

    private boolean isPointingAtBlock() {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private Entity hoveredEntity() {
        return mc.objectMouseOver == null ? null : mc.objectMouseOver.entityHit;
    }

    private void resetState() {
        clickController.reset();
        resetInventoryClickState();
        lastAttackTick = Integer.MIN_VALUE;
    }

    private void resetInventoryClickState() {
        inventoryNextClickTime = 0L;
        inventoryIntervalIndex = 0;
    }

    private static long monotonicTimeMillis() {
        return System.nanoTime() / 1_000_000L;
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
