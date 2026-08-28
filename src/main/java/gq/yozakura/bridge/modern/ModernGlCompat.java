package gq.yozakura.bridge.modern;

import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.nio.IntBuffer;

final class ModernGlCompat {
    private static Method glGetIntegerv;
    private static Method glGetIntegerBuffer;
    private static boolean viewportFailureLogged;

    private ModernGlCompat() {
    }

    static boolean getViewport(IntBuffer buffer) {
        if (buffer == null || buffer.capacity() < 4) {
            return false;
        }
        buffer.clear();
        if (invokeViewport("glGetIntegerv", buffer) || invokeViewport("glGetInteger", buffer)) {
            buffer.position(0);
            return true;
        }
        if (!viewportFailureLogged) {
            viewportFailureLogged = true;
            ModernForgeEventBridge.log("Unable to read OpenGL viewport through LWJGL compatibility layer");
        }
        return false;
    }

    static int[] viewport() {
        IntBuffer buffer = org.lwjgl.BufferUtils.createIntBuffer(16);
        if (getViewport(buffer)) {
            return new int[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
        }
        return new int[]{0, 0, 1, 1};
    }

    private static boolean invokeViewport(String methodName, IntBuffer buffer) {
        try {
            Method method = "glGetIntegerv".equals(methodName) ? glGetIntegerv : glGetIntegerBuffer;
            if (method == null) {
                method = GL11.class.getMethod(methodName, int.class, IntBuffer.class);
                if ("glGetIntegerv".equals(methodName)) {
                    glGetIntegerv = method;
                } else {
                    glGetIntegerBuffer = method;
                }
            }
            method.invoke(null, Integer.valueOf(GL11.GL_VIEWPORT), buffer);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
