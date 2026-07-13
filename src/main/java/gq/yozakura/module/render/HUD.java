package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.manager.NotificationManager;
import gq.yozakura.core.Client;
import gq.yozakura.module.ModuleType;
import gq.yozakura.ui.click.ClickGuiIcons;
import gq.yozakura.module.Module;
import gq.yozakura.util.color.ColorUtils;
import gq.yozakura.util.render.HudDrag;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.glow.GlowRenderer;
import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.util.animation.UiClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HUD extends Module {
    private static final int SAKURA_TEXT = 0xFFF5F0F5;
    private static final int SAKURA_MUTED = 0xFFB8AEB8;
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_STRONG = 0xFFFF80B3;
    private static final int SAKURA_GLASS = 0xFF08080D;
    private static final VisualPalette NIGHT_BLOOM_PALETTE = VisualPalette.nightBloom();
    private static final int NIGHT_BLOOM_PRIMARY = NightBloomHudLayout.PRIMARY_COLOR;
    private static final int NIGHT_BLOOM_SECONDARY = NightBloomHudLayout.SECONDARY_COLOR;
    private static final int NIGHT_BLOOM_SURFACE = NightBloomHudLayout.SURFACE_COLOR;
    private static final int NIGHT_BLOOM_SURFACE_RAISED = NightBloomHudLayout.SURFACE_RAISED_COLOR;
    private static final float NIGHT_BLOOM_RADIUS = NightBloomHudLayout.PANEL_RADIUS;
    private static final long MODULE_LIST_CACHE_MILLIS = 80L;
    private static final LiquidGlassSettings SAKURA_GLASS_SETTINGS = LiquidGlassSettings.defaults()
            .withBlurRadius(18.0f)
            .withBlurDownscale(0.92f)
            .withNoise(0.018f)
            .withRefractionScale(1.16f)
            .withHighlight(1.05f);
    private static final float[][] SAKURA_PETAL_POINTS = new float[][]{
            {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
            {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
            {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f},
            {0.00f, -0.18f}
    };

    public enum HudStyle {
        YOZAKURA,
        OLD,
        NIGHT_BLOOM
    }

    public enum Theme {
        DARK,
        LIGHT,
        SAKURA,
        GRAY
    }

    public enum ArrayListTheme {
        OLD,
        SAKURA
    }

    public enum NotificationTheme {
        OLD,
        SAKURA,
        NIGHT_BLOOM
    }

    private static final class HudPalette {
        final int text, muted, glass, glassSoft, border, accent, accentAlt;
        final int vapePrimary, vapeSecondary, vapeTertiary;
        final int vapeSurface, vapeSurfaceVariant, vapeOnSurface, vapeOnVariant;
        final int shadowColor;

        HudPalette(int text, int muted, int glass, int glassSoft, int border, int accent, int accentAlt,
                   int vapePrimary, int vapeSecondary, int vapeTertiary,
                   int vapeSurface, int vapeSurfaceVariant, int vapeOnSurface, int vapeOnVariant,
                   int shadowColor) {
            this.text = text; this.muted = muted; this.glass = glass; this.glassSoft = glassSoft;
            this.border = border; this.accent = accent; this.accentAlt = accentAlt;
            this.vapePrimary = vapePrimary; this.vapeSecondary = vapeSecondary; this.vapeTertiary = vapeTertiary;
            this.vapeSurface = vapeSurface; this.vapeSurfaceVariant = vapeSurfaceVariant;
            this.vapeOnSurface = vapeOnSurface; this.vapeOnVariant = vapeOnVariant;
            this.shadowColor = shadowColor;
        }

        static final HudPalette DARK = new HudPalette(
                0xFFE8EAEC, 0xFF9EA8B8, 0xFF07090D, 0xFF0A0D12, 0xFF8DBED8,
                0xFF70C1DC, 0xFF8B7CFF,
                0xFF7C9DFF, 0xFF838CEF, 0xFF5AD4FF,
                0xFF171A20, 0xFF1E222B, 0xFFFFFFFF, 0xFFAAB2C5,
                0xFF000000);

        static final HudPalette LIGHT = new HudPalette(
                0xFF1C1E22, 0xFF606468, 0xFFEBEDF2, 0xFFE0E3EA, 0xFF6BA0C0,
                0xFF18A0C8, 0xFF6088E8,
                0xFF6090E0, 0xFF6888E0, 0xFF20AAD4,
                0xFFE8EBF0, 0xFFDCE0E8, 0xFF181A20, 0xFF505560,
                0xFFFFFFFF);

        static final HudPalette SAKURA = new HudPalette(
                0xFF241E26, 0xFF7A6E78, 0xFFFDF4F8, 0xFFF6E8F0, 0xFFE5AFC7,
                0xFFE56B9D, 0xFFD88AC4,
                0xFFE56B9D, 0xFFD979A8, 0xFFF3A4C8,
                0xFFFFF7FA, 0xFFF4E4ED, 0xFF241E26, 0xFF786A75,
                0x66F1B5CC);

        static final HudPalette GRAY = new HudPalette(
                0xFFE7E8EA, 0xFFA8ABB0, 0xFF171A1F, 0xFF1F232A, 0xFFB7BDC6,
                0xFFB8C0CC, 0xFFD6DAE0,
                0xFFB8C0CC, 0xFFA8B0BC, 0xFFE0E4EA,
                0xFF1B1F25, 0xFF252A32, 0xFFF4F5F6, 0xFFB8BEC8,
                0xFF000000);
    }

    private static HudPalette palette() {
        Theme t = getTheme();
        if (t == Theme.SAKURA) return HudPalette.SAKURA;
        if (t == Theme.GRAY) return HudPalette.GRAY;
        return t == Theme.LIGHT ? HudPalette.LIGHT : HudPalette.DARK;
    }

    private static HUD instance;
    private static HudStyle activeStyle = HudStyle.YOZAKURA;

    private final Option<Boolean> watermark = new Option<Boolean>("Watermark", "Watermark", true);
    private final Option<Boolean> arrayList = new Option<Boolean>("ModuleList", "ModuleList", true);
    private final Option<Boolean> backgrounds = new Option<Boolean>("Backgrounds", "Backgrounds", true);
    private final Option<Boolean> frostedGlass = new Option<Boolean>("Frosted Glass", "FrostedGlass", true);
    private final Option<Boolean> keybinds = new Option<Boolean>("Keybinds", "Keybinds", false);
    private final Option<Boolean> parameters = new Option<Boolean>("Parameters", "Parameters", true);
    private final Option<Boolean> notifications = new Option<Boolean>("Notifications", "Notifications", true);
    private final Option<Boolean> potionEffects = new Option<Boolean>("PotionEffects", "PotionEffects", true);
    private final Option<Boolean> inventoryDisplay = new Option<Boolean>("Inventory", "Inventory", true);
    private final Option<Boolean> glow = new Option<Boolean>("Glow", "Glow", false);
    private final Mode<HudStyle> hudStyle = new Mode<HudStyle>("HUD Style", "HUDStyle", HudStyle.values(), HudStyle.YOZAKURA);
    private final Mode<Theme> theme = new Mode<Theme>("Theme", "Theme", Theme.values(), Theme.DARK);
    private final Mode<ArrayListTheme> arrayListTheme = new Mode<ArrayListTheme>("ArrayList Theme", "ArrayListTheme", ArrayListTheme.values(), ArrayListTheme.OLD);
    private final Mode<NotificationTheme> notificationTheme = new Mode<NotificationTheme>("Notification Theme", "NotificationTheme", NotificationTheme.values(), NotificationTheme.OLD);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 128.0, 45.0, 180.0, 5.0);
    private final Numbers<Double> radius = new Numbers<Double>("Radius", "Radius", 8.0, 3.0, 14.0, 1.0);
    private final Numbers<Double> watermarkX = new Numbers<Double>("Watermark X", "WatermarkX", 6.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> watermarkY = new Numbers<Double>("Watermark Y", "WatermarkY", 6.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> watermarkScale = new Numbers<Double>("Watermark Scale", "WatermarkScale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> moduleListX = new Numbers<Double>("ModuleList X", "ModuleListX", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> moduleListY = new Numbers<Double>("ModuleList Y", "ModuleListY", 6.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> moduleListScale = new Numbers<Double>("ModuleList Scale", "ModuleListScale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> potionX = new Numbers<Double>("Potion X", "PotionX", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> potionY = new Numbers<Double>("Potion Y", "PotionY", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> potionScale = new Numbers<Double>("Potion Scale", "PotionScale", 1.0, 0.65, 1.8, 0.05);
    private final Numbers<Double> inventoryX = new Numbers<Double>("Inventory X", "InventoryX", -1.0, -1.0, 2000.0, 1.0);
    private final Numbers<Double> inventoryY = new Numbers<Double>("Inventory Y", "InventoryY", -1.0, -1.0, 1200.0, 1.0);
    private final Numbers<Double> inventoryScale = new Numbers<Double>("Inventory Scale", "InventoryScale", 1.0, 0.65, 1.8, 0.05);

    private final Map<Module, Float> moduleAnimations = new HashMap<Module, Float>();
    private final Map<Module, NightBloomModuleRowMotion> nightBloomModuleMotions = new HashMap<Module, NightBloomModuleRowMotion>();
    private final Map<Module, ModuleListEntry> nightBloomModuleEntries = new HashMap<Module, ModuleListEntry>();
    private final Map<Module, Float> nightBloomModuleWidthScratch = new HashMap<Module, Float>();
    private final List<ModuleListEntry> nightBloomModuleSortScratch = new ArrayList<ModuleListEntry>();
    private final List<Module> nightBloomVisibleModuleScratch = new ArrayList<Module>();
    private final List<NightBloomModuleRenderEntry> nightBloomModuleRenderScratch = new ArrayList<NightBloomModuleRenderEntry>();
    private final UiClock nightBloomModuleClock = new UiClock();
    private final Map<Integer, Float> nightBloomPotionAnimations = new HashMap<Integer, Float>();
    private final float[] nightBloomInventorySlotAnimations = new float[27];
    private final List<Module> hudModuleScratch = new ArrayList<Module>();
    private final List<ModuleListEntry> moduleEntryCache = new ArrayList<ModuleListEntry>();
    private int moduleEntryCacheSignature;
    private int moduleEntryCacheGap;
    private CFontRenderer moduleEntryCacheFont;
    private long moduleEntryCacheTime;
    private long lastFrameMS = System.currentTimeMillis();
    private float nightBloomInventoryFill;

    public HUD() {
        super("HUD", Keyboard.KEY_H, ModuleType.Render, "Show " + Client.name + " HUD Screen");
        Chinese = "HUD界面";
        instance = this;
        activeStyle = getSelectedStyle();
        this.addValues(hudStyle,notificationTheme, arrayListTheme, theme, watermark, arrayList, backgrounds, frostedGlass, keybinds, parameters, notifications,
                potionEffects, inventoryDisplay, glow, alpha, radius, watermarkX, watermarkY, watermarkScale,
                moduleListX, moduleListY, moduleListScale, potionX, potionY, potionScale, inventoryX, inventoryY,
                inventoryScale);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        renderOverlay();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            RenderServices.beginHudEffectsFrame();
            try {
                renderOverlay();
            } finally {
                RenderServices.flushHudEffectsFrame();
            }
        }
    }

    private void renderOverlay() {
        if (!isInGame() || mc.currentScreen instanceof GuiMainMenu) {
            return;
        }

        long now = System.currentTimeMillis();
        float factor = animationFactor(now);
        ScaledResolution sr = new ScaledResolution(mc);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        activeStyle = getSelectedStyle();

        if (Boolean.TRUE.equals(watermark.getValue())) {
            drawWatermark();
        }
        if (Boolean.TRUE.equals(arrayList.getValue())) {
            drawModuleList(width, height, factor);
        }
        if (Boolean.TRUE.equals(potionEffects.getValue())) {
            drawPotionEffects(factor);
        }
        if (Boolean.TRUE.equals(inventoryDisplay.getValue())) {
            drawInventory(width, height, factor);
        }
        if (Boolean.TRUE.equals(notifications.getValue())) {
            NotificationManager.doRender(width, height);
        }
//        if (ModuleManager.getModule("Test").state) {
//            FontLoaders.C16.drawString("this is a test font", 0f, 5f, new Color(255,255,255).getRGB());
//        }
    }

    @Override
    public void disable() {
        moduleAnimations.clear();
        clearNightBloomModuleMotions();
        nightBloomPotionAnimations.clear();
        for (int i = 0; i < nightBloomInventorySlotAnimations.length; i++) {
            nightBloomInventorySlotAnimations[i] = 0.0F;
        }
        nightBloomInventoryFill = 0.0F;
        clearModuleEntryCache();
        lastFrameMS = System.currentTimeMillis();
    }

    private void drawWatermark() {
        if (getSelectedStyle() == HudStyle.NIGHT_BLOOM) {
            drawNightBloomWatermark();
            return;
        }
        if (useVapeStyle()) {
            drawVapeWatermark();
            return;
        }

        String capsule = "Yozakura · " + Minecraft.getDebugFPS() + "fps";
        int ping = getPing();
        if (ping >= 0) {
            capsule += " · " + ping + "ms";
        }

        CFontRenderer smallFont = FontLoaders.C14;
        float capsuleW = smallFont.getStringWidth(capsule) + 36.0f;
        float boxW = Math.max(166.0f, capsuleW + 10.0f);
        float panelW = Math.min(boxW, capsuleW);
        float panelH = 19.0f;
        float round = Math.max(6.0f, getRadius() - 1.0f);
        float uiScale = getScale(watermarkScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_watermark", watermarkX, watermarkY, watermarkScale, 6.0f, 6.0f,
                panelW * uiScale, panelH * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                if (useFrostedGlass()) {
                    RenderServices.shapes().shadow(x, y, x + panelW, y + panelH, 7.0f,
                            withAlpha(0xFF000000, 92), 7, 2.2f);
                    RenderServices.shapes().shadow(x, y, x + panelW, y + panelH, 7.0f,
                            withAlpha(SAKURA, 54), 5, 2.0f);
                }
                drawHudLiquidGlass(x, y, x + panelW, y + panelH, 7.0f, 0.55f,
                        withAlpha(SAKURA_GLASS, 166), withAlpha(SAKURA, 48));
                if (useFrostedGlass()) {
                    RenderServices.shapes().roundedBorder(x + 2.0f, y + 1.0f, x + panelW - 2.0f, y + 9.0f,
                            6.0f, 0.0f,
                            withAlpha(0xFFFFF6FA, 20), withAlpha(SAKURA, 4));
                }
            }

            drawWatermarkPetals(x, y, panelW, panelH, 1.0f);
            drawSakuraFlower(x + 10.5f, y + 10.2f, 3.0f, 1.0f);
            smallFont.drawString(trim(capsule, smallFont, panelW - 28.0f),
                    x + 21.0f, y + 8.4f, sakuraText(236));
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_watermark", x, y, panelW * uiScale, panelH * uiScale, round * uiScale);
        HudDrag.handleScroll("hud_watermark", watermarkScale, x, y, panelW * uiScale, panelH * uiScale, 0.65f, 1.8f);
    }

    private void drawVapeWatermark() {
        String fps = "FPS " + Minecraft.getDebugFPS();
        int ping = getPing();
        String meta = fps + "  |  Ping " + (ping >= 0 ? ping : "--");
        CFontRenderer titleFont = FontLoaders.C18;
        CFontRenderer smallFont = FontLoaders.regular(13);
        float iconSize = 38.0f;
        float boxW = Math.max(214.0f, titleFont.getStringWidth(Client.name) + 86.0f);
        float boxH = 52.0f;
        float uiScale = getScale(watermarkScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_watermark", watermarkX, watermarkY, watermarkScale, 8.0f, 8.0f,
                boxW * uiScale, boxH * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                drawVapeCard(x, y, x + boxW, y + boxH, 7.0f, 170);
                RenderServices.shapes().horizontalGradient(x + 1.0f, y + 1.0f, x + boxW - 1.0f, y + 18.0f,
                        withAlpha(0xFFFFFFFF, 16), withAlpha(0xFF000000, 0));
                RenderServices.shapes().rounded(x + 10.0f, y + 7.0f, x + 10.0f + iconSize,
                        y + 7.0f + iconSize, 8.0f, withAlpha(palette().vapeSurfaceVariant, 235));
                RenderServices.shapes().roundedBorder(x + 10.0f, y + 7.0f, x + 10.0f + iconSize,
                        y + 7.0f + iconSize, 8.0f, 0.8f, 0x00000000,
                        withAlpha(0xFFFFFFFF, 24));
                RenderServices.shapes().circle(x + boxW - 16.0f, y + 18.0f, 0, 360, 4.0f,
                        withAlpha(palette().vapeSecondary, 245));
            }
            FontLoaders.C30.drawString("M", x + 18.0f, y + 14.0f, withAlpha(palette().vapePrimary, 245));
            titleFont.drawString(Client.name, x + 60.0f, y + 13.0f, withAlpha(palette().vapeOnSurface, 248));
            smallFont.drawString(meta, x + 60.0f, y + 31.0f, withAlpha(palette().vapeOnVariant, 226));
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_watermark", x, y, boxW * uiScale, boxH * uiScale, 7.0f * uiScale);
        HudDrag.handleScroll("hud_watermark", watermarkScale, x, y, boxW * uiScale, boxH * uiScale, 0.65f, 1.8f);
    }

    private void drawNightBloomWatermark() {
        CFontRenderer brandFont = FontLoaders.TB14;
        CFontRenderer chipFont = FontLoaders.C12;
        String brand = Client.name == null || Client.name.length() == 0 ? "Yozakura" : Client.name;
        String version = "v" + (Client.version == null || Client.version.length() == 0 ? "--" : Client.version);
        int ping = getPing();
        String fps = Minecraft.getDebugFPS() + " FPS" + (ping >= 0 ? "  " + ping + " ms" : "");
        float brandWidth = brandFont.getStringWidth(brand);
        float versionWidth = chipFont.getStringWidth(version);
        float fpsWidth = chipFont.getStringWidth(fps);
        float panelWidth = NightBloomHudLayout.watermarkWidth(brandWidth, versionWidth, fpsWidth);
        float panelHeight = NightBloomHudLayout.WATERMARK_HEIGHT;
        float uiScale = getScale(watermarkScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_watermark", watermarkX, watermarkY, watermarkScale, 6.0F, 6.0F,
                panelWidth * uiScale, panelHeight * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            drawNightBloomPanel(x, y, x + panelWidth, y + panelHeight, NIGHT_BLOOM_RADIUS, 1.0F);

            if (Boolean.TRUE.equals(backgrounds.getValue())) {

                RenderServices.shapes().rounded(x + 4.0F, y + 4.0F, x + 22.0F, y + panelHeight - 4.0F,
                        3.0F, withNightBloomAlpha(NIGHT_BLOOM_SURFACE_RAISED, 0.88F));
            }
            drawNightBloomCenteredIcon(FontLoaders.ICON_SPARK, FontLoaders.I14,
                    x + 13.0F, y + panelHeight * 0.5F,
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 1.0F),
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.78F), 0.62F);
            float brandX = x + 26.0F;
            float brandY = y + (panelHeight - brandFont.getHeight()) * 0.5F + 1.0F;
            drawNightBloomText(brandFont, brand, brandX, brandY,
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 1.0F),
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.70F), 0.48F);

            float chipX = brandX + brandWidth + 6.0F;
            drawNightBloomChip(version, chipFont, chipX, y + 4.0F, versionWidth,
                    NIGHT_BLOOM_SECONDARY);
            chipX += versionWidth + 16.0F;
            drawNightBloomChip(fps, chipFont, chipX, y + 4.0F, fpsWidth,
                    NIGHT_BLOOM_SECONDARY);
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_watermark", x, y, panelWidth * uiScale, panelHeight * uiScale,
                NIGHT_BLOOM_RADIUS * uiScale);
        HudDrag.handleScroll("hud_watermark", watermarkScale, x, y, panelWidth * uiScale, panelHeight * uiScale,
                0.65F, 1.8F);
    }

    private void drawNightBloomChip(String text, CFontRenderer font, float x, float y, float textWidth, int textColor) {
        float width = textWidth + 12.0F;
        if (Boolean.TRUE.equals(backgrounds.getValue())) {
            RenderServices.shapes().rounded(x, y, x + width, y + 14.0F, 3.0F,
                    withNightBloomAlpha(NIGHT_BLOOM_SURFACE_RAISED, 0.82F));
        }
        drawNightBloomText(font, text, x + 6.0F, y + (14.0F - font.getHeight()) * 0.5F + 1.0F,
                withNightBloomAlpha(textColor, 0.94F),
                withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.30F), 0.22F);
    }

    private void drawNightBloomModuleList(int screenWidth, int screenHeight, float factor, List<Module> modules) {
        CFontRenderer nameFont = FontLoaders.TB20;
        CFontRenderer metaFont = FontLoaders.C16;
        List<ModuleListEntry> entries = getSortedNightBloomModuleListEntries(modules, nameFont, metaFont);
        float listWidth = NightBloomHudLayout.MIN_MODULE_ROW_WIDTH;
        int visibleRows = 0;
        for (ModuleListEntry entry : entries) {
            float metaWidth = entry.sideText.length() == 0 ? 0.0F : metaFont.getStringWidth(entry.sideText);
            listWidth = Math.max(listWidth, NightBloomHudLayout.moduleRowWidth(entry.nameWidth, metaWidth));
            visibleRows++;
            if (visibleRows >= NightBloomHudLayout.MAX_VISIBLE_MODULE_ROWS) {
                break;
            }
        }
        for (ModuleListEntry entry : nightBloomModuleEntries.values()) {
            float metaWidth = entry.sideText.length() == 0 ? 0.0F : metaFont.getStringWidth(entry.sideText);
            listWidth = Math.max(listWidth, NightBloomHudLayout.moduleRowWidth(entry.nameWidth, metaWidth));
        }
        visibleRows = Math.max(visibleRows, Math.min(NightBloomHudLayout.MAX_VISIBLE_MODULE_ROWS,
                nightBloomModuleMotions.size()));
        if (modules.isEmpty() && nightBloomModuleMotions.isEmpty() && !HudDrag.isEditMode()) {
            return;
        }

        float listHeight = NightBloomHudLayout.moduleListHeight(visibleRows);
        float uiScale = getScale(moduleListScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_module_list", moduleListX, moduleListY, moduleListScale,
                screenWidth - listWidth * uiScale - 6.0F, 6.0F,
                listWidth * uiScale, listHeight * uiScale, sr);
        float x = pos[0];
        float y = pos[1];
        float right = x + listWidth;

        beginScaled(x, y, uiScale);
        try {
            long gradientTick = System.currentTimeMillis();
            List<NightBloomModuleRenderEntry> rows = updateNightBloomModuleRows(entries, y,
                    nightBloomModuleClock.tick(System.nanoTime()));
            if (rows.isEmpty() && modules.isEmpty()) {
                drawNightBloomPanel(x, y, right, y + NightBloomHudLayout.MODULE_ROW_HEIGHT
                                + NightBloomHudLayout.MODULE_ROW_GAP,
                        NIGHT_BLOOM_RADIUS, 1.0F);
                float textWidth = nameFont.getStringWidth("Module List");
                drawNightBloomText(nameFont, "Module List", right - textWidth - 8.0F,
                        y + (NightBloomHudLayout.MODULE_ROW_HEIGHT - nameFont.getHeight()) * 0.5F + 1.0F,
                        withNightBloomAlpha(NIGHT_BLOOM_SECONDARY, 0.72F),
                        withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.22F), 0.18F);
            } else {
                drawNightBloomModuleShadows(rows, metaFont, right);
                drawNightBloomModuleSurfaces(rows, metaFont, right);
                for (int index = 0; index < rows.size(); index++) {
                    NightBloomModuleRenderEntry row = rows.get(index);
                    ModuleListEntry entry = row.entry;
                    float progress = row.snapshot.getVisibility();
                    float metaWidth = entry.sideText.length() == 0 ? 0.0F : metaFont.getStringWidth(entry.sideText);
                    float rowWidth = NightBloomHudLayout.moduleRowWidth(entry.nameWidth, metaWidth);
                    float rowX = NightBloomHudLayout.moduleRowX(right, rowWidth, progress)
                            + (1.0F - progress) * 5.0F;
                    drawNightBloomModuleRow(entry, nameFont, metaFont, rowX, row.snapshot.getY(), rowWidth,
                            progress, gradientTick, x, y);
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_module_list", x, y, listWidth * uiScale, listHeight * uiScale,
                NightBloomHudLayout.MODULE_ROW_HEIGHT * 0.5F * uiScale);
        HudDrag.handleScroll("hud_module_list", moduleListScale, x, y, listWidth * uiScale, listHeight * uiScale,
                0.65F, 1.8F);
    }

    private List<NightBloomModuleRenderEntry> updateNightBloomModuleRows(List<ModuleListEntry> entries,
                                                                           float firstRowY, float deltaSeconds) {
        nightBloomVisibleModuleScratch.clear();
        float targetY = firstRowY;
        int visibleCount = 0;
        for (ModuleListEntry entry : entries) {
            if (visibleCount >= NightBloomHudLayout.MAX_VISIBLE_MODULE_ROWS) {
                break;
            }
            NightBloomModuleRowMotion motion = nightBloomModuleMotions.get(entry.module);
            if (motion == null) {
                motion = new NightBloomModuleRowMotion();
                nightBloomModuleMotions.put(entry.module, motion);
            }
            nightBloomModuleEntries.put(entry.module, entry);
            nightBloomVisibleModuleScratch.add(entry.module);
            motion.setVisible(true);
            motion.setTargetY(targetY);
            targetY += NightBloomHudLayout.MODULE_ROW_HEIGHT + NightBloomHudLayout.MODULE_ROW_GAP;
            visibleCount++;
        }

        nightBloomModuleRenderScratch.clear();
        Iterator<Map.Entry<Module, NightBloomModuleRowMotion>> iterator = nightBloomModuleMotions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Module, NightBloomModuleRowMotion> state = iterator.next();
            Module module = state.getKey();
            NightBloomModuleRowMotion motion = state.getValue();
            if (!nightBloomVisibleModuleScratch.contains(module)) {
                motion.setVisible(false);
            }
            NightBloomModuleRowMotion.Snapshot snapshot = motion.update(deltaSeconds);
            ModuleListEntry entry = nightBloomModuleEntries.get(module);
            if (entry != null && snapshot.getVisibility() > 0.01F) {
                nightBloomModuleRenderScratch.add(new NightBloomModuleRenderEntry(entry, snapshot));
            } else if (motion.isFinishedExit()) {
                iterator.remove();
                nightBloomModuleEntries.remove(module);
            }
        }
        nightBloomModuleRenderScratch.sort(new Comparator<NightBloomModuleRenderEntry>() {
            @Override
            public int compare(NightBloomModuleRenderEntry first, NightBloomModuleRenderEntry second) {
                return Float.compare(first.snapshot.getY(), second.snapshot.getY());
            }
        });
        return nightBloomModuleRenderScratch;
    }

    private void drawNightBloomModuleShadows(List<NightBloomModuleRenderEntry> rows,
                                              CFontRenderer metaFont, float right) {
        if (!Boolean.TRUE.equals(backgrounds.getValue())) {
            return;
        }
        for (NightBloomModuleRenderEntry row : rows) {
            ModuleListEntry entry = row.entry;
            float progress = row.snapshot.getVisibility();
            float metaWidth = entry.sideText.length() == 0 ? 0.0F : metaFont.getStringWidth(entry.sideText);
            float rowWidth = NightBloomHudLayout.moduleRowWidth(entry.nameWidth, metaWidth);
            float rowX = NightBloomHudLayout.moduleRowX(right, rowWidth, progress)
                    + (1.0F - progress) * 5.0F;
            float bottom = NightBloomHudLayout.moduleRowBottom(row.snapshot.getY(), 0.0F, false);
            drawNightBloomModuleShadow(rowX, row.snapshot.getY(), rowX + rowWidth, bottom,
                    NIGHT_BLOOM_RADIUS, progress);
        }

        int groupStart = 0;
        while (groupStart < rows.size()) {
            int groupEnd = groupStart;
            float groupAlpha = rows.get(groupStart).snapshot.getVisibility();
            while (groupEnd + 1 < rows.size()
                    && NightBloomHudLayout.moduleRowsTouch(rows.get(groupEnd).snapshot.getY(),
                    rows.get(groupEnd + 1).snapshot.getY())) {
                groupEnd++;
                groupAlpha = Math.min(groupAlpha, rows.get(groupEnd).snapshot.getVisibility());
            }
            if (groupEnd > groupStart) {
                float groupTop = rows.get(groupStart).snapshot.getY();
                float groupBottom = rows.get(groupEnd).snapshot.getY()
                        + NightBloomHudLayout.MODULE_ROW_HEIGHT + NightBloomHudLayout.MODULE_ROW_GAP;
                drawNightBloomModuleShadowSpine(right - NIGHT_BLOOM_RADIUS,
                        groupTop + NIGHT_BLOOM_RADIUS, right,
                        groupBottom - NIGHT_BLOOM_RADIUS, groupAlpha);
            }
            groupStart = groupEnd + 1;
        }
    }

    private void drawNightBloomModuleShadowSpine(float x, float y, float x2, float y2, float alpha) {
        drawNightBloomShadow(x, y, x2, y2, 0.0F, alpha);
    }

    private void drawNightBloomModuleSurfaces(List<NightBloomModuleRenderEntry> rows,
                                               CFontRenderer metaFont, float right) {
        if (!Boolean.TRUE.equals(backgrounds.getValue())) {
            return;
        }
        for (int index = 0; index < rows.size(); index++) {
            NightBloomModuleRenderEntry row = rows.get(index);
            ModuleListEntry entry = row.entry;
            float progress = row.snapshot.getVisibility();
            float metaWidth = entry.sideText.length() == 0 ? 0.0F : metaFont.getStringWidth(entry.sideText);
            float rowWidth = NightBloomHudLayout.moduleRowWidth(entry.nameWidth, metaWidth);
            float rowX = NightBloomHudLayout.moduleRowX(right, rowWidth, progress)
                    + (1.0F - progress) * 5.0F;
            float bottom = NightBloomHudLayout.moduleRowBottom(row.snapshot.getY(), 0.0F, false);
            boolean joinsAbove = index > 0
                    && NightBloomHudLayout.moduleRowsTouch(rows.get(index - 1).snapshot.getY(),
                    row.snapshot.getY());
            boolean joinsBelow = index + 1 < rows.size()
                    && NightBloomHudLayout.moduleRowsTouch(row.snapshot.getY(),
                    rows.get(index + 1).snapshot.getY());

            float topJoinStart = 1.0F;
            float topJoinEnd = 0.0F;
            if (joinsAbove) {
                NightBloomModuleRenderEntry above = rows.get(index - 1);
                float aboveWidth = nightBloomRenderedModuleRowWidth(above.entry, metaFont);
                float aboveX = NightBloomHudLayout.moduleRowX(right, aboveWidth,
                        above.snapshot.getVisibility())
                        + (1.0F - above.snapshot.getVisibility()) * 5.0F;
                topJoinStart = NightBloomHudLayout.moduleJoinStart(
                        rowX, aboveX, NIGHT_BLOOM_RADIUS) - rowX;
                topJoinEnd = NightBloomHudLayout.moduleJoinEnd(
                        rowX + rowWidth, aboveX + aboveWidth) - rowX;
                joinsAbove = NightBloomHudLayout.moduleJoinRangeValid(topJoinStart, topJoinEnd);
            }

            float bottomJoinStart = 1.0F;
            float bottomJoinEnd = 0.0F;
            if (joinsBelow) {
                NightBloomModuleRenderEntry next = rows.get(index + 1);
                float nextWidth = nightBloomRenderedModuleRowWidth(next.entry, metaFont);
                float nextX = NightBloomHudLayout.moduleRowX(right, nextWidth,
                        next.snapshot.getVisibility())
                        + (1.0F - next.snapshot.getVisibility()) * 5.0F;
                bottomJoinStart = NightBloomHudLayout.moduleJoinStart(
                        rowX, nextX, NIGHT_BLOOM_RADIUS) - rowX;
                bottomJoinEnd = NightBloomHudLayout.moduleJoinEnd(
                        rowX + rowWidth, nextX + nextWidth) - rowX;
                joinsBelow = NightBloomHudLayout.moduleJoinRangeValid(bottomJoinStart, bottomJoinEnd);
                if (joinsBelow) {
                    bottom = NightBloomHudLayout.moduleRowBottom(row.snapshot.getY(),
                            rows.get(index + 1).snapshot.getY(), true);
                }
            }
            drawNightBloomModuleSurface(rowX, row.snapshot.getY(), rowX + rowWidth, bottom,
                    NIGHT_BLOOM_RADIUS, progress,
                    joinsAbove ? topJoinStart : 1.0F, joinsAbove ? topJoinEnd : 0.0F,
                    joinsBelow ? bottomJoinStart : 1.0F, joinsBelow ? bottomJoinEnd : 0.0F);
        }
    }

    private void drawNightBloomModuleSurface(float x, float y, float x2, float y2, float radius,
                                              float alpha, float topJoinStart, float topJoinEnd,
                                              float bottomJoinStart, float bottomJoinEnd) {
        if (alpha <= 0.0F) {
            return;
        }
        boolean joinsAbove = NightBloomHudLayout.moduleJoinRangeValid(topJoinStart, topJoinEnd);
        boolean joinsBelow = NightBloomHudLayout.moduleJoinRangeValid(bottomJoinStart, bottomJoinEnd);
        float rowWidth = x2 - x;
        float topRightRadius = joinsAbove
                && NightBloomHudLayout.moduleJoinReachesRight(rowWidth, topJoinEnd) ? 0.0F : radius;
        float bottomRightRadius = joinsBelow
                && NightBloomHudLayout.moduleJoinReachesRight(rowWidth, bottomJoinEnd) ? 0.0F : radius;
        RenderServices.shapes().joinedRounded(x, y, x2, y2,
                radius, topRightRadius, bottomRightRadius, radius,
                topJoinStart, topJoinEnd, bottomJoinStart, bottomJoinEnd,
                withNightBloomAlpha(withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.68F), alpha));
    }

    private void drawNightBloomModuleRow(ModuleListEntry entry, CFontRenderer nameFont, CFontRenderer metaFont,
                                          float x, float y, float width, float progress, long gradientTick,
                                          float gradientOriginX, float gradientOriginY) {
        float rowRight = x + width;
        String meta = entry.sideText;
        float nameY = y + (NightBloomHudLayout.MODULE_ROW_HEIGHT - nameFont.getHeight()) * 0.5F + 1.0F;
        float metaY = y + (NightBloomHudLayout.MODULE_ROW_HEIGHT - metaFont.getHeight()) * 0.5F + 1.0F;
        float contentRight = rowRight - 3.0F;
        float metaWidth = meta.length() == 0 ? 0.0F : metaFont.getStringWidth(meta);
        float nameX = contentRight - entry.nameWidth;
        if (metaWidth > 0.0F) {
            float metaX = contentRight - metaWidth;
            drawNightBloomText(metaFont, meta, metaX, metaY,
                    withNightBloomAlpha(NIGHT_BLOOM_SECONDARY, 0.90F * progress),
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.24F * progress), 0.20F);
            nameX = metaX - 3.0F - entry.nameWidth;
        }
        drawNightBloomArrayListGradientText(nameFont, entry.label.name, nameX, nameY,
                0.98F * progress, 0.68F * progress, 0.52F, gradientTick,
                gradientOriginX, gradientOriginY);
    }

    private void drawNightBloomModuleShadow(float x, float y, float x2, float y2, float radius, float alpha) {
        drawNightBloomShadow(x, y, x2, y2, radius, alpha);
    }

    private static void drawNightBloomArrayListGradientText(CFontRenderer font, String text, float x, float y,
                                                            float opacity, float glowOpacity, float glowStrength,
                                                            long gradientTick, float gradientOriginX,
                                                            float gradientOriginY) {
        if (font == null || text == null || text.length() == 0) {
            return;
        }
        float cursor = x;
        for (int index = 0; index < text.length(); index++) {
            String glyph = String.valueOf(text.charAt(index));
            int color = NightBloomArrayListGradient.colorAt(cursor - gradientOriginX,
                    y - gradientOriginY, gradientTick);
            int glyphColor = withNightBloomAlpha(color, opacity);
            int gradientGlow = withNightBloomAlpha(color, glowOpacity);
            drawNightBloomText(font, glyph, cursor, y, glyphColor, gradientGlow, glowStrength);
            cursor += font.getStringWidth(glyph);
        }
    }

    private void drawNightBloomPanel(float x, float y, float x2, float y2, float radius, float alpha) {
        if (!Boolean.TRUE.equals(backgrounds.getValue()) || alpha <= 0.0F) {
            return;
        }
        drawNightBloomShadow(x, y, x2, y2, radius, alpha);
        RenderServices.shapes().rounded(x, y, x2, y2, radius,
                withNightBloomAlpha(NIGHT_BLOOM_SURFACE, 0.86F * alpha));
        RenderServices.shapes().horizontalGradient(x + radius, y + 1.0F, x2 - radius,
                Math.min(y2 - 1.0F, y + 6.0F),
                withNightBloomAlpha(NIGHT_BLOOM_SURFACE_RAISED, 0.28F * alpha), 0x00000000);
    }

    private static int withNightBloomAlpha(int color, float alpha) {
        int sourceAlpha = color >>> 24 & 255;
        int resolvedAlpha = Math.round(sourceAlpha * Math.max(0.0F, Math.min(1.0F, alpha)));
        return withAlpha(color, resolvedAlpha);
    }

    private void drawVapeTextChip(String text, float x, float y, float width, int accent) {
        RenderServices.shapes().rounded(x, y, x + width, y + 15.0f, 4.0f, withAlpha(palette().vapeSurfaceVariant, 185));
        RenderServices.shapes().rounded(x, y + 13.0f, x + width, y + 15.0f, 1.0f, withAlpha(accent, 165));
        FontLoaders.C12.drawString(text, x + (width - FontLoaders.C12.getStringWidth(text)) / 2.0f,
                y + 4.0f, withAlpha(palette().vapeOnSurface, 232));
    }

    private void drawVapePotionEffects(ArrayList<PotionEffect> effects) {
        float rowH = 19.0f;
        int maxRows = Math.min(6, Math.max(1, effects.size()));
        float width = 174.0f;
        float height = 28.0f + maxRows * rowH + 6.0f;
        float uiScale = getScale(potionScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_potions", potionX, potionY, potionScale, 8.0f,
                Boolean.TRUE.equals(watermark.getValue()) ? 66.0f : 8.0f,
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                drawVapeCard(x, y, x + width, y + height, 6.0f, 158);
                RenderServices.shapes().rounded(x, y, x + 2.0f, y + height, 1.0f,
                        withAlpha(palette().vapeTertiary, 188));
            }
            FontLoaders.C16.drawString("Effects", x + 11.0f, y + 8.0f, withAlpha(palette().vapeOnSurface, 242));
            String count = String.valueOf(effects.size());
            drawVapeTextChip(count, x + width - FontLoaders.C12.getStringWidth(count) - 19.0f, y + 7.0f,
                    FontLoaders.C12.getStringWidth(count) + 14.0f, palette().vapePrimary);
            if (effects.isEmpty()) {
                FontLoaders.C12.drawString("No active effects", x + 12.0f, y + 34.0f,
                        withAlpha(palette().vapeOnVariant, 210));
            } else {
                for (int i = 0; i < Math.min(6, effects.size()); i++) {
                    PotionEffect effect = effects.get(i);
                    Potion potion = Potion.potionTypes[effect.getPotionID()];
                    if (potion == null) {
                        continue;
                    }
                    float rowY = y + 29.0f + i * rowH;
                    int accent = withAlpha(0xFF000000 | potion.getLiquidColor(), 220);
                    String duration = Potion.getDurationString(effect);
                    String name = trim(I18n.format(potion.getName()) + amplifierSuffix(effect.getAmplifier()),
                            FontLoaders.C12, width - FontLoaders.C12.getStringWidth(duration) - 44.0f);
                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        RenderServices.shapes().rounded(x + 8.0f, rowY + 1.0f, x + width - 8.0f,
                                rowY + rowH - 2.0f, 4.0f, withAlpha(palette().vapeSurfaceVariant, 112));
                    }
                    RenderServices.shapes().circle(x + 17.0f, rowY + 8.0f, 0, 360, 3.4f, accent);
                    FontLoaders.C12.drawString(name, x + 26.0f, rowY + 4.0f, withAlpha(palette().vapeOnSurface, 230));
                    FontLoaders.C12.drawString(duration, x + width - FontLoaders.C12.getStringWidth(duration) - 12.0f,
                            rowY + 4.0f, withAlpha(palette().vapeOnVariant, 215));
                    float progress = Math.max(0.08f, Math.min(1.0f, effect.getDuration() / 1200.0f));
                    RenderServices.shapes().progressBar(x + 26.0f, rowY + 14.0f, x + width - 12.0f, rowY + 15.6f,
                            0.8f, progress, withAlpha(0xFFFFFFFF, 18), withAlpha(accent, 185));
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_potions", x, y, width * uiScale, height * uiScale, 6.0f * uiScale);
        HudDrag.handleScroll("hud_potions", potionScale, x, y, width * uiScale, height * uiScale, 0.65f, 1.8f);
    }

    private void drawNightBloomPotionEffects(ArrayList<PotionEffect> effects, float factor) {
        float width = NightBloomHudLayout.POTION_WIDTH;
        float height = NightBloomHudLayout.potionHeight(effects.size());
        float uiScale = getScale(potionScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_potions", potionX, potionY, potionScale, 6.0F,
                Boolean.TRUE.equals(watermark.getValue()) ? 54.0F : 6.0F,
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            drawNightBloomPanel(x, y, x + width, y + height, NIGHT_BLOOM_RADIUS, 1.0F);
            if (effects.isEmpty()) {
                float rowY = y + (height - 18.0F) * 0.5F;
                drawNightBloomCenteredIcon(FontLoaders.ICON_SPARK, FontLoaders.I14,
                        x + 15.0F, rowY + 9.0F,
                        withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.90F),
                        withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.62F), 0.56F);
                drawNightBloomText(FontLoaders.C14, "No active effects", x + 29.0F, rowY + 5.0F,
                        withNightBloomAlpha(NIGHT_BLOOM_SECONDARY, 0.72F),
                        withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.22F), 0.18F);
            } else {
                for (int i = 0; i < Math.min(NightBloomHudLayout.MAX_VISIBLE_POTION_ROWS, effects.size()); i++) {
                    PotionEffect effect = effects.get(i);
                    Potion potion = Potion.potionTypes[effect.getPotionID()];
                    if (potion == null) {
                        continue;
                    }

                    float visibility = animateNightBloomPotion(effect, factor);
                    float rowY = y + 7.0F + i * NightBloomHudLayout.POTION_ROW_HEIGHT
                            + (1.0F - visibility) * 4.0F;
                    int accent = nightBloomPotionAccent(potion);
                    String duration = Potion.getDurationString(effect);
                    float durationWidth = FontLoaders.C12.getStringWidth(duration);
                    float durationX = x + width - durationWidth - 9.0F;
                    String name = trim(I18n.format(potion.getName()) + amplifierSuffix(effect.getAmplifier()),
                            FontLoaders.C14, durationX - (x + 30.0F) - 7.0F);

                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        RenderServices.shapes().rounded(x + 6.0F, rowY, x + width - 6.0F,
                                rowY + 19.0F, NIGHT_BLOOM_RADIUS,
                                withNightBloomAlpha(NIGHT_BLOOM_SURFACE_RAISED, 0.36F * visibility));
                        RenderServices.shapes().rounded(x + 8.0F, rowY + 2.0F, x + 22.0F,
                                rowY + 16.0F, 3.0F,
                                withNightBloomAlpha(NIGHT_BLOOM_SURFACE_RAISED, 0.78F * visibility));
                    }

                    drawNightBloomCenteredIcon(nightBloomPotionIcon(potion), FontLoaders.I14,
                            x + 15.0F, rowY + 9.0F,
                            withNightBloomAlpha(accent, 0.96F * visibility),
                            withNightBloomAlpha(accent, 0.62F * visibility), 0.58F);
                    drawNightBloomText(FontLoaders.C14, name, x + 30.0F, rowY + 5.0F,
                            withNightBloomAlpha(NIGHT_BLOOM_PALETTE.getTextPrimary(), 0.98F * visibility),
                            withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.44F * visibility), 0.34F);
                    drawNightBloomText(FontLoaders.C12, duration, durationX, rowY + 6.0F,
                            withNightBloomAlpha(nightBloomPotionDurationColor(effect), 0.96F * visibility),
                            withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.20F * visibility), 0.17F);
                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        float remaining = Math.max(0.08F, Math.min(1.0F, effect.getDuration() / 1200.0F));
                        RenderServices.shapes().progressBar(x + 30.0F, rowY + 17.0F, x + width - 9.0F,
                                rowY + 18.2F, 0.6F, remaining,
                                withNightBloomAlpha(NIGHT_BLOOM_PALETTE.getSurfaceOverlay(), 0.50F * visibility),
                                withNightBloomAlpha(accent, 0.70F * visibility));
                    }
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_potions", x, y, width * uiScale, height * uiScale, getRadius() * uiScale);
        HudDrag.handleScroll("hud_potions", potionScale, x, y, width * uiScale, height * uiScale, 0.65F, 1.8F);
    }

    private float animateNightBloomPotion(PotionEffect effect, float factor) {
        int key = effect.getPotionID() * 31 + effect.getAmplifier();
        Float current = nightBloomPotionAnimations.get(key);
        float previous = current == null ? 0.0F : current.floatValue();
        float response = Math.max(0.0F, Math.min(1.0F, factor));
        float next = previous + (1.0F - previous) * response;
        nightBloomPotionAnimations.put(key, next);
        return next;
    }

    private int nightBloomPotionAccent(Potion potion) {
        return potion.isBadEffect() ? NIGHT_BLOOM_PALETTE.getDanger() : NIGHT_BLOOM_PRIMARY;
    }

    private String nightBloomPotionIcon(Potion potion) {
        return potion.isBadEffect() ? FontLoaders.ICON_WARNING : FontLoaders.ICON_SHIELD;
    }

    private int nightBloomPotionDurationColor(PotionEffect effect) {
        return effect.getDuration() <= 20 * 15
                ? NIGHT_BLOOM_PALETTE.getWarning()
                : NIGHT_BLOOM_SECONDARY;
    }

    private void drawNightBloomInventory(int screenWidth, int screenHeight, float factor) {
        float slot = NightBloomHudLayout.INVENTORY_SLOT_SIZE;
        float stride = NightBloomHudLayout.INVENTORY_SLOT_STRIDE;
        float width = NightBloomHudLayout.INVENTORY_WIDTH;
        float height = NightBloomHudLayout.INVENTORY_HEIGHT;
        float uiScale = getScale(inventoryScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_inventory", inventoryX, inventoryY, inventoryScale,
                screenWidth / 2.0F - width * uiScale / 2.0F, Math.max(58.0F, screenHeight - 114.0F),
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1];
        int filled = countInventoryItems();
        float response = Math.max(0.0F, Math.min(1.0F, factor));
        nightBloomInventoryFill += (filled / 27.0F - nightBloomInventoryFill) * response;

        beginScaled(x, y, uiScale);
        try {
            float panelRadius = getRadius();
            drawNightBloomPanel(x, y, x + width, y + height,
                    Math.min(panelRadius, NIGHT_BLOOM_RADIUS), 1.0F);

            drawNightBloomText(FontLoaders.C16, "Inventory", x + 10.0F, y + 10.0F,
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.98F),
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.62F), 0.44F);
            String count = filled + "/27";
            float countWidth = FontLoaders.C14.getStringWidth(count);
            float countX = x + width - countWidth - 11.0F;
            drawNightBloomCenteredIcon(FontLoaders.ICON_CUBE, FontLoaders.I14,
                    countX - 8.0F, y + 14.0F,
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.94F),
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.58F), 0.54F);
            drawNightBloomText(FontLoaders.C14, count, countX, y + 10.0F,
                    withNightBloomAlpha(NIGHT_BLOOM_SECONDARY, 0.98F),
                    withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.24F), 0.20F);

            float startX = x + 8.0F;
            float startY = y + 28.0F;
            float gridRight = x + NightBloomHudLayout.inventoryGridRight(8.0F);
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                RenderServices.shapes().progressBar(startX, y + 23.0F, gridRight, y + 24.2F, 0.6F,
                        nightBloomInventoryFill,
                        withNightBloomAlpha(NIGHT_BLOOM_PALETTE.getSurfaceOverlay(), 0.56F),
                        withNightBloomAlpha(NIGHT_BLOOM_PRIMARY, 0.78F));
            }

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < NightBloomHudLayout.INVENTORY_COLUMNS; col++) {
                    int itemIndex = 9 + row * NightBloomHudLayout.INVENTORY_COLUMNS + col;
                    int animationIndex = row * NightBloomHudLayout.INVENTORY_COLUMNS + col;
                    ItemStack stack = mc.thePlayer.inventory.mainInventory[itemIndex];
                    float feedback = animateNightBloomInventorySlot(animationIndex, stack != null, response);
                    float slotX = startX + col * stride;
                    float slotY = startY + row * stride;

                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        int slotFill = stack == null ? NIGHT_BLOOM_SURFACE_RAISED : NIGHT_BLOOM_PRIMARY;
                        float slotAlpha = stack == null ? 0.52F : 0.18F + feedback * 0.10F;
                        RenderServices.shapes().rounded(slotX - 1.0F, slotY - 1.0F,
                                slotX + slot + 1.0F, slotY + slot + 1.0F, 3.0F,
                                withNightBloomAlpha(slotFill, slotAlpha));
                    }
                    drawItemStack(stack, slotX, slotY);
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_inventory", x, y, width * uiScale, height * uiScale, getRadius() * uiScale);
        HudDrag.handleScroll("hud_inventory", inventoryScale, x, y, width * uiScale, height * uiScale, 0.65F, 1.8F);
    }

    private float animateNightBloomInventorySlot(int index, boolean occupied, float response) {
        float target = occupied ? 1.0F : 0.0F;
        float previous = nightBloomInventorySlotAnimations[index];
        float next = previous + (target - previous) * response;
        nightBloomInventorySlotAnimations[index] = next;
        return next;
    }

    private void drawInventory(int screenWidth, int screenHeight, float factor) {
        if (getSelectedStyle() == HudStyle.NIGHT_BLOOM) {
            drawNightBloomInventory(screenWidth, screenHeight, factor);
            return;
        }
        if (useVapeStyle()) {
            drawVapeInventory(screenWidth, screenHeight);
            return;
        }

        float slot = 16.0f;
        float stride = 18.0f;
        float width = 178.0f;
        float height = 88.0f;
        float uiScale = getScale(inventoryScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_inventory", inventoryX, inventoryY, inventoryScale,
                screenWidth / 2.0f - width * uiScale / 2.0f, Math.max(58.0f, screenHeight - 114.0f),
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            drawSakuraPanel(x, y, x + width, y + height, getRadius(), 1.0f);
            FontLoaders.C16.drawString("Inventory", x + 10.0f, y + 10.0f, sakuraText(236));

            int filled = countInventoryItems();
            String count = filled + "/27";
            float countW = FontLoaders.C14.getStringWidth(count);
            float chipX = x + width - countW - 27.0f;
            drawSakuraStatusChip(count, chipX, y + 8.0f, countW + 18.0f, SAKURA);
            drawSakuraFlower(chipX + 7.5f, y + 15.0f, 4.0f, 1.0f);

            float startX = x + 8.0f;
            float startY = y + 28.0f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = 9 + row * 9 + col;
                    float slotX = startX + col * stride;
                    float slotY = startY + row * stride;
                    RenderServices.shapes().rounded(slotX - 1.0f, slotY - 1.0f, slotX + slot + 1.0f, slotY + slot + 1.0f,
                            4.0f, withAlpha(0xFF160F15, 148));
                    RenderServices.shapes().roundedBorder(slotX - 1.0f, slotY - 1.0f, slotX + slot + 1.0f,
                            slotY + slot + 1.0f, 4.0f, 0.7f, 0x00000000,
                            withAlpha(SAKURA, 28));
                    drawItemStack(mc.thePlayer.inventory.mainInventory[index], slotX, slotY);
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_inventory", x, y, width * uiScale, height * uiScale, getRadius() * uiScale);
        HudDrag.handleScroll("hud_inventory", inventoryScale, x, y, width * uiScale, height * uiScale, 0.65f, 1.8f);
    }

    private void drawVapeInventory(int screenWidth, int screenHeight) {
        float slot = 16.0f;
        float stride = 18.0f;
        float width = 190.0f;
        float height = 90.0f;
        float uiScale = getScale(inventoryScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_inventory", inventoryX, inventoryY, inventoryScale,
                screenWidth / 2.0f - width * uiScale / 2.0f, Math.max(58.0f, screenHeight - 112.0f),
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            int filled = countInventoryItems();
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                drawVapeCard(x, y, x + width, y + height, 6.0f, 158);
                RenderServices.shapes().rounded(x, y, x + 2.0f, y + height, 1.0f,
                        withAlpha(palette().vapeSecondary, 190));
            }
            FontLoaders.C16.drawString("Inventory", x + 11.0f, y + 8.0f, withAlpha(palette().vapeOnSurface, 242));
            String count = filled + "/27";
            drawVapeTextChip(count, x + width - FontLoaders.C12.getStringWidth(count) - 21.0f, y + 7.0f,
                    FontLoaders.C12.getStringWidth(count) + 14.0f, palette().vapeSecondary);

            float startX = x + 13.0f;
            float startY = y + 30.0f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = 9 + row * 9 + col;
                    float slotX = startX + col * stride;
                    float slotY = startY + row * stride;
                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        int fill = ((row + col) & 1) == 0
                                ? withAlpha(palette().vapeSurfaceVariant, 154)
                                : withAlpha(0xFF151922, 144);
                        RenderServices.shapes().roundedBorder(slotX - 1.0f, slotY - 1.0f, slotX + slot + 1.0f,
                                slotY + slot + 1.0f, 4.0f, 0.6f, fill, withAlpha(0xFFFFFFFF, 24));
                    }
                    drawItemStack(mc.thePlayer.inventory.mainInventory[index], slotX, slotY);
                }
            }
            float progress = Math.min(1.0f, filled / 27.0f);
            RenderServices.shapes().progressBar(x + 13.0f, y + height - 7.0f, x + width - 13.0f, y + height - 4.8f,
                    1.1f, progress, withAlpha(0xFFFFFFFF, 20), withAlpha(palette().vapePrimary, 218));
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_inventory", x, y, width * uiScale, height * uiScale, 6.0f * uiScale);
        HudDrag.handleScroll("hud_inventory", inventoryScale, x, y, width * uiScale, height * uiScale, 0.65f, 1.8f);
    }

    private void drawModuleList(int screenWidth, int screenHeight, float factor) {
        List<Module> modules = getHudModules();
        moduleAnimations.keySet().retainAll(modules);
        if (getSelectedStyle() == HudStyle.OLD) {
            clearNightBloomModuleMotions();
            drawVapeModuleList(screenWidth, screenHeight, factor, modules);
            return;
        }
        if (getSelectedStyle() == HudStyle.NIGHT_BLOOM) {
            drawNightBloomModuleList(screenWidth, screenHeight, factor, modules);
            return;
        }
        clearNightBloomModuleMotions();

        final boolean sakura = arrayListTheme.getValue() == ArrayListTheme.SAKURA;
        final CFontRenderer font = sakura ? FontLoaders.C14 : FontLoaders.C18;
        final float fontSizeGap = sakura ? 3.0f : 4.0f;
        List<ModuleListEntry> entries = getSortedModuleListEntries(modules, font, fontSizeGap);

        final float iconSlotW = sakura ? 18.0f : 22.0f;
        final float rightPad = sakura ? 14.0f : 17.0f;
        float listW = 88.0f;
        int visibleRows = 0;
        for (ModuleListEntry entry : entries) {
            listW = Math.max(listW, entry.labelWidth + iconSlotW + rightPad);
            visibleRows++;
            if (visibleRows > 22) {
                break;
            }
        }
        if (modules.isEmpty() && !HudDrag.isEditMode()) {
            return;
        }
        final float rowH = sakura ? 20.0f : 18.0f;
        final float rowGap = sakura ? 2.0f : 3.0f;
        float listH = modules.isEmpty() ? 20.0f : Math.min(23, Math.max(1, visibleRows)) * (rowH + rowGap) - rowGap;
        float uiScale = getScale(moduleListScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_module_list", moduleListX, moduleListY, moduleListScale,
                screenWidth - listW * uiScale - 6.0f, 6.0f, listW * uiScale, listH * uiScale, sr);
        float y = pos[1];
        float right = pos[0] + listW;
        float round = getRadius();
        int index = 0;
        if (modules.isEmpty()) {
            beginScaled(pos[0], y, uiScale);
            try {
                if (sakura) {
                    drawSakuraPanel(pos[0], y, pos[0] + listW, y + listH, round, 0.85f);
                    drawTextGlow(FontLoaders.C14, "Module List", pos[0] + 12.0f, y + 6.0f, 0.62f);
                    FontLoaders.C14.drawString("Module List", pos[0] + 12.0f, y + 6.0f,
                            sakuraMuted(210));
                } else {
                    drawGlass(pos[0], y, pos[0] + listW, y + listH, round, getGlassAlpha(), 42);
                    FontLoaders.C14.drawString("Module List", pos[0] + 10.0f, y + 7.0f, withAlpha(palette().muted, 220));
                }
            } finally {
                endScaled();
            }
            HudDrag.drawHint("hud_module_list", pos[0], y, listW * uiScale, listH * uiScale, round * uiScale);
            HudDrag.handleScroll("hud_module_list", moduleListScale, pos[0], y, listW * uiScale, listH * uiScale, 0.65f, 1.8f);
            return;
        }
        beginScaled(pos[0], y, uiScale);
        try {
            for (ModuleListEntry entry : entries) {
                Module module = entry.module;
                ModuleListLabel label = entry.label;
                float progress = animateModule(module, factor);
                if (progress <= 0.01f) {
                    continue;
                }

                int textW = entry.labelWidth;
                String icon = ClickGuiIcons.forModule(module);
                float rowW = textW + iconSlotW + rightPad;
                float x = right - rowW - (1.0f - progress) * 8.0f;
                int accent = sakura ? palette().accent : getCategoryAccent(module);

                if (sakura) {
                    drawSakuraModuleRow(module, icon, label, x, y, right, rowH,
                            progress, accent, font, iconSlotW);
                } else if (Boolean.TRUE.equals(backgrounds.getValue())) {
                    int rowAlpha = Math.round(getGlassAlpha() * progress);
                    RenderServices.shapes().shadow(x, y, right, y + rowH, round,
                            withAlpha(palette().shadowColor, Math.round(34.0f * progress)), 4, 2.4f);
                    drawHudFrostedGlass(x, y, right, y + rowH, round, 0.8f,
                            withAlpha(palette().glass, rowAlpha), withAlpha(palette().border, Math.round(42.0f * progress)));
                    RenderServices.shapes().verticalGradient(right - 3.0f, y + 3.0f, right - 1.4f, y + rowH - 3.0f,
                            withAlpha(accent, Math.round(205.0f * progress)),
                            withAlpha(ColorUtils.lighten(accent, 0.16f), Math.round(165.0f * progress)));
                    drawCenteredIcon(icon, FontLoaders.I16, x + iconSlotW / 2.0f + 2.0f, y + rowH / 2.0f,
                            withAlpha(accent, Math.round(214.0f * progress)));
                    drawModuleLabel(label, FontLoaders.C18, x + iconSlotW + 6.0f, y + 5.0f,
                            withAlpha(palette().text, Math.round(242.0f * progress)),
                            withAlpha(palette().muted, Math.round(216.0f * progress)), 4.0f);
                } else {
                    FontLoaders.C18.drawString(entry.fullText, right - textW, y + 4.0f,
                            withAlpha(accent, Math.round(245.0f * progress)));
                }

                y += rowH + rowGap;
                index++;
                if (index > 22) {
                    break;
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_module_list", pos[0], pos[1], listW * uiScale, listH * uiScale, round * uiScale);
        HudDrag.handleScroll("hud_module_list", moduleListScale, pos[0], pos[1], listW * uiScale, listH * uiScale, 0.65f, 1.8f);
    }

    private void drawSakuraModuleRow(Module module, String icon, ModuleListLabel label,
                                      float x, float y, float right, float rowH,
                                      float progress, int accent, CFontRenderer font, float iconSlotW) {
        boolean enabled = module.getState();
        float glassAlpha = getGlassAlpha() * 0.01f;

        if (useFrostedGlass()) {
            // Double shadow (black + sakura glow)
            RenderServices.shapes().shadow(x, y, right, y + rowH, 4.0f,
                    withAlpha(0xFF000000, Math.round(48.0f * progress)), 5, 2.0f);
            RenderServices.shapes().shadow(x, y, right, y + rowH, 4.0f,
                    withAlpha(SAKURA, Math.round(22.0f * progress)), 4, 1.6f);
        }

        // Dark glass background with sakura border
        drawHudLiquidGlass(x, y, right, y + rowH, 4.0f, 0.50f,
                withAlpha(SAKURA_GLASS, Math.round(152.0f * glassAlpha * progress)),
                withAlpha(SAKURA, Math.round(28.0f * progress)));

        // Right-edge sakura accent bar
        float barAlpha = enabled ? 0.78f : 0.35f;
        RenderServices.shapes().verticalGradient(right - 2.5f, y + 3.5f, right - 1.0f, y + rowH - 3.5f,
                withAlpha(accent, Math.round(190.0f * barAlpha * progress)),
                withAlpha(palette().accentAlt, Math.round(140.0f * barAlpha * progress)));

        // Icon with sakura tint
        float iconX = x + iconSlotW / 2.0f + 2.0f;
        float iconY = y + rowH / 2.0f;
        int iconColor = enabled ? withAlpha(accent, Math.round(228.0f * progress))
                : withAlpha(palette().muted, Math.round(155.0f * progress));
        drawCenteredIcon(icon, FontLoaders.I16, iconX, iconY, iconColor);

        // Module name with glow
        float textX = x + iconSlotW + 2.0f;
        float textY = y + (rowH - font.getHeight()) / 2.0f + 1.0f;
        int nameColor = enabled ? sakuraText(Math.round(242.0f * progress))
                : sakuraMuted(Math.round(200.0f * progress));
        if (enabled) {
            drawTextGlow(font, label.name, textX, textY, progress * 0.55f);
        }
        font.drawString(label.name, textX, textY, nameColor);

        // Parameter and key in muted sakura
        float cursor = textX + font.getStringWidth(label.name);
        if (label.parameter.length() > 0) {
            cursor += 3.0f;
            font.drawString(label.parameter, cursor, textY,
                    sakuraMuted(Math.round(170.0f * progress)));
            cursor += font.getStringWidth(label.parameter);
        }
        if (label.key.length() > 0) {
            cursor += 3.0f;
            font.drawString(label.key, cursor, textY,
                    sakuraMuted(Math.round(148.0f * progress)));
        }

        // Toggle indicator dot
        if (enabled) {
            float dotX = right - 8.0f;
            float dotY = y + rowH / 2.0f;
            RenderServices.shapes().shadow(dotX - 2.5f, dotY - 2.5f, dotX + 2.5f, dotY + 2.5f,
                    2.5f, withAlpha(SAKURA, Math.round(42.0f * progress)), 3, 1.2f);
            RenderServices.shapes().circle(dotX, dotY, 0, 360, 2.2f,
                    withAlpha(0xFFFFF3FA, Math.round(235.0f * progress)));
        }
    }

    private void drawVapeModuleList(int screenWidth, int screenHeight, float factor, List<Module> modules) {
        final CFontRenderer font = FontLoaders.TB14;
        final float rowH = 24.0f;
        final float lineW = 2.4f;
        List<ModuleListEntry> entries = getSortedModuleListEntries(modules, font, 3.0f);

        int visibleRows = 0;
        float listW = 134.0f;
        for (ModuleListEntry entry : entries) {
            listW = Math.max(listW, entry.nameWidth + entry.sideWidth + 34.0f + lineW);
            visibleRows++;
            if (visibleRows >= 12) {
                break;
            }
        }
        if (modules.isEmpty() && !HudDrag.isEditMode()) {
            return;
        }

        int rows = Math.min(12, Math.max(1, visibleRows));
        float listH = modules.isEmpty() ? rowH : rows * rowH;
        float uiScale = getScale(moduleListScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_module_list", moduleListX, moduleListY, moduleListScale,
                8.0f, 8.0f, listW * uiScale, listH * uiScale, sr);
        float x = pos[0];
        float y = pos[1];
        boolean rightSide = x + (listW * uiScale) / 2.0f >= screenWidth / 2.0f;

        beginScaled(x, y, uiScale);
        try {
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                drawVapeCard(x, y, x + listW, y + listH, 6.0f, 150);
                float lineX = rightSide ? x + listW - lineW : x;
                RenderServices.shapes().verticalGradient(lineX, y + 5.0f, lineX + lineW, y + listH - 5.0f,
                        withAlpha(palette().vapePrimary, 230), withAlpha(palette().vapeTertiary, 190));
            }
            if (modules.isEmpty()) {
                float textX = rightSide
                        ? x + listW - lineW - font.getStringWidth("Module List") - 12.0f
                        : x + lineW + 12.0f;
                font.drawString("Module List", textX, y + 7.0f, withAlpha(palette().vapeOnVariant, 225));
            } else {
                int index = 0;
                float drawY = y;
                for (ModuleListEntry entry : entries) {
                    if (index >= 12) {
                        break;
                    }
                    Module module = entry.module;
                    ModuleListLabel label = entry.label;
                    float progress = animateModule(module, factor);
                    if (progress <= 0.01f) {
                        continue;
                    }
                    float rowTop = drawY;
                    float rowBottom = drawY + rowH;
                    int rowAlpha = Math.round((index == 0 ? 44.0f : 26.0f) * progress);
                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        RenderServices.shapes().rect(x + 4.0f, rowTop, x + listW - 4.0f, rowBottom,
                                withAlpha(palette().vapeSurfaceVariant, rowAlpha));
                        if (index > 0) {
                            RenderServices.shapes().rect(x + 8.0f, rowTop, x + listW - 8.0f, rowTop + 0.6f,
                                    withAlpha(palette().vapeOnVariant, Math.round(18.0f * progress)));
                        }
                        float pulseLineX = rightSide ? x + listW - lineW : x;
                        RenderServices.shapes().rect(pulseLineX, rowTop + 4.0f, pulseLineX + lineW, rowBottom - 4.0f,
                                withAlpha(getCategoryAccent(module), Math.round((150.0f + index * 4.0f) * progress)));
                    }
                    String sideText = entry.sideText;
                    float contentLeft = x + (rightSide ? 11.0f : lineW + 12.0f);
                    float contentRight = x + listW - (rightSide ? lineW + 12.0f : 11.0f);
                    float sideW = entry.sideWidth;
                    String name = trim(label.name, font, contentRight - contentLeft - sideW - 8.0f);
                    font.drawString(name, contentLeft, drawY + 7.0f,
                            withAlpha(palette().vapeOnSurface, Math.round(246.0f * progress)));
                    if (sideText.length() > 0) {
                        font.drawString(sideText, contentRight - sideW, drawY + 7.0f,
                                withAlpha(palette().vapeSecondary, Math.round(238.0f * progress)));
                    }
                    drawY += rowH;
                    index++;
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_module_list", pos[0], pos[1], listW * uiScale, listH * uiScale, 6.0f * uiScale);
        HudDrag.handleScroll("hud_module_list", moduleListScale, pos[0], pos[1], listW * uiScale, listH * uiScale, 0.65f, 1.8f);
    }

    private void drawPotionEffects(float factor) {
        Collection<PotionEffect> activeEffects = mc.thePlayer.getActivePotionEffects();
        if ((activeEffects == null || activeEffects.isEmpty()) && !HudDrag.isEditMode()) {
            return;
        }

        ArrayList<PotionEffect> effects = activeEffects == null
                ? new ArrayList<PotionEffect>()
                : new ArrayList<PotionEffect>(activeEffects);
        effects.sort(new Comparator<PotionEffect>() {
            @Override
            public int compare(PotionEffect first, PotionEffect second) {
                return second.getDuration() - first.getDuration();
            }
        });

        if (getSelectedStyle() == HudStyle.NIGHT_BLOOM) {
            drawNightBloomPotionEffects(effects, factor);
            return;
        }

        if (useVapeStyle()) {
            drawVapePotionEffects(effects);
            return;
        }

        float rowH = 23.0f;
        int maxRows = Math.min(6, Math.max(1, effects.size()));
        float width = 166.0f;
        float height = 23.0f + maxRows * rowH + 7.0f;
        float uiScale = getScale(potionScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_potions", potionX, potionY, potionScale, 6.0f,
                Boolean.TRUE.equals(watermark.getValue()) ? 54.0f : 6.0f,
                width * uiScale, height * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            drawSakuraPanel(x, y, x + width, y + height, getRadius(), 1.0f);
            FontLoaders.C16.drawString("Effects", x + 12.0f, y + 10.0f, sakuraText(236));
            String count = String.valueOf(effects.size());
            float chipX = x + width - 38.0f;
            drawSakuraStatusChip(count, chipX, y + 8.0f, 28.0f, SAKURA);
            drawSakuraFlower(chipX + 7.5f, y + 15.0f, 4.0f, 1.0f);

            if (effects.isEmpty()) {
                FontLoaders.C14.drawString("No effects", x + 12.0f, y + 31.0f, sakuraMuted(210));
            } else {
                for (int i = 0; i < Math.min(6, effects.size()); i++) {
                    PotionEffect effect = effects.get(i);
                    Potion potion = Potion.potionTypes[effect.getPotionID()];
                    if (potion == null) {
                        continue;
                    }
                    float rowY = y + 25.0f + i * rowH;
                    int accent = withAlpha(0xFF000000 | potion.getLiquidColor(), 210);
                    String name = trim(I18n.format(potion.getName()) + amplifierSuffix(effect.getAmplifier()),
                            FontLoaders.C14, 88.0f);
                    String duration = Potion.getDurationString(effect);

                    drawHudLiquidGlass(x + 8.0f, rowY, x + 25.0f, rowY + 17.0f, 5.0f, 0.5f,
                            withAlpha(0xFF160F15, 138), withAlpha(SAKURA, 30));
                    RenderServices.shapes().circle(x + 16.5f, rowY + 8.5f, 0, 360, 3.2f, withAlpha(accent, 210));
                    FontLoaders.C14.drawString(name, x + 31.0f, rowY + 2.0f, sakuraText(226));
                    FontLoaders.C12.drawString(duration, x + 31.0f, rowY + 12.0f, sakuraMuted(210));
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_potions", x, y, width * uiScale, height * uiScale, getRadius() * uiScale);
        HudDrag.handleScroll("hud_potions", potionScale, x, y, width * uiScale, height * uiScale, 0.65f, 1.8f);
    }

    private void drawItemStack(ItemStack stack, float x, float y) {
        if (stack == null) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableDepth();
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, Math.round(x), Math.round(y));
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, Math.round(x), Math.round(y), null);
            GlStateManager.disableDepth();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private int countInventoryItems() {
        int count = 0;
        for (int i = 9; i < 36; i++) {
            if (mc.thePlayer.inventory.mainInventory[i] != null) {
                count++;
            }
        }
        return count;
    }

    private String amplifierSuffix(int amplifier) {
        String[] levels = new String[]{"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
        return amplifier >= 0 && amplifier < levels.length ? levels[amplifier] : " " + (amplifier + 1);
    }

    private void drawSakuraPanel(float x, float y, float x2, float y2, float radius, float alpha) {
        if (alpha <= 0.002f) {
            return;
        }
        int panelAccent = useFrostedGlass() ? SAKURA : palette().accent;
        if (useFrostedGlass()) {
            RenderServices.shapes().shadow(x, y, x2, y2, radius,
                    withAlpha(0xFF000000, Math.round(96.0f * alpha)), 8, 3.4f);
            RenderServices.shapes().shadow(x, y, x2, y2, radius,
                    withAlpha(SAKURA, Math.round(34.0f * alpha)), 5, 2.2f);
        }
        drawHudLiquidGlass(x, y, x2, y2, radius, 0.55f,
                withAlpha(SAKURA_GLASS, Math.round(getGlassAlpha() * alpha)),
                withAlpha(SAKURA, Math.round(34.0f * alpha)));
        if (useFrostedGlass()) {
            RenderServices.shapes().shadow(x + 12.0f, y + 5.0f, x2 - 12.0f, y2 - 5.0f,
                    radius, withAlpha(SAKURA, Math.round(20.0f * alpha)), 3, 1.8f);
        }
        RenderServices.shapes().rounded(x + 8.0f, y2 - 9.0f, Math.min(x2 - 8.0f, x + 72.0f), y2 - 4.0f,
                3.0f, withAlpha(panelAccent, Math.round(14.0f * alpha)));
    }

    private void drawSakuraIconWell(float x, float y, float size, float alpha) {
        RenderServices.shapes().shadow(x, y, x + size, y + size, 7.0f,
                withAlpha(0xFF000000, Math.round(86.0f * alpha)), 5, 1.8f);
        RenderServices.shapes().roundedBorder(x, y, x + size, y + size, 7.0f, 0.8f,
                withAlpha(0xFF20171C, Math.round(190.0f * alpha)),
                withAlpha(SAKURA, Math.round(62.0f * alpha)));
    }

    private void drawSakuraStatusChip(String text, float x, float y, float w, int accent) {
        if (Boolean.TRUE.equals(backgrounds.getValue())) {
            drawHudLiquidGlass(x, y, x + w, y + 14.0f, 5.0f, 0.5f,
                    withAlpha(0xFF160F15, 142), withAlpha(SAKURA, 34));
        }
        FontLoaders.C14.drawString(trim(text, FontLoaders.C14, w - 17.0f), x + 15.0f, y + 4.0f,
                sakuraText(220));
    }

    private LiquidGlassSettings sakuraGlassSettings() {
        return SAKURA_GLASS_SETTINGS;
    }

    private void drawTextGlow(CFontRenderer font, String text, float x, float y, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        font.drawGlowString(text, x, y, withAlpha(SAKURA, Math.round(186.0f * alpha)),
                0.72f, GlowProfile.TEXT);
    }

    private void drawSakuraFlower(float centerX, float centerY, float size, float alpha) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        RenderServices.shapes().shadow(centerX - size, centerY - size, centerX + size, centerY + size,
                size, withAlpha(SAKURA, Math.round(74.0f * alpha)), 4, size * 0.70f);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0f);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * 0.20f, 0.0f);
            drawSakuraPetal2D(size, alpha);
            GL11.glPopMatrix();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        RenderServices.shapes().circle(centerX, centerY, 0, 360, size * 0.30f,
                withAlpha(0xFFFFF3FA, Math.round(235.0f * alpha)));
        resetTextRenderState();
    }

    private void drawSakuraPetal2D(float size, float alpha) {
        float width = size * 0.58f;
        float length = size * 1.12f;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(0xFFFFEAF3, alpha * 0.96f);
        GL11.glVertex2f(0.0f, length * 0.36f);
        for (float[] point : SAKURA_PETAL_POINTS) {
            glColor(SAKURA, alpha * 0.70f);
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();

        GL11.glLineWidth(0.75f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        glColor(0xFFFFF6FA, alpha * 0.45f);
        for (float[] point : SAKURA_PETAL_POINTS) {
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();
    }

    private void drawWatermarkPetals(float x, float y, float width, float height, float alpha) {
        if (alpha <= 0.002f) {
            return;
        }
        float time = (System.currentTimeMillis() % 3600L) / 3600.0f;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            for (int i = 0; i < 5; i++) {
                float phase = fract(time * (0.66f + i * 0.08f) + i * 0.19f);
                float sway = (float) Math.sin((time * 6.2831855f) + i * 1.27f);
                float px = x + width - 34.0f + phase * 24.0f + sway * 2.0f;
                float py = y + 5.0f + (i % 3) * 3.6f
                        + (float) Math.sin((phase + i * 0.17f) * 6.2831855f) * 1.8f;
                py = Math.max(y + 4.2f, Math.min(y + height - 4.2f, py));
                float petalAlpha = alpha * (0.20f + 0.05f * i) * (1.0f - phase * 0.32f);
                GL11.glPushMatrix();
                GL11.glTranslatef(px, py, 0.0f);
                GL11.glRotatef(phase * 210.0f + i * 47.0f, 0.0f, 0.0f, 1.0f);
                drawSakuraPetal2D(1.45f + i * 0.14f, petalAlpha);
                GL11.glPopMatrix();
            }
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            resetTextRenderState();
        }
    }

    private void glColor(int color, float alpha) {
        float a = ((color >>> 24) & 255) / 255.0f * Math.max(0.0f, Math.min(1.0f, alpha));
        float r = ((color >>> 16) & 255) / 255.0f;
        float g = ((color >>> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        GlStateManager.color(r, g, b, a);
    }

    private void resetTextRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawStatusChip(String text, float x, float y, float w, int accent) {
        if (Boolean.TRUE.equals(backgrounds.getValue())) {
            drawHudFrostedGlass(x, y, x + w, y + 14.0f, 5.0f, 0.6f,
                    withAlpha(palette().glassSoft, getSoftAlpha()), withAlpha(accent, 48));
            RenderServices.shapes().circle(x + 7.5f, y + 7.0f, 0, 360, 2.0f, withAlpha(accent, 185));
        }
        FontLoaders.C14.drawString(trim(text, FontLoaders.C14, w - 16.0f), x + 14.0f, y + 4.0f,
                withAlpha(palette().text, 218));
    }

    private void drawGlass(float x, float y, float x2, float y2, float round, int fillAlpha, int borderAlpha) {
        int fill = withAlpha(palette().glass, fillAlpha);
        int border = withAlpha(palette().border, borderAlpha);
        if (useFrostedGlass()) {
            RenderServices.shapes().shadow(x, y, x2, y2, round, withAlpha(palette().shadowColor, 44), 6, 3.2f);
        }
        drawHudFrostedGlass(x, y, x2, y2, round, 1.0f, fill, border);
    }

    private void drawHudFrostedGlass(float x, float y, float x2, float y2, float radius,
                                     float strength, int fillColor, int borderColor) {
        if (useFrostedGlass()) {
            HudRenderSupport.drawThemedFrostedGlass(x, y, x2, y2, radius, strength, fillColor, borderColor);
        } else {
            drawSolidPanel(x, y, x2, y2, radius, 0.6f, fillColor, borderColor);
        }
    }

    private void drawHudLiquidGlass(float x, float y, float x2, float y2, float radius, float borderWidth,
                                    int fillColor, int borderColor) {
        if (useFrostedGlass()) {
            RenderServices.liquidGlass().roundedBorder(x, y, x2, y2, radius, borderWidth,
                    fillColor, borderColor, sakuraGlassSettings());
        } else {
            drawSolidPanel(x, y, x2, y2, radius, Math.min(borderWidth, 0.65f), fillColor, borderColor);
        }
    }

    private void drawSolidPanel(float x, float y, float x2, float y2, float radius, float borderWidth,
                                int fillColor, int borderColor) {
        float opacity = solidOpacity(fillColor, Math.max(1, getGlassAlpha()));
        RenderServices.shapes().shadow(x, y, x2, y2, radius,
                getSolidPanelShadowColor(opacity), 8, 4.8f);
        RenderServices.shapes().roundedBorder(x, y, x2, y2, radius, borderWidth,
                solidHudFill(fillColor), solidHudBorder(borderColor));
        if (isLightTheme() || isSakuraTheme()) {
            RenderServices.shapes().horizontalGradient(x + 1.0f, y + 1.0f, x2 - 1.0f,
                    Math.min(y2 - 1.0f, y + 10.0f), withAlpha(0xFFFFFFFF, 26), 0x00FFFFFF);
        } else {
            RenderServices.shapes().horizontalGradient(x + 1.0f, y + 1.0f, x2 - 1.0f,
                    Math.min(y2 - 1.0f, y + 10.0f), withAlpha(0xFFFFFFFF, 12), 0x00FFFFFF);
        }
    }

    private int solidHudFill(int originalColor) {
        return getSolidPanelFillColor(solidOpacity(originalColor, Math.max(1, getGlassAlpha())));
    }

    private int solidHudBorder(int originalColor) {
        return getSolidPanelBorderColor(solidOpacity(originalColor, 34));
    }

    private float solidOpacity(int color, int fullAlpha) {
        int alpha = (color >>> 24) & 255;
        if (alpha <= 0) {
            return 0.0f;
        }
        return ColorUtils.clamp(alpha / (float) Math.max(1, fullAlpha), 0.0f, 1.0f);
    }

    private static int clickGuiStyleFill() {
        Theme t = getTheme();
        if (t == Theme.SAKURA) {
            return 0xFFFFF9FC;
        }
        if (t == Theme.LIGHT) {
            return 0xFFE8ECF4;
        }
        if (t == Theme.GRAY) {
            return 0xFF12151A;
        }
        return 0xFF07090D;
    }

    private static int clickGuiStyleSoftFill() {
        Theme t = getTheme();
        if (t == Theme.SAKURA) {
            return 0xFFFFF9FC;
        }
        if (t == Theme.LIGHT) {
            return 0xFFE1E6F0;
        }
        if (t == Theme.GRAY) {
            return 0xFF181B20;
        }
        return 0xFF07090D;
    }

    private static int clickGuiStyleBorder() {
        Theme t = getTheme();
        if (t == Theme.SAKURA) {
            return 0xFFE2A5C2;
        }
        if (t == Theme.LIGHT) {
            return 0xFF9BB9D2;
        }
        if (t == Theme.GRAY) {
            return 0xFFBEC4CE;
        }
        return 0xFF9ABED6;
    }

    private static int solidPanelFillAlpha() {
        Theme t = getTheme();
        if (t == Theme.SAKURA || t == Theme.LIGHT) {
            return 250;
        }
        return t == Theme.GRAY ? 168 : 154;
    }

    private static int solidPanelBorderAlpha() {
        Theme t = getTheme();
        if (t == Theme.SAKURA || t == Theme.LIGHT) {
            return 104;
        }
        return t == Theme.GRAY ? 62 : 58;
    }

    private static int solidPanelShadowAlpha() {
        return isLightTheme() || isSakuraTheme() ? 92 : 76;
    }

    private boolean useFrostedGlass() {
        return Boolean.TRUE.equals(frostedGlass.getValue());
    }

    private int sakuraText(int alpha) {
        return withAlpha(useFrostedGlass() ? SAKURA_TEXT : palette().text, alpha);
    }

    private int sakuraMuted(int alpha) {
        return withAlpha(useFrostedGlass() ? SAKURA_MUTED : palette().muted, alpha);
    }

    public static void drawThemedFrostedGlass(float x, float y, float x2, float y2, float radius, float strength,
                                                int fillColor, int borderColor) {
        HudRenderSupport.drawThemedFrostedGlass(x, y, x2, y2, radius, strength, fillColor, borderColor);
    }

    private void drawVapeCard(float x, float y, float x2, float y2, float radius, int alpha) {
        HudRenderSupport.drawVapeCard(x, y, x2, y2, radius, withAlpha(palette().shadowColor, 58),
                withAlpha(palette().vapeSurface, alpha), withAlpha(0xFFFFFFFF, 24));
    }

    private void drawGlowIfEnabled(float x, float y, float x2, float y2, float radius, int glowColor) {
        if (Boolean.TRUE.equals(glow.getValue()) && Boolean.TRUE.equals(backgrounds.getValue())) {
            RenderServices.glow().queueRoundedRect(x, y, x2, y2, radius, glowColor,
                    0.70f, GlowProfile.PANEL);
        }
    }

    private void drawCenteredIcon(String icon, CFontRenderer font, float centerX, float centerY, int color) {
        font.drawString(icon, centerX - font.getStringWidth(icon) / 2.0f + ClickGuiIcons.visualOffsetX(icon),
                centerY - font.getHeight() / 2.0f + 2.0f + ClickGuiIcons.visualOffsetY(icon), color);
    }

    private List<Module> getHudModules() {
        hudModuleScratch.clear();
        for (Module module : ModuleManager.getModules()) {
            if (module != null
                    && module.getState()
                    && module != this
                    && !"ClickGUI".equalsIgnoreCase(module.getName())) {
                hudModuleScratch.add(module);
            }
        }
        return hudModuleScratch;
    }

    private List<ModuleListEntry> getSortedNightBloomModuleListEntries(List<Module> modules,
                                                                        CFontRenderer nameFont,
                                                                        final CFontRenderer metaFont) {
        nightBloomModuleSortScratch.clear();
        nightBloomModuleSortScratch.addAll(getSortedModuleListEntries(modules, nameFont, 5.0F));
        nightBloomModuleWidthScratch.clear();
        for (ModuleListEntry entry : nightBloomModuleSortScratch) {
            nightBloomModuleWidthScratch.put(entry.module,
                    nightBloomRenderedModuleRowWidth(entry, metaFont));
        }
        nightBloomModuleSortScratch.sort(new Comparator<ModuleListEntry>() {
            @Override
            public int compare(ModuleListEntry first, ModuleListEntry second) {
                return NightBloomHudLayout.compareModuleRowsByRenderedWidth(
                        nightBloomModuleWidthScratch.get(first.module),
                        nightBloomModuleWidthScratch.get(second.module));
            }
        });
        return nightBloomModuleSortScratch;
    }

    private static float nightBloomRenderedModuleRowWidth(ModuleListEntry entry, CFontRenderer metaFont) {
        float metaWidth = entry.sideText.length() == 0
                ? 0.0F : metaFont.getStringWidth(entry.sideText);
        return NightBloomHudLayout.moduleRowWidth(entry.nameWidth, metaWidth);
    }

    private List<ModuleListEntry> getSortedModuleListEntries(List<Module> modules, CFontRenderer font, float gap) {
        int roundedGap = Math.round(gap);
        int signature = moduleListSignature(modules, font, roundedGap);
        long now = System.currentTimeMillis();
        if (signature == moduleEntryCacheSignature
                && font == moduleEntryCacheFont
                && roundedGap == moduleEntryCacheGap
                && now - moduleEntryCacheTime <= MODULE_LIST_CACHE_MILLIS) {
            return moduleEntryCache;
        }

        rebuildModuleListEntries(moduleEntryCache, modules, font, roundedGap);
        moduleEntryCache.sort(new Comparator<ModuleListEntry>() {
            @Override
            public int compare(ModuleListEntry first, ModuleListEntry second) {
                return second.labelWidth - first.labelWidth;
            }
        });
        moduleEntryCacheSignature = signature;
        moduleEntryCacheFont = font;
        moduleEntryCacheGap = roundedGap;
        moduleEntryCacheTime = now;
        return moduleEntryCache;
    }

    private int moduleListSignature(List<Module> modules, CFontRenderer font, int roundedGap) {
        ArrayListTheme listTheme = arrayListTheme.getValue();
        if (listTheme == null) {
            listTheme = ArrayListTheme.OLD;
        }
        int signature = 17;
        signature = 31 * signature + System.identityHashCode(font);
        signature = 31 * signature + roundedGap;
        signature = 31 * signature + (Boolean.TRUE.equals(parameters.getValue()) ? 1 : 0);
        signature = 31 * signature + (Boolean.TRUE.equals(keybinds.getValue()) ? 1 : 0);
        signature = 31 * signature + getSelectedStyle().ordinal();
        signature = 31 * signature + listTheme.ordinal();
        for (Module module : modules) {
            signature = 31 * signature + System.identityHashCode(module);
            signature = 31 * signature + module.getKey();
        }
        return signature;
    }

    private void clearModuleEntryCache() {
        moduleEntryCache.clear();
        moduleEntryCacheSignature = 0;
        moduleEntryCacheGap = 0;
        moduleEntryCacheFont = null;
        moduleEntryCacheTime = 0L;
    }

    private void rebuildModuleListEntries(List<ModuleListEntry> entries, List<Module> modules, CFontRenderer font, int roundedGap) {
        entries.clear();
        for (Module module : modules) {
            ModuleListLabel label = getModuleListLabel(module);
            int nameWidth = font.getStringWidth(label.name);
            int parameterWidth = label.parameter.length() == 0 ? 0 : font.getStringWidth(label.parameter);
            int keyWidth = label.key.length() == 0 ? 0 : font.getStringWidth(label.key);
            int labelWidth = nameWidth;
            if (parameterWidth > 0) {
                labelWidth += roundedGap + parameterWidth;
            }
            if (keyWidth > 0) {
                labelWidth += roundedGap + keyWidth;
            }
            String sideText = getModuleSideText(label);
            int sideWidth = sideText.length() == 0 ? 0 : font.getStringWidth(sideText);
            entries.add(new ModuleListEntry(module, label, labelWidth, nameWidth, sideText, sideWidth, label.fullText()));
        }
    }

    private ModuleListLabel getModuleListLabel(Module module) {
        String parameter = Boolean.TRUE.equals(parameters.getValue()) ? getModuleParameter(module) : "";
        String key = "";
        if (Boolean.TRUE.equals(keybinds.getValue()) && module.getKey() != Keyboard.KEY_NONE) {
            key = Keyboard.getKeyName(module.getKey());
        }
        return new ModuleListLabel(getDisplayName(module), parameter, key);
    }

    private String getModuleParameter(Module module) {
        String cps = getCpsParameter(module);
        if (cps.length() > 0) {
            return cps;
        }

        Mode modeValue = findModeValue(module);
        if (modeValue != null && modeValue.getValue() instanceof Enum) {
            return formatModeName(((Enum) modeValue.getValue()).name());
        }

        NumberValue number = findNumberParameter(module);
        if (number != null) {
            return formatNumber(number.value) + number.suffix;
        }
        return "";
    }

    private String getCpsParameter(Module module) {
        Numbers min = asNumber(findValue(module, "Min CPS", "MinCPS"));
        Numbers max = asNumber(findValue(module, "Max CPS", "Cps", "MaxCPS"));
        if (min == null || max == null || !(min.getValue() instanceof Number) || !(max.getValue() instanceof Number)) {
            return "";
        }
        double minValue = ((Number) min.getValue()).doubleValue();
        double maxValue = ((Number) max.getValue()).doubleValue();
        double low = Math.min(minValue, maxValue);
        double high = Math.max(minValue, maxValue);
        return formatNumber(low) + "-" + formatNumber(high) + "cps";
    }

    private Mode findModeValue(Module module) {
        Value exact = findValue(module, "Mode");
        if (exact instanceof Mode && !isIgnoredMode(exact)) {
            return (Mode) exact;
        }
        for (Value value : module.getValues()) {
            if (value instanceof Mode && !isIgnoredMode(value)) {
                return (Mode) value;
            }
        }
        return null;
    }

    private boolean isIgnoredMode(Value value) {
        String name = normalizeValueName(value);
        return "priority".equals(name) || "sort".equals(name) || "sortmode".equals(name)
                || "aimpoint".equals(name) || "autoblockmode".equals(name) || "hudstyle".equals(name);
    }

    private NumberValue findNumberParameter(Module module) {
        NumberValue range = namedNumber(module, "r", "Range");
        if (range != null) {
            return range;
        }
        NumberValue delay = namedNumber(module, "ms", "Delay MS", "DelayMS", "Delay", "Packet Delay",
                "PacketDelay", "History MS", "HistoryMS", "Pulse MS", "PulseMS", "Jitter MS", "JitterMS");
        if (delay != null) {
            return delay;
        }
        NumberValue scale = namedNumber(module, "x", "Scale", "Size", "Radius");
        if (scale != null) {
            return scale;
        }
        NumberValue expand = namedNumber(module, "", "Expand", "Height", "Line Width", "LineWidth");
        return expand;
    }

    private NumberValue namedNumber(Module module, String suffix, String... names) {
        Numbers number = asNumber(findValue(module, names));
        if (number == null || !(number.getValue() instanceof Number)) {
            return null;
        }
        return new NumberValue(((Number) number.getValue()).doubleValue(), suffix);
    }

    private Value findValue(Module module, String... names) {
        if (module == null || names == null) {
            return null;
        }
        for (Value value : module.getValues()) {
            String current = normalizeValueName(value);
            for (String name : names) {
                if (current.equals(normalize(name))) {
                    return value;
                }
            }
        }
        return null;
    }

    private String normalizeValueName(Value value) {
        if (value == null) {
            return "";
        }
        String display = normalize(value.getDisplayName());
        if (display.length() > 0) {
            return display;
        }
        return normalize(value.getName());
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace(" ", "").replace("_", "").replace("-", "").toLowerCase();
    }

    private Numbers asNumber(Value value) {
        return value instanceof Numbers ? (Numbers) value : null;
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05D) {
            return String.valueOf(Math.round(value));
        }
        String text = String.format(java.util.Locale.US, "%.1f", value);
        while (text.endsWith("0") && text.indexOf('.') >= 0) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String formatModeName(String raw) {
        if (raw == null || raw.length() == 0) {
            return "";
        }
        String[] words = raw.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private int getModuleLabelWidth(ModuleListLabel label, CFontRenderer font, float gap) {
        if (label == null) {
            return 0;
        }
        int width = font.getStringWidth(label.name);
        if (label.parameter.length() > 0) {
            width += Math.round(gap) + font.getStringWidth(label.parameter);
        }
        if (label.key.length() > 0) {
            width += Math.round(gap) + font.getStringWidth(label.key);
        }
        return width;
    }

    private void drawModuleLabel(ModuleListLabel label, CFontRenderer font, float x, float y,
                                 int nameColor, int parameterColor, float gap) {
        if (label == null) {
            return;
        }
        float cursor = x;
        font.drawString(label.name, cursor, y, nameColor);
        cursor += font.getStringWidth(label.name);
        if (label.parameter.length() > 0) {
            cursor += gap;
            font.drawString(label.parameter, cursor, y, parameterColor);
            cursor += font.getStringWidth(label.parameter);
        }
        if (label.key.length() > 0) {
            cursor += gap;
            font.drawString(label.key, cursor, y, parameterColor);
        }
    }

    private String getModuleSideText(ModuleListLabel label) {
        if (label == null) {
            return "";
        }
        if (label.parameter.length() > 0 && label.key.length() > 0) {
            return label.parameter + " " + label.key;
        }
        if (label.parameter.length() > 0) {
            return label.parameter;
        }
        return label.key;
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

    private void clearNightBloomModuleMotions() {
        nightBloomModuleMotions.clear();
        nightBloomModuleEntries.clear();
        nightBloomVisibleModuleScratch.clear();
        nightBloomModuleRenderScratch.clear();
        nightBloomModuleClock.reset();
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 250.0D);
    }

    private static final class ModuleListLabel {
        private final String name;
        private final String parameter;
        private final String key;

        private ModuleListLabel(String name, String parameter, String key) {
            this.name = name == null ? "" : name;
            this.parameter = parameter == null ? "" : parameter;
            this.key = key == null ? "" : key;
        }

        private String fullText() {
            String text = name;
            if (parameter.length() > 0) {
                text += " " + parameter;
            }
            if (key.length() > 0) {
                text += " " + key;
            }
            return text;
        }
    }

    private static final class ModuleListEntry {
        private final Module module;
        private final ModuleListLabel label;
        private final int labelWidth;
        private final int nameWidth;
        private final String sideText;
        private final int sideWidth;
        private final String fullText;

        private ModuleListEntry(Module module, ModuleListLabel label, int labelWidth, int nameWidth,
                                String sideText, int sideWidth, String fullText) {
            this.module = module;
            this.label = label;
            this.labelWidth = labelWidth;
            this.nameWidth = nameWidth;
            this.sideText = sideText == null ? "" : sideText;
            this.sideWidth = sideWidth;
            this.fullText = fullText == null ? "" : fullText;
        }
    }

    private static final class NightBloomModuleRenderEntry {
        private final ModuleListEntry entry;
        private final NightBloomModuleRowMotion.Snapshot snapshot;

        private NightBloomModuleRenderEntry(ModuleListEntry entry, NightBloomModuleRowMotion.Snapshot snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
        }
    }

    private static final class NumberValue {
        private final double value;
        private final String suffix;

        private NumberValue(double value, String suffix) {
            this.value = value;
            this.suffix = suffix == null ? "" : suffix;
        }
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

    private float getScale(Numbers<Double> value) {
        return value == null || value.getValue() == null ? 1.0f : Math.max(0.1f, value.getValue().floatValue());
    }

    private HudStyle getSelectedStyle() {
        HudStyle selected = hudStyle.getValue();
        return selected == null ? HudStyle.YOZAKURA : selected;
    }

    private boolean useVapeStyle() {
        return getSelectedStyle() == HudStyle.OLD;
    }

    public static HudStyle getActiveStyle() {
        if (instance != null) {
            activeStyle = instance.getSelectedStyle();
        }
        return activeStyle == null ? HudStyle.YOZAKURA : activeStyle;
    }

    public static boolean useVapeSimpleStyle() {
        return getActiveStyle() == HudStyle.OLD;
    }

    public static boolean isGlowEnabled() {
        if (instance != null && instance.glow != null) {
            return Boolean.TRUE.equals(instance.glow.getValue());
        }
        return false;
    }

    // Night Bloom keeps its halo on glyphs, so a small boost improves legibility
    // without reintroducing a glowing panel edge.
    private static final float NIGHT_BLOOM_GLOW_STRENGTH_BOOST = 0.16F;

    public static void drawNightBloomShadow(float x, float y, float x2, float y2,
                                             float radius, float alpha) {
        if (x2 <= x || y2 <= y || radius < 0.0F || alpha <= 0.0F) {
            return;
        }
        GlowRenderer shadows = RenderServices.shadows();
        boolean isolatedFrame = !shadows.isFrameOpen();
        if (isolatedFrame) {
            shadows.beginFrame();
        }
        try {
            shadows.queueRoundedRect(x, y, x2, y2, radius,
                    withNightBloomAlpha(NightBloomHudLayout.SHADOW_MASK_COLOR, alpha),
                    1.0F, GlowProfile.SHADOW);
        } finally {
            if (isolatedFrame) {
                shadows.flush();
            }
        }
    }

    public static void drawNightBloomText(CFontRenderer font, String text, double x, double y,
                                          int textColor, int glowColor, float glowStrength) {
        drawNightBloomGlyph(font, text, x, y, textColor, glowColor, glowStrength, GlowProfile.TEXT);
    }

    public static void drawNightBloomCenteredIcon(String icon, CFontRenderer font,
                                                   float centerX, float centerY,
                                                   int textColor, int glowColor, float glowStrength) {
        float x = centerX - font.getStringWidth(icon) / 2.0F + ClickGuiIcons.visualOffsetX(icon);
        float y = centerY - font.getHeight() / 2.0F + 2.0F + ClickGuiIcons.visualOffsetY(icon);
        drawNightBloomGlyph(font, icon, x, y, textColor, glowColor, glowStrength, GlowProfile.ACCENT);
    }

    private static void drawNightBloomGlyph(CFontRenderer font, String text, double x, double y,
                                            int textColor, int glowColor, float glowStrength,
                                            GlowProfile profile) {
        if (font == null || text == null || text.length() == 0) {
            return;
        }
        if (isGlowEnabled() && RenderServices.glow().isFrameOpen()
                && glowStrength > 0.0F && (glowColor >>> 24) > 0) {
            float resolvedStrength = Math.min(1.0F,
                    Math.max(0.0F, glowStrength + NIGHT_BLOOM_GLOW_STRENGTH_BOOST));
            font.drawStringWithGlow(text, x, y, textColor, glowColor, resolvedStrength, profile);
            return;
        }
        font.drawString(text, (float) x, (float) y, textColor);
    }

    public static boolean isHudFrostedGlassEnabled() {
        if (instance != null && instance.frostedGlass != null) {
            return Boolean.TRUE.equals(instance.frostedGlass.getValue());
        }
        return true;
    }

    public static int getThemeBackgroundColor() {
        return clickGuiStyleFill();
    }

    public static int getThemeSoftBackgroundColor() {
        return clickGuiStyleSoftFill();
    }

    public static int getSolidPanelFillColor(float opacity) {
        return withAlpha(clickGuiStyleFill(),
                Math.round(solidPanelFillAlpha() * ColorUtils.clamp(opacity, 0.0f, 1.0f)));
    }

    public static int getSolidPanelBorderColor(float opacity) {
        return withAlpha(clickGuiStyleBorder(),
                Math.round(solidPanelBorderAlpha() * ColorUtils.clamp(opacity, 0.0f, 1.0f)));
    }

    public static int getSolidPanelShadowColor(float opacity) {
        int color = isLightTheme() || isSakuraTheme() ? 0xFFFFFFFF : 0xFF000000;
        return withAlpha(color,
                Math.round(solidPanelShadowAlpha() * ColorUtils.clamp(opacity, 0.0f, 1.0f)));
    }

    public static int getThemeTextColor() {
        return palette().text;
    }

    public static int getThemeMutedTextColor() {
        return palette().muted;
    }

    public static int getThemeBorderColor() {
        return clickGuiStyleBorder();
    }

    public static int getThemeAccentColor() {
        return palette().accent;
    }

    public static int getThemeAccentAltColor() {
        return palette().accentAlt;
    }

    public static Theme getTheme() {
        if (instance != null) {
            Theme selected = instance.theme.getValue();
            return selected == null ? Theme.DARK : selected;
        }
        return Theme.DARK;
    }

    public static void setTheme(Theme next) {
        if (instance != null && next != null && instance.theme != null) {
            instance.theme.setValue(next);
        }
    }

    public static boolean isLightTheme() {
        return getTheme() == Theme.LIGHT;
    }

    public static boolean isSakuraTheme() {
        return getTheme() == Theme.SAKURA;
    }

    public static boolean isGrayTheme() {
        return getTheme() == Theme.GRAY;
    }

    public static boolean isNotificationSakura() {
        if (instance != null && instance.notificationTheme != null) {
            return instance.notificationTheme.getValue() == NotificationTheme.SAKURA;
        }
        return false;
    }

    public static boolean isNotificationNightBloom() {
        if (instance != null && instance.notificationTheme != null) {
            return instance.notificationTheme.getValue() == NotificationTheme.NIGHT_BLOOM;
        }
        return false;
    }

    private void beginScaled(float x, float y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        GlStateManager.translate(-x, -y, 0.0f);
    }

    private void endScaled() {
        GlStateManager.popMatrix();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (ColorUtils.clamp(alpha, 0, 255) << 24);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static int getCategoryAccent(Module module) {
        if (module.getCategory() == ModuleType.Combat) {
            return 0xFF8B7CFF;
        }
        if (module.getCategory() == ModuleType.Movement) {
            return 0xFF70C1DC;
        }
        if (module.getCategory() == ModuleType.Render) {
            return 0xFFFF8DA8;
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

    private static String trim(String text, gq.yozakura.engine.font.CFontRenderer font, float maxWidth) {
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
