package gq.yozakura.module.combat;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

public class AutoClicker extends Module {
    private final Numbers<Double> minCps = new Numbers<Double>("Min CPS", "MinCPS", 6.0D, 1.0D, 20.0D, 1.0D);
    private final Numbers<Double> maxCps = new Numbers<Double>("Max CPS", "MaxCPS", 13.0D, 1.0D, 20.0D, 1.0D);
    private final Mode<AutoClickRandomization> randomization =
            new Mode<AutoClickRandomization>("Randomization", "Randomization",
                    AutoClickRandomization.values(), AutoClickRandomization.EXTRA);
    private final Option<Boolean> holdToClick = new Option<Boolean>("Hold to Click", "HoldToClick", true);
    private final Option<Boolean> triggerMode = new Option<Boolean>("Trigger Mode", "TriggerMode", false);
    private final Option<Boolean> jitter = new Option<Boolean>("Jitter", "Jitter", false);
    private final Option<Boolean> notUsingItem =
            new Option<Boolean>("Pause While Using", "NotUsingItem", false);
    private final Option<Boolean> breakBlocks = new Option<Boolean>("Break Blocks", "BreakBlocks", false);
    private final Numbers<Double> breakBlocksMinDelay =
            new Numbers<Double>("Break Blocks Min Delay (ms)", "BreakBlocksMinDelay",
                    0.0D, 0.0D, 2000.0D, 10.0D);
    private final Numbers<Double> breakBlocksMaxDelay =
            new Numbers<Double>("Break Blocks Max Delay (ms)", "BreakBlocksMaxDelay",
                    10.0D, 0.0D, 2000.0D, 10.0D);
    private final Option<Boolean> breakBlocksToolsOnly =
            new Option<Boolean>("Break Blocks Whitelist", "BreakBlocksWhitelist", false);
    private final Option<Boolean> breakWithPickaxes =
            new Option<Boolean>("Break With Pickaxes", "BreakWithPickaxes", true);
    private final Option<Boolean> breakWithShovels =
            new Option<Boolean>("Break With Shovels", "BreakWithShovels", true);
    private final Option<Boolean> breakWithAxes =
            new Option<Boolean>("Break With Axes", "BreakWithAxes", false);
    private final Option<Boolean> limitItems = new Option<Boolean>("Limit Items", "LimitItems", false);
    private final Option<Boolean> allowSwords = new Option<Boolean>("Allow Swords", "AllowSwords", true);
    private final Option<Boolean> allowAxes = new Option<Boolean>("Allow Axes", "AllowAxes", false);
    private final Option<Boolean> allowPickaxes = new Option<Boolean>("Allow Pickaxes", "AllowPickaxes", false);
    private final Option<Boolean> allowShovels = new Option<Boolean>("Allow Shovels", "AllowShovels", false);
    private final Option<Boolean> allowOtherItems =
            new Option<Boolean>("Allow Other Items", "AllowOtherItems", false);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Weapon Only", "WeaponOnly", false);
    private final Option<Boolean> disableCreative =
            new Option<Boolean>("Disable In Creative", "DisableCreative", false);
    private final Option<Boolean> inventory = new Option<Boolean>("Inventory Clicking", "Inventory", false);
    private final Numbers<Double> inventoryStartDelay =
            new Numbers<Double>("Inventory Start Delay (ms)", "InventoryStartDelay",
                    100.0D, 0.0D, 250.0D, 10.0D);

    private static Field hoveredSlotField;

    private final AutoClickController clickController = new AutoClickController();
    private final VapeAutoClickTimingState inventoryTimingState =
            new VapeAutoClickTimingState(System.nanoTime() ^ 0x6A09E667F3BCC909L);
    private final VapeAutoClickJitter clickJitter =
            new VapeAutoClickJitter(System.nanoTime() ^ 0xBB67AE8584CAA73BL);
    private final VapeBlockBreakPolicy blockBreakPolicy =
            new VapeBlockBreakPolicy(System.nanoTime() ^ 0x3C6EF372FE94F82BL);
    private long inventoryNextClickTime;
    private int lastAttackTick = Integer.MIN_VALUE;

    public AutoClicker() {
        super("AutoClicker", Keyboard.KEY_K, ModuleType.Combat,
                "Vape-style automatic clicking with hold and trigger activation modes");
        addValues(holdToClick, triggerMode, minCps, maxCps, randomization, jitter,
                limitItems, allowSwords, allowAxes, allowPickaxes, allowShovels, allowOtherItems,
                breakBlocks, breakBlocksMinDelay, breakBlocksMaxDelay, breakBlocksToolsOnly,
                breakWithPickaxes, breakWithShovels, breakWithAxes, notUsingItem, weaponOnly,
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
        if (event.phase == TickEvent.Phase.START) {
            applyJitterRotation();
            handleAttackClick();
        }
        if (event.phase == TickEvent.Phase.END) {
            handleInventoryClick();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (Boolean.TRUE.equals(jitter.getValue())) {
                clickJitter.advance();
            }
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
        boolean active = AutoClickActivationPolicy.isActive(
                Boolean.TRUE.equals(holdToClick.getValue()), leftButtonDown);
        if (!active || KillAura.target != null) {
            clickController.reset();
            blockBreakPolicy.reset();
            return;
        }

        long now = monotonicTimeMillis();
        Entity hovered = hoveredEntity();
        boolean validTarget = AutoClickActivationPolicy.hasValidTarget(
                Boolean.TRUE.equals(triggerMode.getValue()), hovered != null);
        boolean blockPaused = blockBreakPolicy.shouldPause(now, leftButtonDown,
                Boolean.TRUE.equals(breakBlocks.getValue()),
                Boolean.TRUE.equals(breakBlocksToolsOnly.getValue()), isHoldingBreakTool(),
                isPointingAtBlock(), mc.currentScreen instanceof GuiContainer,
                breakBlocksMinDelay.getValue(), breakBlocksMaxDelay.getValue());
        boolean allowed = canClick() && validTarget && !blockPaused;
        AutoClickRandomization mode = randomization.getValue() == null
                ? AutoClickRandomization.EXTRA
                : randomization.getValue();
        if (clickController.shouldClick(now, active, allowed,
                minCps.getValue(), maxCps.getValue(), mode) && performLeftClick()) {
            clickJitter.generate();
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
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return false;
        }
        return !Boolean.TRUE.equals(limitItems.getValue()) || isHeldItemAllowed();
    }

    private boolean performLeftClick() {
        Backtrack.applyBacktrackHit();
        Entity target = hoveredEntity();
        if (target != null && (!HitSelect.shouldAttack(target) || !KnockbackDelay.shouldAttack(target))) {
            return false;
        }

        if (target != null) {
            Criticals.tryCritical(false);
        }

        MinecraftAccessor.setLeftClickCounter(mc, 0);
        if (!MinecraftAccessor.clickMouse(mc)) {
            clickController.reset();
            return false;
        }

        if (target != null) {
            HitSelect.onAttack(target);
            WTap.onAttack(target);
        }
        return true;
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
        AutoClickRandomization clickMode = randomization.getValue() == null
                ? AutoClickRandomization.EXTRA
                : randomization.getValue();
        inventoryNextClickTime = now + inventoryTimingState.nextDelay(
                now, minCps.getValue(), maxCps.getValue(), clickMode);
    }

    private void applyJitterRotation() {
        if (!Boolean.TRUE.equals(jitter.getValue()) || !isInGame() || mc.currentScreen != null) {
            return;
        }
        int yawDelta = clickJitter.yawDelta();
        int pitchDelta = clickJitter.pitchDelta();
        if (yawDelta == 0 && pitchDelta == 0) {
            return;
        }
        float sensitivity = mc.gameSettings == null ? 0.5F : mc.gameSettings.mouseSensitivity;
        float sensitivityFactor = sensitivity * 0.6F + 0.2F;
        float mouseScale = sensitivityFactor * sensitivityFactor * sensitivityFactor * 8.0F;
        mc.thePlayer.setAngles(yawDelta * mouseScale, pitchDelta * mouseScale);
        clickJitter.applied();
    }

    private boolean isHeldItemAllowed() {
        ItemStack stack = mc.thePlayer == null ? null : mc.thePlayer.getCurrentEquippedItem();
        Item item = stack == null ? null : stack.getItem();
        if (item instanceof ItemSword) {
            return Boolean.TRUE.equals(allowSwords.getValue());
        }
        if (item instanceof ItemAxe) {
            return Boolean.TRUE.equals(allowAxes.getValue());
        }
        if (item instanceof ItemPickaxe) {
            return Boolean.TRUE.equals(allowPickaxes.getValue());
        }
        if (item instanceof ItemSpade) {
            return Boolean.TRUE.equals(allowShovels.getValue());
        }
        return Boolean.TRUE.equals(allowOtherItems.getValue());
    }

    private boolean isHoldingBreakTool() {
        ItemStack stack = mc.thePlayer == null ? null : mc.thePlayer.getCurrentEquippedItem();
        Item item = stack == null ? null : stack.getItem();
        return item instanceof ItemPickaxe && Boolean.TRUE.equals(breakWithPickaxes.getValue())
                || item instanceof ItemSpade && Boolean.TRUE.equals(breakWithShovels.getValue())
                || item instanceof ItemAxe && Boolean.TRUE.equals(breakWithAxes.getValue());
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
        clickJitter.reset();
        blockBreakPolicy.reset();
        resetInventoryClickState();
        lastAttackTick = Integer.MIN_VALUE;
    }

    private void resetInventoryClickState() {
        inventoryNextClickTime = 0L;
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
