package gq.yozakura.ui.click.sakura;

import gq.yozakura.core.Client;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.ui.click.ClickGuiIcons;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class SakuraClickGui extends GuiScreen {
    private static final int TEXT = 0xFFF5F0F5;
    private static final int MUTED = 0xFFB8AEB8;
    private static final int FAINT = 0xFF7D717C;
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_STRONG = 0xFFFF80B3;
    private static final int GLASS = 0xFF08080D;
    private static final int GLASS_SOFT = 0xFF160F15;

    private static ModuleType currentType = ModuleType.Combat;
    private static Module selectedModule;
    private static final Map<ModuleType, Float> listScrollByType = new HashMap<ModuleType, Float>();
    private static final Map<Module, Float> detailScrollByModule = new HashMap<Module, Float>();
    private final Map<ModuleType, Float> typeHoverProgress = new HashMap<ModuleType, Float>();
    private final Map<Module, Float> moduleHoverProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> moduleToggleProgress = new HashMap<Module, Float>();
    private final Map<Value, Float> valueHoverProgress = new HashMap<Value, Float>();
    private final Map<Numbers, Float> numberProgress = new HashMap<Numbers, Float>();
    private final Map<Mode, Float> modeExpandProgress = new HashMap<Mode, Float>();
    private final Map<ModeProperty, Float> modePropertyExpandProgress = new HashMap<ModeProperty, Float>();
    private final Map<Option, SwitchAnim> optionSwitchProgress = new HashMap<Option, SwitchAnim>();

    private float x;
    private float y;
    private float w;
    private float h;
    private float scale = 1.0f;
    private float openProgress;
    private float guiAlpha;
    private boolean draggingWindow;
    private float dragOffsetX;
    private float dragOffsetY;
    private float listScroll;
    private float listScrollDisplay;
    private float detailScroll;
    private float detailScrollDisplay;
    private Numbers draggingNumber;
    private float draggingX;
    private float draggingW;
    private double draggingMin;
    private double draggingMax;
    private Module bindingModule;
    private Mode expandedMode;
    private ModeProperty expandedModeProperty;
    private Module detailAnimationModule;
    private float detailProgress;
    private long lastFrameNanos = System.nanoTime();
    private float frameScale = 1.0f;

    private static final class SwitchAnim {
        boolean state;
        boolean initialized;
        float progress;
    }

    @Override
    public void initGui() {
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        openProgress = 0.0f;
        listScroll = scrollForType(currentType);
        listScrollDisplay = listScroll;
        detailScroll = scrollForModule(selectedModule);
        detailScrollDisplay = detailScroll;
        detailAnimationModule = selectedModule;
        detailProgress = selectedModule == null ? 0.0f : 1.0f;
        bindingModule = null;
        draggingNumber = null;
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateFrameScale();
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        updateDrag(sr, mouseX, mouseY);
        updateNumberDrag(mouseX);
        updateScrollAnimations();
        openProgress = 1.0f;
        guiAlpha = openProgress * ClickGUI.clickGuiAlpha.getValue().floatValue();

        ShaderRenderer.invalidateFrostedGlass();
        drawBackdrop(sr);
        drawShell(mouseX, mouseY);
        drawSidebar(mouseX, mouseY);
        drawModuleList(mouseX, mouseY);
        drawDetail(mouseX, mouseY);
        drawFlyingPetals(sr);
        drawBindingOverlay(sr);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void updateLayout(ScaledResolution sr) {
        scale = sr.getScaledWidth() < 760 ? 0.86f : sr.getScaledWidth() < 980 ? 0.94f : 1.0f;
        w = Math.min(sr.getScaledWidth() - 24.0f, 760.0f * scale);
        h = Math.min(sr.getScaledHeight() - 24.0f, 430.0f * scale);
        float defaultX = (sr.getScaledWidth() - w) * 0.5f;
        float defaultY = (sr.getScaledHeight() - h) * 0.5f;
        x = ClickGUI.windowX.getValue() == null || ClickGUI.windowX.getValue() < 0.0D
                ? defaultX : ClickGUI.windowX.getValue().floatValue();
        y = ClickGUI.windowY.getValue() == null || ClickGUI.windowY.getValue() < 0.0D
                ? defaultY : ClickGUI.windowY.getValue().floatValue();
        x = clamp(x, 8.0f, Math.max(8.0f, sr.getScaledWidth() - w - 8.0f));
        y = clamp(y, 8.0f, Math.max(8.0f, sr.getScaledHeight() - h - 8.0f));
    }

    private void drawBackdrop(ScaledResolution sr) {
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(),
                alpha(0xFF030306, 112.0f * guiAlpha));
        RenderServices.shapes().gradient(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(),
                alpha(0x33120A10, 55.0f * guiAlpha), alpha(0x66000000, 96.0f * guiAlpha));
    }

    private void drawShell(int mouseX, int mouseY) {
        pushScale();
        try {
            float sx = unscaleX(x);
            float sy = unscaleY(y + introOffset());
            float sw = w / scale;
            float sh = h / scale;
            drawSakuraPanel(sx, sy, sx + sw, sy + sh, 11.0f, 1.0f);
            RenderServices.shapes().rounded(sx + 9.0f, sy + sh - 11.0f,
                    sx + Math.min(sw - 9.0f, 92.0f), sy + sh - 5.0f, 3.0f,
                    alpha(SAKURA, 16.0f * guiAlpha));
            drawSakuraFlower(sx + 28.0f, sy + 28.0f, 6.6f, 1.0f);
            drawGlowText(FontLoaders.C20, "Sakura", sx + 48.0f, sy + 17.0f,
                    alpha(TEXT, 248.0f * guiAlpha), 0.82f);
            drawGlowText(FontLoaders.C14, "ClickGUI", sx + 48.0f, sy + 35.0f,
                    alpha(MUTED, 210.0f * guiAlpha), 0.46f);
            drawChip(ModuleManager.getEnabledModules().size() + "/" + ModuleManager.getModules().size(),
                    sx + sw - 76.0f, sy + 18.0f, 58.0f);
        } finally {
            popScale();
        }
    }

    private void drawSidebar(int mouseX, int mouseY) {
        pushScale();
        try {
            float sx = unscaleX(x);
            float sy = unscaleY(y + introOffset());
            float rowX = sx + 15.0f;
            float rowY = sy + 64.0f;
            ModuleType[] types = ModuleType.values();
            for (int i = 0; i < types.length; i++) {
                ModuleType type = types[i];
                boolean selected = type == currentType;
                boolean hovered = isHovered(rowX, rowY, rowX + 114.0f, rowY + 28.0f, mouseX, mouseY);
                float hover = animateMap(typeHoverProgress, type, hovered ? 1.0f : 0.0f, 0.20f);
                float active = selected ? 1.0f : 0.0f;
                float intensity = Math.max(hover, active);
                if (intensity > 0.01f) {
                    RenderServices.shapes().shadow(rowX, rowY, rowX + 114.0f, rowY + 28.0f,
                            7.0f, alpha(SAKURA, (18.0f + 36.0f * active + 24.0f * hover) * guiAlpha), 4, 1.8f);
                    drawMiniGlass(rowX, rowY, rowX + 114.0f, rowY + 28.0f, 7.0f,
                            alpha(selected ? 0xFF26151F : 0xFF160E15,
                                    (128.0f + 60.0f * active + 34.0f * hover) * guiAlpha),
                            alpha(selected ? SAKURA_STRONG : SAKURA,
                                    (34.0f + 62.0f * active + 34.0f * hover) * guiAlpha));
                }
                String icon = ClickGuiIcons.forType(type);
                drawGlowIcon(FontLoaders.I16, icon, rowX + 10.0f + ClickGuiIcons.visualOffsetX(icon),
                        rowY + 9.0f + ClickGuiIcons.visualOffsetY(icon),
                        alpha(selected ? SAKURA_STRONG : blend(MUTED, SAKURA, hover),
                                (188.0f + 52.0f * active + 32.0f * hover) * guiAlpha),
                        0.30f + 0.38f * active + 0.24f * hover);
                drawGlowText(FontLoaders.C14, type.getName(), rowX + 32.0f, rowY + 8.0f,
                        alpha(selected ? TEXT : blend(MUTED, TEXT, hover * 0.55f),
                                (190.0f + 50.0f * active + 24.0f * hover) * guiAlpha),
                        0.36f + 0.42f * active + 0.28f * hover);
                rowY += 34.0f;
            }
            drawGlowText(FontLoaders.C12, "Right click module to bind", sx + 16.0f, sy + h / scale - 22.0f,
                    alpha(FAINT, 180.0f * guiAlpha), 0.25f);
        } finally {
            popScale();
        }
    }

    private void drawModuleList(int mouseX, int mouseY) {
        pushScale();
        try {
            float sx = unscaleX(x);
            float sy = unscaleY(y + introOffset());
            float listX = sx + 144.0f;
            float listY = sy + 64.0f;
            float listW = 214.0f;
            float listH = h / scale - 86.0f;
            drawSakuraPanel(listX, listY, listX + listW, listY + listH, 8.0f, 0.92f);
            drawGlowText(FontLoaders.C16, currentType.getName(), listX + 14.0f, listY + 12.0f,
                    alpha(TEXT, 235.0f * guiAlpha), 0.62f);
            beginScissorScaled(listX, listY + 36.0f, listW, listH - 44.0f);
            float rowY = listY + 40.0f - listScrollDisplay;
            List<Module> modules = ModuleManager.getModulesInType(currentType);
            for (Module module : modules) {
                drawModuleRow(module, listX + 10.0f, rowY, listW - 20.0f, mouseX, mouseY);
                rowY += 42.0f;
            }
            endScissor();
        } finally {
            popScale();
        }
    }

    private void drawModuleRow(Module module, float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean selected = module == selectedModule;
        boolean enabled = module.getState();
        boolean hovered = isHovered(rowX, rowY, rowX + rowW, rowY + 34.0f, mouseX, mouseY);
        float hover = animateMap(moduleHoverProgress, module, hovered ? 1.0f : 0.0f, 0.20f);
        float toggle = animateStateMap(moduleToggleProgress, module, enabled ? 1.0f : 0.0f, 0.18f);
        float selectedEase = selected ? 1.0f : 0.0f;
        float rowIntensity = Math.max(Math.max(hover, toggle), selectedEase);
        if (rowIntensity > 0.01f) {
            RenderServices.shapes().shadow(rowX, rowY, rowX + rowW, rowY + 34.0f,
                    7.0f, alpha(SAKURA, (14.0f + 32.0f * hover + 34.0f * selectedEase + 26.0f * toggle) * guiAlpha),
                    4, 1.8f);
            drawMiniGlass(rowX, rowY, rowX + rowW, rowY + 34.0f, 7.0f,
                    alpha(selected ? 0xFF26151F : 0xFF160E15,
                            (118.0f + 72.0f * selectedEase + 42.0f * hover + 28.0f * toggle) * guiAlpha),
                    alpha(toggle > 0.02f || selected ? SAKURA_STRONG : SAKURA,
                            (30.0f + 46.0f * toggle + 34.0f * hover + 28.0f * selectedEase) * guiAlpha));
        }
        String icon = ClickGuiIcons.forModule(module);
        drawGlowIcon(FontLoaders.I16, icon, rowX + 10.0f + ClickGuiIcons.visualOffsetX(icon),
                rowY + 11.0f + ClickGuiIcons.visualOffsetY(icon),
                alpha(toggle > 0.5f || selected ? SAKURA : MUTED, (190.0f + 48.0f * toggle + 22.0f * hover) * guiAlpha),
                0.28f + 0.28f * rowIntensity);
        drawGlowText(FontLoaders.C14, displayName(module), rowX + 31.0f, rowY + 7.0f,
                alpha(toggle > 0.5f || selected ? TEXT : blend(MUTED, TEXT, hover * 0.55f),
                        (210.0f + 30.0f * rowIntensity) * guiAlpha), 0.32f + 0.36f * rowIntensity);
        drawGlowText(FontLoaders.C12, keyName(module.getKey()), rowX + 31.0f, rowY + 21.0f,
                alpha(blend(FAINT, SAKURA, hover * 0.55f), (158.0f + 28.0f * hover) * guiAlpha), 0.22f + 0.2f * hover);
        float tx = rowX + rowW - 31.0f;
        if (hover > 0.02f || toggle > 0.02f) {
            RenderServices.shapes().shadow(tx, rowY + 10.0f, tx + 22.0f, rowY + 22.0f, 6.0f,
                    alpha(SAKURA, (18.0f + 38.0f * toggle + 22.0f * hover) * guiAlpha), 4, 1.6f);
        }
        RenderServices.shapes().rounded(tx, rowY + 10.0f, tx + 22.0f, rowY + 22.0f, 6.0f,
                alpha(blend(0xFF30262F, SAKURA, toggle), (190.0f + 28.0f * toggle) * guiAlpha));
        RenderServices.shapes().circle(tx + 6.0f + 10.0f * toggle, rowY + 16.0f, 0, 360, 4.2f,
                alpha(blend(MUTED, 0xFFFFF3FA, toggle), 238.0f * guiAlpha));
    }

    private void drawDetail(int mouseX, int mouseY) {
        pushScale();
        try {
            float sx = unscaleX(x);
            float sy = unscaleY(y + introOffset());
            float dx = sx + 372.0f;
            float dy = sy + 64.0f;
            float dw = w / scale - 388.0f;
            float dh = h / scale - 86.0f;
            drawSakuraPanel(dx, dy, dx + dw, dy + dh, 8.0f, 0.92f);
            if (selectedModule == null) {
                drawSakuraFlower(dx + dw * 0.5f, dy + dh * 0.5f - 15.0f, 8.0f, 0.82f);
                drawGlowCentered(FontLoaders.C16, "Select a module", dx + dw * 0.5f, dy + dh * 0.5f + 9.0f,
                        alpha(MUTED, 210.0f * guiAlpha), 0.38f);
                return;
            }
            if (detailAnimationModule != selectedModule) {
                detailAnimationModule = selectedModule;
                detailProgress = 0.0f;
            }
            detailProgress = 1.0f;
            float detailEase = detailProgress;
            GL11.glPushMatrix();
            float oldAlpha = guiAlpha;
            guiAlpha *= detailEase;
            drawDetailHeader(dx, dy, dw);
            beginScissorScaled(dx, dy + 70.0f, dw, dh - 78.0f);
            float valueY = dy + 78.0f - detailScrollDisplay;
            List<Value> values = selectedModule.getValues();
            if (values.isEmpty()) {
                drawGlowText(FontLoaders.C14, "No settings", dx + 16.0f, valueY,
                        alpha(MUTED, 205.0f * guiAlpha), 0.3f);
            }
            for (Value value : values) {
                if (!value.isVisible()) {
                    continue;
                }
                drawValue(value, dx + 16.0f, valueY, dw - 32.0f, mouseX, mouseY);
                valueY += valueHeight(value);
            }
            endScissor();
            guiAlpha = oldAlpha;
            GL11.glPopMatrix();
        } finally {
            popScale();
        }
    }

    private void drawDetailHeader(float dx, float dy, float dw) {
        String icon = ClickGuiIcons.forModule(selectedModule);
        drawMiniGlass(dx + 14.0f, dy + 14.0f, dx + 46.0f, dy + 46.0f, 8.0f,
                alpha(GLASS_SOFT, 168.0f * guiAlpha), alpha(SAKURA, 42.0f * guiAlpha));
        RenderServices.shapes().shadow(dx + 14.0f, dy + 14.0f, dx + 46.0f, dy + 46.0f, 8.0f,
                alpha(SAKURA, 24.0f * guiAlpha), 4, 1.8f);
        drawGlowIcon(FontLoaders.I18, icon, dx + 23.0f + ClickGuiIcons.visualOffsetX(icon),
                dy + 25.0f + ClickGuiIcons.visualOffsetY(icon), alpha(SAKURA, 234.0f * guiAlpha), 0.5f);
        drawGlowText(FontLoaders.C20, displayName(selectedModule), dx + 56.0f, dy + 16.0f,
                alpha(TEXT, 244.0f * guiAlpha), 0.72f);
        drawGlowText(FontLoaders.C14, trim(selectedModule.getDescription(), FontLoaders.C14, dw - 130.0f),
                dx + 56.0f, dy + 36.0f, alpha(MUTED, 196.0f * guiAlpha), 0.34f);
        drawChip(selectedModule.getState() ? "Enabled" : "Disabled", dx + dw - 82.0f, dy + 22.0f, 66.0f);
    }

    private void drawValue(Value value, float vx, float vy, float vw, int mouseX, int mouseY) {
        if (value instanceof Option) {
            drawOption((Option) value, vx, vy, vw, mouseX, mouseY);
        } else if (value instanceof ModeProperty) {
            drawModeProperty((ModeProperty) value, vx, vy, vw, mouseX, mouseY);
        } else if (value instanceof Numbers) {
            drawNumber((Numbers) value, vx, vy, vw, mouseX, mouseY);
        } else if (value instanceof Mode) {
            drawMode((Mode) value, vx, vy, vw, mouseX, mouseY);
        } else {
            drawGlowText(FontLoaders.C14, displayName(value), vx, vy + 7.0f, alpha(MUTED, 205.0f * guiAlpha), 0.28f);
            drawGlowText(FontLoaders.C14, String.valueOf(value.getValue()), vx + vw - 90.0f, vy + 7.0f,
                    alpha(TEXT, 220.0f * guiAlpha), 0.35f);
        }
    }

    private void drawOption(Option value, float vx, float vy, float vw, int mouseX, int mouseY) {
        boolean enabled = Boolean.TRUE.equals(value.getValue());
        float hover = animateValue(value, isHovered(vx, vy, vx + vw, vy + 30.0f, mouseX, mouseY) ? 1.0f : 0.0f);
        float active = animateOptionSwitch(value, enabled);
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy + 1.0f, vx + vw + 7.0f, vy + 31.0f,
                    7.0f, alpha(0xFF120D12, 70.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(value), vx, vy + 8.0f,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.32f * hover);
        float tx = vx + vw - 38.0f;
        if (hover > 0.02f || active > 0.02f) {
            RenderServices.shapes().shadow(tx, vy + 7.0f, tx + 34.0f, vy + 25.0f, 9.0f,
                    alpha(SAKURA, (18.0f + 38.0f * active + 20.0f * hover) * guiAlpha), 4, 1.6f);
        }
        RenderServices.shapes().rounded(tx, vy + 7.0f, tx + 34.0f, vy + 25.0f, 9.0f,
                alpha(blend(0xFF30262F, SAKURA, active), (190.0f + 28.0f * active + 18.0f * hover) * guiAlpha));
        RenderServices.shapes().circle(tx + 10.0f + 14.0f * active, vy + 16.0f, 0, 360, 6.0f,
                alpha(blend(MUTED, 0xFFFFF3FA, active), 238.0f * guiAlpha));
    }

    private void drawNumber(Numbers value, float vx, float vy, float vw, int mouseX, int mouseY) {
        double current = numberValue(value);
        float hover = animateValue(value, isHovered(vx, vy, vx + vw, vy + 42.0f, mouseX, mouseY) ? 1.0f : 0.0f);
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy - 5.0f, vx + vw + 7.0f, vy + 40.0f,
                    7.0f, alpha(0xFF120D12, 58.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(value), vx, vy,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.3f * hover);
        String text = formatNumber(current);
        drawGlowText(FontLoaders.C14, text, vx + vw - FontLoaders.C14.getStringWidth(text), vy,
                alpha(hover > 0.1f ? SAKURA : MUTED, (205.0f + 26.0f * hover) * guiAlpha), 0.3f + 0.38f * hover);
        float sx = vx;
        float sy = vy + 24.0f;
        float sw = vw;
        float pct = pct(current, value.getMinimum().doubleValue(), value.getMaximum().doubleValue());
        float displayPct = animateMap(numberProgress, value, pct, draggingNumber == value ? 0.34f : 0.18f);
        RenderServices.shapes().rounded(sx, sy, sx + sw, sy + 5.0f, 2.5f, alpha(0xFF30262F, 190.0f * guiAlpha));
        RenderServices.shapes().rounded(sx, sy, sx + sw * displayPct, sy + 5.0f, 2.5f, alpha(SAKURA, 238.0f * guiAlpha));
        if (hover > 0.02f) {
            RenderServices.shapes().shadow(sx + sw * displayPct - 6.0f, sy - 3.5f, sx + sw * displayPct + 6.0f, sy + 8.5f,
                    6.0f, alpha(SAKURA, 58.0f * hover * guiAlpha), 4, 2.0f);
        }
        RenderServices.shapes().circle(sx + sw * displayPct, sy + 2.5f, 0, 360, 5.6f, alpha(0xFFFFF3FA, 246.0f * guiAlpha));
    }

    private void drawMode(Mode mode, float vx, float vy, float vw, int mouseX, int mouseY) {
        float hover = animateValue(mode, isHovered(vx, vy, vx + vw, vy + 30.0f, mouseX, mouseY) ? 1.0f : 0.0f);
        boolean expanded = mode == expandedMode;
        float expand = animateMap(modeExpandProgress, mode, expanded ? 1.0f : 0.0f, 0.18f);
        boolean showDropdown = expanded;
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy + 1.0f, vx + vw + 7.0f, vy + 31.0f,
                    7.0f, alpha(0xFF120D12, 62.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(mode), vx, vy + 8.0f,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.3f * hover);
        String label = modeLabel(mode.getModeAsString());
        float pillW = Math.max(88.0f, FontLoaders.C14.getStringWidth(label) + 34.0f);
        float px = vx + vw - pillW;
        drawMiniGlass(px, vy + 5.0f, px + pillW, vy + 27.0f, 7.0f,
                alpha(GLASS_SOFT, (150.0f + 28.0f * hover + 30.0f * expand) * guiAlpha),
                alpha(SAKURA, (38.0f + 30.0f * hover + 42.0f * expand) * guiAlpha));
        drawGlowCentered(FontLoaders.C14, label, px + pillW * 0.5f - 5.0f, vy + 10.0f,
                alpha(SAKURA, (226.0f + 18.0f * hover) * guiAlpha), 0.42f + 0.36f * hover);
        drawGlowText(FontLoaders.C14, expanded ? "v" : ">", px + pillW - 14.0f, vy + 10.0f,
                alpha(TEXT, (198.0f + 34.0f * expand) * guiAlpha), 0.36f + 0.36f * expand);

        Enum[] modes = mode.getModes();
        if (modes == null || modes.length == 0 || !showDropdown) {
            return;
        }
        float rowH = 22.0f;
        float dropY = vy + 32.0f;
        float dropH = modes.length * rowH + 8.0f;
        RenderServices.shapes().shadow(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(SAKURA, 34.0f * guiAlpha), 4, 1.8f);
        drawMiniGlass(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(0xFF120D12, 178.0f * guiAlpha), alpha(SAKURA, 52.0f * guiAlpha));
        for (int i = 0; i < modes.length; i++) {
            Enum option = modes[i];
            String name = option.name();
            String optionLabel = modeLabel(name);
            float rowY = dropY + 4.0f + i * rowH;
            boolean selected = name.equalsIgnoreCase(mode.getModeAsString());
            boolean rowHover = isHovered(vx + 4.0f, rowY, vx + vw - 4.0f, rowY + rowH, mouseX, mouseY);
            float rowActive = selected ? 1.0f : rowHover ? 0.55f : 0.0f;
            if (rowActive > 0.01f) {
                RenderServices.shapes().rounded(vx + 5.0f, rowY + 2.0f, vx + vw - 5.0f, rowY + rowH - 2.0f,
                        6.0f, alpha(selected ? 0xFF26151F : 0xFF1B1218, (96.0f + 58.0f * rowActive) * expand * guiAlpha));
            }
            drawGlowText(FontLoaders.C14, optionLabel, vx + 12.0f, rowY + 7.0f,
                    alpha(selected ? TEXT : blend(MUTED, TEXT, rowHover ? 0.45f : 0.0f),
                            (190.0f + 42.0f * rowActive) * guiAlpha),
                    0.28f + 0.34f * rowActive);
            if (selected) {
                drawSakuraFlower(vx + vw - 14.0f, rowY + rowH * 0.5f, 2.0f, 0.75f);
            }
        }
    }

    private void drawModeProperty(ModeProperty mode, float vx, float vy, float vw, int mouseX, int mouseY) {
        float hover = animateValue(mode, isHovered(vx, vy, vx + vw, vy + 30.0f, mouseX, mouseY) ? 1.0f : 0.0f);
        boolean expanded = mode == expandedModeProperty;
        float expand = animateMap(modePropertyExpandProgress, mode, expanded ? 1.0f : 0.0f, 0.18f);
        boolean showDropdown = expanded;
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy + 1.0f, vx + vw + 7.0f, vy + 31.0f,
                    7.0f, alpha(0xFF120D12, 62.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(mode), vx, vy + 8.0f,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.3f * hover);
        String label = modeLabel(mode.getModeString());
        float pillW = Math.max(88.0f, FontLoaders.C14.getStringWidth(label) + 34.0f);
        float px = vx + vw - pillW;
        drawMiniGlass(px, vy + 5.0f, px + pillW, vy + 27.0f, 7.0f,
                alpha(GLASS_SOFT, (150.0f + 28.0f * hover + 30.0f * expand) * guiAlpha),
                alpha(SAKURA, (38.0f + 30.0f * hover + 42.0f * expand) * guiAlpha));
        drawGlowCentered(FontLoaders.C14, label, px + pillW * 0.5f - 5.0f, vy + 10.0f,
                alpha(SAKURA, (226.0f + 18.0f * hover) * guiAlpha), 0.42f + 0.36f * hover);
        drawGlowText(FontLoaders.C14, expanded ? "v" : ">", px + pillW - 14.0f, vy + 10.0f,
                alpha(TEXT, (198.0f + 34.0f * expand) * guiAlpha), 0.36f + 0.36f * expand);

        String[] modes = mode.getModes();
        if (modes == null || modes.length == 0 || !showDropdown) {
            return;
        }
        float rowH = 22.0f;
        float dropY = vy + 32.0f;
        float dropH = modes.length * rowH + 8.0f;
        RenderServices.shapes().shadow(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(SAKURA, 34.0f * guiAlpha), 4, 1.8f);
        drawMiniGlass(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(0xFF120D12, 178.0f * guiAlpha), alpha(SAKURA, 52.0f * guiAlpha));
        for (int i = 0; i < modes.length; i++) {
            String name = modes[i];
            String optionLabel = modeLabel(name);
            float rowY = dropY + 4.0f + i * rowH;
            boolean selected = name.equalsIgnoreCase(mode.getModeString());
            boolean rowHover = isHovered(vx + 4.0f, rowY, vx + vw - 4.0f, rowY + rowH, mouseX, mouseY);
            float rowActive = selected ? 1.0f : rowHover ? 0.55f : 0.0f;
            if (rowActive > 0.01f) {
                RenderServices.shapes().rounded(vx + 5.0f, rowY + 2.0f, vx + vw - 5.0f, rowY + rowH - 2.0f,
                        6.0f, alpha(selected ? 0xFF26151F : 0xFF1B1218, (96.0f + 58.0f * rowActive) * expand * guiAlpha));
            }
            drawGlowText(FontLoaders.C14, optionLabel, vx + 12.0f, rowY + 7.0f,
                    alpha(selected ? TEXT : blend(MUTED, TEXT, rowHover ? 0.45f : 0.0f),
                            (190.0f + 42.0f * rowActive) * guiAlpha),
                    0.28f + 0.34f * rowActive);
            if (selected) {
                drawSakuraFlower(vx + vw - 14.0f, rowY + rowH * 0.5f, 2.0f, 0.75f);
            }
        }
    }

    private void drawBindingOverlay(ScaledResolution sr) {
        if (bindingModule == null) {
            return;
        }
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(), alpha(0xFF000000, 120.0f));
        float bw = 250.0f;
        float bh = 84.0f;
        float bx = (sr.getScaledWidth() - bw) * 0.5f;
        float by = (sr.getScaledHeight() - bh) * 0.5f;
        drawSakuraPanel(bx, by, bx + bw, by + bh, 10.0f, 1.0f);
        drawGlowCentered(FontLoaders.C20, "Press a key", bx + bw * 0.5f, by + 20.0f, alpha(TEXT, 245.0f), 0.72f);
        drawGlowCentered(FontLoaders.C14, displayName(bindingModule) + " / DEL clear",
                bx + bw * 0.5f, by + 50.0f, alpha(MUTED, 220.0f), 0.42f);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (bindingModule != null) {
            return;
        }
        float sx = unscaleX(x);
        float sy = unscaleY(y);
        if (mouseButton == 0 && isHovered(sx, sy, sx + w / scale, sy + 52.0f, mouseX, mouseY)) {
            draggingWindow = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return;
        }
        if (handleSidebarClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleModuleClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleValueClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleSidebarClick(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        float sx = unscaleX(x);
        float sy = unscaleY(y);
        float rowX = sx + 15.0f;
        float rowY = sy + 64.0f;
        for (ModuleType type : ModuleType.values()) {
            if (isHovered(rowX, rowY, rowX + 114.0f, rowY + 28.0f, mouseX, mouseY)) {
                listScrollByType.put(currentType, listScroll);
                currentType = type;
                listScroll = scrollForType(type);
                listScrollDisplay = listScroll;
                return true;
            }
            rowY += 34.0f;
        }
        return false;
    }

    private boolean handleModuleClick(int mouseX, int mouseY, int button) {
        float sx = unscaleX(x);
        float sy = unscaleY(y);
        float listX = sx + 154.0f;
        float rowY = sy + 104.0f - listScrollDisplay;
        for (Module module : ModuleManager.getModulesInType(currentType)) {
            if (isHovered(listX, rowY, listX + 194.0f, rowY + 34.0f, mouseX, mouseY)) {
                if (button == 0) {
                    if (selectedModule != module) {
                        detailScrollByModule.put(selectedModule, detailScroll);
                        selectedModule = module;
                        detailAnimationModule = module;
                        detailProgress = 0.0f;
                        detailScroll = scrollForModule(module);
                        detailScrollDisplay = detailScroll;
                    } else {
                        module.toggle();
                    }
                    return true;
                }
                if (button == 1) {
                    selectedModule = module;
                    detailAnimationModule = module;
                    detailProgress = 0.0f;
                    detailScroll = scrollForModule(module);
                    detailScrollDisplay = detailScroll;
                    bindingModule = module;
                    return true;
                }
            }
            rowY += 42.0f;
        }
        return false;
    }

    private boolean handleValueClick(int mouseX, int mouseY, int button) {
        if (button != 0 || selectedModule == null) {
            return false;
        }
        float sx = unscaleX(x);
        float sy = unscaleY(y);
        float dx = sx + 388.0f;
        float dy = sy + 142.0f - detailScrollDisplay;
        float dw = w / scale - 420.0f;
        for (Value value : selectedModule.getValues()) {
            if (!value.isVisible()) {
                continue;
            }
            float vh = valueHeight(value);
            if (isHovered(dx, dy, dx + dw, dy + vh - 8.0f, mouseX, mouseY)) {
                if (value instanceof Option) {
                    value.setValue(!Boolean.TRUE.equals(value.getValue()));
                    return true;
                }
                if (value instanceof ModeProperty) {
                    return handleModePropertyClick((ModeProperty) value, dx, dy, dw, mouseX, mouseY);
                }
                if (value instanceof Numbers) {
                    Numbers number = (Numbers) value;
                    beginNumberDrag(number, mouseX, dx, dw,
                            number.getMinimum().doubleValue(), number.getMaximum().doubleValue());
                    return true;
                }
                if (value instanceof Mode) {
                    return handleModeClick((Mode) value, dx, dy, dw, mouseX, mouseY);
                }
            }
            dy += vh;
        }
        return false;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingWindow = false;
        draggingNumber = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            ScaledResolution sr = new ScaledResolution(mc);
            int mx = Mouse.getEventX() * sr.getScaledWidth() / Math.max(1, mc.displayWidth);
            int my = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / Math.max(1, mc.displayHeight) - 1;
            handleWheel(mx, my, wheel);
        }
        super.handleMouseInput();
    }

    private void handleWheel(int mouseX, int mouseY, int wheel) {
        float sx = unscaleX(x);
        float sy = unscaleY(y);
        if (isHovered(sx + 144.0f, sy + 64.0f, sx + 358.0f, sy + h / scale - 22.0f, mouseX, mouseY)) {
            listScroll = clamp(listScroll - Math.signum(wheel) * 24.0f, 0.0f, maxListScroll());
            listScrollByType.put(currentType, listScroll);
            return;
        }
        if (isHovered(sx + 372.0f, sy + 64.0f, sx + w / scale - 16.0f, sy + h / scale - 22.0f, mouseX, mouseY)) {
            detailScroll = clamp(detailScroll - Math.signum(wheel) * 24.0f, 0.0f, maxDetailScroll());
            detailScrollByModule.put(selectedModule, detailScroll);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            bindingModule.setKey(keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK ? 0 : keyCode);
            bindingModule = null;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        detailScrollByModule.put(selectedModule, detailScroll);
        listScrollByType.put(currentType, listScroll);
        try {
            Client.SaveConfig();
        } catch (IOException ignored) {
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateDrag(ScaledResolution sr, int mouseX, int mouseY) {
        if (!draggingWindow) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingWindow = false;
            return;
        }
        float nx = clamp(mouseX - dragOffsetX, 8.0f, Math.max(8.0f, sr.getScaledWidth() - w - 8.0f));
        float ny = clamp(mouseY - dragOffsetY, 8.0f, Math.max(8.0f, sr.getScaledHeight() - h - 8.0f));
        ClickGUI.windowX.setValue((double) nx);
        ClickGUI.windowY.setValue((double) ny);
    }

    private void updateScrollAnimations() {
        listScroll = clamp(listScroll, 0.0f, maxListScroll());
        detailScroll = clamp(detailScroll, 0.0f, maxDetailScroll());
        listScrollDisplay = animate(listScrollDisplay, listScroll, 0.26f);
        detailScrollDisplay = animate(detailScrollDisplay, detailScroll, 0.26f);
    }

    private void beginNumberDrag(Numbers number, int mouseX, float x, float w, double min, double max) {
        draggingNumber = number;
        draggingX = x;
        draggingW = w;
        draggingMin = min;
        draggingMax = max;
        updateNumberDrag(mouseX);
    }

    private void updateNumberDrag(int mouseX) {
        if (draggingNumber == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingNumber = null;
            return;
        }
        double pct = clamp((mouseX / scale - draggingX) / Math.max(1.0f, draggingW), 0.0f, 1.0f);
        double raw = draggingMin + (draggingMax - draggingMin) * pct;
        double inc = draggingNumber.getIncrement().doubleValue();
        double next = inc > 0.0D ? Math.round(raw / inc) * inc : raw;
        next = Math.max(draggingMin, Math.min(draggingMax, next));
        draggingNumber.setNumberValue(next);
    }

    private boolean handleModeClick(Mode mode, float x, float y, float w, int mouseX, int mouseY) {
        if (mode == expandedMode) {
            Enum[] modes = mode.getModes();
            if (modes != null) {
                float rowH = 22.0f;
                float dropY = y + 32.0f;
                for (int i = 0; i < modes.length; i++) {
                    float rowY = dropY + 4.0f + i * rowH;
                    if (isHovered(x + 4.0f, rowY, x + w - 4.0f, rowY + rowH, mouseX, mouseY)) {
                        mode.setMode(modes[i].name());
                        modeExpandProgress.put(mode, Float.valueOf(0.0f));
                        expandedMode = null;
                        return true;
                    }
                }
            }
            if (isHovered(x, y, x + w, y + 30.0f, mouseX, mouseY)) {
                modeExpandProgress.put(mode, Float.valueOf(0.0f));
                expandedMode = null;
                return true;
            }
            return true;
        }
        if (expandedMode != null) {
            modeExpandProgress.put(expandedMode, Float.valueOf(0.0f));
        }
        if (expandedModeProperty != null) {
            modePropertyExpandProgress.put(expandedModeProperty, Float.valueOf(0.0f));
            expandedModeProperty = null;
        }
        modeExpandProgress.put(mode, Float.valueOf(0.0f));
        expandedMode = mode;
        return true;
    }

    private boolean handleModePropertyClick(ModeProperty mode, float x, float y, float w, int mouseX, int mouseY) {
        if (mode == expandedModeProperty) {
            String[] modes = mode.getModes();
            if (modes != null) {
                float rowH = 22.0f;
                float dropY = y + 32.0f;
                for (int i = 0; i < modes.length; i++) {
                    float rowY = dropY + 4.0f + i * rowH;
                    if (isHovered(x + 4.0f, rowY, x + w - 4.0f, rowY + rowH, mouseX, mouseY)) {
                        mode.setMode(modes[i]);
                        modePropertyExpandProgress.put(mode, Float.valueOf(0.0f));
                        expandedModeProperty = null;
                        return true;
                    }
                }
            }
            if (isHovered(x, y, x + w, y + 30.0f, mouseX, mouseY)) {
                modePropertyExpandProgress.put(mode, Float.valueOf(0.0f));
                expandedModeProperty = null;
                return true;
            }
            return true;
        }
        if (expandedMode != null) {
            modeExpandProgress.put(expandedMode, Float.valueOf(0.0f));
            expandedMode = null;
        }
        if (expandedModeProperty != null) {
            modePropertyExpandProgress.put(expandedModeProperty, Float.valueOf(0.0f));
        }
        modePropertyExpandProgress.put(mode, Float.valueOf(0.0f));
        expandedModeProperty = mode;
        return true;
    }

    private float valueHeight(Value value) {
        if (value instanceof ModeProperty) {
            if (value == expandedModeProperty) {
                String[] modes = ((ModeProperty) value).getModes();
                int count = modes == null ? 0 : modes.length;
                return 38.0f + count * 22.0f + 8.0f;
            }
            return 38.0f;
        }
        if (value instanceof Numbers) {
            return 52.0f;
        }
        if (value instanceof Mode && value == expandedMode) {
            Enum[] modes = ((Mode) value).getModes();
            int count = modes == null ? 0 : modes.length;
            return 38.0f + count * 22.0f + 8.0f;
        }
        return 38.0f;
    }

    private float maxListScroll() {
        int count = ModuleManager.getModulesInType(currentType).size();
        float visible = h / scale - 130.0f;
        return Math.max(0.0f, count * 42.0f - visible);
    }

    private float maxDetailScroll() {
        if (selectedModule == null) {
            return 0.0f;
        }
        float total = 0.0f;
        for (Value value : selectedModule.getValues()) {
            if (value.isVisible()) {
                total += valueHeight(value);
            }
        }
        return Math.max(0.0f, total - (h / scale - 170.0f));
    }

    private void drawSakuraPanel(float x, float y, float x2, float y2, float radius, float alpha) {
        RenderServices.shapes().shadow(x, y, x2, y2, radius, alpha(0xFF000000, 92.0f * alpha * guiAlpha), 8, 3.4f);
        RenderServices.shapes().shadow(x, y, x2, y2, radius, alpha(SAKURA, 32.0f * alpha * guiAlpha), 5, 2.2f);
        RenderServices.liquidGlass().roundedBorder(x, y, x2, y2, radius, 0.55f,
                alpha(GLASS, 166.0f * alpha * guiAlpha), alpha(SAKURA, 34.0f * alpha * guiAlpha), glassSettings());
        RenderServices.shapes().shadow(x + 12.0f, y + 5.0f, x2 - 12.0f, y2 - 5.0f, radius,
                alpha(SAKURA, 18.0f * alpha * guiAlpha), 3, 1.8f);
    }

    private void drawMiniGlass(float x, float y, float x2, float y2, float radius, int fill, int border) {
        RenderServices.liquidGlass().roundedBorder(x, y, x2, y2, radius, 0.45f, fill, border, glassSettings());
    }

    private void drawChip(String text, float x, float y, float w) {
        drawMiniGlass(x, y, x + w, y + 16.0f, 5.0f, alpha(GLASS_SOFT, 142.0f * guiAlpha), alpha(SAKURA, 32.0f * guiAlpha));
        drawSakuraFlower(x + 8.0f, y + 8.0f, 2.25f, 0.95f);
        drawGlowText(FontLoaders.C12, trim(text, FontLoaders.C12, w - 17.0f), x + 15.0f, y + 5.0f,
                alpha(TEXT, 220.0f * guiAlpha), 0.34f);
    }

    private void drawGlowText(gq.yozakura.engine.font.CFontRenderer font, String text, float x, float y,
                              int color, float strength) {
        if (text == null || text.length() == 0 || strength <= 0.0f) {
            font.drawString(text, x, y, color);
            return;
        }
        int wide = alpha(SAKURA, 18.0f * strength * guiAlpha);
        int near = alpha(0xFFFFDCEB, 30.0f * strength * guiAlpha);
        font.drawString(text, x - 0.65f, y, wide);
        font.drawString(text, x + 0.65f, y, wide);
        font.drawString(text, x, y - 0.65f, wide);
        font.drawString(text, x, y + 0.65f, wide);
        font.drawString(text, x - 0.35f, y - 0.35f, near);
        font.drawString(text, x + 0.35f, y + 0.35f, near);
        font.drawString(text, x, y, color);
    }

    private void drawGlowIcon(gq.yozakura.engine.font.CFontRenderer font, String text, float x, float y,
                              int color, float strength) {
        if (text == null || text.length() == 0 || strength <= 0.0f) {
            font.drawString(text, x, y, color);
            return;
        }
        int wide = alpha(SAKURA, 16.0f * strength * guiAlpha);
        int near = alpha(0xFFFFDCEB, 28.0f * strength * guiAlpha);
        font.drawString(text, x - 0.7f, y, wide);
        font.drawString(text, x + 0.7f, y, wide);
        font.drawString(text, x, y - 0.7f, wide);
        font.drawString(text, x, y + 0.7f, wide);
        font.drawString(text, x, y, near);
        font.drawString(text, x, y, color);
    }

    private void drawGlowCentered(gq.yozakura.engine.font.CFontRenderer font, String text, float centerX, float y,
                                  int color, float strength) {
        drawGlowText(font, text, centerX - font.getStringWidth(text) * 0.5f, y, color, strength);
    }

    private void drawSakuraFlower(float centerX, float centerY, float size, float alpha) {
        RenderServices.shapes().shadow(centerX - size, centerY - size, centerX + size, centerY + size,
                size, alpha(SAKURA, 74.0f * alpha * guiAlpha), 4, size * 0.70f);
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * 0.20f, 0.0f);
            drawPetal(size, alpha);
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
        RenderServices.shapes().circle(centerX, centerY, 0, 360, size * 0.30f, alpha(0xFFFFF3FA, 235.0f * alpha * guiAlpha));
    }

    private void drawPetal(float size, float alpha) {
        float width = size * 0.58f;
        float length = size * 1.12f;
        float[][] points = new float[][]{
                {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
                {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
                {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f}, {0.00f, -0.18f}
        };
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(0xFFFFEAF3, alpha * 0.96f * guiAlpha);
        GL11.glVertex2f(0.0f, length * 0.36f);
        for (float[] point : points) {
            glColor(SAKURA, alpha * 0.70f * guiAlpha);
            GL11.glVertex2f(point[0] * width, point[1] * length);
        }
        GL11.glEnd();
    }

    private void drawFlyingPetals(ScaledResolution sr) {
        if (guiAlpha <= 0.02f) {
            return;
        }
        float time = (System.currentTimeMillis() % 9000L) / 9000.0f;
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (int i = 0; i < 22; i++) {
                float lane = fract(i * 0.173f + 0.11f);
                float speed = 0.44f + (i % 5) * 0.055f;
                float phase = fract(time * speed + i * 0.071f);
                float drift = (float) Math.sin(time * 6.2831855f * (0.82f + i * 0.018f) + i * 1.73f);
                float sway = (float) Math.sin((phase + i * 0.13f) * 6.2831855f);
                float px = sr.getScaledWidth() * lane + drift * (18.0f + (i % 4) * 4.0f)
                        - phase * 42.0f;
                float py = -18.0f + phase * (sr.getScaledHeight() + 42.0f)
                        + sway * (7.0f + (i % 3) * 2.0f);
                if (px < -24.0f) {
                    px += sr.getScaledWidth() + 48.0f;
                } else if (px > sr.getScaledWidth() + 24.0f) {
                    px -= sr.getScaledWidth() + 48.0f;
                }
                float edgeFade = Math.min(1.0f, Math.min((py + 18.0f) / 42.0f,
                        (sr.getScaledHeight() + 24.0f - py) / 48.0f));
                float alpha = clamp(edgeFade, 0.0f, 1.0f) * (0.24f + (i % 4) * 0.045f);
                float size = 2.0f + (i % 5) * 0.34f;
                GL11.glPushMatrix();
                GL11.glTranslatef(px, py, 0.0f);
                GL11.glRotatef(phase * 260.0f + i * 41.0f, 0.0f, 0.0f, 1.0f);
                drawPetal(size, alpha);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glPopMatrix();
        }
    }

    private LiquidGlassSettings glassSettings() {
        return LiquidGlassSettings.defaults()
                .withBlurRadius(18.0f)
                .withBlurDownscale(0.92f)
                .withNoise(0.018f)
                .withRefractionScale(1.16f)
                .withHighlight(1.05f);
    }

    private void beginScissorScaled(float x, float y, float w, float h) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        int ix = Math.round(x * scale * sf);
        int iy = Math.round((sr.getScaledHeight() - (y + h) * scale) * sf);
        int iw = Math.round(w * scale * sf);
        int ih = Math.round(h * scale * sf);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(ix, iy, iw, ih);
    }

    private void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void pushScale() {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0f);
    }

    private void popScale() {
        GL11.glPopMatrix();
    }

    private float unscaleX(float value) {
        return value / scale;
    }

    private float unscaleY(float value) {
        return value / scale;
    }

    private void updateFrameScale() {
        long now = System.nanoTime();
        float measured = clamp((now - lastFrameNanos) / 16666666.0f, 0.45f, 2.2f);
        lastFrameNanos = now;
        frameScale = measured;
    }

    private float animate(float current, float target, float speed) {
        float factor = 1.0f - (float) Math.pow(1.0f - clamp(speed, 0.0f, 1.0f), frameScale);
        float value = current + (target - current) * factor;
        return Math.abs(target - value) < 0.01f ? target : value;
    }

    private <T> float animateMap(Map<T, Float> map, T key, float target, float speed) {
        Float current = map.get(key);
        float value = current == null ? target : current.floatValue();
        value = animate(value, target, speed);
        if (value < 0.01f && target <= 0.0f) {
            map.remove(key);
            return 0.0f;
        }
        if (value > 0.99f && target >= 1.0f) {
            value = 1.0f;
        }
        map.put(key, value);
        return value;
    }

    private <T> float animateStateMap(Map<T, Float> map, T key, float target, float speed) {
        Float current = map.get(key);
        float value = current == null ? 0.0f : current.floatValue();
        value = animate(value, target, speed);
        if (value < 0.01f && target <= 0.0f) {
            value = 0.0f;
            map.put(key, value);
            return value;
        }
        if (value > 0.99f && target >= 1.0f) {
            value = 1.0f;
        }
        map.put(key, value);
        return value;
    }

    private float animateOptionSwitch(Option value, boolean enabled) {
        SwitchAnim anim = optionSwitchProgress.get(value);
        if (anim == null) {
            anim = new SwitchAnim();
            anim.state = enabled;
            anim.progress = enabled ? 1.0f : 0.0f;
            anim.initialized = true;
            optionSwitchProgress.put(value, anim);
            return anim.progress;
        }
        if (!anim.initialized) {
            anim.state = enabled;
            anim.progress = enabled ? 1.0f : 0.0f;
            anim.initialized = true;
            return anim.progress;
        }
        if (anim.state != enabled) {
            anim.state = enabled;
        }
        float target = anim.state ? 1.0f : 0.0f;
        if (anim.progress == target) {
            return anim.progress;
        }
        anim.progress = animate(anim.progress, target, 0.16f);
        if (Math.abs(anim.progress - target) < 0.01f) {
            anim.progress = target;
        }
        return anim.progress;
    }

    private float animateValue(Value value, float target) {
        return animateMap(valueHoverProgress, value, target, 0.20f);
    }

    private float introOffset() {
        return 0.0f;
    }

    private int blend(int from, int to, float progress) {
        float t = clamp(progress, 0.0f, 1.0f);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float scrollForType(ModuleType type) {
        Float value = listScrollByType.get(type);
        return value == null ? 0.0f : value.floatValue();
    }

    private static float scrollForModule(Module module) {
        Float value = detailScrollByModule.get(module);
        return value == null ? 0.0f : value.floatValue();
    }

    private String displayName(Module module) {
        return module == null ? "" : Client.CHINESE ? module.getChinese() : module.getName();
    }

    private String displayName(Value value) {
        if (value == null) {
            return "";
        }
        String display = value.getDisplayName();
        return display == null || display.length() == 0 ? value.getName() : display;
    }

    private String keyName(int key) {
        if (key == 0 || key == Keyboard.KEY_NONE) {
            return "NONE";
        }
        String name = Keyboard.getKeyName(key);
        return name == null ? String.valueOf(key) : name;
    }

    private String modeLabel(String text) {
        if (text == null) {
            return "";
        }
        String[] parts = text.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private double numberValue(Numbers number) {
        Object value = number.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private float pct(double value, double min, double max) {
        if (max <= min) {
            return 0.0f;
        }
        return clamp((float) ((value - min) / (max - min)), 0.0f, 1.0f);
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05D) {
            return String.valueOf(Math.round(value));
        }
        String text = String.format(java.util.Locale.US, "%.2f", value);
        while (text.endsWith("0") && text.indexOf('.') >= 0) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private String trim(String text, gq.yozakura.engine.font.CFontRenderer font, float maxWidth) {
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
        return result.length() <= 1 ? "..." : result + "...";
    }

    private int alpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private void glColor(int color, float alpha) {
        float a = ((color >>> 24) & 255) / 255.0f * clamp(alpha, 0.0f, 1.0f);
        GL11.glColor4f(((color >>> 16) & 255) / 255.0f, ((color >>> 8) & 255) / 255.0f,
                (color & 255) / 255.0f, a);
    }

    private boolean isHovered(float x1, float y1, float x2, float y2, int mouseX, int mouseY) {
        float mx = mouseX / scale;
        float my = mouseY / scale;
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private float fract(float value) {
        return value - (float) Math.floor(value);
    }
}
