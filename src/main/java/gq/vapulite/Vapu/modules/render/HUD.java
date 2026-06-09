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
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.font.CFontRenderer;
import gq.vapulite.font.FontLoaders;
import gq.vapulite.render.ShaderRenderer;
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
    private final Option<Boolean> potionEffects = new Option<Boolean>("PotionEffects", "PotionEffects", true);
    private final Option<Boolean> inventoryDisplay = new Option<Boolean>("Inventory", "Inventory", true);
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
        this.addValues(watermark, arrayList, backgrounds, keybinds, notifications, potionEffects, inventoryDisplay,
                alpha, radius, watermarkX, watermarkY, watermarkScale, moduleListX, moduleListY, moduleListScale,
                potionX, potionY, potionScale, inventoryX, inventoryY, inventoryScale);
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

    private void drawModuleList(int screenWidth, int screenHeight, float factor) {
        List<Module> modules = getHudModules();
        moduleAnimations.keySet().retainAll(modules);
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                return FontLoaders.C18.getStringWidth(getDisplayName(second))
                        - FontLoaders.C18.getStringWidth(getDisplayName(first));
            }
        });

        float listW = 88.0f;
        int visibleRows = 0;
        for (Module module : modules) {
            String displayName = getModuleListName(module);
            int textW = FontLoaders.C18.getStringWidth(displayName);
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
                String displayName = getModuleListName(module);
                float progress = animateModule(module, factor);
                if (progress <= 0.01f) {
                    continue;
                }

                int textW = FontLoaders.C18.getStringWidth(displayName);
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
        } finally {
            endScaled();
        }
        HudDrag.drawHint("hud_module_list", pos[0], pos[1], listW * uiScale, listH * uiScale, round * uiScale);
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

    private void drawInventory(int screenWidth, int screenHeight) {
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

    private String getModuleListName(Module module) {
        String displayName = getDisplayName(module);
        if (Boolean.TRUE.equals(keybinds.getValue()) && module.getKey() != Keyboard.KEY_NONE) {
            displayName += "  " + Keyboard.getKeyName(module.getKey());
        }
        return displayName;
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

    private float getScale(Numbers<Double> value) {
        return value == null || value.getValue() == null ? 1.0f : Math.max(0.1f, value.getValue().floatValue());
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
