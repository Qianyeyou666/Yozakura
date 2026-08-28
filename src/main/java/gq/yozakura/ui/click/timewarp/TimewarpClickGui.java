package gq.yozakura.ui.click.timewarp;

import gq.yozakura.club.ClubConfig;
import gq.yozakura.club.ClubConfigSummary;
import gq.yozakura.club.ClubService;
import gq.yozakura.core.ClientLanguage;
import gq.yozakura.core.ConfigBridge;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.render.GLStateManager;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.ui.click.yozakura.ClickGuiRenderContext;
import gq.yozakura.ui.click.yozakura.EpsilonPanelFonts;
import gq.yozakura.ui.click.yozakura.EpsilonPanelIcons;
import gq.yozakura.ui.click.yozakura.PanelClickGuiCursor;
import gq.yozakura.ui.click.yozakura.PanelClickGuiLayout;
import gq.yozakura.ui.click.yozakura.PanelModuleKeybind;
import gq.yozakura.ui.click.yozakura.PanelPaletteColorControl;
import gq.yozakura.ui.click.yozakura.PanelPaletteColorPicker;
import gq.yozakura.ui.click.yozakura.PanelCloudConfigSearchModel;
import gq.yozakura.ui.click.yozakura.YozakuraPanelClickGui;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.value.Value;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Independent Timewarp ClickGUI. The legacy Panel screen remains a separate selectable UI. */
public final class TimewarpClickGui extends GuiScreen {
    private static final float VALUE_STEP = TimewarpClickGuiValueRenderer.ROW_HEIGHT
            + TimewarpClickGuiValueRenderer.ROW_GAP;
    private static final float CONFIG_ROW_HEIGHT = 34.0f;
    private static final float CONFIG_ROW_GAP = 2.0f;
    private static final float HALL_ROW_HEIGHT = 42.0f;
    private static final float HALL_ROW_GAP = 2.0f;

    private static final NavigationItem[] NAVIGATION = {
            new NavigationItem(ModuleType.Combat, TimewarpClickGuiPageTransition.Page.MODULES,
                    "Combat", "战斗", EpsilonPanelIcons.SWORDS),
            new NavigationItem(ModuleType.Player, TimewarpClickGuiPageTransition.Page.MODULES,
                    "Player", "玩家", EpsilonPanelIcons.PERSON),
            new NavigationItem(ModuleType.Movement, TimewarpClickGuiPageTransition.Page.MODULES,
                    "Move", "移动", EpsilonPanelIcons.DIRECTIONS_RUN),
            new NavigationItem(ModuleType.Render, TimewarpClickGuiPageTransition.Page.MODULES,
                    "Visuals", "视觉", EpsilonPanelIcons.BRUSH),
            new NavigationItem(ModuleType.World, TimewarpClickGuiPageTransition.Page.MODULES,
                    "World", "世界", EpsilonPanelIcons.CONFIG),
            new NavigationItem(ModuleType.Other, TimewarpClickGuiPageTransition.Page.MODULES,
                    "Misc", "其他", "</>"),
            new NavigationItem(null, TimewarpClickGuiPageTransition.Page.CONFIGS,
                    "Configs", "配置", EpsilonPanelIcons.CONFIG),
            new NavigationItem(ModuleType.Config, TimewarpClickGuiPageTransition.Page.SETTINGS,
                    "Settings", "设置", EpsilonPanelIcons.SETTINGS)
    };

    private final AnimationState animations = new AnimationState();
    private final TimewarpClickGuiAnimation windowAnimation = new TimewarpClickGuiAnimation();
    private final TimewarpClickGuiPageTransition pages = new TimewarpClickGuiPageTransition();
    private final TimewarpClickGuiValueRenderer valueRenderer =
            new TimewarpClickGuiValueRenderer(animations);
    private final PanelClickGuiCursor cursor = new PanelClickGuiCursor();
    private final PanelPaletteColorPicker paletteColorPicker = new PanelPaletteColorPicker();
    private PanelPaletteColorControl.Group paletteColorGroup;
    private TimewarpClickGuiGeometry.Rect paletteColorAnchor;
    private final ClubService clubService = ClubService.getInstance();
    private final List<String> configProfiles = new ArrayList<String>();

    private TimewarpClickGuiGeometry.Layout layout;
    private ModuleType selectedCategory = ModuleType.Combat;
    private float moduleScroll;
    private float detailScroll;
    private float moduleMaxScroll;
    private float detailMaxScroll;
    private float configScroll;
    private float configMaxScroll;
    private float settingsScroll;
    private float settingsMaxScroll;
    private long lastFrameNanos;
    private boolean closing;
    private boolean listeningKeybind;
    private boolean draggingWindow;
    private boolean resizingWindow;
    private float dragOffsetX;
    private float dragOffsetY;
    private float resizeStartMouseX;
    private float resizeStartMouseY;
    private float resizeStartWidth;
    private float resizeStartHeight;
    private float windowX;
    private float windowY;
    private float windowWidth;
    private float windowHeight;
    private int selectedConfigProfile = -1;
    private String configName = "default";
    private boolean configNameFocused;
    private String configStatus = "";
    private boolean configStatusError;
    private boolean parameterHallMode;
    private String selectedHallConfigId;
    private String hallSearchText = "";
    private boolean hallSearchFocused;
    private float hallScroll;
    private float hallMaxScroll;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        cursor.install();
        animations.clear();
        valueRenderer.closeDropdown();
        paletteColorPicker.close();
        paletteColorGroup = null;
        paletteColorAnchor = null;
        closing = false;
        listeningKeybind = false;
        parameterHallMode = false;
        hallSearchFocused = false;
        hallScroll = 0.0f;
        selectedHallConfigId = null;
        draggingWindow = false;
        resizingWindow = false;
        lastFrameNanos = System.nanoTime();
        windowAnimation.reset(false, lastFrameNanos);
        restoreWindowGeometry();
        rebuildLayout();
        refreshConfigProfiles(null);
    }

    @Override
    public void onGuiClosed() {
        persistWindowGeometry();
        ConfigBridge.saveModulesQuietly();
        Keyboard.enableRepeatEvents(false);
        valueRenderer.mouseReleased();
        valueRenderer.closeDropdown();
        paletteColorPicker.mouseReleased();
        paletteColorPicker.close();
        paletteColorGroup = null;
        paletteColorAnchor = null;
        draggingWindow = false;
        resizingWindow = false;
        cursor.restore();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ClubConfig pendingDownload = clubService.consumePendingDownload();
        if (pendingDownload != null) {
            try {
                String name = cleanConfigName(pendingDownload.getSummary().getName());
                ConfigBridge.saveProfileSnapshot(name, pendingDownload.getPayload().toString());
                refreshConfigProfiles(name);
                clubService.reportResult(language("Downloaded to Local: ", "已下载到本地：")
                        + name + ".yzk", false);
            } catch (IOException exception) {
                clubService.reportResult(language("Hall config download failed", "大厅参数下载失败"), true);
            }
        }
        ClubConfig pendingUse = clubService.consumePendingUse();
        if (pendingUse != null) {
            try {
                ConfigBridge.importSnapshot(pendingUse.getPayload().toString());
                clubService.reportResult(language("Applied hall parameters ", "已使用大厅参数：")
                        + pendingUse.getSummary().getName(), false);
            } catch (IOException exception) {
                clubService.reportResult(language("Hall config apply failed", "大厅参数使用失败"), true);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        long elapsed = Math.max(1L, Math.min(50_000_000L, now - lastFrameNanos));
        lastFrameNanos = now;
        float frameScale = (float) (elapsed / 16_666_667.0);
        if (draggingWindow) {
            updateWindowPosition(mouseX, mouseY);
        } else if (resizingWindow) {
            updateWindowSize(mouseX, mouseY);
        } else {
            rebuildLayout();
        }
        if (valueRenderer.isDraggingSlider()) {
            valueRenderer.updateDrag(mouseX);
        }
        pages.advance(frameScale);
        float rawOpen = windowAnimation.progressAt(!closing, now);
        float open = TimewarpClickGuiAnimation.easeOutCubic(rawOpen);
        TimewarpClickGuiTheme theme = TimewarpClickGuiTheme.current();

        GLStateManager.begin2D();
        try {
            ClickGuiRenderContext.activate(0.0f, 0.0f, 1.0f);
            RenderServices.shapes().rect(0.0f, 0.0f, width, height,
                    TimewarpClickGuiTheme.alpha(theme.window(), Math.round(118.0f * open)));
            TimewarpClickGuiGeometry.Rect window = layout.window();
            float centerX = window.x() + window.width() * 0.5f;
            float centerY = window.y() + window.height() * 0.5f;
            float scale = 0.96f + open * 0.04f;
            GL11.glPushMatrix();
            GL11.glTranslatef(centerX, centerY + (1.0f - open) * 9.0f, 0.0f);
            GL11.glScalef(scale, scale, 1.0f);
            GL11.glTranslatef(-centerX, -centerY, 0.0f);
            CFontRenderer.pushScaleCompensation();
            try {
                drawWindow(mouseX, mouseY, frameScale, open, theme);
            } finally {
                CFontRenderer.popScaleCompensation();
                GL11.glPopMatrix();
            }
        } finally {
            GLStateManager.end2D();
        }
        if (closing && windowAnimation.isClosed() && mc.currentScreen == this) {
            mc.displayGuiScreen(null);
        }
    }

    private void drawWindow(int mouseX, int mouseY, float frameScale, float open,
                            TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect window = layout.window();
        TimewarpClickGuiGeometry.Rect sidebar = layout.sidebar();
        TimewarpClickGuiGeometry.Rect content = layout.content();
        RenderServices.shapes().shadow(window.x(), window.y(), window.right(), window.bottom(),
                7.0f, theme.shadow(Math.round(150.0f * open)), 12, 16.0f);
        RenderServices.shapes().rounded(window.x(), window.y(), window.right(), window.bottom(),
                7.0f, theme.window());
        RenderServices.shapes().joinedRounded(sidebar.x(), sidebar.y(), sidebar.right(), sidebar.bottom(),
                7.0f, 0.0f, 0.0f, 7.0f, theme.sidebar());
        RenderServices.shapes().joinedRounded(content.x(), content.y(), content.right(), content.bottom(),
                0.0f, 7.0f, 7.0f, 0.0f, theme.content());
        RenderServices.shapes().rect(sidebar.right() - 1.0f, sidebar.y() + 8.0f,
                sidebar.right(), sidebar.bottom() - 8.0f, theme.divider());
        drawSidebar(mouseX, mouseY, frameScale, theme);
        drawPages(mouseX, mouseY, frameScale, open);
        drawResizeHandle(mouseX, mouseY, theme);
    }

    private void drawPages(int mouseX, int mouseY, float frameScale, float open) {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        GLStateManager.pushScissor(content.x(), content.y(), content.width(), content.height());
        try {
            float progress = pages.progress();
            if (pages.outgoing() != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(-content.width() * 0.07f * progress, 0.0f, 0.0f);
                drawPage(pages.outgoing(), pages.outgoingModule(), mouseX, mouseY,
                        frameScale, open * (1.0f - progress));
                GL11.glPopMatrix();
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(content.width() * 0.07f * (1.0f - progress), 0.0f, 0.0f);
            drawPage(pages.current(), pages.detailModule(), mouseX, mouseY,
                    frameScale, open * progress);
            GL11.glPopMatrix();
        } finally {
            GLStateManager.popScissor();
        }
    }

    private void drawPage(TimewarpClickGuiPageTransition.Page page, Module detailModule,
                          int mouseX, int mouseY, float frameScale, float visibility) {
        if (visibility <= 0.001f) {
            return;
        }
        if (page == TimewarpClickGuiPageTransition.Page.DETAIL) {
            drawDetailPage(detailModule, mouseX, mouseY, frameScale, visibility);
        } else if (page == TimewarpClickGuiPageTransition.Page.CONFIGS) {
            drawConfigsPage(mouseX, mouseY, frameScale, visibility);
        } else if (page == TimewarpClickGuiPageTransition.Page.SETTINGS) {
            drawSettingsPage(mouseX, mouseY, frameScale, visibility);
        } else {
            drawModulePage(mouseX, mouseY, frameScale, visibility);
        }
    }

    private void drawSidebar(int mouseX, int mouseY, float frameScale,
                             TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect sidebar = layout.sidebar();
        EpsilonPanelFonts.text(0.48f).drawString("yozakura", sidebar.x() + 14.0f,
                sidebar.y() + 18.0f, theme.secondary());
        RenderServices.shapes().circle(sidebar.right() - 16.0f, sidebar.y() + 20.0f,
                0, 360, 2.0f, theme.accent());

        int selectedIndex = selectedNavigationIndex();
        TimewarpClickGuiGeometry.Rect selected = TimewarpClickGuiGeometry.navigationItem(layout, selectedIndex);
        float selectedY = animations.animateFrom("nav-selection-y", selected.y(), 0.22f,
                frameScale, selected.y());
        RenderServices.shapes().rounded(selected.x(), selectedY, selected.right(),
                selectedY + selected.height(), 4.0f, theme.accentSoft());
        RenderServices.shapes().rounded(selected.x(), selectedY + 6.0f, selected.x() + 2.0f,
                selectedY + selected.height() - 6.0f, 1.0f, theme.accent());

        for (int index = 0; index < NAVIGATION.length; index++) {
            NavigationItem item = NAVIGATION[index];
            TimewarpClickGuiGeometry.Rect bounds = TimewarpClickGuiGeometry.navigationItem(layout, index);
            boolean hovered = bounds.contains(mouseX, mouseY);
            float hover = animations.animateFrom("nav-hover:" + index,
                    hovered ? 1.0f : 0.0f, 0.24f, frameScale, 0.0f);
            boolean active = index == selectedIndex;
            if (!active && hover > 0.01f) {
                RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                        6.0f, TimewarpClickGuiTheme.alpha(theme.text(), Math.round(12.0f * hover)));
            }
            int color = active ? theme.text()
                    : TimewarpClickGuiTheme.blend(theme.muted(), theme.secondary(), hover);
            drawNavigationIcon(item, bounds.x() + 13.0f + hover,
                    bounds.y() + bounds.height() * 0.5f, color);
            EpsilonPanelFonts.text(0.51f).drawString(label(item),
                    bounds.x() + 27.0f + hover,
                    EpsilonPanelFonts.centeredY(bounds.y(), bounds.height(), 0.51f), color);
        }
    }

    private void drawNavigationIcon(NavigationItem item, float centerX, float centerY, int color) {
        if ("</>".equals(item.icon)) {
            EpsilonPanelFonts.text(0.52f).drawString(item.icon, centerX - 8.0f,
                    centerY - 5.0f, color);
        } else {
            EpsilonPanelFonts.drawCenteredIcon(item.icon, centerX, centerY, 0.52f, color);
        }
    }

    private void drawModulePage(int mouseX, int mouseY, float frameScale, float pageVisibility) {
        TimewarpClickGuiTheme theme = TimewarpClickGuiTheme.current();
        TimewarpClickGuiGeometry.Rect content = layout.content();
        EpsilonPanelFonts.text(0.68f).drawString(categoryTitle(selectedCategory),
                content.x() + 14.0f, content.y() + 14.0f,
                TimewarpClickGuiTheme.alpha(theme.text(), Math.round(255.0f * pageVisibility)));
        EpsilonPanelFonts.text(0.41f).drawString(moduleSubtitle(), content.x() + 14.0f,
                content.y() + 32.0f,
                TimewarpClickGuiTheme.alpha(theme.muted(), Math.round(255.0f * pageVisibility)));

        List<Module> modules = modulesInCategory(selectedCategory);
        TimewarpClickGuiGeometry.Rect viewport = TimewarpClickGuiGeometry.moduleViewport(layout);
        moduleMaxScroll = Math.max(0.0f, modules.size() *
                (TimewarpClickGuiGeometry.MODULE_ROW_HEIGHT + TimewarpClickGuiGeometry.MODULE_ROW_GAP)
                - viewport.height());
        moduleScroll = clamp(moduleScroll, 0.0f, moduleMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            for (int index = 0; index < modules.size(); index++) {
                Module module = modules.get(index);
                TimewarpClickGuiGeometry.Rect row = TimewarpClickGuiGeometry.moduleRow(layout, index, moduleScroll);
                float target = TimewarpClickGuiAnimation.stagger(pageVisibility, index, modules.size());
                float entry = animations.animateFrom("module-entry:" + module.getName(), target,
                        0.26f, frameScale, 0.0f);
                if (entry <= 0.001f) {
                    continue;
                }
                GL11.glPushMatrix();
                GL11.glTranslatef((1.0f - entry) * 14.0f, 0.0f, 0.0f);
                drawModuleCard(module, row, mouseX, mouseY, frameScale, entry, theme);
                GL11.glPopMatrix();
            }
        } finally {
            GLStateManager.popScissor();
        }
    }

    private void drawModuleCard(Module module, TimewarpClickGuiGeometry.Rect row,
                                int mouseX, int mouseY, float frameScale, float visibility,
                                TimewarpClickGuiTheme theme) {
        boolean hovered = row.contains(mouseX, mouseY);
        float hover = animations.animateFrom("module-hover:" + module.getName(),
                hovered ? 1.0f : 0.0f, 0.24f, frameScale, 0.0f);
        float enabled = animations.animateFrom("module-toggle:" + module.getName(),
                module.getState() ? 1.0f : 0.0f, 0.24f, frameScale,
                module.getState() ? 1.0f : 0.0f);
        int rowColor = TimewarpClickGuiTheme.blend(theme.content(), theme.cardHover(), hover * 0.72f);
        RenderServices.shapes().rounded(row.x(), row.y(), row.right(), row.bottom(),
                3.0f, TimewarpClickGuiTheme.alpha(rowColor, Math.round(255.0f * visibility)));
        RenderServices.shapes().rect(row.x() + 8.0f, row.bottom() - 1.0f, row.right() - 8.0f,
                row.bottom(), TimewarpClickGuiTheme.alpha(theme.divider(),
                        Math.round(150.0f * visibility))); // module-row-divider
        EpsilonPanelFonts.text(0.56f).drawString(moduleName(module), row.x() + 10.0f,
                row.y() + 8.0f,
                TimewarpClickGuiTheme.alpha(TimewarpClickGuiTheme.blend(theme.text(),
                        theme.accent(), enabled * 0.20f), Math.round(255.0f * visibility)));
        String description = module.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = language("No description", "暂无描述");
        }
        GLStateManager.pushScissor(row.x() + 10.0f, row.y(), row.width() - 90.0f, row.height());
        try {
            EpsilonPanelFonts.text(0.40f).drawString(description, row.x() + 10.0f,
                    row.y() + 25.0f,
                    TimewarpClickGuiTheme.alpha(theme.muted(), Math.round(255.0f * visibility)));
        } finally {
            GLStateManager.popScissor();
        }
        TimewarpClickGuiGeometry.Rect gear = moduleSettingsBounds(row);
        drawGear(gear.x() + gear.width() * 0.5f, gear.y() + gear.height() * 0.5f,
                gear.contains(mouseX, mouseY) ? theme.text() : theme.muted());
        drawModuleToggle(moduleToggleBounds(row), enabled, 0.0f, theme);
    }

    private void drawModuleToggle(TimewarpClickGuiGeometry.Rect toggle, float progress,
                                  float lift, TimewarpClickGuiTheme theme) {
        float y = toggle.y() - lift;
        RenderServices.shapes().rounded(toggle.x(), y, toggle.right(), y + toggle.height(),
                toggle.height() * 0.5f,
                TimewarpClickGuiTheme.blend(theme.control(), theme.accentSoft(), progress));
        float knobSize = 9.0f + progress;
        float stretch = progress * (1.0f - progress) * 3.0f;
        float centerX = toggle.x() + 6.0f + (toggle.width() - 12.0f) * progress;
        RenderServices.shapes().rounded(centerX - knobSize * 0.5f - stretch, y + 3.0f,
                centerX + knobSize * 0.5f + stretch, y + toggle.height() - 3.0f,
                knobSize * 0.5f, progress > 0.5f ? 0xFFFFF0F5 : theme.secondary());
    }

    private void drawDetailPage(Module module, int mouseX, int mouseY,
                                float frameScale, float pageVisibility) {
        if (module == null) {
            return;
        }
        TimewarpClickGuiTheme theme = TimewarpClickGuiTheme.current();
        TimewarpClickGuiGeometry.Rect content = layout.content();
        EpsilonPanelFonts.text(0.82f).drawString(moduleName(module), content.x() + 16.0f,
                content.y() + 17.0f,
                TimewarpClickGuiTheme.alpha(theme.text(), Math.round(255.0f * pageVisibility)));
        EpsilonPanelFonts.text(0.46f).drawString(language("Module settings", "模块设置"),
                content.x() + 16.0f, content.y() + 37.0f,
                TimewarpClickGuiTheme.alpha(theme.muted(), Math.round(255.0f * pageVisibility)));
        drawDetailKeybind(module, mouseX, mouseY, theme, pageVisibility);
        drawPageClose(mouseX, mouseY, theme);

        TimewarpClickGuiGeometry.Rect viewport = TimewarpClickGuiGeometry.detailViewport(layout);
        List<Value> visible = visibleValues(module);
        detailMaxScroll = Math.max(0.0f, valueContentHeight(visible) - viewport.height());
        detailScroll = clamp(detailScroll, 0.0f, detailMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            float y = viewport.y() - detailScroll;
            drawSectionLabel(language("GENERAL", "常规"), viewport.x() + 2.0f, y,
                    viewport.width(), pageVisibility, theme);
            y += 23.0f;
            for (int index = 0; index < visible.size(); index++) {
                Value<?> value = visible.get(index);
                if (index > 0 && valueGroup(visible.get(index - 1)) != valueGroup(value)) {
                    y += TimewarpClickGuiValueRenderer.GROUP_GAP;
                    drawSectionLabel(valueGroupLabel(value), viewport.x() + 2.0f, y,
                            viewport.width(), pageVisibility, theme);
                    y += 23.0f;
                }
                float entry = TimewarpClickGuiAnimation.stagger(pageVisibility, index, visible.size());
                TimewarpClickGuiGeometry.Rect row = new TimewarpClickGuiGeometry.Rect(
                        viewport.x() + (1.0f - entry) * 12.0f, y,
                        viewport.width(), TimewarpClickGuiValueRenderer.ROW_HEIGHT);
                valueRenderer.drawValue(value, valueKey(module, value), row, index, visible.size(),
                        mouseX, mouseY, frameScale);
                y += VALUE_STEP;
            }
        } finally {
            GLStateManager.popScissor();
        }
        valueRenderer.drawOpenDropdown(mouseX, mouseY, frameScale, content);
    }

    private void drawDetailKeybind(Module module, int mouseX, int mouseY,
                                   TimewarpClickGuiTheme theme, float visibility) {
        TimewarpClickGuiGeometry.Rect bounds = TimewarpClickGuiGeometry.detailKeybindButton(layout);
        boolean hovered = bounds.contains(mouseX, mouseY);
        float focus = listeningKeybind ? 1.0f : 0.0f;
        float hover = animations.animateFrom("detail-keybind-hover", hovered ? 1.0f : 0.0f,
                0.24f, 1.0f, 0.0f);
        int background = TimewarpClickGuiTheme.blend(theme.control(), theme.accentSoft(),
                Math.max(focus, hover * 0.55f));
        RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                5.0f, TimewarpClickGuiTheme.alpha(background, Math.round(255.0f * visibility)));
        String key = listeningKeybind ? "..." : compactKeyName(module.getKey());
        String label = language("Bind ", "按键 ") + key;
        EpsilonPanelFonts.drawCenteredText(label, bounds.x() + bounds.width() * 0.5f,
                bounds.y(), bounds.height(), 0.46f,
                TimewarpClickGuiTheme.alpha(focus > 0.0f || hovered ? theme.text() : theme.secondary(),
                        Math.round(255.0f * visibility)));
    }

    private void drawSectionLabel(String label, float x, float y, float width,
                                  float visibility, TimewarpClickGuiTheme theme) {
        int alpha = Math.round(255.0f * visibility);
        EpsilonPanelFonts.text(0.43f).drawString(label, x, y + 2.0f,
                TimewarpClickGuiTheme.alpha(theme.muted(), alpha));
        float labelWidth = EpsilonPanelFonts.text(0.43f).getStringWidth(label);
        RenderServices.shapes().rect(x + labelWidth + 9.0f, y + 8.0f,
                x + width, y + 9.0f, TimewarpClickGuiTheme.alpha(theme.divider(), alpha));
    }

    private void drawConfigsPage(int mouseX, int mouseY, float frameScale, float visibility) {
        TimewarpClickGuiTheme theme = TimewarpClickGuiTheme.current();
        TimewarpClickGuiGeometry.Rect content = layout.content();
        int alpha = Math.round(255.0f * visibility);
        EpsilonPanelFonts.text(0.82f).drawString(language("Configs", "配置"),
                content.x() + 16.0f, content.y() + 14.0f,
                TimewarpClickGuiTheme.alpha(theme.text(), alpha));
        drawConfigTab(localConfigTabBounds(), language("Local", "本地"), !parameterHallMode,
                mouseX, mouseY, theme);
        drawConfigTab(parameterHallTabBounds(), language("Parameter Hall", "参数大厅"),
                parameterHallMode, mouseX, mouseY, theme);
        if (parameterHallMode) {
            drawParameterHall(mouseX, mouseY, frameScale, theme);
        } else {
            drawLocalConfigs(mouseX, mouseY, frameScale, theme);
        }
    }

    private void drawLocalConfigs(int mouseX, int mouseY, float frameScale,
                                  TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        TimewarpClickGuiGeometry.Rect name = configNameBounds();
        RenderServices.shapes().rounded(name.x(), name.y(), name.right(), name.bottom(), 4.0f,
                configNameFocused ? theme.accentSoft() : theme.control());
        String shown = configName.isEmpty() && !configNameFocused
                ? language("Profile name", "配置名称") : configName;
        EpsilonPanelFonts.text(0.50f).drawString(shown, name.x() + 8.0f,
                EpsilonPanelFonts.centeredY(name.y(), name.height(), 0.50f),
                configName.isEmpty() ? theme.muted() : theme.text());
        drawButton(configSaveBounds(), language("Save", "保存"), mouseX, mouseY, theme, false);
        drawButton(configLoadBounds(), language("Load", "加载"), mouseX, mouseY, theme, false);
        drawButton(configRefreshBounds(), language("Refresh", "刷新"), mouseX, mouseY, theme, false);
        drawButton(configFolderBounds(), language("Folder", "目录"), mouseX, mouseY, theme, false);

        TimewarpClickGuiGeometry.Rect viewport = configViewport();
        configMaxScroll = Math.max(0.0f,
                configProfiles.size() * (CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP) - viewport.height());
        configScroll = clamp(configScroll, 0.0f, configMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            for (int index = 0; index < configProfiles.size(); index++) {
                String profile = configProfiles.get(index);
                TimewarpClickGuiGeometry.Rect row = configRow(index);
                boolean hovered = row.contains(mouseX, mouseY);
                boolean selected = index == selectedConfigProfile;
                float hover = animations.animateFrom("config-hover:" + profile,
                        hovered ? 1.0f : 0.0f, 0.24f, frameScale, 0.0f);
                int color = selected ? theme.accentSoft()
                        : TimewarpClickGuiTheme.blend(theme.content(), theme.cardHover(), hover);
                RenderServices.shapes().rounded(row.x(), row.y(), row.right(), row.bottom(),
                        3.0f, color);
                EpsilonPanelFonts.text(0.52f).drawString(profile, row.x() + 9.0f,
                        EpsilonPanelFonts.centeredY(row.y(), row.height(), 0.52f),
                        selected ? theme.text() : theme.secondary());
            }
        } finally {
            GLStateManager.popScissor();
        }
        if (!configStatus.isEmpty()) {
            EpsilonPanelFonts.text(0.43f).drawString(configStatus, content.x() + 16.0f,
                    content.bottom() - 14.0f, configStatusError ? theme.danger() : theme.accent());
        }
    }

    private void drawParameterHall(int mouseX, int mouseY, float frameScale,
                                   TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        ClubService.ClubViewState state = clubService.getState();
        String identity = state.isAuthenticated()
                ? language("Uploader: ", "上传身份：") + state.getUsername()
                : language("Browse and download without sign-in", "无需登录即可浏览和下载");
        EpsilonPanelFonts.text(0.43f).drawString(identity, content.x() + 16.0f,
                content.y() + 71.0f, state.isAuthenticated() ? theme.secondary() : theme.muted());

        TimewarpClickGuiGeometry.Rect search = hallSearchBounds();
        RenderServices.shapes().rounded(search.x(), search.y(), search.right(), search.bottom(),
                4.0f, hallSearchFocused ? theme.accentSoft() : theme.control());
        String searchText = hallSearchText.isEmpty() && !hallSearchFocused
                ? language("Search name or uploader", "搜索名称或上传者") : hallSearchText;
        EpsilonPanelFonts.text(0.48f).drawString(searchText, search.x() + 8.0f,
                EpsilonPanelFonts.centeredY(search.y(), search.height(), 0.48f),
                hallSearchText.isEmpty() ? theme.muted() : theme.text());

        List<ClubConfigSummary> configs = visibleHallConfigs(state);
        ensureHallSelection(configs);
        TimewarpClickGuiGeometry.Rect viewport = hallViewport();
        hallMaxScroll = Math.max(0.0f,
                configs.size() * (HALL_ROW_HEIGHT + HALL_ROW_GAP) - viewport.height());
        hallScroll = clamp(hallScroll, 0.0f, hallMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            if (configs.isEmpty()) {
                EpsilonPanelFonts.drawCenteredText(language("No hall parameters", "参数大厅暂无内容"),
                        viewport.x() + viewport.width() * 0.5f, viewport.y(), viewport.height(),
                        0.50f, theme.muted());
            }
            for (int index = 0; index < configs.size(); index++) {
                ClubConfigSummary config = configs.get(index);
                TimewarpClickGuiGeometry.Rect row = hallRow(index);
                boolean selected = config.getId().equals(selectedHallConfigId);
                boolean hovered = row.contains(mouseX, mouseY);
                float hover = animations.animateFrom("hall-hover:" + config.getId(),
                        hovered ? 1.0f : 0.0f, 0.24f, frameScale, 0.0f);
                int color = selected ? theme.accentSoft()
                        : TimewarpClickGuiTheme.blend(theme.content(), theme.cardHover(), hover);
                RenderServices.shapes().rounded(row.x(), row.y(), row.right(), row.bottom(),
                        3.0f, color);
                EpsilonPanelFonts.text(0.51f).drawString(config.getName(), row.x() + 9.0f,
                        row.y() + 7.0f, selected ? theme.text() : theme.secondary());
                String owner = language("by ", "上传者：")
                        + (config.getOwner() == null ? "-" : config.getOwner());
                EpsilonPanelFonts.text(0.39f).drawString(owner, row.x() + 9.0f,
                        row.y() + 24.0f, theme.muted());
                if (state.ownsConfig(config.getId())) {
                    EpsilonPanelFonts.text(0.38f).drawString(language("YOURS", "我的"),
                            row.right() - 32.0f, row.y() + 8.0f, theme.accent());
                }
            }
        } finally {
            GLStateManager.popScissor();
        }

        boolean available = !state.isBusy();
        boolean selected = selectedHallConfig() != null;
        drawHallAction(hallUploadBounds(), language("Upload", "上传"), mouseX, mouseY, theme,
                available && selectedConfigName() != null, false);
        drawHallAction(hallDownloadBounds(), language("Download", "下载"), mouseX, mouseY, theme,
                available && selected, false);
        drawHallAction(hallUseBounds(), language("Use", "使用"), mouseX, mouseY, theme,
                available && selected, false);
        drawHallAction(hallDeleteBounds(), language("Delete", "删除"), mouseX, mouseY, theme,
                available && ownsSelectedHallConfig(state), true);
        drawHallAction(hallRefreshBounds(), language("Refresh", "刷新"), mouseX, mouseY, theme,
                available, false);
        if (!state.getStatus().isEmpty()) {
            EpsilonPanelFonts.text(0.40f).drawString(state.getStatus(), content.x() + 16.0f,
                    content.bottom() - 13.0f, state.isError() ? theme.danger() : theme.muted());
        }
    }

    private void drawConfigTab(TimewarpClickGuiGeometry.Rect bounds, String label, boolean selected,
                               int mouseX, int mouseY, TimewarpClickGuiTheme theme) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (selected || hovered) {
            RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                    4.0f, selected ? theme.accentSoft() : theme.control());
        }
        EpsilonPanelFonts.drawCenteredText(label, bounds.x() + bounds.width() * 0.5f,
                bounds.y(), bounds.height(), 0.46f, selected ? theme.text() : theme.muted());
    }

    private void drawHallAction(TimewarpClickGuiGeometry.Rect bounds, String label,
                                int mouseX, int mouseY, TimewarpClickGuiTheme theme,
                                boolean enabled, boolean danger) {
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int color = enabled ? theme.control() : TimewarpClickGuiTheme.alpha(theme.control(), 110);
        if (hovered) {
            color = danger ? TimewarpClickGuiTheme.alpha(theme.danger(), 105) : theme.accentSoft();
        }
        RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                3.0f, color);
        EpsilonPanelFonts.drawCenteredText(label, bounds.x() + bounds.width() * 0.5f,
                bounds.y(), bounds.height(), 0.40f,
                enabled ? (hovered ? theme.text() : theme.secondary()) : theme.muted());
    }

    private void drawSettingsPage(int mouseX, int mouseY, float frameScale, float visibility) {
        TimewarpClickGuiTheme theme = TimewarpClickGuiTheme.current();
        TimewarpClickGuiGeometry.Rect content = layout.content();
        int alpha = Math.round(255.0f * visibility);
        EpsilonPanelFonts.text(0.82f).drawString(language("Settings", "设置"),
                content.x() + 16.0f, content.y() + 17.0f,
                TimewarpClickGuiTheme.alpha(theme.text(), alpha));
        EpsilonPanelFonts.text(0.46f).drawString(language("Client appearance and behavior", "客户端外观与行为"),
                content.x() + 16.0f, content.y() + 37.0f,
                TimewarpClickGuiTheme.alpha(theme.muted(), alpha));
        TimewarpClickGuiGeometry.Rect viewport = TimewarpClickGuiGeometry.detailViewport(layout);
        List<Value> settings = settingsValues();
        Value<?>[] controls = new Value<?>[]{ClickGUI.guiStyle, ClickGUI.palette, ClickGUI.language};
        settings.remove(ClickGUI.guiStyle);
        settings.remove(ClickGUI.palette);
        settings.remove(ClickGUI.language);
        settingsMaxScroll = Math.max(0.0f, settingsContentHeight(controls.length, settings.size())
                - viewport.height());
        settingsScroll = clamp(settingsScroll, 0.0f, settingsMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            float y = viewport.y() - settingsScroll;
            drawSectionLabel(language("APPEARANCE", "外观"), viewport.x() + 2.0f, y,
                    viewport.width(), visibility, theme);
            y += 23.0f;
            for (int index = 0; index < controls.length; index++) {
                Value<?> value = controls[index];
                TimewarpClickGuiGeometry.Rect row = new TimewarpClickGuiGeometry.Rect(
                        viewport.x(), y, viewport.width(), TimewarpClickGuiValueRenderer.ROW_HEIGHT);
                valueRenderer.drawValue(value, "ClickGUI:" + value.getName(), row, index,
                        controls.length + settings.size(), mouseX, mouseY, frameScale);
                y += VALUE_STEP;
            }
            if (!settings.isEmpty()) {
                y += TimewarpClickGuiValueRenderer.GROUP_GAP;
                drawSectionLabel(language("ADVANCED", "高级"), viewport.x() + 2.0f, y,
                        viewport.width(), visibility, theme);
                y += 23.0f;
            }
            for (int index = 0; index < settings.size(); index++) {
                Value<?> value = settings.get(index);
                TimewarpClickGuiGeometry.Rect row = new TimewarpClickGuiGeometry.Rect(
                        viewport.x(), y, viewport.width(), TimewarpClickGuiValueRenderer.ROW_HEIGHT);
                PanelPaletteColorControl.Group colorGroup = PanelPaletteColorControl.groupFor(value);
                if (colorGroup != null) {
                    drawPaletteColorRow(colorGroup, row, mouseX, mouseY, visibility, theme);
                } else {
                    valueRenderer.drawValue(value, "ClickGUI:" + value.getName(), row,
                            index + controls.length, controls.length + settings.size(),
                            mouseX, mouseY, frameScale);
                }
                y += VALUE_STEP;
            }
        } finally {
            GLStateManager.popScissor();
        }
        valueRenderer.drawOpenDropdown(mouseX, mouseY, frameScale, content);
        drawPaletteColorPicker(mouseX, mouseY);
    }

    private void drawPaletteColorRow(PanelPaletteColorControl.Group group,
                                     TimewarpClickGuiGeometry.Rect row, int mouseX, int mouseY,
                                     float visibility, TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect swatch = paletteSwatchBounds(row);
        boolean hovered = swatch.contains(mouseX, mouseY);
        int alpha = Math.round(255.0f * visibility);
        EpsilonPanelFonts.text(0.50f).drawString(group.label(), row.x(), row.y() + 7.0f,
                TimewarpClickGuiTheme.alpha(theme.secondary(), alpha));
        RenderServices.shapes().shadow(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(),
                4.0f, theme.shadow(hovered ? 96 : 64), 4, hovered ? 4.0f : 2.0f);
        RenderServices.shapes().rounded(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(),
                4.0f, TimewarpClickGuiTheme.alpha(group.color(), alpha));
        RenderServices.shapes().roundedBorderWH(swatch.x(), swatch.y(), swatch.width(), swatch.height(),
                4.0f, 1.0f, 0x00000000,
                TimewarpClickGuiTheme.alpha(hovered ? theme.accent() : theme.divider(), alpha));
        if (paletteColorPicker.isOpen() && paletteColorPicker.group() == group) {
            paletteColorGroup = group;
            paletteColorAnchor = swatch;
        }
    }

    private void drawPaletteColorPicker(int mouseX, int mouseY) {
        if (!paletteColorPicker.isOpen() || paletteColorAnchor == null || layout == null) {
            return;
        }
        PanelClickGuiLayout.Rect popup = paletteColorPicker.bounds(
                panelRect(paletteColorAnchor), panelRect(layout.content()));
        paletteColorPicker.draw(popup, mouseX, mouseY);
    }

    private void drawPageClose(int mouseX, int mouseY, TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect close = TimewarpClickGuiGeometry.closeButton(layout);
        boolean hovered = close.contains(mouseX, mouseY);
        if (hovered) {
            RenderServices.shapes().rounded(close.x(), close.y(), close.right(), close.bottom(),
                    close.height() * 0.5f, theme.control());
        }
        drawClose(close, hovered ? theme.text() : theme.muted());
    }

    private void drawResizeHandle(int mouseX, int mouseY, TimewarpClickGuiTheme theme) {
        TimewarpClickGuiGeometry.Rect handle = TimewarpClickGuiGeometry.resizeHandle(layout);
        int color = handle.contains(mouseX, mouseY) || resizingWindow ? theme.accent() : theme.muted();
        RenderServices.shapes().line(handle.right() - 4.0f, handle.bottom() - 11.0f,
                handle.right() - 11.0f, handle.bottom() - 4.0f, 1.0f, color);
        RenderServices.shapes().line(handle.right() - 4.0f, handle.bottom() - 7.0f,
                handle.right() - 7.0f, handle.bottom() - 4.0f, 1.0f, color);
    }

    private void drawButton(TimewarpClickGuiGeometry.Rect bounds, String label,
                            int mouseX, int mouseY, TimewarpClickGuiTheme theme, boolean danger) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int base = danger ? TimewarpClickGuiTheme.alpha(theme.danger(), 90) : theme.control();
        int color = hovered ? TimewarpClickGuiTheme.blend(base,
                danger ? theme.danger() : theme.accentSoft(), 0.56f) : base;
        RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                5.0f, color);
        EpsilonPanelFonts.drawCenteredText(label, bounds.x() + bounds.width() * 0.5f,
                bounds.y(), bounds.height(), 0.50f, hovered ? theme.text() : theme.secondary());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (closing || layout == null) {
            return;
        }
        if (listeningKeybind && pages.detailModule() != null) {
            TimewarpClickGuiGeometry.Rect keybind = TimewarpClickGuiGeometry.detailKeybindButton(layout);
            if (keybind.contains(mouseX, mouseY) && mouseButton == 0) {
                listeningKeybind = false;
                return;
            }
            pages.detailModule().setKey(PanelModuleKeybind.encodeMouseButton(mouseButton));
            listeningKeybind = false;
            return;
        }
        if (handleOpenPaletteColorPickerClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (valueRenderer.clickOpenDropdown(mouseX, mouseY, mouseButton, layout.content())) {
            if (ClickGUI.guiStyle.getValue() == ClickGUI.GuiStyle.PANEL) {
                mc.displayGuiScreen(new YozakuraPanelClickGui());
            }
            return;
        }
        if (mouseButton != 0 || !layout.window().contains(mouseX, mouseY)) {
            return;
        }
        if (TimewarpClickGuiGeometry.resizeHandle(layout).contains(mouseX, mouseY)) {
            beginResize(mouseX, mouseY);
            return;
        }
        for (int index = 0; index < NAVIGATION.length; index++) {
            if (TimewarpClickGuiGeometry.navigationItem(layout, index).contains(mouseX, mouseY)) {
                navigateFromSidebar(NAVIGATION[index]);
                return;
            }
        }
        if (pages.current() == TimewarpClickGuiPageTransition.Page.DETAIL) {
            if (TimewarpClickGuiGeometry.closeButton(layout).contains(mouseX, mouseY)) {
                pages.navigate(TimewarpClickGuiPageTransition.Page.MODULES, null);
                detailScroll = 0.0f;
                listeningKeybind = false;
                valueRenderer.closeDropdown();
                return;
            }
            if (TimewarpClickGuiGeometry.detailKeybindButton(layout).contains(mouseX, mouseY)) {
                listeningKeybind = true;
                valueRenderer.closeDropdown();
                return;
            }
            handleValueClick(pages.detailModule(), mouseX, mouseY, mouseButton);
        } else if (pages.current() == TimewarpClickGuiPageTransition.Page.CONFIGS) {
            handleConfigClick(mouseX, mouseY);
        } else if (pages.current() == TimewarpClickGuiPageTransition.Page.SETTINGS) {
            handleSettingsClick(mouseX, mouseY, mouseButton);
        } else {
            handleModuleClick(mouseX, mouseY);
        }
        if (TimewarpClickGuiGeometry.dragHandle(layout).contains(mouseX, mouseY)) {
            beginDrag(mouseX, mouseY);
        }
    }

    private void navigateFromSidebar(NavigationItem item) {
        listeningKeybind = false;
        if (item.page == TimewarpClickGuiPageTransition.Page.MODULES) {
            selectedCategory = item.category;
            moduleScroll = 0.0f;
        }
        pages.navigate(item.page, null);
        detailScroll = 0.0f;
        settingsScroll = 0.0f;
        configNameFocused = false;
        hallSearchFocused = false;
        valueRenderer.closeDropdown();
    }

    private void handleModuleClick(int mouseX, int mouseY) {
        TimewarpClickGuiGeometry.Rect viewport = TimewarpClickGuiGeometry.moduleViewport(layout);
        if (!viewport.contains(mouseX, mouseY)) {
            return;
        }
        List<Module> modules = modulesInCategory(selectedCategory);
        for (int index = 0; index < modules.size(); index++) {
            TimewarpClickGuiGeometry.Rect row = TimewarpClickGuiGeometry.moduleRow(layout, index, moduleScroll);
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            Module module = modules.get(index);
            if (moduleToggleBounds(row).contains(mouseX, mouseY) && !(module instanceof ClickGUI)) {
                module.toggle();
            } else {
                selectedCategory = module.getCategory();
                pages.navigate(TimewarpClickGuiPageTransition.Page.DETAIL, module);
                detailScroll = 0.0f;
            }
            return;
        }
    }

    private void handleValueClick(Module module, int mouseX, int mouseY, int mouseButton) {
        if (module == null) {
            return;
        }
        TimewarpClickGuiGeometry.Rect viewport = TimewarpClickGuiGeometry.detailViewport(layout);
        if (!viewport.contains(mouseX, mouseY)) {
            return;
        }
        List<Value> values = visibleValues(module);
        float y = viewport.y() - detailScroll + 23.0f;
        for (int index = 0; index < values.size(); index++) {
            Value<?> value = values.get(index);
            if (index > 0 && valueGroup(values.get(index - 1)) != valueGroup(value)) {
                y += TimewarpClickGuiValueRenderer.GROUP_GAP + 23.0f;
            }
            TimewarpClickGuiGeometry.Rect row = new TimewarpClickGuiGeometry.Rect(
                    viewport.x(), y, viewport.width(), TimewarpClickGuiValueRenderer.ROW_HEIGHT);
            if (row.contains(mouseX, mouseY)) {
                valueRenderer.mouseClicked(value, valueKey(module, value), row,
                        mouseX, mouseY, mouseButton);
                return;
            }
            y += VALUE_STEP;
        }
    }

    private void handleSettingsClick(int mouseX, int mouseY, int mouseButton) {
        TimewarpClickGuiGeometry.Rect viewport = TimewarpClickGuiGeometry.detailViewport(layout);
        List<Value> settings = settingsValues();
        settings.remove(ClickGUI.guiStyle);
        settings.remove(ClickGUI.palette);
        settings.remove(ClickGUI.language);
        float y = viewport.y() - settingsScroll + 23.0f;
        Value<?>[] controls = new Value<?>[]{ClickGUI.guiStyle, ClickGUI.palette, ClickGUI.language};
        for (Value<?> value : controls) {
            TimewarpClickGuiGeometry.Rect row = new TimewarpClickGuiGeometry.Rect(
                    viewport.x(), y, viewport.width(), TimewarpClickGuiValueRenderer.ROW_HEIGHT);
            if (row.contains(mouseX, mouseY)) {
                valueRenderer.mouseClicked(value, "ClickGUI:" + value.getName(), row,
                        mouseX, mouseY, mouseButton);
                return;
            }
            y += VALUE_STEP;
        }
        if (!settings.isEmpty()) {
            y += TimewarpClickGuiValueRenderer.GROUP_GAP + 23.0f;
        }
        for (Value<?> value : settings) {
            TimewarpClickGuiGeometry.Rect row = new TimewarpClickGuiGeometry.Rect(
                    viewport.x(), y, viewport.width(), TimewarpClickGuiValueRenderer.ROW_HEIGHT);
            if (row.contains(mouseX, mouseY)) {
                PanelPaletteColorControl.Group group = PanelPaletteColorControl.groupFor(value);
                if (group != null) {
                    handlePaletteColorClick(group, row, mouseX, mouseY, mouseButton);
                } else {
                    valueRenderer.mouseClicked(value, "ClickGUI:" + value.getName(), row,
                            mouseX, mouseY, mouseButton);
                }
                return;
            }
            y += VALUE_STEP;
        }
    }

    private void handlePaletteColorClick(PanelPaletteColorControl.Group group,
                                         TimewarpClickGuiGeometry.Rect row,
                                         int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !paletteSwatchBounds(row).contains(mouseX, mouseY)) {
            return;
        }
        if (paletteColorPicker.isOpen() && paletteColorPicker.group() == group) {
            paletteColorPicker.close();
            paletteColorGroup = null;
            paletteColorAnchor = null;
            return;
        }
        paletteColorPicker.open(group);
        paletteColorGroup = group;
        paletteColorAnchor = paletteSwatchBounds(row);
        valueRenderer.closeDropdown();
    }

    private boolean handleOpenPaletteColorPickerClick(int mouseX, int mouseY, int mouseButton) {
        if (!paletteColorPicker.isOpen() || paletteColorAnchor == null || layout == null) {
            return false;
        }
        PanelClickGuiLayout.Rect popup = paletteColorPicker.bounds(
                panelRect(paletteColorAnchor), panelRect(layout.content()));
        boolean consumed = paletteColorPicker.mouseClicked(popup, mouseX, mouseY, mouseButton);
        if (!paletteColorPicker.isOpen()) {
            paletteColorGroup = null;
            paletteColorAnchor = null;
        }
        return consumed;
    }

    private void handleConfigClick(int mouseX, int mouseY) {
        if (localConfigTabBounds().contains(mouseX, mouseY)) {
            parameterHallMode = false;
            hallSearchFocused = false;
            return;
        }
        if (parameterHallTabBounds().contains(mouseX, mouseY)) {
            parameterHallMode = true;
            configNameFocused = false;
            hallScroll = 0.0f;
            clubService.refreshHallConfigs();
            return;
        }
        if (parameterHallMode) {
            handleParameterHallClick(mouseX, mouseY);
            return;
        }
        configNameFocused = configNameBounds().contains(mouseX, mouseY);
        if (configSaveBounds().contains(mouseX, mouseY)) {
            saveConfig();
            return;
        }
        if (configLoadBounds().contains(mouseX, mouseY)) {
            loadSelectedConfig();
            return;
        }
        if (configRefreshBounds().contains(mouseX, mouseY)) {
            refreshConfigProfiles(selectedConfigName());
            return;
        }
        if (configFolderBounds().contains(mouseX, mouseY)) {
            try {
                ConfigBridge.openProfileDirectory();
                setConfigStatus(language("Opened config folder", "已打开配置目录"), false);
            } catch (IOException exception) {
                setConfigStatus(exception.getMessage(), true);
            }
            return;
        }
        TimewarpClickGuiGeometry.Rect viewport = configViewport();
        if (!viewport.contains(mouseX, mouseY)) {
            return;
        }
        for (int index = 0; index < configProfiles.size(); index++) {
            if (configRow(index).contains(mouseX, mouseY)) {
                selectedConfigProfile = index;
                configName = configProfiles.get(index);
                return;
            }
        }
    }

    private void handleParameterHallClick(int mouseX, int mouseY) {
        hallSearchFocused = hallSearchBounds().contains(mouseX, mouseY);
        ClubService.ClubViewState state = clubService.getState();
        List<ClubConfigSummary> configs = visibleHallConfigs(state);
        if (hallViewport().contains(mouseX, mouseY)) {
            for (int index = 0; index < configs.size(); index++) {
                if (hallRow(index).contains(mouseX, mouseY)) {
                    selectedHallConfigId = configs.get(index).getId();
                    return;
                }
            }
        }
        if (state.isBusy()) {
            return;
        }
        if (hallUploadBounds().contains(mouseX, mouseY)) {
            uploadSelectedLocalConfig();
            return;
        }
        ClubConfigSummary selected = selectedHallConfig();
        if (hallDownloadBounds().contains(mouseX, mouseY) && selected != null) {
            clubService.downloadHallConfig(selected.getId());
        } else if (hallUseBounds().contains(mouseX, mouseY) && selected != null) {
            clubService.useHallConfig(selected.getId());
        } else if (hallDeleteBounds().contains(mouseX, mouseY) && selected != null
                && state.ownsConfig(selected.getId())) {
            clubService.deleteHallConfig(selected);
        } else if (hallRefreshBounds().contains(mouseX, mouseY)) {
            clubService.refreshHallConfigs();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        valueRenderer.mouseReleased();
        paletteColorPicker.mouseReleased();
        if (draggingWindow || resizingWindow) {
            persistWindowGeometry();
        }
        draggingWindow = false;
        resizingWindow = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (paletteColorPicker.isDragging() && paletteColorAnchor != null && layout != null) {
            PanelClickGuiLayout.Rect popup = paletteColorPicker.bounds(
                    panelRect(paletteColorAnchor), panelRect(layout.content()));
            paletteColorPicker.mouseDragged(popup, mouseX, mouseY);
        } else if (draggingWindow) {
            updateWindowPosition(mouseX, mouseY);
        } else if (resizingWindow) {
            updateWindowSize(mouseX, mouseY);
        } else if (valueRenderer.isDraggingSlider()) {
            valueRenderer.updateDrag(mouseX);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (layout == null) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || valueRenderer.isDropdownOpen() || paletteColorPicker.isOpen()) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        float amount = wheel > 0 ? -34.0f : 34.0f;
        if (pages.current() == TimewarpClickGuiPageTransition.Page.DETAIL
                && TimewarpClickGuiGeometry.detailViewport(layout).contains(mx, my)) {
            detailScroll = clamp(detailScroll + amount, 0.0f, detailMaxScroll);
        } else if (pages.current() == TimewarpClickGuiPageTransition.Page.CONFIGS
                && parameterHallMode && hallViewport().contains(mx, my)) {
            hallScroll = clamp(hallScroll + amount, 0.0f, hallMaxScroll);
        } else if (pages.current() == TimewarpClickGuiPageTransition.Page.CONFIGS
                && !parameterHallMode && configViewport().contains(mx, my)) {
            configScroll = clamp(configScroll + amount, 0.0f, configMaxScroll);
        } else if (pages.current() == TimewarpClickGuiPageTransition.Page.SETTINGS
                && TimewarpClickGuiGeometry.detailViewport(layout).contains(mx, my)) {
            settingsScroll = clamp(settingsScroll + amount, 0.0f, settingsMaxScroll);
        } else if (pages.current() == TimewarpClickGuiPageTransition.Page.MODULES
                && TimewarpClickGuiGeometry.moduleViewport(layout).contains(mx, my)) {
            moduleScroll = clamp(moduleScroll + amount, 0.0f, moduleMaxScroll);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (hallSearchFocused && parameterHallMode
                && pages.current() == TimewarpClickGuiPageTransition.Page.CONFIGS) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                hallSearchFocused = false;
            } else if (keyCode == Keyboard.KEY_BACK && !hallSearchText.isEmpty()) {
                hallSearchText = hallSearchText.substring(0, hallSearchText.length() - 1);
                hallScroll = 0.0f;
            } else if (typedChar >= 32 && typedChar != 127 && hallSearchText.length() < 64) {
                hallSearchText += typedChar;
                hallScroll = 0.0f;
            }
            return;
        }
        if (configNameFocused && pages.current() == TimewarpClickGuiPageTransition.Page.CONFIGS) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                configNameFocused = false;
            } else if (keyCode == Keyboard.KEY_BACK && !configName.isEmpty()) {
                configName = configName.substring(0, configName.length() - 1);
            } else if (isConfigCharacter(typedChar) && configName.length() < 48) {
                configName += typedChar;
            }
            return;
        }
        if (listeningKeybind && pages.detailModule() != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                listeningKeybind = false;
            } else if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                pages.detailModule().setKey(Keyboard.KEY_NONE);
                listeningKeybind = false;
            } else {
                pages.detailModule().setKey(keyCode);
                listeningKeybind = false;
            }
            return;
        }
        if (valueRenderer.isDropdownOpen()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                valueRenderer.closeDropdown();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (pages.current() != TimewarpClickGuiPageTransition.Page.MODULES) {
                pages.navigate(TimewarpClickGuiPageTransition.Page.MODULES, null);
                detailScroll = 0.0f;
            } else {
                closing = true;
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void beginDrag(int mouseX, int mouseY) {
        draggingWindow = true;
        dragOffsetX = mouseX - layout.window().x();
        dragOffsetY = mouseY - layout.window().y();
    }

    private void beginResize(int mouseX, int mouseY) {
        resizingWindow = true;
        resizeStartMouseX = mouseX;
        resizeStartMouseY = mouseY;
        resizeStartWidth = layout.window().width();
        resizeStartHeight = layout.window().height();
    }

    private void updateWindowPosition(float mouseX, float mouseY) {
        windowX = mouseX - dragOffsetX;
        windowY = mouseY - dragOffsetY;
        rebuildLayout();
        windowX = layout.window().x();
        windowY = layout.window().y();
    }

    private void updateWindowSize(float mouseX, float mouseY) {
        windowWidth = resizeStartWidth + mouseX - resizeStartMouseX;
        windowHeight = resizeStartHeight + mouseY - resizeStartMouseY;
        rebuildLayout();
        windowWidth = layout.window().width();
        windowHeight = layout.window().height();
    }

    private void restoreWindowGeometry() {
        windowWidth = ClickGUI.timewarpWidth.getValue().floatValue();
        windowHeight = ClickGUI.timewarpHeight.getValue().floatValue();
        float defaultX = (width - windowWidth) * 0.5f;
        float defaultY = (height - windowHeight) * 0.5f;
        windowX = ClickGUI.timewarpX.getValue().doubleValue() < 0.0
                ? defaultX : ClickGUI.timewarpX.getValue().floatValue();
        windowY = ClickGUI.timewarpY.getValue().doubleValue() < 0.0
                ? defaultY : ClickGUI.timewarpY.getValue().floatValue();
    }

    private void persistWindowGeometry() {
        if (layout == null) {
            return;
        }
        ClickGUI.timewarpX.setNumberValue(layout.window().x());
        ClickGUI.timewarpY.setNumberValue(layout.window().y());
        ClickGUI.timewarpWidth.setNumberValue(layout.window().width());
        ClickGUI.timewarpHeight.setNumberValue(layout.window().height());
    }

    private void rebuildLayout() {
        layout = TimewarpClickGuiGeometry.compute(width, height, windowWidth, windowHeight,
                windowX, windowY);
        windowX = layout.window().x();
        windowY = layout.window().y();
        windowWidth = layout.window().width();
        windowHeight = layout.window().height();
    }

    private void saveConfig() {
        String name = cleanConfigName(configName);
        if (name.isEmpty()) {
            setConfigStatus(language("Enter a profile name", "请输入配置名称"), true);
            return;
        }
        try {
            ConfigBridge.saveProfile(name);
            configName = name;
            refreshConfigProfiles(name);
            setConfigStatus(language("Saved ", "已保存 ") + name, false);
        } catch (IOException exception) {
            setConfigStatus(exception.getMessage(), true);
        }
    }

    private void loadSelectedConfig() {
        String selected = selectedConfigName();
        if (selected == null) {
            setConfigStatus(language("Select a profile first", "请先选择配置"), true);
            return;
        }
        try {
            ConfigBridge.loadProfile(selected);
            setConfigStatus(language("Loaded ", "已加载 ") + selected, false);
        } catch (IOException exception) {
            setConfigStatus(exception.getMessage(), true);
        }
    }

    private void refreshConfigProfiles(String preferred) {
        try {
            configProfiles.clear();
            configProfiles.addAll(ConfigBridge.listProfiles());
            Collections.sort(configProfiles, String.CASE_INSENSITIVE_ORDER);
            selectedConfigProfile = -1;
            if (preferred != null) {
                for (int index = 0; index < configProfiles.size(); index++) {
                    if (configProfiles.get(index).equalsIgnoreCase(preferred)) {
                        selectedConfigProfile = index;
                        break;
                    }
                }
            }
            if (selectedConfigProfile < 0 && !configProfiles.isEmpty()) {
                selectedConfigProfile = 0;
            }
            if (selectedConfigProfile >= 0) {
                configName = configProfiles.get(selectedConfigProfile);
            }
            configScroll = 0.0f;
        } catch (IOException exception) {
            setConfigStatus(exception.getMessage(), true);
        }
    }

    private void setConfigStatus(String status, boolean error) {
        configStatus = status == null ? language("Config action failed", "配置操作失败") : status;
        configStatusError = error;
    }

    private String selectedConfigName() {
        return selectedConfigProfile >= 0 && selectedConfigProfile < configProfiles.size()
                ? configProfiles.get(selectedConfigProfile) : null;
    }

    private void uploadSelectedLocalConfig() {
        String selected = selectedConfigName();
        if (selected == null) {
            clubService.reportResult(language("Select a Local profile first",
                    "请先在本地配置中选择配置"), true);
            return;
        }
        if (!clubService.isAuthenticated()) {
            clubService.ensureVerifiedSession();
            return;
        }
        try {
            clubService.uploadConfigToHall(selected, ConfigBridge.readProfileSnapshot(selected));
        } catch (IOException exception) {
            clubService.reportResult(language("Unable to read Local profile", "读取本地配置失败"), true);
        }
    }

    private List<ClubConfigSummary> visibleHallConfigs(ClubService.ClubViewState state) {
        return PanelCloudConfigSearchModel.filter(state.getConfigs(), hallSearchText);
    }

    private ClubConfigSummary selectedHallConfig() {
        return PanelCloudConfigSearchModel.findById(
                clubService.getState().getConfigs(), selectedHallConfigId);
    }

    private boolean ownsSelectedHallConfig(ClubService.ClubViewState state) {
        ClubConfigSummary selected = selectedHallConfig();
        return selected != null && state.ownsConfig(selected.getId());
    }

    private void ensureHallSelection(List<ClubConfigSummary> configs) {
        if (PanelCloudConfigSearchModel.findById(configs, selectedHallConfigId) == null) {
            selectedHallConfigId = configs.isEmpty() ? null : configs.get(0).getId();
        }
    }

    private TimewarpClickGuiGeometry.Rect localConfigTabBounds() {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        return new TimewarpClickGuiGeometry.Rect(content.x() + 16.0f,
                content.y() + 38.0f, 60.0f, 24.0f);
    }

    private TimewarpClickGuiGeometry.Rect parameterHallTabBounds() {
        TimewarpClickGuiGeometry.Rect local = localConfigTabBounds();
        return new TimewarpClickGuiGeometry.Rect(local.right() + 5.0f,
                local.y(), 94.0f, local.height());
    }

    private TimewarpClickGuiGeometry.Rect configNameBounds() {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        float innerWidth = content.width() - 32.0f;
        float actionsWidth = 110.0f;
        return new TimewarpClickGuiGeometry.Rect(content.x() + 16.0f, content.y() + 68.0f,
                Math.max(96.0f, innerWidth - actionsWidth - 6.0f), 26.0f);
    }

    private TimewarpClickGuiGeometry.Rect configSaveBounds() {
        TimewarpClickGuiGeometry.Rect name = configNameBounds();
        return new TimewarpClickGuiGeometry.Rect(name.right() + 6.0f, name.y(), 52.0f, name.height());
    }

    private TimewarpClickGuiGeometry.Rect configLoadBounds() {
        TimewarpClickGuiGeometry.Rect save = configSaveBounds();
        return new TimewarpClickGuiGeometry.Rect(save.right() + 6.0f, save.y(), 52.0f, save.height());
    }

    private TimewarpClickGuiGeometry.Rect configRefreshBounds() {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        return new TimewarpClickGuiGeometry.Rect(content.x() + 16.0f, content.y() + 100.0f,
                64.0f, 24.0f);
    }

    private TimewarpClickGuiGeometry.Rect configFolderBounds() {
        TimewarpClickGuiGeometry.Rect refresh = configRefreshBounds();
        return new TimewarpClickGuiGeometry.Rect(refresh.right() + 6.0f, refresh.y(),
                58.0f, refresh.height());
    }

    private TimewarpClickGuiGeometry.Rect configViewport() {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        return new TimewarpClickGuiGeometry.Rect(content.x() + 12.0f, content.y() + 132.0f,
                content.width() - 24.0f, Math.max(1.0f, content.height() - 156.0f));
    }

    private TimewarpClickGuiGeometry.Rect configRow(int index) {
        TimewarpClickGuiGeometry.Rect viewport = configViewport();
        return new TimewarpClickGuiGeometry.Rect(viewport.x(),
                viewport.y() + index * (CONFIG_ROW_HEIGHT + CONFIG_ROW_GAP) - configScroll,
                viewport.width(), CONFIG_ROW_HEIGHT);
    }

    private TimewarpClickGuiGeometry.Rect hallSearchBounds() {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        return new TimewarpClickGuiGeometry.Rect(content.x() + 16.0f,
                content.y() + 82.0f, content.width() - 32.0f, 25.0f);
    }

    private TimewarpClickGuiGeometry.Rect hallViewport() {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        return new TimewarpClickGuiGeometry.Rect(content.x() + 12.0f,
                content.y() + 113.0f, content.width() - 24.0f,
                Math.max(1.0f, content.height() - 166.0f));
    }

    private TimewarpClickGuiGeometry.Rect hallRow(int index) {
        TimewarpClickGuiGeometry.Rect viewport = hallViewport();
        return new TimewarpClickGuiGeometry.Rect(viewport.x(),
                viewport.y() + index * (HALL_ROW_HEIGHT + HALL_ROW_GAP) - hallScroll,
                viewport.width(), HALL_ROW_HEIGHT);
    }

    private TimewarpClickGuiGeometry.Rect hallUploadBounds() {
        return hallActionBounds(0);
    }

    private TimewarpClickGuiGeometry.Rect hallDownloadBounds() {
        return hallActionBounds(1);
    }

    private TimewarpClickGuiGeometry.Rect hallUseBounds() {
        return hallActionBounds(2);
    }

    private TimewarpClickGuiGeometry.Rect hallDeleteBounds() {
        return hallActionBounds(3);
    }

    private TimewarpClickGuiGeometry.Rect hallRefreshBounds() {
        return hallActionBounds(4);
    }

    private TimewarpClickGuiGeometry.Rect hallActionBounds(int index) {
        TimewarpClickGuiGeometry.Rect content = layout.content();
        float gap = 4.0f;
        float width = (content.width() - 32.0f - gap * 4.0f) / 5.0f;
        return new TimewarpClickGuiGeometry.Rect(content.x() + 16.0f + index * (width + gap),
                content.bottom() - 45.0f, width, 22.0f);
    }

    private List<Module> modulesInCategory(ModuleType category) {
        List<Module> result = new ArrayList<Module>();
        for (Module module : ModuleManager.getModules()) {
            if (module != null && module.getCategory() == category
                    && !"cfgmanager".equalsIgnoreCase(module.getName())) {
                result.add(module);
            }
        }
        Collections.sort(result, new Comparator<Module>() {
            @Override
            public int compare(Module left, Module right) {
                return moduleName(left).compareToIgnoreCase(moduleName(right));
            }
        });
        return result;
    }

    private static List<Value> visibleValues(Module module) {
        List<Value> result = new ArrayList<Value>();
        if (module == null) {
            return result;
        }
        for (Value value : module.getValues()) {
            if (value != null && value.isVisible()) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<Value> settingsValues() {
        List<Value> values = visibleValues(clickGuiModule());
        List<Value> collapsed = new ArrayList<Value>();
        for (Value value : values) {
            PanelPaletteColorControl.Group group = PanelPaletteColorControl.groupFor(value);
            if (group == null || PanelPaletteColorControl.isLeader(value)) {
                collapsed.add(value);
            }
        }
        return collapsed;
    }

    private static TimewarpClickGuiGeometry.Rect paletteSwatchBounds(
            TimewarpClickGuiGeometry.Rect row) {
        return new TimewarpClickGuiGeometry.Rect(
                row.right() - PanelPaletteColorControl.SWATCH_TRAILING_INSET
                        - PanelPaletteColorControl.SWATCH_WIDTH,
                row.y() + (row.height() - PanelPaletteColorControl.SWATCH_HEIGHT) * 0.5f,
                PanelPaletteColorControl.SWATCH_WIDTH,
                PanelPaletteColorControl.SWATCH_HEIGHT);
    }

    private static PanelClickGuiLayout.Rect panelRect(TimewarpClickGuiGeometry.Rect rect) {
        return new PanelClickGuiLayout.Rect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static float settingsContentHeight(int controlCount, int advancedCount) {
        float height = 23.0f + controlCount * VALUE_STEP;
        if (advancedCount > 0) {
            height += TimewarpClickGuiValueRenderer.GROUP_GAP + 23.0f;
            height += advancedCount * VALUE_STEP;
        }
        return height;
    }

    private static Module clickGuiModule() {
        for (Module module : ModuleManager.getModules()) {
            if (module instanceof ClickGUI) {
                return module;
            }
        }
        return null;
    }

    private int selectedNavigationIndex() {
        TimewarpClickGuiPageTransition.Page page = pages.current();
        for (int index = 0; index < NAVIGATION.length; index++) {
            NavigationItem item = NAVIGATION[index];
            if (page == TimewarpClickGuiPageTransition.Page.MODULES
                    || page == TimewarpClickGuiPageTransition.Page.DETAIL) {
                if (item.page == TimewarpClickGuiPageTransition.Page.MODULES
                        && item.category == selectedCategory) {
                    return index;
                }
            } else if (item.page == page) {
                return index;
            }
        }
        return 0;
    }

    private static String moduleName(Module module) {
        String name = ClickGUI.getLanguage() == ClientLanguage.CHINESE
                ? module.getChinese() : module.getName();
        return name == null || name.trim().isEmpty() ? "?" : name;
    }

    private static String valueKey(Module module, Value<?> value) {
        return module.getName() + ":" + value.getName();
    }

    private static String label(NavigationItem item) {
        return language(item.english, item.chinese);
    }

    private static String categoryTitle(ModuleType category) {
        for (NavigationItem item : NAVIGATION) {
            if (item.page == TimewarpClickGuiPageTransition.Page.MODULES
                    && item.category == category) {
                return label(item);
            }
        }
        return "?";
    }

    private static String moduleSubtitle() {
        return language("Select a module to configure it", "选择模块以查看详细设置");
    }

    private static float valueContentHeight(List<Value> values) {
        float height = 23.0f;
        for (int index = 0; index < values.size(); index++) {
            if (index > 0 && valueGroup(values.get(index - 1)) != valueGroup(values.get(index))) {
                height += TimewarpClickGuiValueRenderer.GROUP_GAP + 23.0f;
            }
            height += VALUE_STEP;
        }
        return height;
    }

    private static int valueGroup(Value<?> value) {
        if (value instanceof gq.yozakura.value.Mode
                || value instanceof gq.yozakura.value.properties.ModeProperty) {
            return 1;
        }
        if (value instanceof gq.yozakura.value.Numbers) {
            return 2;
        }
        return 0;
    }

    private static String valueGroupLabel(Value<?> value) {
        int group = valueGroup(value);
        if (group == 1) {
            return language("MODES", "模式");
        }
        if (group == 2) {
            return language("VALUES", "数值");
        }
        return language("GENERAL", "常规");
    }

    private static TimewarpClickGuiGeometry.Rect moduleSettingsBounds(TimewarpClickGuiGeometry.Rect row) {
        return new TimewarpClickGuiGeometry.Rect(row.right() - 69.0f, row.y() + 10.0f, 20.0f, 20.0f);
    }

    private static TimewarpClickGuiGeometry.Rect moduleToggleBounds(TimewarpClickGuiGeometry.Rect row) {
        return new TimewarpClickGuiGeometry.Rect(row.right() - 39.0f, row.y() + 13.0f, 29.0f, 16.0f);
    }

    private static void drawGear(float centerX, float centerY, int color) {
        RenderServices.shapes().circleOutline(centerX, centerY, 4.0f, 1.0f, color);
        RenderServices.shapes().circleOutline(centerX, centerY, 1.2f, 0.9f, color);
        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.5 * index;
            float dx = (float) Math.cos(angle) * 7.0f;
            float dy = (float) Math.sin(angle) * 7.0f;
            RenderServices.shapes().line(centerX + dx * 0.70f, centerY + dy * 0.70f,
                    centerX + dx, centerY + dy, 1.2f, color);
        }
    }

    private static void drawClose(TimewarpClickGuiGeometry.Rect bounds, int color) {
        float centerX = bounds.x() + bounds.width() * 0.5f;
        float centerY = bounds.y() + bounds.height() * 0.5f;
        RenderServices.shapes().line(centerX - 4.0f, centerY - 4.0f,
                centerX + 4.0f, centerY + 4.0f, 1.3f, color);
        RenderServices.shapes().line(centerX + 4.0f, centerY - 4.0f,
                centerX - 4.0f, centerY + 4.0f, 1.3f, color);
    }

    private static String language(String english, String chinese) {
        return ClickGUI.getLanguage() == ClientLanguage.CHINESE ? chinese : english;
    }

    private static String compactKeyName(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return "NONE";
        }
        if (PanelModuleKeybind.isMouseButton(keyCode)) {
            return PanelModuleKeybind.compactName(keyCode);
        }
        String name = Keyboard.getKeyName(keyCode);
        return name == null || name.isEmpty() ? "NONE" : name;
    }

    private static String cleanConfigName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.toLowerCase(java.util.Locale.ROOT).endsWith(".yzk")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        return cleaned;
    }

    private static boolean isConfigCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-'
                || character == '.';
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class NavigationItem {
        private final ModuleType category;
        private final TimewarpClickGuiPageTransition.Page page;
        private final String english;
        private final String chinese;
        private final String icon;

        private NavigationItem(ModuleType category, TimewarpClickGuiPageTransition.Page page,
                               String english, String chinese, String icon) {
            this.category = category;
            this.page = page;
            this.english = english;
            this.chinese = chinese;
            this.icon = icon;
        }
    }
}
