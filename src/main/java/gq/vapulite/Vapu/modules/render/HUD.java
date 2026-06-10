package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.Manager.NotificationManager;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.VapeClickGui.ClickGuiIcons;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.HudDrag;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.Vapu.value.Value;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.ShaderRenderer;
import gq.vapulite.utils.GuiRenderUtils;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HUD extends Module {
    public enum HudStyle {
        VAPULITE,
        VAPE
    }

    private static final int TEXT = 0xFFE8EAEC;
    private static final int MUTED = 0xFF9EA8B8;
    private static final int GLASS = 0xFF07090D;
    private static final int GLASS_SOFT = 0xFF0A0D12;
    private static final int BORDER = 0xFF8DBED8;
    private static final int ACCENT = 0xFF70C1DC;
    private static final int ACCENT_ALT = 0xFF8B7CFF;
    private static final int VAPE_PRIMARY = 0xFF7C9DFF;
    private static final int VAPE_SECONDARY = 0xFF838CEF;
    private static final int VAPE_TERTIARY = 0xFF5AD4FF;
    private static final int VAPE_SURFACE = 0xFF171A20;
    private static final int VAPE_SURFACE_VARIANT = 0xFF1E222B;
    private static final int VAPE_ON_SURFACE = 0xFFFFFFFF;
    private static final int VAPE_ON_VARIANT = 0xFFAAB2C5;
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
        this.addValues(hudStyle, watermark, arrayList, backgrounds, keybinds, parameters, notifications,
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
        ShaderRenderer.invalidateFrostedGlass();

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

        String title = Client.name;
        String meta = Client.version + "  |  " + Minecraft.getDebugFPS() + " FPS";
        int ping = getPing();
        if (ping >= 0) {
            meta += "  |  " + ping + " ms";
        }

        int enabled = getEnabledCount();
        String modules = enabled + "/" + ModuleManager.getModules().size() + " modules";

        float round = getRadius();
        float iconSize = 25.0f;
        float titleWidth = FontLoaders.C20.getStringWidth(title);
        float metaWidth = FontLoaders.C14.getStringWidth(meta);
        float modulesWidth = FontLoaders.C14.getStringWidth(modules) + 18.0f;
        float boxW = Math.max(164.0f, Math.max(titleWidth + iconSize + 46.0f, metaWidth + modulesWidth + 42.0f));
        float boxH = 43.0f;
        float uiScale = getScale(watermarkScale);
        ScaledResolution sr = new ScaledResolution(mc);
        float[] pos = HudDrag.update("hud_watermark", watermarkX, watermarkY, watermarkScale, 6.0f, 6.0f,
                boxW * uiScale, boxH * uiScale, sr);
        float x = pos[0];
        float y = pos[1];

        beginScaled(x, y, uiScale);
        try {
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
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_watermark", x, y, boxW * uiScale, boxH * uiScale, round * uiScale);
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
                RenderUtil.drawHorizontalGradientRect(x + 1.0f, y + 1.0f, x + boxW - 1.0f, y + 18.0f,
                        withAlpha(0xFFFFFFFF, 16), withAlpha(0xFF000000, 0));
                RenderUtil.drawRoundedRect(x + 10.0f, y + 7.0f, x + 10.0f + iconSize,
                        y + 7.0f + iconSize, 8.0f, withAlpha(VAPE_SURFACE_VARIANT, 235));
                RenderUtil.drawRoundedBorderedRect(x + 10.0f, y + 7.0f, x + 10.0f + iconSize,
                        y + 7.0f + iconSize, 8.0f, 0.8f, 0x00000000,
                        withAlpha(0xFFFFFFFF, 24));
                RenderUtil.drawCircle(x + boxW - 16.0f, y + 18.0f, 0, 360, 4.0f,
                        withAlpha(VAPE_SECONDARY, 245));
            }
            FontLoaders.C30.drawString("M", x + 18.0f, y + 14.0f, withAlpha(VAPE_PRIMARY, 245));
            titleFont.drawString(Client.name, x + 60.0f, y + 13.0f, withAlpha(VAPE_ON_SURFACE, 248));
            smallFont.drawString(meta, x + 60.0f, y + 31.0f, withAlpha(VAPE_ON_VARIANT, 226));
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_watermark", x, y, boxW * uiScale, boxH * uiScale, 7.0f * uiScale);
    }

    private void drawVapeTextChip(String text, float x, float y, float width, int accent) {
        RenderUtil.drawRoundedRect(x, y, x + width, y + 15.0f, 4.0f, withAlpha(VAPE_SURFACE_VARIANT, 185));
        RenderUtil.drawRoundedRect(x, y + 13.0f, x + width, y + 15.0f, 1.0f, withAlpha(accent, 165));
        FontLoaders.C12.drawString(text, x + (width - FontLoaders.C12.getStringWidth(text)) / 2.0f,
                y + 4.0f, withAlpha(VAPE_ON_SURFACE, 232));
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
                RenderUtil.drawRoundedRect(x, y, x + 2.0f, y + height, 1.0f,
                        withAlpha(VAPE_TERTIARY, 188));
            }
            FontLoaders.C16.drawString("Effects", x + 11.0f, y + 8.0f, withAlpha(VAPE_ON_SURFACE, 242));
            String count = String.valueOf(effects.size());
            drawVapeTextChip(count, x + width - FontLoaders.C12.getStringWidth(count) - 19.0f, y + 7.0f,
                    FontLoaders.C12.getStringWidth(count) + 14.0f, VAPE_PRIMARY);
            if (effects.isEmpty()) {
                FontLoaders.C12.drawString("No active effects", x + 12.0f, y + 34.0f,
                        withAlpha(VAPE_ON_VARIANT, 210));
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
                        RenderUtil.drawRoundedRect(x + 8.0f, rowY + 1.0f, x + width - 8.0f,
                                rowY + rowH - 2.0f, 4.0f, withAlpha(VAPE_SURFACE_VARIANT, 112));
                    }
                    RenderUtil.drawCircle(x + 17.0f, rowY + 8.0f, 0, 360, 3.4f, accent);
                    FontLoaders.C12.drawString(name, x + 26.0f, rowY + 4.0f, withAlpha(VAPE_ON_SURFACE, 230));
                    FontLoaders.C12.drawString(duration, x + width - FontLoaders.C12.getStringWidth(duration) - 12.0f,
                            rowY + 4.0f, withAlpha(VAPE_ON_VARIANT, 215));
                    float progress = Math.max(0.08f, Math.min(1.0f, effect.getDuration() / 1200.0f));
                    RenderUtil.drawProgressBar(x + 26.0f, rowY + 14.0f, x + width - 12.0f, rowY + 15.6f,
                            0.8f, progress, withAlpha(0xFFFFFFFF, 18), withAlpha(accent, 185));
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_potions", x, y, width * uiScale, height * uiScale, 6.0f * uiScale);
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
            drawGlass(x, y, x + width, y + height, getRadius(), getGlassAlpha(), 48);
            RenderUtil.drawHorizontalGradientRect(x + 9.0f, y + 4.0f, x + width - 9.0f, y + 5.1f,
                    withAlpha(ACCENT_ALT, 105), withAlpha(ACCENT, 105));
            FontLoaders.C16.drawString("Inventory", x + 10.0f, y + 10.0f, withAlpha(TEXT, 236));

            int filled = countInventoryItems();
            String count = filled + "/27";
            drawStatusChip(count, x + width - FontLoaders.C14.getStringWidth(count) - 27.0f, y + 8.0f,
                    FontLoaders.C14.getStringWidth(count) + 18.0f, ACCENT_ALT);

            float startX = x + 8.0f;
            float startY = y + 28.0f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = 9 + row * 9 + col;
                    float slotX = startX + col * stride;
                    float slotY = startY + row * stride;
                    RenderUtil.drawRoundedRect(slotX - 1.0f, slotY - 1.0f, slotX + slot + 1.0f, slotY + slot + 1.0f,
                            4.0f, withAlpha(GLASS_SOFT, getSoftAlpha()));
                    RenderUtil.drawRoundedBorderedRect(slotX - 1.0f, slotY - 1.0f, slotX + slot + 1.0f,
                            slotY + slot + 1.0f, 4.0f, 0.7f, 0x00000000,
                            withAlpha(BORDER, 36));
                    drawItemStack(mc.thePlayer.inventory.mainInventory[index], slotX, slotY);
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_inventory", x, y, width * uiScale, height * uiScale, getRadius() * uiScale);
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
                RenderUtil.drawRoundedRect(x, y, x + 2.0f, y + height, 1.0f,
                        withAlpha(VAPE_SECONDARY, 190));
            }
            FontLoaders.C16.drawString("Inventory", x + 11.0f, y + 8.0f, withAlpha(VAPE_ON_SURFACE, 242));
            String count = filled + "/27";
            drawVapeTextChip(count, x + width - FontLoaders.C12.getStringWidth(count) - 21.0f, y + 7.0f,
                    FontLoaders.C12.getStringWidth(count) + 14.0f, VAPE_SECONDARY);

            float startX = x + 13.0f;
            float startY = y + 30.0f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = 9 + row * 9 + col;
                    float slotX = startX + col * stride;
                    float slotY = startY + row * stride;
                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        int fill = ((row + col) & 1) == 0
                                ? withAlpha(VAPE_SURFACE_VARIANT, 154)
                                : withAlpha(0xFF151922, 144);
                        RenderUtil.drawRoundedBorderedRect(slotX - 1.0f, slotY - 1.0f, slotX + slot + 1.0f,
                                slotY + slot + 1.0f, 4.0f, 0.6f, fill, withAlpha(0xFFFFFFFF, 24));
                    }
                    drawItemStack(mc.thePlayer.inventory.mainInventory[index], slotX, slotY);
                }
            }
            float progress = Math.min(1.0f, filled / 27.0f);
            RenderUtil.drawProgressBar(x + 13.0f, y + height - 7.0f, x + width - 13.0f, y + height - 4.8f,
                    1.1f, progress, withAlpha(0xFFFFFFFF, 20), withAlpha(VAPE_PRIMARY, 218));
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_inventory", x, y, width * uiScale, height * uiScale, 6.0f * uiScale);
    }

    private void drawModuleList(int screenWidth, int screenHeight, float factor) {
        List<Module> modules = getHudModules();
        moduleAnimations.keySet().retainAll(modules);
        if (hudStyle.getValue() == HudStyle.VAPE) {
            drawVapeModuleList(screenWidth, screenHeight, factor, modules);
            return;
        }

        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                return getModuleLabelWidth(getModuleListLabel(second), FontLoaders.C18, 4.0f)
                        - getModuleLabelWidth(getModuleListLabel(first), FontLoaders.C18, 4.0f);
            }
        });

        float listW = 88.0f;
        int visibleRows = 0;
        for (Module module : modules) {
            ModuleListLabel label = getModuleListLabel(module);
            int textW = getModuleLabelWidth(label, FontLoaders.C18, 4.0f);
            listW = Math.max(listW, textW + 39.0f);
            visibleRows++;
            if (visibleRows > 22) {
                break;
            }
        }
        if (modules.isEmpty() && !HudDrag.isEditMode()) {
            return;
        }
        float rowH = 18.0f;
        float listH = modules.isEmpty() ? 20.0f : Math.min(23, Math.max(1, visibleRows)) * (rowH + 3.0f) - 3.0f;
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
                drawGlass(pos[0], y, pos[0] + listW, y + listH, round, getGlassAlpha(), 42);
                FontLoaders.C14.drawString("Module List", pos[0] + 10.0f, y + 7.0f, withAlpha(MUTED, 220));
            } finally {
                endScaled();
            }
            HudDrag.drawHint("hud_module_list", pos[0], y, listW * uiScale, listH * uiScale, round * uiScale);
            return;
        }
        beginScaled(pos[0], y, uiScale);
        try {
            for (Module module : modules) {
                ModuleListLabel label = getModuleListLabel(module);
                float progress = animateModule(module, factor);
                if (progress <= 0.01f) {
                    continue;
                }

                int textW = getModuleLabelWidth(label, FontLoaders.C18, 4.0f);
                String icon = ClickGuiIcons.forModule(module);
                float iconSlotW = 22.0f;
                float rowW = textW + iconSlotW + 17.0f;
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
                    drawModuleLabel(label, FontLoaders.C18, x + iconSlotW + 6.0f, y + 5.0f,
                            withAlpha(TEXT, Math.round(242.0f * progress)),
                            withAlpha(MUTED, Math.round(216.0f * progress)), 4.0f);
                } else {
                    FontLoaders.C18.drawString(label.fullText(), right - textW, y + 4.0f,
                            withAlpha(accent, Math.round(245.0f * progress)));
                }

                y += rowH + 3.0f;
                index++;
                if (index > 22) {
                    break;
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_module_list", pos[0], pos[1], listW * uiScale, listH * uiScale, round * uiScale);
    }

    private void drawVapeModuleList(int screenWidth, int screenHeight, float factor, List<Module> modules) {
        final CFontRenderer font = FontLoaders.TB14;
        final float rowH = 24.0f;
        final float lineW = 2.4f;
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                return getModuleLabelWidth(getModuleListLabel(second), font, 3.0f)
                        - getModuleLabelWidth(getModuleListLabel(first), font, 3.0f);
            }
        });

        int visibleRows = 0;
        float listW = 134.0f;
        for (Module module : modules) {
            ModuleListLabel label = getModuleListLabel(module);
            String sideText = getModuleSideText(label);
            int sideW = sideText.length() == 0 ? 0 : font.getStringWidth(sideText);
            listW = Math.max(listW, font.getStringWidth(label.name) + sideW + 34.0f + lineW);
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
                RenderUtil.drawVerticalGradientRect(lineX, y + 5.0f, lineX + lineW, y + listH - 5.0f,
                        withAlpha(VAPE_PRIMARY, 230), withAlpha(VAPE_TERTIARY, 190));
            }
            if (modules.isEmpty()) {
                float textX = rightSide
                        ? x + listW - lineW - font.getStringWidth("Module List") - 12.0f
                        : x + lineW + 12.0f;
                font.drawString("Module List", textX, y + 7.0f, withAlpha(VAPE_ON_VARIANT, 225));
            } else {
                int index = 0;
                float drawY = y;
                for (Module module : modules) {
                    if (index >= 12) {
                        break;
                    }
                    ModuleListLabel label = getModuleListLabel(module);
                    float progress = animateModule(module, factor);
                    if (progress <= 0.01f) {
                        continue;
                    }
                    float rowTop = drawY;
                    float rowBottom = drawY + rowH;
                    int rowAlpha = Math.round((index == 0 ? 44.0f : 26.0f) * progress);
                    if (Boolean.TRUE.equals(backgrounds.getValue())) {
                        RenderUtil.drawRect(x + 4.0f, rowTop, x + listW - 4.0f, rowBottom,
                                withAlpha(VAPE_SURFACE_VARIANT, rowAlpha));
                        if (index > 0) {
                            RenderUtil.drawRect(x + 8.0f, rowTop, x + listW - 8.0f, rowTop + 0.6f,
                                    withAlpha(0xFFFFFFFF, Math.round(18.0f * progress)));
                        }
                        float pulseLineX = rightSide ? x + listW - lineW : x;
                        RenderUtil.drawRect(pulseLineX, rowTop + 4.0f, pulseLineX + lineW, rowBottom - 4.0f,
                                withAlpha(getCategoryAccent(module), Math.round((150.0f + index * 4.0f) * progress)));
                    }
                    String sideText = getModuleSideText(label);
                    float contentLeft = x + (rightSide ? 11.0f : lineW + 12.0f);
                    float contentRight = x + listW - (rightSide ? lineW + 12.0f : 11.0f);
                    float sideW = sideText.length() == 0 ? 0.0f : font.getStringWidth(sideText);
                    String name = trim(label.name, font, contentRight - contentLeft - sideW - 8.0f);
                    font.drawString(name, contentLeft, drawY + 7.0f,
                            withAlpha(0xFFFFFFFF, Math.round(246.0f * progress)));
                    if (sideText.length() > 0) {
                        font.drawString(sideText, contentRight - sideW, drawY + 7.0f,
                                withAlpha(VAPE_SECONDARY, Math.round(238.0f * progress)));
                    }
                    drawY += rowH;
                    index++;
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_module_list", pos[0], pos[1], listW * uiScale, listH * uiScale, 6.0f * uiScale);
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
            drawGlass(x, y, x + width, y + height, getRadius(), getGlassAlpha(), 48);
            RenderUtil.drawHorizontalGradientRect(x + 9.0f, y + 4.0f, x + width - 9.0f, y + 5.1f,
                    withAlpha(ACCENT, 120), withAlpha(ACCENT_ALT, 86));
            FontLoaders.C16.drawString("Effects", x + 12.0f, y + 10.0f, withAlpha(TEXT, 236));
            drawStatusChip(String.valueOf(effects.size()), x + width - 38.0f, y + 8.0f, 28.0f, ACCENT);

            if (effects.isEmpty()) {
                FontLoaders.C14.drawString("No effects", x + 12.0f, y + 31.0f, withAlpha(MUTED, 210));
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

                    RenderUtil.drawFrostedGlassRect(x + 8.0f, rowY, x + 25.0f, rowY + 17.0f, 5.0f, 0.7f,
                            withAlpha(GLASS_SOFT, getSoftAlpha()), withAlpha(accent, 68));
                    RenderUtil.drawCircle(x + 16.5f, rowY + 8.5f, 0, 360, 3.2f, withAlpha(accent, 210));
                    FontLoaders.C14.drawString(name, x + 31.0f, rowY + 2.0f, withAlpha(TEXT, 226));
                    FontLoaders.C12.drawString(duration, x + 31.0f, rowY + 12.0f, withAlpha(MUTED, 210));
                }
            }
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_potions", x, y, width * uiScale, height * uiScale, getRadius() * uiScale);
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

    private void drawVapeCard(float x, float y, float x2, float y2, float radius, int alpha) {
        RenderUtil.drawSoftShadow(x, y, x2, y2, radius, withAlpha(0xFF000000, 58), 6, 2.4f);
        RenderUtil.drawRoundedBorderedRect(x, y, x2, y2, radius, 0.8f,
                withAlpha(VAPE_SURFACE, alpha), withAlpha(0xFFFFFFFF, 24));
        RenderUtil.drawHorizontalGradientRect(x + 1.0f, y + 1.0f, x2 - 1.0f,
                Math.min(y2 - 1.0f, y + 18.0f), withAlpha(0xFFFFFFFF, 14), withAlpha(0xFF000000, 0));
    }

    private void drawGlowIfEnabled(float x, float y, float x2, float y2, float radius, int glowColor) {
        if (Boolean.TRUE.equals(glow.getValue()) && Boolean.TRUE.equals(backgrounds.getValue())) {
            GuiRenderUtils.drawGlowAround(x, y, x2, y2, radius, glowColor, 1.0f);
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
