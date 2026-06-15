package gq.vapulite.module.combat;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ThreadLocalRandom;

public class Velocity extends Module {
    public enum VelocityMode {
        SCALE,
        JUMP_RESET
    }

    private final Mode<VelocityMode> mode =
            new Mode<VelocityMode>("Mode", "Mode", VelocityMode.values(), VelocityMode.SCALE);
    private final Numbers<Double> horizontal = new Numbers<Double>("Horizontal", "Horizontal", 0.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> vertical = new Numbers<Double>("Vertical", "Vertical", 100.0, 0.0, 100.0, 1.0);
    private final Numbers<Double> chance = new Numbers<Double>("Chance", "Chance", 100.0, 0.0, 100.0, 1.0);
    private final Option<Boolean> onlyCombat = new Option<Boolean>("Only Combat", "OnlyCombat", false);
    private boolean wasHurt;

    public Velocity() {
        super("Velocity", Keyboard.KEY_NONE, ModuleType.Combat, "Reduce knockback when hurt");
        this.addValues(mode, horizontal, vertical, chance, onlyCombat);
        Chinese = "反击退";
    }

    @Override
    public void enable() {
        wasHurt = false;
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!isInGame() || event.entityLiving != mc.thePlayer) {
            return;
        }
        if (mc.thePlayer.hurtTime <= 0) {
            wasHurt = false;
            return;
        }
        if (wasHurt) {
            return;
        }
        wasHurt = true;
        if (Boolean.TRUE.equals(onlyCombat.getValue()) && !CombatUtil.hasCombatFocus()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0D) > chance.getValue()) {
            return;
        }
        if (mode.getValue() == VelocityMode.JUMP_RESET) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.motionY = 0.42D;
                mc.thePlayer.onGround = false;
            }
            return;
        }
        mc.thePlayer.motionX *= horizontal.getValue() / 100.0D;
        mc.thePlayer.motionY *= vertical.getValue() / 100.0D;
        mc.thePlayer.motionZ *= horizontal.getValue() / 100.0D;
    }
}
