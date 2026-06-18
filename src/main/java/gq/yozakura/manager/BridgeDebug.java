package gq.yozakura.manager;

import gq.yozakura.core.Client;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.runtime.YozakuraRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public final class BridgeDebug {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final File LOG_FILE = new File(System.getProperty("java.io.tmpdir"), "YozakuraBridgeDebug.log");
    private static final int MAX_LINES = Integer.getInteger("yozakura.bridgeDebug.maxLines", 12000);
    private static final String SESSION_ID = Long.toHexString(System.nanoTime());
    private static boolean sessionLogged;
    private static boolean limitLogged;
    private static int lines;
    private static long sequence;
    private static long lastForgeC03Nanos;
    private static long lastStandaloneC03Nanos;

    private BridgeDebug() {
    }

    public static boolean isEnabled() {
        return Client.DebugMode;
    }

    public static String getLogPath() {
        return LOG_FILE.getAbsolutePath();
    }

    public static void logTick(String bridge, String stage, boolean playerTick, int skippedPumps) {
        if (!isEnabled()) {
            return;
        }
        if (mc.thePlayer == null && mc.theWorld == null) {
            return;
        }
        if (!playerTick && skippedPumps % 20 != 0) {
            return;
        }
        write(bridge, stage, "playerTick=" + playerTick + " skippedPumps=" + skippedPumps, false);
    }

    public static void logUpdate(String bridge, String stage, UpdateEvent event, boolean pendingPost) {
        if (!isEnabled()) {
            return;
        }
        write(bridge, stage, describeUpdate(event), pendingPost);
    }

    public static void logState(String bridge, String stage, boolean pendingPost) {
        if (!isEnabled()) {
            return;
        }
        write(bridge, stage, "", pendingPost);
    }

    public static void logPacket(String bridge, String stage, Packet<?> packet, boolean pendingPost) {
        if (!isEnabled() || !isRelevantPacket(packet)) {
            return;
        }
        write(bridge, stage, describePacket(bridge, stage, packet), pendingPost);
    }

    public static void logPacketDetail(String bridge, String stage, Packet<?> packet, boolean pendingPost,
                                       String detail) {
        if (!isEnabled() || !isRelevantPacket(packet)) {
            return;
        }
        String packetDetail = describePacket(bridge, stage, packet);
        write(bridge, stage, packetDetail + (detail == null || detail.length() == 0 ? "" : " " + detail),
                pendingPost);
    }

    public static void logPacketRewrite(String bridge, C03PacketPlayer original, C03PacketPlayer rewritten,
                                        boolean pendingPost) {
        if (!isEnabled()) {
            return;
        }
        write(bridge, "SEND_REWRITE",
                "from={" + describeC03(bridge, original, false) + "} to={" + describeC03(bridge, rewritten, false)
                        + "} c03DeltaMs=" + nextC03DeltaMs(bridge), pendingPost);
    }

    private static boolean isRelevantPacket(Packet<?> packet) {
        return packet instanceof C03PacketPlayer
                || packet instanceof C02PacketUseEntity
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction
                || packet instanceof C17PacketCustomPayload;
    }

    private static String describeUpdate(UpdateEvent event) {
        if (event == null) {
            return "event=null";
        }
        return "eventType=" + event.getType()
                + " yaw=" + event.getYaw()
                + " pitch=" + event.getPitch()
                + " newYaw=" + event.getNewYaw()
                + " newPitch=" + event.getNewPitch()
                + " preYaw=" + event.getPreYaw()
                + " rotated=" + event.isRotated()
                + " priority=" + event.isRotating();
    }

    private static String describePacket(String bridge, String stage, Packet<?> packet) {
        if (packet == null) {
            return "packet=null";
        }
        if (packet instanceof C03PacketPlayer) {
            return describeC03(bridge, (C03PacketPlayer) packet,
                    "SEND_OUT".equals(stage) || "SEND_NO_EVENT".equals(stage));
        }
        String name = packet.getClass().getSimpleName();
        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) packet;
            String target = "null";
            try {
                Entity entity = mc.theWorld == null ? null : use.getEntityFromWorld(mc.theWorld);
                if (entity != null) {
                    target = entity.getEntityId() + ":" + entity.getName();
                }
            } catch (Throwable ignored) {
                target = "?";
            }
            return "packet=" + name + " action=" + use.getAction() + " target=" + target;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging digging = (C07PacketPlayerDigging) packet;
            return "packet=" + name + " status=" + digging.getStatus()
                    + " pos=" + digging.getPosition()
                    + " facing=" + digging.getFacing();
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement place = (C08PacketPlayerBlockPlacement) packet;
            return "packet=" + name + " pos=" + place.getPosition()
                    + " direction=" + place.getPlacedBlockDirection()
                    + " stack=" + stackName(place);
        }
        if (packet instanceof C09PacketHeldItemChange) {
            return "packet=" + name + " slot=" + ((C09PacketHeldItemChange) packet).getSlotId();
        }
        if (packet instanceof C0APacketAnimation) {
            return "packet=" + name;
        }
        if (packet instanceof C0BPacketEntityAction) {
            return "packet=" + name + " action=" + ((C0BPacketEntityAction) packet).getAction();
        }
        if (packet instanceof C17PacketCustomPayload) {
            return "packet=" + name + " channel=" + ((C17PacketCustomPayload) packet).getChannelName();
        }
        return "packet=" + name;
    }

    private static String describeC03(String bridge, C03PacketPlayer packet, boolean includeDelta) {
        if (packet == null) {
            return "packet=null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("packet=").append(packet.getClass().getSimpleName())
                .append(" moving=").append(packet.isMoving())
                .append(" rotating=").append(packet.getRotating())
                .append(" onGround=").append(packet.isOnGround());
        if (packet.isMoving()) {
            builder.append(" x=").append(packet.getPositionX())
                    .append(" y=").append(packet.getPositionY())
                    .append(" z=").append(packet.getPositionZ());
        }
        if (packet.getRotating()) {
            builder.append(" yaw=").append(packet.getYaw())
                    .append(" pitch=").append(packet.getPitch());
        }
        if (includeDelta) {
            builder.append(" c03DeltaMs=").append(nextC03DeltaMs(bridge));
        }
        return builder.toString();
    }

    private static String stackName(C08PacketPlayerBlockPlacement packet) {
        try {
            return packet.getStack() == null ? "null" : packet.getStack().getUnlocalizedName();
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String nextC03DeltaMs(String bridge) {
        long now = System.nanoTime();
        long previous;
        if ("forge".equals(bridge)) {
            previous = lastForgeC03Nanos;
            lastForgeC03Nanos = now;
        } else {
            previous = lastStandaloneC03Nanos;
            lastStandaloneC03Nanos = now;
        }
        if (previous == 0L) {
            return "first";
        }
        return String.valueOf((now - previous) / 1000000L);
    }

    private static synchronized void write(String bridge, String stage, String detail, boolean pendingPost) {
        if (!isEnabled()) {
            return;
        }
        if (lines >= MAX_LINES) {
            if (!limitLogged) {
                limitLogged = true;
                append("limit reached maxLines=" + MAX_LINES);
            }
            return;
        }
        if (!sessionLogged) {
            sessionLogged = true;
            append("session=" + SESSION_ID + " debugLog=" + LOG_FILE.getAbsolutePath());
        }
        append(System.currentTimeMillis()
                + " seq=" + (++sequence)
                + " thread=" + sanitize(Thread.currentThread().getName())
                + " bridge=" + bridge
                + " stage=" + stage
                + (detail == null || detail.length() == 0 ? "" : " " + sanitize(detail))
                + " " + snapshot(pendingPost));
    }

    private static void append(String message) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(LOG_FILE, true));
            writer.println(message);
            lines++;
        } catch (Throwable ignored) {
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String snapshot(boolean pendingPost) {
        StringBuilder builder = new StringBuilder();
        builder.append("pendingPost=").append(pendingPost)
                .append(" rotationActive=").append(RotationState.isActived())
                .append(" rotationYaw=").append(RotationState.getRotationYawHead())
                .append(" rotationPitch=").append(RotationState.getRotationPitch())
                .append(" rotationPriority=").append(RotationState.getPriority())
                .append(" visualActive=").append(VisualRotationState.isActived())
                .append(" visualSource=").append(sanitize(VisualRotationState.getSource()));
        try {
            if (mc.thePlayer != null) {
                builder.append(" tick=").append(mc.thePlayer.ticksExisted)
                        .append(" localYaw=").append(mc.thePlayer.rotationYaw)
                        .append(" localPitch=").append(mc.thePlayer.rotationPitch)
                        .append(" headYaw=").append(mc.thePlayer.rotationYawHead)
                        .append(" renderYaw=").append(mc.thePlayer.renderYawOffset)
                        .append(" motionX=").append(mc.thePlayer.motionX)
                        .append(" motionZ=").append(mc.thePlayer.motionZ)
                        .append(" inputF=").append(mc.thePlayer.movementInput == null
                                ? "null" : mc.thePlayer.movementInput.moveForward)
                        .append(" inputS=").append(mc.thePlayer.movementInput == null
                                ? "null" : mc.thePlayer.movementInput.moveStrafe)
                        .append(" sprinting=").append(mc.thePlayer.isSprinting());
            } else {
                builder.append(" tick=null");
            }
        } catch (Throwable ignored) {
            builder.append(" tick=?");
        }
        builder.append(" modules=").append(moduleState("KillAura"))
                .append(",").append(moduleState("Scaffold"))
                .append(" playerState=").append(playerState());
        return builder.toString();
    }

    private static String moduleState(String name) {
        try {
            gq.yozakura.module.Module module = ModuleManager.getModule(name);
            return name + ":" + (module != null && module.getState() ? "on" : "off");
        } catch (Throwable ignored) {
            return name + ":?";
        }
    }

    private static String playerState() {
        try {
            PlayerStateManager state = YozakuraRuntime.playerStateManager;
            if (state == null) {
                return "null";
            }
            return "attack=" + state.attacking
                    + ",dig=" + state.digging
                    + ",place=" + state.placing
                    + ",swap=" + state.swapping
                    + ",swing=" + state.swinging;
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
    }
}
