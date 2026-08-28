package gq.yozakura.module.combat;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.util.module.ServerUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.lwjgl.input.Keyboard;

public class AntiBot extends Module {
    public AntiBot() {
        super("AntiBot", Keyboard.KEY_NONE, ModuleType.Combat, "Make cheats exclude the hypixel robot");
        Chinese = "反机器人";
    }

    public static boolean isServerBot(Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }
        Module antiBot = ModuleManager.getModule("AntiBot");
        if (antiBot == null || !antiBot.getState()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == entity) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        NetworkPlayerInfo info = minecraft.getNetHandler() == null ? null
                : minecraft.getNetHandler().getPlayerInfo(player.getUniqueID());
        ScorePlayerTeam team = info == null ? null : info.getPlayerTeam();
        String profileName = player.getGameProfile() == null
                ? player.getName() : player.getGameProfile().getName();
        String displayName = player.getDisplayName() == null
                ? profileName : player.getDisplayName().getFormattedText();
        HypixelBotPolicy.Snapshot snapshot = new HypixelBotPolicy.Snapshot(
                ServerUtil.isHypixel(), player.isDead, profileName, displayName,
                player.isInvisible(), info != null, info == null ? -1 : info.getResponseTime(),
                team == null ? "" : team.getRegisteredName(),
                team == null ? "" : team.getColorPrefix(),
                player.getHealth(), player.maxHurtTime, player.isPlayerSleeping(), player.ticksExisted);
        return snapshot.isBot();
    }
}
