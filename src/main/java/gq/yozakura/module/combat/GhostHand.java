package gq.yozakura.module.combat;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.util.module.TeamUtil;
import gq.yozakura.value.Option;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class GhostHand extends Module {
    private final Option<Boolean> teamsOnly = new Option<Boolean>("Team Only", "TeamOnly", true);
    private final Option<Boolean> ignoreWeapons = new Option<Boolean>("Ignore Weapons", "IgnoreWeapons", false);

    public GhostHand() {
        super("GhostHand", Keyboard.KEY_NONE, ModuleType.Combat, "Click through teammates");
        this.addValues(teamsOnly, ignoreWeapons);
        Chinese = "隔空手";
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            return;
        }
        applyGhostHand(1.0f);
    }

    private void applyGhostHand(float partialTicks) {
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) {
            return;
        }

        MovingObjectPosition original = mc.objectMouseOver;
        if (original == null || original.entityHit == null) {
            return;
        }
        if (!shouldSkip(original.entityHit)) {
            return;
        }

        // The entity under the crosshair should be skipped — redo ray trace excluding it
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 eyes = view.getPositionEyes(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

        // Check for blocks first (preserve vanilla block targeting)
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, end, false, true, false);
        double blockDist = blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? eyes.distanceTo(blockHit.hitVec) : reach;

        Entity pointed = null;
        Vec3 hitVec = null;
        double bestDistance = blockDist;

        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(view,
                view.getEntityBoundingBox().addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach)
                        .expand(1.0D, 1.0D, 1.0D));

        for (Entity entity : entities) {
            if (!entity.canBeCollidedWith()) {
                continue;
            }
            if (shouldSkip(entity)) {
                continue;
            }
            if (entity instanceof EntityItemFrame) {
                // Item frames handled by vanilla block ray trace
                continue;
            }

            double border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);

            if (box.isVecInside(eyes)) {
                if (bestDistance >= 0.0D) {
                    pointed = entity;
                    hitVec = intercept == null ? eyes : intercept.hitVec;
                    bestDistance = 0.0D;
                }
            } else if (intercept != null) {
                double currentDistance = eyes.distanceTo(intercept.hitVec);
                if (currentDistance < bestDistance || bestDistance == 0.0D) {
                    if (entity == view.ridingEntity && !entity.canRiderInteract()) {
                        continue;
                    }
                    pointed = entity;
                    hitVec = intercept.hitVec;
                    bestDistance = currentDistance;
                }
            }
        }

        if (pointed != null && hitVec != null) {
            mc.objectMouseOver = new MovingObjectPosition(pointed, hitVec);
            mc.pointedEntity = pointed;
        }
    }

    private boolean shouldSkip(Entity entity) {
        return entity instanceof EntityPlayer
                && !TeamUtil.isBot((EntityPlayer) entity)
                && (!teamsOnly.getValue() || TeamUtil.isSameTeam((EntityPlayer) entity))
                && (!ignoreWeapons.getValue() || !hasRawUnbreakingEnchant());
    }

    private boolean hasRawUnbreakingEnchant() {
        if (mc.thePlayer == null) {
            return false;
        }
        ItemStack itemStack = mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        if (itemStack.hasTagCompound()) {
            NBTTagCompound tag = itemStack.getTagCompound();
            if (tag.hasKey("ExtraAttributes")) {
                NBTTagCompound extra = tag.getCompoundTag("ExtraAttributes");
                if (extra.hasKey("UHCid")) {
                    long id = extra.getLong("UHCid");
                    if (id == 50006L || id == 50009L) {
                        return true;
                    }
                }
            }
            if (tag.hasKey("HideFlags")
                    && itemStack.getItem() instanceof ItemSpade
                    && ((ItemSpade) itemStack.getItem()).getToolMaterial() == Item.ToolMaterial.EMERALD) {
                return true;
            }
        }
        if (itemStack.getItem() instanceof ItemEnchantedBook) {
            return false;
        }
        if (EnchantmentHelper.getEnchantments(itemStack).containsKey(Enchantment.unbreaking.effectId)) {
            return true;
        }
        return itemStack.getItem() instanceof ItemSword;
    }
}
