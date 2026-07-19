package gq.yozakura.ui.click;

import gq.yozakura.core.ConfigBridge;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.manager.FileManager;
import gq.yozakura.module.render.ClickGUI;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ConfigProfileScreen extends GuiScreen {
    private static final int OPEN_FOLDER = 1, REFRESH = 2, SAVE = 3, LOAD = 4, BACK = 5;
    private static final int PANEL_WIDTH = 420, PANEL_HEIGHT = 286, ROW_HEIGHT = 22, VISIBLE_ROWS = 6;

    private final GuiScreen previousScreen;
    private final List<String> profiles = new ArrayList<String>();
    private GuiTextField nameField;
    private GuiButton loadButton;
    private int selectedIndex = -1;
    private int scrollOffset;
    private String status = "Drop .yzk files into the profile folder, then refresh.";
    private int statusColor = 0xFFBDAFBE;

    public ConfigProfileScreen(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int x = panelX(), y = panelY();
        nameField = new GuiTextField(10, fontRendererObj, x + 20, y + 196, 188, 20);
        nameField.setMaxStringLength(64);
        buttonList.add(new GuiButton(SAVE, x + 216, y + 196, 88, 20, "Save .yzk"));
        loadButton = new GuiButton(LOAD, x + 312, y + 196, 88, 20, "Load");
        buttonList.add(loadButton);
        buttonList.add(new GuiButton(OPEN_FOLDER, x + 20, y + 232, 118, 20, "Open Folder"));
        buttonList.add(new GuiButton(REFRESH, x + 146, y + 232, 88, 20, "Refresh"));
        buttonList.add(new GuiButton(BACK, x + 242, y + 232, 158, 20, "Back to ClickGUI"));
        refreshProfiles(null);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        super.updateScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        VisualPalette palette = ClickGUI.currentPalette();
        int x = panelX(), y = panelY();
        drawRect(0, 0, width, height, palette.getCanvas());
        drawRect(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, palette.getSurface());
        drawRect(x, y, x + PANEL_WIDTH, y + 34, palette.getSurfaceRaised());
        drawCenteredString(fontRendererObj, "Config Profiles", width / 2, y + 12, palette.getTextPrimary());
        fontRendererObj.drawString("Available .yzk files", x + 20, y + 43, palette.getTextSecondary());
        drawProfileRows(x, y, mouseX, mouseY, palette);
        fontRendererObj.drawString("Profile name", x + 20, y + 184, palette.getTextSecondary());
        nameField.drawTextBox();
        drawCenteredString(fontRendererObj, status, width / 2, y + 264, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawProfileRows(int x, int y, int mouseX, int mouseY, VisualPalette palette) {
        int listX = x + 20, listY = y + 58, listWidth = PANEL_WIDTH - 40;
        drawRect(listX, listY, listX + listWidth, listY + VISIBLE_ROWS * ROW_HEIGHT, palette.getSurfaceOverlay());
        if (profiles.isEmpty()) {
            drawCenteredString(fontRendererObj, "No .yzk profiles found", width / 2, listY + 58,
                    palette.getTextDisabled());
            return;
        }
        int end = Math.min(profiles.size(), scrollOffset + VISIBLE_ROWS);
        for (int index = scrollOffset; index < end; index++) {
            int rowY = listY + (index - scrollOffset) * ROW_HEIGHT;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (selected || hovered) {
                drawRect(listX + 1, rowY + 1, listX + listWidth - 1, rowY + ROW_HEIGHT - 1,
                        selected ? palette.getAccentSoft() : palette.getSurfaceRaised());
            }
            fontRendererObj.drawString(profiles.get(index) + ".yzk", listX + 9, rowY + 7,
                    selected ? palette.getAccentPrimary() : palette.getTextPrimary());
        }
        if (profiles.size() > VISIBLE_ROWS) {
            fontRendererObj.drawString((scrollOffset + 1) + "-" + end + " / " + profiles.size(),
                    listX + listWidth - 58, listY - 14, palette.getTextDisabled());
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == OPEN_FOLDER) {
            runAction("Opened profile folder", new ConfigAction() {
                public void run() throws IOException { ConfigBridge.openProfileDirectory(); }
            });
        } else if (button.id == REFRESH) {
            refreshProfiles(selectedName());
        } else if (button.id == SAVE) {
            saveProfile();
        } else if (button.id == LOAD && selectedIndex >= 0) {
            final String selected = profiles.get(selectedIndex);
            runAction("Loaded " + selected + ".yzk", new ConfigAction() {
                public void run() throws IOException { ConfigBridge.loadProfile(selected); }
            });
        } else if (button.id == BACK) {
            mc.displayGuiScreen(previousScreen);
        }
        super.actionPerformed(button);
    }

    private void saveProfile() {
        final String name = nameField.getText();
        final String success = "Saved " + displayName(name);
        runAction(success, new ConfigAction() {
            public void run() throws IOException { ConfigBridge.saveProfile(name); }
        });
        if (statusColor == ClickGUI.currentPalette().getSuccess()) {
            refreshProfiles(stripExtension(name));
            status = success;
            statusColor = ClickGUI.currentPalette().getSuccess();
        }
    }

    private void runAction(String success, ConfigAction action) {
        try {
            action.run();
            status = success;
            statusColor = ClickGUI.currentPalette().getSuccess();
        } catch (Exception exception) {
            status = errorMessage(exception);
            statusColor = ClickGUI.currentPalette().getDanger();
            FileManager.logConfigFailure("Config profile action failed", exception);
        }
    }

    private void refreshProfiles(String preferredSelection) {
        try {
            profiles.clear();
            profiles.addAll(ConfigBridge.listProfiles());
            selectedIndex = indexOf(preferredSelection);
            if (selectedIndex < 0 && !profiles.isEmpty()) selectedIndex = 0;
            if (selectedIndex >= 0) nameField.setText(profiles.get(selectedIndex));
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, profiles.size() - VISIBLE_ROWS)));
            status = profiles.size() + " profile" + (profiles.size() == 1 ? "" : "s") + " found";
            statusColor = ClickGUI.currentPalette().getTextSecondary();
        } catch (IOException exception) {
            profiles.clear();
            selectedIndex = -1;
            status = errorMessage(exception);
            statusColor = ClickGUI.currentPalette().getDanger();
            FileManager.logConfigFailure("Config profile refresh failed", exception);
        }
        loadButton.enabled = selectedIndex >= 0;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        int listX = panelX() + 20, listY = panelY() + 58;
        if (mouseButton != 0 || mouseX < listX || mouseX >= listX + PANEL_WIDTH - 40
                || mouseY < listY || mouseY >= listY + VISIBLE_ROWS * ROW_HEIGHT) return;
        int index = scrollOffset + (mouseY - listY) / ROW_HEIGHT;
        if (index < profiles.size()) {
            selectedIndex = index;
            nameField.setText(profiles.get(index));
            loadButton.enabled = true;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && profiles.size() > VISIBLE_ROWS) {
            scrollOffset += wheel < 0 ? 1 : -1;
            scrollOffset = Math.max(0, Math.min(scrollOffset, profiles.size() - VISIBLE_ROWS));
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previousScreen);
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && nameField.isFocused()) {
            saveProfile();
            return;
        }
        nameField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }

    private int indexOf(String name) {
        if (name == null) return -1;
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).equalsIgnoreCase(name)) return index;
        }
        return -1;
    }

    private String selectedName() {
        return selectedIndex >= 0 && selectedIndex < profiles.size() ? profiles.get(selectedIndex) : null;
    }

    private static String displayName(String name) {
        String stripped = stripExtension(name);
        return stripped.isEmpty() ? "profile" : stripped + ".yzk";
    }

    private static String stripExtension(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.toLowerCase().endsWith(".yzk")
                ? trimmed.substring(0, trimmed.length() - 4).trim() : trimmed;
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return "Profile action failed. Check YozakuraConfig.log.";
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 72 ? singleLine : singleLine.substring(0, 69) + "...";
    }

    private interface ConfigAction { void run() throws IOException; }
}
