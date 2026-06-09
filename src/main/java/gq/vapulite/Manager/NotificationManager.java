package gq.vapulite.Manager;


import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.utils.Notification;

import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {
    private static final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();
    private static final int MAX_NOTIFICATIONS = 6;

    public static void doRender(float wid, float hei) {
            float startY = hei - 8;
            for (Notification notification : notifications) {
                if (notification == null)
                    continue;
                notification.draw(wid, startY);
                startY -= notification.getHeight() + 6;
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
