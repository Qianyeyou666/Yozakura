package gq.vapulite.Vapu.modules.movement;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.SprintUtil;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", Keyboard.KEY_R, ModuleType.Movement,"Force sprint when you moving");
        Chinese="强制疾跑";
    }

    @SubscribeEvent
    public void onUpdate(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            return;
        }
        if (!mc.thePlayer.isCollidedHorizontally && mc.thePlayer.moveForward > 0.0f) {
            SprintUtil.setSprinting(true);
        }
    }

    @Override
    public void disable() {
        SprintUtil.setSprinting(false);
    }
}
