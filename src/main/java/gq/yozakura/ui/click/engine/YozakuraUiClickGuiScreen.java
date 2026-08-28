package gq.yozakura.ui.click.engine;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.ui.engine.api.DocumentContext;
import gq.yozakura.ui.engine.api.HtmlCssResourceLoader;
import gq.yozakura.ui.engine.dom.ElementNode;
import gq.yozakura.ui.engine.input.ModifierKeys;
import gq.yozakura.ui.engine.input.PointerButton;
import gq.yozakura.ui.engine.input.PointerEvent;
import gq.yozakura.ui.engine.layout.MeasureContext;
import gq.yozakura.ui.engine.paint.PaintCommandList;
import gq.yozakura.ui.engine.paint.PaintVisualState;
import gq.yozakura.ui.engine.render.CompiledPaint;
import gq.yozakura.ui.engine.render.LwjglUiRenderer;
import gq.yozakura.ui.engine.text.FontManager;
import gq.yozakura.ui.engine.text.GlyphAtlas;
import gq.yozakura.ui.engine.text.GlyphRasterizer;
import gq.yozakura.ui.engine.text.LwjglGlyphTextureBackend;
import gq.yozakura.util.minecraft.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.awt.Font;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Transparent in-game host for the custom retained HTML/CSS UI engine. */
public final class YozakuraUiClickGuiScreen extends GuiScreen {
    private static final float DESIGN_WIDTH = 960.0F;
    private static final float DESIGN_HEIGHT = 640.0F;
    private static final long OPEN_DURATION_MS = 190L;

    private final UiWindowGeometry geometry = new UiWindowGeometry(DESIGN_WIDTH, DESIGN_HEIGHT);
    private DocumentContext document;
    private YozakuraUiClickGuiModel model;
    private LwjglUiRenderer renderer;
    private CompiledPaint compiled;
    private PaintCommandList compiledSource;
    private Cursor hiddenCursor;
    private long openedAtMs;
    private ElementNode pressedElement;
    private boolean searchFocused;
    private long lastSyncMs;
    private String sliderModule;
    private String sliderValue;
    private float sliderX;
    private float sliderWidth;
    private String colorDragAction;
    private String colorModule;
    private String colorRed;
    private String colorGreen;
    private String colorBlue;
    private float colorX;
    private float colorY;
    private float colorWidth;
    private float colorHeight;
    private int lastPointerX = Integer.MIN_VALUE;
    private int lastPointerY = Integer.MIN_VALUE;
    private long lastPointerDispatchMs;
    private String bindingModule;
    private final Map<ElementNode, VisualValue> visualValues =
            new IdentityHashMap<ElementNode, VisualValue>();
    private final List<VisualMotion> visualMotions = new ArrayList<VisualMotion>();
    private ElementNode hoverMotionTarget;
    private boolean scrollDragging;
    private float scrollTrackY;
    private float scrollTrackHeight;
    private float scrollThumbHeight;
    private String pendingSettingsTarget;
    private long pendingSettingsAtMs;
    private String pendingModeModule;
    private String pendingModeValue;
    private long pendingModeAtMs;
    private boolean pendingPaletteClose;
    private long pendingPaletteAtMs;

    public static void open(Minecraft minecraft) {
        minecraft.displayGuiScreen(new YozakuraUiClickGuiScreen());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        geometry.updateViewport(width, height, new ScaledResolution(mc).getScaleFactor());
        if (document != null && renderer != null) {
            installCustomCursor();
            return;
        }
        openedAtMs = nowMs();
        try {
            document = HtmlCssResourceLoader.loadDocument(
                    "/assets/yozakura/ui/clickgui/index.html",
                    "/assets/yozakura/ui/clickgui/style.css",
                    new FixedMeasureContext());
            model = new YozakuraUiClickGuiModel(document.rootElement());
            document.markStyleDirty();
            document.recompute();

            FontManager fonts = new FontManager();
            fonts.registerResource("Inter", "/assets/minecraft/font/Inter.ttf", null);
            fonts.registerResource("Bricolage Grotesque",
                    "/assets/minecraft/font/BricolageGrotesque.ttf", null);
            fonts.registerResource("JetBrains Mono",
                    "/assets/minecraft/font/JetBrainsMono.ttf", null);
            fonts.registerResource("Alibaba Sans",
                    "/assets/minecraft/font/AlibabaSans-Regular.otf", null);
            fonts.registerResource("NovICON", "/assets/minecraft/font/NovICON.ttf", null);
            // AlibabaSans in this client is a compact subset and does not cover all CJK.
            // Dialog is an explicitly registered Java composite face backed by Windows CJK fonts.
            fonts.register("System CJK", new Font("Dialog", Font.PLAIN, 1), null);
            fonts.addFallback("Inter");
            fonts.addFallback("Alibaba Sans");
            fonts.addFallback("System CJK");
            GlyphAtlas atlas = new GlyphAtlas(1024, 1024, 1,
                    new LwjglGlyphTextureBackend(), new GlyphRasterizer());
            renderer = new LwjglUiRenderer(fonts, atlas);
            installCustomCursor();
            startModuleIntro(nowMs());
        } catch (Throwable error) {
            Helper.sendMessage("YozakuraUI ClickGUI failed: " + rootMessage(error));
            closeScreen();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (document == null || renderer == null) return;
        long frameTimeMs = nowMs();
        ScaledResolution resolution = new ScaledResolution(mc);
        geometry.updateViewport(width, height, resolution.getScaleFactor());
        if (geometry.isInteracting()) {
            geometry.updatePointer(mouseX, mouseY);
        } else if (sliderModule != null && Mouse.isButtonDown(0)) {
            updateSlider(toLocalX(mouseX));
        } else if (colorDragAction != null && Mouse.isButtonDown(0)) {
            updateColor(toLocalX(mouseX), toLocalY(mouseY));
        } else if (scrollDragging && Mouse.isButtonDown(0)) {
            updateScrollDrag(toLocalY(mouseY));
        } else if ((mouseX != lastPointerX || mouseY != lastPointerY)
                && frameTimeMs - lastPointerDispatchMs >= 30L) {
            float localX = toLocalX(mouseX);
            float localY = toLocalY(mouseY);
            document.dispatchPointer(PointerEvent.move(localX, localY, modifiers(), frameTimeMs), 0, 0);
            lastPointerX = mouseX;
            lastPointerY = mouseY;
            lastPointerDispatchMs = frameTimeMs;
            updateHoverMotion(frameTimeMs);
        }
        document.tick(frameTimeMs);
        tickVisualMotions(frameTimeMs);
        tickPendingActions(frameTimeMs);
        if (frameTimeMs - lastSyncMs >= 500L) {
            lastSyncMs = frameTimeMs;
            if (model.sync()) {
                resetVisualMotions();
                refreshDocument();
            }
        }
        document.recompute();
        PaintCommandList commands = document.paintCommands();
        if (commands != compiledSource) {
            compiled = renderer.compile(commands);
            compiledSource = commands;
        }

        float open = openingProgress();
        float easedOpen = ClickGuiMotion.openingEase(open);
        float scale = geometry.scale() * (0.96F + 0.04F * easedOpen);
        float originX = geometry.x() + DESIGN_WIDTH * (geometry.scale() - scale) * 0.5F;
        float originY = geometry.y() + DESIGN_HEIGHT * (geometry.scale() - scale) * 0.5F
                + 8.0F * (1.0F - easedOpen);
        try {
            renderer.render(compiled, width, height, scale, geometry.scale(),
                    resolution.getScaleFactor(),
                    originX, originY, mc.displayHeight);
        } catch (Throwable error) {
            Helper.sendMessage("YozakuraUI render failed: " + rootMessage(error));
            closeScreen();
            return;
        }
        drawClientCursor(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (document == null || !geometry.contains(mouseX, mouseY)) return;
        float localX = toLocalX(mouseX);
        float localY = toLocalY(mouseY);
        PointerButton button = PointerButton.fromLwjgl(mouseButton);
        if (button.isNone()) return;
        ElementNode hit = document.hitTest(localX, localY);
        ElementNode action = findAction(hit);
        pressedElement = action;
        document.dispatchPointer(PointerEvent.down(localX, localY, button,
                modifiers(), 1, nowMs()), 0, 0);
        if (action == null) {
            searchFocused = false;
            if (model.closePopups()) refreshDocument();
            return;
        }
        String kind = action.attribute("data-action");
        if (!kind.startsWith("setting-mode") && !kind.startsWith("setting-color")
                && !"palette".equals(kind)
                && model.closePopups()) {
            refreshDocument();
        }
        if (button.isLeft() && "drag-window".equals(kind)) {
            geometry.beginMove(mouseX, mouseY);
        } else if (button.isLeft() && "resize-window".equals(kind)) {
            geometry.beginResize(mouseX, mouseY);
        } else if (button.isLeft() && "scroll-thumb".equals(kind)) {
            beginScrollDrag(action, localY);
        } else if (button.isLeft() && "close".equals(kind)) {
            closeScreen();
        } else if (button.isLeft() && "language".equals(kind)) {
            resetVisualMotions();
            model.toggleLanguage();
            refreshDocument();
        } else if (button.isLeft() && "palette".equals(kind)) {
            if (model.globalPaletteElement().hasClass("open")) {
                animateTo(model.globalPaletteElement(), 0.0F, 18.0F, 0.0F,
                        240L, 0L, nowMs());
                pendingPaletteClose = true;
                pendingPaletteAtMs = nowMs() + 240L;
            } else {
                model.toggleGlobalPalette();
                refreshDocument();
                animateFrom(model.globalPaletteElement(), 0.0F, 18.0F, 0.0F,
                        0.0F, 0.0F, 1.0F, 320L, 0L, nowMs());
            }
        } else if (button.isLeft() && "category".equals(kind)) {
            resetVisualMotions();
            model.selectCategory(action.attribute("data-category"));
            refreshDocument();
            startModuleIntro(nowMs());
            startActiveAccent(nowMs());
        } else if ("toggle-module".equals(kind)) {
            String moduleName = action.attribute("data-module");
            if (button.isRight()) {
                toggleSettingsAnimated(moduleName, nowMs());
            } else if (button.isLeft()) {
                model.toggleModule(moduleName);
                beginToggleMotion(moduleName, nowMs());
            }
            if (button.isLeft()) refreshDocument();
        } else if ("settings-module".equals(kind) && button.isLeft()) {
            String moduleName = action.attribute("data-module");
            toggleSettingsAnimated(moduleName, nowMs());
        } else if ("setting-color".equals(kind) && button.isLeft()) {
            if (model.setColor(action.attribute("data-module"), action.attribute("data-red"),
                    action.attribute("data-green"), action.attribute("data-blue"),
                    action.attribute("data-color"))) {
                refreshDocument();
            }
        } else if ("setting-color-open".equals(kind) && button.isLeft()) {
            model.toggleColorPalette(action.attribute("data-module"), action.attribute("data-red"));
            refreshDocument();
        } else if (("setting-color-sv".equals(kind) || "setting-color-hue".equals(kind))
                && button.isLeft()) {
            beginColorDrag(action, kind, localX, localY);
        } else if ("setting-mode".equals(kind) && button.isLeft()) {
            String moduleName = action.attribute("data-module");
            String valueName = action.attribute("data-value");
            ElementNode menu = model.modeMenuElement(moduleName, valueName);
            if (menu != null) {
                animateTo(menu, 0.0F, -6.0F, 0.0F, 200L, 0L, nowMs());
                pendingModeModule = moduleName;
                pendingModeValue = valueName;
                pendingModeAtMs = nowMs() + 200L;
            } else {
                model.toggleModeDropdown(moduleName, valueName);
                refreshDocument();
                menu = model.modeMenuElement(moduleName, valueName);
                if (menu != null) animateFrom(menu, 0.0F, -6.0F, 0.0F,
                        0.0F, 0.0F, 1.0F, 260L, 0L, nowMs());
            }
        } else if ("setting-mode-option".equals(kind) && button.isLeft()) {
            model.selectModeOption(action.attribute("data-module"), action.attribute("data-value"),
                    action.attribute("data-option"));
            refreshDocument();
        } else if ("setting-keybind".equals(kind) && button.isLeft()) {
            bindingModule = action.attribute("data-module");
        } else if ("setting".equals(kind)) {
            String moduleName = action.attribute("data-module");
            String valueName = action.attribute("data-value");
            if (button.isLeft() && model.isNumberSetting(moduleName, valueName)) {
                float[] bounds = document.logicalBounds(action);
                if (bounds != null && bounds[2] > 0.0F) {
                    sliderModule = moduleName;
                    sliderValue = valueName;
                    sliderX = bounds[0];
                    sliderWidth = bounds[2];
                    updateSlider(localX);
                }
            } else {
                boolean toggleSetting = model.isBooleanSetting(moduleName, valueName);
                model.activateSetting(moduleName, valueName, button.isRight() ? -1 : 1);
                refreshDocument();
                if (toggleSetting) {
                    ElementNode knob = model.settingToggleKnob(moduleName, valueName);
                    boolean enabled = model.booleanSettingState(moduleName, valueName);
                    if (knob != null) animateFrom(knob,
                            enabled ? 0.0F : 16.0F, 0.0F, 1.0F,
                            enabled ? 16.0F : 0.0F, 0.0F, 1.0F,
                            200L, 0L, nowMs());
                }
            }
        } else if ("search".equals(kind) && button.isLeft()) {
            searchFocused = true;
            document.interactionState().focus(action);
        } else {
            searchFocused = false;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (geometry.isInteracting()) geometry.endInteraction();
        if (document != null) {
            PointerButton button = PointerButton.fromLwjgl(state);
            if (!button.isNone()) {
                document.dispatchPointer(PointerEvent.up(toLocalX(mouseX), toLocalY(mouseY),
                        button, modifiers(), 1, nowMs()), 0, 0);
            }
        }
        pressedElement = null;
        sliderModule = null;
        sliderValue = null;
        colorDragAction = null;
        colorModule = null;
        colorRed = null;
        colorGreen = null;
        colorBlue = null;
        scrollDragging = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || document == null) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (geometry.contains(mouseX, mouseY)) {
            model.scroll(wheel > 0 ? 1.0F : -1.0F);
            refreshDocument();
            document.dispatchPointer(PointerEvent.wheel(toLocalX(mouseX), toLocalY(mouseY),
                    wheel, modifiers(), nowMs()), 0, 0);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            int next = keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK
                    || keyCode == Keyboard.KEY_DELETE ? Keyboard.KEY_NONE : keyCode;
            resetVisualMotions();
            model.setKeybind(bindingModule, next);
            bindingModule = null;
            refreshDocument();
            return;
        }
        Module clickGui = ModuleManager.getModule("ClickGUI");
        int closeKey = clickGui == null ? Keyboard.KEY_NONE : clickGui.getKey();
        if (keyCode == Keyboard.KEY_ESCAPE
                || closeKey != Keyboard.KEY_NONE && keyCode == closeKey) {
            closeScreen();
            return;
        }
        if (!searchFocused || model == null) return;
        String value = model.search();
        if (keyCode == Keyboard.KEY_BACK && !value.isEmpty()) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        } else if (typedChar >= 32 && typedChar != 127) {
            value += typedChar;
        }
        resetVisualMotions();
        model.setSearch(value);
        refreshDocument();
        startModuleIntro(nowMs());
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        restoreSystemCursor();
        if (document != null) document.clearAll();
        if (renderer != null) renderer.dispose();
        renderer = null;
        compiled = null;
        compiledSource = null;
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void refreshDocument() {
        document.markStyleDirty();
        document.recompute();
    }

    private void updateSlider(float localX) {
        if (sliderModule == null || sliderWidth <= 0.0F) return;
        double ratio = (localX - sliderX) / sliderWidth;
        if (model.setNumberRatio(sliderModule, sliderValue, ratio)) refreshDocument();
    }

    private void beginScrollDrag(ElementNode thumb, float localY) {
        if (!(thumb.parent() instanceof ElementNode)) return;
        float[] track = document.logicalBounds((ElementNode) thumb.parent());
        float[] knob = document.logicalBounds(thumb);
        if (track == null || knob == null) return;
        scrollDragging = true;
        scrollTrackY = track[1];
        scrollTrackHeight = track[3];
        scrollThumbHeight = knob[3];
        updateScrollDrag(localY);
    }

    private void updateScrollDrag(float localY) {
        float travel = scrollTrackHeight - scrollThumbHeight;
        if (!scrollDragging || travel <= 0.0F) return;
        float ratio = (localY - scrollTrackY - scrollThumbHeight * 0.5F) / travel;
        model.setScrollRatio(ratio);
        refreshDocument();
    }

    private void beginColorDrag(ElementNode action, String kind, float localX, float localY) {
        float[] bounds = document.logicalBounds(action);
        if (bounds == null || bounds[2] <= 0.0F || bounds[3] <= 0.0F) return;
        colorDragAction = kind;
        colorModule = action.attribute("data-module");
        colorRed = action.attribute("data-red");
        colorGreen = action.attribute("data-green");
        colorBlue = action.attribute("data-blue");
        colorX = bounds[0];
        colorY = bounds[1];
        colorWidth = bounds[2];
        colorHeight = bounds[3];
        updateColor(localX, localY);
    }

    private void updateColor(float localX, float localY) {
        if (colorDragAction == null || colorWidth <= 0.0F || colorHeight <= 0.0F) return;
        HsvColor current = model.color(colorModule, colorRed, colorGreen, colorBlue);
        if (current == null) return;
        float x = Math.max(0.0F, Math.min(1.0F, (localX - colorX) / colorWidth));
        float y = Math.max(0.0F, Math.min(1.0F, (localY - colorY) / colorHeight));
        boolean changed;
        if ("setting-color-hue".equals(colorDragAction)) {
            changed = model.setColorHsv(colorModule, colorRed, colorGreen, colorBlue,
                    colorHeight > colorWidth ? y : x, current.saturation(), current.value());
        } else {
            changed = model.setColorHsv(colorModule, colorRed, colorGreen, colorBlue,
                    current.hue(), x, 1.0F - y);
        }
        if (changed) refreshDocument();
    }

    private void beginToggleMotion(String moduleName, long nowMs) {
        ElementNode knob = model.moduleToggleKnob(moduleName);
        if (knob == null) return;
        boolean enabled = model.moduleState(moduleName);
        animateFrom(knob, enabled ? 0.0F : 16.0F, 0.0F, 1.0F,
                enabled ? 16.0F : 0.0F, 0.0F, 1.0F, 200L, 0L, nowMs);
        ElementNode accent = model.moduleAccent(moduleName);
        if (enabled && accent != null) {
            animateFrom(accent, -3.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 1.0F, 280L, 0L, nowMs);
        }
    }

    private void startModuleIntro(long nowMs) {
        if (document == null || model == null) return;
        List<ElementNode> cards = model.visibleModuleCards();
        for (int i = 0; i < cards.size(); i++) {
            animateFrom(cards.get(i), 0.0F, 9.0F, 0.0F,
                    0.0F, 0.0F, 1.0F, 350L, i * 35L, nowMs);
        }
    }

    private void toggleSettingsAnimated(String moduleName, long nowMs) {
        String expanded = model.expandedModuleName();
        if (expanded == null || expanded.isEmpty()) {
            Map<ElementNode, Float> previousPositions = captureModulePositions();
            model.toggleSettings(moduleName);
            refreshDocument();
            animateModulePositionChanges(previousPositions, nowMs, 400L);
            ElementNode settings = model.settingsElement(moduleName);
            if (settings != null) animateFrom(settings, 0.0F, -7.0F, 0.0F,
                    0.0F, 0.0F, 1.0F, 300L, 0L, nowMs);
            return;
        }
        ElementNode current = model.settingsElement(expanded);
        if (current != null) animateTo(current, 0.0F, -7.0F, 0.0F,
                220L, 0L, nowMs);
        pendingSettingsTarget = normalizeName(expanded).equals(normalizeName(moduleName))
                ? "" : moduleName;
        pendingSettingsAtMs = nowMs + 220L;
    }

    private void tickPendingActions(long nowMs) {
        if (pendingSettingsTarget != null && nowMs >= pendingSettingsAtMs) {
            Map<ElementNode, Float> previousPositions = captureModulePositions();
            String current = model.expandedModuleName();
            if (current != null && !current.isEmpty()) {
                clearVisualSubtree(model.settingsElement(current));
                model.toggleSettings(current);
            }
            String target = pendingSettingsTarget;
            pendingSettingsTarget = null;
            if (!target.isEmpty()) model.toggleSettings(target);
            refreshDocument();
            animateModulePositionChanges(previousPositions, nowMs, 400L);
            if (!target.isEmpty()) {
                ElementNode settings = model.settingsElement(target);
                if (settings != null) animateFrom(settings, 0.0F, -7.0F, 0.0F,
                        0.0F, 0.0F, 1.0F, 300L, 0L, nowMs);
            }
        }
        if (pendingModeModule != null && nowMs >= pendingModeAtMs) {
            model.toggleModeDropdown(pendingModeModule, pendingModeValue);
            pendingModeModule = null;
            pendingModeValue = null;
            refreshDocument();
        }
        if (pendingPaletteClose && nowMs >= pendingPaletteAtMs) {
            pendingPaletteClose = false;
            model.toggleGlobalPalette();
            refreshDocument();
        }
    }

    private Map<ElementNode, Float> captureModulePositions() {
        Map<ElementNode, Float> positions = new IdentityHashMap<ElementNode, Float>();
        List<ElementNode> groups = model.visibleModuleGroups();
        for (int i = 0; i < groups.size(); i++) {
            ElementNode group = groups.get(i);
            float[] bounds = document.logicalBounds(group);
            if (bounds != null) positions.put(group, bounds[1]);
        }
        return positions;
    }

    private void animateModulePositionChanges(Map<ElementNode, Float> previousPositions,
                                              long nowMs, long durationMs) {
        List<ElementNode> groups = model.visibleModuleGroups();
        for (int i = 0; i < groups.size(); i++) {
            ElementNode group = groups.get(i);
            Float previous = previousPositions.get(group);
            float[] bounds = document.logicalBounds(group);
            if (previous == null || bounds == null) continue;
            float compensation = ClickGuiMotion.layoutCompensation(previous, bounds[1]);
            if (Math.abs(compensation) > 0.01F) {
                animateFrom(group, 0.0F, compensation, 1.0F,
                        0.0F, 0.0F, 1.0F, durationMs, 0L, nowMs);
            }
        }
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.replace(" ", "").replace("_", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private void startActiveAccent(long nowMs) {
        ElementNode accent = findFirstClass(document.rootElement(), "category-accent");
        if (accent != null) {
            animateFrom(accent, -3.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 1.0F, 350L, 0L, nowMs);
        }
    }

    private void updateHoverMotion(long nowMs) {
        ElementNode next = findMotionTarget(document.interactionState().hover());
        if (next == hoverMotionTarget) return;
        if (hoverMotionTarget != null) animateTo(hoverMotionTarget,
                0.0F, 0.0F, 1.0F, 220L, 0L, nowMs);
        hoverMotionTarget = next;
        if (next != null) {
            float y = next.hasClass("module-card") ? -2.0F : -1.0F;
            animateTo(next, 0.0F, y, 1.0F, 220L, 0L, nowMs);
        }
    }

    private static ElementNode findMotionTarget(ElementNode element) {
        ElementNode current = element;
        while (current != null) {
            if (current.hasClass("module-card") || current.hasClass("category")
                    || current.hasClass("settings-button") || current.hasClass("mode-control")
                    || current.hasClass("color-trigger") || current.hasClass("keybind-control")
                    || current.hasClass("color-swatch") || current.hasClass("mode-option")
                    || current.hasClass("toggle") || current.hasClass("setting-toggle")
                    || current.hasClass("setting-slider-knob")
                    || current.hasClass("title-button") || current.hasClass("close-button")) {
                return current;
            }
            current = current.parent();
        }
        return null;
    }

    private static ElementNode findFirstClass(ElementNode root, String className) {
        if (root.hasClass(className)) return root;
        for (int i = 0; i < root.childCount(); i++) {
            if (root.child(i) instanceof ElementNode) {
                ElementNode found = findFirstClass((ElementNode) root.child(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void animateFrom(ElementNode element, float fromX, float fromY, float fromOpacity,
                             float toX, float toY, float toOpacity, long durationMs,
                             long delayMs, long nowMs) {
        if (element == null) return;
        removeVisualMotion(element);
        setVisual(element, new VisualValue(fromX, fromY, fromOpacity));
        visualMotions.add(new VisualMotion(element, fromX, fromY, fromOpacity,
                toX, toY, toOpacity, nowMs + delayMs, durationMs));
    }

    private void animateTo(ElementNode element, float toX, float toY, float toOpacity,
                           long durationMs, long delayMs, long nowMs) {
        VisualValue from = visualValues.get(element);
        if (from == null) from = VisualValue.IDENTITY;
        animateFrom(element, from.x, from.y, from.opacity,
                toX, toY, toOpacity, durationMs, delayMs, nowMs);
    }

    private void removeVisualMotion(ElementNode element) {
        for (Iterator<VisualMotion> iterator = visualMotions.iterator(); iterator.hasNext(); ) {
            if (iterator.next().element == element) iterator.remove();
        }
    }

    private void resetVisualMotions() {
        if (document != null) {
            for (ElementNode element : visualValues.keySet()) {
                document.clearPaintVisualState(element);
            }
        }
        visualValues.clear();
        visualMotions.clear();
        hoverMotionTarget = null;
    }

    private void clearVisualSubtree(ElementNode element) {
        if (element == null) return;
        for (int i = 0; i < element.childCount(); i++) {
            if (element.child(i) instanceof ElementNode) {
                clearVisualSubtree((ElementNode) element.child(i));
            }
        }
        removeVisualMotion(element);
        if (visualValues.remove(element) != null) document.clearPaintVisualState(element);
        if (hoverMotionTarget == element) hoverMotionTarget = null;
    }

    private void tickVisualMotions(long nowMs) {
        for (Iterator<VisualMotion> iterator = visualMotions.iterator(); iterator.hasNext(); ) {
            VisualMotion motion = iterator.next();
            float raw = motion.durationMs <= 0L ? 1.0F
                    : (nowMs - motion.startedMs) / (float) motion.durationMs;
            if (raw < 0.0F) continue;
            float progress = Math.max(0.0F, Math.min(1.0F, raw));
            float eased = ClickGuiMotion.controlSpring(progress);
            VisualValue value = new VisualValue(
                    lerp(motion.fromX, motion.toX, eased),
                    lerp(motion.fromY, motion.toY, eased),
                    lerp(motion.fromOpacity, motion.toOpacity, eased));
            setVisual(motion.element, value);
            if (progress >= 1.0F) {
                iterator.remove();
                if (value.isIdentity()) {
                    visualValues.remove(motion.element);
                    document.clearPaintVisualState(motion.element);
                }
            }
        }
    }

    private void setVisual(ElementNode element, VisualValue value) {
        visualValues.put(element, value);
        document.setPaintVisualState(element,
                new PaintVisualState(value.x, value.y, value.opacity));
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private float toLocalX(float mouseX) { return (mouseX - geometry.x()) / geometry.scale(); }
    private float toLocalY(float mouseY) { return (mouseY - geometry.y()) / geometry.scale(); }

    private static ElementNode findAction(ElementNode element) {
        ElementNode current = element;
        while (current != null) {
            if (current.attribute("data-action") != null) return current;
            current = current.parent();
        }
        return null;
    }

    private void closeScreen() { mc.displayGuiScreen(null); }

    private static ModifierKeys modifiers() {
        return ModifierKeys.builder()
                .shift(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
                .ctrl(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
                .alt(Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU))
                .build();
    }

    private void installCustomCursor() {
        if (!Mouse.isCreated()) return;
        try {
            if (hiddenCursor != null) {
                Mouse.setNativeCursor(hiddenCursor);
                return;
            }
            IntBuffer pixels = BufferUtils.createIntBuffer(1);
            pixels.put(0x00000000).flip();
            hiddenCursor = new Cursor(1, 1, 0, 0, 1, pixels, null);
            Mouse.setNativeCursor(hiddenCursor);
        } catch (LWJGLException error) {
            throw new IllegalStateException("custom cursor initialization failed: " + error.getMessage(), error);
        }
    }

    private void restoreSystemCursor() {
        try {
            if (Mouse.isCreated()) Mouse.setNativeCursor(null);
        } catch (LWJGLException ignored) {
        }
        if (hiddenCursor != null) {
            hiddenCursor.destroy();
            hiddenCursor = null;
        }
    }

    private static void drawClientCursor(float x, float y) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0.035F, 0.035F, 0.045F, 1.0F);
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + 2.8F, y + 12.0F);
            GL11.glVertex2f(x + 5.2F, y + 7.2F);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + 5.2F, y + 7.2F);
            GL11.glVertex2f(x + 9.2F, y + 7.0F);
            GL11.glEnd();
            GL11.glColor4f(0.94F, 0.40F, 0.66F, 0.90F);
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2f(x + 1.0F, y + 1.8F);
            GL11.glVertex2f(x + 2.0F, y + 7.4F);
            GL11.glVertex2f(x + 3.1F, y + 5.8F);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private float openingProgress() {
        return Math.max(0.0F, Math.min(1.0F, (nowMs() - openedAtMs) / (float) OPEN_DURATION_MS));
    }

    private static long nowMs() { return System.nanoTime() / 1_000_000L; }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName() : message;
    }

    private static final class VisualValue {
        private static final VisualValue IDENTITY = new VisualValue(0.0F, 0.0F, 1.0F);
        private final float x;
        private final float y;
        private final float opacity;

        private VisualValue(float x, float y, float opacity) {
            this.x = x;
            this.y = y;
            this.opacity = opacity;
        }

        private boolean isIdentity() {
            return Math.abs(x) < 0.001F && Math.abs(y) < 0.001F
                    && Math.abs(opacity - 1.0F) < 0.001F;
        }
    }

    private static final class VisualMotion {
        private final ElementNode element;
        private final float fromX;
        private final float fromY;
        private final float fromOpacity;
        private final float toX;
        private final float toY;
        private final float toOpacity;
        private final long startedMs;
        private final long durationMs;

        private VisualMotion(ElementNode element, float fromX, float fromY, float fromOpacity,
                             float toX, float toY, float toOpacity,
                             long startedMs, long durationMs) {
            this.element = element;
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromOpacity = fromOpacity;
            this.toX = toX;
            this.toY = toY;
            this.toOpacity = toOpacity;
            this.startedMs = startedMs;
            this.durationMs = durationMs;
        }
    }

    private static final class FixedMeasureContext implements MeasureContext {
        @Override public int viewportWidth() { return (int) DESIGN_WIDTH; }
        @Override public int viewportHeight() { return (int) DESIGN_HEIGHT; }
        @Override public float rootFontSizePx() { return 14.0F; }
    }
}
