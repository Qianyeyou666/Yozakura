package gq.yozakura.module.combat;

import java.util.Random;
import java.util.SplittableRandom;

// Derived from OpenVapeCN/VapeV4.21 AutoClickerTimingState (CC0-1.0).
final class VapeAutoClickTimingState {
    private static final long MIN_DRIFT_INTERVAL_MILLIS = 1200L;
    private static final long MAX_DRIFT_INTERVAL_MILLIS = 3200L;
    private static final long MIN_TARGET_INTERVAL_MILLIS = 30000L;
    private static final long MAX_TARGET_INTERVAL_MILLIS = 90000L;
    private static final long IDLE_RESET_THRESHOLD_MILLIS = 1200L;
    private static final double MAX_FATIGUE = 0.4D;

    private final Random legacyRandom;
    private final SplittableRandom random;
    private final long timingSalt;
    private double currentCps;
    private long previousDelayMillis;
    private double targetCps;
    private boolean initialized;
    private double targetOffset;
    private double pauseChanceOffset;
    private double repeatDelayChance = 0.06D;
    private double minimumCps;
    private double maximumCps;
    private long nextDriftTimestamp;
    private double fatigue;
    private int clicksSinceVariation;
    private int burstClicksRemaining;
    private long lastClickTimestamp;
    private long nextTargetTimestamp;
    private boolean legacyBurstActive;
    private int legacyBurstLength;
    private int legacyBurstClickCount;
    private boolean legacyFastPhaseActive = true;
    private int legacyFastPhaseClickCount;
    private int legacySlowPhaseClickCount;
    private int legacyFastPhaseLength;
    private int legacySlowPhaseLength;
    private long legacyLastDelayMillis;

    VapeAutoClickTimingState(long seed) {
        legacyRandom = new Random(seed);
        long randomSeed = mixSeed(seed ^ 0x9E3779B97F4A7C15L);
        random = new SplittableRandom(randomSeed);
        timingSalt = mixSeed(randomSeed - 3335678366873096957L);
    }

    long nextDelay(long now, double minimum, double maximum, AutoClickRandomization mode) {
        int minimumInt = (int) clampCps(Math.min(minimum, maximum));
        int maximumInt = (int) clampCps(Math.max(minimum, maximum));
        int range = maximumInt - minimumInt;
        int selectedCps = range <= 0
                ? minimumInt
                : legacyRandom.nextInt(range) + minimumInt + 1;

        if (mode == AutoClickRandomization.NORMAL) {
            legacyLastDelayMillis = 1000L / Math.max(1, selectedCps);
            return legacyLastDelayMillis;
        }
        if (mode == AutoClickRandomization.EXTRA) {
            return legacyDelay(selectedCps);
        }

        configureCpsRange(minimumInt, maximumInt);
        return advancedDelay(now);
    }

    private long legacyDelay(int selectedCps) {
        if (!legacyBurstActive) {
            legacyLastDelayMillis = 1000L / Math.max(1, selectedCps);
            if (legacyRandom.nextInt(4) == 1) {
                legacyBurstActive = true;
                legacyBurstLength = 1 + legacyRandom.nextInt(5);
            } else if (legacyRandom.nextInt(10) != 1 && legacyRandom.nextInt(10) == 1) {
                legacyBurstActive = true;
                legacyBurstLength = 5 + legacyRandom.nextInt(10);
            }
        }
        if (legacyBurstActive && ++legacyBurstClickCount >= legacyBurstLength) {
            legacyBurstClickCount = 0;
            legacyBurstActive = false;
        }
        if (legacyRandom.nextInt(48) % (legacyFastPhaseActive ? 6 : 10) == 0
                && !legacyBurstActive) {
            legacyLastDelayMillis += legacyRandom.nextInt(45) + 40L;
        }
        if (legacyFastPhaseActive) {
            if (++legacyFastPhaseClickCount >= legacyFastPhaseLength) {
                legacySlowPhaseLength = 75 + legacyRandom.nextInt(125);
                legacyFastPhaseActive = false;
                legacyFastPhaseClickCount = 0;
            }
            return legacyLastDelayMillis + (legacyRandom.nextInt(5) == 3 ? 50L : 25L);
        }
        if (++legacySlowPhaseClickCount >= legacySlowPhaseLength) {
            legacyFastPhaseActive = true;
            legacyFastPhaseLength = 7 + legacyRandom.nextInt(8);
            legacySlowPhaseClickCount = 0;
        }
        return legacyLastDelayMillis;
    }

    private void configureCpsRange(int minimum, int maximum) {
        double rawMinimum = Math.max(0, minimum);
        double rawMaximum = Math.max(rawMinimum, (double) maximum);
        double scaledMinimum = Math.max(0.5D, rawMinimum * 1.5D);
        double scaledMaximum = Math.max(0.5D, rawMaximum * 1.5D);
        minimumCps = scaledMinimum;
        maximumCps = Math.max(scaledMinimum + 0.1D, scaledMaximum);
        if (!initialized) {
            targetCps = midpoint(minimumCps, maximumCps);
            currentCps = clamp(targetCps + sampleNormalDistribution() * 0.25D,
                    minimumCps, maximumCps);
        }
    }

    private long advancedDelay(long now) {
        if (!initialized) {
            initialized = true;
            initializeTiming(now);
            lastClickTimestamp = now;
        }

        long idleDuration = lastClickTimestamp == 0L ? 0L : now - lastClickTimestamp;
        if (idleDuration >= IDLE_RESET_THRESHOLD_MILLIS) {
            double recoveryAmount = (double) Math.min(5000L, idleDuration) / 10.0D;
            fatigue = Math.max(0.0D, fatigue - 0.004D * recoveryAmount);
            burstClicksRemaining = random.nextDouble() < 0.7D ? 2 + random.nextInt(4) : 0;
            repeatDelayChance = 0.03D + random.nextDouble() * 0.06D;
            pauseChanceOffset = random.nextDouble() * 0.04D;
        }
        if (++clicksSinceVariation > 80 + random.nextInt(120)) {
            repeatDelayChance = 0.03D + random.nextDouble() * 0.06D;
            pauseChanceOffset = random.nextDouble() * 0.04D;
            clicksSinceVariation = 0;
        }
        if (now >= nextDriftTimestamp) {
            double drift = sampleNormalDistribution();
            currentCps += 0.25D * (targetCps - currentCps) + 0.45D * drift;
            if (random.nextDouble() < 0.03D) {
                currentCps += (random.nextDouble() - 0.5D) * 1.2D;
            }
            currentCps = clamp(currentCps, minimumCps, maximumCps);
            scheduleDrift(now);
        }
        if (now >= nextTargetTimestamp) {
            targetOffset = (random.nextDouble() * 2.0D - 1.0D) * 0.75D;
            targetCps = clamp(midpoint(minimumCps, maximumCps) + targetOffset,
                    minimumCps, maximumCps);
            scheduleTargetChange(now);
        }

        boolean burstClick = burstClicksRemaining > 0;
        double effectiveCps = currentCps;
        if (burstClick) {
            effectiveCps *= 1.0D + 0.05D * (0.4D + 0.8D * random.nextDouble());
            burstClicksRemaining--;
        }
        effectiveCps *= 1.0D - Math.min(MAX_FATIGUE, fatigue);
        effectiveCps = clamp(effectiveCps, minimumCps, maximumCps);

        double delayMillis = 1000.0D / effectiveCps * Math.exp(0.24D * sampleClickNoise());
        int maximumMicroJitter = 35 + random.nextInt(11) - 5;
        delayMillis += random.nextInt(Math.max(1, maximumMicroJitter) + 1);
        if (burstClick) {
            delayMillis += random.nextInt(15);
        }
        if (previousDelayMillis > 0L && random.nextDouble() < repeatDelayChance) {
            delayMillis = previousDelayMillis + random.nextInt(7) - 3L;
        }
        if (random.nextDouble() < 0.07D) {
            delayMillis *= 0.7D + random.nextDouble() * 0.2D;
        }
        double pauseChance = Math.min(0.04D + 0.12D * fatigue + pauseChanceOffset, 0.18D);
        if (random.nextDouble() < pauseChance) {
            delayMillis += 50 + random.nextInt(101);
        }

        fatigue = Math.min(MAX_FATIGUE, fatigue + 0.015D);
        lastClickTimestamp = now;
        previousDelayMillis = (long) Math.max(1.0D, Math.min(delayMillis, Integer.MAX_VALUE));
        return previousDelayMillis;
    }

    private void initializeTiming(long now) {
        scheduleDrift(now);
        scheduleTargetChange(now);
        targetOffset = (random.nextDouble() * 2.0D - 1.0D) * 0.75D;
        targetCps = clamp(midpoint(minimumCps, maximumCps) + targetOffset,
                minimumCps, maximumCps);
    }

    private void scheduleTargetChange(long now) {
        nextTargetTimestamp = now + randomDuration(now,
                MIN_TARGET_INTERVAL_MILLIS, MAX_TARGET_INTERVAL_MILLIS);
    }

    private void scheduleDrift(long now) {
        nextDriftTimestamp = now + randomDuration(now,
                MIN_DRIFT_INTERVAL_MILLIS, MAX_DRIFT_INTERVAL_MILLIS);
    }

    private long randomDuration(long now, long minimumDuration, long maximumDuration) {
        long timeComponent = (now ^ timingSalt) & 0xFFFFL;
        long durationRange = maximumDuration - minimumDuration + 1L;
        return minimumDuration
                + (Math.abs((int) timeComponent) + random.nextInt((int) durationRange)) % durationRange;
    }

    private double sampleNormalDistribution() {
        double sampleSum = 0.0D;
        for (int sampleIndex = 0; sampleIndex < 6; sampleIndex++) {
            sampleSum += random.nextDouble();
        }
        return (sampleSum - 3.0D) * 1.22474487139D;
    }

    private double sampleClickNoise() {
        double normalSample = sampleNormalDistribution();
        double variationRoll = random.nextDouble();
        if (variationRoll < 0.12D) {
            return (random.nextDouble() - 0.5D) * 3.0D;
        }
        if (variationRoll < 0.17D) {
            return normalSample * (1.4D + random.nextDouble() * 0.6D);
        }
        return normalSample;
    }

    private static double midpoint(double first, double second) {
        return (first + second) * 0.5D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clampCps(double cps) {
        if (Double.isNaN(cps) || cps <= 1.0D) {
            return 1.0D;
        }
        return Math.min(20.0D, cps);
    }

    private static long mixSeed(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }
}
