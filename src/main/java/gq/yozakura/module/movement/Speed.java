package gq.yozakura.module.movement;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.SprintUtil;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class Speed extends Module {
    public Speed() {
        super("Speed", Keyboard.KEY_G, ModuleType.Movement,"moved quickly");
        Chinese="加速";
    }

    @SubscribeEvent
    public void onUpdate(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            return;
        }
        if (!mc.thePlayer.isCollidedHorizontally && mc.thePlayer.moveForward > 0.0f && mc.thePlayer.onGround) {
            SprintUtil.setSprinting(true);
            mc.thePlayer.jump();
        }
    }
}
