package gq.yozakura.core.modern;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Random;

final class ModernRaycastBridge {
    private static final Random RANDOM = new Random();
    private static Object lastHitResult;

    private ModernRaycastBridge() {
    }

    static void onClientTick(Object event) {
        try {
            if (!isEndPhase(event)) {
                return;
            }
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object level = ModernMinecraftAccess.level(minecraft);
            if (minecraft == null || player == null || level == null) {
                lastHitResult = null;
                return;
            }
            RaycastResult result = null;
            if (ModernForgeEventBridge.enabled("Reach") && canReach(minecraft, player)) {
                result = raycastEntities(minecraft, player, reachDistance(), reachExpand(), "Reach", true);
            }
            if (result == null && ModernForgeEventBridge.enabled("HitBoxes") && canHitBoxes(player)) {
                result = raycastEntities(minecraft, player,
                        ModernForgeEventBridge.number("HitBoxes", "Range", 3.2D),
                        ModernForgeEventBridge.number("HitBoxes", "Expand", 0.18D),
                        "HitBoxes", false);
            }
            if (result == null && ModernForgeEventBridge.enabled("Backtrack")) {
                result = raycastHistorical(player,
                        ModernForgeEventBridge.number("Backtrack", "Range", 3.6D),
                        ModernForgeEventBridge.number("Backtrack", "Expand", 0.08D),
                        ModernPacketBridge.backtrackHistory());
            }
            if (result != null && result.entity != null) {
                applyHitResult(minecraft, result);
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern raycast bridge tick failed", throwable);
        }
    }

    static Object hitResultEntity(Object minecraft) {
        Object hitResult = hitResult(minecraft);
        Object entity = entityFromHitResult(hitResult);
        if (entity != null) {
            return entity;
        }
        return entityFromHitResult(lastHitResult);
    }

    static RaycastResult raycastEntities(Object minecraft, Object player, double distance, double expand,
                                         String module, boolean respectReachGates) {
        if (minecraft == null || player == null || distance <= 0.0D) {
            return null;
        }
        Vec3 eyes = eyePosition(player);
        Vec3 look = lookVector(ModernRotationBridge.yaw(player), ModernRotationBridge.pitch(player));
        Vec3 end = eyes.add(look.scale(distance));
        Object best = null;
        Vec3 bestHit = null;
        double bestDistance = distance;
        double searchRangeSq = (distance + expand + 1.5D) * (distance + expand + 1.5D);
        for (Object entity : ModernMinecraftAccess.livingEntities(minecraft)) {
            if (entity == null || entity == player || !canHitEntity(entity, module, distance, respectReachGates)) {
                continue;
            }
            if (ModernRotationBridge.distanceSq(player, entity) > searchRangeSq) {
                continue;
            }
            Box box = entityBox(entity, expand + collisionBorder(entity));
            if (box == null) {
                continue;
            }
            Vec3 hit = box.clip(eyes, end);
            double currentDistance;
            if (box.contains(eyes)) {
                hit = eyes;
                currentDistance = 0.0D;
            } else if (hit != null) {
                currentDistance = eyes.distanceTo(hit);
            } else {
                continue;
            }
            if (currentDistance <= bestDistance) {
                best = entity;
                bestHit = hit;
                bestDistance = currentDistance;
            }
        }
        return best == null ? null : new RaycastResult(best, bestHit, bestDistance, null);
    }

    static RaycastResult raycastHistorical(Object player, double distance, double expand,
                                           Map<Integer, ArrayDeque<ModernPacketBridge.TrackedBox>> history) {
        if (player == null || history == null || history.isEmpty()) {
            return null;
        }
        Vec3 eyes = eyePosition(player);
        Vec3 look = lookVector(ModernRotationBridge.yaw(player), ModernRotationBridge.pitch(player));
        Vec3 end = eyes.add(look.scale(distance));
        ModernPacketBridge.TrackedBox best = null;
        Vec3 bestHit = null;
        double bestDistance = distance;
        for (ArrayDeque<ModernPacketBridge.TrackedBox> boxes : history.values()) {
            if (boxes == null) {
                continue;
            }
            for (ModernPacketBridge.TrackedBox tracked : boxes) {
                if (tracked == null || tracked.entity == null || tracked.box == null) {
                    continue;
                }
                if (ModernRotationBridge.distanceSq(player, tracked.entity) > (distance + expand + 1.0D)
                        * (distance + expand + 1.0D)) {
                    continue;
                }
                Box box = tracked.box.expand(expand);
                Vec3 hit = box.clip(eyes, end);
                double currentDistance;
                if (box.contains(eyes)) {
                    hit = eyes;
                    currentDistance = 0.0D;
                } else if (hit != null) {
                    currentDistance = eyes.distanceTo(hit);
                } else {
                    continue;
                }
                if (currentDistance <= bestDistance) {
                    best = tracked;
                    bestHit = hit;
                    bestDistance = currentDistance;
                }
            }
        }
        return best == null ? null : new RaycastResult(best.entity, bestHit, bestDistance, best.box);
    }

    static Box entityBox(Object entity, double expand) {
        Object box = ModernForgeEventBridge.invoke(entity, "getBoundingBox");
        if (box == null) {
            box = ModernForgeEventBridge.invoke(entity, "m_20191_");
        }
        if (box != null) {
            Box parsed = fromAabb(box);
            if (parsed != null) {
                return parsed.expand(expand);
            }
        }
        double width = Math.max(0.3D, width(entity));
        double half = width * 0.5D + expand;
        double x = ModernRotationBridge.x(entity);
        double y = ModernRotationBridge.y(entity);
        double z = ModernRotationBridge.z(entity);
        double height = Math.max(0.4D, ModernRotationBridge.height(entity));
        return new Box(x - half, y - expand, z - half, x + half, y + height + expand, z + half);
    }

    static void applyHitResult(Object minecraft, RaycastResult result) {
        if (minecraft == null || result == null || result.entity == null) {
            return;
        }
        Object hit = createEntityHitResult(result.entity, result.hit);
        if (hit == null) {
            return;
        }
        setHitResult(minecraft, hit);
        setPointedEntity(minecraft, result.entity);
        lastHitResult = hit;
    }

    private static Object hitResult(Object minecraft) {
        Object hitResult = ModernForgeEventBridge.field(minecraft, "hitResult");
        if (hitResult == null) {
            hitResult = ModernForgeEventBridge.field(minecraft, "f_91077_");
        }
        if (hitResult == null) {
            hitResult = ModernForgeEventBridge.field(minecraft, "objectMouseOver");
        }
        return hitResult;
    }

    private static Object entityFromHitResult(Object hitResult) {
        if (hitResult == null) {
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

    private static void setHitResult(Object minecraft, Object hit) {
        setField(minecraft, hit, "hitResult", "f_91077_", "objectMouseOver");
    }

    private static void setPointedEntity(Object minecraft, Object entity) {
        setField(minecraft, entity, "crosshairPickEntity", "f_91076_", "pointedEntity");
    }

    private static Object createEntityHitResult(Object entity, Vec3 hit) {
        Class<?> hitResultClass = ModernRotationBridge.classForName("net.minecraft.world.phys.EntityHitResult");
        if (hitResultClass == null) {
            return null;
        }
        Object vec = createVec3(hit == null ? new Vec3(ModernRotationBridge.x(entity),
                ModernRotationBridge.y(entity) + ModernRotationBridge.height(entity) * 0.5D,
                ModernRotationBridge.z(entity)) : hit);
        try {
            for (Constructor<?> constructor : hitResultClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                constructor.setAccessible(true);
                if (types.length == 2 && types[0].isInstance(entity) && vec != null && types[1].isInstance(vec)) {
                    return constructor.newInstance(entity, vec);
                }
                if (types.length == 1 && types[0].isInstance(entity)) {
                    return constructor.newInstance(entity);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object createVec3(Vec3 vector) {
        Class<?> vecClass = ModernRotationBridge.classForName("net.minecraft.world.phys.Vec3");
        if (vecClass == null || vector == null) {
            return null;
        }
        try {
            Constructor<?> constructor = vecClass.getDeclaredConstructor(double.class, double.class, double.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Double.valueOf(vector.x), Double.valueOf(vector.y), Double.valueOf(vector.z));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Box fromAabb(Object aabb) {
        double minX = doubleField(aabb, "minX", "f_82288_", "field_72340_a");
        double minY = doubleField(aabb, "minY", "f_82289_", "field_72338_b");
        double minZ = doubleField(aabb, "minZ", "f_82290_", "field_72339_c");
        double maxX = doubleField(aabb, "maxX", "f_82291_", "field_72336_d");
        double maxY = doubleField(aabb, "maxY", "f_82292_", "field_72337_e");
        double maxZ = doubleField(aabb, "maxZ", "f_82293_", "field_72334_f");
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
            return null;
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean canReach(Object minecraft, Object player) {
        if (ModernForgeEventBridge.bool("Reach", "Weapon Only", false) && !isHoldingWeapon(player)) {
            return false;
        }
        if (ModernForgeEventBridge.bool("Reach", "Moving Only", false) && !ModernMovementBridge.isMovingForBridge(player)) {
            return false;
        }
        if (ModernForgeEventBridge.bool("Reach", "Sprint Only", false) && !isSprinting(player)) {
            return false;
        }
        if (!ModernForgeEventBridge.bool("Reach", "Through Blocks", false) && isBlockHit(minecraft)) {
            return false;
        }
        return isAttackHeld(minecraft);
    }

    private static boolean canHitBoxes(Object player) {
        return !ModernForgeEventBridge.bool("HitBoxes", "Weapon Only", false) || isHoldingWeapon(player);
    }

    private static boolean canHitEntity(Object entity, String module, double distance, boolean respectReachGates) {
        if (entity == null || !isAlive(entity)) {
            return false;
        }
        if (isPlayer(entity)) {
            return ModernForgeEventBridge.bool(module, "Players", true);
        }
        if (isAnimal(entity)) {
            return ModernForgeEventBridge.bool(module, "Animals", false);
        }
        return ModernForgeEventBridge.bool(module, "Mobs", false);
    }

    private static double reachDistance() {
        double min = ModernForgeEventBridge.number("Reach", "Min Reach", 3.2D);
        double max = ModernForgeEventBridge.number("Reach", "Max Reach", 3.6D);
        if (!ModernForgeEventBridge.bool("Reach", "Random Reach", true)) {
            return Math.max(min, max);
        }
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        return low + RANDOM.nextDouble() * Math.max(0.0D, high - low);
    }

    private static double reachExpand() {
        return ModernForgeEventBridge.number("Reach", "Expand", 0.08D);
    }

    static Vec3 eyePosition(Object entity) {
        return new Vec3(ModernRotationBridge.x(entity),
                ModernRotationBridge.y(entity) + ModernRotationBridge.eyeHeight(entity),
                ModernRotationBridge.z(entity));
    }

    static Vec3 lookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(-yaw - 90.0F);
        double pitchRad = Math.toRadians(-pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3(Math.cos(yawRad) * cosPitch, Math.sin(pitchRad), Math.sin(yawRad) * cosPitch);
    }

    private static boolean isAttackHeld(Object minecraft) {
        Object options = ModernMinecraftAccess.options(minecraft);
        return ModernInputBridge.down(options, "attack");
    }

    private static boolean isBlockHit(Object minecraft) {
        Object hit = hitResult(minecraft);
        if (hit == null) {
            return false;
        }
        String name = hit.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("blockhitresult")) {
            Object type = ModernForgeEventBridge.invoke(hit, "getType");
            if (type == null) {
                type = ModernForgeEventBridge.invoke(hit, "m_6662_");
            }
            return type == null || "BLOCK".equals(String.valueOf(type));
        }
        return false;
    }

    private static boolean isHoldingWeapon(Object player) {
        Object stack = ModernForgeEventBridge.invoke(player, "getMainHandItem");
        if (stack == null) {
            stack = ModernForgeEventBridge.invoke(player, "m_21205_");
        }
        Object item = ModernForgeEventBridge.invoke(stack, "getItem");
        if (item == null) {
            item = ModernForgeEventBridge.invoke(stack, "m_41720_");
        }
        String name = item == null ? "" : item.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("sword") || name.contains("axe") || name.contains("trident") || name.contains("mace");
    }

    private static boolean isSprinting(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isSprinting");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_20142_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
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

    private static boolean isPlayer(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.player.Player");
    }

    private static boolean isAnimal(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.animal.Animal")
                || isInstance(entity, "net.minecraft.world.entity.animal.WaterAnimal")
                || isInstance(entity, "net.minecraft.world.entity.ambient.AmbientCreature")
                || isInstance(entity, "net.minecraft.world.entity.npc.Villager");
    }

    private static boolean isInstance(Object object, String className) {
        Class<?> type = ModernRotationBridge.classForName(className);
        return type != null && object != null && type.isInstance(object);
    }

    private static double collisionBorder(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getPickRadius");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_6088_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "getCollisionBorderSize");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.1D;
    }

    private static double width(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getBbWidth");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20205_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.6D;
    }

    private static double doubleField(Object object, String... names) {
        if (object == null) {
            return 0.0D;
        }
        for (String name : names) {
            Object value = ModernForgeEventBridge.field(object, name);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return 0.0D;
    }

    private static void setField(Object target, Object value, String... names) {
        if (target == null || names == null) {
            return;
        }
        for (String name : names) {
            try {
                Field field = ModernRotationBridge.findField(target.getClass(), name);
                if (field != null && (value == null || field.getType().isInstance(value))) {
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isEndPhase(Object event) {
        Object phase = ModernForgeEventBridge.field(event, "phase");
        if (phase == null) {
            phase = ModernForgeEventBridge.invoke(event, "phase");
        }
        if (phase == null) {
            phase = ModernForgeEventBridge.invoke(event, "getPhase");
        }
        return phase == null || "END".equals(String.valueOf(phase));
    }

    static final class RaycastResult {
        final Object entity;
        final Vec3 hit;
        final double distance;
        final Box sourceBox;

        RaycastResult(Object entity, Vec3 hit, double distance, Box sourceBox) {
            this.entity = entity;
            this.hit = hit;
            this.distance = distance;
            this.sourceBox = sourceBox;
        }
    }

    static final class Vec3 {
        final double x;
        final double y;
        final double z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        Vec3 scale(double value) {
            return new Vec3(x * value, y * value, z * value);
        }

        double distanceTo(Vec3 other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    static final class Box {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        Box expand(double amount) {
            return new Box(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount);
        }

        boolean contains(Vec3 point) {
            return point.x >= minX && point.x <= maxX
                    && point.y >= minY && point.y <= maxY
                    && point.z >= minZ && point.z <= maxZ;
        }

        Vec3 clip(Vec3 start, Vec3 end) {
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double dz = end.z - start.z;
            double tMin = 0.0D;
            double tMax = 1.0D;
            double[] result = clipAxis(start.x, dx, minX, maxX, tMin, tMax);
            if (result == null) {
                return null;
            }
            tMin = result[0];
            tMax = result[1];
            result = clipAxis(start.y, dy, minY, maxY, tMin, tMax);
            if (result == null) {
                return null;
            }
            tMin = result[0];
            tMax = result[1];
            result = clipAxis(start.z, dz, minZ, maxZ, tMin, tMax);
            if (result == null) {
                return null;
            }
            tMin = result[0];
            return new Vec3(start.x + dx * tMin, start.y + dy * tMin, start.z + dz * tMin);
        }

        private double[] clipAxis(double start, double delta, double min, double max, double tMin, double tMax) {
            if (Math.abs(delta) < 1.0E-7D) {
                return start >= min && start <= max ? new double[]{tMin, tMax} : null;
            }
            double first = (min - start) / delta;
            double second = (max - start) / delta;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            tMin = Math.max(tMin, first);
            tMax = Math.min(tMax, second);
            return tMax >= tMin ? new double[]{tMin, tMax} : null;
        }
    }
}
