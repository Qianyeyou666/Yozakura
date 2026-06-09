package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class CombatUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private CombatUtil() {
    }

    enum TargetPriority {
        DISTANCE,
        HEALTH,
        FOV,
        ARMOR,
        HURT_TIME
    }

    static boolean isReady() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    static boolean isHoldingWeapon() {
        if (!isReady()) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        return stack != null && (stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemAxe);
    }

    static List<EntityLivingBase> collectTargets(double range, double fov, boolean players, boolean mobs,
                                                 boolean animals, boolean throughWalls) {
        ArrayList<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        if (!isReady()) {
            return targets;
        }
        List<Entity> entities = mc.theWorld.getLoadedEntityList();
        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (isValidTarget(living, range, fov, players, mobs, animals, throughWalls)) {
                targets.add(living);
            }
        }
        return targets;
    }

    static EntityLivingBase bestTarget(double range, double fov, boolean players, boolean mobs, boolean animals,
                                       boolean throughWalls, TargetPriority priority) {
        List<EntityLivingBase> targets = collectTargets(range, fov, players, mobs, animals, throughWalls);
        if (targets.isEmpty()) {
            return null;
        }
        sortTargets(targets, priority);
        return targets.get(0);
    }

    static void sortTargets(List<EntityLivingBase> targets, final TargetPriority priority) {
        Collections.sort(targets, new Comparator<EntityLivingBase>() {
            @Override
            public int compare(EntityLivingBase first, EntityLivingBase second) {
                return Double.compare(priorityScore(first, priority), priorityScore(second, priority));
            }
        });
    }

    static boolean isValidTarget(EntityLivingBase entity, double range, double fov, boolean players, boolean mobs,
                                 boolean animals, boolean throughWalls) {
        if (!isReady() || entity == null || entity == mc.thePlayer || entity instanceof EntityArmorStand) {
            return false;
        }
        if (entity.isDead || entity.deathTime > 0 || entity.getHealth() <= 0.0f) {
            return false;
        }
        if (mc.thePlayer.getDistanceToEntity(entity) > range) {
            return false;
        }
        if (fov < 180.0D && getFovDifference(entity) > fov) {
            return false;
        }
        if (!throughWalls && !mc.thePlayer.canEntityBeSeen(entity)) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            if (AntiBot.isServerBot(entity)) {
                return false;
            }
            return players;
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityWaterMob || entity instanceof EntityAmbientCreature) {
            return animals;
        }
        if (entity instanceof EntityMob || entity instanceof EntitySlime || entity instanceof IMob) {
            return mobs;
        }
        return mobs;
    }

    static void faceEntity(Entity entity, float yawSpeed, float pitchSpeed, boolean onlyYaw, float freeZone) {
        faceEntity(entity, yawSpeed, pitchSpeed, onlyYaw, freeZone, null);
    }

    static void faceEntity(Entity entity, float yawSpeed, float pitchSpeed, boolean onlyYaw, float freeZone,
                           RotationUtil.State state) {
        if (!isReady() || entity == null) {
            return;
        }
        float[] rotations = getRotations(entity);
        RotationUtil.applyToPlayer(mc, rotations[0], rotations[1], yawSpeed, pitchSpeed, onlyYaw, freeZone,
                state, 0.38f, 0.18f, true);
    }

    static float[] getRotations(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            return RotationUtil.getRotations(mc, entity, 0.18D, 0.82D);
        }
        return RotationUtil.getRotations(mc, entity, 0.0D, 0.5D);
    }

    static float updateRotation(float current, float target, float maxTurn) {
        return RotationUtil.limitAngleChange(current, target, maxTurn);
    }

    static double getFovDifference(Entity entity) {
        float[] rotations = getRotations(entity);
        return Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
    }

    static double priorityScore(EntityLivingBase entity, TargetPriority priority) {
        if (priority == TargetPriority.HEALTH) {
            return entity.getHealth();
        }
        if (priority == TargetPriority.FOV) {
            return getFovDifference(entity);
        }
        if (priority == TargetPriority.ARMOR) {
            return entity.getTotalArmorValue();
        }
        if (priority == TargetPriority.HURT_TIME) {
            return entity.hurtTime;
        }
        return mc.thePlayer.getDistanceToEntity(entity);
    }

    static int nextDelay(double minCps, double maxCps) {
        double min = Math.max(1.0D, Math.min(minCps, maxCps));
        double max = Math.max(min, Math.max(minCps, maxCps));
        double cps = min + ThreadLocalRandom.current().nextDouble() * (max - min + 0.001D);
        return Math.max(1, (int) Math.round(1000.0D / cps));
    }

    static boolean shouldPauseForScreen() {
        return mc.currentScreen != null;
    }

    static boolean hasCombatFocus() {
        return KillAura.target != null
                || mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase;
    }
}
