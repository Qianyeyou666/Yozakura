package gq.vapulite.Vapu.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

public final class SprintUtil {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private SprintUtil() {
    }

    public static void setSprinting(boolean sprinting) {
        EntityPlayerSP player = MC.thePlayer;
        if (player == null || player.isSprinting() == sprinting) {
            return;
        }
        try {
            player.setSprinting(sprinting);
        } catch (IllegalArgumentException ignored) {
            // Vanilla can throw when the sprint speed modifier was already applied by another tick path.
        }
    }
}
