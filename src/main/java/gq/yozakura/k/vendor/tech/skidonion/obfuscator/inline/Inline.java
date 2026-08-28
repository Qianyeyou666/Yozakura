package gq.yozakura.k.vendor.tech.skidonion.obfuscator.inline;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Locale;

public class Inline {
    public Inline() {
    }

    public static void processEnvironment() {
        _advanced_checkProtection(0);
    }

    public static void trycatch() {
        try {
            _advanced_checkProtection(0);
        } catch (Throwable ignored) {
        }
    }

    public static int _advanced_checkProtection(int value) {
        int result = value;
        if (Inline.class.getResource("Inline.class") == null) {
            result ^= 2;
        }
        return result;
    }

    public static int _advanced_checkCRCImage(int value) {
        int result = value;
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.isEmpty()) {
            return result ^ 4;
        }
        String[] entries = classPath.split(File.pathSeparator);
        boolean foundExistingEntry = false;
        for (String entry : entries) {
            if (!entry.isEmpty() && new File(entry).exists()) {
                foundExistingEntry = true;
                break;
            }
        }
        return foundExistingEntry ? result : result ^ 4;
    }

    public static int _advanced_checkIsVirtualPC(int value) {
        String probe = (System.getProperty("java.vm.name", "") + ' '
                + System.getProperty("java.vendor", "") + ' '
                + System.getProperty("os.name", "") + ' '
                + System.getenv("PROCESSOR_IDENTIFIER")).toLowerCase(Locale.ROOT);
        return containsAny(probe, "virtualbox", "vmware", "vbox", "qemu", "hyper-v", "xen")
                ? value ^ 8
                : value;
    }

    public static int _advanced_checkIsDebuggerPresent(int value) {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String lower = argument.toLowerCase(Locale.ROOT);
            if (lower.contains("jdwp") || lower.contains("xdebug") || lower.contains("javaagent")) {
                return value ^ 16;
            }
        }
        return value;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
