package gq.yozakura.module.combat;

final class DisplacePlan {
    private DisplacePlan() {
    }

    static float displacedYaw(float baseYaw, float angle, boolean right) {
        return wrapYaw(baseYaw + (right ? angle : -angle));
    }

    private static float wrapYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw >= 180.0F) {
            yaw -= 360.0F;
        }
        if (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }
}
