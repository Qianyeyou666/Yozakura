package gq.yozakura.bridge.modern;

import gq.yozakura.bridge.util.ReflectionUtils;
import gq.yozakura.ui.click.web.ModernWebClickGuiState;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ModernForgeEventBridge {
    private static final ModernForgeEventBridge INSTANCE = new ModernForgeEventBridge();
    private static final String MODERN_BRIDGE_PREFIX = "gq.yozakura.bridge.modern.ModernForgeEventBridge$";
    private static final List<Object> REGISTERED_LISTENERS = new ArrayList<Object>();
    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_LOGS_PER_MESSAGE = 8;
    private static volatile boolean initialized;
    private static final Map<String, Integer> LOG_COUNTS = new HashMap<String, Integer>();

    private ModernForgeEventBridge() {
    }

    public static synchronized void initBridge() {
        INSTANCE.init();
    }

    public static synchronized void shutdownBridge() {
        INSTANCE.shutdown();
    }

    public void init() {
        if (initialized) {
            return;
        }
        try {
            register();
            initialized = true;
        } catch (RuntimeException failure) {
            rollbackRegistration(failure);
            throw failure;
        } catch (Error failure) {
            rollbackRegistration(failure);
            throw failure;
        }
    }

    private void register() {
        try {
            Object bus = eventBus();
            cleanupOldModernListeners(bus);
            registerListener(bus, "net.minecraftforge.client.event.RenderGuiEvent$Post", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernVisualRenderer.renderGui(event);
                }
            });
            registerListener(bus, "net.minecraftforge.client.event.RenderLevelStageEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernVisualRenderer.renderLevel(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.client.event.InputEvent$MouseScrollingEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernHudEditor.handleScroll(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.client.event.ScreenEvent$MouseScrolled$Pre", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernHudEditor.handleScroll(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.event.TickEvent$ClientTickEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernRaycastBridge.onClientTick(event);
                    ModernVelocityBridge.onClientTick(event);
                    ModernCombatBridge.onClientTick(event);
                    ModernBowAimBotBridge.onClientTick(event);
                    ModernJumpResetBridge.onClientTick(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.client.event.MovementInputUpdateEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernMovementBridge.onMovementInput(event);
                    ModernJumpResetBridge.onMovementInput(event);
                }
            });
            registerOptionalListener(bus, "net.minecraftforge.event.TickEvent$ClientTickEvent", new Consumer<Object>() {
                @Override
                public void accept(Object event) {
                    ModernMovementBridge.onClientTick(event);
                    ModernPacketBridge.onClientTick(event);
                    ModernFullBrightBridge.onClientTick(event);
                }
            });
            log("Modern visual and gameplay event bridge registered");
        } catch (Throwable throwable) {
            log("Unable to register modern visual and gameplay event bridge", throwable);
            throw new IllegalStateException("Unable to register modern Forge event bridge", throwable);
        }
    }

    private static void rollbackRegistration(Throwable failure) {
        try {
            unregisterListeners();
        } catch (Throwable rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        initialized = false;
    }

    private static void cleanupOldModernListeners(Object bus) {
        if (bus == null) {
            return;
        }
        try {
            Field listenersField = ReflectionUtils.findFieldDeep(bus.getClass(), "listeners");
            if (listenersField == null) {
                return;
            }
            listenersField.setAccessible(true);
            Object value = listenersField.get(bus);
            if (!(value instanceof Map)) {
                return;
            }
            Map<?, ?> listeners = (Map<?, ?>) value;
            List<Object> stale = new ArrayList<Object>();
            for (Object key : listeners.keySet()) {
                if (isModernBridgeConsumer(key)) {
                    stale.add(key);
                }
            }
            if (stale.isEmpty()) {
                return;
            }
            Method unregister = bus.getClass().getMethod("unregister", Object.class);
            int removed = 0;
            for (Object key : stale) {
                try {
                    unregister.invoke(bus, key);
                    removed++;
                } catch (Throwable ignored) {
                }
            }
            log("Removed stale modern visual listeners: " + removed);
        } catch (Throwable throwable) {
            log("Unable to clean stale modern visual listeners", throwable);
        }
    }

    private static boolean isModernBridgeConsumer(Object object) {
        if (object == null) {
            return false;
        }
        Class<?> type = object.getClass();
        return type.getName().startsWith(MODERN_BRIDGE_PREFIX);
    }

    private static Object eventBus() throws Exception {
        Class<?> minecraftForge = ReflectionUtils.classForName("net.minecraftforge.common.MinecraftForge");
        Field field = minecraftForge.getField("EVENT_BUS");
        return field.get(null);
    }

    private static void registerListener(Object bus, String eventClassName, Consumer<Object> consumer) throws Exception {
        Class<?> eventClass = ReflectionUtils.classForName(eventClassName);
        Class<?> priorityClass = ReflectionUtils.classForName("net.minecraftforge.eventbus.api.EventPriority");
        Object priority = Enum.valueOf((Class<Enum>) priorityClass.asSubclass(Enum.class), "NORMAL");
        Method method = bus.getClass().getMethod("addListener", priorityClass, boolean.class, Class.class, Consumer.class);
        method.invoke(bus, priority, Boolean.FALSE, eventClass, consumer);
        synchronized (REGISTERED_LISTENERS) {
            REGISTERED_LISTENERS.add(consumer);
        }
        log("Registered listener for " + eventClassName);
    }

    private static void unregisterListeners() {
        List<Object> listeners;
        synchronized (REGISTERED_LISTENERS) {
            if (REGISTERED_LISTENERS.isEmpty()) {
                return;
            }
            listeners = new ArrayList<Object>(REGISTERED_LISTENERS);
            REGISTERED_LISTENERS.clear();
        }
        Object bus;
        try {
            bus = eventBus();
        } catch (Throwable throwable) {
            log("Unable to resolve modern event bus during shutdown", throwable);
            return;
        }
        Method unregister;
        try {
            unregister = bus.getClass().getMethod("unregister", Object.class);
        } catch (Throwable throwable) {
            log("Modern event bus does not expose unregister", throwable);
            return;
        }
        for (Object listener : listeners) {
            try {
                unregister.invoke(bus, listener);
            } catch (Throwable throwable) {
                log("Unable to unregister modern event listener", throwable);
            }
        }
    }

    private static void registerOptionalListener(Object bus, String eventClassName, Consumer<Object> consumer) {
        try {
            registerListener(bus, eventClassName, consumer);
        } catch (Throwable throwable) {
            log("Optional modern listener unavailable: " + eventClassName, throwable);
        }
    }

    static Class<?> findClass(String name) throws ClassNotFoundException {
        return ReflectionUtils.classForName(name);
    }

    static Object invoke(Object target, String methodName, Object... args) {
        return ReflectionUtils.invokeMethod(target, methodName, args);
    }

    static Object field(Object target, String name) {
        return ReflectionUtils.getFieldValue(target, name);
    }

    static boolean enabled(String module) {
        return ModernWebClickGuiState.isEnabled(module);
    }

    static double number(String module, String value, double fallback) {
        return ModernWebClickGuiState.numberValue(module, value, fallback);
    }

    static boolean bool(String module, String value, boolean fallback) {
        return ModernWebClickGuiState.booleanValue(module, value, fallback);
    }

    static String mode(String module, String value, String fallback) {
        return ModernWebClickGuiState.modeValue(module, value, fallback);
    }

    static void log(String message) {
        log(message, null);
    }

    static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraModernVisual.log");
            if (log.length() > MAX_LOG_BYTES) {
                if (!log.delete()) {
                    return;
                }
            }
            if (!shouldLog(message, throwable)) {
                return;
            }
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static synchronized boolean shouldLog(String message, Throwable throwable) {
        String key = String.valueOf(message);
        if (throwable != null) {
            key += ":" + throwable.getClass().getName() + ":" + String.valueOf(throwable.getMessage());
        }
        Integer count = LOG_COUNTS.get(key);
        int next = count == null ? 1 : count.intValue() + 1;
        LOG_COUNTS.put(key, Integer.valueOf(next));
        return next <= MAX_LOGS_PER_MESSAGE;
    }

    public void shutdown() {
        initialized = false;
        unregisterListeners();
        ModernPacketBridge.shutdown();
        ModernMovementBridge.shutdown();
        ModernCombatBridge.shutdown();
        ModernBowAimBotBridge.shutdown();
        ModernHitSelectBridge.shutdown();
        ModernJumpResetBridge.shutdown();
        ModernVelocityBridge.shutdown();
        ModernFullBrightBridge.shutdown();
        ModernHudEditor.shutdown();
        ModernRotationBridge.clearSilentRotation();
    }

    public boolean isInGame() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        Object level = ModernMinecraftAccess.level(minecraft);
        return minecraft != null && player != null && level != null
                && ModernMinecraftAccess.connection(minecraft, player) != null;
    }

    public Object getMinecraft() {
        return ModernMinecraftAccess.minecraft();
    }

    public boolean isBridgeActive() {
        return initialized;
    }

    public void tick(boolean playerTick) {
    }

    public void sendPacket(Object packet) {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        ModernRotationBridge.sendPacket(player, packet);
    }

    public void markPacketBypass(Object packet) {
        ModernPacketBridge.markBypass(packet);
    }

    public void setSilentRotation(float yaw, float pitch, boolean moveFix) {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        if (player != null) {
            ModernRotationBridge.requestSilentRotation(player, yaw, pitch, moveFix);
        }
    }

    public void clearSilentRotation() {
        ModernRotationBridge.clearSilentRotation();
    }

    public boolean hasSilentRotation() {
        return ModernRotationBridge.hasSilentRotation();
    }

    public float getSilentYaw() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        return ModernRotationBridge.silentYaw(ModernMinecraftAccess.player(minecraft));
    }

    public float getSilentPitch() {
        Object minecraft = ModernMinecraftAccess.minecraft();
        return ModernRotationBridge.silentPitch(ModernMinecraftAccess.player(minecraft));
    }

    public void applyVisibleRotation(float yaw, float pitch) {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object player = ModernMinecraftAccess.player(minecraft);
        if (player != null) {
            ModernRotationBridge.applyVisibleRotation(player, yaw, pitch);
        }
    }

    public boolean isKeyDown(String keyName) {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object options = ModernMinecraftAccess.options(minecraft);
        return options != null && keyName != null && ModernInputBridge.down(options, keyName);
    }

    public void setKeyDown(String keyName, boolean down) {
        Object minecraft = ModernMinecraftAccess.minecraft();
        Object options = ModernMinecraftAccess.options(minecraft);
        if (options != null && keyName != null) {
            ModernInputBridge.setDown(options, keyName, down);
        }
    }
}
