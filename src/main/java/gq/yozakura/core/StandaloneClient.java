package gq.yozakura.core;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.auth.token.TokenAuthStandaloneBridge;
import gq.yozakura.bridge.StandaloneEventBridge;
import gq.yozakura.manager.BridgeDebug;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.ui.overlay.InjectionSuccessAnimation;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class StandaloneClient {
    private static final String ACTIVE_INSTANCE_PROPERTY = "yozakura.standalone.activeInstance";
    private static final String FAILED_INSTANCE_PROPERTY = "yozakura.standalone.failedInstance";
    private static final long BRIDGE_SHUTDOWN_TIMEOUT_MS = 2000L;
    private static final long PREVIOUS_PUMP_JOIN_TIMEOUT_MS = 5000L;
    private static final int KEYBOARD_SIZE = 256;
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static volatile boolean state;
    private static volatile boolean shutdownCompleted;
    private static volatile StandaloneClient activeClient;
    private static volatile boolean terminalBridgeFailure;
    private final StandaloneEventBridge bridge = new StandaloneEventBridge();
    private final TokenAuthStandaloneBridge tokenAuthBridge = new TokenAuthStandaloneBridge();
    private final AtomicBoolean tickQueued = new AtomicBoolean();
    private final Set<Integer> pressedKeys = new HashSet<Integer>();
    private final String instanceId = Long.toHexString(System.nanoTime());
    private volatile boolean running;
    private volatile boolean pumpStarted;
    private boolean tickFailureLogged;
    private int lastPlayerTick = Integer.MIN_VALUE;
    private int skippedPlayerTicks;

    public StandaloneClient() {
        throwIfPreviousStandaloneBridgeFailed();
        if (state) {
            showInjectionSuccessAnimation();
            return;
        }
        stopExistingStandalonePumps();
        throwIfPreviousStandaloneBridgeIsStillActive();
        YozakuraAuthGate.verifyOrThrow("standalone");
        Client.username = YozakuraAuthGate.getVerifiedUsername();
        boolean initialized = false;
        try {
            shutdownCompleted = false;
            YozakuraRuntime.init();
            ensureCoreModules();
            loadConfigOnStartup();
            System.setProperty(ACTIVE_INSTANCE_PROPERTY, instanceId);
            activeClient = this;
            running = true;
            state = true;
            startMainThreadPump();
            showInjectionSuccessAnimation();
            log("Standalone attach initialized: minecraft=" + mc.getClass().getName());
            initialized = true;
        } finally {
            if (!initialized) {
                rollbackFailedInitialization();
            }
        }
    }

    public static boolean isState() {
        return state;
    }

    public static boolean isBridgeOwnerActive() {
        String owner = System.getProperty(ACTIVE_INSTANCE_PROPERTY);
        return owner != null && !owner.isEmpty();
    }

    public static void unInject() {
        shutdownForReinjection();
    }

    public static void shutdownForReinjection() {
        state = false;
        StandaloneClient client = activeClient;
        if (client == null) {
            if (isBridgeOwnerActive()) {
                terminalBridgeFailure = true;
                shutdownCompleted = false;
                System.setProperty(FAILED_INSTANCE_PROPERTY, System.getProperty(ACTIVE_INSTANCE_PROPERTY));
            } else {
                shutdownCompleted = true;
            }
            return;
        }
        client.running = false;
        if (mc.isCallingFromMinecraftThread()) {
            if (!shutdownCompleted) {
                try {
                    client.bridge.shutdown();
                    client.completeSuccessfulShutdown();
                } catch (Throwable throwable) {
                    client.recordTerminalBridgeFailure("Standalone direct teardown failed", throwable);
                }
            }
        }
    }

    public static boolean isShutdownComplete() {
        return shutdownCompleted && !hasTerminalBridgeFailure();
    }

    public static void showInjectionSuccessAnimation() {
        InjectionSuccessAnimation.show();
    }

    private void ensureCoreModules() {
        ModuleManager.getModules();
        ensureModule("Scaffold", new ModuleFactory() {
            @Override
            public Module create() {
                return new gq.yozakura.module.world.Scaffold();
            }
        });
        ensureModule("KillAura", new ModuleFactory() {
            @Override
            public Module create() {
                return new gq.yozakura.module.combat.KillAura();
            }
        });
        ensureModule("KeepSprint", new ModuleFactory() {
            @Override
            public Module create() {
                return new gq.yozakura.module.movement.KeepSprint();
            }
        });
    }

    private void ensureModule(String name, ModuleFactory factory) {
        if (ModuleManager.getModule(name) != null) {
            return;
        }
        Module module = factory.create();
        if (module == null) {
            throw new IllegalStateException("Standalone module factory returned null: " + name);
        }
        ModuleManager.getModules().add(module);
    }

    private void loadConfigOnStartup() {
        try {
            ConfigBridge.loadModules();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load standalone module configuration", exception);
        }
    }

    private void rollbackFailedInitialization() {
        running = false;
        state = false;
        if (!pumpStarted) {
            completeSuccessfulShutdown();
            return;
        }
        if (!mc.isCallingFromMinecraftThread()) {
            return;
        }
        try {
            bridge.shutdown();
            completeSuccessfulShutdown();
        } catch (Throwable throwable) {
            recordTerminalBridgeFailure("Standalone initialization rollback failed", throwable);
        }
    }

    private void completeSuccessfulShutdown() {
        shutdownCompleted = true;
        if (activeClient == this) {
            activeClient = null;
        }
        clearActiveInstanceIfOwner();
    }

    private void recordTerminalBridgeFailure(String message, Throwable throwable) {
        terminalBridgeFailure = true;
        running = false;
        state = false;
        shutdownCompleted = false;
        System.setProperty(FAILED_INSTANCE_PROPERTY, instanceId);
        log(message, throwable);
    }

    private static boolean hasTerminalBridgeFailure() {
        String failedInstance = System.getProperty(FAILED_INSTANCE_PROPERTY);
        return terminalBridgeFailure || (failedInstance != null && !failedInstance.isEmpty());
    }

    private static void throwIfPreviousStandaloneBridgeFailed() {
        if (hasTerminalBridgeFailure()) {
            throw new IllegalStateException("A previous standalone bridge failed to tear down; restart Minecraft before reinjecting");
        }
    }

    private static void throwIfPreviousStandaloneBridgeIsStillActive() {
        if (isBridgeOwnerActive()) {
            throw new IllegalStateException("A previous standalone bridge still owns this client; restart Minecraft before reinjecting");
        }
    }

    private void clearActiveInstanceIfOwner() {
        if (instanceId.equals(System.getProperty(ACTIVE_INSTANCE_PROPERTY))) {
            System.clearProperty(ACTIVE_INSTANCE_PROPERTY);
        }
    }

    private void startMainThreadPump() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (running && state && instanceId.equals(System.getProperty(ACTIVE_INSTANCE_PROPERTY))) {
                        queueTick();
                        sleep(10L);
                    }
                    if (!terminalBridgeFailure) {
                        if (!shutdownCompleted) {
                            shutdownBridgeOnMainThread();
                            completeSuccessfulShutdown();
                        }
                    }
                } catch (Throwable throwable) {
                    recordTerminalBridgeFailure("Standalone bridge emergency teardown failed", throwable);
                }
            }
        }, "Yozakura Standalone Pump");
        thread.setDaemon(true);
        thread.setContextClassLoader(StandaloneClient.class.getClassLoader());
        thread.start();
        pumpStarted = true;
    }

    private void stopExistingStandalonePumps() {
        Thread currentThread = Thread.currentThread();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null || thread == currentThread || !thread.isAlive()
                    || !"Yozakura Standalone Pump".equals(thread.getName())) {
                continue;
            }
            requestStandaloneShutdown(thread.getContextClassLoader());
            try {
                thread.interrupt();
                thread.join(PREVIOUS_PUMP_JOIN_TIMEOUT_MS);
                if (thread.isAlive()) {
                    throw new IllegalStateException("Previous standalone pump did not stop within "
                            + PREVIOUS_PUMP_JOIN_TIMEOUT_MS + " ms");
                }
                if (!isStandaloneShutdownComplete(thread.getContextClassLoader())) {
                    throw new IllegalStateException("Previous standalone bridge teardown did not complete");
                }
                log("Stopped previous standalone pump from loader=" + loaderName(thread.getContextClassLoader()));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the previous standalone pump",
                        interrupted);
            } catch (Throwable throwable) {
                log("Failed to stop previous standalone pump", throwable);
                throw throwable instanceof RuntimeException
                        ? (RuntimeException) throwable
                        : new IllegalStateException("Failed to stop previous standalone pump", throwable);
            }
        }
    }

    private void requestStandaloneShutdown(ClassLoader loader) {
        if (loader == null) {
            return;
        }
        if (loader == StandaloneClient.class.getClassLoader()) {
            shutdownForReinjection();
            return;
        }
        try {
            Class<?> type = Class.forName("gq.yozakura.core.StandaloneClient", false, loader);
            Method method = type.getDeclaredMethod("shutdownForReinjection");
            method.setAccessible(true);
            method.invoke(null);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to request shutdown from the previous standalone loader",
                    throwable);
        }
    }

    private boolean isStandaloneShutdownComplete(ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class<?> type = Class.forName("gq.yozakura.core.StandaloneClient", false, loader);
            Method method = type.getDeclaredMethod("isShutdownComplete");
            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (Throwable throwable) {
            log("Unable to verify previous standalone bridge teardown", throwable);
            return false;
        }
    }

    private void shutdownBridgeOnMainThread() {
        final CountDownLatch shutdownComplete = new CountDownLatch(1);
        final AtomicReference<Throwable> shutdownFailure = new AtomicReference<Throwable>();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                try {
                    bridge.shutdown();
                } catch (Throwable throwable) {
                    shutdownFailure.set(throwable);
                } finally {
                    shutdownComplete.countDown();
                }
            }
        });
        try {
            if (!shutdownComplete.await(BRIDGE_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Minecraft thread did not complete standalone bridge teardown within "
                        + BRIDGE_SHUTDOWN_TIMEOUT_MS + " ms");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for standalone bridge teardown", interrupted);
        }
        Throwable failure = shutdownFailure.get();
        if (failure != null) {
            throw failure instanceof RuntimeException
                    ? (RuntimeException) failure
                    : new IllegalStateException("Standalone bridge teardown failed", failure);
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
            stopForTickSchedulingFailure(throwable);
        }
    }

    private void runTick() {
        if (!running || !state || !instanceId.equals(System.getProperty(ACTIVE_INSTANCE_PROPERTY))) {
            running = false;
            return;
        }
        try {
            boolean playerTick = mc.thePlayer != null && mc.thePlayer.ticksExisted != lastPlayerTick;
            if (playerTick) {
                lastPlayerTick = mc.thePlayer.ticksExisted;
            } else {
                skippedPlayerTicks++;
            }
            BridgeDebug.logTick("standalone", "PUMP_TICK", playerTick, skippedPlayerTicks);
            tokenAuthBridge.tick();
            handleKeys();
            bridge.tick(playerTick);
            if (playerTick) {
                skippedPlayerTicks = 0;
            }
            if (playerTick) {
                ConfigBridge.autoSaveTick();
            }
        } catch (Throwable throwable) {
            stopForTickFailure(throwable);
        }
    }

    private void stopForTickSchedulingFailure(Throwable throwable) {
        recordTerminalBridgeFailure("Standalone tick scheduling failed", throwable);
    }

    private void stopForTickFailure(Throwable throwable) {
        running = false;
        state = false;
        if (!tickFailureLogged) {
            tickFailureLogged = true;
            log("Standalone tick failed", throwable);
        }
        try {
            bridge.shutdown();
            completeSuccessfulShutdown();
        } catch (Throwable teardownFailure) {
            recordTerminalBridgeFailure("Standalone bridge emergency teardown failed", teardownFailure);
        }
    }

    private void handleKeys() {
        if (mc.currentScreen != null || !Keyboard.isCreated()) {
            pressedKeys.clear();
            return;
        }
        for (int key = 0; key < KEYBOARD_SIZE; key++) {
            boolean down = Keyboard.isKeyDown(key);
            if (down && pressedKeys.add(Integer.valueOf(key))) {
                dispatchKeyPress(key);
                toggleModulesBoundTo(key);
            } else if (!down) {
                pressedKeys.remove(Integer.valueOf(key));
            }
        }
    }

    private void dispatchKeyPress(int key) {
        gq.yozakura.event.bus.EventManager.call(new gq.yozakura.bridge.forge.InputEvent.KeyInputEvent());
    }

    private void toggleModulesBoundTo(int key) {
        if (key == Keyboard.KEY_NONE) {
            return;
        }
        for (Module module : ModuleManager.getModules()) {
            if (module.getKey() == key) {
                module.toggle();
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
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraStandalone.log");
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
