package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Manager.NotificationManager;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.VapeClickGui.ClickGuiIcons;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.ShaderRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HUD extends Module {
    private static final int TEXT = 0xFFE8EAEC;
    private static final int MUTED = 0xFF9EA8B8;
    private static final int GLASS = 0xFF07090D;
    private static final int GLASS_SOFT = 0xFF0A0D12;
    private static final int BORDER = 0xFF8DBED8;
    private static final int ACCENT = 0xFF70C1DC;
    private static final int ACCENT_ALT = 0xFF8B7CFF;

    private final Option<Boolean> watermark = new Option<Boolean>("Watermark", "Watermark", true);
    private final Option<Boolean> arrayList = new Option<Boolean>("ModuleList", "ModuleList", true);
    private final Option<Boolean> backgrounds = new Option<Boolean>("Backgrounds", "Backgrounds", true);
    private final Option<Boolean> keybinds = new Option<Boolean>("Keybinds", "Keybinds", false);
    private final Option<Boolean> notifications = new Option<Boolean>("Notifications", "Notifications", true);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 128.0, 45.0, 180.0, 5.0);
    private final Numbers<Double> radius = new Numbers<Double>("Radius", "Radius", 8.0, 3.0, 14.0, 1.0);

    private final Map<Module, Float> moduleAnimations = new HashMap<Module, Float>();
    private long lastFrameMS = System.currentTimeMillis();

    public HUD() {
        super("HUD", Keyboard.KEY_H, ModuleType.Render, "Show " + Client.name + " HUD Screen");
        Chinese = "HUD界面";
        this.addValues(watermark, arrayList, backgrounds, keybinds, notifications, alpha, radius);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!isInGame() || mc.currentScreen instanceof GuiMainMenu) {
            return;
        }

        long now = System.currentTimeMillis();
        float factor = animationFactor(now);
        ScaledResolution sr = new ScaledResolution(mc);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        ShaderRenderer.invalidateFrostedGlass();

        if (Boolean.TRUE.equals(watermark.getValue())) {
            drawWatermark();
        }
        if (Boolean.TRUE.equals(arrayList.getValue())) {
            drawModuleList(width, factor);
        }
        if (Boolean.TRUE.equals(notifications.getValue())) {
            NotificationManager.doRender(width, height);
        }
    }

    @Override
    public void disable() {
        moduleAnimations.clear();
        lastFrameMS = System.currentTimeMillis();
    }

    private void drawWatermark() {
        String title = Client.name;
        String meta = Client.version + "  |  " + Minecraft.getDebugFPS() + " FPS";
        int ping = getPing();
        if (ping >= 0) {
            meta += "  |  " + ping + " ms";
        }

        int enabled = getEnabledCount();
        String modules = enabled + "/" + ModuleManager.getModules().size() + " modules";

        float x = 6.0f;
        float y = 6.0f;
        float round = getRadius();
        float iconSize = 25.0f;
        float titleWidth = FontLoaders.C20.getStringWidth(title);
        float metaWidth = FontLoaders.C14.getStringWidth(meta);
        float modulesWidth = FontLoaders.C14.getStringWidth(modules) + 18.0f;
        float boxW = Math.max(164.0f, Math.max(titleWidth + iconSize + 46.0f, metaWidth + modulesWidth + 42.0f));
        float boxH = 43.0f;

        if (Boolean.TRUE.equals(backgrounds.getValue())) {
            drawGlass(x, y, x + boxW, y + boxH, round, getGlassAlpha(), 54);
            RenderUtil.drawHorizontalGradientRect(x + 9.0f, y + 4.0f, x + boxW - 9.0f, y + 5.2f,
                    withAlpha(ACCENT, 120), withAlpha(ACCENT_ALT, 92));
            RenderUtil.drawFrostedGlassRect(x + 10.0f, y + 9.0f, x + 10.0f + iconSize, y + 9.0f + iconSize,
                    7.0f, 0.8f, withAlpha(GLASS_SOFT, getSoftAlpha() + 18), withAlpha(ACCENT, 84));
            RenderUtil.drawSoftShadow(x + 10.0f, y + 9.0f, x + 10.0f + iconSize, y + 9.0f + iconSize,
                    7.0f, withAlpha(ACCENT, 34), 4, 2.0f);
        }

        FontLoaders.C20.drawString(trim(title, FontLoaders.C20, boxW - iconSize - 52.0f),
                x + 43.0f, y + 10.0f, TEXT);
        FontLoaders.C14.drawString(trim(meta, FontLoaders.C14, boxW - modulesWidth - 56.0f),
                x + 43.0f, y + 27.0f, MUTED);
        FontLoaders.C18.drawString("V", x + 19.0f, y + 17.0f, withAlpha(TEXT, 238));
        drawStatusChip(modules, x + boxW - modulesWidth - 9.0f, y + 24.0f, modulesWidth, ACCENT_ALT);
    }

    private void drawModuleList(int screenWidth, float factor) {
        List<Module> modules = getHudModules();
        moduleAnimations.keySet().retainAll(modules);
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                return FontLoaders.C18.getStringWidth(getDisplayName(second))
                        - FontLoaders.C18.getStringWidth(getDisplayName(first));
            }
        });

        float y = 6.0f;
        float right = screenWidth - 6.0f;
        float round = getRadius();
        int index = 0;
        for (Module module : modules) {
            String displayName = getDisplayName(module);
            if (Boolean.TRUE.equals(keybinds.getValue()) && module.getKey() != Keyboard.KEY_NONE) {
                displayName += "  " + Keyboard.getKeyName(module.getKey());
            }
            float progress = animateModule(module, factor);
            if (progress <= 0.01f) {
                continue;
            }

            int textW = FontLoaders.C18.getStringWidth(displayName);
            String icon = ClickGuiIcons.forModule(module);
            float iconSlotW = 22.0f;
            float rowW = textW + iconSlotW + 17.0f;
            float rowH = 18.0f;
            float x = right - rowW - (1.0f - progress) * 10.0f;
            int accent = getCategoryAccent(module);
            int rowAlpha = Math.round(getGlassAlpha() * progress);

            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                RenderUtil.drawSoftShadow(x, y, right, y + rowH, round,
                        withAlpha(0xFF000000, Math.round(34.0f * progress)), 4, 2.4f);
                RenderUtil.drawFrostedGlassRect(x, y, right, y + rowH, round, 0.8f,
                        withAlpha(GLASS, rowAlpha), withAlpha(BORDER, Math.round(42.0f * progress)));
                RenderUtil.drawVerticalGradientRect(right - 3.0f, y + 3.0f, right - 1.4f, y + rowH - 3.0f,
                        withAlpha(accent, Math.round(205.0f * progress)),
                        withAlpha(ColorUtils.lighten(accent, 0.16f), Math.round(165.0f * progress)));
                drawCenteredIcon(icon, FontLoaders.I16, x + iconSlotW / 2.0f + 2.0f, y + rowH / 2.0f,
                        withAlpha(accent, Math.round(214.0f * progress)));
                FontLoaders.C18.drawString(displayName, x + iconSlotW + 6.0f, y + 5.0f,
                        withAlpha(TEXT, Math.round(242.0f * progress)));
            } else {
                FontLoaders.C18.drawStringWithShadow(displayName, right - textW, y + 4.0f,
                        withAlpha(accent, Math.round(245.0f * progress)));
            }

            y += rowH + 3.0f;
            index++;
            if (index > 22) {
                break;
            }
        }
    }

    private void drawStatusChip(String text, float x, float y, float w, int accent) {
        if (Boolean.TRUE.equals(backgrounds.getValue())) {
            RenderUtil.drawFrostedGlassRect(x, y, x + w, y + 14.0f, 5.0f, 0.6f,
                    withAlpha(GLASS_SOFT, getSoftAlpha()), withAlpha(accent, 48));
            RenderUtil.drawCircle(x + 7.5f, y + 7.0f, 0, 360, 2.0f, withAlpha(accent, 185));
        }
        FontLoaders.C14.drawString(trim(text, FontLoaders.C14, w - 16.0f), x + 14.0f, y + 4.0f,
                withAlpha(TEXT, 218));
    }

    private void drawGlass(float x, float y, float x2, float y2, float round, int fillAlpha, int borderAlpha) {
        RenderUtil.drawSoftShadow(x, y, x2, y2, round, withAlpha(0xFF000000, 44), 6, 3.2f);
        RenderUtil.drawFrostedGlassRect(x, y, x2, y2, round, 1.0f,
                withAlpha(GLASS, fillAlpha), withAlpha(BORDER, borderAlpha));
    }

    private void drawCenteredIcon(String icon, CFontRenderer font, float centerX, float centerY, int color) {
        font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f + ClickGuiIcons.visualOffsetX(icon),
                centerY - font.getHeight() / 2.0f + 2.0f + ClickGuiIcons.visualOffsetY(icon), color);
    }

    private List<Module> getHudModules() {
        ArrayList<Module> modules = new ArrayList<Module>(Client.instance.moduleManager.getEnabledModules());
        for (int i = modules.size() - 1; i >= 0; i--) {
            Module module = modules.get(i);
            if (module == null || module == this || "ClickGUI".equalsIgnoreCase(module.getName())) {
                modules.remove(i);
            }
        }
        return modules;
    }

    private float animateModule(Module module, float factor) {
        Float current = moduleAnimations.get(module);
        float value = current == null ? 0.0f : current.floatValue();
        value += (1.0f - value) * factor;
        if (Math.abs(1.0f - value) < 0.01f) {
            value = 1.0f;
        }
        moduleAnimations.put(module, value);
        return value;
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 250.0D);
    }

    private int getEnabledCount() {
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.getState()) {
                enabled++;
            }
        }
        return enabled;
    }

    private int getGlassAlpha() {
        return ColorUtils.clamp(alpha.getValue().intValue(), 45, 180);
    }

    private int getSoftAlpha() {
        return ColorUtils.clamp(Math.round(getGlassAlpha() * 0.72f), 32, 150);
    }

    private float getRadius() {
        return radius.getValue().floatValue();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (ColorUtils.clamp(alpha, 0, 255) << 24);
    }

    private static int getCategoryAccent(Module module) {
        if (module.getCategory() == ModuleType.Combat) {
            return 0xFF8B7CFF;
        }
        if (module.getCategory() == ModuleType.Movement) {
            return 0xFF70C1DC;
        }
        if (module.getCategory() == ModuleType.Render) {
            return 0xFF9DE7FF;
        }
        if (module.getCategory() == ModuleType.Player) {
            return 0xFF6FD39A;
        }
        if (module.getCategory() == ModuleType.World) {
            return 0xFFFFC76D;
        }
        if (module.getCategory() == ModuleType.Config) {
            return 0xFFB7A4FF;
        }
        return 0xFFD4DAE3;
    }

    private static String getDisplayName(Module module) {
        return Client.CHINESE ? module.getChinese() : module.getName();
    }

    private static String trim(String text, gq.vapulite.font.CFontRenderer font, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && font.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private int getPing() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return -1;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }
}
