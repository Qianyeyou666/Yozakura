package gq.yozakura.ui.click.sakura;

import gq.yozakura.core.Client;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.render.ShaderRenderer;
import gq.yozakura.engine.render.glow.GlowProfile;
import gq.yozakura.engine.render.ui.LiquidGlassSettings;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.ui.click.ClickGuiIcons;
import gq.yozakura.util.animation.AnimationUtil;
import gq.yozakura.util.animation.UiClock;
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

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class SakuraClickGui extends GuiScreen {
    private static final float BASE_WIDTH = 760.0f;
    private static final float BASE_HEIGHT = 430.0f;
    private static final float MINIMUM_SCALE = 0.70f;
    private static final float MAXIMUM_SCALE = 1.35f;
    private static VisualPalette PALETTE = VisualPalette.nightBloom();
    private static ClickGUI.Palette appliedPalette;
    private static int TEXT = PALETTE.getTextPrimary();
    private static int MUTED = PALETTE.getTextSecondary();
    private static int FAINT = PALETTE.getTextDisabled();
    private static int SAKURA = PALETTE.getAccentPrimary();
    private static int SAKURA_STRONG = PALETTE.getGlowPrimary();
    private static int GLASS = PALETTE.getCanvas();
    private static int GLASS_SOFT = PALETTE.getSurface();
    private static int SHADOW = PALETTE.getShadow();
    private static int SURFACE_RAISED = PALETTE.getSurfaceRaised();
    private static int SURFACE_OVERLAY = PALETTE.getSurfaceOverlay();
    private static int TRACK = PALETTE.getBorderSubtle();
    private static final float[][] SAKURA_PETAL_POINTS = new float[][]{
            {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
            {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
            {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f},
            {0.00f, -0.18f}
    };
    private static final LiquidGlassSettings GLASS_SETTINGS = LiquidGlassSettings.defaults()
            .withBlurRadius(18.0f)
            .withBlurDownscale(0.92f)
            .withNoise(0.018f)
            .withRefractionScale(1.16f)
            .withHighlight(1.05f);

    private static ModuleType currentType = ModuleType.Combat;
    private static Module selectedModule;
    private static final Map<ModuleType, Float> listScrollByType = new HashMap<ModuleType, Float>();
    private static final Map<Module, Float> detailScrollByModule = new HashMap<Module, Float>();
    private final Map<ModuleType, Float> typeHoverProgress = new HashMap<ModuleType, Float>();
    private final Map<ModuleType, Float> typeSelectProgress = new HashMap<ModuleType, Float>();
    private final Map<Module, Float> moduleHoverProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> moduleToggleProgress = new HashMap<Module, Float>();
    private final Map<Module, Float> moduleSelectProgress = new HashMap<Module, Float>();
    private final Map<Value, Float> valueHoverProgress = new HashMap<Value, Float>();
    private final Map<Numbers, Float> numberProgress = new HashMap<Numbers, Float>();
    private final Map<Mode, Float> modeExpandProgress = new HashMap<Mode, Float>();
    private final Map<ModeProperty, Float> modePropertyExpandProgress = new HashMap<ModeProperty, Float>();
    private final Map<Value, Float> modeSelectionPulse = new HashMap<Value, Float>();
    private final Map<String, Float> modeRowHoverProgress = new HashMap<String, Float>();
    private final Map<String, Float> modeRowSelectProgress = new HashMap<String, Float>();
    private final Map<Option, SwitchAnim> optionSwitchProgress = new HashMap<Option, SwitchAnim>();

    private float x;
    private float y;
    private float w;
    private float h;
    private float scale = 1.0f;
    private float responsiveScale = 1.0f;
    private float openProgress;
    private float guiAlpha;
    private boolean draggingWindow;
    private boolean resizingWindow;
    private float dragOffsetX;
    private float dragOffsetY;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private float resizeStartScale;
    private float listScroll;
    private float listScrollDisplay;
    private float detailScroll;
    private float detailScrollDisplay;
    private Numbers draggingNumber;
    private Numbers draggingColorRed;
    private Numbers draggingColorGreen;
    private Numbers draggingColorBlue;
    private float draggingColorX;
    private float draggingColorY;
    private float draggingColorW;
    private float draggingColorH;
    private float draggingX;
    private float draggingW;
    private double draggingMin;
    private double draggingMax;
    private Module bindingModule;
    private Module bindingAnimationModule;
    private Mode expandedMode;
    private ModeProperty expandedModeProperty;
    private Module detailAnimationModule;
    private float detailProgress;
    private float listTransitionProgress = 1.0f;
    private float bindingOverlayProgress;
    private boolean closing;
    private final UiClock uiClock = new UiClock();
    private float uiDeltaSeconds;

    private static final class SwitchAnim {
        boolean state;
        boolean initialized;
        float progress;
    }

    @Override
    public void initGui() {
        refreshPalette();
        ScaledResolution sr = new ScaledResolution(mc);
        uiClock.reset();
        uiClock.tick(System.nanoTime());
        uiDeltaSeconds = 0.0f;
        updateLayout(sr);
        openProgress = 0.0f;
        closing = false;
        listTransitionProgress = 1.0f;
        bindingOverlayProgress = bindingModule == null ? 0.0f : 1.0f;
        listScroll = scrollForType(currentType);
        listScrollDisplay = listScroll;
        detailScroll = scrollForModule(selectedModule);
        detailScrollDisplay = detailScroll;
        detailAnimationModule = selectedModule;
        detailProgress = selectedModule == null ? 0.0f : 0.0f;
        bindingModule = null;
        bindingAnimationModule = null;
        draggingNumber = null;
        clearColorDrag();
        resizingWindow = false;
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshPalette();
        updateUiClock();
        ScaledResolution sr = new ScaledResolution(mc);
        updateLayout(sr);
        updateDrag(sr, mouseX, mouseY);
        updateNumberDrag(mouseX);
        updateColorDrag(mouseX, mouseY);
        updateScrollAnimations();
        openProgress = animate(openProgress, closing ? 0.0f : 1.0f, closing ? 0.24f : 0.18f);
        if (closing && openProgress <= 0.015f) {
            mc.displayGuiScreen(null);
            return;
        }
        listTransitionProgress = animate(listTransitionProgress, 1.0f, 0.20f);
        bindingOverlayProgress = animate(bindingOverlayProgress, bindingModule == null ? 0.0f : 1.0f, 0.20f);
        guiAlpha = easeOut(openProgress) * ClickGUI.clickGuiAlpha.getValue().floatValue();

        RenderServices.glow().beginFrame();
        try {
            ShaderRenderer.invalidateFrostedGlass();
            drawBackdrop(sr);
            drawShell(mouseX, mouseY);
            drawResizeHandle(mouseX, mouseY);
            drawSidebar(mouseX, mouseY);
            drawModuleList(mouseX, mouseY);
            drawDetail(mouseX, mouseY);
            drawFlyingPetals(sr);
            drawBindingOverlay(sr);
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            RenderServices.glow().flush();
        }
    }

    private void updateLayout(ScaledResolution sr) {
        responsiveScale = sr.getScaledWidth() < 760 ? 0.86f : sr.getScaledWidth() < 980 ? 0.94f : 1.0f;
        scale = responsiveScale * ClickGUI.sakuraScale.getValue().floatValue();
        w = Math.min(sr.getScaledWidth() - 24.0f, BASE_WIDTH * scale);
        h = Math.min(sr.getScaledHeight() - 24.0f, BASE_HEIGHT * scale);
        float defaultX = (sr.getScaledWidth() - w) * 0.5f;
        float defaultY = (sr.getScaledHeight() - h) * 0.5f;
        x = ClickGUI.windowX.getValue() == null || ClickGUI.windowX.getValue() < 0.0D
                ? defaultX : ClickGUI.windowX.getValue().floatValue();
        y = ClickGUI.windowY.getValue() == null || ClickGUI.windowY.getValue() < 0.0D
                ? defaultY : ClickGUI.windowY.getValue().floatValue();
        x = clamp(x, 8.0f, Math.max(8.0f, sr.getScaledWidth() - w - 8.0f));
        y = clamp(y, 8.0f, Math.max(8.0f, sr.getScaledHeight() - h - 8.0f));
        x = clamp(x, 8.0f, Math.max(8.0f, sr.getScaledWidth() - w - 8.0f));
        y = clamp(y, 8.0f, Math.max(8.0f, sr.getScaledHeight() - h - 8.0f));
    }

    private void drawBackdrop(ScaledResolution sr) {
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(),
                alpha(SHADOW, 128.0f * guiAlpha));
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
                float hover = easeSmooth(animateMap(typeHoverProgress, type, hovered ? 1.0f : 0.0f, 0.20f));
                float active = easeSmooth(animateMap(typeSelectProgress, type, selected ? 1.0f : 0.0f, 0.18f));
                float intensity = Math.max(hover, active);
                if (intensity > 0.01f) {
                    RenderServices.shapes().shadow(rowX, rowY, rowX + 114.0f, rowY + 28.0f,
                            7.0f, alpha(SAKURA, (18.0f + 36.0f * active + 24.0f * hover) * guiAlpha), 4, 1.8f);
                    drawMiniGlass(rowX, rowY, rowX + 114.0f, rowY + 28.0f, 7.0f,
                            alpha(selected ? SURFACE_OVERLAY : GLASS_SOFT,
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
            float listEase = easeOut(listTransitionProgress);
            float oldAlpha = guiAlpha;
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                float rowEase = clamp((listEase - i * 0.035f) / 0.82f, 0.0f, 1.0f);
                if (rowEase > 0.01f) {
                    guiAlpha = oldAlpha * easeOut(rowEase);
                    drawModuleRow(module, listX + 10.0f, rowY + (1.0f - rowEase) * 8.0f,
                            listW - 20.0f, mouseX, mouseY);
                }
                rowY += 42.0f;
            }
            guiAlpha = oldAlpha;
            endScissor();
        } finally {
            popScale();
        }
    }

    private void drawModuleRow(Module module, float rowX, float rowY, float rowW, int mouseX, int mouseY) {
        boolean selected = module == selectedModule;
        boolean enabled = module.getState();
        boolean hovered = isHovered(rowX, rowY, rowX + rowW, rowY + 34.0f, mouseX, mouseY);
        float hover = easeSmooth(animateMap(moduleHoverProgress, module, hovered ? 1.0f : 0.0f, 0.20f));
        float toggle = easeSmooth(animateStateMap(moduleToggleProgress, module, enabled ? 1.0f : 0.0f, 0.18f));
        float selectedEase = easeSmooth(animateMap(moduleSelectProgress, module, selected ? 1.0f : 0.0f, 0.16f));
        float rowIntensity = Math.max(Math.max(hover, toggle), selectedEase);
        if (rowIntensity > 0.01f) {
            RenderServices.shapes().shadow(rowX, rowY, rowX + rowW, rowY + 34.0f,
                    7.0f, alpha(SAKURA, (14.0f + 32.0f * hover + 34.0f * selectedEase + 26.0f * toggle) * guiAlpha),
                    4, 1.8f);
            drawMiniGlass(rowX, rowY, rowX + rowW, rowY + 34.0f, 7.0f,
                    alpha(selected ? SURFACE_OVERLAY : GLASS_SOFT,
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
                alpha(blend(TRACK, SAKURA, toggle), (190.0f + 28.0f * toggle) * guiAlpha));
        RenderServices.shapes().circle(tx + 6.0f + 10.0f * toggle, rowY + 16.0f, 0, 360, 4.2f,
                alpha(blend(MUTED, TEXT, toggle), 238.0f * guiAlpha));
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
            detailProgress = animate(detailProgress, 1.0f, 0.20f);
            float detailEase = easeOut(detailProgress);
            GL11.glPushMatrix();
            float oldAlpha = guiAlpha;
            guiAlpha *= detailEase;
            GL11.glTranslatef((1.0f - detailEase) * 10.0f, 0.0f, 0.0f);
            drawDetailHeader(dx, dy, dw);
            beginScissorScaled(dx, dy + 70.0f, dw, dh - 78.0f);
            float valueY = dy + 78.0f - detailScrollDisplay;
            List<Value> values = selectedModule.getValues();
            if (values.isEmpty()) {
                drawGlowText(FontLoaders.C14, "No settings", dx + 16.0f, valueY,
                        alpha(MUTED, 205.0f * guiAlpha), 0.3f);
            }
            int visibleIndex = 0;
            for (int index = 0; index < values.size(); index++) {
                Value value = values.get(index);
                if (!value.isVisible()) {
                    continue;
                }
                if (isHiddenStandaloneNumber(values, index)) {
                    continue;
                }
                if (isColorContinuation(values, index)) {
                    continue;
                }
                float rowEase = clamp((detailEase - visibleIndex * 0.025f) / 0.86f, 0.0f, 1.0f);
                float rowAlpha = guiAlpha;
                if (rowEase > 0.01f) {
                    guiAlpha = oldAlpha * easeOut(rowEase) * detailEase;
                    if (isColorStart(values, index)) {
                        drawColorPicker((Numbers) value, (Numbers) values.get(index + 1), (Numbers) values.get(index + 2),
                                dx + 16.0f, valueY + (1.0f - rowEase) * 5.0f, dw - 32.0f);
                    } else {
                        drawValue(value, dx + 16.0f, valueY + (1.0f - rowEase) * 5.0f,
                                dw - 32.0f, mouseX, mouseY);
                    }
                    guiAlpha = rowAlpha;
                }
                valueY += valueHeight(values, index);
                visibleIndex++;
                if (isColorStart(values, index)) {
                    index += 2;
                }
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
        float hover = easeSmooth(animateValue(value, isHovered(vx, vy, vx + vw, vy + 30.0f, mouseX, mouseY) ? 1.0f : 0.0f));
        float active = easeSmooth(animateOptionSwitch(value, enabled));
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy + 1.0f, vx + vw + 7.0f, vy + 31.0f,
                    7.0f, alpha(GLASS, 70.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(value), vx, vy + 8.0f,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.32f * hover);
        float tx = vx + vw - 38.0f;
        if (hover > 0.02f || active > 0.02f) {
            RenderServices.shapes().shadow(tx, vy + 7.0f, tx + 34.0f, vy + 25.0f, 9.0f,
                    alpha(SAKURA, (18.0f + 38.0f * active + 20.0f * hover) * guiAlpha), 4, 1.6f);
        }
        RenderServices.shapes().rounded(tx, vy + 7.0f, tx + 34.0f, vy + 25.0f, 9.0f,
                alpha(blend(TRACK, SAKURA, active), (190.0f + 28.0f * active + 18.0f * hover) * guiAlpha));
        RenderServices.shapes().circle(tx + 10.0f + 14.0f * active, vy + 16.0f, 0, 360, 6.0f,
                alpha(blend(MUTED, TEXT, active), 238.0f * guiAlpha));
    }

    private void drawNumber(Numbers value, float vx, float vy, float vw, int mouseX, int mouseY) {
        double current = numberValue(value);
        float hover = easeSmooth(animateValue(value, isHovered(vx, vy, vx + vw, vy + 42.0f, mouseX, mouseY) ? 1.0f : 0.0f));
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy - 5.0f, vx + vw + 7.0f, vy + 40.0f,
                    7.0f, alpha(GLASS, 58.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(value), vx, vy,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.3f * hover);
        String text = formatNumber(current);
        drawGlowText(FontLoaders.C14, text, vx + vw - FontLoaders.C14.getStringWidth(text), vy,
                alpha(hover > 0.1f ? SAKURA : MUTED, (205.0f + 26.0f * hover) * guiAlpha), 0.3f + 0.38f * hover);
        float sliderY = vy + 24.0f;
        float progress = animateMap(numberProgress, value,
                pct(current, value.getMinimum().doubleValue(), value.getMaximum().doubleValue()),
                draggingNumber == value ? 0.34f : 0.18f);
        RenderServices.shapes().rounded(vx, sliderY, vx + vw, sliderY + 5.0f, 2.5f,
                alpha(TRACK, 190.0f * guiAlpha));
        RenderServices.shapes().rounded(vx, sliderY, vx + vw * progress, sliderY + 5.0f, 2.5f,
                alpha(SAKURA, 238.0f * guiAlpha));
        if (hover > 0.02f) {
            RenderServices.shapes().shadow(vx + vw * progress - 6.0f, sliderY - 3.5f,
                    vx + vw * progress + 6.0f, sliderY + 8.5f, 6.0f,
                    alpha(SAKURA, 58.0f * hover * guiAlpha), 4, 2.0f);
        }
        RenderServices.shapes().circle(vx + vw * progress, sliderY + 2.5f, 0, 360, 5.6f,
                alpha(TEXT, 246.0f * guiAlpha));
    }

    private void drawColorPicker(Numbers red, Numbers green, Numbers blue, float vx, float vy, float vw) {
        String label = displayName(red).replaceFirst("(?i)\\s*red$", "");
        drawGlowText(FontLoaders.C14, label.length() == 0 ? "Color" : label, vx, vy,
                alpha(TEXT, 228.0f * guiAlpha), 0.36f);
        float paletteY = vy + 16.0f;
        float paletteH = 66.0f;
        int color = 0xFF000000 | colorChannel(red) << 16 | colorChannel(green) << 8 | colorChannel(blue);
        RenderServices.shapes().shadow(vx, paletteY, vx + vw, paletteY + paletteH, 6.0f,
                alpha(color, 38.0f * guiAlpha), 4, 1.8f);
        RenderServices.shapes().roundedHue(vx, paletteY, vx + vw, paletteY + paletteH, 6.0f, guiAlpha);
        RenderServices.shapes().roundedBorder(vx, paletteY, vx + vw, paletteY + paletteH, 6.0f, 0.8f, 0x00000000,
                alpha(TEXT, 58.0f * guiAlpha));
        float[] hsb = Color.RGBtoHSB(colorChannel(red), colorChannel(green), colorChannel(blue), null);
        float markerX = clamp(vx + hsb[0] * vw, vx + 4.0f, vx + vw - 4.0f);
        float markerY = clamp(paletteY + (1.0f - hsb[2]) * paletteH, paletteY + 4.0f, paletteY + paletteH - 4.0f);
        RenderServices.shapes().circleOutline(markerX, markerY, 4.1f, 1.2f, alpha(TEXT, 238.0f * guiAlpha));
        RenderServices.shapes().circle(markerX, markerY, 0, 360, 2.0f, alpha(color, 245.0f * guiAlpha));
    }

    private void drawMode(Mode mode, float vx, float vy, float vw, int mouseX, int mouseY) {
        float hover = easeSmooth(animateValue(mode, isHovered(vx, vy, vx + vw, vy + 30.0f, mouseX, mouseY) ? 1.0f : 0.0f));
        boolean expanded = mode == expandedMode;
        float expandRaw = animateMap(modeExpandProgress, mode, expanded ? 1.0f : 0.0f, 0.20f);
        float expand = easeSmooth(expandRaw);
        float pulse = animateMap(modeSelectionPulse, mode, 0.0f, 0.16f);
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy + 1.0f, vx + vw + 7.0f, vy + 31.0f,
                    7.0f, alpha(GLASS, 62.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(mode), vx, vy + 8.0f,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.3f * hover);
        String label = modeLabel(mode.getModeAsString());
        float pillW = Math.max(88.0f, FontLoaders.C14.getStringWidth(label) + 34.0f);
        float px = vx + vw - pillW;
        if (pulse > 0.02f) {
            RenderServices.shapes().shadow(px, vy + 5.0f, px + pillW, vy + 27.0f, 8.0f,
                    alpha(SAKURA, 50.0f * pulse * guiAlpha), 4, 1.8f);
        }
        drawMiniGlass(px, vy + 5.0f, px + pillW, vy + 27.0f, 7.0f,
                alpha(GLASS_SOFT, (150.0f + 28.0f * hover + 30.0f * expand + 30.0f * pulse) * guiAlpha),
                alpha(SAKURA, (38.0f + 30.0f * hover + 42.0f * expand + 54.0f * pulse) * guiAlpha));
        drawGlowCentered(FontLoaders.C14, label, px + pillW * 0.5f - 5.0f, vy + 10.0f - pulse * 1.2f,
                alpha(SAKURA, (226.0f + 18.0f * hover + 22.0f * pulse) * guiAlpha), 0.42f + 0.36f * hover + 0.24f * pulse);
        drawChevron(FontLoaders.C14, px + pillW - 11.0f, vy + 15.0f, expand,
                alpha(TEXT, (198.0f + 34.0f * expand) * guiAlpha), 0.36f + 0.36f * expand);

        Enum[] modes = mode.getModes();
        if (modes == null || modes.length == 0 || expand <= 0.012f) {
            return;
        }
        float rowH = 22.0f;
        float dropY = vy + 32.0f;
        float fullDropH = modes.length * rowH + 8.0f;
        float dropH = Math.max(7.0f, fullDropH * expand);
        RenderServices.shapes().shadow(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(SAKURA, 34.0f * expand * guiAlpha), 4, 1.8f);
        drawMiniGlass(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(GLASS, 178.0f * expand * guiAlpha), alpha(SAKURA, 52.0f * expand * guiAlpha));
        for (int i = 0; i < modes.length; i++) {
            Enum option = modes[i];
            String name = option.name();
            String optionLabel = modeLabel(name);
            float rowReveal = easeOut(clamp((fullDropH * expand - 4.0f - i * rowH) / rowH, 0.0f, 1.0f));
            if (rowReveal <= 0.01f) {
                continue;
            }
            float rowY = dropY + 4.0f + i * rowH + (1.0f - rowReveal) * 5.0f;
            boolean selected = name.equalsIgnoreCase(mode.getModeAsString());
            boolean rowHover = expanded && rowReveal > 0.80f
                    && isHovered(vx + 4.0f, rowY, vx + vw - 4.0f, rowY + rowH, mouseX, mouseY);
            String rowKey = modeRowKey(mode, name);
            float rowHoverAnim = easeSmooth(animateMap(modeRowHoverProgress, rowKey, rowHover ? 1.0f : 0.0f, 0.18f));
            float rowSelectedAnim = easeSmooth(animateMap(modeRowSelectProgress, rowKey, selected ? 1.0f : 0.0f, 0.18f));
            float rowActive = Math.max(rowSelectedAnim, rowHoverAnim * 0.62f);
            if (rowActive > 0.01f) {
                RenderServices.shapes().rounded(vx + 5.0f, rowY + 2.0f, vx + vw - 5.0f, rowY + rowH - 2.0f,
                        6.0f, alpha(selected ? SURFACE_OVERLAY : SURFACE_RAISED,
                                (96.0f + 58.0f * rowActive) * rowReveal * guiAlpha));
            }
            drawGlowText(FontLoaders.C14, optionLabel, vx + 12.0f, rowY + 7.0f,
                    alpha(rowSelectedAnim > 0.45f ? TEXT : blend(MUTED, TEXT, rowHoverAnim * 0.45f),
                            (190.0f + 42.0f * rowActive) * rowReveal * guiAlpha),
                    0.28f + 0.34f * rowActive);
            if (rowSelectedAnim > 0.02f) {
                drawSakuraFlower(vx + vw - 14.0f, rowY + rowH * 0.5f, 2.0f,
                        0.75f * rowSelectedAnim * rowReveal);
            }
        }
    }

    private void drawModeProperty(ModeProperty mode, float vx, float vy, float vw, int mouseX, int mouseY) {
        float hover = easeSmooth(animateValue(mode, isHovered(vx, vy, vx + vw, vy + 30.0f, mouseX, mouseY) ? 1.0f : 0.0f));
        boolean expanded = mode == expandedModeProperty;
        float expandRaw = animateMap(modePropertyExpandProgress, mode, expanded ? 1.0f : 0.0f, 0.20f);
        float expand = easeSmooth(expandRaw);
        float pulse = animateMap(modeSelectionPulse, mode, 0.0f, 0.16f);
        if (hover > 0.02f) {
            RenderServices.shapes().rounded(vx - 7.0f, vy + 1.0f, vx + vw + 7.0f, vy + 31.0f,
                    7.0f, alpha(GLASS, 62.0f * hover * guiAlpha));
        }
        drawGlowText(FontLoaders.C14, displayName(mode), vx, vy + 8.0f,
                alpha(TEXT, (218.0f + 22.0f * hover) * guiAlpha), 0.34f + 0.3f * hover);
        String label = modeLabel(mode.getModeString());
        float pillW = Math.max(88.0f, FontLoaders.C14.getStringWidth(label) + 34.0f);
        float px = vx + vw - pillW;
        if (pulse > 0.02f) {
            RenderServices.shapes().shadow(px, vy + 5.0f, px + pillW, vy + 27.0f, 8.0f,
                    alpha(SAKURA, 50.0f * pulse * guiAlpha), 4, 1.8f);
        }
        drawMiniGlass(px, vy + 5.0f, px + pillW, vy + 27.0f, 7.0f,
                alpha(GLASS_SOFT, (150.0f + 28.0f * hover + 30.0f * expand + 30.0f * pulse) * guiAlpha),
                alpha(SAKURA, (38.0f + 30.0f * hover + 42.0f * expand + 54.0f * pulse) * guiAlpha));
        drawGlowCentered(FontLoaders.C14, label, px + pillW * 0.5f - 5.0f, vy + 10.0f - pulse * 1.2f,
                alpha(SAKURA, (226.0f + 18.0f * hover + 22.0f * pulse) * guiAlpha), 0.42f + 0.36f * hover + 0.24f * pulse);
        drawChevron(FontLoaders.C14, px + pillW - 11.0f, vy + 15.0f, expand,
                alpha(TEXT, (198.0f + 34.0f * expand) * guiAlpha), 0.36f + 0.36f * expand);

        String[] modes = mode.getModes();
        if (modes == null || modes.length == 0 || expand <= 0.012f) {
            return;
        }
        float rowH = 22.0f;
        float dropY = vy + 32.0f;
        float fullDropH = modes.length * rowH + 8.0f;
        float dropH = Math.max(7.0f, fullDropH * expand);
        RenderServices.shapes().shadow(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(SAKURA, 34.0f * expand * guiAlpha), 4, 1.8f);
        drawMiniGlass(vx, dropY, vx + vw, dropY + dropH, 8.0f,
                alpha(GLASS, 178.0f * expand * guiAlpha), alpha(SAKURA, 52.0f * expand * guiAlpha));
        for (int i = 0; i < modes.length; i++) {
            String name = modes[i];
            String optionLabel = modeLabel(name);
            float rowReveal = easeOut(clamp((fullDropH * expand - 4.0f - i * rowH) / rowH, 0.0f, 1.0f));
            if (rowReveal <= 0.01f) {
                continue;
            }
            float rowY = dropY + 4.0f + i * rowH + (1.0f - rowReveal) * 5.0f;
            boolean selected = name.equalsIgnoreCase(mode.getModeString());
            boolean rowHover = expanded && rowReveal > 0.80f
                    && isHovered(vx + 4.0f, rowY, vx + vw - 4.0f, rowY + rowH, mouseX, mouseY);
            String rowKey = modeRowKey(mode, name);
            float rowHoverAnim = easeSmooth(animateMap(modeRowHoverProgress, rowKey, rowHover ? 1.0f : 0.0f, 0.18f));
            float rowSelectedAnim = easeSmooth(animateMap(modeRowSelectProgress, rowKey, selected ? 1.0f : 0.0f, 0.18f));
            float rowActive = Math.max(rowSelectedAnim, rowHoverAnim * 0.62f);
            if (rowActive > 0.01f) {
                RenderServices.shapes().rounded(vx + 5.0f, rowY + 2.0f, vx + vw - 5.0f, rowY + rowH - 2.0f,
                        6.0f, alpha(selected ? SURFACE_OVERLAY : SURFACE_RAISED,
                                (96.0f + 58.0f * rowActive) * rowReveal * guiAlpha));
            }
            drawGlowText(FontLoaders.C14, optionLabel, vx + 12.0f, rowY + 7.0f,
                    alpha(rowSelectedAnim > 0.45f ? TEXT : blend(MUTED, TEXT, rowHoverAnim * 0.45f),
                            (190.0f + 42.0f * rowActive) * rowReveal * guiAlpha),
                    0.28f + 0.34f * rowActive);
            if (rowSelectedAnim > 0.02f) {
                drawSakuraFlower(vx + vw - 14.0f, rowY + rowH * 0.5f, 2.0f,
                        0.75f * rowSelectedAnim * rowReveal);
            }
        }
    }

    private void drawBindingOverlay(ScaledResolution sr) {
        Module target = bindingModule == null ? bindingAnimationModule : bindingModule;
        float progress = easeSmooth(bindingOverlayProgress);
        if (target == null || progress <= 0.02f) {
            return;
        }
        float oldAlpha = guiAlpha;
        guiAlpha *= progress;
        RenderServices.shapes().rect(0.0f, 0.0f, sr.getScaledWidth(), sr.getScaledHeight(), alpha(SHADOW, 120.0f * guiAlpha));
        float bw = 250.0f;
        float bh = 84.0f;
        float bx = (sr.getScaledWidth() - bw) * 0.5f;
        float by = (sr.getScaledHeight() - bh) * 0.5f + (1.0f - progress) * 8.0f;
        drawSakuraPanel(bx, by, bx + bw, by + bh, 10.0f, 1.0f);
        drawGlowCentered(FontLoaders.C20, "Press a key", bx + bw * 0.5f, by + 20.0f,
                alpha(TEXT, 245.0f * guiAlpha), 0.72f);
        drawGlowCentered(FontLoaders.C14, displayName(target) + " / DEL clear",
                bx + bw * 0.5f, by + 50.0f, alpha(MUTED, 220.0f * guiAlpha), 0.42f);
        guiAlpha = oldAlpha;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (closing) {
            return;
        }
        if (bindingModule != null) {
            return;
        }
        if (mouseButton == 0 && isResizeHandleHovered(mouseX, mouseY)) {
            resizingWindow = true;
            draggingWindow = false;
            resizeStartMouseX = mouseX;
            resizeStartMouseY = mouseY;
            resizeStartScale = ClickGUI.sakuraScale.getValue().floatValue();
            return;
        }
        float sx = unscaleX(x);
        float sy = unscaleY(y + introOffset());
        if (mouseButton == 0 && isHovered(sx, sy, sx + w / scale, sy + 52.0f, mouseX, mouseY)) {
            draggingWindow = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - (y + introOffset());
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
        float sy = unscaleY(y + introOffset());
        float rowX = sx + 15.0f;
        float rowY = sy + 64.0f;
        for (ModuleType type : ModuleType.values()) {
            if (isHovered(rowX, rowY, rowX + 114.0f, rowY + 28.0f, mouseX, mouseY)) {
                listScrollByType.put(currentType, listScroll);
                if (currentType != type) {
                    currentType = type;
                    listScroll = scrollForType(type);
                    listScrollDisplay = listScroll;
                    listTransitionProgress = 0.0f;
                }
                return true;
            }
            rowY += 34.0f;
        }
        return false;
    }

    private boolean handleModuleClick(int mouseX, int mouseY, int button) {
        float sx = unscaleX(x);
        float sy = unscaleY(y + introOffset());
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
                    bindingAnimationModule = module;
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
        float sy = unscaleY(y + introOffset());
        float dx = sx + 388.0f;
        float dy = sy + 142.0f - detailScrollDisplay;
        float dw = w / scale - 420.0f;
        List<Value> values = selectedModule.getValues();
        for (int index = 0; index < values.size(); index++) {
            Value value = values.get(index);
            if (!value.isVisible()) {
                continue;
            }
            if (isHiddenStandaloneNumber(values, index)) {
                continue;
            }
            if (isColorContinuation(values, index)) {
                continue;
            }
            float vh = valueHeight(values, index);
            if (isHovered(dx, dy, dx + dw, dy + vh - 8.0f, mouseX, mouseY)) {
                if (isColorStart(values, index)) {
                    beginColorDrag((Numbers) value, (Numbers) values.get(index + 1), (Numbers) values.get(index + 2),
                            mouseX, mouseY, dx, dy + 16.0f, dw, 66.0f);
                    return true;
                }
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
            if (isColorStart(values, index)) {
                index += 2;
            }
        }
        return false;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingWindow = false;
        resizingWindow = false;
        draggingNumber = null;
        clearColorDrag();
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
        float sy = unscaleY(y + introOffset());
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
            requestClose();
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
        if (resizingWindow) {
            if (!Mouse.isButtonDown(0)) {
                resizingWindow = false;
                return;
            }
            float nextScale = SakuraWindowGeometry.resizeScale(resizeStartScale,
                    resizeStartMouseX, resizeStartMouseY, mouseX, mouseY, responsiveScale,
                    BASE_WIDTH, BASE_HEIGHT, MINIMUM_SCALE, MAXIMUM_SCALE);
            ClickGUI.sakuraScale.setValue((double) nextScale);
            return;
        }
        if (!draggingWindow) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingWindow = false;
            return;
        }
        float nx = clamp(mouseX - dragOffsetX, 8.0f, Math.max(8.0f, sr.getScaledWidth() - w - 8.0f));
        float ny = clamp(SakuraWindowGeometry.windowYFromHeader(mouseY, dragOffsetY, introOffset()),
                8.0f, Math.max(8.0f, sr.getScaledHeight() - h - 8.0f));
        ClickGUI.windowX.setValue((double) nx);
        ClickGUI.windowY.setValue((double) ny);
    }

    private boolean isResizeHandleHovered(int mouseX, int mouseY) {
        float hitSize = 32.0f;
        float shellY = y + introOffset();
        return SakuraWindowGeometry.containsScreen(x + w - hitSize, shellY + h - hitSize,
                x + w + 4.0f, shellY + h + 4.0f, mouseX, mouseY);
    }

    private void drawResizeHandle(int mouseX, int mouseY) {
        float size = 16.0f;
        float handleX = x + w - size - 6.0f;
        float handleY = y + introOffset() + h - size - 6.0f;
        boolean active = resizingWindow || isResizeHandleHovered(mouseX, mouseY);
        RenderServices.shapes().rounded(handleX, handleY, handleX + size, handleY + size, 4.0f,
                alpha(GLASS_SOFT, (106.0f + (active ? 72.0f : 0.0f)) * guiAlpha));
        int color = alpha(active ? SAKURA_STRONG : MUTED, (active ? 230.0f : 170.0f) * guiAlpha);
        RenderServices.shapes().line(handleX + 4.0f, handleY + size - 2.5f, handleX + size - 2.5f, handleY + 4.0f, 1.1f, color);
        RenderServices.shapes().line(handleX + 7.0f, handleY + size - 2.5f, handleX + size - 2.5f, handleY + 7.0f, 1.1f, color);
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

    private void beginColorDrag(Numbers red, Numbers green, Numbers blue, int mouseX, int mouseY,
                                float x, float y, float w, float h) {
        draggingNumber = null;
        draggingColorRed = red;
        draggingColorGreen = green;
        draggingColorBlue = blue;
        draggingColorX = x;
        draggingColorY = y;
        draggingColorW = w;
        draggingColorH = h;
        updateColorFromPointer(mouseX, mouseY);
    }

    private void updateColorDrag(int mouseX, int mouseY) {
        if (draggingColorRed == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            clearColorDrag();
            return;
        }
        updateColorFromPointer(mouseX, mouseY);
    }

    private void updateColorFromPointer(int mouseX, int mouseY) {
        float localMouseX = mouseX / scale;
        float localMouseY = mouseY / scale;
        float hue = clamp((localMouseX - draggingColorX) / Math.max(1.0f, draggingColorW), 0.0f, 1.0f);
        float brightness = 1.0f - clamp((localMouseY - draggingColorY) / Math.max(1.0f, draggingColorH), 0.0f, 1.0f) * 0.72f;
        int color = Color.HSBtoRGB(hue, 0.86f, brightness);
        draggingColorRed.setNumberValue((double) ((color >> 16) & 255));
        draggingColorGreen.setNumberValue((double) ((color >> 8) & 255));
        draggingColorBlue.setNumberValue((double) (color & 255));
    }

    private void clearColorDrag() {
        draggingColorRed = null;
        draggingColorGreen = null;
        draggingColorBlue = null;
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
                        modeSelectionPulse.put(mode, Float.valueOf(1.0f));
                        expandedMode = null;
                        return true;
                    }
                }
            }
            if (isHovered(x, y, x + w, y + 30.0f, mouseX, mouseY)) {
                expandedMode = null;
                return true;
            }
            return true;
        }
        if (expandedModeProperty != null) {
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
                        modeSelectionPulse.put(mode, Float.valueOf(1.0f));
                        expandedModeProperty = null;
                        return true;
                    }
                }
            }
            if (isHovered(x, y, x + w, y + 30.0f, mouseX, mouseY)) {
                expandedModeProperty = null;
                return true;
            }
            return true;
        }
        if (expandedMode != null) {
            expandedMode = null;
        }
        modePropertyExpandProgress.put(mode, Float.valueOf(0.0f));
        expandedModeProperty = mode;
        return true;
    }

    private float valueHeight(Value value) {
        if (value instanceof ModeProperty) {
            String[] modes = ((ModeProperty) value).getModes();
            int count = modes == null ? 0 : modes.length;
            return 38.0f + (count * 22.0f + 8.0f) * animatedModeHeight((ModeProperty) value);
        }
        if (value instanceof Numbers) {
            return 52.0f;
        }
        if (value instanceof Mode) {
            Enum[] modes = ((Mode) value).getModes();
            int count = modes == null ? 0 : modes.length;
            return 38.0f + (count * 22.0f + 8.0f) * animatedModeHeight((Mode) value);
        }
        return 38.0f;
    }

    private float valueHeight(List<Value> values, int index) {
        if (isColorStart(values, index)) {
            return 94.0f;
        }
        return valueHeight(values.get(index));
    }

    private boolean isColorStart(List<Value> values, int index) {
        if (index < 0 || index + 2 >= values.size()) {
            return false;
        }
        String base = colorChannelBase(values.get(index), "red");
        return base != null
                && base.equals(colorChannelBase(values.get(index + 1), "green"))
                && base.equals(colorChannelBase(values.get(index + 2), "blue"));
    }

    private boolean isColorContinuation(List<Value> values, int index) {
        return isColorStart(values, index - 1) || isColorStart(values, index - 2);
    }

    private boolean isHiddenStandaloneNumber(List<Value> values, int index) {
        return selectedModule instanceof ClickGUI && values.get(index) instanceof Numbers && !isColorStart(values, index);
    }

    private String colorChannelBase(Value value, String channel) {
        if (!(value instanceof Numbers)) {
            return null;
        }
        String name = value.getName() == null ? "" : value.getName();
        name = name.replace(" ", "").replace("_", "").replace("-", "").toLowerCase();
        return name.endsWith(channel) ? name.substring(0, name.length() - channel.length()) : null;
    }

    private int colorChannel(Numbers value) {
        return Math.max(0, Math.min(255, ((Number) value.getValue()).intValue()));
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
        List<Value> values = selectedModule.getValues();
        for (int index = 0; index < values.size(); index++) {
            if (!values.get(index).isVisible() || isColorContinuation(values, index)
                    || isHiddenStandaloneNumber(values, index)) {
                continue;
            }
            total += valueHeight(values, index);
            if (isColorStart(values, index)) {
                index += 2;
            }
        }
        return Math.max(0.0f, total - (h / scale - 170.0f));
    }

    private void drawSakuraPanel(float x, float y, float x2, float y2, float radius, float alpha) {
        RenderServices.shapes().shadow(x, y, x2, y2, radius, alpha(SHADOW, 92.0f * alpha * guiAlpha), 8, 3.4f);
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
        if (text == null || text.length() == 0 || (color >>> 24 & 255) <= 0) {
            return;
        }
        if (strength <= 0.0f) {
            font.drawString(text, x, y, color);
            return;
        }
        font.drawStringWithGlow(text, x, y, color,
                alpha(SAKURA, 184.0f * guiAlpha), strength, GlowProfile.TEXT);
    }

    private void drawGlowIcon(gq.yozakura.engine.font.CFontRenderer font, String text, float x, float y,
                              int color, float strength) {
        if (text == null || text.length() == 0 || (color >>> 24 & 255) <= 0) {
            return;
        }
        if (strength <= 0.0f) {
            font.drawString(text, x, y, color);
            return;
        }
        font.drawStringWithGlow(text, x, y, color,
                alpha(SAKURA_STRONG, 196.0f * guiAlpha), strength, GlowProfile.TEXT);
    }

    private void drawGlowCentered(gq.yozakura.engine.font.CFontRenderer font, String text, float centerX, float y,
                                  int color, float strength) {
        drawGlowText(font, text, centerX - font.getStringWidth(text) * 0.5f, y, color, strength);
    }

    private void drawChevron(gq.yozakura.engine.font.CFontRenderer font, float centerX, float centerY,
                             float expand, int color, float strength) {
        String glyph = ">";
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(centerX, centerY, 0.0f);
            GL11.glRotatef(90.0f * clamp(expand, 0.0f, 1.0f), 0.0f, 0.0f, 1.0f);
            drawGlowText(font, glyph, -font.getStringWidth(glyph) * 0.5f,
                    -font.getHeight() * 0.5f, color, strength);
        } finally {
            GL11.glPopMatrix();
        }
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
        RenderServices.shapes().circle(centerX, centerY, 0, 360, size * 0.30f, alpha(TEXT, 235.0f * alpha * guiAlpha));
    }

    private void drawPetal(float size, float alpha) {
        float width = size * 0.58f;
        float length = size * 1.12f;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glColor(TEXT, alpha * 0.96f * guiAlpha);
        GL11.glVertex2f(0.0f, length * 0.36f);
        for (float[] point : SAKURA_PETAL_POINTS) {
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
        return GLASS_SETTINGS;
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

    private void updateUiClock() {
        uiDeltaSeconds = uiClock.tick(System.nanoTime());
    }

    private static void refreshPalette() {
        ClickGUI.Palette requested = ClickGUI.palette.getValue();
        if (requested == appliedPalette && requested != ClickGUI.Palette.CUSTOM) {
            return;
        }
        appliedPalette = requested;
        PALETTE = ClickGUI.currentPalette();
        TEXT = PALETTE.getTextPrimary();
        MUTED = PALETTE.getTextSecondary();
        FAINT = PALETTE.getTextDisabled();
        SAKURA = PALETTE.getAccentPrimary();
        SAKURA_STRONG = PALETTE.getGlowPrimary();
        GLASS = PALETTE.getCanvas();
        GLASS_SOFT = PALETTE.getSurface();
        SHADOW = PALETTE.getShadow();
        SURFACE_RAISED = PALETTE.getSurfaceRaised();
        SURFACE_OVERLAY = PALETTE.getSurfaceOverlay();
        TRACK = PALETTE.getBorderSubtle();
    }

    private float animate(float current, float target, float speed) {
        return SakuraUiMotion.approach(current, target, speed, uiDeltaSeconds);
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

    private float easeOut(float value) {
        return AnimationUtil.ease(value, AnimationUtil.Ease.OUT_CUBIC);
    }

    private float easeSmooth(float value) {
        return AnimationUtil.ease(value, AnimationUtil.Ease.IN_OUT_CUBIC);
    }

    private float animatedModeHeight(Mode mode) {
        Float value = modeExpandProgress.get(mode);
        return easeSmooth(value == null ? (mode == expandedMode ? 1.0f : 0.0f) : value.floatValue());
    }

    private float animatedModeHeight(ModeProperty mode) {
        Float value = modePropertyExpandProgress.get(mode);
        return easeSmooth(value == null ? (mode == expandedModeProperty ? 1.0f : 0.0f) : value.floatValue());
    }

    private String modeRowKey(Value owner, String name) {
        return System.identityHashCode(owner) + ":" + (name == null ? "" : name);
    }

    private void requestClose() {
        if (closing) {
            return;
        }
        closing = true;
        draggingWindow = false;
        draggingNumber = null;
        clearColorDrag();
        expandedMode = null;
        expandedModeProperty = null;
    }

    private float introOffset() {
        return (1.0f - easeOut(openProgress)) * 14.0f;
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
