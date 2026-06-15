package gq.yozakura.manager;

import gq.yozakura.event.bridge.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public final class RotationDebug {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean scaffold;
    private static boolean killAura;
    private static long lastUpdateLog;
    private static long lastPacketLog;

    private RotationDebug() {
    }

    public static void setSourceEnabled(String source, boolean enabled) {
        if ("Scaffold".equals(source)) {
            scaffold = enabled;
        } else if ("KillAura".equals(source)) {
            killAura = enabled;
        }
    }

    public static boolean isEnabled() {
        return scaffold || killAura;
    }

    public static void logUpdate(String bridge, UpdateEvent event) {
        if (!isEnabled() || event == null || !throttleUpdate()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(bridge).append(" update] ");
        if (mc.thePlayer != null) {
            builder.append("localYaw=").append(mc.thePlayer.rotationYaw)
                    .append(" localPitch=").append(mc.thePlayer.rotationPitch)
                    .append(" headYaw=").append(mc.thePlayer.rotationYawHead)
                    .append(" renderYawOffset=").append(mc.thePlayer.renderYawOffset)
                    .append(' ');
        }
        builder.append("eventYaw=").append(event.getNewYaw())
                .append(" eventPitch=").append(event.getNewPitch())
                .append(" visual=").append(VisualRotationState.isActived())
                .append(" visualSource=").append(VisualRotationState.getSource())
                .append(" packet=").append(PacketRotationState.isActived())
                .append(" packetSource=").append(PacketRotationState.getSource());
        log(builder.toString());
    }

    public static void logPacket(String bridge, C03PacketPlayer packet, boolean rewritten) {
        if (!isEnabled() || packet == null || !throttlePacket()) {
            return;
        }
        log("[" + bridge + " packet] rotating=" + packet.getRotating()
                + " moving=" + packet.isMoving()
                + " rewritten=" + rewritten
                + " visualActive=" + VisualRotationState.isActived()
                + " packetActive=" + PacketRotationState.isActived());
    }

    private static boolean throttleUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateLog < 250L) {
            return false;
        }
        lastUpdateLog = now;
        return true;
    }

    private static boolean throttlePacket() {
        long now = System.currentTimeMillis();
        if (now - lastPacketLog < 250L) {
            return false;
        }
        lastPacketLog = now;
        return true;
    }

    private static void log(String message) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraRotationDebug.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(System.currentTimeMillis() + " " + message);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
