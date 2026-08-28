package gq.yozakura.module.combat;

import gq.yozakura.bridge.util.ReflectionUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.lang.reflect.Field;

/**
 * Temporarily exposes a sword-use state only while the world frame is rendered.
 * Direct field restoration avoids invoking item-use lifecycle callbacks.
 */
final class BlockHitRenderPose {
    private static final Field ITEM_IN_USE = ReflectionUtils.findField(
            EntityPlayer.class, "itemInUse", "field_71074_e", "g");
    private static final Field ITEM_IN_USE_COUNT = ReflectionUtils.findField(
            EntityPlayer.class, "itemInUseCount", "field_71072_f", "h");

    private EntityPlayerSP player;
    private ItemStack originalItemInUse;
    private int originalItemInUseCount;

    void begin(EntityPlayerSP currentPlayer) {
        end();
        if (currentPlayer == null || ITEM_IN_USE == null || ITEM_IN_USE_COUNT == null) {
            return;
        }
        ItemStack heldItem = currentPlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemSword)) {
            return;
        }
        try {
            originalItemInUse = (ItemStack) ITEM_IN_USE.get(currentPlayer);
            originalItemInUseCount = ITEM_IN_USE_COUNT.getInt(currentPlayer);
            player = currentPlayer;
            ITEM_IN_USE.set(currentPlayer, heldItem);
            ITEM_IN_USE_COUNT.setInt(currentPlayer, heldItem.getMaxItemUseDuration());
        } catch (IllegalAccessException ignored) {
            end();
        }
    }

    void end() {
        EntityPlayerSP posePlayer = player;
        if (posePlayer == null) {
            return;
        }
        ItemStack savedItem = originalItemInUse;
        int savedCount = originalItemInUseCount;
        player = null;
        originalItemInUse = null;
        originalItemInUseCount = 0;
        try {
            ITEM_IN_USE.set(posePlayer, savedItem);
            ITEM_IN_USE_COUNT.setInt(posePlayer, savedCount);
        } catch (IllegalAccessException ignored) {
        }
    }
}
