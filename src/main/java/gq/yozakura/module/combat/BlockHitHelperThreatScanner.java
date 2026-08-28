package gq.yozakura.module.combat;

import gq.yozakura.util.module.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MathHelper;

/** Reads remote player state only; it never mutates input or packets. */
final class BlockHitHelperThreatScanner {
    private static final int THREAT_LINGER_TICKS = 2;

    private final Minecraft mc;
    private int lingerTicks;

    BlockHitHelperThreatScanner(Minecraft mc) {
        this.mc = mc;
    }

    boolean hasThreat(double maximumDistance, double maximumFacingDifference) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return false;
        }
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (!(entity instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer opponent = (EntityPlayer) entity;
            if (isThreat(opponent, maximumDistance, maximumFacingDifference)) {
                lingerTicks = THREAT_LINGER_TICKS;
                return true;
            }
        }
        if (lingerTicks > 0) {
            lingerTicks--;
            return true;
        }
        return false;
    }

    void reset() {
        lingerTicks = 0;
    }

    private boolean isThreat(EntityPlayer opponent, double maximumDistance,
                             double maximumFacingDifference) {
        boolean validOpponent = opponent != mc.thePlayer && !opponent.isDead
                && opponent.deathTime <= 0 && opponent.getHealth() > 0.0F
                && !AntiBot.isServerBot(opponent) && !TeamUtil.isFriend(opponent)
                && !TeamUtil.isSameTeam(opponent);
        double distance = mc.thePlayer.getDistanceToEntity(opponent);
        return BlockHitHelperThreatPredictor.isThreat(
                validOpponent,
                mc.thePlayer.canEntityBeSeen(opponent),
                isHoldingMeleeWeapon(opponent),
                opponent.isSwingInProgress || opponent.swingProgressInt > 0
                        || opponent.swingProgress > 0.0F,
                distance,
                Math.max(0.0D, maximumDistance),
                facingDifference(opponent),
                Math.max(0.0D, maximumFacingDifference),
                closingSpeed(opponent, distance));
    }

    private double facingDifference(EntityPlayer opponent) {
        double deltaX = mc.thePlayer.posX - opponent.posX;
        double deltaZ = mc.thePlayer.posZ - opponent.posZ;
        float yawToPlayer = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        return Math.abs(MathHelper.wrapAngleTo180_float(yawToPlayer - opponent.rotationYawHead));
    }

    private double closingSpeed(EntityPlayer opponent, double currentDistance) {
        double nextSelfX = mc.thePlayer.posX + mc.thePlayer.motionX;
        double nextSelfY = mc.thePlayer.posY + mc.thePlayer.motionY;
        double nextSelfZ = mc.thePlayer.posZ + mc.thePlayer.motionZ;
        double nextOpponentX = opponent.posX + opponent.motionX;
        double nextOpponentY = opponent.posY + opponent.motionY;
        double nextOpponentZ = opponent.posZ + opponent.motionZ;
        double deltaX = nextSelfX - nextOpponentX;
        double deltaY = nextSelfY - nextOpponentY;
        double deltaZ = nextSelfZ - nextOpponentZ;
        double nextDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        return currentDistance - nextDistance;
    }

    private static boolean isHoldingMeleeWeapon(EntityPlayer opponent) {
        ItemStack heldItem = opponent.getHeldItem();
        return heldItem != null && (heldItem.getItem() instanceof ItemSword
                || heldItem.getItem() instanceof ItemAxe);
    }
}
