package gq.yozakura.manager;

import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.TickEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

import java.util.concurrent.atomic.AtomicInteger;

public class PlayerStateManager {
    private static final int ATTACKING = 1;
    private static final int DIGGING = 1 << 1;
    private static final int PLACING = 1 << 2;
    private static final int SWAPPING = 1 << 3;
    private static final int SWINGING = 1 << 4;

    private final AtomicInteger stateMask = new AtomicInteger();

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE || event.getType() == EventType.POST) {
            resetTransientState();
        }
    }

    public void handlePacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            setState(ATTACKING);
        }
        if (packet instanceof C07PacketPlayerDigging) {
            setState(DIGGING);
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            setState(PLACING);
        }
        if (packet instanceof C09PacketHeldItemChange) {
            setState(SWAPPING);
        }
        if (packet instanceof C0APacketAnimation) {
            setState(SWINGING);
        }
        if (packet instanceof C03PacketPlayer) {
            resetTransientState();
        }
    }

    public void resetTransientState() {
        stateMask.set(0);
    }

    public boolean isAttacking() {
        return hasState(ATTACKING);
    }

    public boolean isDigging() {
        return hasState(DIGGING);
    }

    public boolean isPlacing() {
        return hasState(PLACING);
    }

    public boolean isSwapping() {
        return hasState(SWAPPING);
    }

    public boolean isSwinging() {
        return hasState(SWINGING);
    }

    private boolean hasState(int state) {
        return (stateMask.get() & state) != 0;
    }

    private void setState(int state) {
        int current = stateMask.get();
        while (!stateMask.compareAndSet(current, current | state)) {
            current = stateMask.get();
        }
    }
}
