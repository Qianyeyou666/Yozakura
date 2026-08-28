package gq.yozakura.module.render;

import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

/** Prevents sprint toggles from changing camera FOV while preserving other FOV effects. */
public final class NoSprintFOV extends Module {
    public NoSprintFOV() {
        super("NoSprintFOV", Keyboard.KEY_NONE, ModuleType.Render,
                "Disable camera zoom when sprint starts or stops");
        Chinese = "绂佺敤鐤捐窇瑙嗛噹";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFovUpdate(FOVUpdateEvent event) {
        if (event == null || event.entity == null) {
            return;
        }
        double movementSpeed = event.entity.getEntityAttribute(
                SharedMonsterAttributes.movementSpeed).getAttributeValue();
        event.newfov = SprintFovPolicy.withoutSprint(event.newfov, movementSpeed,
                event.entity.capabilities.getWalkSpeed(), event.entity.isSprinting());
    }
}
