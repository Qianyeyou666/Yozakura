package gq.yozakura.core.modern;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

final class ModernCombatBridge {
    private static final Random RANDOM = new Random();
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<String, Class<?>>();
    private static final long TARGET_SCAN_DELAY_MS = 90L;
    private static final long SWITCH_DELAY_MS = 450L;
    private static final String[] COMBAT_MODULES = new String[]{
            "AimAssist", "KillAura", "AutoClicker", "AntiBot", "Criticals", "WTap",
            "BlockHit", "Velocity", "Reach", "HitBoxes", "Backtrack", "HitSelect"
    };

    private static long nextAttackAt;
    private static long nextClickAt;
    private static long nextTargetScanAt;
    private static long nextSwitchAt;
    private static long wTapRestoreAt;
    private static Object cachedTarget;
    private static int switchIndex;
    private static float assistedYaw;
    private static float assistedPitch;
    private static boolean rotationInitialized;
    private static boolean wTapActive;
    private static Object lastCombatTarget;
    private static long lastCombatFocusAt;

    private ModernCombatBridge() {
    }

    static void onClientTick(Object event) {
        try {
            Object phase = ModernForgeEventBridge.field(event, "phase");
            if (phase == null) {
                phase = ModernForgeEventBridge.invoke(event, "phase");
            }
            if (phase == null) {
                phase = ModernForgeEventBridge.invoke(event, "getPhase");
            }
            if (phase != null && !"END".equals(String.valueOf(phase))) {
                return;
            }

            boolean aimAssist = ModernForgeEventBridge.enabled("AimAssist");
            boolean killAura = ModernForgeEventBridge.enabled("KillAura");
            boolean autoClicker = ModernForgeEventBridge.enabled("AutoClicker");
            if (!aimAssist && !killAura && !autoClicker) {
                resetState();
                return;
            }

            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object level = ModernMinecraftAccess.level(minecraft);
            if (minecraft == null || player == null || level == null) {
                resetState();
                return;
            }
            handleWTapTick(player);
            if ((aimAssist && ModernForgeEventBridge.bool("AimAssist", "Weapon Only", false)
                    || killAura && ModernForgeEventBridge.bool("KillAura", "Weapon Only", false))
                    && !isHoldingWeapon(player)) {
                resetTargetState();
                return;
            }
            if (screenOpen(minecraft)) {
                resetTargetState();
                return;
            }

            if (aimAssist && !killAura
                    && ModernForgeEventBridge.bool("AimAssist", "Require Mouse Down", true)
                    && !ModernInputBridge.down(ModernMinecraftAccess.options(minecraft), "attack")) {
                resetTargetState();
                return;
            }

            if (autoClicker) {
                handleAutoClick(minecraft, player);
            }

            if (!aimAssist && !killAura) {
                return;
            }

            Object target = chooseTarget(minecraft, player, aimAssist, killAura);
            if (target == null) {
                resetTargetState();
                return;
            }
            markCombatFocus(target);

            float[] rotations = ModernRotationBridge.rotationsToEntity(player, target);
            if (rotations == null) {
                resetTargetState();
                return;
            }

            if (killAura) {
                handleKillAura(minecraft, player, target, rotations);
            } else if (aimAssist) {
                float[] assisted = aimAt(player, rotations, true, false);
                if (assisted != null) {
                    ModernRotationBridge.applyVisibleRotation(player, assisted[0], assisted[1]);
                }
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern combat bridge tick failed", throwable);
        }
    }

    private static Object chooseTarget(Object minecraft, Object player, boolean aimAssist, boolean killAura) {
        long now = System.currentTimeMillis();
        if (cachedTarget != null && isValidTarget(minecraft, player, cachedTarget, aimAssist, killAura)) {
            return cachedTarget;
        }
        if (now < nextTargetScanAt) {
            return cachedTarget;
        }
        nextTargetScanAt = now + TARGET_SCAN_DELAY_MS;

        List<Object> entities = ModernMinecraftAccess.livingEntities(minecraft);
        ArrayList<Object> candidates = new ArrayList<Object>();
        for (Object entity : entities) {
            if (isValidTarget(minecraft, player, entity, aimAssist, killAura)) {
                candidates.add(entity);
            }
        }
        if (candidates.isEmpty()) {
            cachedTarget = null;
            return null;
        }

        sortTargets(player, candidates, killAura);
        if (killAura && "Switch".equalsIgnoreCase(ModernForgeEventBridge.mode("KillAura", "Mode", "Single"))) {
            if (now >= nextSwitchAt) {
                switchIndex++;
                nextSwitchAt = now + SWITCH_DELAY_MS;
            }
            if (switchIndex < 0) {
                switchIndex = 0;
            }
            switchIndex %= candidates.size();
            cachedTarget = candidates.get(switchIndex);
            return cachedTarget;
        }
        switchIndex = 0;
        cachedTarget = candidates.get(0);
        return cachedTarget;
    }

    private static void sortTargets(final Object player, ArrayList<Object> candidates, boolean killAura) {
        final String sort = killAura ? ModernForgeEventBridge.mode("KillAura", "Sort", "Distance") : "FOV";
        candidates.sort(new Comparator<Object>() {
            @Override
            public int compare(Object first, Object second) {
                int primary = Double.compare(targetScore(player, first, sort), targetScore(player, second, sort));
                return primary != 0 ? primary : Double.compare(distanceSq(player, first), distanceSq(player, second));
            }
        });
    }

    private static double targetScore(Object player, Object entity, String sort) {
        if ("Health".equalsIgnoreCase(sort)) {
            return health(entity);
        }
        if ("Hurt Time".equalsIgnoreCase(sort) || "HurtTime".equalsIgnoreCase(sort)) {
            return intValue(fieldOrInvoke(entity, "hurtTime", "f_20916_", "getHurtTime"), 0);
        }
        if ("FOV".equalsIgnoreCase(sort) || "FoV".equalsIgnoreCase(sort)) {
            float[] rotations = ModernRotationBridge.rotationsToEntity(player, entity);
            return rotations == null ? 180.0D : ModernRotationBridge.angleDelta(ModernRotationBridge.yaw(player), rotations[0]);
        }
        return distanceSq(player, entity);
    }

    private static boolean isValidTarget(Object minecraft, Object player, Object entity, boolean aimAssist, boolean killAura) {
        if (entity == null || entity == player) {
            return false;
        }
        if (!isAlive(entity)) {
            return false;
        }
        if (!isCombatTarget(entity)) {
            return false;
        }
        if (ModernForgeEventBridge.enabled("AntiBot") && isProbablyBot(entity)) {
            return false;
        }
        if (!targetTypeAllowed(entity, aimAssist, killAura)) {
            return false;
        }
        double distanceSq = distanceSq(player, entity);
        double range = targetRange(aimAssist, killAura);
        if (distanceSq > range * range) {
            return false;
        }
        double fov = targetFov(aimAssist, killAura);
        if (fov < 360.0D) {
            float[] rotations = ModernRotationBridge.rotationsToEntity(player, entity);
            if (rotations == null || ModernRotationBridge.angleDelta(ModernRotationBridge.yaw(player), rotations[0]) > fov * 0.5D) {
                return false;
            }
        }
        if (!throughWallsAllowed(aimAssist, killAura) && !hasLineOfSight(player, entity)) {
            return false;
        }
        if (killAura) {
            int hurtLimit = (int) ModernForgeEventBridge.number("KillAura", "Hurt Time", 10.0D);
            int hurtTime = intValue(fieldOrInvoke(entity, "hurtTime", "f_20916_", "getHurtTime"), 0);
            if (hurtTime > hurtLimit) {
                return false;
            }
        }
        return true;
    }

    private static double targetRange(boolean aimAssist, boolean killAura) {
        double range = 0.0D;
        if (killAura) {
            range = ModernForgeEventBridge.number("KillAura", "Range", 3.2D);
        } else if (aimAssist) {
            range = ModernForgeEventBridge.number("AimAssist", "Range", 4.5D);
        }
        if (ModernForgeEventBridge.enabled("Reach")) {
            range = Math.max(range, ModernForgeEventBridge.number("Reach", "Max Reach", range));
        }
        return Math.max(1.0D, range);
    }

    private static double targetFov(boolean aimAssist, boolean killAura) {
        if (killAura) {
            return ModernForgeEventBridge.number("KillAura", "FOV", 360.0D);
        }
        return aimAssist ? ModernForgeEventBridge.number("AimAssist", "FOV", 90.0D) : 360.0D;
    }

    private static boolean throughWallsAllowed(boolean aimAssist, boolean killAura) {
        if (killAura) {
            return ModernForgeEventBridge.bool("KillAura", "Through Walls", false);
        }
        return aimAssist && ModernForgeEventBridge.bool("AimAssist", "Through Walls", false);
    }

    private static boolean targetTypeAllowed(Object entity, boolean aimAssist, boolean killAura) {
        String module = killAura ? "KillAura" : "AimAssist";
        if (isPlayerEntity(entity)) {
            return ModernForgeEventBridge.bool(module, "Players", true);
        }
        if (isAnimalEntity(entity)) {
            return ModernForgeEventBridge.bool(module, "Animals", false);
        }
        if (isMobEntity(entity)) {
            return ModernForgeEventBridge.bool(module, "Mobs", false);
        }
        return ModernForgeEventBridge.bool(module, "Mobs", false);
    }

    private static boolean isCombatTarget(Object entity) {
        String name = simpleName(entity);
        if (name == null) {
            return true;
        }
        if (name.contains("armorstand") || name.contains("marker")) {
            return false;
        }
        if (name.contains("spectator")) {
            return false;
        }
        if (name.contains("fake")) {
            return false;
        }
        return true;
    }

    private static boolean isAlive(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "isAlive");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_6084_");
        }
        if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
            return false;
        }
        Object health = ModernForgeEventBridge.invoke(entity, "getHealth");
        if (health == null) {
            health = ModernForgeEventBridge.invoke(entity, "m_21223_");
        }
        return !(health instanceof Number) || ((Number) health).floatValue() > 0.0F;
    }

    private static float[] aimAt(Object player, float[] targetRotations, boolean aimAssist, boolean killAura) {
        if (targetRotations == null || (!aimAssist && !killAura)) {
            return null;
        }
        if (!rotationInitialized) {
            assistedYaw = ModernRotationBridge.yaw(player);
            assistedPitch = ModernRotationBridge.pitch(player);
            rotationInitialized = true;
        }
        if (!aimAssist) {
            assistedYaw = targetRotations[0];
            assistedPitch = targetRotations[1];
            return new float[]{assistedYaw, ModernRotationBridge.clampPitch(assistedPitch)};
        }
        double speed = ModernForgeEventBridge.number("AimAssist", "Speed", 45.0D);
        double smooth = ModernForgeEventBridge.number("AimAssist", "Smooth", 18.0D);
        float yaw = smoothAimAngle(assistedYaw, targetRotations[0], speed, smooth, true);
        float pitch = ModernForgeEventBridge.bool("AimAssist", "Vertical Aim", true)
                ? smoothAimAngle(assistedPitch, targetRotations[1], speed * 0.72D, smooth * 1.25D, false)
                : ModernRotationBridge.pitch(player);
        assistedYaw = yaw;
        assistedPitch = pitch;
        Object minecraft = ModernMinecraftAccess.minecraft();
        float[] snapped = snapToSensitivity(yaw, ModernRotationBridge.clampPitch(pitch),
                ModernInputBridge.mouseSensitivity(minecraft));
        assistedYaw = snapped[0];
        assistedPitch = snapped[1];
        return snapped;
    }

    private static float smoothAimAngle(float current, float target, double speed, double smooth, boolean yawAxis) {
        float delta = ModernRotationBridge.wrapDegrees(target - current);
        float abs = Math.abs(delta);

        // 参考 openzen 实现：简单的除法平滑
        // delta / smooth 产生渐进式接近效果
        float step = abs / (float) Math.max(1.0, smooth);

        // speed 控制最大每帧移动量
        float maxStep = (float) (speed / 10.0);
        if (step > maxStep) {
            step = maxStep;
        }

        // 如果很接近目标，直接到达
        if (abs <= step || abs < 0.5F) {
            return target;
        }

        return current + Math.copySign(step, delta);
    }

    private static float[] snapToSensitivity(float yaw, float pitch, double sensitivity) {
        double scaled = sensitivity * 0.6D + 0.2D;
        float quantum = (float) (scaled * scaled * scaled * 8.0D * 0.15D);
        if (quantum <= 0.0F || Float.isNaN(quantum)) {
            return new float[]{yaw, ModernRotationBridge.clampPitch(pitch)};
        }
        return new float[]{
                Math.round(yaw / quantum) * quantum,
                ModernRotationBridge.clampPitch(Math.round(pitch / quantum) * quantum)
        };
    }

    private static boolean shouldApplyVisibleRotation(boolean aimAssist, boolean killAura) {
        if (aimAssist) {
            return true;
        }
        String mode = ModernForgeEventBridge.mode("KillAura", "Rotations", "Silent");
        return killAura && ("Legit".equalsIgnoreCase(mode) || "Lock View".equalsIgnoreCase(mode));
    }

    private static void handleAutoClick(Object minecraft, Object player) {
        long now = System.currentTimeMillis();
        if (now < nextClickAt) {
            return;
        }
        if (ModernForgeEventBridge.bool("AutoClicker", "Weapon Only", false) && !isHoldingWeapon(player)) {
            nextClickAt = now + 60L;
            return;
        }
        Object options = ModernMinecraftAccess.options(minecraft);
        if (!ModernInputBridge.down(options, "attack")) {
            nextClickAt = 0L;
            return;
        }

        if (isDragging(player)) {
            nextClickAt = now + 40L;
            return;
        }

        Object hitTarget = hitResultEntity(minecraft);
        if (hitTarget != null && isAlive(hitTarget) && !isProbablyBot(hitTarget)) {
            markCombatFocus(hitTarget);
            performAttack(minecraft, player, hitTarget, false);
        } else {
            swing(player);
        }
        nextClickAt = now + Math.max(30L, Math.round(randomCpsDelay(
                ModernForgeEventBridge.number("AutoClicker", "Min CPS", 8.0D),
                ModernForgeEventBridge.number("AutoClicker", "Max CPS", 12.0D))));
    }

    private static void handleKillAura(Object minecraft, Object player, Object target, float[] rotations) {
        if (rotations == null || target == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAttackAt) {
            return;
        }
        if (ModernForgeEventBridge.enabled("AntiBot") && isProbablyBot(target)) {
            return;
        }
        double range = ModernForgeEventBridge.number("KillAura", "Range", 3.2D);
        if (distanceSq(player, target) > range * range) {
            return;
        }
        String rotationMode = ModernForgeEventBridge.mode("KillAura", "Rotations", "Silent");
        if ("Silent".equalsIgnoreCase(rotationMode)) {
            boolean moveFix = !"None".equalsIgnoreCase(ModernForgeEventBridge.mode("KillAura", "Move Fix", "None"));
            ModernRotationBridge.sendRotationPacket(player, rotations[0], rotations[1]);
            ModernRotationBridge.requestSilentRotation(player, rotations[0], rotations[1], moveFix);
        } else if ("Legit".equalsIgnoreCase(rotationMode) || "Lock View".equalsIgnoreCase(rotationMode)) {
            ModernRotationBridge.applyVisibleRotation(player, rotations[0], rotations[1]);
        }
        ModernRaycastBridge.applyHitResult(minecraft,
                new ModernRaycastBridge.RaycastResult(target, null, Math.sqrt(distanceSq(player, target)), null));
        performAttack(minecraft, player, target, true);
        nextAttackAt = now + Math.max(35L, Math.round(1000.0D
                / Math.max(1.0D, ModernForgeEventBridge.number("KillAura", "APS", 12.0D))));
    }

    private static double randomCpsDelay(double minCps, double maxCps) {
        double min = Math.max(1.0D, Math.min(minCps, maxCps));
        double max = Math.max(min, Math.max(minCps, maxCps));
        double cps = min + RANDOM.nextDouble() * (max - min + 0.001D);
        return 1000.0D / Math.max(1.0D, cps);
    }

    private static void performAttack(Object minecraft, Object player, Object target, boolean allowCriticals) {
        Object gameMode = ModernMinecraftAccess.gameMode(minecraft);
        if (gameMode == null || target == null) {
            return;
        }
        if (allowCriticals) {
            tryCritical(player);
        }

        boolean attacked = false;
        try {
            Method attack = findMethod(gameMode.getClass(), "attack", 2);
            if (attack == null) {
                attack = findMethod(gameMode.getClass(), "m_105005_", 2);
            }
            if (attack != null) {
                attack.setAccessible(true);
                attack.invoke(gameMode, player, target);
                attacked = true;
            }
        } catch (Throwable ignored) {
        }

        if (!attacked) {
            attacked = sendAttackPacket(player, target);
        }
        if (!attacked) {
            return;
        }
        swing(player);
        onAttackModules(player, target);
    }

    private static void onAttackModules(Object player, Object target) {
        markCombatFocus(target);
        if (ModernForgeEventBridge.enabled("WTap")) {
            startWTap(player);
        }
        if (ModernForgeEventBridge.enabled("BlockHit")) {
            startUseItem(player);
        }
    }

    private static void tryCritical(Object player) {
        if (!ModernForgeEventBridge.enabled("Criticals") || !canCritical(player)) {
            return;
        }
        if (ModernForgeEventBridge.bool("Criticals", "Weapon Only", true) && !isHoldingWeapon(player)) {
            return;
        }
        String mode = ModernForgeEventBridge.mode("Criticals", "Mode", "Packet");
        if ("NoGround".equalsIgnoreCase(mode)) {
            ModernRotationBridge.sendPositionPacket(player, ModernRotationBridge.x(player),
                    ModernRotationBridge.y(player), ModernRotationBridge.z(player), false);
            return;
        }
        if ("Jump".equalsIgnoreCase(mode) || "MiniJump".equalsIgnoreCase(mode)) {
            Object jumped = ModernForgeEventBridge.invoke(player, "jumpFromGround");
            if (jumped == null) {
                ModernForgeEventBridge.invoke(player, "jump");
            }
            return;
        }
        double px = ModernRotationBridge.x(player);
        double py = ModernRotationBridge.y(player);
        double pz = ModernRotationBridge.z(player);
        boolean sentFirst = ModernRotationBridge.sendPositionPacket(player, px, py + 0.0625D, pz, false);
        if (sentFirst) {
            ModernRotationBridge.sendPositionPacket(player, px, py, pz, false);
        }
    }

    private static boolean canCritical(Object player) {
        Object onGround = ModernForgeEventBridge.invoke(player, "onGround");
        if (onGround == null) {
            onGround = ModernForgeEventBridge.invoke(player, "m_20096_");
        }
        Object water = ModernForgeEventBridge.invoke(player, "isInWater");
        if (water == null) {
            water = ModernForgeEventBridge.invoke(player, "m_20069_");
        }
        Object lava = ModernForgeEventBridge.invoke(player, "isInLava");
        if (lava == null) {
            lava = ModernForgeEventBridge.invoke(player, "m_20077_");
        }
        Object passenger = ModernForgeEventBridge.invoke(player, "isPassenger");
        if (passenger == null) {
            passenger = ModernForgeEventBridge.invoke(player, "m_20159_");
        }
        return booleanValue(onGround, true)
                && !booleanValue(water, false)
                && !booleanValue(lava, false)
                && !booleanValue(passenger, false);
    }

    private static boolean sendAttackPacket(Object player, Object target) {
        try {
            Class<?> packetClass = classForName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
            Method factory = findStaticMethod(packetClass, "createAttackPacket", 2);
            if (factory == null) {
                factory = findStaticMethod(packetClass, "m_179694_", 2);
            }
            if (factory == null) {
                return false;
            }
            factory.setAccessible(true);
            Object packet = factory.invoke(null, target, Boolean.valueOf(isShiftDown(player)));
            return sendPacket(player, packet);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sendPacket(Object player, Object packet) {
        return ModernRotationBridge.sendPacket(player, packet);
    }

    private static void swing(Object player) {
        Object hand = enumConstant("net.minecraft.world.InteractionHand", "MAIN_HAND");
        if (hand != null) {
            Object result = ModernForgeEventBridge.invoke(player, "swing", hand);
            if (result == null) {
                ModernForgeEventBridge.invoke(player, "m_6674_", hand);
            }
        }
    }

    private static void startUseItem(Object player) {
        String mode = ModernForgeEventBridge.mode("BlockHit", "Mode", "Manual");
        if ("Manual".equalsIgnoreCase(mode)) {
            return;
        }
        Object hand = ModernForgeEventBridge.invoke(player, "getUsedItemHand");
        if (hand == null) {
            hand = ModernForgeEventBridge.invoke(player, "m_7655_");
        }
        if (hand == null) {
            hand = enumConstant("net.minecraft.world.InteractionHand", "MAIN_HAND");
        }
        if (hand != null) {
            ModernForgeEventBridge.invoke(player, "startUsingItem", hand);
            ModernForgeEventBridge.invoke(player, "m_6672_", hand);
        }
    }

    private static void startWTap(Object player) {
        if (RANDOM.nextDouble() * 100.0D > ModernForgeEventBridge.number("WTap", "Chance", 100.0D)) {
            return;
        }
        long ticks = Math.max(1L, Math.round(ModernForgeEventBridge.number("WTap", "Action Ticks", 1.0D)));
        wTapRestoreAt = System.currentTimeMillis() + ticks * 50L;
        wTapActive = true;
        setSprinting(player, false);
    }

    private static void handleWTapTick(Object player) {
        if (!wTapActive) {
            return;
        }
        if (System.currentTimeMillis() < wTapRestoreAt) {
            setSprinting(player, false);
            return;
        }
        setSprinting(player, true);
        wTapActive = false;
    }

    private static void setSprinting(Object player, boolean sprinting) {
        ModernForgeEventBridge.invoke(player, "setSprinting", Boolean.valueOf(sprinting));
        ModernForgeEventBridge.invoke(player, "m_6858_", Boolean.valueOf(sprinting));
    }

    private static boolean isDragging(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isUsingItem");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_6117_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isProbablyBot(Object entity) {
        String name = entityName(entity);
        String type = simpleName(entity);
        return containsAny(name, "fakeplayer", "bot", "[npc]", "npc")
                || containsAny(type, "fakeplayer", "bot");
    }

    private static boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (lower.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static double distanceSq(Object first, Object second) {
        return ModernRotationBridge.distanceSq(first, second);
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) {
        Class<?> current = owner;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static Method findStaticMethod(Class<?> owner, String name, int parameterCount) {
        for (Method method : owner.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getName().equals(name)
                    && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        for (Method method : owner.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getName().equals(name)
                    && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static Object hitResultEntity(Object minecraft) {
        Object raycastEntity = ModernRaycastBridge.hitResultEntity(minecraft);
        if (raycastEntity != null) {
            return raycastEntity;
        }
        Object hitResult = ModernForgeEventBridge.field(minecraft, "hitResult");
        if (hitResult == null) {
            hitResult = ModernForgeEventBridge.field(minecraft, "f_91077_");
        }
        if (hitResult == null) {
            hitResult = ModernForgeEventBridge.field(minecraft, "objectMouseOver");
        }
        String hitResultName = simpleName(hitResult);
        if (hitResult == null || hitResultName == null || !hitResultName.contains("entityhitresult")) {
            return null;
        }
        Object entity = ModernForgeEventBridge.invoke(hitResult, "getEntity");
        if (entity == null) {
            entity = ModernForgeEventBridge.invoke(hitResult, "m_82443_");
        }
        if (entity == null) {
            entity = ModernForgeEventBridge.field(hitResult, "entity");
        }
        return entity;
    }

    private static boolean screenOpen(Object minecraft) {
        Object screen = ModernForgeEventBridge.field(minecraft, "screen");
        if (screen == null) {
            screen = ModernForgeEventBridge.field(minecraft, "f_91080_");
        }
        if (screen == null) {
            screen = ModernForgeEventBridge.field(minecraft, "currentScreen");
        }
        return screen != null;
    }

    private static boolean isHoldingWeapon(Object player) {
        Object stack = ModernForgeEventBridge.invoke(player, "getMainHandItem");
        if (stack == null) {
            stack = ModernForgeEventBridge.invoke(player, "m_21205_");
        }
        if (stack == null) {
            return false;
        }
        Object empty = ModernForgeEventBridge.invoke(stack, "isEmpty");
        if (empty == null) {
            empty = ModernForgeEventBridge.invoke(stack, "m_41619_");
        }
        if (Boolean.TRUE.equals(empty)) {
            return false;
        }
        Object item = ModernForgeEventBridge.invoke(stack, "getItem");
        if (item == null) {
            item = ModernForgeEventBridge.invoke(stack, "m_41720_");
        }
        String itemName = simpleName(item);
        if (containsAny(itemName, "sword", "axe", "trident", "mace")) {
            return true;
        }
        Object damageable = ModernForgeEventBridge.invoke(stack, "isDamageableItem");
        if (damageable == null) {
            damageable = ModernForgeEventBridge.invoke(stack, "m_41763_");
        }
        return Boolean.TRUE.equals(damageable);
    }

    private static boolean hasLineOfSight(Object player, Object entity) {
        Object value = ModernForgeEventBridge.invoke(player, "hasLineOfSight", entity);
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_20280_", entity);
        }
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static boolean isShiftDown(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isShiftKeyDown");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_20142_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isPlayerEntity(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.player.Player");
    }

    private static boolean isMobEntity(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.Mob")
                || isInstance(entity, "net.minecraft.world.entity.monster.Monster")
                || isInstance(entity, "net.minecraft.world.entity.monster.Enemy")
                || isInstance(entity, "net.minecraft.world.entity.monster.Slime")
                || containsAny(simpleName(entity), "monster", "mob", "slime", "zombie", "skeleton", "creeper", "spider");
    }

    private static boolean isAnimalEntity(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.animal.Animal")
                || isInstance(entity, "net.minecraft.world.entity.animal.WaterAnimal")
                || isInstance(entity, "net.minecraft.world.entity.ambient.AmbientCreature")
                || isInstance(entity, "net.minecraft.world.entity.npc.Villager")
                || containsAny(simpleName(entity), "animal", "villager", "squid", "bat");
    }

    private static boolean isInstance(Object object, String className) {
        Class<?> type = classForName(className);
        return type != null && object != null && type.isInstance(object);
    }

    private static Class<?> classForName(String name) {
        synchronized (CLASS_CACHE) {
            if (CLASS_CACHE.containsKey(name)) {
                return CLASS_CACHE.get(name);
            }
        }
        Class<?> type = null;
        try {
            type = ModernForgeEventBridge.findClass(name);
        } catch (Throwable ignored) {
        }
        synchronized (CLASS_CACHE) {
            CLASS_CACHE.put(name, type);
        }
        return type;
    }

    private static Object enumConstant(String className, String constant) {
        Class<?> type = classForName(className);
        if (type == null || !type.isEnum()) {
            return null;
        }
        try {
            return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), constant);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object fieldOrInvoke(Object target, String fieldName, String obfuscatedField, String methodName) {
        Object value = ModernForgeEventBridge.field(target, fieldName);
        if (value == null) {
            value = ModernForgeEventBridge.field(target, obfuscatedField);
        }
        if (value == null) {
            value = ModernForgeEventBridge.invoke(target, methodName);
        }
        return value;
    }

    private static String entityName(Object entity) {
        Object component = ModernForgeEventBridge.invoke(entity, "getDisplayName");
        if (component == null) {
            component = ModernForgeEventBridge.invoke(entity, "getName");
        }
        if (component == null) {
            component = ModernForgeEventBridge.invoke(entity, "m_5446_");
        }
        if (component == null) {
            component = ModernForgeEventBridge.invoke(entity, "m_7755_");
        }
        Object text = ModernForgeEventBridge.invoke(component, "getString");
        if (text == null) {
            text = ModernForgeEventBridge.invoke(component, "m_130668_");
        }
        return text == null ? null : String.valueOf(text);
    }

    private static double health(Object entity) {
        Object health = ModernForgeEventBridge.invoke(entity, "getHealth");
        if (health == null) {
            health = ModernForgeEventBridge.invoke(entity, "m_21223_");
        }
        return health instanceof Number ? ((Number) health).doubleValue() : 0.0D;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String simpleName(Object object) {
        if (object == null) {
            return null;
        }
        String name = object.getClass().getSimpleName();
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private static void resetState() {
        resetTargetState();
        nextClickAt = 0L;
        wTapRestoreAt = 0L;
        wTapActive = false;
        lastCombatTarget = null;
        lastCombatFocusAt = 0L;
    }

    private static void resetTargetState() {
        cachedTarget = null;
        nextAttackAt = 0L;
        nextTargetScanAt = 0L;
        nextSwitchAt = 0L;
        switchIndex = 0;
        rotationInitialized = false;
        assistedYaw = 0.0F;
        assistedPitch = 0.0F;
    }

    static boolean hasCombatFocus() {
        return lastCombatTarget != null && System.currentTimeMillis() - lastCombatFocusAt < 1200L;
    }

    static Object combatTarget() {
        return hasCombatFocus() ? lastCombatTarget : cachedTarget;
    }

    private static void markCombatFocus(Object target) {
        if (target == null) {
            return;
        }
        lastCombatTarget = target;
        lastCombatFocusAt = System.currentTimeMillis();
    }
}
