package gq.yozakura.util.notification;

import gq.yozakura.ui.click.ClickGuiIcons;
import gq.yozakura.module.Module;
import gq.yozakura.module.render.HUD;
import gq.yozakura.module.render.NightBloomHudDockRenderer;
import gq.yozakura.util.color.ColorUtils;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.RenderServices;
import net.minecraft.client.Minecraft;

public class Notification {
    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int SUCCESS = 3;
    public static final int MODULE = 4;

    private static boolean isLightStyle() { return HUD.isLightTheme() || HUD.isSakuraTheme(); }
    private static boolean isSakura() { return HUD.getTheme() == HUD.Theme.SAKURA; }

    private static int TEXT() { return isLightStyle() ? 0xFF1C1E22 : 0xFFE8EAEC; }
    private static int MUTED() { return isLightStyle() ? 0xFF606468 : 0xFFA4ADBB; }
    private static int GLASS() { return isSakura() ? 0xFFF5EEF2 : (isLightStyle() ? 0xFFEBEDF2 : 0xFF07090D); }
    private static int BORDER() { return isSakura() ? 0xFFD0A8B8 : (isLightStyle() ? 0xFF6BA0C0 : 0xFF8DBED8); }
    private static int VAPE_PRIMARY() { return isSakura() ? 0xFFE090A8 : (isLightStyle() ? 0xFF6090E0 : 0xFF7C9DFF); }
    private static int VAPE_SECONDARY() { return isSakura() ? 0xFFD88098 : (isLightStyle() ? 0xFF6888E0 : 0xFF838CEF); }
    private static int VAPE_SURFACE() { return isSakura() ? 0xFFF0E8EC : (isLightStyle() ? 0xFFE8EBF0 : 0xFF171A20); }
    private static int VAPE_SURFACE_VARIANT() { return isSakura() ? 0xFFE8DCE2 : (isLightStyle() ? 0xFFDCE0E8 : 0xFF1E222B); }
    private static int VAPE_ON_VARIANT() { return isLightStyle() ? 0xFF505560 : 0xFFAAB2C5; }
    private static int onSurfaceColor() { return isLightStyle() ? 0xFF181A20 : 0xFFFFFFFF; }
    private static int progressTrack() { return isLightStyle() ? 0xFF000000 : 0xFFFFFFFF; }
    private static int shadowColor() { return isLightStyle() ? 0xFFFFFFFF : 0xFF000000; }

    // Sakura-style colors (dark theme, matching SakuraClickGui)
    private static final int SK_TEXT = 0xFFF5F0F5;
    private static final int SK_MUTED = 0xFFB8AEB8;
    private static final int SK_SAKURA = 0xFFFFB7D1;
    private static final int SK_SAKURA_STRONG = 0xFFFF80B3;
    private static final int SK_GLASS = 0xFF08080D;
    private static final int SK_GLASS_SOFT = 0xFF160F15;
    private static final int SK_BORDER = 0xFFFFB7D1;
    private static final int SK_ACCENT = 0xFFFFB7D1;
    private static final int SK_ACCENT_ALT = 0xFFFF80B3;
    private static final int NIGHT_BLOOM_PANEL_FILL = 0xFF16161A;
    private static final int NIGHT_BLOOM_ICON_FILL = 0xFF202025;
    private static final int NIGHT_BLOOM_PRIMARY = 0xFFFF4FC7;
    private static final int NIGHT_BLOOM_SECONDARY = 0xFFEEEEEE;
    private static final float NIGHT_BLOOM_PANEL_RADIUS = 4.0F;
    private static final LiquidGlassSettings SAKURA_GLASS_SETTINGS = LiquidGlassSettings.defaults()
            .withBlurRadius(18.0f)
            .withBlurDownscale(0.92f)
            .withNoise(0.018f)
            .withRefractionScale(1.16f)
            .withHighlight(1.05f);

    public static Minecraft mc = Minecraft.getMinecraft();

    public boolean isClassicNotification;
    public String message;
    public String title;
    public String icon;
    public int type;
    public long stayTime;
    Module module;
    private float animationX;
    private float animationY;
    private final float width;
    private final float height;
    private final float modernWidth;
    private final float modernHeight;
    private final ModernNotificationLayout.Layout modernLayout;
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
        this.stayTime = Math.max(0L, stayTime);
        this.createdAt = monotonicMillis();
        this.lastFrameMS = this.createdAt;
        this.animationX = 1.0f;
        this.height = this.message.length() == 0 ? 44.0f : 52.0f;
        this.width = Math.max(226.0f, Math.min(292.0f,
                Math.max(FontLoaders.C18.getStringWidth(getTitle()),
                        FontLoaders.C14.getStringWidth(this.message)) + 74.0f));
        this.modernWidth = ModernNotificationLayout.panelWidth(
                FontLoaders.C16.getStringWidth(getTitle()),
                FontLoaders.C12.getStringWidth(this.message));
        this.modernHeight = ModernNotificationLayout.panelHeight(this.message.length() > 0);
        this.modernLayout = ModernNotificationLayout.create(0.0F, 0.0F, modernWidth, modernHeight);
        this.isClassicNotification = false;
    }

    public void draw(float screenWidth, float offsetY) {
        long now = monotonicMillis();
        float factor = animationFactor(now);
        float target = isFinished(now) ? 1.0f : 0.0f;
        animationX = lerp(animationX, target, factor);

        if (animationY == 0.0f) {
            animationY = offsetY;
        }
        animationY = lerp(animationY, offsetY, factor);

        float drawWidth = renderWidth();
        float drawHeight = renderHeight();
        float easedOut = ease(animationX);
        float x1 = screenWidth - drawWidth - 9.0f + easedOut * (drawWidth + 16.0f);
        float y1 = animationY - drawHeight;
        float x2 = x1 + drawWidth;
        float y2 = y1 + drawHeight;
        float bodyAlpha = 1.0f - Math.min(1.0f, animationX * 0.85f);
        int accent = getAccentColor();
        float progress = ModernNotificationLayout.progressForLifetime(now - createdAt, stayTime);

        if (HUD.useVapeSimpleStyle()) {
            drawVape(x1, y1, x2, y2, bodyAlpha, accent, progress);
            return;
        }

        if (HUD.isNotificationSakura()) {
            drawSakura(x1, y1, x2, y2, bodyAlpha, accent, progress);
            return;
        }

        if (useNightBloomRenderer()) {
            drawNightBloom(x1, y1, x2, y2,
                    NightBloomNotificationLayout.progressForLifetime(now - createdAt, stayTime));
            return;
        }

        drawModern(x1, y1, x2, y2, bodyAlpha, accent, progress);
    }

    private void drawModern(float x1, float y1, float x2, float y2,
                            float bodyAlpha, int accent, float progress) {
        ModernNotificationLayout.Layout layout = modernLayout;
        drawModernPanelBackground(x1, y1, x2, y2, ModernNotificationLayout.PANEL_RADIUS, bodyAlpha);

        float accentLeft = x1 + layout.getAccentLeft();
        RenderServices.shapes().rounded(accentLeft, y1 + 8.0F,
                accentLeft + layout.getAccentWidth(), y2 - 8.0F,
                layout.getAccentWidth() * 0.5F,
                withAlpha(accent, Math.round(224.0F * bodyAlpha)));

        float iconX = x1 + layout.getIconLeft();
        float iconY = y1 + layout.getIconTop();
        float iconSize = layout.getIconSize();
        RenderServices.shapes().rounded(iconX, iconY, iconX + iconSize, iconY + iconSize, 6.0F,
                withAlpha(accent, Math.round(30.0F * bodyAlpha)));
        drawCenteredIconWithOptionalGlow(getIcon(), FontLoaders.I16,
                iconX + iconSize * 0.5F, iconY + iconSize * 0.5F,
                withAlpha(accent, Math.round(244.0F * bodyAlpha)),
                withAlpha(accent, Math.round(116.0F * bodyAlpha)), 0.38F);

        float textX = x1 + layout.getTextX();
        float contentWidth = Math.max(0.0F, x2 - 8.0F - textX);
        FontLoaders.C16.drawString(trim(getTitle(), FontLoaders.C16, contentWidth),
                textX, y1 + layout.getTitleY(),
                withAlpha(TEXT(), Math.round(248.0F * bodyAlpha)));
        if (message.length() > 0) {
            FontLoaders.C12.drawString(trim(message, FontLoaders.C12, contentWidth),
                    textX, y1 + layout.getMessageY(),
                    withAlpha(MUTED(), Math.round(218.0F * bodyAlpha)));
        }

        RenderServices.shapes().progressBar(x1 + layout.getProgressLeft(), y1 + layout.getProgressTop(),
                x1 + layout.getProgressRight(), y1 + layout.getProgressBottom(), 0.8F, progress,
                withAlpha(progressTrack(), Math.round(14.0F * bodyAlpha)),
                withAlpha(accent, Math.round(176.0F * bodyAlpha)));
    }

    private void drawVape(float x1, float y1, float x2, float y2, float bodyAlpha, int accent, float progress) {
        float radius = 7.0f;
        if (HUD.isGlowEnabled() && isGlowFrameOpen()) {
            RenderServices.glow().queueRoundedRect(x1, y1, x2, y2, radius,
                    withAlpha(accent, Math.round(220.0f * bodyAlpha)), 0.62f, GlowProfile.PANEL);
        }
        drawVapePanelBackground(x1, y1, x2, y2, radius, bodyAlpha);
        RenderServices.shapes().horizontalGradient(x1 + 1.0f, y1 + 1.0f, x2 - 1.0f, y1 + 18.0f,
                withAlpha(0xFFFFFFFF, Math.round(15.0f * bodyAlpha)), withAlpha(0xFF000000, 0));
        RenderServices.shapes().rounded(x2 - 3.0f, y1 + 7.0f, x2 - 1.0f, y2 - 7.0f, 1.0f,
                withAlpha(accent, Math.round(205.0f * bodyAlpha)));

        float iconCenterX = x1 + 25.0f;
        float iconCenterY = y1 + (y2 - y1) / 2.0f;
        RenderServices.shapes().circle(iconCenterX, iconCenterY, 0, 360, 15.0f,
                withAlpha(accent, Math.round(218.0f * bodyAlpha)));
        RenderServices.shapes().circle(iconCenterX, iconCenterY, 0, 360, 12.0f,
                withAlpha(VAPE_SURFACE_VARIANT(), Math.round(40.0f * bodyAlpha)));
        drawCenteredIcon(getIcon(), FontLoaders.I18, iconCenterX, iconCenterY,
                withAlpha(onSurfaceColor(), Math.round(238.0f * bodyAlpha)));

        FontLoaders.C16.drawString(trim(getTitle(), FontLoaders.C16, width - 64.0f),
                x1 + 51.0f, y1 + 11.0f, withAlpha(onSurfaceColor(), Math.round(246.0f * bodyAlpha)));
        if (message.length() > 0) {
            FontLoaders.C12.drawString(trim(message, FontLoaders.C12, width - 68.0f),
                    x1 + 51.0f, y1 + 29.0f, withAlpha(VAPE_ON_VARIANT(), Math.round(220.0f * bodyAlpha)));
        }
        RenderServices.shapes().progressBar(x1 + 51.0f, y2 - 5.0f, x2 - 12.0f, y2 - 3.2f, 0.9f, progress,
                withAlpha(progressTrack(), Math.round(16.0f * bodyAlpha)),
                withAlpha(accent, Math.round(150.0f * bodyAlpha)));
    }

    private static void drawModernPanelBackground(float x1, float y1, float x2, float y2,
                                                  float radius, float bodyAlpha) {
        if (HUD.isHudFrostedGlassEnabled()) {
            RenderServices.shapes().shadow(x1, y1, x2, y2, radius,
                    withAlpha(0xFF000000, Math.round((isLightStyle() ? 34.0F : 72.0F) * bodyAlpha)),
                    7, 3.2F);
            HUD.drawThemedFrostedGlass(x1, y1, x2, y2, radius, 0.55F,
                    withAlpha(GLASS(), Math.round(158.0F * bodyAlpha)),
                    withAlpha(BORDER(), Math.round(30.0F * bodyAlpha)));
            return;
        }

        RenderServices.shapes().shadow(x1, y1, x2, y2, radius,
                HUD.getSolidPanelShadowColor(bodyAlpha), 7, 3.6F);
        RenderServices.shapes().roundedBorder(x1, y1, x2, y2, radius, 0.55F,
                HUD.getSolidPanelFillColor(bodyAlpha), HUD.getSolidPanelBorderColor(bodyAlpha));
    }

    private static void drawVapePanelBackground(float x1, float y1, float x2, float y2, float radius,
                                                float bodyAlpha) {
        if (HUD.isHudFrostedGlassEnabled()) {
            RenderServices.shapes().shadow(x1, y1, x2, y2, radius,
                    withAlpha(shadowColor(), Math.round(58.0f * bodyAlpha)), 6, 2.2f);
            RenderServices.shapes().roundedBorder(x1, y1, x2, y2, radius, 0.8f,
                    withAlpha(VAPE_SURFACE(), Math.round(164.0f * bodyAlpha)),
                    withAlpha(0xFFFFFFFF, Math.round(24.0f * bodyAlpha)));
            return;
        }

        drawHudSolidPanel(x1, y1, x2, y2, radius, 0.8f, bodyAlpha);
    }

    private static void drawHudSolidPanel(float x1, float y1, float x2, float y2, float radius,
                                          float borderWidth, float opacity) {
        RenderServices.shapes().shadow(x1, y1, x2, y2, radius,
                HUD.getSolidPanelShadowColor(opacity), 8, 4.8f);
        RenderServices.shapes().roundedBorder(x1, y1, x2, y2, radius, Math.min(borderWidth, 0.65f),
                HUD.getSolidPanelFillColor(opacity), HUD.getSolidPanelBorderColor(opacity));
        drawSolidPanelHighlight(x1, y1, x2, y2, opacity);
    }

    private static void drawSolidPanelHighlight(float x1, float y1, float x2, float y2, float opacity) {
        int alpha = (HUD.isLightTheme() || HUD.isSakuraTheme()) ? 26 : 12;
        RenderServices.shapes().horizontalGradient(x1 + 1.0f, y1 + 1.0f, x2 - 1.0f,
                Math.min(y2 - 1.0f, y1 + 10.0f), withAlpha(0xFFFFFFFF, Math.round(alpha * opacity)), 0x00FFFFFF);
    }

    private void drawNightBloom(float x1, float y1, float x2, float y2, float progress) {
        float alpha = NightBloomNotificationLayout.alphaForSlide(animationX);
        if (alpha <= 0.002F) {
            return;
        }
        boolean stackDocked = NightBloomHudDockRenderer.hasLink("hud_notifications");

        NightBloomNotificationLayout.LiquidPair pair =
                NightBloomNotificationLayout.createLiquidPair(x1, y1, x2, y2, alpha);
        if (!pair.isRenderable()) {
            if (!stackDocked) {
                HUD.drawNightBloomShadow(x1, y1, x2, y2, NIGHT_BLOOM_PANEL_RADIUS, alpha);
                RenderServices.shapes().rounded(x1, y1, x2, y2, NIGHT_BLOOM_PANEL_RADIUS,
                        withAlpha(NIGHT_BLOOM_PANEL_FILL, Math.round(220.0F * alpha)));
            }
            return;
        }

        if (!stackDocked) {
            drawNightBloomFusedShadows(pair, alpha);
            drawNightBloomFusedSurfaces(pair, alpha);
        }

        float iconSize = pair.getIconSize();
        float iconCenterX = pair.getIconCenterX();
        float iconCenterY = pair.getIconCenterY();
        RenderServices.shapes().circle(iconCenterX, iconCenterY, 0, 360, iconSize * 0.5F,
                withAlpha(NIGHT_BLOOM_ICON_FILL, Math.round(217.0F * alpha)));
        HUD.drawNightBloomCenteredIcon(getIcon(), FontLoaders.I16, iconCenterX, iconCenterY,
                withAlpha(NIGHT_BLOOM_PRIMARY, Math.round(246.0F * alpha)),
                withAlpha(NIGHT_BLOOM_PRIMARY, Math.round(156.0F * alpha)), 0.58F);

        float contentWidth = Math.max(0.0F, pair.getProgressRight() - pair.getTitleX());
        String titleText = trim(getTitle(), FontLoaders.C16, contentWidth);
        HUD.drawNightBloomText(FontLoaders.C16, titleText, pair.getTitleX(), y1 + 7.0F,
                withAlpha(NIGHT_BLOOM_PRIMARY, Math.round(248.0F * alpha)),
                withAlpha(NIGHT_BLOOM_PRIMARY, Math.round(94.0F * alpha)), 0.44F);
        if (message.length() > 0) {
            String messageText = trim(message, FontLoaders.C12, contentWidth);
            HUD.drawNightBloomText(FontLoaders.C12, messageText, pair.getTitleX(), y1 + 22.0F,
                    withAlpha(NIGHT_BLOOM_SECONDARY, Math.round(224.0F * alpha)),
                    withAlpha(NIGHT_BLOOM_PRIMARY, Math.round(48.0F * alpha)), 0.24F);
        }

        RenderServices.shapes().progressBar(pair.getProgressLeft(), y2 - 3.8F,
                pair.getProgressRight(), y2 - 2.0F, 1.0F, progress,
                withAlpha(NIGHT_BLOOM_SECONDARY, Math.round(28.0F * alpha)),
                withAlpha(NIGHT_BLOOM_PRIMARY, Math.round(224.0F * alpha)));
    }

    private void drawNightBloomFusedShadows(NightBloomNotificationLayout.LiquidPair pair, float alpha) {
        float individualOpacity = 1.0F - pair.getCompositeProgress();
        if (individualOpacity > 0.01F) {
            HUD.drawNightBloomShadow(pair.getIconLeft(), pair.getIconTop(),
                    pair.getIconRight(), pair.getIconBottom(), NIGHT_BLOOM_PANEL_RADIUS,
                    alpha * individualOpacity);
            HUD.drawNightBloomShadow(pair.getBodyLeft(), pair.getCompositeTop(),
                    pair.getBodyRight(), pair.getCompositeBottom(), NIGHT_BLOOM_PANEL_RADIUS,
                    alpha * individualOpacity);
        }
        if (pair.hasVisibleBridge()) {
            HUD.drawNightBloomShadow(pair.getBridgeLeft(), pair.getBridgeTop(),
                    pair.getBridgeRight(), pair.getBridgeBottom(), pair.getBridgeRadius(),
                    alpha * pair.getBridgeOpacity() * individualOpacity);
        }
        if (pair.getCompositeProgress() > 0.01F) {
            HUD.drawNightBloomShadow(pair.getCompositeLeft(), pair.getCompositeTop(),
                    pair.getCompositeRight(), pair.getCompositeBottom(), NIGHT_BLOOM_PANEL_RADIUS,
                    alpha * pair.getCompositeProgress());
        }
    }

    private void drawNightBloomFusedSurfaces(NightBloomNotificationLayout.LiquidPair pair, float alpha) {
        float individualOpacity = 1.0F - pair.getCompositeProgress();
        if (pair.hasVisibleBridge() && individualOpacity > 0.01F) {
            float bridgeOpacity = alpha * pair.getBridgeOpacity() * individualOpacity;
            RenderServices.shapes().joinedRounded(pair.getBridgeLeft(), pair.getBridgeTop(),
                    pair.getBridgeRight(), pair.getBridgeBottom(),
                    pair.getBridgeRadius(), pair.getBridgeRadius(), pair.getBridgeRadius(), pair.getBridgeRadius(),
                    nightBloomSurfaceColor(bridgeOpacity));
        }
        if (individualOpacity > 0.01F) {
            float joinRadius = NIGHT_BLOOM_PANEL_RADIUS * (1.0F - pair.getEdgeProgress());
            int tileColor = nightBloomSurfaceColor(alpha * individualOpacity);
            RenderServices.shapes().joinedRounded(pair.getIconLeft(), pair.getIconTop(),
                    pair.getIconRight(), pair.getIconBottom(),
                    NIGHT_BLOOM_PANEL_RADIUS, joinRadius, joinRadius, NIGHT_BLOOM_PANEL_RADIUS,
                    1.0F, 0.0F, 1.0F, 0.0F,
                    1.0F, 0.0F, pair.getRightJoinStart(), pair.getRightJoinEnd(), tileColor);
            RenderServices.shapes().joinedRounded(pair.getBodyLeft(), pair.getCompositeTop(),
                    pair.getBodyRight(), pair.getCompositeBottom(),
                    joinRadius, NIGHT_BLOOM_PANEL_RADIUS, NIGHT_BLOOM_PANEL_RADIUS, joinRadius,
                    1.0F, 0.0F, 1.0F, 0.0F,
                    pair.getLeftJoinStart(), pair.getLeftJoinEnd(), 1.0F, 0.0F, tileColor);
        }
        if (pair.getCompositeProgress() > 0.01F) {
            RenderServices.shapes().joinedRounded(pair.getCompositeLeft(), pair.getCompositeTop(),
                    pair.getCompositeRight(), pair.getCompositeBottom(),
                    NIGHT_BLOOM_PANEL_RADIUS, NIGHT_BLOOM_PANEL_RADIUS,
                    NIGHT_BLOOM_PANEL_RADIUS, NIGHT_BLOOM_PANEL_RADIUS,
                    nightBloomSurfaceColor(NightBloomNotificationLayout.fusedCompositeSurfaceOpacity(
                            220.0F / 255.0F, alpha, pair.getCompositeProgress())));
        }
    }

    private static int nightBloomSurfaceColor(float alpha) {
        return withAlpha(NIGHT_BLOOM_PANEL_FILL,
                Math.round(220.0F * ColorUtils.clamp(alpha, 0.0F, 1.0F)));
    }

    private void drawSakura(float x1, float y1, float x2, float y2, float bodyAlpha, int accent, float progress) {
        float radius = 8.0f;
        float sakuraAlpha = bodyAlpha;

        // Double shadow (black + sakura glow)
        RenderServices.shapes().shadow(x1, y1, x2, y2, radius,
                withAlpha(0xFF000000, Math.round(64.0f * sakuraAlpha)), 6, 2.4f);
        RenderServices.shapes().shadow(x1, y1, x2, y2, radius,
                withAlpha(SK_SAKURA, Math.round(26.0f * sakuraAlpha)), 4, 1.8f);

        // Dark glass background with sakura border
        RenderServices.liquidGlass().roundedBorder(x1, y1, x2, y2, radius, 0.52f,
                withAlpha(SK_GLASS, Math.round(158.0f * sakuraAlpha)),
                withAlpha(SK_BORDER, Math.round(32.0f * sakuraAlpha)),
                SAKURA_GLASS_SETTINGS);

        // Top accent gradient line
        RenderServices.shapes().horizontalGradient(x1 + 10.0f, y1 + 4.0f, x2 - 10.0f, y1 + 5.1f,
                withAlpha(SK_SAKURA, Math.round(130.0f * sakuraAlpha)),
                withAlpha(SK_SAKURA_STRONG, Math.round(80.0f * sakuraAlpha)));

        // Icon well
        float iconX = x1 + 11.0f;
        float iconY = y1 + 10.0f;
        RenderServices.shapes().shadow(iconX, iconY, iconX + 24.0f, iconY + 24.0f, 7.0f,
                withAlpha(0xFF000000, Math.round(48.0f * sakuraAlpha)), 4, 1.4f);
        RenderServices.shapes().roundedBorder(iconX, iconY, iconX + 24.0f, iconY + 24.0f, 7.0f, 0.8f,
                withAlpha(SK_GLASS_SOFT, Math.round(168.0f * sakuraAlpha)),
                withAlpha(SK_SAKURA, Math.round(58.0f * sakuraAlpha)));
        drawCenteredIcon(getIcon(), FontLoaders.I18, iconX + 12.0f, iconY + 12.0f,
                withAlpha(SK_SAKURA, Math.round(230.0f * sakuraAlpha)));

        // Title with glow
        String titleStr = trim(getTitle(), FontLoaders.C18, width - 52.0f);
        float titleX = x1 + 43.0f;
        float titleY = y1 + 11.0f;
        drawStringWithOptionalGlow(FontLoaders.C18, titleStr, titleX, titleY,
                withAlpha(SK_TEXT, Math.round(245.0f * sakuraAlpha)),
                withAlpha(SK_SAKURA, Math.round(184.0f * sakuraAlpha)), 0.62F);

        // Message in muted sakura
        if (message.length() > 0) {
            FontLoaders.C14.drawString(trim(message, FontLoaders.C14, width - 54.0f),
                    x1 + 43.0f, y1 + 27.0f,
                    withAlpha(SK_MUTED, Math.round(216.0f * sakuraAlpha)));
        }

        // Progress bar in sakura pink
        RenderServices.shapes().progressBar(x1 + 12.0f, y2 - 4.0f, x2 - 12.0f, y2 - 2.3f, 1.5f, progress,
                withAlpha(0xFF30262F, Math.round(80.0f * sakuraAlpha)),
                withAlpha(SK_SAKURA, Math.round(210.0f * sakuraAlpha)));
    }

    public boolean shouldDelete() {
        return isFinished(monotonicMillis()) && animationX > 0.985f;
    }

    private float renderWidth() {
        if (useNightBloomRenderer()) {
            return NightBloomNotificationLayout.panelWidth(
                    FontLoaders.C16.getStringWidth(getTitle()),
                    FontLoaders.C12.getStringWidth(message));
        }
        if (useModernRenderer()) {
            return modernWidth;
        }
        return width;
    }

    private float renderHeight() {
        if (useNightBloomRenderer()) {
            return NightBloomNotificationLayout.panelHeight(message.length() > 0);
        }
        if (useModernRenderer()) {
            return modernHeight;
        }
        return height;
    }

    private boolean useModernRenderer() {
        return !HUD.useVapeSimpleStyle()
                && !HUD.isNotificationSakura()
                && !HUD.isNotificationNightBloom();
    }

    private boolean useNightBloomRenderer() {
        return !HUD.useVapeSimpleStyle()
                && !HUD.isNotificationSakura()
                && HUD.isNotificationNightBloom();
    }

    public float getHeight() {
        return renderHeight();
    }

    public float getWidth() {
        return renderWidth();
    }

    private boolean isFinished(long now) {
        return now - createdAt >= stayTime;
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

    private static long monotonicMillis() {
        return System.nanoTime() / 1000000L;
    }

    private static boolean isGlowFrameOpen() {
        return RenderServices.glow().isFrameOpen();
    }

    private static void drawStringWithOptionalGlow(CFontRenderer font, String text, float x, float y,
                                                   int textColor, int glowColor, float glowStrength) {
        if (HUD.isGlowEnabled() && isGlowFrameOpen()) {
            font.drawStringWithGlow(text, x, y, textColor, glowColor, glowStrength, GlowProfile.TEXT);
            return;
        }
        font.drawString(text, x, y, textColor);
    }

    private static void drawCenteredIcon(String icon, CFontRenderer font, float centerX, float centerY, int color) {
        font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f + ClickGuiIcons.visualOffsetX(icon),
                centerY - font.getHeight() / 2.0f + 2.0f + ClickGuiIcons.visualOffsetY(icon), color);
    }

    private static void drawCenteredIconWithOptionalGlow(String icon, CFontRenderer font, float centerX, float centerY,
                                                          int textColor, int glowColor, float glowStrength) {
        float x = centerX - font.getStringWidth(icon) / 2.0F + ClickGuiIcons.visualOffsetX(icon);
        float y = centerY - font.getHeight() / 2.0F + 2.0F + ClickGuiIcons.visualOffsetY(icon);
        if (HUD.isGlowEnabled() && isGlowFrameOpen()) {
            font.drawStringWithGlow(icon, x, y, textColor, glowColor, glowStrength, GlowProfile.ACCENT);
            return;
        }
        font.drawString(icon, x, y, textColor);
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
