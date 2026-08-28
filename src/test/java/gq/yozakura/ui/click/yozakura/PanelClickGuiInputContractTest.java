package gq.yozakura.ui.click.yozakura;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class PanelClickGuiInputContractTest {
    @Test
    public void dropdownConsumesClicksBeforeUnderlyingPanels() throws IOException {
        String source = source();

        int popup = source.indexOf("handleOpenDropdownClick(mouseX, mouseY, mouseButton)");
        int detail = source.indexOf("handleDetailClick(mouseX, mouseY, mouseButton)");
        int modules = source.indexOf("handleModuleClick(mouseX, mouseY, mouseButton)");
        int rail = source.indexOf("handleRailClick(mouseX, mouseY)");

        assertTrue(popup >= 0);
        assertTrue(popup < detail);
        assertTrue(detail < rail);
        assertTrue(rail < modules);
        assertTrue(source.contains("values.requestDropdownClose();\n        return true;"));
    }

    @Test
    public void palettePickerConsumesClicksAndDragsBeforePanelControls() throws IOException {
        String source = source();
        int pickerClick = source.indexOf("handleOpenPaletteColorPickerClick(mouseX, mouseY, mouseButton)");
        int dropdownClick = source.indexOf("handleOpenDropdownClick(mouseX, mouseY, mouseButton)");
        int pickerDrag = source.indexOf("updatePaletteColorPickerDrag(mouseX, mouseY)");
        int sliderDrag = source.indexOf("values.isDraggingSlider() && detailOwner() != null");
        String drawScreen = between(source, "public void drawScreen(",
                "private void rebuildLayout(", 0);

        assertTrue(pickerClick >= 0 && pickerClick < dropdownClick);
        assertTrue(pickerDrag >= 0 && pickerDrag < sliderDrag);
        assertTrue(drawScreen.contains("paletteColorPicker.isDragging()"));
        assertTrue(drawScreen.contains("updatePaletteColorPickerDrag(mouseX, mouseY);"));
        assertTrue(source.contains("paletteColorPicker.mouseReleased();"));
        assertTrue(source.contains("paletteColorPicker.close();"));
    }

    @Test
    public void moduleSwitchDoesNotImplicitlyOpenDetailPage() throws IOException {
        String source = source();
        String handler = between(source, "private boolean handleModuleClick(",
                "private boolean handleDetailClick(", 0);

        assertTrue(handler.contains("toggleBounds.contains(mouseX, mouseY)"));
        assertTrue(handler.contains("module.toggle();"));
        assertTrue(handler.contains("settingsBounds.contains(mouseX, mouseY)"));
        assertTrue(handler.contains("openModuleDetail(module);"));
        assertTrue(handler.contains("moduleDetailOpen = true;"));
    }

    @Test
    public void keybindListeningSupportsCancelClearAndAssign() throws IOException {
        String source = source();

        assertTrue(source.contains("keyCode == Keyboard.KEY_ESCAPE"));
        assertTrue(source.contains("keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE"));
        assertTrue(source.contains("selectedModule.setKey(Keyboard.KEY_NONE);"));
        assertTrue(source.contains("selectedModule.setKey(keyCode);"));
    }

    @Test
    public void detailHeaderSegmentsBindToRealModuleState() throws IOException {
        String source = source();

        // Draw side reads the live module state (Epsilon ModuleDetailPanel).
        assertTrue(source.contains("owner.getBindMode() == Module.BindMode.HOLD, mouseX, mouseY"));
        assertTrue(source.contains("owner.isHidden(), mouseX, mouseY"));

        // Click side reuses the exact draw bounds and their true midpoint.
        assertTrue(source.contains("EpsilonPanelGeometry.DetailHeader headerGeometry"));
        assertTrue(source.contains("if (bindMode.contains(mouseX, mouseY))"));
        assertTrue(source.contains("mouseX < bindMode.x() + bindMode.width() * 0.5f"));
        assertTrue(source.contains("if (hidden.contains(mouseX, mouseY))"));
        assertTrue(source.contains("mouseX >= hidden.x() + hidden.width() * 0.5f"));
    }

    @Test
    public void enumPopupScrollsBeforePanelsWhenOpen() throws IOException {
        String source = source();
        int popupScroll = source.indexOf("scrollOpenDropdown(scrollY);");
        int moduleScroll = source.indexOf("moduleScrollVelocity = PanelClickGuiMotion.addWheelImpulse");
        assertTrue(popupScroll >= 0);
        assertTrue(popupScroll < moduleScroll);
    }

    @Test
    public void nonLeftButtonsOnlyReachTheActiveKeybindListener() throws IOException {
        String source = source();
        int mouseBind = source.indexOf("selectedModule.setKey(PanelModuleKeybind.encodeMouseButton(mouseButton));");
        int leftOnlyGate = source.indexOf("if (mouseButton != 0) {\n            return;");
        int popup = source.indexOf("handleOpenDropdownClick(mouseX, mouseY, mouseButton)");

        assertTrue(mouseBind >= 0);
        assertTrue(leftOnlyGate > mouseBind);
        assertTrue(popup > leftOnlyGate);
        String listenerBranch = between(source,
                "if (listeningKeybind && selectedModule != null && !clientSettingsMode)",
                "if (mouseButton != 0)", 0);
        assertTrue(listenerBranch.contains("headerGeometry.keybind().contains(mouseX, mouseY)"));
        assertTrue(listenerBranch.contains("selectedModule.setKey(PanelModuleKeybind.encodeMouseButton(mouseButton));"));
    }

    @Test
    public void moduleListUsesWideCardsWithSettingsAndToggleActions() throws IOException {
        String source = source();
        String rowRenderer = between(source, "private void drawModuleRow(",
                "private void drawDetailPanel(", 0);

        assertTrue(rowRenderer.contains("moduleSettingsButton(rowBounds)"));
        assertTrue(rowRenderer.contains("moduleSwitch(rowBounds)"));
        assertTrue(rowRenderer.contains("panelModuleDescription(module)"));
        assertTrue(!rowRenderer.contains("keyName(module.getKey())"));
    }

    @Test
    public void moduleDetailReplacesTheListAndHasACloseButton() throws IOException {
        String source = source();
        int detail = source.indexOf("handleDetailClick(mouseX, mouseY, mouseButton)");
        int drag = source.indexOf("handlePanelDragClick(mouseX, mouseY, mouseButton)");

        assertTrue(source.contains("private boolean moduleDetailOpen"));
        assertTrue(source.contains("if (moduleDetailOpen || clientSettingsMode)"));
        assertTrue(source.contains("EpsilonPanelGeometry.detailCloseButton(bounds)"));
        assertTrue(source.contains("moduleDetailOpen = true;"));
        assertTrue(source.contains("moduleDetailOpen = false;"));
        assertTrue(detail >= 0 && detail < drag);
    }

    @Test
    public void navigationConsumesTheTopLeftMenuBeforePanelDragging() throws IOException {
        String source = source();
        int rail = source.indexOf("handleRailClick(mouseX, mouseY)");
        int drag = source.indexOf("handlePanelDragClick(mouseX, mouseY, mouseButton)");

        assertTrue(rail >= 0 && rail < drag);
    }

    @Test
    public void wheelRoutesOnlyToTheVisibleRightHandPage() throws IOException {
        String source = source();
        String wheel = between(source, "public void handleMouseInput()", "private void scrollOpenDropdown", 0);

        assertTrue(wheel.contains("if (moduleDetailOpen || clientSettingsMode)"));
        assertTrue(wheel.contains("detailViewport().contains(mx, my)"));
        assertTrue(wheel.contains("moduleViewport().contains(mx, my)"));
    }

    @Test
    public void scrollbarClickRoutesOnlyToTheVisibleRightHandPage() throws IOException {
        String source = source();
        String click = between(source, "protected void mouseClicked(",
                "private boolean handleOpenPaletteColorPickerClick", 0);
        int visiblePageBranch = click.indexOf("if (moduleDetailOpen || clientSettingsMode)");
        int detailContent = click.indexOf("handleDetailClick(mouseX, mouseY, mouseButton)");

        assertTrue(visiblePageBranch >= 0 && visiblePageBranch < detailContent);
        assertTrue(click.contains("handleScrollbarClick(mouseX, mouseY, true)"));
        assertTrue(click.contains("handleScrollbarClick(mouseX, mouseY, false)"));
    }

    @Test
    public void rightHandPagesUseAnAnimatedHorizontalTransition() throws IOException {
        String source = source();

        assertTrue(source.contains("drawAnimatedContentPage("));
        assertTrue(source.contains("panel-page-transition"));
        assertTrue(source.contains("GL11.glTranslatef"));
    }

    @Test
    public void languageSelectorUsesThePersistedClickGuiLanguageValue() throws IOException {
        String source = source();
        String detail = between(source, "private void drawDetailPanel(",
                "private void drawPaletteColorRow(", 0);
        String viewport = between(source, "private PanelClickGuiLayout.Rect detailViewport()",
                "private float moduleViewportHeight()", 0);

        assertTrue(source.contains("ClickGUI.getLanguage() == ClientLanguage.CHINESE"));
        assertTrue(source.contains("ClickGUI.setLanguage(PanelClickGuiLanguageControl.languageAt(language, mouseX));"));
        assertTrue(source.contains("PanelClickGuiLanguageControl.segmentBounds(layout.detail())"));
        assertTrue(detail.contains("drawLanguageControl(bounds, mouseX, mouseY);"));
        assertTrue(detail.contains("PanelClickGuiLayout.Rect viewport = detailViewport();"));
        assertTrue(viewport.contains("PanelClickGuiLanguageControl.settingsContentTop(b)"));
    }

    @Test
    public void resizeUsesPerFramePhysicalPointerCaptureAndPersistsOnlyOnRelease() throws IOException {
        String source = source();
        int resize = source.indexOf("handleResizeClick(mouseX, mouseY, mouseButton)");
        int detail = source.indexOf("handleDetailClick(mouseX, mouseY, mouseButton)");
        String drawScreen = between(source, "public void drawScreen(",
                "private void rebuildLayout(", 0);
        String updateSize = between(source, "private void updatePanelSize(",
                "private void persistPanelSize(", 0);
        String persistSize = between(source, "private void persistPanelSize(",
                "private boolean handleScrollbarClick(", 0);

        assertTrue(resize >= 0 && resize < detail);
        assertTrue(drawScreen.contains("else if (resizingPanel)"));
        assertTrue(drawScreen.contains("updatePanelSize(mouseX, mouseY);"));
        assertTrue(updateSize.contains("preciseMouseX(mouseX) - resizeStartMouseX"));
        assertTrue(updateSize.contains("preciseMouseY(mouseY) - resizeStartMouseY"));
        assertTrue(updateSize.contains("PanelClickGuiLayout.resized(layout"));
        assertTrue(!updateSize.contains("ClickGUI.panelWidth.setNumberValue"));
        assertTrue(!updateSize.contains("ClickGUI.panelHeight.setNumberValue"));
        assertTrue(!updateSize.contains("rebuildLayout()"));
        assertTrue(!updateSize.contains("moduleContentHeight()"));
        assertTrue(!updateSize.contains("detailContentHeight()"));
        assertTrue(!updateSize.contains("modulesInCategory()"));
        assertTrue(!updateSize.contains("visibleValues("));
        assertTrue(persistSize.contains("ClickGUI.panelWidth.setNumberValue"));
        assertTrue(persistSize.contains("ClickGUI.panelHeight.setNumberValue"));
        assertTrue(source.contains("PanelClickGuiLayout.resizeHandle(layout.panel())"));
    }

    @Test
    public void panelShadowAndModuleActionsShareTheExactVisualGeometry() throws IOException {
        String source = source();
        String panel = between(source, "private void drawPanel(",
                "private void drawAnimatedContentPage(", 0);
        String moduleRow = between(source, "private void drawModuleRow(",
                "private PanelClickGuiLayout.Rect configProfileListBounds(", 0);

        assertTrue(panel.contains("17.0f, 0.0f, 0.0f, PanelClickGuiPalette.shadow(96)"));
        assertTrue(source.contains("MODULE_SETTINGS_ICON_Y_OFFSET = -1.0f"));
        assertTrue(moduleRow.contains("settingsBounds.y()\n                + (settingsBounds.height() - settingsIconSize) * 0.5f\n                + MODULE_SETTINGS_ICON_Y_OFFSET"));
        assertTrue(moduleRow.contains("EpsilonPanelGeometry.moduleSwitch(rowBounds)"));
    }

    @Test
    public void panelDragUsesTitleBarPointerCaptureAndPersistsOnlyOnRelease() throws IOException {
        String source = source();
        int drag = source.indexOf("handlePanelDragClick(mouseX, mouseY, mouseButton)");
        int detail = source.indexOf("handleDetailClick(mouseX, mouseY, mouseButton)");
        String updatePosition = between(source, "private void updatePanelPosition(",
                "private void persistPanelPosition(", 0);
        String persistPosition = between(source, "private void persistPanelPosition(",
                "private boolean handleResizeClick(", 0);

        assertTrue(detail >= 0 && detail < drag);
        assertTrue(source.contains("PanelClickGuiLayout.dragHandle(layout)"));
        assertTrue(source.contains("if (draggingPanel)"));
        assertTrue(source.contains("updatePanelPosition(mouseX, mouseY);"));
        String drawScreen = between(source, "public void drawScreen(",
                "private void rebuildLayout(", 0);
        assertTrue(drawScreen.contains("if (draggingPanel)"));
        assertTrue(drawScreen.contains("updatePanelPosition(mouseX, mouseY);"));
        assertTrue(updatePosition.contains("PanelClickGuiLayout.translated(layout"));
        assertTrue(updatePosition.contains("preciseMouseX(mouseX) - dragOffsetX"));
        assertTrue(updatePosition.contains("preciseMouseY(mouseY) - dragOffsetY"));
        assertTrue(updatePosition.contains("panelPositionX = x"));
        assertTrue(updatePosition.contains("panelPositionY = y"));
        assertTrue(!updatePosition.contains("ClickGUI.panelX.setNumberValue"));
        assertTrue(!updatePosition.contains("ClickGUI.panelY.setNumberValue"));
        assertTrue(!updatePosition.contains("rebuildLayout()"));
        assertTrue(persistPosition.contains("ClickGUI.panelX.setNumberValue"));
        assertTrue(persistPosition.contains("ClickGUI.panelY.setNumberValue"));
        String release = between(source, "protected void mouseReleased(", "public void handleMouseInput(", 0);
        assertTrue(release.contains("if (draggingPanel)"));
        assertTrue(release.contains("persistPanelPosition();"));
        assertTrue(release.contains("draggingPanel = false;"));
    }

    @Test
    public void palettePickerUsesRoundedHsvSurfacesAndSkipsUnchangedRgbWrites() throws IOException {
        String picker = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/PanelPaletteColorPicker.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(picker.contains("RenderServices.shapes().roundedPalette("));
        assertTrue(picker.contains("RenderServices.shapes().roundedHue("));
        assertTrue(!picker.contains("RenderServices.shapes().horizontalGradient(sv.x()"));
        assertTrue(!picker.contains("RenderServices.shapes().verticalGradient(sv.x()"));
        assertTrue(picker.contains("public boolean isDragging()"));
        assertTrue(picker.contains("if (color == lastAppliedColor)"));
    }

    @Test
    public void palettePickerBottomBarEditsThePersistedClickGuiAlpha() throws IOException {
        String picker = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/PanelPaletteColorPicker.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(picker.contains("private boolean draggingAlpha;"));
        assertTrue(picker.contains("private PanelClickGuiLayout.Rect alphaBounds("));
        assertTrue(picker.contains("ClickGUI.clickGuiAlpha.setNumberValue("));
        assertTrue(picker.contains("return draggingSv || draggingHue || draggingAlpha;"));
        assertTrue(!picker.contains("previewBounds("));
    }

    @Test
    public void modePopupKeepsAClosingKeyUntilItsReverseAnimationFinishes() throws IOException {
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiValueRenderer.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(renderer.contains("public String closingDropdownKey;"));
        assertTrue(renderer.contains("public void requestDropdownClose()"));
        assertTrue(renderer.contains("state.closingDropdownKey = state.openDropdownKey;"));
        assertTrue(renderer.contains("float popupTarget = open ? 1.0f : 0.0f;"));
        assertTrue(renderer.contains("state.closingDropdownKey = null;"));
        assertTrue(renderer.contains("return state.openDropdownKey != null\n                || state.closingDropdownKey != null;"));
    }

    @Test
    public void escapeRequestsPanelCloseAndDrawCompletesItAfterAnimation() throws IOException {
        String source = source();
        String draw = between(source, "public void drawScreen(",
                "private void rebuildLayout(", 0);
        String keys = between(source, "protected void keyTyped(",
                "private PanelClickGuiLayout.Rect moduleViewport()", 0);

        assertTrue(source.contains("private boolean closingGui;"));
        assertTrue(source.contains("private final PanelClickGuiOpenCloseAnimation openCloseAnimation"));
        assertTrue(source.contains("private void requestClose()"));
        assertTrue(keys.contains("requestClose();"));
        String escapeBranch = between(keys, "if (keyCode == Keyboard.KEY_ESCAPE) {",
                "super.keyTyped(typedChar, keyCode);", keys.lastIndexOf("if (keyCode == Keyboard.KEY_ESCAPE) {"));
        assertTrue(!escapeBranch.contains("mc.displayGuiScreen(null);"));
        assertTrue(draw.contains("openCloseAnimation.progressAt(!closingGui, frameNowNanos)"));
        assertTrue(draw.contains("openCloseAnimation.visualProgress(progress)"));
        assertTrue(draw.contains("completeCloseIfReady();"));
        assertTrue(source.contains("openCloseAnimation.isClosed()"));
        assertTrue(!source.contains("animations.eased(\"panel-open\""));
    }

    @Test
    public void settingLabelsFitTheirColumnInsteadOfHardClippingAtOneScale() throws IOException {
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiValueRenderer.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(renderer.contains("labelScaleForWidth(label, availableLabelWidth)"));
        assertTrue(renderer.contains("Math.max(MIN_LABEL_SCALE"));
        assertTrue(renderer.contains("controlX = x + labelWidth"));
    }

    @Test
    public void numberFieldUsesDraftEditingWithoutStealingSliderCapture() throws IOException {
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiValueRenderer.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        String click = between(renderer, "private boolean clickNumbers(",
                "@SuppressWarnings(\"unchecked\")", 0);

        assertTrue(renderer.contains("public String focusedNumberKey;"));
        assertTrue(renderer.contains("public String numberDraft;"));
        assertTrue(click.contains("numberField(row)"));
        assertTrue(click.contains("beginNumberEditing(num, key)"));
        assertTrue(click.indexOf("field.contains(mouseX, mouseY)")
                < click.indexOf("interactive.contains(mouseX, mouseY)"));
        assertTrue(renderer.contains("public boolean keyTypedNumber("));
        assertTrue(renderer.contains("commitNumberEditing(num, key)"));
        assertTrue(renderer.contains("cancelNumberEditing()"));
        assertTrue(renderer.contains("Keyboard.KEY_RETURN"));
        assertTrue(renderer.contains("Keyboard.KEY_NUMPADENTER"));
        assertTrue(renderer.contains("Keyboard.KEY_ESCAPE"));
        assertTrue(renderer.contains("Keyboard.KEY_BACK"));
        assertTrue(renderer.contains("Keyboard.KEY_DELETE"));
        assertTrue(renderer.contains("PanelNumberInputPolicy.normalizeTypedValue(num, parsed)"));
        assertTrue(renderer.contains("updateSliderFromMouse("));
        assertTrue(renderer.contains("Math.round((v - mn) / inc) * inc"));
        assertTrue(!renderer.contains("quantizeAndClamp(num, parsed)"));
    }

    @Test
    public void modePropertiesUseTextDropdownsInsteadOfNumericSliders() throws IOException {
        String source = source();
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiValueRenderer.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        String drawDispatch = between(renderer, "if (value instanceof Mode) {",
                "return ROW_H + ROW_GAP;", 0);
        String clickDispatch = between(renderer, "if (value instanceof Mode) {",
                "return false;", renderer.indexOf("public boolean mouseClicked("));

        assertTrue(renderer.contains("import gq.yozakura.value.properties.ModeProperty;"));
        assertTrue(drawDispatch.contains("value instanceof ModeProperty"));
        assertTrue(drawDispatch.indexOf("value instanceof ModeProperty")
                < drawDispatch.indexOf("value instanceof Numbers"));
        assertTrue(clickDispatch.contains("value instanceof ModeProperty"));
        assertTrue(clickDispatch.indexOf("value instanceof ModeProperty")
                < clickDispatch.indexOf("value instanceof Numbers"));
        assertTrue(renderer.contains("mode.getModeString()"));
        assertTrue(renderer.contains("String[] modes = mode.getModes();"));
        assertTrue(renderer.contains("mode.setMode(modes[index]);"));
        assertTrue(source.contains("value instanceof Numbers\n                    && !(value instanceof ModeProperty)"));
        assertTrue(source.contains("value instanceof ModeProperty"));
        assertTrue(source.contains("mouseScrolledOpenDropdown((ModeProperty) value"));
    }

    @Test
    public void panelCachesTheCapturedMainMenuInsteadOfRedrawingItsPanoramaEveryFrame() throws IOException {
        String source = source();
        String draw = between(source, "public void drawScreen(",
                "private void rebuildLayout(", 0);

        assertTrue(source.contains("private final GuiScreen backgroundScreen;"));
        assertTrue(source.contains("private final MainMenuBackdropSnapshot mainMenuBackdrop"));
        assertTrue(source.contains("Minecraft.getMinecraft().currentScreen"));
        assertTrue(source.contains("previousScreen instanceof GuiMainMenu"));
        assertTrue(source.contains("mainMenuBackdrop.capture();"));
        assertTrue(draw.contains("mainMenuBackdrop.draw(width, height);"));
        assertTrue(!draw.contains("backgroundScreen.drawScreen("));
        assertTrue(source.contains("mainMenuBackdrop.dispose();"));
        String snapshot = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/MainMenuBackdropSnapshot.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(snapshot.contains("GL11.glCopyTexSubImage2D("));
        assertTrue(snapshot.contains("GL11.GL_LINEAR"));
        assertTrue(snapshot.contains("GL11.glDeleteTextures(textureId);"));
        assertTrue(!snapshot.contains("glReadPixels"));
        assertTrue(draw.contains("drawStableBackdrop(open)"));
        assertTrue(draw.contains("Math.round(82.0f * open)"));
        assertTrue(draw.contains("if (useRealtimeBackdrop())"));
        assertTrue(source.contains("return mc.theWorld != null && !draggingPanel && !resizingPanel;"));
        assertTrue(source.contains("mc.displayGuiScreen(backgroundScreen);"));
        assertTrue(source.contains("new PanelClickGuiOpenCloseAnimation()"));
        assertTrue(draw.contains("float scale = 0.94f + 0.06f * open;"));
        assertTrue(draw.contains("(1.0f - open) * 12.0f"));
    }

    @Test
    public void panelUsesOneHighPrecisionMonotonicClockForEveryAnimationInAFrame() throws IOException {
        String source = source();
        String draw = between(source, "public void drawScreen(",
                "private boolean useRealtimeBackdrop()", 0);
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiValueRenderer.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(source.contains("private long lastFrameNanos;"));
        assertTrue(source.contains("private long frameTimeMillis;"));
        assertTrue(draw.contains("long frameNowNanos = System.nanoTime();"));
        assertTrue(draw.contains("frameTimeMillis = frameNowNanos / 1_000_000L;"));
        assertTrue(draw.contains("values.beginFrame(frameTimeMillis);"));
        assertTrue(draw.contains("16_666_667.0"));
        assertTrue(draw.contains("advanceScroll(frameScale);"));
        assertTrue(!draw.contains("advanceScroll(partialTicks);"));
        assertTrue(!source.contains("System.currentTimeMillis()"));
        assertTrue(renderer.contains("public void beginFrame(long frameTimeMillis)"));
        assertTrue(!renderer.contains("System.currentTimeMillis()"));
    }

    @Test
    public void moduleSettingsUsesTheProvidedLinearFilteredTexture() throws IOException {
        String source = source();
        String moduleRow = between(source, "private void drawModuleRow(",
                "private PanelClickGuiLayout.Rect configProfileListBounds(", 0);

        assertTrue(source.contains("PANEL_SETTINGS_ICON"));
        assertTrue(source.contains("new ResourceLocation(\"minecraft\",\n            \"yozakura/panel/settings-gear.png\")"));
        assertTrue(moduleRow.contains("RenderUtil.drawTexturedRectTinted("));
        assertTrue(source.contains("RAIL_CONFIG_ICON_Y_OFFSET = -3.0f"));
        assertTrue(source.contains("RAIL_SETTINGS_ICON_Y_OFFSET = -2.0f"));
        assertTrue(source.contains("MODULE_SETTINGS_ICON_Y_OFFSET = -1.0f"));
    }

    @Test
    public void valueControlsRejectEveryNonLeftButton() throws IOException {
        String renderer = new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/ClickGuiValueRenderer.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(renderer.contains("if (button != 0) {\n            return false;\n        }"));
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/gq/yozakura/ui/click/yozakura/YozakuraPanelClickGui.java")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String between(String source, String start, String end, int fromIndex) {
        int startIndex = source.indexOf(start, fromIndex);
        int endIndex = source.indexOf(end, startIndex);
        return source.substring(startIndex, endIndex);
    }
}
