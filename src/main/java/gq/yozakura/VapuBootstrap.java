package gq.vapulite;

import gq.vapulite.bridge.ForgeEnvironment;
import gq.vapulite.bridge.VanillaRemapClassLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;

public final class VapuBootstrap {
    private static boolean vanillaStarted;
    private static boolean lunarStarted;

    public VapuBootstrap() {
        start();
    }

    public static synchronized void start() {
        try {
            if (ForgeEnvironment.isForgeAvailable()) {
                log("Detected Forge environment");
                if (isForgeClientRunning()) {
                    log("Forge client is already running");
                    return;
                }
                startClass("gq.vapulite.core.Client");
            } else if (isLunarClient()) {
                log("Detected Lunar environment");
                if (lunarStarted) {
                    log("Lunar remapped client is already running");
                    return;
                }
                startMappedClass("gq.vapulite.core.StandaloneClient", true, "lunar");
            } else if (isVanillaObfuscated()) {
                log("Detected vanilla obfuscated environment");
                if (vanillaStarted) {
                    log("Vanilla remapped client is already running");
                    return;
                }
                startMappedClass("gq.vapulite.core.StandaloneClient", false, "vanilla");
            } else {
                log("Detected standalone named environment");
                if (isStandaloneRunning()) {
                    log("Standalone client is already running");
                    return;
                }
                startClass("gq.vapulite.core.StandaloneClient");
            }
        } catch (Throwable throwable) {
            log("Bootstrap failed", throwable);
            throw new RuntimeException(throwable);
        }
    }

    private static void startClass(String className) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = VapuBootstrap.class.getClassLoader();
        }
        Class<?> clientClass = Class.forName(className, true, loader);
        clientClass.newInstance();
        log("Started " + className);
    }

    private static void startMappedClass(String className, boolean keepMinecraftClassNames, String label) throws Exception {
        ClassLoader parent = currentLoader();
        URL jar = VapuBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
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

    private static boolean isForgeClientRunning() {
        try {
            Class<?> clientClass = Class.forName("gq.vapulite.core.Client", false, currentLoader());
            return clientClass.getField("state").getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isStandaloneRunning() {
        try {
            Class<?> clientClass = Class.forName("gq.vapulite.core.StandaloneClient", false, currentLoader());
            return ((Boolean) clientClass.getMethod("isState").invoke(null)).booleanValue();
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
        return loader == null ? VapuBootstrap.class.getClassLoader() : loader;
    }

    private static void log(String message) {
        log(message, null);
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "VapuLiteBootstrap.log");
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
