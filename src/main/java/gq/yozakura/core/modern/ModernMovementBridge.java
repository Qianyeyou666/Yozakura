package gq.yozakura.core.modern;

import java.lang.reflect.Field;
import java.util.Locale;

final class ModernMovementBridge {
    private static boolean sprintKeyForced;
    private static int noSlowTicks;
    private static int longJumpTicks;
    private static int jumpCooldownMisses;
    private static Field jumpCooldownField;

    private ModernMovementBridge() {
    }

    static void onClientTick(Object event) {
        try {
            if (!isEndPhase(event)) {
                return;
            }
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object level = ModernMinecraftAccess.level(minecraft);
            Object options = ModernMinecraftAccess.options(minecraft);
            if (minecraft == null || player == null || level == null || options == null) {
                resetKeys(options);
                noSlowTicks = 0;
                return;
            }

            handleNoJumpDelay(player);
            handleNoSlow(player);
            handleSprint(player, options);
            handleSpeed(player);
            handleLongJump(player);
            handleSpider(player);
            handleInvMove(minecraft, player, options);
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern movement bridge tick failed", throwable);
        }
    }

    static void onMovementInput(Object event) {
        try {
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object options = ModernMinecraftAccess.options(minecraft);
            Object entity = ModernForgeEventBridge.invoke(event, "getEntity");
            Object input = ModernForgeEventBridge.invoke(event, "getInput");
            if (player == null || input == null || (entity != null && entity != player)) {
                return;
            }
            if (ModernForgeEventBridge.enabled("InvMove") && isContainerScreen(minecraft)) {
                setInputFromKeys(input, options, ModernForgeEventBridge.bool("InvMove", "Sneak", true));
            }
            if (ModernForgeEventBridge.enabled("NoSlowDown")) {
                applyNoSlowInput(input);
            }
            ModernRotationBridge.applyMoveFix(input, player);
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern movement input bridge failed", throwable);
        }
    }

    private static void handleSprint(Object player, Object options) {
        boolean sprintEnabled = ModernForgeEventBridge.enabled("Sprint");
        boolean speedEnabled = ModernForgeEventBridge.enabled("Speed") || ModernForgeEventBridge.enabled("LongJump");
        boolean noSlowEnabled = ModernForgeEventBridge.enabled("NoSlowDown");
        if (!sprintEnabled && !speedEnabled && !noSlowEnabled) {
            restoreSprintKey(options);
            return;
        }
        if (!isMoving(player)) {
            restoreSprintKey(options);
            return;
        }
        if (isCollidedHorizontally(player) || isShiftDown(player)) {
            restoreSprintKey(options);
            return;
        }

        // 参考 openzen_ref 的实现：关闭 toggleSprint + 使用 KeyMapping.set 静态方法
        if (sprintEnabled || speedEnabled || noSlowEnabled && isUsingItem(player)) {
            // 1. 关闭 toggleSprint
            disableToggleSprint(options);

            // 2. 使用静态 KeyMapping.set(Key, boolean) 强制按下 sprint 键
            forceSprintKeyDown(options);

            sprintKeyForced = true;
        }
    }

    private static void disableToggleSprint(Object options) {
        try {
            // options.toggleSprint().set(false)
            Object toggleSprintOption = ModernForgeEventBridge.invoke(options, "toggleSprint");
            if (toggleSprintOption == null) {
                toggleSprintOption = ModernForgeEventBridge.invoke(options, "m_231994_");
            }
            if (toggleSprintOption != null) {
                ModernForgeEventBridge.invoke(toggleSprintOption, "set", Boolean.FALSE);
                ModernForgeEventBridge.invoke(toggleSprintOption, "m_231513_", Boolean.FALSE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void forceSprintKeyDown(Object options) {
        // 获取 keySprint KeyMapping
        Object sprintKeyMapping = ModernForgeEventBridge.field(options, "keySprint");
        if (sprintKeyMapping == null) {
            sprintKeyMapping = ModernForgeEventBridge.field(options, "f_92091_");
        }
        if (sprintKeyMapping == null) {
            return;
        }

        // 获取 Key 对象
        Object key = ModernForgeEventBridge.invoke(sprintKeyMapping, "getKey");
        if (key == null) {
            key = ModernForgeEventBridge.invoke(sprintKeyMapping, "m_7221_");
        }
        if (key == null) {
            return;
        }

        // 调用静态方法 KeyMapping.set(Key, boolean)
        try {
            Class<?> keyMappingClass = findClass("net.minecraft.client.KeyMapping");
            if (keyMappingClass != null) {
                for (java.lang.reflect.Method method : keyMappingClass.getMethods()) {
                    if (method.getName().equals("set") && method.getParameterCount() == 2
                            && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        method.invoke(null, key, Boolean.TRUE);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return ModernForgeEventBridge.findClass(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void handleSpeed(Object player) {
        if (!ModernForgeEventBridge.enabled("Speed") || !isMoving(player) || isCollidedHorizontally(player)) {
            return;
        }
        String mode = ModernForgeEventBridge.mode("Speed", "Mode", "Vanilla");
        double multiplier = ModernForgeEventBridge.number("Speed", "Speed", 1.0D);
        if ("Timer".equalsIgnoreCase(mode)) {
            setSprinting(player, true);
            return;
        }
        if (ModernRotationBridge.onGround(player)) {
            setSprinting(player, true);
            invokeAny(player, new String[]{"jumpFromGround", "jump", "m_6478_", "m_20334_"});
            return;
        }
        if ("Strafe".equalsIgnoreCase(mode) || multiplier > 1.0D) {
            double boost = Math.min(0.08D, Math.max(0.0D, (multiplier - 1.0D) * 0.035D));
            if (boost > 0.0D) {
                strafeForward(player, boost);
            }
        }
    }

    private static void handleLongJump(Object player) {
        if (!ModernForgeEventBridge.enabled("LongJump") || !isMoving(player) || isCollidedHorizontally(player)) {
            longJumpTicks = 0;
            return;
        }
        if (ModernRotationBridge.onGround(player)) {
            longJumpTicks = 0;
            setSprinting(player, true);
            invokeAny(player, new String[]{"jumpFromGround", "jump", "m_6478_", "m_20334_"});
            strafeForward(player, 0.18D);
            return;
        }
        if (++longJumpTicks <= 10) {
            strafeForward(player, Math.max(0.025D, ModernForgeEventBridge.number("LongJump", "Boost", 0.08D)));
        }
    }

    private static void handleInvMove(Object minecraft, Object player, Object options) {
        if (!ModernForgeEventBridge.enabled("InvMove") || !isContainerScreen(minecraft)) {
            return;
        }
        setInputFromKeys(inputOf(player), options, ModernForgeEventBridge.bool("InvMove", "Sneak", true));
        double speed = ModernRotationBridge.onGround(player) ? 0.05D : 0.0125D;
        if (keyDown(options, "forward")) {
            moveRelative(player, speed, 1.0F, 0.0F);
        }
        if (keyDown(options, "back")) {
            moveRelative(player, speed * 0.75D, -1.0F, 0.0F);
        }
        if (keyDown(options, "left")) {
            moveRelative(player, speed * 0.75D, 0.0F, 1.0F);
        }
        if (keyDown(options, "right")) {
            moveRelative(player, speed * 0.75D, 0.0F, -1.0F);
        }
        if (ModernRotationBridge.onGround(player) && keyDown(options, "jump")) {
            invokeAny(player, new String[]{"jumpFromGround", "jump", "m_6478_", "m_20334_"});
        }
    }

    private static void handleNoSlow(Object player) {
        if (!ModernForgeEventBridge.enabled("NoSlowDown")) {
            noSlowTicks = 0;
            return;
        }
        if (!isUsingItem(player)) {
            noSlowTicks = 0;
            return;
        }
        noSlowTicks++;
        applyNoSlowInput(inputOf(player));
        ModernRotationBridge.applyMoveFix(inputOf(player), player);
        setSprinting(player, true);
        String mode = ModernForgeEventBridge.mode("NoSlowDown", "Mode", "Vanilla");
        if ("NCP".equalsIgnoreCase(mode) || "Watchdog".equalsIgnoreCase(mode)) {
            if (noSlowTicks % 3 == 0) {
                sendSprintAction(player, "START_SPRINTING");
            } else if (noSlowTicks % 3 == 1) {
                sendSprintAction(player, "STOP_SPRINTING");
            }
        }
    }

    private static Object inputOf(Object player) {
        Object input = ModernForgeEventBridge.field(player, "input");
        if (input == null) {
            input = ModernForgeEventBridge.field(player, "f_108618_");
        }
        return input;
    }

    private static void applyNoSlowInput(Object input) {
        if (input == null) {
            return;
        }
        float factor = (float) Math.max(1.0D, 1.0D / Math.max(0.2D,
                ModernForgeEventBridge.number("NoSlowDown", "Speed", 1.0D)));
        float forward = getFloat(input, "forwardImpulse", "moveForward");
        float left = getFloat(input, "leftImpulse", "moveStrafe");
        if (Math.abs(forward) > 0.01F && Math.abs(forward) < 0.99F) {
            setFloat(input, "forwardImpulse", clamp(forward * factor, -1.0F, 1.0F), "moveForward");
        }
        if (Math.abs(left) > 0.01F && Math.abs(left) < 0.99F) {
            setFloat(input, "leftImpulse", clamp(left * factor, -1.0F, 1.0F), "moveStrafe");
        }
        if (forward > 0.0F) {
            setFloat(input, "forwardImpulse", 1.0F, "moveForward");
        }
    }

    private static void handleNoJumpDelay(Object player) {
        if (!ModernForgeEventBridge.enabled("NoJumpDelay")) {
            return;
        }
        Field field = jumpCooldownField(player);
        if (field == null) {
            return;
        }
        try {
            field.setInt(player, 0);
        } catch (Throwable ignored) {
        }
    }

    private static void handleSpider(Object player) {
        if (!ModernForgeEventBridge.enabled("Spider") || isOnLadder(player)) {
            return;
        }
        if (!isCollidedHorizontally(player)) {
            return;
        }
        Object movement = deltaMovement(player);
        double x = vectorComponent(movement, "x", "f_82479_");
        double z = vectorComponent(movement, "z", "f_82481_");
        setDeltaMovement(player, x, 0.2D, z);
    }

    private static void setInputFromKeys(Object input, Object options, boolean allowSneak) {
        if (input == null) {
            return;
        }
        boolean forward = keyDown(options, "forward");
        boolean back = keyDown(options, "back");
        boolean left = keyDown(options, "left");
        boolean right = keyDown(options, "right");
        setBoolean(input, "up", forward, "forwardKeyDown");
        setBoolean(input, "down", back, "backKeyDown");
        setBoolean(input, "left", left, "leftKeyDown");
        setBoolean(input, "right", right, "rightKeyDown");
        setBoolean(input, "jumping", keyDown(options, "jump"), "jump");
        if (allowSneak) {
            setBoolean(input, "shiftKeyDown", keyDown(options, "shift"), "sneak");
        }
        setFloat(input, "forwardImpulse", forward == back ? 0.0F : forward ? 1.0F : -1.0F, "moveForward");
        setFloat(input, "leftImpulse", left == right ? 0.0F : left ? 1.0F : -1.0F, "moveStrafe");
    }

    private static void moveRelative(Object player, double speed, float forward, float strafe) {
        double direction = directionYaw(player, forward, strafe);
        Object movement = deltaMovement(player);
        double x = vectorComponent(movement, "x", "f_82479_") - Math.sin(direction) * speed;
        double y = vectorComponent(movement, "y", "f_82480_");
        double z = vectorComponent(movement, "z", "f_82481_") + Math.cos(direction) * speed;
        setDeltaMovement(player, x, y, z);
    }

    private static void strafeForward(Object player, double speed) {
        Object input = ModernForgeEventBridge.field(player, "input");
        float forward = getFloat(input, "forwardImpulse", "moveForward", "zza");
        float strafe = getFloat(input, "leftImpulse", "moveStrafe", "xxa");
        if (forward == 0.0F && strafe == 0.0F) {
            return;
        }
        moveRelative(player, speed, forward, strafe);
    }

    private static double directionYaw(Object player, float forward, float strafe) {
        float yaw = ModernRotationBridge.yaw(player);
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        float strafeFactor = 1.0F;
        if (forward < 0.0F) {
            strafeFactor = -0.5F;
        } else if (forward > 0.0F) {
            strafeFactor = 0.5F;
        }
        if (strafe > 0.0F) {
            yaw -= 90.0F * strafeFactor;
        }
        if (strafe < 0.0F) {
            yaw += 90.0F * strafeFactor;
        }
        return Math.toRadians(yaw);
    }

    private static boolean isMoving(Object player) {
        Object input = ModernForgeEventBridge.field(player, "input");
        float forward = getFloat(input, "forwardImpulse", "moveForward", "zza");
        float strafe = getFloat(input, "leftImpulse", "moveStrafe", "xxa");
        if (Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F) {
            return true;
        }
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object options = ModernMinecraftAccess.options(minecraft);
        return keyDown(options, "forward")
                || keyDown(options, "back")
                || keyDown(options, "left")
                || keyDown(options, "right");
    }

    static boolean isMovingForBridge(Object player) {
        return player != null && isMoving(player);
    }

    private static boolean isContainerScreen(Object minecraft) {
        Object screen = ModernForgeEventBridge.field(minecraft, "screen");
        if (screen == null) {
            screen = ModernForgeEventBridge.field(minecraft, "f_91080_");
        }
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("containerscreen")
                || name.contains("inventoryscreen")
                || name.contains("abstractcontainerscreen")
                || name.contains("genericcontainermenu")
                || name.contains("chest");
    }

    private static void resetKeys(Object options) {
        restoreSprintKey(options);
    }

    private static void restoreSprintKey(Object options) {
        if (!sprintKeyForced || options == null) {
            sprintKeyForced = false;
            return;
        }
        sprintKeyForced = false;
        Object key = ModernInputBridge.key(options, "sprint");
        if (key != null) {
            ModernInputBridge.setKeyDown(key, ModernInputBridge.physicalDown(key));
        }
    }

    private static boolean keyDown(Object options, String... names) {
        if (names == null) {
            return false;
        }
        for (String name : names) {
            if (ModernInputBridge.down(options, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsingItem(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isUsingItem");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_6117_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isShiftDown(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "isShiftKeyDown");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_20142_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isCollidedHorizontally(Object player) {
        Object value = ModernForgeEventBridge.field(player, "horizontalCollision");
        if (value == null) {
            value = ModernForgeEventBridge.field(player, "isCollidedHorizontally");
        }
        if (value == null) {
            value = ModernForgeEventBridge.field(player, "f_19863_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static boolean isOnLadder(Object player) {
        Object value = ModernForgeEventBridge.invoke(player, "onClimbable");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "m_6147_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.invoke(player, "isOnLadder");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    static boolean setSprinting(Object player, boolean sprinting) {
        if (player == null) {
            return false;
        }

        // 尝试方法调用
        ModernForgeEventBridge.invoke(player, "setSprinting", Boolean.valueOf(sprinting));
        ModernForgeEventBridge.invoke(player, "m_6858_", Boolean.valueOf(sprinting));

        // 直接设置字段 - 尝试所有可能的 LivingEntity sprint 字段名
        boolean fieldSet = setFieldBoolean(player, sprinting,
            "isSprinting", "f_20098_", "sprinting",  // 常见名称
            "f_20911_", "f_20890_", "f_20899_", "f_20948_");  // 1.20.1 LivingEntity 候选字段

        return fieldSet;
    }

    private static boolean diagnosedOnce = false;

    private static void diagnosePlayerClass(Object player) {
        if (diagnosedOnce) {
            return;
        }
        diagnosedOnce = true;

        StringBuilder report = new StringBuilder();
        report.append("=== Player Class Diagnosis ===\n");
        report.append("Player class: ").append(player.getClass().getName()).append("\n");

        // 列出所有 boolean 字段
        report.append("\nBoolean fields:\n");
        Class<?> current = player.getClass();
        while (current != null && !current.getName().equals("java.lang.Object")) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(player);
                        report.append("  ").append(current.getSimpleName()).append(".")
                              .append(field.getName()).append(" = ").append(value).append("\n");
                    } catch (Throwable ignored) {
                    }
                }
            }
            current = current.getSuperclass();
        }

        // 列出 setSprinting 相关方法
        report.append("\nMethods containing 'sprint':\n");
        current = player.getClass();
        while (current != null && !current.getName().equals("java.lang.Object")) {
            for (java.lang.reflect.Method method : current.getDeclaredMethods()) {
                String name = method.getName().toLowerCase();
                if (name.contains("sprint")) {
                    report.append("  ").append(current.getSimpleName()).append(".")
                          .append(method.getName()).append("(");
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        if (i > 0) report.append(", ");
                        report.append(params[i].getSimpleName());
                    }
                    report.append(")\n");
                }
            }
            current = current.getSuperclass();
        }

        ModernForgeEventBridge.log(report.toString());
    }

    private static boolean setFieldBoolean(Object target, boolean value, String... names) {
        if (target == null) {
            return false;
        }
        for (String name : names) {
            try {
                java.lang.reflect.Field field = findField(target.getClass(), name);
                if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
                    field.setAccessible(true);
                    field.setBoolean(target, value);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static java.lang.reflect.Field findField(Class<?> owner, String name) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean sendSprintAction(Object player, String actionName) {
        try {
            Class<?> packetClass = ModernRotationBridge.classForName("net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket");
            Class<?> actionClass = ModernRotationBridge.classForName("net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket$Action");
            if (packetClass == null || actionClass == null || !actionClass.isEnum()) {
                return false;
            }
            Object action = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), actionName);
            Object packet = null;
            for (java.lang.reflect.Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 2 && types[1].isAssignableFrom(actionClass)) {
                    constructor.setAccessible(true);
                    packet = constructor.newInstance(player, action);
                    break;
                }
                if (types.length == 3 && types[1].isAssignableFrom(actionClass)) {
                    constructor.setAccessible(true);
                    packet = constructor.newInstance(player, action, Integer.valueOf(0));
                    break;
                }
            }
            return ModernRotationBridge.sendPacket(player, packet);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object deltaMovement(Object player) {
        Object movement = ModernForgeEventBridge.invoke(player, "getDeltaMovement");
        if (movement == null) {
            movement = ModernForgeEventBridge.invoke(player, "m_20184_");
        }
        return movement;
    }

    private static void setDeltaMovement(Object player, double x, double y, double z) {
        Object result = ModernForgeEventBridge.invoke(player, "setDeltaMovement",
                Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        if (result == null) {
            ModernForgeEventBridge.invoke(player, "m_20256_", Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        }
    }

    private static double vectorComponent(Object vector, String named, String obfuscated) {
        Object value = ModernForgeEventBridge.field(vector, named);
        if (value == null) {
            value = ModernForgeEventBridge.field(vector, obfuscated);
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static Field jumpCooldownField(Object player) {
        if (jumpCooldownField != null) {
            return jumpCooldownField;
        }
        if (jumpCooldownMisses > 3 || player == null) {
            return null;
        }
        String[] names = new String[]{"jumpTriggerTime", "jumpTicks", "f_108622_", "field_70773_bE"};
        for (String name : names) {
            try {
                Field field = ModernRotationBridge.findField(player.getClass(), name);
                if (field != null && (field.getType() == int.class || field.getType() == Integer.class)) {
                    field.setAccessible(true);
                    jumpCooldownField = field;
                    return field;
                }
            } catch (Throwable ignored) {
            }
        }
        jumpCooldownMisses++;
        return null;
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

    private static Object invokeAny(Object target, String[] names, Object... args) {
        for (String name : names) {
            Object value = ModernForgeEventBridge.invoke(target, name, args);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static float getFloat(Object object, String... names) {
        if (object == null || names == null) {
            return 0.0F;
        }
        for (String name : names) {
            Object value = ModernForgeEventBridge.field(object, name);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        }
        return 0.0F;
    }

    private static void setFloat(Object object, String name, float value, String fallback) {
        setFloat(object, value, name, fallback);
    }

    private static void setFloat(Object object, float value, String... names) {
        if (object == null || names == null) {
            return;
        }
        for (String name : names) {
            try {
                Field field = ModernRotationBridge.findField(object.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    field.setFloat(object, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void setBoolean(Object object, String name, boolean value, String fallback) {
        if (object == null) {
            return;
        }
        for (String next : new String[]{name, fallback}) {
            try {
                Field field = ModernRotationBridge.findField(object.getClass(), next);
                if (field != null) {
                    field.setAccessible(true);
                    field.setBoolean(object, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
