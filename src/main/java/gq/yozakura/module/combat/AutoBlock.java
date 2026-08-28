package gq.yozakura.module.combat;

import gq.yozakura.core.Client;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.module.KeyBindUtil;
import gq.yozakura.value.Numbers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Starts a real vanilla sword-use cycle while the crosshair points at a nearby
 * living entity. Manual use input and other combat use owners take priority.
 */
public class AutoBlock extends Module {
    private final Numbers<Double> chance =
            new Numbers<Double>("Chance", "Chance", 100.0D, 0.0D, 100.0D, 1.0D);
    private final Numbers<Double> distance =
            new Numbers<Double>("Distance", "Distance", 4.0D, 1.0D, 6.0D, 0.1D);
    private final Numbers<Double> duration =
            new Numbers<Double>("Duration (ms)", "Duration", 100.0D, 0.0D, 1000.0D, 10.0D);
    private final AutoBlockController controller = new AutoBlockController();
    private int lastHandledTick = Integer.MIN_VALUE;

    public AutoBlock() {
        super("AutoBlock", Keyboard.KEY_NONE, ModuleType.Combat,
                "Automatically use a sword while aiming at a nearby entity");
        addValues(chance, distance, duration);
        Chinese = "自动格挡";
    }

    @Override
    public void enable() {
        releaseOwnedUse(controller.reset());
        lastHandledTick = Integer.MIN_VALUE;
        Client.AutoBlock = true;
    }

    @Override
    public void disable() {
        Client.AutoBlock = false;
        releaseOwnedUse(controller.reset());
        lastHandledTick = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            updateOncePerGameTick();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            updateOncePerGameTick();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!getState() || event == null || event.isCancelled()
                || !isValidTarget(pointedEntity())) {
            return;
        }
        releaseForAttack();
    }

    public void releaseForAttack() {
        releaseOwnedUse(controller.releaseForAttack());
    }

    private void updateOncePerGameTick() {
        if (isInGame()) {
            int tick = mc.thePlayer.ticksExisted;
            if (tick == lastHandledTick) {
                return;
            }
            lastHandledTick = tick;
        } else {
            lastHandledTick = Integer.MIN_VALUE;
        }
        updateAutoBlock();
    }

    private void updateAutoBlock() {
        boolean gameplayReady = isGameplayReady();
        boolean externalUseOwner = gameplayReady && hasExternalUseOwner();
        Entity target = pointedEntity();
        boolean pointingAtEntity = isValidTarget(target);
        double targetDistance = pointingAtEntity
                ? mc.thePlayer.getDistanceToEntity(target)
                : Double.POSITIVE_INFINITY;
        double chancePercent = clamp(chance.getValue(), 0.0D, 100.0D);
        double randomPercent = chancePercent > 0.0D && chancePercent < 100.0D
                ? ThreadLocalRandom.current().nextDouble(0.0D, 100.0D)
                : 0.0D;

        apply(controller.update(
                monotonicTimeMillis(),
                gameplayReady,
                externalUseOwner,
                pointingAtEntity,
                targetDistance,
                Math.max(0.0D, distance.getValue()),
                chancePercent,
                Math.max(0L, duration.getValue().longValue()),
                randomPercent));
    }

    public boolean isBlocking() {
        return controller.isBlocking();
    }

    private Entity pointedEntity() {
        if (!isInGame()) {
            return null;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit == null ? null : hit.entityHit;
    }

    private boolean isValidTarget(Entity target) {
        if (!(target instanceof EntityLivingBase) || target == mc.thePlayer) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) target;
        return !living.isDead && living.getHealth() > 0.0F;
    }

    private boolean isGameplayReady() {
        if (!isInGame() || mc.playerController == null || mc.currentScreen != null
                || !mc.inGameHasFocus || mc.thePlayer.isDead) {
            return false;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        return heldItem != null && heldItem.getItem() instanceof ItemSword;
    }

    private boolean hasExternalUseOwner() {
        if (mc.gameSettings != null && KeyBindUtil.isBindingDown(mc.gameSettings.keyBindUseItem)) {
            return true;
        }
        if (!controller.isBlocking() && mc.thePlayer.isUsingItem()) {
            return true;
        }
        if (BlockHit.isBlockingActive()) {
            return true;
        }
        Module module = ModuleManager.getModule("KillAura");
        if (!(module instanceof KillAura) || !module.getState()) {
            return false;
        }
        KillAura aura = (KillAura) module;
        return aura.isPlayerBlocking() || aura.isBlocking();
    }

    private void apply(AutoBlockController.Action action) {
        if (action == AutoBlockController.Action.PRESS) {
            startOwnedUse();
        } else if (action == AutoBlockController.Action.RELEASE) {
            stopOwnedUse();
        }
    }

    private void startOwnedUse() {
        if (!isGameplayReady()) {
            controller.pressFailed();
            return;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (!mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, heldItem)) {
            controller.pressFailed();
        }
    }

    private void stopOwnedUse() {
        if (isInGame() && mc.playerController != null) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
        }
    }

    private void releaseOwnedUse(AutoBlockController.Action action) {
        if (action == AutoBlockController.Action.RELEASE) {
            stopOwnedUse();
        }
    }

    private static long monotonicTimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
