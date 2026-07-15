package gq.yozakura.module.combat;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.SprintUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class WTap extends Module {
    public enum WTapMode {
        SILENT,
        NORMAL
    }

    private static WTap INSTANCE;

    private final Mode<WTapMode> mode = new Mode<WTapMode>("Mode", "Mode", WTapMode.values(), WTapMode.SILENT);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> waitTicks = new Numbers<Double>("Wait Ticks", "WaitTicks", 0.0, 0.0, 5.0, 1.0);
    private final Numbers<Double> actionTicks = new Numbers<Double>("Action Ticks", "ActionTicks", 1.0, 1.0, 5.0, 1.0);
    private final Numbers<Double> jitterTicks = new Numbers<Double>("Jitter Ticks", "JitterTicks", 1.0, 0.0, 4.0, 1.0);
    private final Option<Boolean> onlyPlayers = new Option<Boolean>("Only Players", "OnlyPlayers", false);
    private int phase;
    private int ticksRemaining;

    public WTap() {
        super("WTap", Keyboard.KEY_NONE, ModuleType.Combat, "Reset sprint after landing attacks");
        this.addValues(mode, chance, waitTicks, actionTicks, jitterTicks, onlyPlayers);
        Chinese = "自动急停";
        INSTANCE = this;
    }

    @Override
    public void disable() {
        restoreForwardKey();
        phase = 0;
        ticksRemaining = 0;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button != 0 || !event.buttonstate || !isInGame() || mc.objectMouseOver == null) {
            return;
        }
        Entity entity = mc.objectMouseOver.entityHit;
        if (entity != null) {
            start(entity);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            return;
        }
        if (phase == 0) {
            return;
        }
        if (!isForwardHeld()) {
            phase = 0;
            return;
        }
        ticksRemaining--;
        if (ticksRemaining > 0) {
            return;
        }
        if (phase == 1) {
            stopSprint();
            phase = 2;
            ticksRemaining = randomTicks(actionTicks.getValue().intValue(), jitterTicks.getValue().intValue());
        } else {
            restoreForwardKey();
            phase = 0;
        }
    }

    public static void onAttack(Entity entity) {
        if (INSTANCE != null && INSTANCE.getState()) {
            INSTANCE.start(entity);
        }
    }

    private void start(Entity entity) {
        if (!isInGame() || phase != 0 || entity == null) {
            return;
        }
        if (Boolean.TRUE.equals(onlyPlayers.getValue()) && !(entity instanceof EntityPlayer)) {
            return;
        }
        if (!isForwardHeld() || ThreadLocalRandom.current().nextDouble(100.0D) > chance.getValue()) {
            return;
        }
        phase = 1;
        ticksRemaining = Math.max(1, randomTicks(waitTicks.getValue().intValue(), jitterTicks.getValue().intValue()));
    }

    private void stopSprint() {
        if (mode.getValue() == WTapMode.NORMAL) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        } else {
            SprintUtil.setSprinting(false);
        }
    }

    private void restoreForwardKey() {
        if (!isInGame()) {
            return;
        }
        int key = mc.gameSettings.keyBindForward.getKeyCode();
        if (mode.getValue() == WTapMode.NORMAL && key >= 0 && Keyboard.isKeyDown(key)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        }
        if (mode.getValue() == WTapMode.SILENT && isForwardHeld()) {
            SprintUtil.setSprinting(true);
        }
    }

    private boolean isForwardHeld() {
        int key = mc.gameSettings.keyBindForward.getKeyCode();
        return mc.gameSettings.keyBindForward.isKeyDown() || key >= 0 && Keyboard.isKeyDown(key);
    }

    private static int randomTicks(int base, int jitter) {
        int safeBase = Math.max(0, base);
        int safeJitter = Math.max(0, jitter);
        return safeBase + (safeJitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(safeJitter + 1));
    }
}
