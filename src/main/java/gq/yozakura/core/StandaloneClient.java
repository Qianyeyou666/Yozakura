package gq.vapulite.core;

import gq.vapulite.bridge.StandaloneEventBridge;
import gq.vapulite.manager.ModuleManager;
import gq.vapulite.module.Module;
import gq.vapulite.runtime.VapuRuntime;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StandaloneClient {
    private static final String ACTIVE_INSTANCE_PROPERTY = "vapulite.standalone.activeInstance";
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static volatile boolean state;
    private final StandaloneEventBridge bridge = new StandaloneEventBridge();
    private final AtomicBoolean tickQueued = new AtomicBoolean();
    private final Set<Integer> pressedKeys = new HashSet<Integer>();
    private final String instanceId = Long.toHexString(System.nanoTime());
    private volatile boolean running;
    private boolean tickFailureLogged;

    public StandaloneClient() {
        stopExistingStandalonePumps();
        if (state) {
            return;
        }
        state = true;
        running = true;
        System.setProperty(ACTIVE_INSTANCE_PROPERTY, instanceId);
        VapuRuntime.init();
        ensureCoreModules();
        ConfigBridge.loadModulesQuietly();
        startMainThreadPump();
        log("Standalone attach initialized: minecraft=" + mc.getClass().getName());
    }

    public static boolean isState() {
        return state;
    }

    public static void unInject() {
        state = false;
    }

    private void ensureCoreModules() {
        try {
            ModuleManager.getModules();
            ensureModule("Scaffold", new ModuleFactory() {
                @Override
                public Module create() {
                    return new gq.vapulite.module.world.Scaffold();
                }
            });
            ensureModule("KillAura", new ModuleFactory() {
                @Override
                public Module create() {
                    return new gq.vapulite.module.combat.KillAura();
                }
            });
            ensureModule("KeepSprint", new ModuleFactory() {
                @Override
                public Module create() {
                    return new gq.vapulite.module.movement.KeepSprint();
                }
            });
        } catch (Throwable throwable) {
            log("Standalone module bootstrap failed", throwable);
        }
    }

    private void ensureModule(String name, ModuleFactory factory) {
        if (ModuleManager.getModule(name) != null) {
            return;
        }
        try {
            Module module = factory.create();
            if (module != null) {
                ModuleManager.getModules().add(module);
            }
        } catch (Throwable throwable) {
            log("Failed to initialize standalone module: " + name, throwable);
        }
    }

    private void startMainThreadPump() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running && state && instanceId.equals(System.getProperty(ACTIVE_INSTANCE_PROPERTY))) {
                    queueTick();
                    sleep(10L);
                }
                bridge.shutdown();
            }
        }, "VapuLite Standalone Pump");
        thread.setDaemon(true);
        thread.setContextClassLoader(StandaloneClient.class.getClassLoader());
        thread.start();
    }

    private void stopExistingStandalonePumps() {
        ClassLoader currentLoader = StandaloneClient.class.getClassLoader();
        Thread currentThread = Thread.currentThread();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null || thread == currentThread || !thread.isAlive()
                    || !"VapuLite Standalone Pump".equals(thread.getName())) {
                continue;
            }
            if (thread.getContextClassLoader() == currentLoader) {
                continue;
            }
            requestStandaloneShutdown(thread.getContextClassLoader());
            try {
                thread.stop();
                log("Stopped previous standalone pump from loader=" + loaderName(thread.getContextClassLoader()));
            } catch (Throwable throwable) {
                log("Failed to stop previous standalone pump", throwable);
            }
        }
    }

    private void requestStandaloneShutdown(ClassLoader loader) {
        if (loader == null || loader == StandaloneClient.class.getClassLoader()) {
            return;
        }
        try {
            Class<?> type = Class.forName("gq.vapulite.core.StandaloneClient", false, loader);
            Method method = type.getDeclaredMethod("unInject");
            method.setAccessible(true);
            method.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private void queueTick() {
        if (!tickQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    tickQueued.set(false);
                    runTick();
                }
            });
        } catch (Throwable throwable) {
            tickQueued.set(false);
            runTick();
        }
    }

    private void runTick() {
        if (!running || !state || !instanceId.equals(System.getProperty(ACTIVE_INSTANCE_PROPERTY))) {
            running = false;
            return;
        }
        try {
            handleKeys();
            bridge.tick();
            ConfigBridge.autoSaveTick();
        } catch (Throwable throwable) {
            if (!tickFailureLogged) {
                tickFailureLogged = true;
                log("Standalone tick failed", throwable);
            }
        }
    }

    private void handleKeys() {
        if (mc.currentScreen != null || !Keyboard.isCreated()) {
            pressedKeys.clear();
            return;
        }
        for (Module module : ModuleManager.getModules()) {
            int key = module.getKey();
            if (key == Keyboard.KEY_NONE) {
                continue;
            }
            boolean down = key >= 0 && Keyboard.isKeyDown(key);
            if (down && pressedKeys.add(Integer.valueOf(key))) {
                gq.vapulite.event.bus.EventManager.call(new gq.vapulite.bridge.forge.InputEvent.KeyInputEvent());
                module.toggle();
            } else if (!down) {
                pressedKeys.remove(Integer.valueOf(key));
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        log(message, null);
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "VapuLiteStandalone.log");
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

    private static String loaderName(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName();
    }

    private interface ModuleFactory {
        Module create();
    }
}
