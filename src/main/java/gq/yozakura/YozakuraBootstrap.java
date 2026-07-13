package gq.yozakura;

import gq.yozakura.bridge.ForgeEnvironment;
import gq.yozakura.bridge.VanillaRemapClassLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;

public final class YozakuraBootstrap {
    private static boolean vanillaStarted;
    private static boolean lunarStarted;

    public YozakuraBootstrap() {
        start();
    }

    public static synchronized void start() {
        try {
            if (isLunarClient()) {
                log("Detected Lunar environment");
                if (lunarStarted) {
                    log("Lunar remapped client is already running");
                    notifyRunningClient("gq.yozakura.core.StandaloneClient");
                    return;
                }
                startMappedClass("gq.yozakura.core.StandaloneClient", true, "lunar");
            } else if (ForgeEnvironment.isForgeAvailable()) {
                log("Detected Forge environment");
                if (ForgeEnvironment.isModernForgeAvailable() && !ForgeEnvironment.isLegacyForgeAvailable()) {
                    log("Detected modern Forge environment");
                    if (isClientRunning("gq.yozakura.core.ModernForgeClient", "isState")) {
                        log("Modern Forge client is already running");
                        notifyRunningClient("gq.yozakura.core.ModernForgeClient");
                        return;
                    }
                    startClass("gq.yozakura.core.ModernForgeClient");
                } else {
                    if (isForgeClientRunning()) {
                        log("Forge client is already running");
                        notifyRunningClient("gq.yozakura.core.Client");
                        return;
                    }
                    startClass("gq.yozakura.core.Client");
                }
            } else if (isVanillaObfuscated()) {
                log("Detected vanilla obfuscated environment");
                if (vanillaStarted) {
                    log("Vanilla remapped client is already running");
                    notifyRunningClient("gq.yozakura.core.StandaloneClient");
                    return;
                }
                startMappedClass("gq.yozakura.core.StandaloneClient", false, "vanilla");
            } else {
                log("Detected standalone named environment");
                if (isStandaloneRunning()) {
                    log("Standalone client is already running");
                    notifyRunningClient("gq.yozakura.core.StandaloneClient");
                    return;
                }
                startClass("gq.yozakura.core.StandaloneClient");
            }
        } catch (Throwable throwable) {
            log("Bootstrap failed", throwable);
            throw new RuntimeException(throwable);
        }
    }

    private static void startClass(String className) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = YozakuraBootstrap.class.getClassLoader();
        }
        Class<?> clientClass = Class.forName(className, true, loader);
        clientClass.newInstance();
        log("Started " + className);
    }

    private static void startMappedClass(String className, boolean keepMinecraftClassNames, String label) throws Exception {
        ClassLoader parent = currentLoader();
        URL jar = YozakuraBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
        VanillaRemapClassLoader remapLoader = new VanillaRemapClassLoader(new URL[]{jar}, parent, keepMinecraftClassNames);
        Thread.currentThread().setContextClassLoader(remapLoader);
        Class<?> clientClass = Class.forName(className, true, remapLoader);
        clientClass.newInstance();
        if ("lunar".equals(label)) {
            lunarStarted = true;
        } else {
            vanillaStarted = true;
        }
        log("Started " + label + " remapped " + className);
    }

    private static void notifyRunningClient(String className) {
        try {
            Class<?> clientClass = Class.forName(className, false, currentLoader());
            clientClass.getMethod("showInjectionSuccessAnimation").invoke(null);
            log("Played injection success animation for " + className);
        } catch (Throwable throwable) {
            log("Unable to replay injection success animation for " + className, throwable);
        }
    }

    private static boolean isForgeClientRunning() {
        try {
            Class<?> clientClass = Class.forName("gq.yozakura.core.Client", false, currentLoader());
            return clientClass.getField("state").getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isStandaloneRunning() {
        return isClientRunning("gq.yozakura.core.StandaloneClient", "isState");
    }

    private static boolean isClientRunning(String className, String methodName) {
        try {
            Class<?> clientClass = Class.forName(className, false, currentLoader());
            return ((Boolean) clientClass.getMethod(methodName).invoke(null)).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isVanillaObfuscated() {
        return !isClassAvailable("net.minecraft.client.Minecraft") && isClassAvailable("ave");
    }

    private static boolean isLunarClient() {
        return isClassAvailable("com.moonsworth.lunar.genesis.Genesis")
                || System.getProperty("lunar.webosr.url") != null
                || System.getProperty("ichor.logsFile") != null
                || hasCommandLineToken(".lunarclient")
                || hasCommandLineToken("com.moonsworth.lunar.genesis");
    }

    private static boolean hasCommandLineToken(String token) {
        String command = System.getProperty("sun.java.command", "");
        return command != null && command.toLowerCase().indexOf(token.toLowerCase()) >= 0;
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, currentLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ClassLoader currentLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? YozakuraBootstrap.class.getClassLoader() : loader;
    }

    private static void log(String message) {
        log(message, null);
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraBootstrap.log");
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
}
