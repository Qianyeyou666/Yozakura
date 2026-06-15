package gq.vapulite.module.movement;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.module.combat.BlockHit;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import gq.vapulite.value.Numbers;

public class NoSlowDown extends Module {
    private final Numbers<Float> speedValue = new Numbers<>("Speed", "Speed", 1.0F, 0.2F, 1.0F, 0.05F);
    public NoSlowDown() {
        super("NoSlowDown", Keyboard.KEY_R, ModuleType.Movement);
        Chinese="没有减速";
        addValues(speedValue);
    }

    @SubscribeEvent
    public void onUpdate(TickEvent event) {
        if (!isInGame()) {
            return;
        }
        if (BlockHit.isBlockingActive()) {
            return;
        }
        float speed = speedValue.getValue().floatValue();
        mc.thePlayer.movementInput.moveStrafe = speed;
        mc.thePlayer.movementInput.moveForward = speed;
    }
}
