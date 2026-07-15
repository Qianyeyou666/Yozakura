package gq.yozakura.core;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.core.modern.ModernForgeEventBridge;
import gq.yozakura.ui.click.web.ModernWebClickGuiService;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;

public final class ModernForgeClient {
    private static volatile boolean state;

    public ModernForgeClient() {
        if (state) {
            log("Modern Forge client is already active");
            ModernWebClickGuiService.open();
            return;
        }
        YozakuraAuthGate.verifyOrThrow("modern-forge");
        Client.username = YozakuraAuthGate.getVerifiedUsername();
        state = true;
        log("Modern Forge attach initialized: minecraft=" + minecraftVersion()
                + ", forge=" + forgeVersion()
                + ", mappings=" + mappingHint());
        ModernForgeEventBridge.init();
        ModernWebClickGuiService.open();
    }

    public static boolean isState() {
        return state;
    }

    public static void unInject() {
        state = false;
        ModernWebClickGuiService.stop();
    }

    public static void showInjectionSuccessAnimation() {
        ModernWebClickGuiService.open();
    }

    private static String minecraftVersion() {
        try {
            Class<?> sharedConstants = findClass("net.minecraft.SharedConstants");
            Method method = sharedConstants.getMethod("getCurrentVersion");
            Object version = method.invoke(null);
            if (version == null) {
                return "unknown";
            }
            Method name = version.getClass().getMethod("getName");
            Object value = name.invoke(version);
            return String.valueOf(value);
        } catch (Throwable throwable) {
            return "unknown";
        }
    }

    private static String forgeVersion() {
        try {
            Class<?> forgeVersion = findClass("net.minecraftforge.versions.forge.ForgeVersion");
            Method method = forgeVersion.getMethod("getVersion");
            Object value = method.invoke(null);
            return String.valueOf(value);
        } catch (Throwable throwable) {
            return "unknown";
        }
    }

    private static String mappingHint() {
        if (classExists("net.minecraft.client.Minecraft")) {
            return "official";
        }
        return "runtime";
    }

    private static boolean classExists(String name) {
        try {
            findClass(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> findClass(String name) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ModernForgeClient.class.getClassLoader();
        }
        return Class.forName(name, false, loader);
    }

    private static void log(String message) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraModernForge.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
