package gq.yozakura.module.combat;

import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bridge.MoveInputEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.Scaffold;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.value.properties.BooleanProperty;
import gq.yozakura.value.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty fakeCheck = new BooleanProperty("Fake Check", false);
    public final BooleanProperty forceForward = new BooleanProperty("Force Forward", true);
    public final PercentProperty chance = new PercentProperty("Chance", 100);

    private final JumpResetController controller = new JumpResetController();
    private final JumpResetKeyGuard jumpKeyGuard = new JumpResetKeyGuard(mc);
    private final Queue<QueuedPacket> pendingPackets = new ConcurrentLinkedQueue<QueuedPacket>();
    private final AtomicLong packetSession = new AtomicLong();
    private volatile boolean acceptingPackets;

    public JumpReset() {
        super("JumpReset", false);
        setCategory(ModuleType.Combat);
        Chinese = "跳跃重置";
        Descript = "Jump reset after receiving velocity";
        About = Descript;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof S12PacketEntityVelocity) && !(packet instanceof S19PacketEntityStatus)) {
            return;
        }
        long session = packetSession.get();
        if (!acceptingPackets) {
            return;
        }
        pendingPackets.offer(new QueuedPacket(session, packet));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || !isInGame()) {
            if (event.getType() == EventType.PRE && !isInGame()) {
                discardPendingPackets();
                resetTransientState();
            }
            return;
        }

        if (isResetBlocked()) {
            discardPendingPackets();
            resetTransientState();
            return;
        }

        drainPendingPackets();
        applyJumpAction(controller.advance(mc.thePlayer.onGround));
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || !isInGame() || !controller.shouldForceForward()) {
            return;
        }
        if (isResetBlocked()) {
            discardPendingPackets();
            resetTransientState();
            return;
        }
        if (!Boolean.TRUE.equals(forceForward.getValue())) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 1.0F;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        acceptingPackets = false;
        discardPendingPackets();
        resetTransientState();
    }

    @Override
    public void onEnabled() {
        resetTransientState();
        beginPacketSession();
    }

    @Override
    public void onDisabled() {
        acceptingPackets = false;
        discardPendingPackets();
        resetTransientState();
    }

    private void drainPendingPackets() {
        long activeSession = packetSession.get();
        QueuedPacket queued;
        while ((queued = pendingPackets.poll()) != null) {
            if (queued.session != activeSession) {
                continue;
            }
            if (queued.packet instanceof S19PacketEntityStatus) {
                handleEntityStatus((S19PacketEntityStatus) queued.packet);
            } else if (queued.packet instanceof S12PacketEntityVelocity) {
                handleVelocity((S12PacketEntityVelocity) queued.packet);
            }
        }
    }

    private void handleEntityStatus(S19PacketEntityStatus packet) {
        Entity entity = packet.getEntity(mc.theWorld);
        if (entity == mc.thePlayer && packet.getOpCode() == 2) {
            controller.observePlayerHurt();
        }
    }

    private void handleVelocity(S12PacketEntityVelocity packet) {
        if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
            controller.acceptVelocity(Boolean.TRUE.equals(fakeCheck.getValue()), chance.getValue());
        }
    }

    private boolean isResetBlocked() {
        Scaffold scaffold = (Scaffold) YozakuraRuntime.moduleManager.modules.get(Scaffold.class);
        return mc.currentScreen instanceof GuiInventory || scaffold != null && scaffold.isEnabled();
    }

    private void applyJumpAction(JumpResetController.JumpAction action) {
        if (action == JumpResetController.JumpAction.PRESS) {
            jumpKeyGuard.holdJump();
        } else if (action == JumpResetController.JumpAction.RELEASE) {
            jumpKeyGuard.releaseJump();
        }
    }

    private void beginPacketSession() {
        acceptingPackets = false;
        discardPendingPackets();
        acceptingPackets = true;
    }

    private void discardPendingPackets() {
        packetSession.incrementAndGet();
        pendingPackets.clear();
    }

    private void resetTransientState() {
        applyJumpAction(controller.cancel());
        jumpKeyGuard.reset();
        controller.reset();
    }

    private static final class QueuedPacket {
        private final long session;
        private final Packet<?> packet;

        private QueuedPacket(long session, Packet<?> packet) {
            this.session = session;
            this.packet = packet;
        }
    }
}
