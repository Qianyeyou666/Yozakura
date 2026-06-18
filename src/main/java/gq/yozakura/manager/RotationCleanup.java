package gq.yozakura.manager;

import net.minecraft.client.Minecraft;

public final class RotationCleanup {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private RotationCleanup() {
    }

    public static void clearModuleRotations(String source, int priority) {
        VisualRotationState.clearSource(source);
        PacketRotationState.clearSource(source);
        if (!RotationState.isActived() || RotationState.getPriority() <= priority) {
            RotationState.clear();
        }
        restorePlayerVisualRotation();
    }

    private static void restorePlayerVisualRotation() {
        if (mc.thePlayer == null || VisualRotationState.isActived()) {
            return;
        }
        mc.thePlayer.prevRotationYawHead = mc.thePlayer.rotationYawHead;
        mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
        mc.thePlayer.prevRenderYawOffset = mc.thePlayer.renderYawOffset;
        mc.thePlayer.renderYawOffset = mc.thePlayer.rotationYaw;
        mc.thePlayer.prevRotationPitch = mc.thePlayer.rotationPitch;
    }
}
