package gq.yozakura.core.modern;

final class ModernFullBrightBridge {
    private static final double MODERN_GAMMA_MAX = 1.0D;
    private static double oldGamma = Double.NaN;
    private static boolean potionApplied;

    private ModernFullBrightBridge() {
    }

    static void onClientTick(Object event) {
        try {
            if (!isEndPhase(event)) {
                return;
            }
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object options = ModernMinecraftAccess.options(minecraft);
            if (minecraft == null || options == null) {
                return;
            }
            if (!ModernForgeEventBridge.enabled("FullBright")) {
                restore(options, player);
                return;
            }
            String mode = ModernForgeEventBridge.mode("FullBright", "Mode", "Potion");
            if ("Gamma".equalsIgnoreCase(mode)) {
                if (potionApplied && player != null) {
                    tryPotion(player, false);
                    potionApplied = false;
                }
                applyGamma(options);
            } else {
                applyPotion(player);
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern fullbright bridge tick failed", throwable);
        }
    }

    private static void applyGamma(Object options) {
        Object gamma = ModernInputBridge.gammaOption(options);
        if (gamma == null) {
            ModernForgeEventBridge.log("FullBright: gamma option not found");
            return;
        }
        if (Double.isNaN(oldGamma)) {
            Object value = ModernInputBridge.optionValue(gamma);
            oldGamma = value instanceof Number ? ((Number) value).doubleValue() : 1.0D;
        }
        Object current = ModernInputBridge.optionValue(gamma);
        if (!(current instanceof Number) || ((Number) current).doubleValue() < MODERN_GAMMA_MAX) {
            boolean success = ModernInputBridge.setOptionValue(gamma, Double.valueOf(MODERN_GAMMA_MAX));
            if (!success) {
                ModernForgeEventBridge.log("FullBright: failed to set gamma value");
            }
        }
    }

    private static void applyPotion(Object player) {
        if (player == null) {
            return;
        }
        restoreGammaOnly(ModernMinecraftAccess.options(ModernMinecraftAccess.minecraft()));
        potionApplied = true;
        if (!tryPotion(player, true)) {
            ModernForgeEventBridge.log("Modern fullbright potion mode unavailable");
        }
    }

    private static boolean tryPotion(Object player, boolean apply) {
        try {
            Class<?> mobEffects = ModernForgeEventBridge.findClass("net.minecraft.world.effect.MobEffects");
            Class<?> mobEffectInstance = ModernForgeEventBridge.findClass("net.minecraft.world.effect.MobEffectInstance");
            if (mobEffects == null || mobEffectInstance == null) {
                return false;
            }
            Object nightVision = staticField(mobEffects, "NIGHT_VISION", "f_19611_");
            if (nightVision == null) {
                return false;
            }
            if (apply) {
                ConstructorHolder holder = new ConstructorHolder(mobEffectInstance);
                Object effect = holder.newInstance(nightVision, Integer.valueOf(999999), Integer.valueOf(0),
                        Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
                if (effect == null) {
                    effect = holder.newInstance(nightVision, Integer.valueOf(999999), Integer.valueOf(0));
                }
                if (effect == null) {
                    return false;
                }
                Object result = ModernForgeEventBridge.invoke(player, "addEffect", effect);
                if (result == null) {
                    ModernForgeEventBridge.invoke(player, "m_7292_", effect);
                }
            } else {
                Object result = ModernForgeEventBridge.invoke(player, "removeEffect", nightVision);
                if (result == null) {
                    result = ModernForgeEventBridge.invoke(player, "m_21195_", nightVision);
                }
                if (result == null) {
                    ModernForgeEventBridge.invoke(player, "m_6234_", nightVision);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object staticField(Class<?> owner, String... names) {
        if (owner == null || names == null) {
            return null;
        }
        for (String name : names) {
            try {
                java.lang.reflect.Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void restore(Object options, Object player) {
        restoreGammaOnly(options);
        if (potionApplied && player != null) {
            tryPotion(player, false);
        }
        potionApplied = false;
    }

    private static void restoreGammaOnly(Object options) {
        if (!Double.isNaN(oldGamma)) {
            Object gamma = ModernInputBridge.gammaOption(options);
            if (gamma != null) {
                ModernInputBridge.setOptionValue(gamma, Double.valueOf(clampGamma(oldGamma)));
            }
            oldGamma = Double.NaN;
        }
    }

    private static double clampGamma(double gamma) {
        if (Double.isNaN(gamma) || Double.isInfinite(gamma)) {
            return MODERN_GAMMA_MAX;
        }
        return Math.max(0.0D, Math.min(MODERN_GAMMA_MAX, gamma));
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

    private static final class ConstructorHolder {
        private final Class<?> type;

        ConstructorHolder(Class<?> type) {
            this.type = type;
        }

        Object newInstance(Object... args) {
            try {
                for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
                    Class<?>[] parameters = constructor.getParameterTypes();
                    if (parameters.length != args.length) {
                        continue;
                    }
                    boolean ok = true;
                    for (int i = 0; i < parameters.length; i++) {
                        if (args[i] != null && !wrap(parameters[i]).isInstance(args[i])) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    constructor.setAccessible(true);
                    return constructor.newInstance(args);
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private Class<?> wrap(Class<?> type) {
            if (!type.isPrimitive()) {
                return type;
            }
            if (type == boolean.class) {
                return Boolean.class;
            }
            if (type == int.class) {
                return Integer.class;
            }
            if (type == long.class) {
                return Long.class;
            }
            if (type == float.class) {
                return Float.class;
            }
            if (type == double.class) {
                return Double.class;
            }
            if (type == byte.class) {
                return Byte.class;
            }
            if (type == short.class) {
                return Short.class;
            }
            if (type == char.class) {
                return Character.class;
            }
            return type;
        }
    }
}
