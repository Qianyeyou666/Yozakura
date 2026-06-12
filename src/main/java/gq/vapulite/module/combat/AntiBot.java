package gq.vapulite.module.combat;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.Module;
import gq.vapulite.manager.ModuleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class AntiBot extends Module {
        public AntiBot() {
            super("AntiBot", Keyboard.KEY_NONE, ModuleType.Combat,"Make cheats exclude the hypixel robot");
            Chinese="反机器人";
        }

        public static boolean isServerBot(Entity entity) {
            if (entity == null || !(entity instanceof EntityPlayer)) {
                return false;
            }
            if (Objects.requireNonNull(ModuleManager.getModule("AntiBot")).state) {
                String name = entity.getDisplayName() == null ? "" : entity.getDisplayName().getFormattedText().toLowerCase();
                return entity.isInvisible() || name.contains("npc") || name.contains("[npc]");
            }
            return false;
        }
    }
