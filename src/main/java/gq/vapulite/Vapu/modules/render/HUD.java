package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Manager.NotificationManager;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.ColorUtils;
import gq.vapulite.Vapu.utils.RenderUtil;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HUD extends Module {
    private final Option<Boolean> watermark = new Option<Boolean>("Watermark", "Watermark", true);
    private final Option<Boolean> arrayList = new Option<Boolean>("ModuleList", "ModuleList", true);
    private final Option<Boolean> backgrounds = new Option<Boolean>("Backgrounds", "Backgrounds", true);
    private final Option<Boolean> keybinds = new Option<Boolean>("Keybinds", "Keybinds", false);
    private final Option<Boolean> notifications = new Option<Boolean>("Notifications", "Notifications", true);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 135.0, 40.0, 220.0, 5.0);
    private final Numbers<Double> radius = new Numbers<Double>("Radius", "Radius", 5.0, 0.0, 10.0, 1.0);

    public HUD() {
        super("HUD", Keyboard.KEY_H, ModuleType.Render,"Show " + Client.name + " HUD Screen");
        Chinese="HUD界面";
        this.addValues(watermark, arrayList, backgrounds, keybinds, notifications, alpha, radius);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text event) {
        if (!isInGame() || mc.currentScreen instanceof GuiMainMenu) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        if (Boolean.TRUE.equals(watermark.getValue())) {
            drawWatermark();
        }
        if (Boolean.TRUE.equals(arrayList.getValue())) {
            drawModuleList(width);
        }
        if (Boolean.TRUE.equals(notifications.getValue())) {
            NotificationManager.doRender(width, height);
        }
    }

    private void drawWatermark() {
        String title = Client.name;
        String stats = Client.version + " | " + Minecraft.getDebugFPS() + " FPS";
        int ping = getPing();
        if (ping >= 0) {
            stats += " | " + ping + "ms";
        }

        float x = 5.0f;
        float y = 5.0f;
        float paddingX = 8.0f;
        float titleWidth = FontLoaders.C18.getStringWidth(title);
        float statsWidth = FontLoaders.C14.getStringWidth(stats);
        float boxWidth = Math.max(titleWidth, statsWidth) + paddingX * 2.0f;
        float boxHeight = 28.0f;
        float round = radius.getValue().floatValue();
        int firstAccent = ColorUtils.rainbow(260, 18, 0);
        int secondAccent = ColorUtils.rainbow(260, 18, 8);
        int background = ColorUtils.applyAlpha(0xFF10131A, alpha.getValue().intValue());
        int border = ColorUtils.applyAlpha(ColorUtils.lighten(firstAccent, 0.2f), 115);

        if (Boolean.TRUE.equals(backgrounds.getValue())) {
            RenderUtil.drawSoftShadow(x, y, x + boxWidth, y + boxHeight, round, 0x90000000, 5, 3.0f);
            RenderUtil.drawRoundedBorderedRect(x, y, x + boxWidth, y + boxHeight, round, 1.0f, background, border);
            RenderUtil.drawHorizontalGradientRect(x + 5.0f, y + 4.0f, x + boxWidth - 5.0f, y + 5.5f,
                    ColorUtils.applyAlpha(firstAccent, 220), ColorUtils.applyAlpha(secondAccent, 220));
        }

        FontLoaders.C18.drawString(title, x + paddingX, y + 8.0f, 0xFFFFFFFF);
        FontLoaders.C14.drawString(stats, x + paddingX, y + 19.0f, 0xFFC8D0DA);
    }

    private void drawModuleList(int screenWidth) {
        List<Module> modules = new ArrayList<Module>(Client.instance.moduleManager.getEnabledModules());
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module o1, Module o2) {
                return FontLoaders.C18.getStringWidth(getDisplayName(o2)) - FontLoaders.C18.getStringWidth(getDisplayName(o1));
            }
        });

        float y = 5.0f;
        int index = 0;
        for (Module m : modules) {
            if (m == null || m == this || "ClickGUI".equalsIgnoreCase(m.getName())) {
                continue;
            }

            String displayName = getDisplayName(m);
            if (Boolean.TRUE.equals(keybinds.getValue()) && m.getKey() != Keyboard.KEY_NONE) {
                displayName += " [" + Keyboard.getKeyName(m.getKey()) + "]";
            }

            int moduleWidth = FontLoaders.C18.getStringWidth(displayName);
            float rowHeight = 14.0f;
            float boxWidth = moduleWidth + 12.0f;
            float x = screenWidth - boxWidth - 5.0f;
            int accent = ColorUtils.rainbow(220, 18, index);
            int accentDark = ColorUtils.darken(accent, 0.35f);

            if (Boolean.TRUE.equals(backgrounds.getValue())) {
                int background = ColorUtils.applyAlpha(0xFF10131A, alpha.getValue().intValue());
                int border = ColorUtils.applyAlpha(accent, 90);
                RenderUtil.drawSoftShadow(x, y, x + boxWidth, y + rowHeight, radius.getValue().floatValue(), 0x70000000, 4, 2.0f);
                RenderUtil.drawRoundedBorderedRect(x, y, x + boxWidth, y + rowHeight,
                        radius.getValue().floatValue(), 1.0f, background, border);
                RenderUtil.drawVerticalGradientRect(screenWidth - 7.0f, y + 2.0f, screenWidth - 5.5f, y + rowHeight - 2.0f,
                        ColorUtils.applyAlpha(accent, 230), ColorUtils.applyAlpha(accentDark, 230));
                FontLoaders.C18.drawString(displayName, x + 5.0f, y + 4.0f, 0xFFFFFFFF);
            } else {
                FontLoaders.C18.drawStringWithShadow(displayName, screenWidth - moduleWidth - 2.0f, y + 2.0f, accent);
            }

            y += rowHeight + 2.0f;
            index++;
        }
    }

    private static String getDisplayName(Module module) {
        return Client.CHINESE ? module.getChinese() : module.getName();
    }

    private int getPing() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return -1;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }
}
