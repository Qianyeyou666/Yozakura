package gq.vapulite.module.render;

import gq.vapulite.manager.ModuleManager;
import gq.vapulite.manager.NotificationManager;
import gq.vapulite.core.Client;
import gq.vapulite.module.ModuleType;
import gq.vapulite.ui.click.ClickGuiIcons;
import gq.vapulite.module.Module;
import gq.vapulite.util.color.ColorUtils;
import gq.vapulite.util.render.HudDrag;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import gq.vapulite.value.Value;
import gq.vapulite.engine.font.CFontRenderer;
import gq.vapulite.engine.font.FontLoaders;
import gq.vapulite.engine.render.ui.LiquidGlassSettings;
import gq.vapulite.engine.render.ui.RenderServices;
import gq.vapulite.util.render.RenderUtil;
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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HUD extends Module {
    private static final int SAKURA_TEXT = 0xFFF5F0F5;
    private static final int SAKURA_MUTED = 0xFFB8AEB8;
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_STRONG = 0xFFFF80B3;
    private static final int SAKURA_GLASS = 0xFF08080D;

    public enum HudStyle {
        VAPULITE,
        VAPE
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
        SAKURA
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
    private static HudStyle activeStyle = HudStyle.VAPULITE;

    private final Option<Boolean> watermark = new Option<Boolean>("Watermark", "Watermark", true);
    private final Option<Boolean> arrayList = new Option<Boolean>("ModuleList", "ModuleList", true);
    private final Option<Boolean> backgrounds = new Option<Boolean>("Backgrounds", "Backgrounds", true);
    private final Option<Boolean> keybinds = new Option<Boolean>("Keybinds", "Keybinds", false);
    private final Option<Boolean> parameters = new Option<Boolean>("Parameters", "Parameters", true);
    private final Option<Boolean> notifications = new Option<Boolean>("Notifications", "Notifications", true);
    private final Option<Boolean> potionEffects = new Option<Boolean>("PotionEffects", "PotionEffects", true);
    private final Option<Boolean> inventoryDisplay = new Option<Boolean>("Inventory", "Inventory", true);
    private final Option<Boolean> glow = new Option<Boolean>("Glow", "Glow", false);
    private final Mode<HudStyle> hudStyle = new Mode<HudStyle>("HUD Style", "HUDStyle", HudStyle.values(), HudStyle.VAPULITE);
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
    private long lastFrameMS = System.currentTimeMillis();

    public HUD() {
        super("HUD", Keyboard.KEY_H, ModuleType.Render, "Show " + Client.name + " HUD Screen");
        Chinese = "HUD界面";
        instance = this;
        activeStyle = getSelectedStyle();
        this.addValues(notificationTheme, arrayListTheme, theme, watermark, arrayList, backgrounds, keybinds, parameters, notifications,
                potionEffects, inventoryDisplay, glow, alpha, radius, watermarkX, watermarkY, watermarkScale,
                moduleListX, moduleListY, moduleListScale, potionX, potionY, potionScale, inventoryX, inventoryY,
                inventoryScale);
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

        activeStyle = getSelectedStyle();

        if (Boolean.TRUE.equals(watermark.getValue())) {
            drawWatermark();
        }
        if (Boolean.TRUE.equals(arrayList.getValue())) {
            drawModuleList(width, height, factor);
        }
        if (Boolean.TRUE.equals(potionEffects.getValue())) {
            drawPotionEffects();
        }
        if (Boolean.TRUE.equals(inventoryDisplay.getValue())) {
            drawInventory(width, height);
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
        lastFrameMS = System.currentTimeMillis();
    }

    private void drawWatermark() {
        if (useVapeStyle()) {
            drawVapeWatermark();
            return;
        }

        String capsule = "Sakura · " + Minecraft.getDebugFPS() + "fps";
        int ping = getPing();
        if (ping >= 0) {
            capsule += " · " + ping + "ms";
        }

        CFontRenderer smallFont = FontLoaders.C14;
        float capsuleW = smallFont.getStringWidth(capsule) + 36.0f;
        float boxW = Math.max(166.0f, capsuleW + 10.0f);
        float boxH = 21.0f;
        float round = Math.max(6.0f, getRadius() - 1.0f);
        float uiScale = getScale(watermarkScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_watermark", watermarkX, watermarkY, watermarkScale, 6.0f, 6.0f,
                boxW * uiScale, boxH * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
            float pillW = Math.min(boxW, capsuleW);
            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                RenderServices.shapes().shadow(x, y + 1.0f, x + pillW, y + 19.0f, 7.0f,
                        withAlpha(0xFF000000, 92), 7, 2.2f);
                RenderServices.shapes().shadow(x - 1.0f, y, x + pillW + 1.0f, y + 20.0f, 7.0f,
                        withAlpha(SAKURA, 54), 5, 2.0f);
                RenderServices.liquidGlass().roundedBorder(x, y, x + pillW, y + 19.0f, 7.0f, 0.55f,
                        withAlpha(SAKURA_GLASS, 166), withAlpha(SAKURA, 48), sakuraGlassSettings());
                RenderServices.shapes().horizontalGradient(x + 2.0f, y + 1.0f, x + pillW - 2.0f, y + 9.0f,
                        withAlpha(0xFFFFF6FA, 20), withAlpha(SAKURA, 4));
            }

            drawWatermarkPetals(x, y, pillW, 19.0f, 1.0f);
            drawSakuraFlower(x + 10.5f, y + 10.2f, 3.0f, 1.0f);
            smallFont.drawString(trim(capsule, smallFont, pillW - 28.0f),
                    x + 21.0f, y + 7.2f, withAlpha(SAKURA_TEXT, 236));
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_watermark", x, y, boxW * uiScale, boxH * uiScale, round * uiScale);
        HudDrag.handleScroll("hud_watermark", watermarkScale, x, y, boxW * uiScale, boxH * uiScale, 0.65f, 1.8f);
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

    private void drawInventory(int screenWidth, int screenHeight) {
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
            FontLoaders.C16.drawString("Inventory", x + 10.0f, y + 10.0f, withAlpha(SAKURA_TEXT, 236));

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
        if (hudStyle.getValue() == HudStyle.VAPE) {
            drawVapeModuleList(screenWidth, screenHeight, factor, modules);
            return;
        }

        final boolean sakura = arrayListTheme.getValue() == ArrayListTheme.SAKURA;
        final CFontRenderer font = sakura ? FontLoaders.C14 : FontLoaders.C18;
        final float fontSizeGap = sakura ? 3.0f : 4.0f;
        List<ModuleListEntry> entries = buildModuleListEntries(modules, font, fontSizeGap);
        entries.sort(new Comparator<ModuleListEntry>() {
            @Override
            public int compare(ModuleListEntry first, ModuleListEntry second) {
                return second.labelWidth - first.labelWidth;
            }
        });

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
                            withAlpha(SAKURA_MUTED, 210));
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
                int accent = sakura ? SAKURA : getCategoryAccent(module);

                if (sakura) {
                    drawSakuraModuleRow(module, icon, label, x, y, right, rowH,
                            progress, accent, font, iconSlotW);
                } else if (Boolean.TRUE.equals(backgrounds.getValue())) {
                    int rowAlpha = Math.round(getGlassAlpha() * progress);
                    RenderServices.shapes().shadow(x, y, right, y + rowH, round,
                            withAlpha(palette().shadowColor, Math.round(34.0f * progress)), 4, 2.4f);
                    drawThemedFrostedGlass(x, y, right, y + rowH, round, 0.8f,
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

        // Double shadow (black + sakura glow)
        RenderServices.shapes().shadow(x, y, right, y + rowH, 4.0f,
                withAlpha(0xFF000000, Math.round(48.0f * progress)), 5, 2.0f);
        RenderServices.shapes().shadow(x, y, right, y + rowH, 4.0f,
                withAlpha(SAKURA, Math.round(22.0f * progress)), 4, 1.6f);

        // Dark glass background with sakura border
        RenderServices.liquidGlass().roundedBorder(x, y, right, y + rowH, 4.0f, 0.50f,
                withAlpha(SAKURA_GLASS, Math.round(152.0f * glassAlpha * progress)),
                withAlpha(SAKURA, Math.round(28.0f * progress)), sakuraGlassSettings());

        // Right-edge sakura accent bar
        float barAlpha = enabled ? 0.78f : 0.35f;
        RenderServices.shapes().verticalGradient(right - 2.5f, y + 3.5f, right - 1.0f, y + rowH - 3.5f,
                withAlpha(SAKURA, Math.round(190.0f * barAlpha * progress)),
                withAlpha(SAKURA_STRONG, Math.round(140.0f * barAlpha * progress)));

        // Icon with sakura tint
        float iconX = x + iconSlotW / 2.0f + 2.0f;
        float iconY = y + rowH / 2.0f;
        int iconColor = enabled ? withAlpha(SAKURA, Math.round(228.0f * progress))
                : withAlpha(SAKURA_MUTED, Math.round(155.0f * progress));
        drawCenteredIcon(icon, FontLoaders.I16, iconX, iconY, iconColor);

        // Module name with glow
        float textX = x + iconSlotW + 2.0f;
        float textY = y + (rowH - font.getHeight()) / 2.0f + 1.0f;
        int nameColor = enabled ? withAlpha(SAKURA_TEXT, Math.round(242.0f * progress))
                : withAlpha(SAKURA_MUTED, Math.round(200.0f * progress));
        if (enabled) {
            drawTextGlow(font, label.name, textX, textY, progress * 0.55f);
        }
        font.drawString(label.name, textX, textY, nameColor);

        // Parameter and key in muted sakura
        float cursor = textX + font.getStringWidth(label.name);
        if (label.parameter.length() > 0) {
            cursor += 3.0f;
            font.drawString(label.parameter, cursor, textY,
                    withAlpha(SAKURA_MUTED, Math.round(170.0f * progress)));
            cursor += font.getStringWidth(label.parameter);
        }
        if (label.key.length() > 0) {
            cursor += 3.0f;
            font.drawString(label.key, cursor, textY,
                    withAlpha(SAKURA_MUTED, Math.round(148.0f * progress)));
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
        List<ModuleListEntry> entries = buildModuleListEntries(modules, font, 3.0f);
        entries.sort(new Comparator<ModuleListEntry>() {
            @Override
            public int compare(ModuleListEntry first, ModuleListEntry second) {
                return second.labelWidth - first.labelWidth;
            }
        });

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

    private void drawPotionEffects() {
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
            FontLoaders.C16.drawString("Effects", x + 12.0f, y + 10.0f, withAlpha(SAKURA_TEXT, 236));
            String count = String.valueOf(effects.size());
            float chipX = x + width - 38.0f;
            drawSakuraStatusChip(count, chipX, y + 8.0f, 28.0f, SAKURA);
            drawSakuraFlower(chipX + 7.5f, y + 15.0f, 4.0f, 1.0f);

            if (effects.isEmpty()) {
                FontLoaders.C14.drawString("No effects", x + 12.0f, y + 31.0f, withAlpha(SAKURA_MUTED, 210));
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

                    RenderServices.liquidGlass().roundedBorder(x + 8.0f, rowY, x + 25.0f, rowY + 17.0f, 5.0f, 0.5f,
                            withAlpha(0xFF160F15, 138), withAlpha(SAKURA, 30), sakuraGlassSettings());
                    RenderServices.shapes().circle(x + 16.5f, rowY + 8.5f, 0, 360, 3.2f, withAlpha(accent, 210));
                    FontLoaders.C14.drawString(name, x + 31.0f, rowY + 2.0f, withAlpha(SAKURA_TEXT, 226));
                    FontLoaders.C12.drawString(duration, x + 31.0f, rowY + 12.0f, withAlpha(SAKURA_MUTED, 210));
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
        RenderServices.shapes().shadow(x, y, x2, y2, radius,
                withAlpha(0xFF000000, Math.round(96.0f * alpha)), 8, 3.4f);
        RenderServices.shapes().shadow(x, y, x2, y2, radius,
                withAlpha(SAKURA, Math.round(34.0f * alpha)), 5, 2.2f);
        RenderServices.liquidGlass().roundedBorder(x, y, x2, y2, radius, 0.55f,
                withAlpha(SAKURA_GLASS, Math.round(getGlassAlpha() * alpha)),
                withAlpha(SAKURA, Math.round(34.0f * alpha)), sakuraGlassSettings());
        RenderServices.shapes().shadow(x + 12.0f, y + 5.0f, x2 - 12.0f, y2 - 5.0f,
                radius, withAlpha(SAKURA, Math.round(20.0f * alpha)), 3, 1.8f);
        RenderServices.shapes().rounded(x + 8.0f, y2 - 9.0f, Math.min(x2 - 8.0f, x + 72.0f), y2 - 4.0f,
                3.0f, withAlpha(SAKURA, Math.round(14.0f * alpha)));
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
            RenderServices.liquidGlass().roundedBorder(x, y, x + w, y + 14.0f, 5.0f, 0.5f,
                    withAlpha(0xFF160F15, 142), withAlpha(SAKURA, 34), sakuraGlassSettings());
        }
        FontLoaders.C14.drawString(trim(text, FontLoaders.C14, w - 17.0f), x + 15.0f, y + 4.0f,
                withAlpha(SAKURA_TEXT, 220));
    }

    private LiquidGlassSettings sakuraGlassSettings() {
        return LiquidGlassSettings.defaults()
                .withBlurRadius(18.0f)
                .withBlurDownscale(0.92f)
                .withNoise(0.018f)
                .withRefractionScale(1.16f)
                .withHighlight(1.05f);
    }

    private void drawTextGlow(CFontRenderer font, String text, float x, float y, float alpha) {
        int wideGlow = withAlpha(SAKURA, Math.round(28.0f * alpha));
        int nearGlow = withAlpha(0xFFFFBED8, Math.round(48.0f * alpha));
        font.drawString(text, x - 0.88f, y, wideGlow);
        font.drawString(text, x + 0.88f, y, wideGlow);
        font.drawString(text, x, y - 0.88f, wideGlow);
        font.drawString(text, x, y + 0.88f, wideGlow);
        font.drawString(text, x - 0.50f, y - 0.50f, nearGlow);
        font.drawString(text, x + 0.50f, y - 0.50f, nearGlow);
        font.drawString(text, x - 0.50f, y + 0.50f, nearGlow);
        font.drawString(text, x + 0.50f, y + 0.50f, nearGlow);
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
        float[][] points = new float[][]{
                {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
                {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
                {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f}, {0.00f, -0.18f}
        };

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(0xFFFFEAF3, alpha * 0.96f);
        GL11.glVertex2f(0.0f, length * 0.36f);
        for (float[] point : points) {
            glColor(SAKURA, alpha * 0.70f);
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();

        GL11.glLineWidth(0.75f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        glColor(0xFFFFF6FA, alpha * 0.45f);
        for (float[] point : points) {
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
            drawThemedFrostedGlass(x, y, x + w, y + 14.0f, 5.0f, 0.6f,
                    withAlpha(palette().glassSoft, getSoftAlpha()), withAlpha(accent, 48));
            RenderServices.shapes().circle(x + 7.5f, y + 7.0f, 0, 360, 2.0f, withAlpha(accent, 185));
        }
        FontLoaders.C14.drawString(trim(text, FontLoaders.C14, w - 16.0f), x + 14.0f, y + 4.0f,
                withAlpha(palette().text, 218));
    }

    private void drawGlass(float x, float y, float x2, float y2, float round, int fillAlpha, int borderAlpha) {
        HudRenderSupport.drawGlass(x, y, x2, y2, round, withAlpha(palette().shadowColor, 44),
                withAlpha(palette().glass, fillAlpha), withAlpha(palette().border, borderAlpha));
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
            RenderUtil.drawGlowAround(x, y, x2, y2, radius, glowColor, 1.0f);
        }
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

    private List<ModuleListEntry> buildModuleListEntries(List<Module> modules, CFontRenderer font, float gap) {
        ArrayList<ModuleListEntry> entries = new ArrayList<ModuleListEntry>(modules.size());
        int roundedGap = Math.round(gap);
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
        return entries;
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
        return selected == null ? HudStyle.VAPULITE : selected;
    }

    private boolean useVapeStyle() {
        return getSelectedStyle() == HudStyle.VAPE;
    }

    public static HudStyle getActiveStyle() {
        if (instance != null) {
            activeStyle = instance.getSelectedStyle();
        }
        return activeStyle == null ? HudStyle.VAPULITE : activeStyle;
    }

    public static boolean useVapeSimpleStyle() {
        return getActiveStyle() == HudStyle.VAPE;
    }

    public static boolean isGlowEnabled() {
        if (instance != null && instance.glow != null) {
            return Boolean.TRUE.equals(instance.glow.getValue());
        }
        return false;
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

    private static String trim(String text, gq.vapulite.engine.font.CFontRenderer font, float maxWidth) {
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
