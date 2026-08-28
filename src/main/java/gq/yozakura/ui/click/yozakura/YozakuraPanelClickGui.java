package gq.yozakura.ui.click.yozakura;

import gq.yozakura.club.ClubConfig;
import gq.yozakura.club.ClubConfigSummary;
import gq.yozakura.club.ClubService;
import gq.yozakura.core.ClientLanguage;
import gq.yozakura.core.ConfigBridge;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.engine.font.CFontRenderer;
import gq.yozakura.engine.render.GLStateManager;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.manager.FileManager;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.util.animation.AnimationState;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct LWJGL implementation of Epsilon's Panel mode.
 *
 * <p>The screen deliberately has no DOM, parser or retained HTML command list.
 * It renders the three panel columns directly with the client's rounded/shadow
 * shader path and keeps input in the same scaled coordinate space.</p>
 */
public final class YozakuraPanelClickGui extends GuiScreen {
    private static final ModuleType[] CATEGORIES = EpsilonPanelCategories.visibleCategories();
    private static final float CATEGORY_ITEM_H = EpsilonPanelMetrics.CATEGORY_ITEM_HEIGHT;
    private static final float CATEGORY_ITEM_STEP = EpsilonPanelMetrics.CATEGORY_ITEM_STEP;
    private static final float CATEGORY_START = EpsilonPanelMetrics.CATEGORY_START_Y;
    private static final float MODULE_ROW_H = PanelClickGuiModuleRow.HEIGHT;
    private static final float ROW_GAP = 3.0f;
    private static final float DETAIL_HEADER_H = 76.0f;
    private static final float VALUE_ROW_H = 28.0f;
    private static final float SCROLLBAR_W = PanelClickGuiScroll.TOTAL_WIDTH;
    private static final float SCROLLBAR_GAP = 0.0f;
    private static final ResourceLocation PANEL_SETTINGS_ICON = new ResourceLocation("minecraft",
            "yozakura/panel/settings-gear.png");
    private static final float MODULE_SETTINGS_ICON_Y_OFFSET = -1.0f;
    private static final float RAIL_CATEGORY_ICON_Y_OFFSET = -1.0f;
    private static final float RAIL_CONFIG_ICON_Y_OFFSET = -3.0f;
    private static final float RAIL_SETTINGS_ICON_Y_OFFSET = -2.0f;
    private static final float CONFIG_ROW_H = 30.0f;
    private static final float CONFIG_ROW_GAP = 4.0f;
    private static final PanelClickGuiSessionState panelSessionState =
            new PanelClickGuiSessionState();

    private final AnimationState animations = new AnimationState();
    private final EpsilonPanelAnimation.State epsilonAnimations = new EpsilonPanelAnimation.State();
    private final ClickGuiValueRenderer.InteractionState valueInteraction =
            new ClickGuiValueRenderer.InteractionState();
    private final ClickGuiValueRenderer values =
            new ClickGuiValueRenderer(valueInteraction, animations);
    private final PanelPaletteColorPicker paletteColorPicker =
            new PanelPaletteColorPicker();
    private final PanelClickGuiCursor cursor = new PanelClickGuiCursor();
    private final GuiScreen backgroundScreen;
    private final MainMenuBackdropSnapshot mainMenuBackdrop;

    private PanelClickGuiLayout.Layout layout;
    private final PanelClickGuiRailAnimation railAnimation = new PanelClickGuiRailAnimation();
    private ModuleType selectedCategory = ModuleType.Combat;
    private Module selectedModule;
    private boolean moduleDetailOpen;
    private boolean clientSettingsMode;
    private boolean configManagerMode;
    private final List<String> configProfiles = new ArrayList<String>();
    private int selectedConfigProfile = -1;
    private int configProfileScroll;
    private String configName = "";
    private int configNameCursor;
    private boolean configNameFocused;
    private String configStatus = "";
    private int configStatusColor = PanelClickGuiPalette.textMuted();
    private final ClubService clubService = ClubService.getInstance();
    private boolean cloudConfigMode;
    private String selectedCloudConfigId;
    private int cloudConfigScroll;
    private String cloudConfigSearchText = "";
    private int cloudConfigSearchCursor;
    private boolean cloudConfigSearchFocused;
    private float moduleScroll;
    private float moduleScrollVelocity;
    private float detailScroll;
    private float detailScrollVelocity;
    private float moduleMaxScroll;
    private float detailMaxScroll;
    private boolean sidebarExpanded;
    private boolean draggingScrollbar;
    private boolean draggingDetailScrollbar;
    private float scrollbarGrabOffset;
    private boolean listeningKeybind;
    private boolean searchFocused;
    private String searchText = "";
    private int searchCursorIndex;
    private float railHoverY;
    private long lastFrameNanos;
    private long frameTimeMillis;
    private boolean stateInitialized;
    private boolean resizingPanel;
    private boolean draggingPanel;
    private boolean panelPositionInitialized;
    private float panelPositionX;
    private float panelPositionY;
    private float dragOffsetX;
    private float dragOffsetY;
    private float resizeStartMouseX;
    private float resizeStartMouseY;
    private float resizeStartWidth;
    private float resizeStartHeight;
    private boolean closingGui;
    private final PanelClickGuiOpenCloseAnimation openCloseAnimation =
            new PanelClickGuiOpenCloseAnimation();

    public YozakuraPanelClickGui() {
        GuiScreen previousScreen = Minecraft.getMinecraft().currentScreen;
        backgroundScreen = previousScreen instanceof GuiMainMenu ? previousScreen : null;
        mainMenuBackdrop = backgroundScreen == null ? null : new MainMenuBackdropSnapshot();
        resetFrameClock();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        cursor.install();
        resetFrameClock();
        animations.clear();
        epsilonAnimations.clear();
        closingGui = false;
        openCloseAnimation.reset(false, lastFrameNanos);
        openCloseAnimation.progressAt(true, lastFrameNanos);
        if (!stateInitialized) {
            PanelClickGuiSessionState.Snapshot session = panelSessionState.restore();
            selectedCategory = session.selectedCategory;
            selectedModule = session.selectedModule;
            moduleDetailOpen = session.moduleDetailOpen;
            clientSettingsMode = session.clientSettingsMode;
            configManagerMode = session.configManagerMode;
            cloudConfigMode = session.cloudConfigMode;
            sidebarExpanded = session.sidebarExpanded;
            moduleScroll = session.moduleScroll;
            detailScroll = session.detailScroll;
            configProfileScroll = session.configProfileScroll;
            cloudConfigScroll = session.cloudConfigScroll;
            searchText = session.searchText;
            searchCursorIndex = searchText.length();
            moduleScrollVelocity = 0.0f;
            detailScrollVelocity = 0.0f;
            railHoverY = Float.NaN;
            if (configManagerMode) {
                refreshConfigProfiles(null);
                if (cloudConfigMode) {
                    clubService.refreshHallConfigs();
                }
            }
            stateInitialized = true;
        }
        rebuildLayout(frameTimeMillis);
    }

    private void resetFrameClock() {
        lastFrameNanos = System.nanoTime();
        frameTimeMillis = lastFrameNanos / 1_000_000L;
        values.beginFrame(frameTimeMillis);
    }

    @Override
    public void onGuiClosed() {
        panelSessionState.capture(selectedCategory, selectedModule, moduleDetailOpen,
                clientSettingsMode, configManagerMode, cloudConfigMode, sidebarExpanded,
                moduleScroll, detailScroll, configProfileScroll, cloudConfigScroll, searchText);
        ConfigBridge.saveModulesQuietly();
        Keyboard.enableRepeatEvents(false);
        draggingScrollbar = false;
        draggingDetailScrollbar = false;
        moduleScrollVelocity = 0.0f;
        detailScrollVelocity = 0.0f;
        values.clearDropdownImmediately();
        values.clearNumberEditing();
        paletteColorPicker.close();
        values.mouseReleased();
        listeningKeybind = false;
        if (resizingPanel) {
            persistPanelSize();
        }
        resizingPanel = false;
        if (draggingPanel) {
            persistPanelPosition();
        }
        draggingPanel = false;
        panelPositionInitialized = false;
        if (mainMenuBackdrop != null) {
            mainMenuBackdrop.dispose();
        }
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
                ConfigBridge.saveProfileSnapshot(pendingDownload.getSummary().getName(),
                        pendingDownload.getPayload().toString());
                refreshConfigProfiles(pendingDownload.getSummary().getName());
                clubService.reportResult(languageText("Downloaded to Local: ", "已下载到本地：")
                        + pendingDownload.getSummary().getName() + ".yzk", false);
            } catch (IOException exception) {
                clubService.reportResult(languageText("Cloud config download failed", "大厅配置下载失败"), true);
                FileManager.logConfigFailure("Panel hall config download failed", exception);
            }
        }
        ClubConfig pendingUse = clubService.consumePendingUse();
        if (pendingUse != null) {
            try {
                ConfigBridge.importSnapshot(pendingUse.getPayload().toString());
                panelPositionInitialized = false;
                rebuildLayout(frameTimeMillis);
                clubService.reportResult(languageText("Applied hall config ", "已使用大厅配置 ")
                        + pendingUse.getSummary().getName(), false);
            } catch (IOException exception) {
                clubService.reportResult(languageText("Hall config apply failed", "大厅配置使用失败"), true);
                FileManager.logConfigFailure("Panel hall config import failed", exception);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mainMenuBackdrop != null) {
            if (!mainMenuBackdrop.isCaptured()) {
                mainMenuBackdrop.capture();
            }
            mainMenuBackdrop.draw(width, height);
        }
        long frameNowNanos = System.nanoTime();
        long elapsedNanos = Math.max(1L, Math.min(64_000_000L,
                frameNowNanos - lastFrameNanos));
        lastFrameNanos = frameNowNanos;
        frameTimeMillis = frameNowNanos / 1_000_000L;
        values.beginFrame(frameTimeMillis);
        float frameScale = (float) (elapsedNanos / 16_666_667.0);
        if (draggingPanel) {
            updatePanelPosition(mouseX, mouseY);
        } else if (resizingPanel) {
            updatePanelSize(mouseX, mouseY);
        } else {
            rebuildLayout(frameTimeMillis);
        }
        if (paletteColorPicker.isDragging()) {
            updatePaletteColorPickerDrag(mouseX, mouseY);
        }
        advanceScroll(frameScale);
        float progress = openCloseAnimation.progressAt(!closingGui, frameNowNanos);
        float open = openCloseAnimation.visualProgress(progress);

        GLStateManager.begin2D();
        try {
            ClickGuiRenderContext.activate(0.0f, 0.0f, 1.0f);
            if (useRealtimeBackdrop()) {
                int backdropAlpha = Math.round(82.0f * open);
                RenderServices.blur().glass(0.0f, 0.0f, width, height, 0.0f, 0.0f,
                        PanelClickGuiPalette.shadow(backdropAlpha), 0x00000000);
            } else {
                drawStableBackdrop(open);
            }
            PanelClickGuiLayout.Rect panel = layout.panel();
            float centerX = panel.x() + panel.width() * 0.5f;
            float centerY = panel.y() + panel.height() * 0.5f;
            float scale = 0.94f + 0.06f * open;
            GL11.glPushMatrix();
            GL11.glTranslatef(centerX, centerY + (1.0f - open) * 12.0f, 0.0f);
            GL11.glScalef(scale, scale, 1.0f);
            GL11.glTranslatef(-centerX, -centerY, 0.0f);
            CFontRenderer.pushScaleCompensation();
            try {
                drawPanel(mouseX, mouseY, frameScale);
            } finally {
                CFontRenderer.popScaleCompensation();
                GL11.glPopMatrix();
            }
            completeCloseIfReady();
        } finally {
            GLStateManager.end2D();
        }
    }

    private boolean useRealtimeBackdrop() {
        return mc.theWorld != null && !draggingPanel && !resizingPanel;
    }

    private void drawStableBackdrop(float open) {
        int baseAlpha = mc.theWorld == null ? 72 : 58;
        RenderServices.shapes().rect(0.0f, 0.0f, width, height,
                PanelClickGuiPalette.shadow(Math.round(baseAlpha * open)));
    }

    private void rebuildLayout(long now) {
        float railWidth = railAnimation.valueAt(sidebarExpanded, now);
        float requestedWidth = ClickGUI.panelWidth.getValue().floatValue();
        float requestedHeight = ClickGUI.panelHeight.getValue().floatValue();
        if (!panelPositionInitialized) {
            float requestedX = ClickGUI.panelX.getValue().floatValue();
            float requestedY = ClickGUI.panelY.getValue().floatValue();
            if (requestedX < 0.0f || requestedY < 0.0f) {
                layout = PanelClickGuiLayout.compute(width, height, railWidth,
                        requestedWidth, requestedHeight);
            } else {
                layout = PanelClickGuiLayout.compute(width, height, railWidth,
                        requestedWidth, requestedHeight, requestedX, requestedY);
            }
            panelPositionX = layout.panel().x();
            panelPositionY = layout.panel().y();
            panelPositionInitialized = true;
        } else {
            layout = PanelClickGuiLayout.compute(width, height, railWidth,
                    requestedWidth, requestedHeight, panelPositionX, panelPositionY);
            panelPositionX = layout.panel().x();
            panelPositionY = layout.panel().y();
        }
        moduleMaxScroll = Math.max(0.0f, moduleContentHeight() - moduleViewportHeight());
        detailMaxScroll = Math.max(0.0f, detailContentHeight() - detailViewportHeight());
        valueInteraction.popupBounds = layout.detail();
        valueInteraction.popupInset = 3.0f;
        moduleScroll = clamp(moduleScroll, 0.0f, moduleMaxScroll);
        detailScroll = clamp(detailScroll, 0.0f, detailMaxScroll);
        if (moduleDetailOpen && (selectedModule == null
                || !EpsilonPanelCategories.belongsTo(selectedModule.getCategory(), selectedCategory))) {
            moduleDetailOpen = false;
            selectedModule = null;
        }
    }

    private void advanceScroll(float frameScale) {
        PanelClickGuiMotion.ScrollFrame moduleFrame = PanelClickGuiMotion.advanceScroll(
                moduleScroll, moduleScrollVelocity, frameScale, 0.0f, moduleMaxScroll);
        moduleScroll = moduleFrame.scroll();
        moduleScrollVelocity = moduleFrame.velocity();
        PanelClickGuiMotion.ScrollFrame detailFrame = PanelClickGuiMotion.advanceScroll(
                detailScroll, detailScrollVelocity, frameScale, 0.0f, detailMaxScroll);
        detailScroll = detailFrame.scroll();
        detailScrollVelocity = detailFrame.velocity();
    }

    private void drawPanel(int mouseX, int mouseY, float frameScale) {
        PanelClickGuiLayout.Rect panel = layout.panel();
        int accent = PanelClickGuiPalette.accent();
        RenderServices.shapes().shadowOffset(panel.x(), panel.y(), panel.right(), panel.bottom(),
                17.0f, 0.0f, 0.0f, PanelClickGuiPalette.shadow(96), 12, 18.0f);
        RenderServices.shapes().rounded(panel.x(), panel.y(), panel.right(), panel.bottom(),
                17.0f, PanelClickGuiPalette.canvas());

        boolean popupOpen = values.isDropdownOpen() || paletteColorPicker.isOpen();
        int contentMouseX = popupOpen ? Integer.MIN_VALUE : mouseX;
        int contentMouseY = popupOpen ? Integer.MIN_VALUE : mouseY;
        drawRail(contentMouseX, contentMouseY, frameScale, accent);
        drawAnimatedContentPage(contentMouseX, contentMouseY,
                mouseX, mouseY, frameScale, accent);
        drawResizeHandle(mouseX, mouseY);
        drawOpenPaletteColorPicker(mouseX, mouseY);
    }

    private void drawAnimatedContentPage(int contentMouseX, int contentMouseY,
                                         int popupMouseX, int popupMouseY,
                                         float frameScale, int accent) {
        boolean detailVisible = moduleDetailOpen || clientSettingsMode || configManagerMode;
        float pageProgress = epsilonAnimations.value("panel-page-transition",
                detailVisible ? 1.0f : 0.0f, frameTimeMillis, 160L,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float direction = detailVisible ? 1.0f - pageProgress : -pageProgress;
        float offsetX = direction * Math.min(24.0f, layout.detail().width() * 0.08f);
        GLStateManager.pushScissor(layout.detail().x(), layout.detail().y(),
                layout.detail().width(), layout.detail().height());
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(offsetX, 0.0f, 0.0f);
            if (configManagerMode) {
                drawConfigManagerPage(contentMouseX, contentMouseY);
            } else if (detailVisible) {
                drawDetailPanel(contentMouseX, contentMouseY,
                        popupMouseX, popupMouseY, frameScale, accent);
            } else {
                drawModulePanel(contentMouseX, contentMouseY, frameScale, accent);
            }
        } finally {
            GL11.glPopMatrix();
            GLStateManager.popScissor();
        }
    }

    private void drawResizeHandle(int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect handle = PanelClickGuiLayout.resizeHandle(layout.panel());
        boolean hovered = resizingPanel || handle.contains(mouseX, mouseY);
        int color = hovered ? PanelClickGuiPalette.accent() : PanelClickGuiPalette.textMuted();
        float alpha = hovered ? 0.95f : 0.62f;
        float right = handle.right() - 4.0f;
        float bottom = handle.bottom() - 4.0f;
        for (int i = 0; i < 3; i++) {
            float offset = i * 4.0f;
            RenderServices.shapes().rect(right - 8.0f + offset, bottom,
                    right - 6.0f + offset, bottom + 2.0f,
                    PanelClickGuiPalette.alpha(color, (int) (255.0f * alpha)));
            RenderServices.shapes().rect(right, bottom - 8.0f + offset,
                    right + 2.0f, bottom - 6.0f + offset,
                    PanelClickGuiPalette.alpha(color, (int) (255.0f * alpha)));
        }
    }

    private void drawRail(int mouseX, int mouseY, float frameScale, int accent) {
        PanelClickGuiLayout.Rect rail = layout.rail();
        long now = frameTimeMillis;
        float contentProgress = epsilonAnimations.value("panel-rail-content", sidebarExpanded ? 1.0f : 0.0f,
                now, EpsilonPanelAnimation.RAIL_CONTENT_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        GLStateManager.pushScissor(rail.x(), rail.y(), rail.width(), rail.height());
        try {
            RenderServices.shapes().rounded(rail.x(), rail.y(), rail.right(), rail.bottom(),
                    EpsilonPanelMetrics.SECTION_RADIUS, PanelClickGuiPalette.surface());

            PanelClickGuiLayout.Rect menu = EpsilonPanelGeometry.railMenuButton(rail);
            boolean menuHovered = menu.contains(mouseX, mouseY);
            float menuHover = epsilonAnimations.value("panel-rail-menu-hover", menuHovered ? 1.0f : 0.0f,
                    now, EpsilonPanelAnimation.RAIL_MENU_HOVER_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            drawRect(menu, 12.0f, ClickGuiTheme.blend(
                    PanelClickGuiPalette.alpha(PanelClickGuiPalette.surface(), 0),
                    PanelClickGuiPalette.overlay(), menuHover));
            drawMenuGlyph(menu.x() + menu.width() * 0.5f, menu.y() + menu.height() * 0.5f,
                    PanelClickGuiPalette.textPrimary());
            drawRailHeader(rail, contentProgress, now);

            PanelClickGuiLayout.Rect configManager =
                    EpsilonPanelGeometry.railConfigManagerItem(rail);
            PanelClickGuiLayout.Rect settings = EpsilonPanelGeometry.railSettingsItem(rail);
            float selectedY = configManagerMode ? configManager.y()
                    : (clientSettingsMode ? settings.y()
                    : EpsilonPanelGeometry.railCategoryItem(rail, categoryIndex(selectedCategory)).y());
            float animatedSelectionY = epsilonAnimations.value("panel-rail-selection-y", selectedY,
                    now, EpsilonPanelAnimation.RAIL_SELECTION_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            RenderServices.shapes().rounded(rail.x() + EpsilonPanelGeometry.RAIL_ITEM_INSET,
                    animatedSelectionY, rail.right() - EpsilonPanelGeometry.RAIL_ITEM_INSET,
                    animatedSelectionY + CATEGORY_ITEM_H, EpsilonPanelMetrics.CARD_RADIUS,
                    PanelClickGuiPalette.selected());

            float hoveredY = Float.NaN;
            for (int i = 0; i < CATEGORIES.length; i++) {
                PanelClickGuiLayout.Rect item = EpsilonPanelGeometry.railCategoryItem(rail, i);
                if (item.contains(mouseX, mouseY)) {
                    hoveredY = item.y();
                    break;
                }
            }
            if (Float.isNaN(hoveredY) && configManager.contains(mouseX, mouseY)) {
                hoveredY = configManager.y();
            }
            if (Float.isNaN(hoveredY) && settings.contains(mouseX, mouseY)) {
                hoveredY = settings.y();
            }
            if (!Float.isNaN(hoveredY)) {
                railHoverY = hoveredY;
            } else if (Float.isNaN(railHoverY)) {
                railHoverY = selectedY;
            }
            float hoverAlpha = epsilonAnimations.value("panel-rail-hover-alpha",
                    Float.isNaN(hoveredY) ? 0.0f : 1.0f, now,
                    EpsilonPanelAnimation.RAIL_HOVER_ALPHA_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            float animatedHoverY = epsilonAnimations.value("panel-rail-hover-y", railHoverY, now,
                    EpsilonPanelAnimation.RAIL_HOVER_POSITION_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            if (hoverAlpha > 0.01f) {
                RenderServices.shapes().rounded(rail.x() + EpsilonPanelGeometry.RAIL_ITEM_INSET,
                        animatedHoverY, rail.right() - EpsilonPanelGeometry.RAIL_ITEM_INSET,
                        animatedHoverY + CATEGORY_ITEM_H, EpsilonPanelMetrics.CARD_RADIUS,
                        PanelClickGuiPalette.alpha(PanelClickGuiPalette.overlay(),
                                (int) (200.0f * hoverAlpha)));
            }

            for (int i = 0; i < CATEGORIES.length; i++) {
                ModuleType type = CATEGORIES[i];
                PanelClickGuiLayout.Rect item = EpsilonPanelGeometry.railCategoryItem(rail, i);
                boolean selected = !clientSettingsMode && !configManagerMode
                        && type == selectedCategory;
                boolean hovered = item.contains(mouseX, mouseY);
                int iconColor = selected ? PanelClickGuiPalette.textPrimary() : (hovered ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textSecondary());
                float iconCenterX = menu.x() + menu.width() * 0.5f;
                EpsilonPanelFonts.drawCenteredIcon(EpsilonPanelIcons.category(type), iconCenterX,
                        item.y() + item.height() * 0.5f + RAIL_CATEGORY_ICON_Y_OFFSET,
                        EpsilonPanelMetrics.CATEGORY_ICON_SCALE, iconColor);
                drawRailItemText(rail, item, type.getName(), String.valueOf(moduleCount(type)),
                        selected, contentProgress);
            }

            boolean configHovered = configManager.contains(mouseX, mouseY);
            float configHover = epsilonAnimations.value("panel-rail-config-hover",
                    configHovered ? 1.0f : 0.0f, now, EpsilonPanelAnimation.RAIL_MENU_HOVER_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            int configColor = configManagerMode ? PanelClickGuiPalette.textPrimary()
                    : ClickGuiTheme.blend(PanelClickGuiPalette.textMuted(),
                    PanelClickGuiPalette.textPrimary(), configHover);
            EpsilonPanelFonts.drawCenteredIcon(EpsilonPanelIcons.CONFIG,
                    menu.x() + menu.width() * 0.5f,
                    configManager.y() + configManager.height() * 0.5f
                            + RAIL_CONFIG_ICON_Y_OFFSET,
                    EpsilonPanelMetrics.CATEGORY_ICON_SCALE, configColor);
            drawRailItemText(rail, configManager,
                    languageText("Config Manager", "配置管理"), null,
                    configManagerMode, contentProgress);

            boolean settingsHovered = settings.contains(mouseX, mouseY);
            float settingsHover = epsilonAnimations.value("panel-rail-settings-hover",
                    settingsHovered ? 1.0f : 0.0f, now, EpsilonPanelAnimation.RAIL_MENU_HOVER_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            int settingsColor = clientSettingsMode ? PanelClickGuiPalette.textPrimary()
                    : ClickGuiTheme.blend(PanelClickGuiPalette.textMuted(), PanelClickGuiPalette.textPrimary(), settingsHover);
            EpsilonPanelFonts.drawCenteredIcon(EpsilonPanelIcons.SETTINGS,
                    menu.x() + menu.width() * 0.5f,
                    settings.y() + settings.height() * 0.5f
                            + RAIL_SETTINGS_ICON_Y_OFFSET,
                    EpsilonPanelMetrics.CATEGORY_ICON_SCALE, settingsColor);
            drawRailItemText(rail, settings, languageText("Settings", "设置"), null,
                    clientSettingsMode, contentProgress);
        } finally {
            GLStateManager.popScissor();
        }
    }

    private void drawRailHeader(PanelClickGuiLayout.Rect rail, float contentProgress, long now) {
        float titleProgress = epsilonAnimations.value("panel-rail-header-title", contentProgress,
                now, EpsilonPanelAnimation.RAIL_HEADER_TITLE_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float subtitleProgress = epsilonAnimations.value("panel-rail-header-subtitle",
                contentProgress > 0.08f ? 1.0f : 0.0f, now,
                EpsilonPanelAnimation.RAIL_HEADER_SUBTITLE_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float dividerProgress = epsilonAnimations.value("panel-rail-header-divider",
                contentProgress > 0.12f ? 1.0f : 0.0f, now,
                EpsilonPanelAnimation.RAIL_HEADER_DIVIDER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        if (titleProgress <= 0.02f) {
            return;
        }
        int titleColor = ClickGuiTheme.withAlpha(PanelClickGuiPalette.textPrimary(), (int) (255.0f * titleProgress));
        int subtitleColor = ClickGuiTheme.withAlpha(PanelClickGuiPalette.textSecondary(), (int) (210.0f * subtitleProgress));
        float titleX = rail.x() + 38.0f + (1.0f - titleProgress) * 8.0f;
        float titleY = rail.y() + 7.0f;
        EpsilonPanelFonts.text(EpsilonPanelMetrics.RAIL_TITLE_SCALE).drawString("Yozakura", titleX,
                titleY + EpsilonPanelFonts.BASELINE_COMPENSATION, titleColor);
        float subtitleY = titleY + EpsilonPanelFonts.lineHeight(EpsilonPanelMetrics.RAIL_TITLE_SCALE) + 3.0f;
        if (subtitleProgress > 0.02f) {
            EpsilonPanelFonts.text(EpsilonPanelMetrics.RAIL_SUBTITLE_SCALE).drawString("v1.5.0",
                    rail.x() + 38.0f + (1.0f - subtitleProgress) * 10.0f,
                    subtitleY + EpsilonPanelFonts.BASELINE_COMPENSATION, subtitleColor);
        }
        if (dividerProgress > 0.02f) {
            float dividerY = subtitleY + EpsilonPanelFonts.lineHeight(EpsilonPanelMetrics.RAIL_SUBTITLE_SCALE) + 4.0f;
            float dividerX = rail.x() + 7.0f + (1.0f - dividerProgress) * 6.0f;
            float dividerWidth = (rail.width() - 14.0f) * dividerProgress;
            RenderServices.shapes().rect(dividerX, dividerY, dividerX + dividerWidth, dividerY + 1.0f,
                    ClickGuiTheme.withAlpha(PanelClickGuiPalette.textMuted(), (int) (120.0f * dividerProgress)));
            RenderServices.shapes().rect(dividerX, dividerY,
                    dividerX + Math.min(18.0f, dividerWidth), dividerY + 1.0f,
                    ClickGuiTheme.withAlpha(PanelClickGuiPalette.textSecondary(), (int) (52.0f * dividerProgress)));
        }
    }

    private void drawRailItemText(PanelClickGuiLayout.Rect rail, PanelClickGuiLayout.Rect item,
                                  String label, String count, boolean selected, float contentProgress) {
        if (contentProgress <= 0.02f) {
            return;
        }
        float textOffset = (1.0f - contentProgress) * 5.0f;
        int labelBase = selected ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textPrimary();
        int countBase = selected ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textSecondary();
        int labelColor = ClickGuiTheme.withAlpha(labelBase, (int) (255.0f * contentProgress));
        int countColor = ClickGuiTheme.withAlpha(countBase, (int) (220.0f * contentProgress));
        EpsilonPanelFonts.text(EpsilonPanelMetrics.CATEGORY_LABEL_SCALE).drawString(label,
                item.x() + 30.0f + textOffset,
                EpsilonPanelFonts.centeredY(item.y(), item.height(),
                        EpsilonPanelMetrics.CATEGORY_LABEL_SCALE), labelColor);
        if (count != null) {
            CFontRenderer countFont = EpsilonPanelFonts.text(EpsilonPanelMetrics.CATEGORY_COUNT_SCALE);
            float countWidth = countFont.getStringWidth(count);
            countFont.drawString(count, item.right() - 12.0f - countWidth,
                    EpsilonPanelFonts.centeredY(item.y(), item.height(),
                            EpsilonPanelMetrics.CATEGORY_COUNT_SCALE), countColor);
        }
    }

    private int categoryIndex(ModuleType type) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i] == type) {
                return i;
            }
        }
        return 0;
    }

    private void drawModulePanel(int mouseX, int mouseY, float frameScale, int accent) {
        PanelClickGuiLayout.Rect bounds = layout.modules();
        RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                13.0f, PanelClickGuiPalette.raised());
        EpsilonPanelFonts.text(0.86f).drawString(selectedCategory.getName(),
                bounds.x() + 14.0f, bounds.y() + 12.0f + EpsilonPanelFonts.BASELINE_COMPENSATION,
                PanelClickGuiPalette.textPrimary());
        drawSearch(bounds, mouseX, mouseY);

        PanelClickGuiLayout.Rect viewport = moduleViewport();
        List<Module> modules = modulesInCategory();
        moduleMaxScroll = Math.max(0.0f, modules.size() * (MODULE_ROW_H + ROW_GAP) - viewport.height());
        moduleScroll = clamp(moduleScroll, 0.0f, moduleMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            float y = viewport.y() - moduleScroll;
            float rowWidth = moduleMaxScroll > 0.0f
                    ? viewport.width() - PanelClickGuiScroll.TOTAL_WIDTH : viewport.width();
            for (Module module : modules) {
                drawModuleRow(module, viewport.x(), y, rowWidth, mouseX, mouseY, frameScale, accent);
                y += MODULE_ROW_H + ROW_GAP;
            }
        } finally {
            GLStateManager.popScissor();
        }
        drawScrollbar(viewport, moduleScroll, moduleMaxScroll, modules.size() * (MODULE_ROW_H + ROW_GAP), mouseX, mouseY,
                false);
    }

    private void drawConfigManagerPage(int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect bounds = layout.detail();
        RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                13.0f, PanelClickGuiPalette.raised());
        EpsilonPanelFonts.text(0.86f).drawString(languageText("Config Manager", "配置管理"),
                bounds.x() + 14.0f,
                EpsilonPanelFonts.centeredY(bounds.y() + 10.0f,
                        EpsilonPanelGeometry.HEADER_CONTROL_HEIGHT, 0.86f),
                PanelClickGuiPalette.textPrimary());
        PanelClickGuiLayout.Rect close = EpsilonPanelGeometry.detailCloseButton(bounds);
        boolean closeHovered = close.contains(mouseX, mouseY);
        drawRect(close, close.height() * 0.5f,
                closeHovered ? PanelClickGuiPalette.overlay() : PanelClickGuiPalette.surface());
        drawCloseGlyph(close, closeHovered
                ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textMuted());

        drawConfigTab(PanelClubGeometry.localTab(bounds),
                languageText("Local", "本地配置"), !cloudConfigMode, mouseX, mouseY);
        drawConfigTab(PanelClubGeometry.cloudTab(bounds),
                languageText("Config Hall", "配置大厅"), cloudConfigMode, mouseX, mouseY);
        if (cloudConfigMode) {
            drawCloudConfigPage(bounds, mouseX, mouseY);
            return;
        }

        PanelClickGuiLayout.Rect list = configProfileListBounds(bounds);
        RenderServices.shapes().rounded(list.x(), list.y(), list.right(), list.bottom(),
                EpsilonPanelMetrics.CARD_RADIUS, PanelClickGuiPalette.surface());
        int visibleRows = Math.max(1, (int) (list.height() / CONFIG_ROW_H));
        int maxScroll = Math.max(0, configProfiles.size() - visibleRows);
        configProfileScroll = Math.max(0, Math.min(configProfileScroll, maxScroll));
        GLStateManager.pushScissor(list.x(), list.y(), list.width(), list.height());
        try {
            if (configProfiles.isEmpty()) {
                EpsilonPanelFonts.drawCenteredText(languageText("No .yzk profiles", "没有 .yzk 配置"),
                        list.x() + list.width() * 0.5f, list.y(), list.height(), 0.62f,
                        PanelClickGuiPalette.textMuted());
            } else {
                float y = list.y();
                int end = Math.min(configProfiles.size(), configProfileScroll + visibleRows);
                for (int index = configProfileScroll; index < end; index++) {
                    PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(
                            list.x() + 4.0f, y + 2.0f, list.width() - 8.0f,
                            CONFIG_ROW_H - CONFIG_ROW_GAP);
                    boolean selected = index == selectedConfigProfile;
                    boolean hovered = row.contains(mouseX, mouseY);
                    int rowColor = selected ? PanelClickGuiPalette.selected()
                            : (hovered ? PanelClickGuiPalette.overlay() : PanelClickGuiPalette.surface());
                    drawRect(row, EpsilonPanelMetrics.CARD_RADIUS, rowColor);
                    EpsilonPanelFonts.text(0.64f).drawString(configProfiles.get(index) + ".yzk",
                            row.x() + 9.0f,
                            EpsilonPanelFonts.centeredY(row.y(), row.height(), 0.64f),
                            selected ? PanelClickGuiPalette.textPrimary()
                                    : PanelClickGuiPalette.textSecondary());
                    y += CONFIG_ROW_H;
                }
            }
        } finally {
            GLStateManager.popScissor();
        }

        PanelClickGuiLayout.Rect nameField = configNameFieldBounds(bounds);
        boolean nameHovered = nameField.contains(mouseX, mouseY);
        RenderServices.shapes().roundedBorderWH(nameField.x(), nameField.y(),
                nameField.width(), nameField.height(), EpsilonPanelMetrics.CONTROL_RADIUS, 1.0f,
                PanelClickGuiPalette.surface(), configNameFocused
                        ? PanelClickGuiPalette.accent()
                        : (nameHovered ? PanelClickGuiPalette.textMuted() : PanelClickGuiPalette.border()));
        String nameText = configName.isEmpty() && !configNameFocused
                ? languageText("Profile name", "配置名称") : configName;
        CFontRenderer nameFont = EpsilonPanelFonts.text(0.60f);
        GLStateManager.pushScissor(nameField.x(), nameField.y(),
                nameField.width(), nameField.height());
        try {
            nameFont.drawString(nameText, nameField.x() + 8.0f,
                    EpsilonPanelFonts.centeredY(nameField.y(), nameField.height(), 0.60f),
                    configName.isEmpty() && !configNameFocused
                            ? PanelClickGuiPalette.textMuted() : PanelClickGuiPalette.textPrimary());
            if (configNameFocused) {
                int cursor = Math.max(0, Math.min(configNameCursor, configName.length()));
                float caretX = nameField.x() + 8.0f
                        + nameFont.getStringWidth(configName.substring(0, cursor));
                RenderServices.shapes().rect(caretX, nameField.y() + 5.0f, caretX + 1.0f,
                        nameField.bottom() - 5.0f, PanelClickGuiPalette.accent());
            }
        } finally {
            GLStateManager.popScissor();
        }

        drawConfigButton(configSaveButton(bounds), languageText("Save", "保存"), mouseX, mouseY, true);
        drawConfigButton(configLoadButton(bounds), languageText("Load", "加载"), mouseX, mouseY,
                selectedConfigProfile >= 0 && selectedConfigProfile < configProfiles.size());
        drawConfigButton(configRefreshButton(bounds), languageText("Refresh", "刷新"), mouseX, mouseY, true);
        drawConfigButton(configFolderButton(bounds), languageText("Folder", "目录"), mouseX, mouseY, true);
        if (!configStatus.isEmpty()) {
            EpsilonPanelFonts.text(0.54f).drawString(configStatus, bounds.x() + 14.0f,
                    bounds.bottom() - 18.0f, configStatusColor);
        }
    }

    private void drawConfigTab(PanelClickGuiLayout.Rect tab, String label, boolean selected,
                               int mouseX, int mouseY) {
        boolean hovered = tab.contains(mouseX, mouseY);
        drawRect(tab, EpsilonPanelMetrics.CONTROL_RADIUS, selected
                ? PanelClickGuiPalette.selected()
                : (hovered ? PanelClickGuiPalette.overlay() : PanelClickGuiPalette.surface()));
        EpsilonPanelFonts.drawCenteredText(label, tab.x() + tab.width() * 0.5f,
                tab.y(), tab.height(), 0.56f, selected
                        ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textSecondary());
    }

    private void drawCloudConfigPage(PanelClickGuiLayout.Rect bounds, int mouseX, int mouseY) {
        ClubService.ClubViewState state = clubService.getState();
        String identity = state.isAuthenticated()
                ? languageText("Uploader: ", "上传身份：") + state.getUsername()
                : languageText("Browse and download without sign-in",
                "无需登录即可浏览和下载");
        drawClippedSingleLine(identity, PanelClubGeometry.identity(bounds), 0.58f,
                state.isAuthenticated() ? PanelClickGuiPalette.textSecondary()
                        : PanelClickGuiPalette.textMuted());
        drawCloudConfigSearchField(bounds, mouseX, mouseY);
        List<ClubConfigSummary> visibleConfigs = visibleCloudConfigs(state);
        drawCloudConfigList(bounds, visibleConfigs, mouseX, mouseY);
        boolean selected = selectedCloudConfig() != null;
        boolean available = !state.isBusy();
        boolean localSelected = selectedConfigName() != null;
        boolean ownSelected = ownsSelectedCloudConfig(state);
        drawConfigButton(PanelClubGeometry.uploadButton(bounds), languageText("Upload", "上传"),
                mouseX, mouseY, available && localSelected);
        drawConfigButton(PanelClubGeometry.downloadButton(bounds), languageText("Download", "下载"),
                mouseX, mouseY, available && selected);
        drawConfigButton(PanelClubGeometry.useButton(bounds), languageText("Use", "使用"),
                mouseX, mouseY, available && selected);
        drawConfigButton(PanelClubGeometry.deleteButton(bounds), languageText("Delete", "删除"),
                mouseX, mouseY, available && ownSelected);
        drawConfigButton(PanelClubGeometry.refreshButton(bounds), languageText("Refresh", "刷新"),
                mouseX, mouseY, available);
        if (!state.getStatus().isEmpty()) {
            drawClippedSingleLine(state.getStatus(), PanelClubGeometry.status(bounds), 0.54f,
                    state.isError() ? ClickGUI.currentPalette().getDanger()
                            : PanelClickGuiPalette.textMuted());
        }
    }

    private void drawClippedSingleLine(String text, PanelClickGuiLayout.Rect bounds,
                                       float scale, int color) {
        GLStateManager.pushScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
            EpsilonPanelFonts.text(scale).drawString(text, bounds.x(),
                    EpsilonPanelFonts.centeredY(bounds.y(), bounds.height(), scale), color);
        } finally {
            GLStateManager.popScissor();
        }
    }

    private void drawCloudConfigSearchField(PanelClickGuiLayout.Rect bounds,
                                            int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect field = PanelClubGeometry.searchField(bounds);
        boolean hovered = field.contains(mouseX, mouseY);
        RenderServices.shapes().roundedBorderWH(field.x(), field.y(), field.width(), field.height(),
                EpsilonPanelMetrics.CONTROL_RADIUS, 1.0f, PanelClickGuiPalette.surface(),
                cloudConfigSearchFocused ? PanelClickGuiPalette.accent()
                        : (hovered ? PanelClickGuiPalette.textMuted() : PanelClickGuiPalette.border()));
        boolean placeholder = cloudConfigSearchText.isEmpty() && !cloudConfigSearchFocused;
        String text = placeholder ? languageText("Search name or uploader", "搜索名称或上传者")
                : cloudConfigSearchText;
        CFontRenderer font = EpsilonPanelFonts.text(0.58f);
        GLStateManager.pushScissor(field.x(), field.y(), field.width(), field.height());
        try {
            font.drawString(text, field.x() + 8.0f,
                    EpsilonPanelFonts.centeredY(field.y(), field.height(), 0.58f),
                    placeholder ? PanelClickGuiPalette.textMuted()
                            : PanelClickGuiPalette.textPrimary());
            if (cloudConfigSearchFocused) {
                int cursor = Math.max(0, Math.min(cloudConfigSearchCursor,
                        cloudConfigSearchText.length()));
                float caretX = field.x() + 8.0f
                        + font.getStringWidth(cloudConfigSearchText.substring(0, cursor));
                RenderServices.shapes().rect(caretX, field.y() + 4.0f, caretX + 1.0f,
                        field.bottom() - 4.0f, PanelClickGuiPalette.accent());
            }
        } finally {
            GLStateManager.popScissor();
        }
    }

    private void drawCloudConfigList(PanelClickGuiLayout.Rect bounds,
                                     List<ClubConfigSummary> configs, int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect list = PanelClubGeometry.cloudList(bounds);
        RenderServices.shapes().rounded(list.x(), list.y(), list.right(), list.bottom(),
                EpsilonPanelMetrics.CARD_RADIUS, PanelClickGuiPalette.surface());
        int visibleRows = Math.max(1, (int) (list.height() / CONFIG_ROW_H));
        cloudConfigScroll = Math.max(0, Math.min(cloudConfigScroll,
                Math.max(0, configs.size() - visibleRows)));
        if (PanelCloudConfigSearchModel.findById(configs, selectedCloudConfigId) == null) {
            selectedCloudConfigId = configs.isEmpty() ? null : configs.get(0).getId();
        }
        GLStateManager.pushScissor(list.x(), list.y(), list.width(), list.height());
        try {
            if (configs.isEmpty()) {
                EpsilonPanelFonts.drawCenteredText(languageText("No hall configs", "配置大厅暂无内容"),
                        list.x() + list.width() * 0.5f, list.y(), list.height(), 0.62f,
                        PanelClickGuiPalette.textMuted());
            } else {
                float y = list.y();
                int end = Math.min(configs.size(), cloudConfigScroll + visibleRows);
                for (int index = cloudConfigScroll; index < end; index++) {
                    PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(list.x() + 4.0f,
                            y + 2.0f, list.width() - 8.0f, CONFIG_ROW_H - CONFIG_ROW_GAP);
                    ClubConfigSummary config = configs.get(index);
                    boolean selected = config.getId().equals(selectedCloudConfigId);
                    boolean hovered = row.contains(mouseX, mouseY);
                    drawRect(row, EpsilonPanelMetrics.CARD_RADIUS, selected
                            ? PanelClickGuiPalette.selected()
                            : (hovered ? PanelClickGuiPalette.overlay() : PanelClickGuiPalette.surface()));
                    String owner = config.getOwner();
                    String label = owner == null || owner.isEmpty()
                            ? config.getName() : config.getName() + "  ·  " + owner;
                    GLStateManager.pushScissor(row.x() + 9.0f, row.y(),
                            row.width() - 18.0f, row.height());
                    try {
                        EpsilonPanelFonts.text(0.64f).drawString(label,
                                row.x() + 9.0f,
                                EpsilonPanelFonts.centeredY(row.y(), row.height(), 0.64f),
                                selected ? PanelClickGuiPalette.textPrimary()
                                        : PanelClickGuiPalette.textSecondary());
                    } finally {
                        GLStateManager.popScissor();
                    }
                    y += CONFIG_ROW_H;
                }
            }
        } finally {
            GLStateManager.popScissor();
        }
    }

    private void drawConfigButton(PanelClickGuiLayout.Rect button, String label,
                                  int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && button.contains(mouseX, mouseY);
        int fill = enabled
                ? (hovered ? PanelClickGuiPalette.selected() : PanelClickGuiPalette.surface())
                : PanelClickGuiPalette.raised();
        drawRect(button, EpsilonPanelMetrics.CONTROL_RADIUS, fill);
        EpsilonPanelFonts.drawCenteredText(label,
                button.x() + button.width() * 0.5f, button.y(), button.height(), 0.56f,
                enabled ? (hovered ? PanelClickGuiPalette.textPrimary()
                        : PanelClickGuiPalette.textSecondary()) : PanelClickGuiPalette.textMuted());
    }

    private void drawModuleRow(Module module, float x, float y, float width, int mouseX, int mouseY,
                               float frameScale, int accent) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + MODULE_ROW_H;
        boolean selected = module == selectedModule;
        long now = frameTimeMillis;
        float hover = epsilonAnimations.value("panel-module-hover:" + module.getName(),
                hovered ? 1.0f : 0.0f, now, EpsilonPanelAnimation.MODULE_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float selectedT = epsilonAnimations.value("panel-module-selected:" + module.getName(),
                selected ? 1.0f : 0.0f, now, EpsilonPanelAnimation.MODULE_SELECTION_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        int surface = ClickGuiTheme.blend(PanelClickGuiPalette.raised(),
                PanelClickGuiPalette.overlay(), hover);
        RenderServices.shapes().rounded(x, y, x + width, y + MODULE_ROW_H,
                EpsilonPanelMetrics.CARD_RADIUS, surface);
        if (selectedT > 0.01f) {
            RenderServices.shapes().rounded(x, y, x + width, y + MODULE_ROW_H, 9.0f,
                    ClickGuiTheme.withAlpha(accent, (int) (42.0f * selectedT)));
        }
        int titleColor = ClickGuiTheme.blend(PanelClickGuiPalette.textPrimary(), PanelClickGuiPalette.selectedText(), selectedT);
        String title = panelModuleName(module);
        CFontRenderer titleFont = EpsilonPanelFonts.text(PanelClickGuiModuleRow.TITLE_SCALE);
        titleFont.drawString(title, x + 12.0f,
                y + PanelClickGuiModuleRow.titleY(MODULE_ROW_H, titleFont.getHeight()), titleColor);
        String description = panelModuleDescription(module);
        CFontRenderer descriptionFont = EpsilonPanelFonts.text(PanelClickGuiModuleRow.DESCRIPTION_SCALE);
        GLStateManager.pushScissor(x + 12.0f, y,
                Math.max(1.0f, width - 92.0f), MODULE_ROW_H);
        try {
            descriptionFont.drawString(description, x + 12.0f,
                    y + PanelClickGuiModuleRow.descriptionY(MODULE_ROW_H, descriptionFont.getHeight()),
                    PanelClickGuiPalette.textMuted());
        } finally {
            GLStateManager.popScissor();
        }

        PanelClickGuiLayout.Rect rowBounds = new PanelClickGuiLayout.Rect(x, y, width, MODULE_ROW_H);
        PanelClickGuiLayout.Rect settingsBounds = EpsilonPanelGeometry.moduleSettingsButton(rowBounds);
        boolean settingsHovered = settingsBounds.contains(mouseX, mouseY);
        if (settingsHovered) {
            drawRect(settingsBounds, 8.0f, PanelClickGuiPalette.overlay());
        }
        float settingsIconSize = 14.0f;
        float settingsIconX = alignHalfPixel(settingsBounds.x()
                + (settingsBounds.width() - settingsIconSize) * 0.5f);
        float settingsIconY = alignHalfPixel(settingsBounds.y()
                + (settingsBounds.height() - settingsIconSize) * 0.5f
                + MODULE_SETTINGS_ICON_Y_OFFSET);
        RenderUtil.drawTexturedRectTinted(PANEL_SETTINGS_ICON,
                settingsIconX, settingsIconY,
                settingsIconX + settingsIconSize, settingsIconY + settingsIconSize,
                settingsHovered ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textMuted());
        PanelClickGuiLayout.Rect toggleBounds = EpsilonPanelGeometry.moduleSwitch(rowBounds);
        boolean toggleHovered = toggleBounds.contains(mouseX, mouseY);
        drawSwitch(toggleBounds.x(), toggleBounds.y(), module.getState(), toggleHovered, frameScale,
                "panel-module-switch:" + module.getName());
    }

    private PanelClickGuiLayout.Rect configProfileListBounds(PanelClickGuiLayout.Rect bounds) {
        return PanelConfigManagerGeometry.profileList(bounds);
    }

    private PanelClickGuiLayout.Rect configNameFieldBounds(PanelClickGuiLayout.Rect bounds) {
        return PanelConfigManagerGeometry.nameField(bounds);
    }

    private PanelClickGuiLayout.Rect configSaveButton(PanelClickGuiLayout.Rect bounds) {
        return PanelConfigManagerGeometry.saveButton(bounds);
    }

    private PanelClickGuiLayout.Rect configLoadButton(PanelClickGuiLayout.Rect bounds) {
        return PanelConfigManagerGeometry.loadButton(bounds);
    }

    private PanelClickGuiLayout.Rect configRefreshButton(PanelClickGuiLayout.Rect bounds) {
        return PanelConfigManagerGeometry.refreshButton(bounds);
    }

    private PanelClickGuiLayout.Rect configFolderButton(PanelClickGuiLayout.Rect bounds) {
        return PanelConfigManagerGeometry.folderButton(bounds);
    }

    private void drawDetailPanel(int mouseX, int mouseY, int popupMouseX, int popupMouseY,
                                 float frameScale, int accent) {
        PanelClickGuiLayout.Rect bounds = layout.detail();
        RenderServices.shapes().rounded(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                13.0f, PanelClickGuiPalette.raised());
        Module owner = detailOwner();
        String title = clientSettingsMode ? languageText("Client Settings", "客户端设置")
                : (owner == null ? languageText("No module", "未选择模块") : panelModuleName(owner));
        EpsilonPanelFonts.text(0.86f).drawString(title,
                bounds.x() + 14.0f,
                EpsilonPanelFonts.centeredY(bounds.y() + 10.0f,
                        EpsilonPanelGeometry.HEADER_CONTROL_HEIGHT,
                        0.86f), PanelClickGuiPalette.textPrimary());
        PanelClickGuiLayout.Rect close = EpsilonPanelGeometry.detailCloseButton(bounds);
        boolean closeHovered = close.contains(mouseX, mouseY);
        drawRect(close, close.height() * 0.5f,
                closeHovered ? PanelClickGuiPalette.overlay() : PanelClickGuiPalette.surface());
        drawCloseGlyph(close, closeHovered ? PanelClickGuiPalette.textPrimary() : PanelClickGuiPalette.textMuted());
        if (owner == null) {
            FontLoaders.INTER12.drawString(languageText("Select a module", "请选择模块"), bounds.x() + 8.0f,
                    bounds.y() + 48.0f, PanelClickGuiPalette.textMuted());
            return;
        }

        if (clientSettingsMode) {
            drawLanguageControl(bounds, mouseX, mouseY);
        } else {
            EpsilonPanelGeometry.DetailHeader headerGeometry = EpsilonPanelGeometry.detailHeader(bounds);
            drawRect(headerGeometry.background(), EpsilonPanelMetrics.CARD_RADIUS,
                    PanelClickGuiPalette.surface());
            String keyLabel = listeningKeybind ? "..." : compactKeyName(owner.getKey());
            PanelClickGuiLayout.Rect keybind = headerGeometry.keybind();
            long now = frameTimeMillis;
            float keybindHover = epsilonAnimations.value("panel-keybind-hover",
                    keybind.contains(mouseX, mouseY) ? 1.0f : 0.0f, now,
                    EpsilonPanelAnimation.KEYBIND_HOVER_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            float keybindFocus = epsilonAnimations.value("panel-keybind-focus",
                    listeningKeybind ? 1.0f : 0.0f, now,
                    EpsilonPanelAnimation.KEYBIND_FOCUS_MS,
                    gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
            float haloInset = 1.5f * keybindFocus;
            if (haloInset > 0.01f) {
                RenderServices.shapes().rounded(keybind.x() - haloInset, keybind.y() - haloInset,
                        keybind.right() + haloInset, keybind.bottom() + haloInset,
                        8.0f + haloInset,
                        ClickGuiTheme.withAlpha(PanelClickGuiPalette.accent(), (int) (28.0f * keybindFocus)));
            }
            int keyBackground = ClickGuiTheme.blend(PanelClickGuiPalette.selected(),
                    PanelClickGuiPalette.alpha(PanelClickGuiPalette.accent(), 236), keybindFocus);
            drawRect(keybind, 8.0f, keyBackground);
            if (keybindHover > 0.01f) {
                drawRect(keybind, 8.0f, ClickGuiTheme.withAlpha(PanelClickGuiPalette.textPrimary(),
                        (int) ((listeningKeybind ? 18.0f : 12.0f) * keybindHover)));
            }
            float keyScale = keyLabel.length() >= 3 ? 0.42f : 0.50f;
            int keyForeground = ClickGuiTheme.blend(PanelClickGuiPalette.textPrimary(), PanelClickGuiPalette.selectedText(), keybindFocus);
            EpsilonPanelFonts.drawCenteredText(keyLabel,
                    keybind.x() + keybind.width() * 0.5f, keybind.y(), keybind.height(), keyScale,
                    keyForeground);
            drawSegmentedControl(headerGeometry.bindMode(),
                    languageText("Toggle", "切换"), languageText("Hold", "按住"),
                    owner.getBindMode() == Module.BindMode.HOLD, mouseX, mouseY);
            drawSegmentedControl(headerGeometry.hidden(),
                    languageText("Visible", "显示"), languageText("Hidden", "隐藏"),
                    owner.isHidden(), mouseX, mouseY);
        }

        PanelClickGuiLayout.Rect viewport = detailViewport();
        List<Value> visible = visibleValues(owner);
        detailMaxScroll = Math.max(0.0f, visible.size() * values.rowHeight() - viewport.height());
        detailScroll = clamp(detailScroll, 0.0f, detailMaxScroll);
        GLStateManager.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        try {
            float y = viewport.y() - detailScroll;
            float rowW = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
            for (Value value : visible) {
                String valueKey = "panel:" + owner.getName() + ":" + value.getName();
                PanelClickGuiLayout.Rect valueBounds = new PanelClickGuiLayout.Rect(
                        viewport.x(), y, rowW, VALUE_ROW_H);
                float settingHover = epsilonAnimations.value("panel-setting-hover:" + valueKey,
                        valueBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f,
                        frameTimeMillis, EpsilonPanelAnimation.SETTING_HOVER_MS,
                        gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
                RenderServices.shapes().rounded(viewport.x(), y, viewport.x() + rowW,
                        y + VALUE_ROW_H, EpsilonPanelMetrics.CARD_RADIUS,
                        ClickGuiTheme.blend(PanelClickGuiPalette.raised(),
                                PanelClickGuiPalette.overlay(), settingHover));
                PanelPaletteColorControl.Group colorGroup =
                        PanelPaletteColorControl.groupFor(value);
                if (colorGroup != null) {
                    drawPaletteColorRow(colorGroup,
                            new PanelClickGuiLayout.Rect(viewport.x() + 5.0f, y + 1.0f,
                                    rowW - 10.0f, VALUE_ROW_H), mouseX, mouseY);
                } else {
                    values.drawValue(value, valueKey,
                            viewport.x() + 5.0f, y + 1.0f, rowW - 10.0f, mouseX, mouseY,
                            frameScale, true);
                }
                y += values.rowHeight();
            }
        } finally {
            GLStateManager.popScissor();
        }
        drawOpenModeOverlay(viewport, visible, popupMouseX, popupMouseY, frameScale);
        drawScrollbar(viewport, detailScroll, detailMaxScroll, visible.size() * values.rowHeight(), mouseX, mouseY,
                true);
    }

    private void drawPaletteColorRow(PanelPaletteColorControl.Group group,
                                     PanelClickGuiLayout.Rect row,
                                     int mouseX,
                                     int mouseY) {
        PanelClickGuiLayout.Rect swatch = PanelPaletteColorControl.swatchBounds(row);
        float labelWidth = Math.max(0.0f, swatch.x() - row.x() - 8.0f);
        GLStateManager.pushScissor(row.x(), row.y(), labelWidth, row.height());
        try {
            EpsilonPanelFonts.text(EpsilonPanelMetrics.SETTING_LABEL_SCALE).drawString(
                    group.label(),
                    row.x(),
                    EpsilonPanelFonts.centeredY(row.y(), row.height(),
                            EpsilonPanelMetrics.SETTING_LABEL_SCALE),
                    PanelClickGuiPalette.textSecondary());
        } finally {
            GLStateManager.popScissor();
        }

        boolean hovered = swatch.contains(mouseX, mouseY);
        int border = hovered ? PanelClickGuiPalette.accent() : PanelClickGuiPalette.border();
        RenderServices.shapes().shadow(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(),
                5.0f, PanelClickGuiPalette.shadow(hovered ? 92 : 58), 4, 5.0f);
        RenderServices.shapes().roundedBorderWH(swatch.x(), swatch.y(), swatch.width(),
                swatch.height(), 5.0f, 1.0f, group.color(), border);
    }

    private void drawOpenPaletteColorPicker(int mouseX, int mouseY) {
        if (!paletteColorPicker.isOpen()) {
            return;
        }
        PanelClickGuiLayout.Rect anchor = paletteColorAnchor(paletteColorPicker.group());
        if (anchor == null) {
            paletteColorPicker.close();
            return;
        }
        PanelClickGuiLayout.Rect popup = paletteColorPicker.bounds(anchor, layout.detail());
        GLStateManager.pushScissor(layout.detail().x(), layout.detail().y(),
                layout.detail().width(), layout.detail().height());
        try {
            paletteColorPicker.draw(popup, mouseX, mouseY);
        } finally {
            GLStateManager.popScissor();
        }
    }

    private PanelClickGuiLayout.Rect paletteColorAnchor(PanelPaletteColorControl.Group target) {
        Module owner = detailOwner();
        if (owner == null || target == null) {
            return null;
        }
        PanelClickGuiLayout.Rect viewport = detailViewport();
        float y = viewport.y() - detailScroll;
        float rowWidth = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
        for (Value value : visibleValues(owner)) {
            PanelPaletteColorControl.Group group = PanelPaletteColorControl.groupFor(value);
            if (group == target) {
                PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(
                        viewport.x() + 5.0f, y + 1.0f, rowWidth - 10.0f, VALUE_ROW_H);
                return PanelPaletteColorControl.swatchBounds(row);
            }
            y += values.rowHeight();
        }
        return null;
    }

    /** Replays an opened enum menu above the viewport clip, matching Epsilon's popup layer. */
    private void drawOpenModeOverlay(PanelClickGuiLayout.Rect viewport, List<Value> visible,
                                     int mouseX, int mouseY, float frameScale) {
        String openKey = values.dropdownRenderKey();
        Module owner = detailOwner();
        if (openKey == null || owner == null) {
            return;
        }
        float y = viewport.y() - detailScroll;
        float rowW = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
        for (Value value : visible) {
            String key = "panel:" + owner.getName() + ":" + value.getName();
            if (key.equals(openKey)) {
                GLStateManager.pushScissor(layout.detail().x(), layout.detail().y(),
                        layout.detail().width(), layout.detail().height());
                try {
                    values.drawValue(value, key, viewport.x() + 5.0f, y + 1.0f,
                            rowW - 10.0f, mouseX, mouseY, frameScale, true);
                } finally {
                    GLStateManager.popScissor();
                }
                return;
            }
            y += values.rowHeight();
        }
    }

    private void drawSwitch(float x, float y, boolean on, boolean hovered, float frameScale, String key) {
        long now = frameTimeMillis;
        float target = on ? 1.0f : 0.0f;
        float progress = epsilonAnimations.value(key, target, now,
                EpsilonPanelAnimation.TOGGLE_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_ELASTIC);
        float hoverProgress = epsilonAnimations.value(key + ":hover", hovered ? 1.0f : 0.0f, now,
                EpsilonPanelAnimation.TOGGLE_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float visualProgress = clamp(progress, 0.0f, 1.0f);
        int track = ClickGuiTheme.blend(PanelClickGuiPalette.overlay(),
                PanelClickGuiPalette.accent(), visualProgress);
        RenderServices.shapes().rounded(x, y, x + 26.0f, y + 16.0f, 8.0f, track);
        float handleSize = 8.0f + 4.0f * visualProgress;
        float stretch = 4.0f * visualProgress * (1.0f - visualProgress);
        float handleWidth = handleSize + 3.5f * stretch;
        float inset = 4.0f - 2.0f * visualProgress;
        float minCenter = x + inset + handleWidth * 0.5f;
        float maxCenter = x + 26.0f - inset - handleWidth * 0.5f;
        float handleCenterX = minCenter + (maxCenter - minCenter) * progress;
        float handleY = y + (16.0f - handleSize) * 0.5f;
        int handle = ClickGuiTheme.blend(PanelClickGuiPalette.textMuted(),
                PanelClickGuiPalette.canvas(), visualProgress);
        if (hoverProgress > 0.02f) {
            RenderServices.shapes().rounded(handleCenterX - 10.0f, y - 2.0f,
                    handleCenterX + 10.0f, y + 18.0f, 10.0f,
                    ClickGuiTheme.withAlpha(PanelClickGuiPalette.textPrimary(), (int) (18.0f * hoverProgress)));
        }
        RenderServices.shapes().rounded(handleCenterX - handleWidth * 0.5f, handleY,
                handleCenterX + handleWidth * 0.5f, handleY + handleSize,
                handleSize * 0.5f, handle);
    }

    private void drawSegmentedControl(PanelClickGuiLayout.Rect bounds, String left, String right,
                                      boolean rightSelected, int mouseX, int mouseY) {
        long now = frameTimeMillis;
        String animationKey = "panel-segment:" + left + ":" + right;
        float selection = epsilonAnimations.value(animationKey, rightSelected ? 1.0f : 0.0f,
                now, EpsilonPanelAnimation.SEGMENT_SELECTION_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float hover = epsilonAnimations.value(animationKey + ":hover",
                bounds.contains(mouseX, mouseY) ? 1.0f : 0.0f, now,
                EpsilonPanelAnimation.SEGMENT_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float shellInset = 1.0f;
        float innerX = bounds.x() + shellInset;
        float innerY = bounds.y() + shellInset;
        float innerWidth = bounds.width() - shellInset * 2.0f;
        float innerHeight = bounds.height() - shellInset * 2.0f;
        float segmentWidth = innerWidth * 0.5f;
        drawRect(bounds, EpsilonPanelMetrics.CONTROL_RADIUS, PanelClickGuiPalette.border());
        RenderServices.shapes().rounded(innerX, innerY, innerX + innerWidth, innerY + innerHeight,
                EpsilonPanelMetrics.CONTROL_RADIUS - shellInset, PanelClickGuiPalette.raised());
        if (hover > 0.01f) {
            RenderServices.shapes().rounded(innerX, innerY, innerX + innerWidth, innerY + innerHeight,
                    EpsilonPanelMetrics.CONTROL_RADIUS - shellInset,
                    ClickGuiTheme.withAlpha(PanelClickGuiPalette.textPrimary(), (int) (14.0f * hover)));
        }
        RenderServices.shapes().rect(innerX + segmentWidth - 0.5f, innerY + 3.0f,
                innerX + segmentWidth + 0.5f, innerY + innerHeight - 3.0f,
                PanelClickGuiPalette.border());
        float indicatorInset = 1.5f;
        float indicatorX = innerX + indicatorInset + segmentWidth * selection;
        RenderServices.shapes().rounded(indicatorX, innerY + indicatorInset,
                indicatorX + segmentWidth - indicatorInset * 2.0f,
                innerY + innerHeight - indicatorInset,
                Math.max(4.0f, EpsilonPanelMetrics.CONTROL_RADIUS - 2.0f),
                PanelClickGuiPalette.selected());
        int active = PanelClickGuiPalette.textPrimary();
        int inactive = PanelClickGuiPalette.textMuted();
        EpsilonPanelFonts.drawCenteredText(left, innerX + segmentWidth * 0.5f,
                innerY, innerHeight, 0.52f, ClickGuiTheme.blend(active, inactive, selection));
        EpsilonPanelFonts.drawCenteredText(right, innerX + segmentWidth * 1.5f,
                innerY, innerHeight, 0.52f, ClickGuiTheme.blend(inactive, active, selection));
    }

    private void drawLanguageControl(PanelClickGuiLayout.Rect modulePanel, int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect card = PanelClickGuiLanguageControl.cardBounds(modulePanel);
        PanelClickGuiLayout.Rect language = PanelClickGuiLanguageControl.segmentBounds(modulePanel);
        boolean hovered = card.contains(mouseX, mouseY);
        float hover = epsilonAnimations.value("panel-language-row-hover", hovered ? 1.0f : 0.0f,
                frameTimeMillis, EpsilonPanelAnimation.SETTING_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        drawRect(card, EpsilonPanelMetrics.CARD_RADIUS,
                ClickGuiTheme.blend(PanelClickGuiPalette.raised(),
                        PanelClickGuiPalette.overlay(), hover));
        EpsilonPanelFonts.text(EpsilonPanelMetrics.SETTING_LABEL_SCALE).drawString(
                languageText("Language", "语言"), card.x() + 9.0f,
                EpsilonPanelFonts.centeredY(card.y(), card.height(),
                        EpsilonPanelMetrics.SETTING_LABEL_SCALE), PanelClickGuiPalette.textPrimary());
        drawSegmentedControl(language, "English", "中文",
                ClickGUI.getLanguage() == ClientLanguage.CHINESE, mouseX, mouseY);
    }

    private void drawSearch(PanelClickGuiLayout.Rect modulePanel, int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect search = EpsilonPanelSearchModel.bounds(modulePanel);
        boolean hovered = search.contains(mouseX, mouseY);
        long now = frameTimeMillis;
        float hover = epsilonAnimations.value("panel-search-hover", hovered ? 1.0f : 0.0f,
                now, EpsilonPanelAnimation.SEARCH_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float focus = epsilonAnimations.value("panel-search-focus", searchFocused ? 1.0f : 0.0f,
                now, EpsilonPanelAnimation.SEARCH_FOCUS_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float fieldHover = Math.max(hover, focus * 0.85f);
        int background = ClickGuiTheme.blend(PanelClickGuiPalette.surface(),
                PanelClickGuiPalette.overlay(), clamp(fieldHover * 0.85f, 0.0f, 1.0f));
        int indicator = ClickGuiTheme.blend(PanelClickGuiPalette.border(),
                PanelClickGuiPalette.accent(), focus);
        RenderServices.shapes().roundedBorderWH(search.x(), search.y(), search.width(), search.height(),
                EpsilonPanelMetrics.CONTROL_RADIUS, 1.0f, background, indicator);
        boolean showPlaceholder = searchText.isEmpty() && !searchFocused;
        String text = showPlaceholder ? languageText("Search", "搜索") : searchText;
        int color = showPlaceholder
                ? ClickGuiTheme.blend(PanelClickGuiPalette.textMuted(), PanelClickGuiPalette.textPrimary(), focus)
                : PanelClickGuiPalette.textPrimary();
        CFontRenderer font = EpsilonPanelFonts.text(EpsilonPanelMetrics.SEARCH_TEXT_SCALE);
        font.drawString(text, search.x() + 8.0f,
                EpsilonPanelFonts.centeredY(search.y(), search.height(),
                        EpsilonPanelMetrics.SEARCH_TEXT_SCALE), color);
        if (searchFocused) {
            int cursor = Math.max(0, Math.min(searchCursorIndex, searchText.length()));
            float caretX = search.x() + 8.0f + font.getStringWidth(searchText.substring(0, cursor));
            RenderServices.shapes().rect(caretX, search.y() + 4.0f, caretX + 1.0f,
                    search.bottom() - 4.0f, PanelClickGuiPalette.accent());
        }
    }

    private void drawScrollbar(PanelClickGuiLayout.Rect viewport, float scroll, float maxScroll,
                               float contentHeight, int mouseX, int mouseY, boolean detail) {
        PanelClickGuiScroll.Geometry geometry = PanelClickGuiScroll.geometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null) {
            return;
        }
        boolean hovered = geometry.trackContains(mouseX, mouseY)
                || (detail ? draggingDetailScrollbar : draggingScrollbar);
        long now = frameTimeMillis;
        String hoverKey = detail ? "panel-detail-scrollbar-hover" : "panel-module-scrollbar-hover";
        float hoverProgress = epsilonAnimations.value(hoverKey, hovered ? 1.0f : 0.0f,
                now, EpsilonPanelAnimation.SETTING_HOVER_MS,
                gq.yozakura.util.animation.AnimationUtil.Ease.OUT_CUBIC);
        float visualWidth = PanelClickGuiScroll.visualWidth(geometry, hoverProgress);
        float visualX = PanelClickGuiScroll.visualX(geometry, hoverProgress);
        RenderServices.shapes().rounded(visualX, geometry.y(), visualX + visualWidth,
                geometry.y() + geometry.height(), visualWidth * 0.5f,
                ClickGuiTheme.blend(PanelClickGuiPalette.textMuted(), 0xFFB8A9D6, hoverProgress));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (closingGui) {
            return;
        }
        rebuildLayout(frameTimeMillis);
        if (listeningKeybind && selectedModule != null && !clientSettingsMode) {
            EpsilonPanelGeometry.DetailHeader headerGeometry =
                    EpsilonPanelGeometry.detailHeader(layout.detail());
            if (headerGeometry.keybind().contains(mouseX, mouseY)) {
                selectedModule.setKey(PanelModuleKeybind.encodeMouseButton(mouseButton));
                listeningKeybind = false;
                return;
            }
        }
        if (mouseButton != 0) {
            return;
        }
        if (handleOpenPaletteColorPickerClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleOpenDropdownClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleFocusedNumberClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (!layout.panel().contains(mouseX, mouseY)) {
            return;
        }
        if (!moduleDetailOpen && !clientSettingsMode && !configManagerMode
                && EpsilonPanelSearchModel.bounds(layout.modules()).contains(mouseX, mouseY)) {
            searchFocused = mouseButton == 0;
            searchCursorIndex = searchText.length();
            return;
        }
        searchFocused = false;
        if (clientSettingsMode) {
            PanelClickGuiLayout.Rect language =
                    PanelClickGuiLanguageControl.segmentBounds(layout.detail());
            if (language.contains(mouseX, mouseY)) {
                ClickGUI.setLanguage(PanelClickGuiLanguageControl.languageAt(language, mouseX));
                return;
            }
        }
        if (handleResizeClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (moduleDetailOpen || clientSettingsMode) {
            if (handleScrollbarClick(mouseX, mouseY, true)) {
                return;
            }
        } else if (!configManagerMode && handleScrollbarClick(mouseX, mouseY, false)) {
            return;
        }
        if (handleConfigManagerClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleDetailClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleRailClick(mouseX, mouseY)) {
            return;
        }
        if (handlePanelDragClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleModuleClick(mouseX, mouseY, mouseButton)) {
            return;
        }
    }

    private boolean handleOpenPaletteColorPickerClick(int mouseX, int mouseY, int mouseButton) {
        if (!paletteColorPicker.isOpen()) {
            return false;
        }
        PanelClickGuiLayout.Rect anchor = paletteColorAnchor(paletteColorPicker.group());
        if (anchor == null) {
            paletteColorPicker.close();
            return true;
        }
        PanelClickGuiLayout.Rect popup = paletteColorPicker.bounds(anchor, layout.detail());
        paletteColorPicker.mouseClicked(popup, mouseX, mouseY, mouseButton);
        return true;
    }

    private boolean handleOpenDropdownClick(int mouseX, int mouseY, int mouseButton) {
        String openKey = valueInteraction.openDropdownKey;
        Module owner = detailOwner();
        if (!values.isDropdownInteractive() || openKey == null) {
            return values.isDropdownOpen();
        }
        if (owner == null) {
            values.requestDropdownClose();
            return true;
        }
        PanelClickGuiLayout.Rect viewport = detailViewport();
        float y = viewport.y() - detailScroll;
        float rowW = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
        for (Value value : visibleValues(owner)) {
            String key = "panel:" + owner.getName() + ":" + value.getName();
            if (key.equals(openKey)) {
                values.mouseClicked(value, key, viewport.x() + 5.0f, y + 1.0f,
                        rowW - 10.0f, mouseX, mouseY, mouseButton);
                return true;
            }
            y += values.rowHeight();
        }
        values.requestDropdownClose();
        return true;
    }

    private boolean handleFocusedNumberClick(int mouseX, int mouseY, int mouseButton) {
        if (!values.isEditingNumber()) {
            return false;
        }
        String focusedKey = values.focusedNumberKey();
        Module owner = detailOwner();
        if (focusedKey != null && owner != null) {
            PanelClickGuiLayout.Rect viewport = detailViewport();
            float y = viewport.y() - detailScroll;
            float rowW = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
            for (Value value : visibleValues(owner)) {
                String key = "panel:" + owner.getName() + ":" + value.getName();
                if (key.equals(focusedKey)) {
                    boolean consumed = values.mouseClicked(value, key,
                            viewport.x() + 5.0f, y + 1.0f, rowW - 10.0f,
                            mouseX, mouseY, mouseButton);
                    if (consumed) {
                        return true;
                    }
                    break;
                }
                y += values.rowHeight();
            }
        }
        values.cancelNumberEditing();
        return false;
    }

    private boolean handleConfigManagerClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        if (!configManagerMode) {
            return false;
        }
        PanelClickGuiLayout.Rect bounds = layout.detail();
        if (EpsilonPanelGeometry.detailCloseButton(bounds).contains(mouseX, mouseY)) {
            closeConfigManager();
            return true;
        }
        if (PanelClubGeometry.localTab(bounds).contains(mouseX, mouseY)) {
            cloudConfigMode = false;
            return true;
        }
        if (PanelClubGeometry.cloudTab(bounds).contains(mouseX, mouseY)) {
            cloudConfigMode = true;
            configNameFocused = false;
            clubService.refreshHallConfigs();
            return true;
        }
        if (cloudConfigMode) {
            return handleCloudConfigClick(bounds, mouseX, mouseY);
        }
        PanelClickGuiLayout.Rect list = configProfileListBounds(bounds);
        if (list.contains(mouseX, mouseY)) {
            int visibleRows = Math.max(1, (int) (list.height() / CONFIG_ROW_H));
            int row = (int) ((mouseY - list.y()) / CONFIG_ROW_H);
            int index = configProfileScroll + row;
            if (row >= 0 && row < visibleRows && index < configProfiles.size()) {
                selectedConfigProfile = index;
                configName = configProfiles.get(index);
                configNameCursor = configName.length();
            }
            return true;
        }
        if (configNameFieldBounds(bounds).contains(mouseX, mouseY)) {
            configNameFocused = true;
            configNameCursor = configName.length();
            return true;
        }
        configNameFocused = false;
        if (configSaveButton(bounds).contains(mouseX, mouseY)) {
            saveConfigProfile();
            return true;
        }
        if (configLoadButton(bounds).contains(mouseX, mouseY)) {
            loadConfigProfile();
            return true;
        }
        if (configRefreshButton(bounds).contains(mouseX, mouseY)) {
            refreshConfigProfiles(selectedConfigName());
            return true;
        }
        if (configFolderButton(bounds).contains(mouseX, mouseY)) {
            runConfigAction(languageText("Opened config folder", "已打开配置目录"), new ConfigAction() {
                @Override
                public void run() throws IOException {
                    ConfigBridge.openProfileDirectory();
                }
            });
            return true;
        }
        return bounds.contains(mouseX, mouseY);
    }

    private boolean handleCloudConfigClick(PanelClickGuiLayout.Rect bounds, int mouseX, int mouseY) {
        ClubService.ClubViewState state = clubService.getState();
        List<ClubConfigSummary> configs = visibleCloudConfigs(state);
        PanelClickGuiLayout.Rect search = PanelClubGeometry.searchField(bounds);
        if (search.contains(mouseX, mouseY)) {
            cloudConfigSearchFocused = true;
            cloudConfigSearchCursor = cloudConfigSearchText.length();
            return true;
        }
        cloudConfigSearchFocused = false;
        PanelClickGuiLayout.Rect list = PanelClubGeometry.cloudList(bounds);
        if (list.contains(mouseX, mouseY)) {
            int row = (int) ((mouseY - list.y()) / CONFIG_ROW_H);
            int index = cloudConfigScroll + row;
            if (row >= 0 && index < configs.size()) {
                ClubConfigSummary config = configs.get(index);
                selectedCloudConfigId = config.getId();
                configName = config.getName();
                configNameCursor = configName.length();
            }
            return true;
        }
        if (state.isBusy()) {
            return bounds.contains(mouseX, mouseY);
        }
        if (PanelClubGeometry.uploadButton(bounds).contains(mouseX, mouseY)) {
            uploadSelectedLocalConfig();
            return true;
        }
        if (PanelClubGeometry.downloadButton(bounds).contains(mouseX, mouseY)) {
            ClubConfigSummary selected = selectedCloudConfig();
            if (selected != null) {
                clubService.downloadHallConfig(selected.getId());
            }
            return true;
        }
        if (PanelClubGeometry.useButton(bounds).contains(mouseX, mouseY)) {
            ClubConfigSummary selected = selectedCloudConfig();
            if (selected != null) {
                clubService.useHallConfig(selected.getId());
            }
            return true;
        }
        if (PanelClubGeometry.deleteButton(bounds).contains(mouseX, mouseY)) {
            ClubConfigSummary selected = selectedCloudConfig();
            if (selected != null && ownsSelectedCloudConfig(state)) {
                clubService.deleteHallConfig(selected);
            }
            return true;
        }
        if (PanelClubGeometry.refreshButton(bounds).contains(mouseX, mouseY)) {
            clubService.refreshHallConfigs();
            return true;
        }
        return bounds.contains(mouseX, mouseY);
    }

    private void uploadSelectedLocalConfig() {
        final String selected = selectedConfigName();
        if (selected == null) {
            clubService.reportResult(languageText("Select a Local profile first",
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
            clubService.reportResult(languageText("Unable to read Local profile",
                    "读取本地配置失败"), true);
            FileManager.logConfigFailure("Panel hall upload profile read failed", exception);
        }
    }

    private List<ClubConfigSummary> visibleCloudConfigs(ClubService.ClubViewState state) {
        return PanelCloudConfigSearchModel.filter(state.getConfigs(), cloudConfigSearchText);
    }

    private ClubConfigSummary selectedCloudConfig() {
        return PanelCloudConfigSearchModel.findById(
                clubService.getState().getConfigs(), selectedCloudConfigId);
    }

    private boolean ownsSelectedCloudConfig(ClubService.ClubViewState state) {
        ClubConfigSummary selected = selectedCloudConfig();
        return selected != null && state.ownsConfig(selected.getId());
    }

    private void openConfigManager() {
        configManagerMode = true;
        moduleDetailOpen = false;
        clientSettingsMode = false;
        selectedModule = null;
        searchFocused = false;
        configNameFocused = false;
        refreshConfigProfiles(null);
    }

    private void closeConfigManager() {
        configManagerMode = false;
        configNameFocused = false;
        configStatus = "";
    }

    private void saveConfigProfile() {
        final String name = configName;
        runConfigAction(languageText("Saved ", "已保存 ") + displayConfigName(name), new ConfigAction() {
            @Override
            public void run() throws IOException {
                ConfigBridge.saveProfile(name);
            }
        });
        if (configStatusColor == PanelClickGuiPalette.accent()) {
            refreshConfigProfiles(stripConfigExtension(name));
        }
    }

    private void loadConfigProfile() {
        final String selected = selectedConfigName();
        if (selected == null) {
            setConfigError(languageText("Select a profile first", "请先选择配置"));
            return;
        }
        runConfigAction(languageText("Loaded ", "已加载 ") + selected + ".yzk", new ConfigAction() {
            @Override
            public void run() throws IOException {
                ConfigBridge.loadProfile(selected);
                panelPositionInitialized = false;
                rebuildLayout(frameTimeMillis);
            }
        });
    }

    private void refreshConfigProfiles(String preferredSelection) {
        try {
            configProfiles.clear();
            configProfiles.addAll(ConfigBridge.listProfiles());
            selectedConfigProfile = indexOfConfigProfile(preferredSelection);
            if (selectedConfigProfile < 0 && !configProfiles.isEmpty()) {
                selectedConfigProfile = 0;
            }
            if (selectedConfigProfile >= 0) {
                configName = configProfiles.get(selectedConfigProfile);
                configNameCursor = configName.length();
            }
            int visibleRows = Math.max(1,
                    (int) (configProfileListBounds(layout.detail()).height() / CONFIG_ROW_H));
            configProfileScroll = Math.max(0, Math.min(configProfileScroll,
                    Math.max(0, configProfiles.size() - visibleRows)));
            configStatus = configProfiles.size() + languageText(" profiles", " 个配置");
            configStatusColor = PanelClickGuiPalette.textMuted();
        } catch (IOException exception) {
            configProfiles.clear();
            selectedConfigProfile = -1;
            setConfigError(configErrorMessage(exception));
            FileManager.logConfigFailure("Panel config profile refresh failed", exception);
        }
    }

    private void runConfigAction(String success, ConfigAction action) {
        try {
            action.run();
            configStatus = success;
            configStatusColor = PanelClickGuiPalette.accent();
        } catch (Exception exception) {
            setConfigError(configErrorMessage(exception));
            FileManager.logConfigFailure("Panel config profile action failed", exception);
        }
    }

    private void setConfigError(String message) {
        configStatus = message;
        configStatusColor = ClickGUI.currentPalette().getDanger();
    }

    private boolean handleRailClick(int mouseX, int mouseY) {
        PanelClickGuiLayout.Rect rail = layout.rail();
        if (EpsilonPanelGeometry.railMenuButton(rail).contains(mouseX, mouseY)) {
            sidebarExpanded = !sidebarExpanded;
            rebuildLayout(frameTimeMillis);
            return true;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            ModuleType category = CATEGORIES[i];
            if (EpsilonPanelGeometry.railCategoryItem(rail, i).contains(mouseX, mouseY)) {
                selectedCategory = category;
                clientSettingsMode = false;
                configManagerMode = false;
                moduleDetailOpen = false;
                selectedModule = null;
                moduleScroll = 0.0f;
                moduleScrollVelocity = 0.0f;
                detailScroll = 0.0f;
                detailScrollVelocity = 0.0f;
                return true;
            }
        }
        if (EpsilonPanelGeometry.railConfigManagerItem(rail).contains(mouseX, mouseY)) {
            openConfigManager();
            detailScroll = 0.0f;
            detailScrollVelocity = 0.0f;
            return true;
        }
        if (EpsilonPanelGeometry.railSettingsItem(rail).contains(mouseX, mouseY)) {
            clientSettingsMode = true;
            configManagerMode = false;
            moduleDetailOpen = false;
            selectedModule = null;
            detailScroll = 0.0f;
            detailScrollVelocity = 0.0f;
            return true;
        }
        return false;
    }

    private boolean handleModuleClick(int mouseX, int mouseY, int mouseButton) {
        if (clientSettingsMode || moduleDetailOpen || configManagerMode) {
            return false;
        }
        PanelClickGuiLayout.Rect bounds = layout.modules();
        PanelClickGuiLayout.Rect viewport = moduleViewport();
        if (!viewport.contains(mouseX, mouseY)) {
            return false;
        }
        float y = viewport.y() - moduleScroll;
        float rowWidth = moduleMaxScroll > 0.0f
                ? viewport.width() - PanelClickGuiScroll.TOTAL_WIDTH : viewport.width();
        for (Module module : modulesInCategory()) {
            if (mouseX >= viewport.x() && mouseX <= viewport.x() + rowWidth
                    && mouseY >= y && mouseY <= y + MODULE_ROW_H) {
                PanelClickGuiLayout.Rect rowBounds = new PanelClickGuiLayout.Rect(
                        viewport.x(), y, rowWidth, MODULE_ROW_H);
                PanelClickGuiLayout.Rect toggleBounds = EpsilonPanelGeometry.moduleSwitch(rowBounds);
                PanelClickGuiLayout.Rect settingsBounds = EpsilonPanelGeometry.moduleSettingsButton(rowBounds);
                if (mouseButton == 0 && toggleBounds.contains(mouseX, mouseY)) {
                    module.toggle();
                } else if (mouseButton == 0 && settingsBounds.contains(mouseX, mouseY)) {
                    openModuleDetail(module);
                } else if (mouseButton == 0) {
                    openModuleDetail(module);
                }
                return true;
            }
            y += MODULE_ROW_H + ROW_GAP;
        }
        return true;
    }

    private void openModuleDetail(Module module) {
        selectedModule = module;
        moduleDetailOpen = true;
        clientSettingsMode = false;
        configManagerMode = false;
        searchFocused = false;
        detailScroll = 0.0f;
        detailScrollVelocity = 0.0f;
    }

    private boolean handleDetailClick(int mouseX, int mouseY, int mouseButton) {
        Module owner = detailOwner();
        if (owner == null) {
            return false;
        }
        PanelClickGuiLayout.Rect bounds = layout.detail();
        PanelClickGuiLayout.Rect close = EpsilonPanelGeometry.detailCloseButton(bounds);
        if (mouseButton == 0 && close.contains(mouseX, mouseY)) {
            values.requestDropdownClose();
            paletteColorPicker.close();
            listeningKeybind = false;
            clientSettingsMode = false;
            moduleDetailOpen = false;
            selectedModule = null;
            detailScroll = 0.0f;
            detailScrollVelocity = 0.0f;
            return true;
        }
        EpsilonPanelGeometry.DetailHeader headerGeometry = EpsilonPanelGeometry.detailHeader(bounds);
        if (!clientSettingsMode && mouseButton == 0 && headerGeometry.keybind().contains(mouseX, mouseY)) {
            listeningKeybind = true;
            return true;
        }
        if (!clientSettingsMode && mouseButton == 0) {
            PanelClickGuiLayout.Rect bindMode = headerGeometry.bindMode();
            if (bindMode.contains(mouseX, mouseY)) {
                owner.setBindMode(mouseX < bindMode.x() + bindMode.width() * 0.5f
                        ? Module.BindMode.TOGGLE : Module.BindMode.HOLD);
                return true;
            }
            PanelClickGuiLayout.Rect hidden = headerGeometry.hidden();
            if (hidden.contains(mouseX, mouseY)) {
                owner.setHidden(mouseX >= hidden.x() + hidden.width() * 0.5f);
                return true;
            }
        }
        PanelClickGuiLayout.Rect viewport = detailViewport();
        if (!viewport.contains(mouseX, mouseY)) {
            return false;
        }
        float y = viewport.y() - detailScroll;
        float rowW = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
        for (Value value : visibleValues(owner)) {
            if (mouseY >= y && mouseY <= y + VALUE_ROW_H) {
                PanelClickGuiLayout.Rect row = new PanelClickGuiLayout.Rect(
                        viewport.x() + 5.0f, y + 1.0f, rowW - 10.0f, VALUE_ROW_H);
                PanelPaletteColorControl.Group colorGroup =
                        PanelPaletteColorControl.groupFor(value);
                if (colorGroup != null) {
                    PanelClickGuiLayout.Rect swatch =
                            PanelPaletteColorControl.swatchBounds(row);
                    if (swatch.contains(mouseX, mouseY)) {
                        values.requestDropdownClose();
                        paletteColorPicker.open(colorGroup);
                        return true;
                    }
                    return true;
                }
                return values.mouseClicked(value, "panel:" + owner.getName() + ":" + value.getName(),
                        row.x(), row.y(), row.width(), mouseX, mouseY, mouseButton);
            }
            y += values.rowHeight();
        }
        return true;
    }

    private boolean handlePanelDragClick(int mouseX, int mouseY, int mouseButton) {
        PanelClickGuiLayout.Rect handle = PanelClickGuiLayout.dragHandle(layout);
        if (mouseButton != 0 || !handle.contains(mouseX, mouseY)) {
            return false;
        }
        draggingPanel = true;
        dragOffsetX = preciseMouseX(mouseX) - layout.panel().x();
        dragOffsetY = preciseMouseY(mouseY) - layout.panel().y();
        moduleScrollVelocity = 0.0f;
        detailScrollVelocity = 0.0f;
        return true;
    }

    private void updatePanelPosition(float mouseX, float mouseY) {
        float panelWidth = layout.panel().width();
        float panelHeight = layout.panel().height();
        float maxX = Math.max(PanelClickGuiLayout.VIEWPORT_MARGIN,
                width - PanelClickGuiLayout.VIEWPORT_MARGIN - panelWidth);
        float maxY = Math.max(PanelClickGuiLayout.VIEWPORT_MARGIN,
                height - PanelClickGuiLayout.VIEWPORT_MARGIN - panelHeight);
        float x = clamp(preciseMouseX(mouseX) - dragOffsetX,
                PanelClickGuiLayout.VIEWPORT_MARGIN, maxX);
        float y = clamp(preciseMouseY(mouseY) - dragOffsetY,
                PanelClickGuiLayout.VIEWPORT_MARGIN, maxY);
        float offsetX = x - layout.panel().x();
        float offsetY = y - layout.panel().y();
        panelPositionX = x;
        panelPositionY = y;
        layout = PanelClickGuiLayout.translated(layout, offsetX, offsetY);
        valueInteraction.popupBounds = layout.detail();
    }

    private static float alignHalfPixel(float value) {
        return (float) Math.floor(value) + 0.5f;
    }

    private float preciseMouseX(float fallback) {
        if (mc == null || mc.displayWidth <= 0) {
            return fallback;
        }
        return Mouse.getX() * (float) width / (float) mc.displayWidth;
    }

    private float preciseMouseY(float fallback) {
        if (mc == null || mc.displayHeight <= 0) {
            return fallback;
        }
        return height - Mouse.getY() * (float) height / (float) mc.displayHeight - 1.0f;
    }

    private void persistPanelPosition() {
        ClickGUI.panelX.setNumberValue(panelPositionX);
        ClickGUI.panelY.setNumberValue(panelPositionY);
    }

    private boolean handleResizeClick(int mouseX, int mouseY, int mouseButton) {
        PanelClickGuiLayout.Rect handle = PanelClickGuiLayout.resizeHandle(layout.panel());
        if (mouseButton != 0 || !handle.contains(mouseX, mouseY)) {
            return false;
        }
        resizingPanel = true;
        resizeStartMouseX = preciseMouseX(mouseX);
        resizeStartMouseY = preciseMouseY(mouseY);
        resizeStartWidth = layout.panel().width();
        resizeStartHeight = layout.panel().height();
        panelPositionX = layout.panel().x();
        panelPositionY = layout.panel().y();
        moduleScrollVelocity = 0.0f;
        detailScrollVelocity = 0.0f;
        return true;
    }

    private void updatePanelSize(float mouseX, float mouseY) {
        float requestedWidth = resizeStartWidth
                + (preciseMouseX(mouseX) - resizeStartMouseX);
        float requestedHeight = resizeStartHeight
                + (preciseMouseY(mouseY) - resizeStartMouseY);
        float maxWidth = Math.max(1.0f,
                width - PanelClickGuiLayout.VIEWPORT_MARGIN - layout.panel().x());
        float maxHeight = Math.max(1.0f,
                height - PanelClickGuiLayout.VIEWPORT_MARGIN - layout.panel().y());
        float safeWidth = clamp(requestedWidth,
                Math.min(PanelClickGuiLayout.PANEL_MIN_WIDTH, maxWidth), maxWidth);
        float safeHeight = clamp(requestedHeight,
                Math.min(PanelClickGuiLayout.PANEL_MIN_HEIGHT, maxHeight), maxHeight);
        layout = PanelClickGuiLayout.resized(layout, safeWidth, safeHeight);
        valueInteraction.popupBounds = layout.detail();
    }

    private void persistPanelSize() {
        ClickGUI.panelWidth.setNumberValue(layout.panel().width());
        ClickGUI.panelHeight.setNumberValue(layout.panel().height());
        persistPanelPosition();
    }

    private boolean handleScrollbarClick(int mouseX, int mouseY, boolean detail) {
        PanelClickGuiLayout.Rect viewport = detail ? detailViewport() : moduleViewport();
        float scroll = detail ? detailScroll : moduleScroll;
        float max = detail ? detailMaxScroll : moduleMaxScroll;
        float content = detail ? detailContentHeight() : moduleContentHeight();
        if (max <= 0.0f) {
            return false;
        }
        PanelClickGuiScroll.Geometry geometry = PanelClickGuiScroll.geometry(viewport, scroll, max, content);
        if (geometry == null) {
            return false;
        }
        if (geometry.trackContains(mouseX, mouseY)) {
            if (detail) {
                draggingDetailScrollbar = true;
                detailScrollVelocity = 0.0f;
            } else {
                draggingScrollbar = true;
                moduleScrollVelocity = 0.0f;
            }
            scrollbarGrabOffset = geometry.thumbContains(mouseX, mouseY)
                    ? mouseY - geometry.y() : geometry.height() * 0.5f;
            updateScrollFromMouse(mouseY, detail);
            return true;
        }
        return false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingPanel) {
            updatePanelPosition(mouseX, mouseY);
            return;
        }
        if (resizingPanel) {
            updatePanelSize(mouseX, mouseY);
            return;
        }
        if (updatePaletteColorPickerDrag(mouseX, mouseY)) {
            return;
        }
        if (draggingScrollbar || draggingDetailScrollbar) {
            updateScrollFromMouse(mouseY, draggingDetailScrollbar);
            return;
        }
        if (values.isDraggingSlider() && detailOwner() != null) {
            updateDraggedValue(mouseX, mouseY);
        }
    }

    private boolean updatePaletteColorPickerDrag(float mouseX, float mouseY) {
        if (!paletteColorPicker.isOpen() || !paletteColorPicker.isDragging()) {
            return false;
        }
        PanelClickGuiLayout.Rect anchor = paletteColorAnchor(paletteColorPicker.group());
        if (anchor == null) {
            paletteColorPicker.close();
            return true;
        }
        PanelClickGuiLayout.Rect popup = paletteColorPicker.bounds(anchor, layout.detail());
        return paletteColorPicker.mouseDragged(popup,
                preciseMouseX(mouseX), preciseMouseY(mouseY));
    }

    private void updateScrollFromMouse(float mouseY, boolean detail) {
        PanelClickGuiLayout.Rect viewport = detail ? detailViewport() : moduleViewport();
        float max = detail ? detailMaxScroll : moduleMaxScroll;
        float content = detail ? detailContentHeight() : moduleContentHeight();
        PanelClickGuiScroll.Geometry geometry = PanelClickGuiScroll.geometry(viewport, 0.0f, max, content);
        if (geometry == null) {
            return;
        }
        float value = PanelClickGuiScroll.scrollFromThumbTop(mouseY - scrollbarGrabOffset,
                viewport, max, content);
        if (detail) {
            detailScroll = value;
            detailScrollVelocity = 0.0f;
        } else {
            moduleScroll = value;
            moduleScrollVelocity = 0.0f;
        }
    }

    private void updateDraggedValue(int mouseX, int mouseY) {
        String dragged = values.getDraggedSliderKey();
        if (dragged == null) {
            return;
        }
        PanelClickGuiLayout.Rect viewport = detailViewport();
        float y = viewport.y() - detailScroll;
        float rowW = viewport.width() - SCROLLBAR_W - SCROLLBAR_GAP;
        Module owner = detailOwner();
        if (owner == null) {
            return;
        }
        for (Value value : visibleValues(owner)) {
            String key = "panel:" + owner.getName() + ":" + value.getName();
            if (key.equals(dragged)) {
                values.updateDraggedSlider((gq.yozakura.value.Numbers<?>) value, key,
                        viewport.x() + 5.0f, y + 1.0f, rowW - 10.0f, mouseX);
                return;
            }
            y += values.rowHeight();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingPanel) {
            persistPanelPosition();
        }
        if (resizingPanel) {
            persistPanelSize();
        }
        draggingPanel = false;
        resizingPanel = false;
        paletteColorPicker.mouseReleased();
        draggingScrollbar = false;
        draggingDetailScrollbar = false;
        values.mouseReleased();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || layout == null) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        float scrollY = wheel / 120.0f;
        if (values.isDropdownOpen()) {
            // Popup-first: a closing popup swallows input; only an open popup scrolls.
            if (values.isDropdownInteractive()) {
                scrollOpenDropdown(scrollY);
            }
            return;
        }
        if (configManagerMode) {
            if (cloudConfigMode) {
                PanelClickGuiLayout.Rect list = PanelClubGeometry.cloudList(layout.detail());
                List<ClubConfigSummary> configs = visibleCloudConfigs(clubService.getState());
                if (list.contains(mx, my) && !configs.isEmpty()) {
                    int visibleRows = Math.max(1, (int) (list.height() / CONFIG_ROW_H));
                    int maxScroll = Math.max(0, configs.size() - visibleRows);
                    cloudConfigScroll += wheel < 0 ? 1 : -1;
                    cloudConfigScroll = Math.max(0, Math.min(cloudConfigScroll, maxScroll));
                }
            } else {
                PanelClickGuiLayout.Rect list = configProfileListBounds(layout.detail());
                if (list.contains(mx, my) && !configProfiles.isEmpty()) {
                    int visibleRows = Math.max(1, (int) (list.height() / CONFIG_ROW_H));
                    int maxScroll = Math.max(0, configProfiles.size() - visibleRows);
                    configProfileScroll += wheel < 0 ? 1 : -1;
                    configProfileScroll = Math.max(0, Math.min(configProfileScroll, maxScroll));
                }
            }
        } else if (moduleDetailOpen || clientSettingsMode) {
            if (detailViewport().contains(mx, my)) {
                detailScrollVelocity = PanelClickGuiMotion.addWheelImpulse(
                        detailScrollVelocity, scrollY);
            }
        } else if (moduleViewport().contains(mx, my)) {
            moduleScrollVelocity = PanelClickGuiMotion.addWheelImpulse(
                    moduleScrollVelocity, scrollY);
        }
    }

    private void handleCloudConfigSearchKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cloudConfigSearchFocused = false;
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (cloudConfigSearchCursor > 0 && !cloudConfigSearchText.isEmpty()) {
                cloudConfigSearchText = cloudConfigSearchText.substring(0,
                        cloudConfigSearchCursor - 1)
                        + cloudConfigSearchText.substring(cloudConfigSearchCursor);
                cloudConfigSearchCursor--;
            }
        } else if (keyCode == Keyboard.KEY_DELETE) {
            if (cloudConfigSearchCursor < cloudConfigSearchText.length()) {
                cloudConfigSearchText = cloudConfigSearchText.substring(0, cloudConfigSearchCursor)
                        + cloudConfigSearchText.substring(cloudConfigSearchCursor + 1);
            }
        } else if (keyCode == Keyboard.KEY_LEFT) {
            cloudConfigSearchCursor = Math.max(0, cloudConfigSearchCursor - 1);
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            cloudConfigSearchCursor = Math.min(cloudConfigSearchText.length(),
                    cloudConfigSearchCursor + 1);
        } else if (keyCode != Keyboard.KEY_RETURN && keyCode != Keyboard.KEY_NUMPADENTER
                && !Character.isISOControl(typedChar) && cloudConfigSearchText.length() < 64) {
            cloudConfigSearchText = cloudConfigSearchText.substring(0, cloudConfigSearchCursor)
                    + typedChar + cloudConfigSearchText.substring(cloudConfigSearchCursor);
            cloudConfigSearchCursor++;
        }
        cloudConfigScroll = 0;
        selectedCloudConfigId = null;
    }

    private boolean keyTypedFocusedNumber(char typedChar, int keyCode) {
        String focusedKey = values.focusedNumberKey();
        Module owner = detailOwner();
        if (focusedKey == null || owner == null) {
            return false;
        }
        for (Value value : visibleValues(owner)) {
            String key = "panel:" + owner.getName() + ":" + value.getName();
            if (key.equals(focusedKey) && value instanceof Numbers
                    && !(value instanceof ModeProperty)) {
                return values.keyTypedNumber((Numbers<?>) value, key, typedChar, keyCode);
            }
        }
        return false;
    }

    /** Finds the open enum value and applies Epsilon popup wheel scrolling. */
    private void scrollOpenDropdown(float scrollY) {
        String openKey = valueInteraction.openDropdownKey;
        Module owner = detailOwner();
        if (openKey == null || owner == null) {
            return;
        }
        for (Value value : visibleValues(owner)) {
            String key = "panel:" + owner.getName() + ":" + value.getName();
            if (!key.equals(openKey)) {
                continue;
            }
            if (value instanceof Mode) {
                values.mouseScrolledOpenDropdown((Mode<?>) value, key, scrollY);
                return;
            }
            if (value instanceof ModeProperty) {
                values.mouseScrolledOpenDropdown((ModeProperty) value, key, scrollY);
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (closingGui) {
            return;
        }
        if (configManagerMode) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeConfigManager();
                return;
            }
            if (cloudConfigMode) {
                if (cloudConfigSearchFocused) {
                    handleCloudConfigSearchKey(typedChar, keyCode);
                }
                return;
            }
            if (configNameFocused) {
                if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    saveConfigProfile();
                } else if (keyCode == Keyboard.KEY_BACK) {
                    if (configNameCursor > 0 && !configName.isEmpty()) {
                        configName = configName.substring(0, configNameCursor - 1)
                                + configName.substring(configNameCursor);
                        configNameCursor--;
                    }
                } else if (keyCode == Keyboard.KEY_DELETE) {
                    if (configNameCursor < configName.length()) {
                        configName = configName.substring(0, configNameCursor)
                                + configName.substring(configNameCursor + 1);
                    }
                } else if (keyCode == Keyboard.KEY_LEFT) {
                    configNameCursor = Math.max(0, configNameCursor - 1);
                } else if (keyCode == Keyboard.KEY_RIGHT) {
                    configNameCursor = Math.min(configName.length(), configNameCursor + 1);
                } else if (!Character.isISOControl(typedChar) && configName.length() < 64
                        && "<>:\"/\\|?*".indexOf(typedChar) < 0) {
                    configName = configName.substring(0, configNameCursor) + typedChar
                            + configName.substring(configNameCursor);
                    configNameCursor++;
                }
            }
            return;
        }
        if (paletteColorPicker.isOpen()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                paletteColorPicker.close();
            }
            return;
        }
        if (values.isEditingNumber()) {
            if (keyTypedFocusedNumber(typedChar, keyCode)) {
                return;
            }
            values.cancelNumberEditing();
        }
        if (values.isDropdownOpen()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                values.requestDropdownClose();
            }
            return;
        }
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchFocused = false;
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                return;
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (searchCursorIndex > 0 && !searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchCursorIndex - 1)
                            + searchText.substring(searchCursorIndex);
                    searchCursorIndex--;
                }
            } else if (keyCode == Keyboard.KEY_DELETE) {
                if (searchCursorIndex < searchText.length()) {
                    searchText = searchText.substring(0, searchCursorIndex)
                            + searchText.substring(searchCursorIndex + 1);
                }
            } else if (keyCode == Keyboard.KEY_LEFT) {
                searchCursorIndex = Math.max(0, searchCursorIndex - 1);
            } else if (keyCode == Keyboard.KEY_RIGHT) {
                searchCursorIndex = Math.min(searchText.length(), searchCursorIndex + 1);
            } else if (!Character.isISOControl(typedChar) && searchText.length() < 32) {
                searchText = searchText.substring(0, searchCursorIndex) + typedChar
                        + searchText.substring(searchCursorIndex);
                searchCursorIndex++;
            }
            moduleScroll = 0.0f;
            moduleScrollVelocity = 0.0f;
            return;
        }
        if (listeningKeybind && selectedModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                listeningKeybind = false;
            } else if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                selectedModule.setKey(Keyboard.KEY_NONE);
                listeningKeybind = false;
            } else {
                selectedModule.setKey(keyCode);
                listeningKeybind = false;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (moduleDetailOpen || clientSettingsMode) {
                values.requestDropdownClose();
                paletteColorPicker.close();
                listeningKeybind = false;
                moduleDetailOpen = false;
                clientSettingsMode = false;
                selectedModule = null;
                detailScroll = 0.0f;
                detailScrollVelocity = 0.0f;
                return;
            }
            requestClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void requestClose() {
        if (closingGui) {
            return;
        }
        closingGui = true;
        draggingPanel = false;
        resizingPanel = false;
        draggingScrollbar = false;
        draggingDetailScrollbar = false;
        paletteColorPicker.mouseReleased();
        values.mouseReleased();
        values.requestDropdownClose();
    }

    private void completeCloseIfReady() {
        if (closingGui && openCloseAnimation.isClosed() && mc.currentScreen == this) {
            if (backgroundScreen != null) {
                mc.displayGuiScreen(backgroundScreen);
            } else {
                mc.displayGuiScreen(null);
            }
        }
    }

    private PanelClickGuiLayout.Rect moduleViewport() {
        PanelClickGuiLayout.Rect b = layout.modules();
        float top = b.y() + 46.0f;
        return new PanelClickGuiLayout.Rect(b.x() + 12.0f, top,
                b.width() - 24.0f, Math.max(1.0f, b.bottom() - 12.0f - top));
    }

    private PanelClickGuiLayout.Rect detailViewport() {
        PanelClickGuiLayout.Rect b = layout.detail();
        float top = clientSettingsMode
                ? PanelClickGuiLanguageControl.settingsContentTop(b)
                : b.y() + DETAIL_HEADER_H;
        return new PanelClickGuiLayout.Rect(b.x() + 3.0f, top,
                b.width() - 6.0f, Math.max(1.0f, b.bottom() - 3.0f - top));
    }

    private float moduleViewportHeight() { return moduleViewport().height(); }
    private float detailViewportHeight() { return detailViewport().height(); }
    private float moduleContentHeight() { return modulesInCategory().size() * (MODULE_ROW_H + ROW_GAP); }
    private float detailContentHeight() {
        Module owner = detailOwner();
        return owner == null ? 0.0f : visibleValues(owner).size() * values.rowHeight();
    }

    private Module detailOwner() {
        if (clientSettingsMode) {
            return ModuleManager.getModule("ClickGUI");
        }
        return moduleDetailOpen ? selectedModule : null;
    }

    private List<Module> modulesInCategory() {
        java.util.ArrayList<Module> modules = new java.util.ArrayList<Module>();
        for (Module module : ModuleManager.getModules()) {
            if (module != null
                    && !isConfigManagerModule(module)
                    && EpsilonPanelCategories.belongsTo(module.getCategory(), selectedCategory)
                    && EpsilonPanelSearchModel.matches(module.getName(), module.getChinese(), searchText)) {
                modules.add(module);
            }
        }
        java.util.Collections.sort(modules, new java.util.Comparator<Module>() {
            @Override
            public int compare(Module left, Module right) {
                String leftName = panelModuleName(left);
                String rightName = panelModuleName(right);
                return leftName.compareToIgnoreCase(rightName);
            }
        });
        return modules;
    }

    private List<Value> visibleValues(Module module) {
        java.util.ArrayList<Value> result = new java.util.ArrayList<Value>();
        for (Value value : module.getValues()) {
            if (value == null || !value.isVisible()) {
                continue;
            }
            PanelPaletteColorControl.Group colorGroup =
                    PanelPaletteColorControl.groupFor(value);
            if (colorGroup != null && !PanelPaletteColorControl.isLeader(value)) {
                continue;
            }
            result.add(value);
        }
        return result;
    }

    private Module firstModule(ModuleType category) {
        ModuleType previous = selectedCategory;
        selectedCategory = category;
        List<Module> modules = modulesInCategory();
        selectedCategory = previous;
        return modules.isEmpty() ? null : modules.get(0);
    }

    private static String panelModuleName(Module module) {
        if (module == null) {
            return "?";
        }
        String name = ClickGUI.getLanguage() == ClientLanguage.CHINESE
                ? module.getChinese() : module.getName();
        return name == null || name.trim().isEmpty() ? "?" : name;
    }

    private static String panelModuleDescription(Module module) {
        if (module == null) {
            return "";
        }
        String description = module.getDescription();
        return description == null ? "" : description;
    }

    private static String languageText(String english, String chinese) {
        return ClickGUI.languageText(english, chinese);
    }

    private static boolean isConfigManagerModule(Module module) {
        return module != null && "cfgmanager".equalsIgnoreCase(module.getName());
    }

    private String selectedConfigName() {
        return selectedConfigProfile >= 0 && selectedConfigProfile < configProfiles.size()
                ? configProfiles.get(selectedConfigProfile) : null;
    }

    private int indexOfConfigProfile(String name) {
        if (name == null) {
            return -1;
        }
        for (int index = 0; index < configProfiles.size(); index++) {
            if (configProfiles.get(index).equalsIgnoreCase(stripConfigExtension(name))) {
                return index;
            }
        }
        return -1;
    }

    private static String displayConfigName(String name) {
        String stripped = stripConfigExtension(name);
        return stripped.isEmpty() ? "profile" : stripped + ".yzk";
    }

    private static String stripConfigExtension(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(".yzk")
                ? trimmed.substring(0, trimmed.length() - 4).trim() : trimmed;
    }

    private static String configErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Config action failed";
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 52 ? singleLine : singleLine.substring(0, 49) + "...";
    }

    private interface ConfigAction {
        void run() throws IOException;
    }

    private int moduleCount(ModuleType category) {
        int count = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module != null && !isConfigManagerModule(module)
                    && EpsilonPanelCategories.belongsTo(module.getCategory(), category)) {
                count++;
            }
        }
        return count;
    }

    private String keyName(int keyCode) {
        if (PanelModuleKeybind.isMouseButton(keyCode)) {
            return PanelModuleKeybind.compactName(keyCode);
        }
        if (keyCode <= Keyboard.KEY_NONE) {
            return "NONE";
        }
        String name = Keyboard.getKeyName(keyCode);
        return name == null || name.isEmpty() ? "KEY" : name;
    }

    private void drawCloseGlyph(PanelClickGuiLayout.Rect bounds, int color) {
        float centerX = bounds.x() + bounds.width() * 0.5f;
        float centerY = bounds.y() + bounds.height() * 0.5f;
        float arm = 4.0f;
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0.0f);
        GL11.glRotatef(45.0f, 0.0f, 0.0f, 1.0f);
        RenderServices.shapes().rect(-arm, -0.75f, arm, 0.75f, color);
        GL11.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
        RenderServices.shapes().rect(-arm, -0.75f, arm, 0.75f, color);
        GL11.glPopMatrix();
    }

    private void drawMenuGlyph(float centerX, float centerY, int color) {
        float x = centerX - EpsilonPanelMetrics.MENU_LINE_WIDTH * 0.5f;
        float y = centerY - EpsilonPanelMetrics.MENU_LINE_GAP;
        for (int i = 0; i < 3; i++) {
            RenderServices.shapes().rect(x, y,
                    x + EpsilonPanelMetrics.MENU_LINE_WIDTH,
                    y + EpsilonPanelMetrics.MENU_LINE_HEIGHT, color);
            y += EpsilonPanelMetrics.MENU_LINE_GAP;
        }
    }

    private String compactKeyName(int keyCode) {
        String name = keyName(keyCode).toUpperCase();
        if (name.length() <= 3) {
            return name;
        }
        return name.substring(0, 3);
    }

    private void drawRect(PanelClickGuiLayout.Rect rect, float radius, int color) {
        RenderServices.shapes().rounded(rect.x(), rect.y(), rect.right(), rect.bottom(), radius, color);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
