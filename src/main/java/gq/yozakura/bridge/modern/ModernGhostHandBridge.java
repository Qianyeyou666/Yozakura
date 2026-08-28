package gq.yozakura.bridge.modern;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

final class ModernGhostHandBridge {
    private ModernGhostHandBridge() {
    }

    static ModernRaycastBridge.RaycastResult apply(Object minecraft, Object player) {
        if (minecraft == null || player == null || !ModernForgeEventBridge.enabled("GhostHand")) {
            return null;
        }
        Object current = currentHitEntity(minecraft);
        if (current == null || !shouldSkip(player, current)) {
            return null;
        }

        double reach = interactionRange(minecraft);
        ModernRaycastBridge.Vec3 eyes = ModernRaycastBridge.eyePosition(player);
        ModernRaycastBridge.Vec3 look = ModernRaycastBridge.lookVector(
                ModernRotationBridge.yaw(player), ModernRotationBridge.pitch(player));
        ModernRaycastBridge.Vec3 end = eyes.add(look.scale(reach));
        double blockDistance = blockHitDistance(minecraft, player, eyes, end, reach);
        double bestDistance = blockDistance;
        Object best = null;
        ModernRaycastBridge.Vec3 bestHit = null;
        Object skipped = current;

        double searchRangeSq = (reach + 1.5D) * (reach + 1.5D);
        for (Object entity : ModernMinecraftAccess.entities(minecraft)) {
            if (entity == null || entity == player || entity == skipped || shouldSkip(player, entity)
                    || isItemFrame(entity) || !canCollide(entity)
                    || ModernRotationBridge.distanceSq(player, entity) > searchRangeSq) {
                continue;
            }
            ModernRaycastBridge.Box box = ModernRaycastBridge.entityBox(entity, pickRadius(entity));
            if (box == null) {
                continue;
            }
            ModernRaycastBridge.Vec3 hit = box.clip(eyes, end);
            double distance;
            if (box.contains(eyes)) {
                hit = eyes;
                distance = 0.0D;
            } else if (hit != null) {
                distance = eyes.distanceTo(hit);
            } else {
                continue;
            }
            if (distance <= bestDistance) {
                best = entity;
                bestHit = hit;
                bestDistance = distance;
            }
        }
        if (best == null || bestHit == null) {
            // The current vanilla target was intentionally consumed. Returning a marker
            // prevents Reach/HitBoxes from selecting the skipped player again this tick.
            return new ModernRaycastBridge.RaycastResult(null, null, blockDistance, null);
        }
        ModernRaycastBridge.RaycastResult result = new ModernRaycastBridge.RaycastResult(
                best, bestHit, bestDistance, null);
        ModernRaycastBridge.applyHitResult(minecraft, result);
        return result;
    }

    private static Object currentHitEntity(Object minecraft) {
        return ModernRaycastBridge.currentHitEntity(minecraft);
    }

    private static boolean shouldSkip(Object player, Object entity) {
        boolean playerEntity = isPlayer(entity);
        boolean bot = ModernCombatBridge.isProbablyBotForBridge(entity);
        boolean teamOnly = ModernForgeEventBridge.bool("GhostHand", "Team Only", true);
        boolean sameTeam = isSameTeam(player, entity);
        boolean ignoreWeapons = ModernForgeEventBridge.bool("GhostHand", "Ignore Weapons", false);
        boolean protectedWeapon = hasProtectedWeapon(player);
        return ModernGhostHandPolicy.shouldSkip(playerEntity, bot, teamOnly, sameTeam,
                ignoreWeapons, protectedWeapon);
    }

    private static boolean isSameTeam(Object player, Object entity) {
        Object allied = ModernForgeEventBridge.invoke(player, "isAlliedTo", entity);
        if (allied == null) {
            allied = ModernForgeEventBridge.invoke(player, "m_7307_", entity);
        }
        if (allied instanceof Boolean) {
            return ((Boolean) allied).booleanValue();
        }
        Object selfTeam = team(player);
        Object targetTeam = team(entity);
        if (selfTeam == null || targetTeam == null) {
            return false;
        }
        String selfName = teamName(selfTeam);
        String targetName = teamName(targetTeam);
        return selfName != null && selfName.equals(targetName);
    }

    private static Object team(Object player) {
        Object team = ModernForgeEventBridge.invoke(player, "getTeam");
        if (team == null) {
            team = ModernForgeEventBridge.invoke(player, "m_5647_");
        }
        return team;
    }

    private static String teamName(Object team) {
        Object value = ModernForgeEventBridge.invoke(team, "getName");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(team, "m_5758_");
        }
        return value == null ? null : String.valueOf(value);
    }

    private static boolean hasProtectedWeapon(Object player) {
        Object stack = mainHandItem(player);
        if (stack == null || Boolean.TRUE.equals(invokeEither(stack, "isEmpty", "m_41619_"))) {
            return false;
        }
        Object item = invokeEither(stack, "getItem", "m_41720_");
        String itemName = simpleName(item);
        if (itemName.contains("sword")) {
            return true;
        }
        if (hasUnbreaking(stack)) {
            return true;
        }
        return isUhcProtected(stack) || isProtectedDiamondShovel(stack, item);
    }

    private static boolean hasUnbreaking(Object stack) {
        if (simpleName(invokeEither(stack, "getItem", "m_41720_")).contains("enchantedbook")) {
            return false;
        }
        Object unbreaking = staticField("net.minecraft.world.item.enchantment.Enchantments",
                "UNBREAKING", "f_44986_");
        Class<?> helper = ModernRotationBridge.classForName(
                "net.minecraft.world.item.enchantment.EnchantmentHelper");
        if (unbreaking == null || helper == null) {
            return false;
        }
        Object level = invokeStaticEither(helper, "getItemEnchantmentLevel", "m_44843_",
                unbreaking, stack);
        return level instanceof Number && ((Number) level).intValue() > 0;
    }

    private static boolean isUhcProtected(Object stack) {
        Object tag = invokeEither(stack, "getTag", "m_41783_");
        if (tag == null) {
            return false;
        }
        Object extra = ModernForgeEventBridge.invoke(tag, "getCompound", "ExtraAttributes");
        if (extra == null) {
            extra = ModernForgeEventBridge.invoke(tag, "m_128469_", "ExtraAttributes");
        }
        if (extra == null) {
            return false;
        }
        Object id = ModernForgeEventBridge.invoke(extra, "getLong", "UHCid");
        if (id == null) {
            id = ModernForgeEventBridge.invoke(extra, "m_128454_", "UHCid");
        }
        return id instanceof Number && (((Number) id).longValue() == 50006L
                || ((Number) id).longValue() == 50009L);
    }

    private static boolean isProtectedDiamondShovel(Object stack, Object item) {
        Object diamondShovel = staticField("net.minecraft.world.item.Items",
                "DIAMOND_SHOVEL", "f_42389_");
        if (item == null || diamondShovel == null || item != diamondShovel) {
            return false;
        }
        Object tag = invokeEither(stack, "getTag", "m_41783_");
        if (tag == null) {
            return false;
        }
        Object hideFlags = ModernForgeEventBridge.invoke(tag, "contains", "HideFlags");
        if (hideFlags == null) {
            hideFlags = ModernForgeEventBridge.invoke(tag, "m_128441_", "HideFlags");
        }
        return Boolean.TRUE.equals(hideFlags);
    }

    private static double blockHitDistance(Object minecraft, Object player,
                                           ModernRaycastBridge.Vec3 eyes,
                                           ModernRaycastBridge.Vec3 end, double reach) {
        Object hit = clipLevel(minecraft, player, eyes, end);
        if (hit == null) {
            return reach;
        }
        Object location = ModernForgeEventBridge.invoke(hit, "getLocation");
        if (location == null) {
            location = ModernForgeEventBridge.invoke(hit, "m_82450_");
        }
        ModernRaycastBridge.Vec3 point = ModernRaycastBridge.fromMinecraftVec(location);
        return point == null ? reach : Math.min(reach, eyes.distanceTo(point));
    }

    private static Object clipLevel(Object minecraft, Object player,
                                    ModernRaycastBridge.Vec3 eyes,
                                    ModernRaycastBridge.Vec3 end) {
        Object level = ModernMinecraftAccess.level(minecraft);
        Object start = ModernRaycastBridge.toMinecraftVec(eyes);
        Object finish = ModernRaycastBridge.toMinecraftVec(end);
        Class<?> contextClass = ModernRotationBridge.classForName("net.minecraft.world.level.ClipContext");
        if (level == null || start == null || finish == null || contextClass == null) {
            return null;
        }
        try {
            Object blockMode = enumConstant("net.minecraft.world.level.ClipContext$Block", "OUTLINE");
            Object fluidMode = enumConstant("net.minecraft.world.level.ClipContext$Fluid", "NONE");
            if (blockMode == null || fluidMode == null) {
                return null;
            }
            for (java.lang.reflect.Constructor<?> constructor : contextClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 5 && types[0].isInstance(start) && types[1].isInstance(finish)) {
                    constructor.setAccessible(true);
                    Object context = constructor.newInstance(start, finish, blockMode, fluidMode, player);
                    Object hit = ModernForgeEventBridge.invoke(level, "clip", context);
                    return hit == null ? ModernForgeEventBridge.invoke(level, "m_45547_", context) : hit;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static double interactionRange(Object minecraft) {
        Object gameMode = ModernMinecraftAccess.gameMode(minecraft);
        Object value = ModernForgeEventBridge.invoke(gameMode, "getPickRange");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(gameMode, "m_105286_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.invoke(minecraft, "getBlockInteractionRange");
        }
        return value instanceof Number ? Math.max(1.0D, ((Number) value).doubleValue()) : 4.5D;
    }

    private static boolean canCollide(Object entity) {
        Object value = invokeEither(entity, "isPickable", "m_6094_");
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static double pickRadius(Object entity) {
        Object value = invokeEither(entity, "getPickRadius", "m_6088_");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.1D;
    }

    private static boolean isPlayer(Object entity) {
        Class<?> type = ModernRotationBridge.classForName("net.minecraft.world.entity.player.Player");
        return type != null && entity != null && type.isInstance(entity);
    }

    private static boolean isItemFrame(Object entity) {
        Class<?> type = ModernRotationBridge.classForName("net.minecraft.world.entity.decoration.ItemFrame");
        return type != null && entity != null && type.isInstance(entity);
    }

    private static Object mainHandItem(Object player) {
        return invokeEither(player, "getMainHandItem", "m_21205_");
    }

    private static Object invokeEither(Object target, String named, String obfuscated) {
        Object value = ModernForgeEventBridge.invoke(target, named);
        return value == null ? ModernForgeEventBridge.invoke(target, obfuscated) : value;
    }

    private static Object invokeStaticEither(Class<?> owner, String named, String obfuscated,
                                             Object... arguments) {
        if (owner == null) {
            return null;
        }
        String[] names = new String[]{named, obfuscated};
        for (String name : names) {
            Method method = findCompatibleStaticMethod(owner, name, arguments);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(null, arguments);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findCompatibleStaticMethod(Class<?> owner, String name, Object[] arguments) {
        for (Method method : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                    && compatible(method.getParameterTypes(), arguments)) {
                return method;
            }
        }
        for (Method method : owner.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                    && compatible(method.getParameterTypes(), arguments)) {
                return method;
            }
        }
        return null;
    }

    private static boolean compatible(Class<?>[] parameterTypes, Object[] arguments) {
        if (parameterTypes.length != arguments.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (arguments[i] != null && !parameterTypes[i].isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object staticField(String className, String... names) {
        Class<?> type = ModernRotationBridge.classForName(className);
        if (type == null) {
            return null;
        }
        for (String name : names) {
            try {
                java.lang.reflect.Field field = ModernRotationBridge.findField(type, name);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(null);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object enumConstant(String className, String name) {
        Class<?> type = ModernRotationBridge.classForName(className);
        if (type == null || !type.isEnum()) {
            return null;
        }
        try {
            return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String simpleName(Object object) {
        return object == null ? "" : object.getClass().getName().toLowerCase(Locale.ROOT);
    }
}
