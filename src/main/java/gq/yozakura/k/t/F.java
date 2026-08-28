package gq.yozakura.k.t;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

public final class F {
    private static final int TOKEN_AUTH_BUTTON_ID = 7420;

    private final Minecraft minecraft;
    private final H sessionManager;

    public F() {
        this.minecraft = Minecraft.getMinecraft();
        this.sessionManager = new H(minecraft);
    }

    @SubscribeEvent
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer) || hasButton(event.buttonList)) {
            return;
        }
        event.buttonList.add(new GuiButton(TOKEN_AUTH_BUTTON_ID, 5, 5, 100, 20, "TokenAuth"));
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        Session session = sessionManager.getCurrentSession();
        if (session == null) {
            return;
        }
        String status = String.format("User: \u00a7a%s \u00a7rUUID: \u00a7a%s",
                safe(session.getUsername()), safe(session.getPlayerID()));
        minecraft.fontRendererObj.drawString(status, 115, 10, 0xFFFFFFFF);
    }

    @SubscribeEvent
    public void onActionPerformedPre(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiMultiplayer) || event.button == null
                || event.button.id != TOKEN_AUTH_BUTTON_ID) {
            return;
        }
        minecraft.displayGuiScreen(new G(event.gui, sessionManager));
    }

    private static boolean hasButton(List<GuiButton> buttons) {
        for (GuiButton button : buttons) {
            if (button.id == TOKEN_AUTH_BUTTON_ID) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
