package gq.yozakura.module.render;

/** Removes the vanilla sprint speed multiplier from the player's final FOV multiplier. */
public final class SprintFovPolicy {
    private static final double VANILLA_SPRINT_SPEED_MULTIPLIER = 1.3D;

    private SprintFovPolicy() {
    }

    public static float withoutSprint(float currentFov, double movementSpeed, float walkSpeed,
                                      boolean sprinting) {
        if (!sprinting || walkSpeed <= 0.0F || movementSpeed <= 0.0D
                || Float.isNaN(currentFov) || Float.isInfinite(currentFov)) {
            return currentFov;
        }
        double currentSpeedRatio = movementSpeed / walkSpeed;
        double currentMovementFov = (currentSpeedRatio + 1.0D) * 0.5D;
        double baseSpeedRatio = currentSpeedRatio / VANILLA_SPRINT_SPEED_MULTIPLIER;
        double baseMovementFov = (baseSpeedRatio + 1.0D) * 0.5D;
        if (currentMovementFov <= 0.0D) {
            return currentFov;
        }
        return (float) (currentFov * baseMovementFov / currentMovementFov);
    }
}
