package gq.yozakura.module.movement;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

public class NoJumpDelay extends Module {
    private Field jumpTicksField;

    public NoJumpDelay() {
        super("NoJumpDelay", Keyboard.KEY_NONE, ModuleType.Movement, "Remove vanilla jump input cooldown");
        Chinese = "无跳跃延迟";
    }

    @Override
    public void enable() {
        resolveJumpTicksField();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            return;
        }
        setJumpTicks(0);
    }

    private void setJumpTicks(int ticks) {
        Field field = resolveJumpTicksField();
        if (field == null || mc.thePlayer == null) {
            return;
        }
        try {
            field.setInt(mc.thePlayer, ticks);
        } catch (Throwable ignored) {
        }
    }

    private Field resolveJumpTicksField() {
        if (jumpTicksField != null) {
            return jumpTicksField;
        }
        String[] names = new String[]{"jumpTicks", "field_70773_bE"};
        for (String name : names) {
            try {
                Field field = EntityLivingBase.class.getDeclaredField(name);
                field.setAccessible(true);
                jumpTicksField = field;
                return field;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
