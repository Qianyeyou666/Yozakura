package gq.yozakura.manager;


import gq.yozakura.module.Module;
import gq.yozakura.module.render.HUD;
import gq.yozakura.module.render.NightBloomHudDockRenderer;
import gq.yozakura.util.notification.Notification;
import gq.yozakura.util.render.HudDrag;
import gq.yozakura.value.Numbers;
import net.minecraft.client.gui.ScaledResolution;

import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {
    private static final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();
    private static final int MAX_NOTIFICATIONS = 6;

    public static void doRender(float wid, float hei) {
        doRender(wid, hei, null, null);
    }

    public static void doRender(float wid, float hei, Numbers<Double> xPosition, Numbers<Double> yPosition) {
        if (notifications.isEmpty()) {
            return;
        }
        float stackWidth = 0.0F;
        float stackHeight = 0.0F;
        int visible = 0;
        for (Notification notification : notifications) {
            if (notification == null) {
                continue;
            }
            stackWidth = Math.max(stackWidth, notification.getWidth());
            if (visible++ > 0) {
                stackHeight += 6.0F;
            }
            stackHeight += notification.getHeight();
        }
        if (visible == 0) {
            notifications.removeIf(Notification::shouldDelete);
            return;
        }

        float defaultX = wid - stackWidth - 9.0F;
        float defaultY = hei - stackHeight - 8.0F;
        float drawX = defaultX;
        float drawY = defaultY;
        boolean nightBloom = HUD.getActiveStyle() == HUD.HudStyle.NIGHT_BLOOM;
        if (nightBloom) {
            ScaledResolution resolution = new ScaledResolution(Notification.mc);
            float[] position = HudDrag.updateDocked("hud_notifications", xPosition, yPosition, null,
                    defaultX, defaultY, stackWidth, stackHeight, 4.0F, resolution);
            drawX = position[0];
            drawY = position[1];
            if (NightBloomHudDockRenderer.hasLink("hud_notifications")) {
                NightBloomHudDockRenderer.drawPanel("hud_notifications", drawX, drawY,
                        stackWidth, stackHeight, 4.0F, 1.0F, 0xDC16161A);
            }
        }

        float startY = drawY + stackHeight;
        float syntheticWidth = drawX + stackWidth + 9.0F;
        for (Notification notification : notifications) {
            if (notification == null) {
                continue;
            }
            notification.draw(syntheticWidth, startY);
            startY -= notification.getHeight() + 6.0F;
        }
        if (nightBloom) {
            HudDrag.drawDockHint("hud_notifications", drawX, drawY, stackWidth, stackHeight, 4.0F);
        }
        notifications.removeIf(Notification::shouldDelete);
    }

    public static void show(String title, String message, int type) {
        push(new Notification(title, message, type, 2500L));
    }

    public static void show(String title, String message, int type, long stayTime) {
        push(new Notification(title, message, type, stayTime));
    }

    public static void show(String title, String message, Module module) {
        push(new Notification(title, message, Notification.MODULE, 2500L, module));
    }

    private static void push(Notification notification) {
        notifications.add(notification);
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.remove(0);
        }
    }
}
