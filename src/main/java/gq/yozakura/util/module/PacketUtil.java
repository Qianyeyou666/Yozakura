package gq.yozakura.util.module;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.play.INetHandlerPlayClient;
import gq.yozakura.bridge.PacketBridgeSupport;

public class PacketUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void sendPacket(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        PacketBridgeSupport.sendNoEvent(mc.getNetHandler().getNetworkManager(), packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet, ChannelPromise promise, long writeId,
                                         boolean alreadyBridgeProcessed) {
        PacketBridgeSupport.sendNoEvent(mc.getNetHandler().getNetworkManager(), packet, promise, writeId,
                alreadyBridgeProcessed);
    }
    public static void receivePacketNoEvent(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }


    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }
    @SuppressWarnings("unchecked")
    public static <H extends INetHandler> Packet<H> castPacket(Packet<?> packet) throws ClassCastException {
        return (Packet<H>) packet;
    }
}
