package gq.vapulite.utils;

import gq.vapulite.Vapu.VapeClickGui.ClickGuiIcons;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.modules.render.HUD;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.utils.TimerUtil;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.Minecraft;

public class Notification {
    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int SUCCESS = 3;
    public static final int MODULE = 4;

    private static boolean isLightStyle() { return HUD.isLightTheme(); }
    private static boolean isSakura() { return HUD.getTheme() == HUD.Theme.SAKURA; }

    private static int TEXT() { return isLightStyle() ? 0xFF1C1E22 : 0xFFE8EAEC; }
    private static int MUTED() { return isLightStyle() ? 0xFF606468 : 0xFFA4ADBB; }
    private static int GLASS() { return isSakura() ? 0xFFF5EEF2 : (isLightStyle() ? 0xFFEBEDF2 : 0xFF07090D); }
    private static int GLASS_SOFT() { return isSakura() ? 0xFFEBE0E6 : (isLightStyle() ? 0xFFE0E3EA : 0xFF0B0E14); }
    private static int BORDER() { return isSakura() ? 0xFFD0A8B8 : (isLightStyle() ? 0xFF6BA0C0 : 0xFF8DBED8); }
    private static int VAPE_PRIMARY() { return isSakura() ? 0xFFE090A8 : (isLightStyle() ? 0xFF6090E0 : 0xFF7C9DFF); }
    private static int VAPE_SECONDARY() { return isSakura() ? 0xFFD88098 : (isLightStyle() ? 0xFF6888E0 : 0xFF838CEF); }
    private static int VAPE_SURFACE() { return isSakura() ? 0xFFF0E8EC : (isLightStyle() ? 0xFFE8EBF0 : 0xFF171A20); }
    private static int VAPE_SURFACE_VARIANT() { return isSakura() ? 0xFFE8DCE2 : (isLightStyle() ? 0xFFDCE0E8 : 0xFF1E222B); }
    private static int VAPE_ON_VARIANT() { return isLightStyle() ? 0xFF505560 : 0xFFAAB2C5; }
    private static int ACCENT_ALT() { return isSakura() ? 0xFFE87898 : (isLightStyle() ? 0xFF6088E8 : 0xFF8B7CFF); }
    private static int onSurfaceColor() { return isLightStyle() ? 0xFF181A20 : 0xFFFFFFFF; }
    private static int progressTrack() { return isLightStyle() ? 0xFF000000 : 0xFFFFFFFF; }
    private static int shadowColor() { return isLightStyle() ? 0xFFFFFFFF : 0xFF000000; }

    public static Minecraft mc = Minecraft.getMinecraft();

    public boolean isClassicNotification;
    public String message;
    public String title;
    public String icon;
    public TimerUtil timer;
    public int type;
    public long stayTime;
    Module module;
    private float animationX;
    private float animationY;
    private final float width;
    private final float height;
    private final long createdAt;
    private long lastFrameMS;

    public Notification(String title, String message, int type, long stayTime) {
        this(title, message, type, stayTime, null);
    }

    public Notification(String title, String message, int type, long stayTime, Module module) {
        this.module = module;
        this.message = message == null ? "" : message;
        this.title = title == null ? "" : title;
        this.type = normalizeType(type);
        this.stayTime = stayTime;
        this.createdAt = System.currentTimeMillis();
        this.lastFrameMS = this.createdAt;
        this.animationX = 1.0f;
        this.height = this.message.length() == 0 ? 44.0f : 52.0f;
        this.width = Math.max(226.0f, Math.min(292.0f,
                Math.max(FontLoaders.C18.getStringWidth(getTitle()), FontLoaders.C14.getStringWidth(this.message)) + 74.0f));
        this.isClassicNotification = false;
        this.timer = new TimerUtil();
        timer.reset();
    }

    public void draw(float screenWidth, float offsetY) {
        long now = System.currentTimeMillis();
        float factor = animationFactor(now);
        float target = isFinished() ? 1.0f : 0.0f;
        animationX = lerp(animationX, target, factor);

        if (animationY == 0.0f) {
            animationY = offsetY;
        }
        animationY = lerp(animationY, offsetY, factor);

        float easedOut = ease(animationX);
        float x1 = screenWidth - width - 9.0f + easedOut * (width + 16.0f);
        float y1 = animationY - height;
        float x2 = x1 + width;
        float y2 = y1 + height;
        float bodyAlpha = 1.0f - Math.min(1.0f, animationX * 0.85f);
        int accent = getAccentColor();
        float progress = 1.0f - ColorUtils.clamp((now - createdAt) / (float) Math.max(1L, stayTime), 0.0f, 1.0f);

        if (HUD.useVapeSimpleStyle()) {
            drawVape(x1, y1, x2, y2, bodyAlpha, accent, progress);
            return;
        }

        if (HUD.isGlowEnabled()) {
            GuiRenderUtils.drawGlowAround(x1, y1, x2, y2, 8.0f,
                    withAlpha(accent, Math.round(220.0f * bodyAlpha)), 1.0f);
        }
        RenderUtil.drawSoftShadow(x1, y1, x2, y2, 8.0f,
                withAlpha(shadowColor(), Math.round(58.0f * bodyAlpha)), 7, 3.4f);
        HUD.drawThemedFrostedGlass(x1, y1, x2, y2, 8.0f, 1.0f,
                withAlpha(GLASS(), Math.round(146.0f * bodyAlpha)),
                withAlpha(BORDER(), Math.round(56.0f * bodyAlpha)));
        RenderUtil.drawHorizontalGradientRect(x1 + 10.0f, y1 + 4.0f, x2 - 10.0f, y1 + 5.1f,
                withAlpha(accent, Math.round(120.0f * bodyAlpha)),
                withAlpha(ACCENT_ALT(), Math.round(72.0f * bodyAlpha)));

        float iconX = x1 + 11.0f;
        float iconY = y1 + 10.0f;
        HUD.drawThemedFrostedGlass(iconX, iconY, iconX + 24.0f, iconY + 24.0f, 7.0f, 0.8f,
                withAlpha(GLASS_SOFT(), Math.round(154.0f * bodyAlpha)),
                withAlpha(accent, Math.round(76.0f * bodyAlpha)));
        RenderUtil.drawSoftShadow(iconX, iconY, iconX + 24.0f, iconY + 24.0f, 7.0f,
                withAlpha(accent, Math.round(28.0f * bodyAlpha)), 4, 1.8f);
        drawCenteredIcon(getIcon(), FontLoaders.I18, iconX + 12.0f, iconY + 12.0f,
                withAlpha(accent, Math.round(230.0f * bodyAlpha)));

        FontLoaders.C18.drawString(trim(getTitle(), FontLoaders.C18, width - 52.0f), x1 + 43.0f, y1 + 11.0f,
                withAlpha(TEXT(), Math.round(245.0f * bodyAlpha)));
        if (message.length() > 0) {
            FontLoaders.C14.drawString(trim(message, FontLoaders.C14, width - 54.0f), x1 + 43.0f, y1 + 27.0f,
                    withAlpha(MUTED(), Math.round(216.0f * bodyAlpha)));
        }

        RenderUtil.drawProgressBar(x1 + 12.0f, y2 - 4.0f, x2 - 12.0f, y2 - 2.3f, 1.5f, progress,
                withAlpha(progressTrack(), Math.round(18.0f * bodyAlpha)),
                withAlpha(accent, Math.round(190.0f * bodyAlpha)));
    }

    private void drawVape(float x1, float y1, float x2, float y2, float bodyAlpha, int accent, float progress) {
        float radius = 7.0f;
        if (HUD.isGlowEnabled()) {
            GuiRenderUtils.drawGlowAround(x1, y1, x2, y2, radius,
                    withAlpha(accent, Math.round(220.0f * bodyAlpha)), 1.0f);
        }
        RenderUtil.drawSoftShadow(x1, y1, x2, y2, radius,
                withAlpha(shadowColor(), Math.round(58.0f * bodyAlpha)), 6, 2.2f);
        RenderUtil.drawRoundedBorderedRect(x1, y1, x2, y2, radius, 0.8f,
                withAlpha(VAPE_SURFACE(), Math.round(164.0f * bodyAlpha)),
                withAlpha(0xFFFFFFFF, Math.round(24.0f * bodyAlpha)));
        RenderUtil.drawHorizontalGradientRect(x1 + 1.0f, y1 + 1.0f, x2 - 1.0f, y1 + 18.0f,
                withAlpha(0xFFFFFFFF, Math.round(15.0f * bodyAlpha)), withAlpha(0xFF000000, 0));
        RenderUtil.drawRoundedRect(x2 - 3.0f, y1 + 7.0f, x2 - 1.0f, y2 - 7.0f, 1.0f,
                withAlpha(accent, Math.round(205.0f * bodyAlpha)));

        float iconCenterX = x1 + 25.0f;
        float iconCenterY = y1 + (y2 - y1) / 2.0f;
        RenderUtil.drawCircle(iconCenterX, iconCenterY, 0, 360, 15.0f,
                withAlpha(accent, Math.round(218.0f * bodyAlpha)));
        RenderUtil.drawCircle(iconCenterX, iconCenterY, 0, 360, 12.0f,
                withAlpha(VAPE_SURFACE_VARIANT(), Math.round(40.0f * bodyAlpha)));
        drawCenteredIcon(getIcon(), FontLoaders.I18, iconCenterX, iconCenterY,
                withAlpha(onSurfaceColor(), Math.round(238.0f * bodyAlpha)));

        FontLoaders.C16.drawString(trim(getTitle(), FontLoaders.C16, width - 64.0f),
                x1 + 51.0f, y1 + 11.0f, withAlpha(onSurfaceColor(), Math.round(246.0f * bodyAlpha)));
        if (message.length() > 0) {
            FontLoaders.C12.drawString(trim(message, FontLoaders.C12, width - 68.0f),
                    x1 + 51.0f, y1 + 29.0f, withAlpha(VAPE_ON_VARIANT(), Math.round(220.0f * bodyAlpha)));
        }
        RenderUtil.drawProgressBar(x1 + 51.0f, y2 - 5.0f, x2 - 12.0f, y2 - 3.2f, 0.9f, progress,
                withAlpha(progressTrack(), Math.round(16.0f * bodyAlpha)),
                withAlpha(accent, Math.round(150.0f * bodyAlpha)));
    }

    public boolean shouldDelete() {
        return isFinished() && animationX > 0.985f;
    }

    public float getHeight() {
        return height;
    }

    private boolean isFinished() {
        return timer.delay(stayTime);
    }

    private String getTitle() {
        if (title.length() > 0) {
            return title;
        }
        if (module != null) {
            return module.getName();
        }
        return getTypeName();
    }

    private String getIcon() {
        if (module != null) {
            return ClickGuiIcons.forModule(module);
        }
        switch (type) {
            case SUCCESS:
                return FontLoaders.ICON_CHECKMARK;
            case ERROR:
                return FontLoaders.ICON_XMARK;
            case WARNING:
                return FontLoaders.ICON_WARNING;
            case MODULE:
                return FontLoaders.ICON_SETTINGS;
            case INFO:
            default:
                return FontLoaders.ICON_INFO;
        }
    }

    private int getAccentColor() {
        switch (type) {
            case MODULE:
            case INFO:
                return VAPE_SECONDARY();
            case WARNING:
                return 0xFFFFC857;
            case ERROR:
                return 0xFFFF5C72;
            case SUCCESS:
                return VAPE_PRIMARY();
            default:
                return VAPE_SECONDARY();
        }
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 300.0D);
    }

    private static float ease(float value) {
        float clamped = ColorUtils.clamp(value, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static float lerp(float current, float target, float factor) {
        return current + (target - current) * ColorUtils.clamp(factor, 0.0f, 1.0f);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (ColorUtils.clamp(alpha, 0, 255) << 24);
    }

    private static void drawCenteredIcon(String icon, CFontRenderer font, float centerX, float centerY, int color) {
        font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f + ClickGuiIcons.visualOffsetX(icon),
                centerY - font.getHeight() / 2.0f + 2.0f + ClickGuiIcons.visualOffsetY(icon), color);
    }

    private static String trim(String text, CFontRenderer font, float maxWidth) {
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

    private String getTypeName() {
        switch (type) {
            case WARNING:
                return "WARNING";
            case ERROR:
                return "ERROR";
            case SUCCESS:
                return "SUCCESS";
            case MODULE:
                return "MODULE";
            case INFO:
            default:
                return "INFO";
        }
    }

    private static int normalizeType(int type) {
        if (type < INFO || type > MODULE) {
            return INFO;
        }
        return type;
    }
}
