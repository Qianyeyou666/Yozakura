package gq.yozakura.module.combat;

import java.util.Random;

final class VapeAutoClickJitter {
    private static final float MAXIMUM_OFFSET = 7.0F;

    private final Random random;
    private double horizontalOffset;
    private double verticalOffset;
    private double stepCount;
    private double remainingSteps;
    private float pendingYaw;
    private float pendingPitch;

    VapeAutoClickJitter(long seed) {
        random = new Random(seed);
    }

    void generate() {
        horizontalOffset = randomBetween(-MAXIMUM_OFFSET, MAXIMUM_OFFSET);
        verticalOffset = randomBetween(-MAXIMUM_OFFSET, MAXIMUM_OFFSET);
        stepCount = (Math.abs(horizontalOffset) + Math.abs(verticalOffset)) * 0.45D;
        remainingSteps = stepCount;
    }

    void advance() {
        if (remainingSteps > 0.0D && stepCount > 0.0D) {
            pendingYaw += (float) (horizontalOffset / stepCount);
            pendingPitch += (float) (verticalOffset / stepCount);
            remainingSteps -= 1.0D;
        } else {
            pendingYaw = moveTowardZero(pendingYaw);
            pendingPitch = moveTowardZero(pendingPitch);
        }
    }

    int yawDelta() {
        return (int) pendingYaw;
    }

    int pitchDelta() {
        return (int) -pendingPitch;
    }

    void applied() {
        pendingYaw = moveTowardZero(pendingYaw);
        pendingPitch = moveTowardZero(pendingPitch);
    }

    void reset() {
        horizontalOffset = 0.0D;
        verticalOffset = 0.0D;
        stepCount = 0.0D;
        remainingSteps = 0.0D;
        pendingYaw = 0.0F;
        pendingPitch = 0.0F;
    }

    private double randomBetween(double minimum, double maximum) {
        return minimum + random.nextDouble() * (maximum - minimum);
    }

    private static float moveTowardZero(float value) {
        if (value > 0.0F) {
            return Math.max(0.0F, value - 1.0F);
        }
        return Math.min(0.0F, value + 1.0F);
    }
}
