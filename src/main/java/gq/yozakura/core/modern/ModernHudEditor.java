package gq.yozakura.core.modern;

import gq.yozakura.ui.click.web.ModernWebClickGuiState;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModernHudEditor {
    private static final Map<String, Element> ELEMENTS = new LinkedHashMap<String, Element>();
    private static Method glfwGetCursorPos;
    private static Method glfwGetMouseButton;
    private static String activeId;
    private static String selectedId;
    private static int mouseX;
    private static int mouseY;
    private static float dragOffsetX;
    private static float dragOffsetY;
    private static boolean leftDown;
    private static boolean editMode;
    private static Object minecraft;

    private ModernHudEditor() {
    }

    static void beginFrame(Object minecraftInstance, int screenWidth, int screenHeight) {
        minecraft = minecraftInstance;
        editMode = isEditMode(minecraftInstance);
        updateMouseFromWindow(minecraftInstance, screenWidth, screenHeight);
        if (!editMode) {
            activeId = null;
            selectedId = null;
        } else if (!leftDown) {
            activeId = null;
        }
        ELEMENTS.clear();
    }

    static Element place(String id, String module, String xValue, String yValue, String scaleValue,
                         float defaultX, float defaultY, float width, float height,
                         int screenWidth, int screenHeight, float scale, float minScale, float maxScale) {
        float x = (float) ModernWebClickGuiState.numberValue(module, xValue, defaultX);
        float y = (float) ModernWebClickGuiState.numberValue(module, yValue, defaultY);
        if (x < 0.0f) {
            x = defaultX;
        }
        if (y < 0.0f) {
            y = defaultY;
        }
        x = clamp(x, 2.0f, Math.max(2.0f, screenWidth - width - 2.0f));
        y = clamp(y, 2.0f, Math.max(2.0f, screenHeight - height - 2.0f));

        boolean hovered = hit(mouseX, mouseY, x, y, width, height);
        if (editMode && leftDown && activeId == null && hovered) {
            activeId = id;
            selectedId = id;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
        if (editMode && id.equals(activeId)) {
            x = clamp(mouseX - dragOffsetX, 2.0f, Math.max(2.0f, screenWidth - width - 2.0f));
            y = clamp(mouseY - dragOffsetY, 2.0f, Math.max(2.0f, screenHeight - height - 2.0f));
            ModernWebClickGuiState.setNumberValue(module, xValue, roundPosition(x));
            ModernWebClickGuiState.setNumberValue(module, yValue, roundPosition(y));
        }

        Element element = new Element(id, module, scaleValue, x, y, width, height, scale, minScale, maxScale);
        ELEMENTS.put(id, element);
        return element;
    }

    static boolean handleScroll(Object event) {
        if (!isEditMode(minecraft)) {
            return false;
        }
        double scroll = doubleValue(ModernForgeEventBridge.invoke(event, "getScrollDelta"), 0.0D);
        if (Math.abs(scroll) < 0.0001D) {
            return false;
        }
        Object eventX = ModernForgeEventBridge.invoke(event, "getMouseX");
        Object eventY = ModernForgeEventBridge.invoke(event, "getMouseY");
        int mx = eventX instanceof Number ? Math.round(((Number) eventX).floatValue()) : mouseX;
        int my = eventY instanceof Number ? Math.round(((Number) eventY).floatValue()) : mouseY;

        List<Element> elements = new ArrayList<Element>(ELEMENTS.values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            Element element = elements.get(i);
            if (element.scaleValue == null || !hit(mx, my, element.x, element.y, element.width, element.height)) {
                continue;
            }
            double current = ModernWebClickGuiState.numberValue(element.module, element.scaleValue, element.scale);
            double step = Math.abs(scroll) >= 2.0D ? 0.10D : 0.05D;
            double next = current + (scroll > 0.0D ? step : -step);
            next = Math.max(element.minScale, Math.min(element.maxScale, next));
            ModernWebClickGuiState.setNumberValue(element.module, element.scaleValue, next);
            selectedId = element.id;
            ModernForgeEventBridge.invoke(event, "setCanceled", Boolean.TRUE);
            return true;
        }
        return false;
    }

    static void drawHint(ModernRender2D render, Element element, int color) {
        if (!editMode || element == null) {
            return;
        }
        boolean hovered = hit(mouseX, mouseY, element.x, element.y, element.width, element.height);
        if (!hovered && !element.id.equals(activeId) && !element.id.equals(selectedId)) {
            return;
        }
        int alpha = element.id.equals(activeId) ? 150 : hovered ? 112 : 78;
        int border = (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        render.roundedBorder(Math.round(element.x) - 1, Math.round(element.y) - 1,
                Math.round(element.width) + 2, Math.round(element.height) + 2,
                Math.max(4, Math.round(Math.min(element.width, element.height) / 5.0f)),
                0x00000000, border);
    }

    private static void updateMouseFromWindow(Object minecraftInstance, int guiWidth, int guiHeight) {
        try {
            Object window = ModernForgeEventBridge.invoke(minecraftInstance, "getWindow");
            if (window == null) {
                window = ModernForgeEventBridge.invoke(minecraftInstance, "m_91268_");
            }
            long handle = longValue(ModernForgeEventBridge.invoke(window, "getWindow"), 0L);
            if (handle == 0L) {
                handle = longValue(ModernForgeEventBridge.invoke(window, "m_85439_"), 0L);
            }
            if (handle == 0L) {
                leftDown = false;
                return;
            }
            double[] x = new double[1];
            double[] y = new double[1];
            if (glfwGetCursorPos == null) {
                Class<?> glfw = ModernForgeEventBridge.findClass("org.lwjgl.glfw.GLFW");
                glfwGetCursorPos = glfw.getMethod("glfwGetCursorPos", long.class, double[].class, double[].class);
                glfwGetMouseButton = glfw.getMethod("glfwGetMouseButton", long.class, int.class);
            }
            glfwGetCursorPos.invoke(null, Long.valueOf(handle), x, y);
            int windowWidth = positiveInt(window, new String[]{"getWidth", "m_85443_", "m_85441_"}, guiWidth);
            int windowHeight = positiveInt(window, new String[]{"getHeight", "m_85444_", "m_85442_"}, guiHeight);
            mouseX = Math.round((float) (x[0] * guiWidth / Math.max(1, windowWidth)));
            mouseY = Math.round((float) (y[0] * guiHeight / Math.max(1, windowHeight)));
            Object button = glfwGetMouseButton.invoke(null, Long.valueOf(handle), Integer.valueOf(0));
            leftDown = button instanceof Number && ((Number) button).intValue() == 1;
        } catch (Throwable throwable) {
            leftDown = false;
        }
    }

    private static boolean isEditMode(Object minecraftInstance) {
        if (minecraftInstance == null || ModernMinecraftAccess.player(minecraftInstance) == null
                || ModernMinecraftAccess.level(minecraftInstance) == null) {
            return false;
        }
        Object screen = ModernForgeEventBridge.field(minecraftInstance, "screen");
        if (screen == null) {
            screen = ModernForgeEventBridge.field(minecraftInstance, "f_91080_");
        }
        return isChatScreen(screen);
    }

    private static boolean isChatScreen(Object screen) {
        if (screen == null) {
            return false;
        }
        Class<?> type = screen.getClass();
        while (type != null) {
            String name = type.getName();
            String simpleName = type.getSimpleName();
            if ("GuiChat".equals(simpleName) || "ChatScreen".equals(simpleName)
                    || "net.minecraft.client.gui.GuiChat".equals(name)
                    || "net.minecraft.client.gui.screen.ChatScreen".equals(name)
                    || "net.minecraft.client.gui.screens.ChatScreen".equals(name)) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static int positiveInt(Object target, String[] names, int fallback) {
        for (String name : names) {
            Object value = ModernForgeEventBridge.invoke(target, name);
            if (value instanceof Number && ((Number) value).intValue() > 0) {
                return ((Number) value).intValue();
            }
        }
        return Math.max(1, fallback);
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private static double roundPosition(float value) {
        return Math.round(value * 10.0f) / 10.0D;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Element {
        final String id;
        final String module;
        final String scaleValue;
        final float x;
        final float y;
        final float width;
        final float height;
        final float scale;
        final float minScale;
        final float maxScale;

        Element(String id, String module, String scaleValue, float x, float y, float width, float height,
                float scale, float minScale, float maxScale) {
            this.id = id;
            this.module = module;
            this.scaleValue = scaleValue;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.minScale = minScale;
            this.maxScale = maxScale;
        }
    }
}
