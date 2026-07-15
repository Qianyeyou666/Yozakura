package gq.yozakura.module.combat;

import gq.yozakura.util.module.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/**
 * Owns one short sword-use action through Minecraft's PlayerController.
 * It never builds interaction packets or changes the physical use key.
 */
final class BlockHitVanillaUseAction {
    private static final long NO_CYCLE = BlockHitController.NO_CYCLE;
    private static final long NO_WRITE_ID = 0L;

    private final Minecraft mc;
    private boolean using;
    private long activeUseCycleId = NO_CYCLE;
    private long ownedUseWriteId = NO_WRITE_ID;
    private boolean ownedUseWriteSucceeded;

    BlockHitVanillaUseAction(Minecraft mc) {
        this.mc = mc;
    }

    boolean startUse(long cycleId) {
        if (!isReady() || isPhysicalUseDown()) {
            return false;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) {
            return false;
        }
        synchronized (this) {
            if (cycleId == NO_CYCLE || using) {
                return false;
            }
            using = true;
            activeUseCycleId = cycleId;
            ownedUseWriteId = NO_WRITE_ID;
            ownedUseWriteSucceeded = false;
        }
        try {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, heldItem);
        } catch (RuntimeException failure) {
            clearUseCycle(cycleId);
            throw failure;
        }
        return true;
    }

    boolean releaseUse() {
        return releaseUse(getActiveUseCycleId());
    }

    boolean releaseUse(long cycleId) {
        synchronized (this) {
            if (!using || !ownedUseWriteSucceeded || cycleId == NO_CYCLE || activeUseCycleId != cycleId) {
                return false;
            }
            using = false;
            activeUseCycleId = NO_CYCLE;
            ownedUseWriteId = NO_WRITE_ID;
            ownedUseWriteSucceeded = false;
        }
        if (!isReady() || isPhysicalUseDown()) {
            return false;
        }
        mc.playerController.onStoppedUsingItem(mc.thePlayer);
        return true;
    }

    synchronized long claimOwnedUseWrite(long writeId) {
        if (!using || activeUseCycleId == NO_CYCLE || writeId == NO_WRITE_ID
                || ownedUseWriteId != NO_WRITE_ID) {
            return NO_CYCLE;
        }
        ownedUseWriteId = writeId;
        return activeUseCycleId;
    }

    synchronized long cycleForOwnedUseWrite(long writeId) {
        return using && writeId != NO_WRITE_ID && ownedUseWriteId == writeId
                ? activeUseCycleId
                : NO_CYCLE;
    }

    synchronized long completeOwnedUseWrite(long writeId, boolean success) {
        long cycleId = cycleForOwnedUseWrite(writeId);
        if (cycleId == NO_CYCLE) {
            return NO_CYCLE;
        }
        if (success) {
            ownedUseWriteSucceeded = true;
            return cycleId;
        }
        clearUseCycle(cycleId);
        return cycleId;
    }

    synchronized boolean isUseWriteSucceeded(long cycleId) {
        return using && cycleId != NO_CYCLE && activeUseCycleId == cycleId && ownedUseWriteSucceeded;
    }

    synchronized void observeRelease() {
        using = false;
        activeUseCycleId = NO_CYCLE;
        ownedUseWriteId = NO_WRITE_ID;
        ownedUseWriteSucceeded = false;
    }

    synchronized long getActiveUseCycleId() {
        return activeUseCycleId;
    }

    synchronized boolean isUsing() {
        return using;
    }

    boolean isPhysicalUseDown() {
        return mc != null && mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null
                && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    private boolean isReady() {
        return mc != null && mc.thePlayer != null && mc.theWorld != null && mc.playerController != null;
    }

    private synchronized void clearUseCycle(long cycleId) {
        if (activeUseCycleId == cycleId) {
            using = false;
            activeUseCycleId = NO_CYCLE;
            ownedUseWriteId = NO_WRITE_ID;
            ownedUseWriteSucceeded = false;
        }
    }
}
