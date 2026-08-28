package gq.yozakura.bridge.modern;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModernRender3D {
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<String, Class<?>>();
    private static Method lineBoxMethod;
    private static Constructor<?> aabbConstructor;
    private static boolean lineRendererFailed;

    private ModernRender3D() {
    }

    static void render(Object event, List<RenderBox> boxes) {
        if (event == null || boxes == null || boxes.isEmpty()) {
            return;
        }
        if (!lineRendererFailed && renderWithModernLines(event, boxes)) {
            return;
        }
    }

    private static boolean renderWithModernLines(Object event, List<RenderBox> boxes) {
        boolean stateSetup = false;
        try {
            Object poseStack = invokeAny(event, "getPoseStack", "getPose");
            Object camera = invokeAny(event, "getCamera");
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object renderBuffers = invokeAny(minecraft, "renderBuffers", "m_91269_");
            Object bufferSource = invokeAny(renderBuffers, "bufferSource", "m_110104_");
            Object renderType = invokeStaticAny("net.minecraft.client.renderer.RenderType", "lines", "m_110504_");
            if (poseStack == null || camera == null || bufferSource == null || renderType == null) {
                return false;
            }
            Object vertexConsumer = invokeAny(bufferSource, "getBuffer", "m_6299_", renderType);
            if (vertexConsumer == null) {
                return false;
            }

            Vec3 cameraPos = cameraPosition(camera);
            setupModernState(false);
            stateSetup = true;
            boolean depthDisabled = false;
            for (RenderBox box : boxes) {
                if (box == null || box.box == null || alpha(box.color) <= 0) {
                    continue;
                }
                if (box.throughWalls && !depthDisabled) {
                    invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "disableDepthTest");
                    depthDisabled = true;
                } else if (!box.throughWalls && depthDisabled) {
                    invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "enableDepthTest");
                    depthDisabled = false;
                }
                int color = lineColor(box.color);
                Object aabb = createAabb(box.box, cameraPos);
                if (aabb == null) {
                    continue;
                }
                invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "lineWidth",
                        Float.valueOf(Math.max(0.5f, box.lineWidth)));
                Method method = lineBoxMethod(poseStack, vertexConsumer, aabb);
                if (method == null) {
                    return false;
                }
                method.invoke(null, poseStack, vertexConsumer, aabb,
                        Float.valueOf(red(color)), Float.valueOf(green(color)),
                        Float.valueOf(blue(color)), Float.valueOf(alpha(color) / 255.0f));
            }
            invokeAny(bufferSource, "endBatch", "m_109912_", renderType);
            return true;
        } catch (Throwable throwable) {
            lineRendererFailed = true;
            ModernForgeEventBridge.log("Modern 3D line renderer unavailable; ESP/backtrack boxes hidden", throwable);
            return false;
        } finally {
            if (stateSetup) {
                resetModernState();
            }
        }
    }

    private static Method lineBoxMethod(Object poseStack, Object vertexConsumer, Object aabb) {
        if (lineBoxMethod != null) {
            return lineBoxMethod;
        }
        Class<?> levelRenderer = classForName("net.minecraft.client.renderer.LevelRenderer");
        if (levelRenderer == null) {
            return null;
        }
        for (Method method : levelRenderer.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!isLineBoxMethodName(method.getName())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 7
                    && types[0].isInstance(poseStack)
                    && types[1].isInstance(vertexConsumer)
                    && types[2].isInstance(aabb)) {
                method.setAccessible(true);
                lineBoxMethod = method;
                return method;
            }
        }
        return null;
    }

    private static boolean isLineBoxMethodName(String name) {
        return "renderLineBox".equals(name) || "m_109646_".equals(name);
    }

    private static Object createAabb(ModernRaycastBridge.Box box, Vec3 camera) {
        try {
            if (aabbConstructor == null) {
                Class<?> aabb = classForName("net.minecraft.world.phys.AABB");
                if (aabb == null) {
                    return null;
                }
                aabbConstructor = aabb.getDeclaredConstructor(double.class, double.class, double.class,
                        double.class, double.class, double.class);
                aabbConstructor.setAccessible(true);
            }
            return aabbConstructor.newInstance(
                    Double.valueOf(box.minX - camera.x),
                    Double.valueOf(box.minY - camera.y),
                    Double.valueOf(box.minZ - camera.z),
                    Double.valueOf(box.maxX - camera.x),
                    Double.valueOf(box.maxY - camera.y),
                    Double.valueOf(box.maxZ - camera.z));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vec3 cameraPosition(Object camera) {
        Object position = invokeAny(camera, "getPosition", "m_90583_");
        if (position != null) {
            return new Vec3(vectorComponent(position, "x", "f_82479_"),
                    vectorComponent(position, "y", "f_82480_"),
                    vectorComponent(position, "z", "f_82481_"));
        }
        return new Vec3(0.0D, 0.0D, 0.0D);
    }

    private static void setupModernState(boolean throughWalls) {
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "enableBlend");
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "defaultBlendFunc");
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "disableCull");
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "depthMask", Boolean.FALSE);
        if (throughWalls) {
            invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "disableDepthTest");
        }
    }

    private static void resetModernState() {
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "enableDepthTest");
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "depthMask", Boolean.TRUE);
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "enableCull");
        invokeStaticAny("com.mojang.blaze3d.systems.RenderSystem", "setShaderColor",
                Float.valueOf(1.0f), Float.valueOf(1.0f), Float.valueOf(1.0f), Float.valueOf(1.0f));
    }

    private static Object invokeAny(Object target, String first, String second, Object... args) {
        Object value = ModernForgeEventBridge.invoke(target, first, args);
        return value != null ? value : ModernForgeEventBridge.invoke(target, second, args);
    }

    private static Object invokeAny(Object target, String first, Object... args) {
        return ModernForgeEventBridge.invoke(target, first, args);
    }

    private static Object invokeStaticAny(String className, String first, Object... args) {
        return invokeStaticAny(className, first, null, args);
    }

    private static Object invokeStaticAny(String className, String first, String second, Object... args) {
        Class<?> type = classForName(className);
        if (type == null) {
            return null;
        }
        Object value = invokeStatic(type, first, args);
        return value != null || second == null ? value : invokeStatic(type, second, args);
    }

    private static Object invokeStatic(Class<?> type, String name, Object[] args) {
        try {
            Method method = findMethod(type, name, args == null ? 0 : args.length);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static double vectorComponent(Object vector, String field, String obfuscatedField) {
        Object value = ModernForgeEventBridge.field(vector, field);
        if (value == null) {
            value = ModernForgeEventBridge.field(vector, obfuscatedField);
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static int lineColor(int color) {
        return withAlpha(color, Math.max(alpha(color), 135));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static float red(int color) {
        return ((color >>> 16) & 255) / 255.0f;
    }

    private static float green(int color) {
        return ((color >>> 8) & 255) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 255) / 255.0f;
    }

    private static int alpha(int color) {
        return (color >>> 24) & 255;
    }

    private static Class<?> classForName(String name) {
        synchronized (CLASS_CACHE) {
            if (CLASS_CACHE.containsKey(name)) {
                return CLASS_CACHE.get(name);
            }
        }
        Class<?> type = null;
        try {
            type = ModernForgeEventBridge.findClass(name);
        } catch (Throwable ignored) {
        }
        synchronized (CLASS_CACHE) {
            CLASS_CACHE.put(name, type);
        }
        return type;
    }

    static final class RenderBox {
        final ModernRaycastBridge.Box box;
        final int color;
        final float lineWidth;
        final boolean fill;
        final boolean throughWalls;

        RenderBox(ModernRaycastBridge.Box box, int color, float lineWidth, boolean fill, boolean throughWalls) {
            this.box = box;
            this.color = color;
            this.lineWidth = lineWidth;
            this.fill = fill;
            this.throughWalls = throughWalls;
        }
    }

    private static final class Vec3 {
        final double x;
        final double y;
        final double z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
