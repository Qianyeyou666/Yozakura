package gq.yozakura.module.combat.aim;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public final class AimAssistAimPoint {
    private final double xFactor;
    private final double yFactor;
    private final double zFactor;

    private AimAssistAimPoint(double xFactor, double yFactor, double zFactor) {
        this.xFactor = xFactor;
        this.yFactor = yFactor;
        this.zFactor = zFactor;
    }

    public static AimAssistAimPoint from(EntityLivingBase entity, Vec3 point) {
        if (entity == null || point == null) {
            throw new IllegalArgumentException("entity and point are required");
        }
        AxisAlignedBB box = entity.getEntityBoundingBox();
        return new AimAssistAimPoint(
                factor(point.xCoord, box.minX, box.maxX),
                factor(point.yCoord, box.minY, box.maxY),
                factor(point.zCoord, box.minZ, box.maxZ)
        );
    }

    public Vec3 resolve(EntityLivingBase entity, float partialTicks) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is required");
        }
        float partial = MathHelper.clamp_float(partialTicks, 0.0F, 1.0F);
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial;
        AxisAlignedBB box = entity.getEntityBoundingBox().offset(x - entity.posX, y - entity.posY, z - entity.posZ);
        return new Vec3(
                lerp(box.minX, box.maxX, xFactor),
                lerp(box.minY, box.maxY, yFactor),
                lerp(box.minZ, box.maxZ, zFactor)
        );
    }

    private static double factor(double value, double min, double max) {
        double size = max - min;
        if (size <= 1.0E-6D) {
            return 0.5D;
        }
        return MathHelper.clamp_double((value - min) / size, 0.0D, 1.0D);
    }

    private static double lerp(double min, double max, double factor) {
        return min + (max - min) * factor;
    }
}
