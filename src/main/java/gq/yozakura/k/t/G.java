package gq.yozakura.k.t;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public final class G extends GuiScreen {
    private static final int LOGIN_BUTTON_ID = 1;
    private static final int RESTORE_BUTTON_ID = 2;
    private static final int BACK_BUTTON_ID = 3;

    private final GuiScreen previousScreen;
    private final H sessionManager;

    private String status = "Session:";
    private GuiTextField sessionField;

    public G(GuiScreen previousScreen, H sessionManager) {
        this.previousScreen = previousScreen;
        this.sessionManager = sessionManager;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int fieldWidth = Math.min(360, Math.max(220, this.width - 80));
        int x = (this.width - fieldWidth) / 2;
        int y = this.height / 2 - 10;

        this.sessionField = new GuiTextField(1, this.fontRendererObj, x, y, fieldWidth, 20);
        this.sessionField.setMaxStringLength(32767);
        this.sessionField.setFocused(true);

        this.buttonList.add(new GuiButton(LOGIN_BUTTON_ID, x, y + 30, fieldWidth, 20, "Login"));
        this.buttonList.add(new GuiButton(RESTORE_BUTTON_ID, x, y + 56, fieldWidth, 20, "Restore"));
        this.buttonList.add(new GuiButton(BACK_BUTTON_ID, x, y + 82, fieldWidth, 20, "Back"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        if (sessionField != null) {
            sessionField.updateCursorCounter();
        }
        super.updateScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int statusX = this.width / 2 - this.fontRendererObj.getStringWidth(status) / 2;
        this.fontRendererObj.drawString(status, statusX, this.height / 2 - 32, 0xFFFFFFFF);
        if (sessionField != null) {
            sessionField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == LOGIN_BUTTON_ID) {
            login();
        } else if (button.id == RESTORE_BUTTON_ID) {
            restore();
        } else if (button.id == BACK_BUTTON_ID) {
            this.mc.displayGuiScreen(previousScreen);
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(previousScreen);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            login();
            return;
        }
        if (sessionField != null) {
            sessionField.textboxKeyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (sessionField != null) {
            sessionField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void login() {
        try {
            sessionManager.login(sessionField.getText());
            this.mc.displayGuiScreen(previousScreen);
        } catch (Exception exception) {
            status = EnumChatFormatting.RED + errorMessage("Couldn't set session", exception);
            exception.printStackTrace();
        }
    }

    private void restore() {
        try {
            sessionManager.restore();
            this.mc.displayGuiScreen(previousScreen);
        } catch (Exception exception) {
            status = EnumChatFormatting.RED + errorMessage("Couldn't restore session", exception);
            exception.printStackTrace();
        }
    }

    private static String errorMessage(String fallback, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return fallback + " (check logs)";
        }
        String trimmed = message.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.length() <= 96 ? trimmed : trimmed.substring(0, 93) + "...";
    }
}
