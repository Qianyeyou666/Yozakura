package gq.vapulite.engine.font;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FontResourceLoader {
    private FontResourceLoader() {
    }

    public static InputStream open(ResourceLocation location) throws IOException {
        Throwable minecraftFailure = null;
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.getResourceManager() != null) {
                return minecraft.getResourceManager().getResource(location).getInputStream();
            }
        } catch (Throwable throwable) {
            minecraftFailure = throwable;
        }

        InputStream classpathStream = openClasspath(location);
        if (classpathStream != null) {
            return classpathStream;
        }

        IOException exception = new IOException("Missing font resource " + location);
        if (minecraftFailure != null) {
            try {
                exception.initCause(minecraftFailure);
            } catch (IllegalStateException ignored) {
            }
        }
        throw exception;
    }

    public static void logFailure(ResourceLocation location, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "VapuLiteFont.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println("Failed to load font " + location + ": " + throwable.getClass().getName()
                        + ": " + throwable.getMessage());
                throwable.printStackTrace(writer);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static InputStream openClasspath(ResourceLocation location) {
        Set<String> candidates = candidates(location);
        InputStream stream = openWith(Thread.currentThread().getContextClassLoader(), candidates);
        if (stream != null) {
            return stream;
        }
        stream = openWith(FontResourceLoader.class.getClassLoader(), candidates);
        if (stream != null) {
            return stream;
        }
        for (String candidate : candidates) {
            stream = FontResourceLoader.class.getResourceAsStream("/" + candidate);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    private static InputStream openWith(ClassLoader loader, Set<String> candidates) {
        if (loader == null) {
            return null;
        }
        for (String candidate : candidates) {
            InputStream stream = loader.getResourceAsStream(candidate);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    private static Set<String> candidates(ResourceLocation location) {
        LinkedHashSet<String> paths = new LinkedHashSet<String>();
        String value = String.valueOf(location);
        String domain = "minecraft";
        String path = value;
        int split = value.indexOf(':');
        if (split >= 0) {
            domain = value.substring(0, split);
            path = value.substring(split + 1);
        }

        domain = normalize(domain);
        path = normalize(path);
        add(paths, "assets/" + domain + "/" + path);
        if (!"minecraft".equals(domain)) {
            add(paths, "assets/minecraft/" + path);
        }
        add(paths, path);
        return paths;
    }

    private static String normalize(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static void add(Set<String> paths, String path) {
        String normalized = normalize(path);
        if (normalized.length() > 0) {
            paths.add(normalized);
        }
    }
}
