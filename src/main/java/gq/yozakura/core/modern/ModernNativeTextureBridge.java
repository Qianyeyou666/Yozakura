package gq.yozakura.core.modern;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class ModernNativeTextureBridge {
    private static final List<Object> LIVE_TEXTURES = new ArrayList<Object>();
    private static boolean unavailable;
    private static boolean successLogged;

    private ModernNativeTextureBridge() {
    }

    static int createTexture(BufferedImage image) {
        if (unavailable || image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return 0;
        }
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> nativeImageClass = load(loader, "com.mojang.blaze3d.platform.NativeImage");
            Object nativeImage = newNativeImage(nativeImageClass, image.getWidth(), image.getHeight());
            Method setPixel = method(nativeImageClass, new String[]{"setPixelRGBA", "m_84988_"},
                    int.class, int.class, int.class);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    setPixel.invoke(nativeImage, Integer.valueOf(x), Integer.valueOf(y),
                            Integer.valueOf(toNativePixel(image.getRGB(x, y))));
                }
            }

            Class<?> dynamicTextureClass = load(loader, "net.minecraft.client.renderer.texture.DynamicTexture");
            Constructor<?> textureCtor = dynamicTextureClass.getConstructor(nativeImageClass);
            Object dynamicTexture = textureCtor.newInstance(nativeImage);
            method(dynamicTextureClass, new String[]{"upload", "m_117985_"}).invoke(dynamicTexture);
            int id = intValue(method(dynamicTextureClass, new String[]{"getId", "m_117963_"}).invoke(dynamicTexture), 0);
            if (id <= 0) {
                close(nativeImage);
                return 0;
            }
            synchronized (LIVE_TEXTURES) {
                LIVE_TEXTURES.add(dynamicTexture);
            }
            ModernLegacyRenderer.bindTexture(id);
            ModernLegacyRenderer.configureTexture();
            if (!successLogged) {
                successLogged = true;
                ModernForgeEventBridge.log("Modern HUD texture upload uses NativeImage/DynamicTexture bridge");
            }
            return id;
        } catch (Throwable throwable) {
            unavailable = true;
            ModernForgeEventBridge.log("Modern NativeImage texture bridge unavailable; keeping geometry fallback", unwrap(throwable));
            return 0;
        }
    }

    private static Object newNativeImage(Class<?> nativeImageClass, int width, int height) throws Exception {
        try {
            Constructor<?> constructor = nativeImageClass.getConstructor(int.class, int.class, boolean.class);
            return constructor.newInstance(Integer.valueOf(width), Integer.valueOf(height), Boolean.FALSE);
        } catch (NoSuchMethodException ignored) {
            Class<?> formatClass = load(nativeImageClass.getClassLoader(), "com.mojang.blaze3d.platform.NativeImage$Format");
            Object rgba = enumConstant(formatClass, "RGBA");
            Constructor<?> constructor = nativeImageClass.getConstructor(formatClass, int.class, int.class, boolean.class);
            return constructor.newInstance(rgba, Integer.valueOf(width), Integer.valueOf(height), Boolean.FALSE);
        }
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                if (name.equals(String.valueOf(constant))) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException("Missing enum constant " + enumClass.getName() + "." + name);
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        if (loader != null) {
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(name);
    }

    private static Method method(Class<?> owner, String[] names, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        for (String name : names) {
            try {
                Method method = owner.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + names[0]);
    }

    private static int toNativePixel(int argb) {
        int a = (argb >>> 24) & 255;
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getTargetException() != null) {
            return ((InvocationTargetException) throwable).getTargetException();
        }
        return throwable;
    }

    private static void close(Object object) {
        if (object instanceof AutoCloseable) {
            try {
                ((AutoCloseable) object).close();
            } catch (Exception ignored) {
            }
        }
    }
}
