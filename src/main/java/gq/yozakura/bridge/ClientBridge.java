package gq.yozakura.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

public interface ClientBridge {
    void init();

    void shutdown();

    boolean isInGame();

    Minecraft getMinecraft();

    boolean isBridgeActive();

    void tick(boolean playerTick);

    void sendPacket(Packet<?> packet);

    void markPacketBypass(Packet<?> packet);

    void setSilentRotation(float yaw, float pitch, boolean moveFix);

    void clearSilentRotation();

    boolean hasSilentRotation();

    float getSilentYaw();

    float getSilentPitch();

    void applyVisibleRotation(float yaw, float pitch);

    boolean isKeyDown(String keyName);

    void setKeyDown(String keyName, boolean down);
}
