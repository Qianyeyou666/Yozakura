package gq.yozakura.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import gq.yozakura.manager.BlinkModules;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.util.module.PacketUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BlinkManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    private BlinkModules blinkModule = BlinkModules.NONE;
    private boolean blinking = false;
    private final Deque<Packet<?>> blinkedPackets = new ConcurrentLinkedDeque<>();

    public synchronized boolean offerPacket(Packet<?> packet) {
        if (!this.blinking || this.blinkModule == BlinkModules.NONE
                || packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if (this.blinkedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction) {
            return false;
        } else {
            this.blinkedPackets.offer(packet);
            return true;
        }
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        Deque<Packet<?>> pending = null;
        synchronized (this) {
            if (module == null || module == BlinkModules.NONE) {
                return false;
            }
            if (state) {
                if (blinking && blinkModule != module) {
                    return false;
                }
                this.blinkModule = module;
                this.blinking = true;
                return true;
            }
            if (blinkModule != module) {
                return false;
            }

            this.blinking = false;
            this.blinkModule = BlinkModules.NONE;
            if (this.blinkedPackets.isEmpty()) {
                return true;
            }
            pending = new ArrayDeque<Packet<?>>();
            Packet<?> packet;
            while ((packet = this.blinkedPackets.poll()) != null) {
                pending.offer(packet);
            }
        }

        if (Minecraft.getMinecraft().getNetHandler() == null) {
            return true;
        }
        for (Packet<?> blinkedPacket : pending) {
            PacketUtil.sendPacketNoEvent(blinkedPacket);
        }
        return true;
    }

    /**
     * Acquires the outbound blink channel only when no other module owns it.
     * BlockHit uses this instead of replacing an existing KillAura/fakelag lease.
     */
    public boolean tryAcquire(BlinkModules module) {
        if (module == null || module == BlinkModules.NONE) {
            return false;
        }
        return setBlinkState(true, module);
    }

    public synchronized boolean owns(BlinkModules module) {
        return blinking && blinkModule == module;
    }

    public synchronized boolean discard(BlinkModules module) {
        if (module == null || module == BlinkModules.NONE || blinkModule != module) {
            return false;
        }
        blinking = false;
        blinkModule = BlinkModules.NONE;
        blinkedPackets.clear();
        return true;
    }

    public synchronized BlinkModules getBlinkingModule() {
        return this.blinkModule;
    }

    public synchronized long countMovement() {
        return this.blinkedPackets.stream().filter(packet -> packet instanceof C03PacketPlayer).count();
    }

    public synchronized boolean isBlinking() {
        return blinking;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.discard(this.getBlinkingModule());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer != null && mc.thePlayer.isDead) {
                this.setBlinkState(false, this.getBlinkingModule());
            }
        }
    }
}
