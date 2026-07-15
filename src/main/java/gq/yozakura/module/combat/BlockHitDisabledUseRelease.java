package gq.yozakura.module.combat;

import gq.yozakura.event.bridge.PacketAcceptedEvent;
import gq.yozakura.event.bridge.PacketWriteEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import java.util.concurrent.atomic.AtomicLong;

/** Finishes an already submitted BlockHit use after the module has been disabled. */
final class BlockHitDisabledUseRelease {
    private final BlockHitVanillaUseAction useAction;
    private final long cycleId;
    private final AtomicLong acceptedAttackSequence;
    private long lastPostAcceptedAttackSequence;
    private boolean waitForNextPost = true;
    private volatile boolean useWriteSucceeded;

    BlockHitDisabledUseRelease(BlockHitVanillaUseAction useAction, long cycleId,
                               AtomicLong acceptedAttackSequence) {
        this.useAction = useAction;
        this.cycleId = cycleId;
        this.acceptedAttackSequence = acceptedAttackSequence;
        this.lastPostAcceptedAttackSequence = acceptedAttackSequence.get();
    }

    void register() {
        EventManager.register(this);
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketAccepted(PacketAcceptedEvent event) {
        if (event == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            acceptedAttackSequence.incrementAndGet();
            return;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement
                && ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() == 255) {
            useAction.claimOwnedUseWrite(event.getWriteId());
            return;
        }
        if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus()
                == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            useAction.observeRelease();
            unregister();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacketWritten(PacketWriteEvent event) {
        if (event == null || !event.isPacketAccepted()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof C08PacketPlayerBlockPlacement)
                || ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() != 255) {
            return;
        }
        long completedCycle = useAction.completeOwnedUseWrite(event.getWriteId(), event.isSuccess());
        if (completedCycle != cycleId) {
            return;
        }
        if (!event.isSuccess()) {
            unregister();
            return;
        }
        useWriteSucceeded = true;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (event == null || event.getType() != EventType.POST) {
            return;
        }
        if (!useWriteSucceeded) {
            return;
        }
        long acceptedAttacks = acceptedAttackSequence.get();
        if (waitForNextPost || acceptedAttacks != lastPostAcceptedAttackSequence) {
            waitForNextPost = false;
            lastPostAcceptedAttackSequence = acceptedAttacks;
            return;
        }
        if (useAction.releaseUse(cycleId) || !useAction.isUsing()) {
            unregister();
        }
    }

    private void unregister() {
        EventManager.unregister(this);
    }
}
