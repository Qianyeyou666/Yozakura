package gq.yozakura.auth.token;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.Session;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.List;

public final class TokenAuthStandaloneBridge {
    private static final int TOKEN_AUTH_BUTTON_ID = 7420;
    private static Field buttonListField;

    private final Minecraft minecraft;
    private final TokenAuthSessionManager sessionManager;
    private boolean lastLeftDown;
    private boolean failureLogged;

    public TokenAuthStandaloneBridge() {
        this.minecraft = Minecraft.getMinecraft();
        this.sessionManager = new TokenAuthSessionManager(minecraft);
    }

    public void tick() {
        try {
            GuiScreen screen = minecraft.currentScreen;
            if (!(screen instanceof GuiMultiplayer)) {
                lastLeftDown = false;
                return;
            }

            GuiButton button = ensureButton(screen);
            updateButtonText(button);
            handleClick(screen, button);
        } catch (Throwable throwable) {
            logFailure(throwable);
        }
    }

    private GuiButton ensureButton(GuiScreen screen) throws IllegalAccessException, NoSuchFieldException {
        List<GuiButton> buttons = buttonList(screen);
        for (GuiButton button : buttons) {
            if (button.id == TOKEN_AUTH_BUTTON_ID) {
                return button;
            }
        }
        GuiButton button = new GuiButton(TOKEN_AUTH_BUTTON_ID, 5, 5, 120, 20, "TokenAuth");
        buttons.add(button);
        return button;
    }

    private void updateButtonText(GuiButton button) {
        if (button == null) {
            return;
        }
        Session session = sessionManager.getCurrentSession();
        String username = session == null ? null : session.getUsername();
        if (username == null || username.trim().isEmpty()) {
            button.displayString = "TokenAuth";
            return;
        }
        button.displayString = trimToButton("TA: " + username, button.width - 10);
    }

    private void handleClick(GuiScreen screen, GuiButton button) {
        if (button == null || !Mouse.isCreated()) {
            lastLeftDown = false;
            return;
        }
        boolean leftDown = Mouse.isButtonDown(0);
        if (leftDown && !lastLeftDown && isMouseOver(button)) {
            minecraft.displayGuiScreen(new TokenAuthSessionGui(screen, sessionManager));
        }
        lastLeftDown = leftDown;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttonList(GuiScreen screen) throws NoSuchFieldException, IllegalAccessException {
        if (buttonListField == null) {
            buttonListField = findButtonListField(screen.getClass());
            buttonListField.setAccessible(true);
        }
        return (List<GuiButton>) buttonListField.get(screen);
    }

    private static Field findButtonListField(Class<?> screenClass) throws NoSuchFieldException {
        Class<?> type = screenClass;
        while (type != null) {
            for (String name : new String[]{"buttonList", "field_146292_n", "n"}) {
                try {
                    Field field = type.getDeclaredField(name);
                    if (List.class.isAssignableFrom(field.getType())) {
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException("GuiScreen button list");
    }

    private boolean isMouseOver(GuiButton button) {
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int mouseX = Mouse.getX() * resolution.getScaledWidth() / Math.max(1, minecraft.displayWidth);
        int mouseY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / Math.max(1, minecraft.displayHeight) - 1;
        return mouseX >= button.xPosition && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height;
    }

    private String trimToButton(String value, int maxWidth) {
        if (minecraft.fontRendererObj == null || minecraft.fontRendererObj.getStringWidth(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int suffixWidth = minecraft.fontRendererObj.getStringWidth(suffix);
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() > 0
                && minecraft.fontRendererObj.getStringWidth(builder.toString()) + suffixWidth > maxWidth) {
            builder.setLength(builder.length() - 1);
        }
        return builder.append(suffix).toString();
    }

    private void logFailure(Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraStandalone.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println("TokenAuth standalone bridge failed");
                throwable.printStackTrace(writer);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
