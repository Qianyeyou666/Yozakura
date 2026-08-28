package gq.yozakura.bridge.modern;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ModernVisualRenderer {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFF5F0F5;
    private static final int TEXT_MUTED = 0xFFB8AEB8;
    private static final int PANEL = 0xFF08080D;
    private static final int PANEL_SOFT = 0xFF14111A;
    private static final int SAKURA = 0xFFFFB7D1;
    private static final int SAKURA_DARK = 0xFFFF80B3;
    private static final int SAKURA_DEEP = 0xFFE56B9D;
    private static final int CYAN = 0xFF70C1DC;

    private static final long TARGET_SCAN_INTERVAL_MS = 180L;
    private static final long MODULE_LIST_CACHE_MS = 80L;
    private static final double TARGET_RANGE = 12.0D;
    private static final double RENDER_RANGE = 128.0D;
    private static final String[] MODULE_NAMES = new String[]{
            "AntiBot", "AutoClicker", "AimAssist", "KillAura", "Backtrack", "Criticals", "WTap",
            "BlockHit", "FakeLag", "KnockbackDelay", "HitSelect", "Velocity", "BowAimBot", "Reach",
            "HitBoxes", "GhostHand", "Speed", "Sprint", "KeepSprint", "NoJumpDelay", "LongJump",
            "InvMove", "NoSlowDown", "Spider", "HUD", "TargetHUD", "TargetESP", "KillEffect",
            "KeyboardDisplay",
            "FullBright", "Health", "StorageESP", "Chams", "ESP", "WebClickGUI", "AutoTools",
            "InventoryManager", "ChestStealer", "LTap", "FastPlace", "Scaffold", "Clutch",
            "BridgeAssist", "AutoMLG", "MurderMystery", "FuckServer"
    };

    private static final long startTime = System.currentTimeMillis();
    private static long lastGuiFrame;
    private static long lastTargetFrame = System.currentTimeMillis();
    private static long lastTargetScanMs;
    private static Object cachedTarget;
    private static Object displayedTarget;
    private static float targetVisibility;
    private static float targetHealthAnim = 0.88f;
    private static float targetDamageAnim = 0.88f;
    private static float targetFlowerAnim = 0.88f;
    private static float targetSwitchPulse;
    private static long moduleListCacheAt;
    private static int moduleListCacheFontSize;
    private static List<String> moduleListCache = Collections.emptyList();
    private static final ArrayList<ModernRender3D.RenderBox> LEVEL_BOX_SCRATCH = new ArrayList<ModernRender3D.RenderBox>();
    private static final String[] KEYBOARD_KEYS = new String[]{"W", "A", "S", "D"};
    private static final int[][] KEYBOARD_POSITIONS = new int[][]{{1, 0}, {0, 1}, {1, 1}, {2, 1}};

    private ModernVisualRenderer() {
    }

    static void renderGui(Object event) {
        try {
            Object graphics = ModernForgeEventBridge.invoke(event, "getGuiGraphics");
            Object window = ModernForgeEventBridge.invoke(event, "getWindow");
            Object minecraft = ModernMinecraftAccess.minecraft();
            if (graphics == null || minecraft == null) {
                return;
            }
            int width = guiWidth(graphics, window);
            int height = guiHeight(graphics, window);
            Object fallbackFont = ModernMinecraftAccess.font(minecraft);
            if (fallbackFont == null) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now == lastGuiFrame) {
                return;
            }
            lastGuiFrame = now;
            ModernShaderRenderer.beginFrame();
            ModernHudEditor.beginFrame(minecraft, width, height);

            ModernRender2D render = new ModernRender2D(graphics, minecraft);
            if (ModernForgeEventBridge.enabled("HUD")) {
                renderHud(render, width, height, minecraft, now);
            }
            if (ModernForgeEventBridge.enabled("TargetHUD")) {
                renderTargetHud(render, width, height, minecraft, now);
            } else {
                clearTargetHud();
            }
            if (ModernForgeEventBridge.enabled("KeyboardDisplay")) {
                renderKeyboardDisplay(render, width, height);
            }
            if (ModernForgeEventBridge.enabled("Health")) {
                renderHealth(render, minecraft, width, height);
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern GUI render failed", throwable);
        }
    }

    static void renderLevel(Object event) {
        try {
            Object stage = ModernForgeEventBridge.invoke(event, "getStage");
            if (!isLevelRenderStage(stage)) {
                return;
            }
            if (!ModernForgeEventBridge.enabled("Chams") && !ModernForgeEventBridge.enabled("ESP")
                    && !ModernForgeEventBridge.enabled("TargetESP") && !ModernForgeEventBridge.enabled("Backtrack")
                    && !ModernForgeEventBridge.enabled("HitBoxes")) {
                return;
            }
            Object minecraft = ModernMinecraftAccess.minecraft();
            Object player = ModernMinecraftAccess.player(minecraft);
            Object level = ModernMinecraftAccess.level(minecraft);
            if (minecraft == null || player == null || level == null) {
                return;
            }
            List<ModernRender3D.RenderBox> boxes = collectLevelBoxes(minecraft, player, System.currentTimeMillis());
            if (!boxes.isEmpty()) {
                ModernRender3D.render(event, boxes);
            }
        } catch (Throwable throwable) {
            ModernForgeEventBridge.log("Modern level render failed", throwable);
        }
    }

    private static List<ModernRender3D.RenderBox> collectLevelBoxes(Object minecraft, Object player, long now) {
        ArrayList<ModernRender3D.RenderBox> boxes = LEVEL_BOX_SCRATCH;
        boxes.clear();
        boolean esp = ModernForgeEventBridge.enabled("ESP");
        boolean chams = ModernForgeEventBridge.enabled("Chams");
        boolean hitBoxes = ModernForgeEventBridge.enabled("HitBoxes");
        if (esp || chams || hitBoxes) {
            for (Object entity : ModernMinecraftAccess.livingEntities(minecraft)) {
                if (entity == null || entity == player) {
                    continue;
                }
                if (esp && isRenderable3DTarget(player, entity, "ESP")) {
                    addEntityBox(boxes, entity, ModernForgeEventBridge.number("ESP", "Expand", 0.03D),
                            espColor(entity, now), espFilled(), espThroughWalls(), 1.15f);
                }
                if (chams && isRenderable3DTarget(player, entity, "Chams")) {
                    addEntityBox(boxes, entity, ModernForgeEventBridge.number("Chams", "Expand", 0.04D),
                            chamsColor(entity, now), true,
                            ModernForgeEventBridge.bool("Chams", "Through Walls", true), 1.0f);
                }
                if (hitBoxes && isRenderable3DTarget(player, entity, "HitBoxes")) {
                    addEntityBox(boxes, entity, ModernForgeEventBridge.number("HitBoxes", "Expand", 0.18D),
                            withAlpha(CYAN, 135), false,
                            ModernForgeEventBridge.bool("HitBoxes", "Through Walls", false), 1.0f);
                }
            }
        }
        if (ModernForgeEventBridge.enabled("TargetESP")) {
            Object target = targetFor3D(minecraft, player, now);
            if (target != null && isRenderable3DTarget(player, target, "TargetESP")) {
                int color = hurtTime(target) > 0 ? 0xFFFF6270 : SAKURA_DARK;
                addEntityBox(boxes, target, 0.07D,
                        withAlpha(color, Math.round((float) ModernForgeEventBridge.number("TargetESP", "Alpha", 185.0D))),
                        "Box".equalsIgnoreCase(ModernForgeEventBridge.mode("TargetESP", "Mode", "Ring")),
                        ModernForgeEventBridge.bool("TargetESP", "Through Walls", true),
                        (float) ModernForgeEventBridge.number("TargetESP", "Line Width", 2.0D));
            }
        }
        if (ModernForgeEventBridge.enabled("Backtrack") && ModernForgeEventBridge.bool("Backtrack", "Render", true)) {
            addBacktrackBoxes(boxes, now);
        }
        return boxes;
    }

    private static boolean isLevelRenderStage(Object stage) {
        if (stage == null) {
            return true;
        }
        String name = String.valueOf(stage).toUpperCase(Locale.ROOT);
        return name.contains("AFTER_ENTITIES") || name.endsWith(".AFTER_ENTITIES");
    }

    private static void addEntityBox(List<ModernRender3D.RenderBox> boxes, Object entity, double expand,
                                     int color, boolean fill, boolean throughWalls, float lineWidth) {
        ModernRaycastBridge.Box box = ModernRaycastBridge.entityBox(entity, expand);
        if (box != null) {
            boxes.add(new ModernRender3D.RenderBox(box, color, lineWidth, fill, throughWalls));
        }
    }

    private static void addBacktrackBoxes(List<ModernRender3D.RenderBox> boxes, long now) {
        Map<Integer, ArrayDeque<ModernPacketBridge.TrackedBox>> history = ModernPacketBridge.backtrackHistory();
        if (history == null || history.isEmpty()) {
            return;
        }
        double expand = ModernForgeEventBridge.number("Backtrack", "Expand", 0.08D);
        double historyMs = Math.max(50.0D, ModernForgeEventBridge.number("Backtrack", "History MS", 180.0D));
        boolean trail = ModernForgeEventBridge.bool("Backtrack", "Trail", true);
        boolean throughWalls = ModernForgeEventBridge.bool("Backtrack", "Through Walls", false);
        int baseAlpha = Math.max(10, Math.min(255,
                Math.round((float) ModernForgeEventBridge.number("Backtrack", "Render Alpha", 82.0D))));
        int rendered = 0;
        outer:
        for (ArrayDeque<ModernPacketBridge.TrackedBox> trackedBoxes : history.values()) {
            if (trackedBoxes == null || trackedBoxes.isEmpty()) {
                continue;
            }
            int index = 0;
            int size = trackedBoxes.size();
            int step = trail ? Math.max(1, size / 5) : size;
            for (ModernPacketBridge.TrackedBox tracked : trackedBoxes) {
                index++;
                if (tracked == null || tracked.box == null || tracked.entity == null) {
                    continue;
                }
                if (!trail && index != size) {
                    continue;
                }
                if (trail && index != size && index % step != 0) {
                    continue;
                }
                float fade = trail ? clamp01(1.0f - (float) ((now - tracked.time) / historyMs)) : 1.0f;
                if (fade <= 0.02f) {
                    continue;
                }
                int alpha = Math.max(8, Math.round(baseAlpha * fade));
                boxes.add(new ModernRender3D.RenderBox(tracked.box.expand(expand),
                        withAlpha(CYAN, alpha), trail ? 1.0f : 1.4f, true, throughWalls));
                if (++rendered >= 96) {
                    break outer;
                }
            }
        }
    }

    private static Object targetFor3D(Object minecraft, Object player, long now) {
        Object target = ModernCombatBridge.combatTarget();
        if (isValidTarget(player, target, TARGET_RANGE * TARGET_RANGE)) {
            return target;
        }
        target = ModernRaycastBridge.hitResultEntity(minecraft);
        if (isValidTarget(player, target, TARGET_RANGE * TARGET_RANGE)) {
            return target;
        }
        return chooseTarget(minecraft, now);
    }

    private static boolean isRenderable3DTarget(Object player, Object entity, String module) {
        double range = "TargetESP".equals(module) ? TARGET_RANGE : RENDER_RANGE;
        if (!isValidTarget(player, entity, range * range)) {
            return false;
        }
        if (!ModernForgeEventBridge.bool(module, "Invisible", false) && isInvisible(entity)) {
            return false;
        }
        if (isPlayerType(entity)) {
            return ModernForgeEventBridge.bool(module, "Players", true);
        }
        if (isAnimalType(entity)) {
            return ModernForgeEventBridge.bool(module, "Animals", false);
        }
        return ModernForgeEventBridge.bool(module, "Mobs", false);
    }

    private static int espColor(Object entity, long now) {
        int alpha = Math.max(0, Math.min(255, Math.round((float) ModernForgeEventBridge.number("ESP", "Alpha", 160.0D))));
        if (ModernForgeEventBridge.bool("ESP", "Red On Damage", true) && hurtTime(entity) > 0) {
            return withAlpha(0xFFFF5E70, alpha);
        }
        if (ModernForgeEventBridge.bool("ESP", "Palette Rainbow", false)) {
            return withAlpha(rainbow(now, ModernRotationBridge.entityId(entity)), alpha);
        }
        int red = clampColor((int) ModernForgeEventBridge.number("ESP", "Red", 95.0D));
        int green = clampColor((int) ModernForgeEventBridge.number("ESP", "Green", 190.0D));
        int blue = clampColor((int) ModernForgeEventBridge.number("ESP", "Blue", 255.0D));
        return (alpha << 24) | red << 16 | green << 8 | blue;
    }

    private static int chamsColor(Object entity, long now) {
        int alpha = Math.max(0, Math.min(255, Math.round((float) ModernForgeEventBridge.number("Chams", "Alpha", 115.0D))));
        if (hurtTime(entity) > 0) {
            return withAlpha(0xFFFF5E70, alpha);
        }
        if (ModernForgeEventBridge.bool("Chams", "Rainbow", false)) {
            return withAlpha(rainbow(now, ModernRotationBridge.entityId(entity) * 3), alpha);
        }
        int red = clampColor((int) ModernForgeEventBridge.number("Chams", "Red", 88.0D));
        int green = clampColor((int) ModernForgeEventBridge.number("Chams", "Green", 190.0D));
        int blue = clampColor((int) ModernForgeEventBridge.number("Chams", "Blue", 255.0D));
        return (alpha << 24) | red << 16 | green << 8 | blue;
    }

    private static boolean espFilled() {
        String mode = ModernForgeEventBridge.mode("ESP", "Mode", "BOTH");
        return "FILLED".equalsIgnoreCase(mode) || "BOTH".equalsIgnoreCase(mode);
    }

    private static boolean espThroughWalls() {
        return ModernForgeEventBridge.bool("ESP", "Through Walls", false);
    }

    private static boolean isInvisible(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "isInvisible");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_5833_");
        }
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static int hurtTime(Object entity) {
        Object value = ModernForgeEventBridge.field(entity, "hurtTime");
        if (value == null) {
            value = ModernForgeEventBridge.field(entity, "f_20916_");
        }
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "getHurtTime");
        }
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean isPlayerType(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.player.Player");
    }

    private static boolean isAnimalType(Object entity) {
        return isInstance(entity, "net.minecraft.world.entity.animal.Animal")
                || isInstance(entity, "net.minecraft.world.entity.animal.WaterAnimal")
                || isInstance(entity, "net.minecraft.world.entity.ambient.AmbientCreature")
                || isInstance(entity, "net.minecraft.world.entity.npc.Villager");
    }

    private static boolean isInstance(Object object, String className) {
        Class<?> type = ModernRotationBridge.classForName(className);
        return type != null && object != null && type.isInstance(object);
    }

    private static int rainbow(long now, int offset) {
        float hue = ((now + offset * 73L) % 4200L) / 4200.0f;
        return java.awt.Color.HSBtoRGB(hue, 0.52f, 1.0f) | 0xFF000000;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void renderHud(ModernRender2D render, int width, int height, Object minecraft, long now) {
        if (ModernForgeEventBridge.bool("HUD", "Watermark", true)) {
            renderWatermark(render, minecraft, now, width, height);
        }
        if (ModernForgeEventBridge.bool("HUD", "ModuleList", true)) {
            renderModuleList(render, width, height);
        }
    }

    private static void drawPanel(ModernRender2D render, int x, int y, int width, int height, int radius,
                                  int fillColor, int borderColor, int glowColor, boolean accent,
                                  boolean frostedGlass) {
        if (frostedGlass) {
            render.glass(x, y, width, height, radius, fillColor, borderColor, glowColor, accent);
            return;
        }
        render.shadow(x, y, x + width, y + height, glowColor, 3);
        render.shadow(x, y, x + width, y + height, 0x18000000, 2);
        render.roundedBorder(x, y, width, height, radius, fillColor, borderColor);
        if (accent) {
            render.rect(x + 2, y + 1, x + width - 2, y + 2, 0x12FFFFFF);
        }
    }

    private static void renderWatermark(ModernRender2D render, Object minecraft, long now, int screenWidth, int screenHeight) {
        boolean frostedGlass = ModernForgeEventBridge.bool("HUD", "Frosted Glass", true);
        float scale = clamp((float) ModernForgeEventBridge.number("HUD", "Watermark Scale", 1.0D), 0.65f, 1.8f);
        ModernFontRenderer font = render.font(Math.max(10, Math.round(14.0f * scale)));
        int fps = fps(minecraft);
        String text = fps > 0 ? "Yozakura | " + fps + "fps" : "Yozakura | 1.20.1";
        int textWidth = render.textWidth(font, text);
        int width = Math.max(Math.round(132.0f * scale), textWidth + Math.round(34.0f * scale));
        int height = Math.round(19.0f * scale);
        ModernHudEditor.Element box = ModernHudEditor.place("hud.watermark", "HUD",
                "Watermark X", "Watermark Y", "Watermark Scale",
                6.0f, 6.0f, width, height, screenWidth, screenHeight, scale, 0.65f, 1.8f);
        int x = Math.round(box.x);
        int y = Math.round(box.y);
        float pulse = (float) ((Math.sin((now - startTime) / 640.0D) + 1.0D) * 0.5D);

        drawPanel(render, x, y, width, height, 7, withAlpha(PANEL, 166),
                withAlpha(SAKURA, 44), withAlpha(SAKURA, 28 + Math.round(18.0f * pulse)), true, frostedGlass);
        render.watermarkPetals(x, y, width, height, now);
        render.sakuraLogo(x + Math.round(10.5f * scale), y + Math.round(10.2f * scale),
                Math.max(2, Math.round(3.0f * scale)), 236);
        render.text(font, render.trim(text, width - Math.round(28.0f * scale), font), x + Math.round(21.0f * scale),
                render.centeredTextY(font, y, height), withAlpha(TEXT, 238), true);
        ModernHudEditor.drawHint(render, box, SAKURA);
    }

    private static void renderModuleList(final ModernRender2D render, int screenWidth, int screenHeight) {
        boolean frostedGlass = ModernForgeEventBridge.bool("HUD", "Frosted Glass", true);
        float scale = clamp((float) ModernForgeEventBridge.number("HUD", "ModuleList Scale", 1.0D), 0.65f, 1.8f);
        final ModernFontRenderer font = render.font(Math.max(10, Math.round(14.0f * scale)));
        List<String> enabled = enabledModules(render, font, Math.max(10, Math.round(14.0f * scale)));
        if (enabled.isEmpty()) {
            return;
        }

        int rowH = Math.max(14, Math.round(20.0f * scale));
        int rowGap = Math.max(1, Math.round(2.0f * scale));
        int maxRows = Math.min(22, enabled.size());
        int listW = 1;
        for (int i = 0; i < maxRows; i++) {
            listW = Math.max(listW, render.textWidth(font, enabled.get(i)) + Math.round(31.0f * scale));
        }
        int listH = maxRows * rowH + Math.max(0, maxRows - 1) * rowGap;
        ModernHudEditor.Element listBox = ModernHudEditor.place("hud.moduleList", "HUD",
                "ModuleList X", "ModuleList Y", "ModuleList Scale",
                screenWidth - 6.0f - listW, 6.0f, listW, listH, screenWidth, screenHeight, scale, 0.65f, 1.8f);
        int baseX = Math.round(listBox.x);
        int baseY = Math.round(listBox.y);
        int right = baseX + listW;
        for (int i = 0; i < maxRows; i++) {
            String name = enabled.get(i);
            int textWidth = render.textWidth(font, name);
            int rowW = textWidth + Math.round(31.0f * scale);
            int x = right - rowW;
            int top = baseY + i * (rowH + rowGap);
            int accent = moduleAccent(name, i);
            render.shadow(x, top, right, top + rowH, withAlpha(accent, 16), 2);
            drawPanel(render, x, top, rowW, rowH, 4, withAlpha(PANEL, 152),
                    withAlpha(accent, 28), withAlpha(accent, 18), false, frostedGlass);
            render.verticalGradient(right - Math.max(3, Math.round(3.0f * scale)), top + Math.round(4.0f * scale),
                    right - Math.max(1, Math.round(1.0f * scale)), top + rowH - Math.round(4.0f * scale),
                    withAlpha(accent, 190), withAlpha(SAKURA_DARK, 140));
            render.sakuraLogo(x + Math.round(9.0f * scale), top + rowH / 2, Math.max(3, Math.round(4.0f * scale)), 192);
            render.text(font, name, x + Math.round(18.0f * scale), render.centeredTextY(font, top, rowH),
                    withAlpha(TEXT, 238), true);
        }
        ModernHudEditor.drawHint(render, listBox, SAKURA);
    }

    private static void renderTargetHud(ModernRender2D render, int width, int height, Object minecraft, long now) {
        Object target = chooseTarget(minecraft, now);
        float factor = animationFactor(now);
        if (target != null) {
            if (target != displayedTarget) {
                displayedTarget = target;
                targetHealthAnim = healthRatio(target);
                targetDamageAnim = targetHealthAnim;
                targetFlowerAnim = targetHealthAnim;
                targetSwitchPulse = 1.0f;
            }
            targetVisibility += (1.0f - targetVisibility) * factor;
        } else {
            targetVisibility += (0.0f - targetVisibility) * factor;
            if (targetVisibility <= 0.018f) {
                displayedTarget = null;
                return;
            }
        }

        Object renderTarget = target == null ? displayedTarget : target;
        if (renderTarget == null) {
            return;
        }
        ModernFontRenderer nameFont = render.font(18);
        ModernFontRenderer smallFont = render.font(11);

        float ratio = healthRatio(renderTarget);
        float healthFactor = ratio < targetHealthAnim ? Math.min(1.0f, factor * 0.50f)
                : Math.min(1.0f, factor * 1.30f);
        float damageFactor = ratio < targetDamageAnim ? Math.min(1.0f, factor * 0.18f)
                : Math.min(1.0f, factor * 1.05f);
        targetHealthAnim += (ratio - targetHealthAnim) * healthFactor;
        targetDamageAnim += (ratio - targetDamageAnim) * damageFactor;
        targetFlowerAnim += (targetHealthAnim - targetFlowerAnim) * Math.min(1.0f, factor * 0.72f);
        targetSwitchPulse += (0.0f - targetSwitchPulse) * factor;

        boolean frostedGlass = ModernForgeEventBridge.bool("TargetHUD", "Frosted Glass", true);
        float scale = clamp((float) ModernForgeEventBridge.number("TargetHUD", "Scale", 1.0D), 0.5f, 2.0f);
        int boxW = Math.round(200.0f * scale);
        int boxH = Math.round(42.0f * scale);
        float defaultX = width / 2.0f + 22.0f * scale;
        float defaultY = height / 2.0f + (28.0f + (1.0f - targetVisibility) * 10.0f) * scale;
        ModernHudEditor.Element box = ModernHudEditor.place("hud.target", "TargetHUD",
                "X", "Y", "Scale", defaultX, defaultY, boxW, boxH, width, height, scale, 0.5f, 2.0f);
        int x = Math.round(box.x);
        int y = Math.round(box.y);
        float alpha = smoothStep(targetVisibility);
        float pulse = clamp01(targetSwitchPulse);

        int radius = Math.max(5, Math.round(8.0f * scale));
        drawPanel(render, x, y, boxW, boxH, radius,
                withAlpha(PANEL, Math.round((158.0f + pulse * 16.0f) * alpha)),
                withAlpha(SAKURA, Math.round((28.0f + pulse * 18.0f) * alpha)),
                withAlpha(SAKURA, Math.round((28.0f + pulse * 22.0f) * alpha)), true, frostedGlass);
        render.rect(x + Math.round(10.0f * scale), y + boxH - Math.round(8.0f * scale),
                x + Math.round(72.0f * scale), y + boxH - Math.round(5.0f * scale),
                withAlpha(SAKURA, Math.round(14.0f * alpha)));

        int avatar = Math.round(26.0f * scale);
        int avatarX = x + Math.round(14.0f * scale);
        int avatarY = y + Math.round(8.0f * scale);
        drawPanel(render, avatarX, avatarY, avatar, avatar, Math.max(4, Math.round(6.0f * scale)),
                withAlpha(PANEL_SOFT, Math.round(190.0f * alpha)),
                withAlpha(SAKURA, Math.round(62.0f * alpha)),
                withAlpha(SAKURA, Math.round(30.0f * alpha)), false, frostedGlass);
        render.sakuraMark(avatarX + avatar / 2, avatarY + avatar / 2,
                Math.max(3, Math.round(4.0f * scale)), Math.round(232.0f * alpha));

        int textX = x + Math.round(54.0f * scale);
        int nameY = y + Math.round(9.0f * scale);
        int smallY = y + Math.round(22.0f * scale);
        int right = x + boxW - Math.round(11.0f * scale);
        String rawName = entityName(renderTarget);
        String name = render.trim(rawName, Math.max(35, right - textX - 34), nameFont);
        String distance = String.format(Locale.ROOT, "%.1fm", distanceToPlayer(minecraft, renderTarget));
        render.textGlow(nameFont, name, textX, nameY, SAKURA, alpha * 0.46f);
        render.text(nameFont, name, textX, nameY, withAlpha(TEXT, Math.round(248.0f * alpha)), true);
        render.text(smallFont, distance, textX, smallY, withAlpha(TEXT_MUTED, Math.round(210.0f * alpha)), false);

        int hpText = Math.round(health(renderTarget));
        String hp = hpText + " HP";
        int hpWidth = render.textWidth(smallFont, hp);
        render.text(smallFont, hp, right - hpWidth, smallY, withAlpha(SAKURA, Math.round(218.0f * alpha)), false);
        drawTargetHealth(render, x, y, boxW, scale, alpha);
        ModernHudEditor.drawHint(render, box, SAKURA);
    }

    private static void drawTargetHealth(ModernRender2D render, int x, int y, int width, float scale, float alpha) {
        int barX = x + Math.round(54.0f * scale);
        int barY = y + Math.round(30.0f * scale);
        int barW = Math.round(110.0f * scale);
        int lineH = Math.max(1, Math.round(1.15f * scale));
        float health = clamp01(targetHealthAnim);
        float delayed = clamp01(Math.max(health, targetDamageAnim));
        render.rect(barX, barY, barX + barW, barY + lineH, withAlpha(0xFFFFD3E3, Math.round(34.0f * alpha)));
        render.rect(barX, barY + lineH, barX + barW, barY + lineH + 1, withAlpha(0xFF09090D, Math.round(72.0f * alpha)));
        if (delayed > health + 0.003f) {
            render.rect(barX, barY, barX + Math.round(barW * delayed), barY + lineH,
                    withAlpha(0xFFFF6F9A, Math.round(72.0f * alpha)));
        }
        int fillW = Math.round(barW * health);
        if (fillW > 0) {
            render.rect(barX, barY, barX + fillW, barY + lineH, withAlpha(SAKURA, Math.round(218.0f * alpha)));
            render.rect(barX, barY - 1, barX + fillW, barY, withAlpha(0xFFFFF3F8, Math.round(56.0f * alpha)));
        }
        if (health > 0.035f) {
            int marker = barX + Math.max(2, Math.min(barW - 2, Math.round(barW * clamp01(targetFlowerAnim))));
            render.sakuraFlower(marker, barY, Math.max(2, Math.round(3.0f * scale)), Math.round(210.0f * alpha));
        }
    }

    private static void renderKeyboardDisplay(ModernRender2D render, int width, int height) {
        float scale = clamp((float) ModernForgeEventBridge.number("KeyboardDisplay", "Scale", 1.0D), 0.6f, 1.8f);
        ModernFontRenderer font = render.font(Math.max(9, Math.round(13.0f * scale)));
        int keyW = Math.round(20.0f * scale);
        int keyH = Math.round(15.0f * scale);
        int stepX = Math.round(24.0f * scale);
        int stepY = Math.round(18.0f * scale);
        int totalW = stepX * 2 + keyW;
        int totalH = stepY + keyH;
        ModernHudEditor.Element box = ModernHudEditor.place("hud.keyboard", "KeyboardDisplay",
                "X", "Y", "Scale", 12.0f, height - 58.0f * scale, totalW, totalH,
                width, height, scale, 0.6f, 1.8f);
        int x = Math.round(box.x);
        int y = Math.round(box.y);
        for (int i = 0; i < KEYBOARD_KEYS.length; i++) {
            int px = x + KEYBOARD_POSITIONS[i][0] * stepX;
            int py = y + KEYBOARD_POSITIONS[i][1] * stepY;
            render.glass(px, py, keyW, keyH, Math.max(3, Math.round(4.0f * scale)),
                    withAlpha(PANEL, 145), withAlpha(SAKURA, 26), 0x36000000, false);
            render.centeredText(font, KEYBOARD_KEYS[i], px, py, keyW, keyH, withAlpha(TEXT, 235), true);
        }
        ModernHudEditor.drawHint(render, box, SAKURA);
    }

    private static void renderHealth(ModernRender2D render, Object minecraft, int width, int height) {
        Object player = ModernMinecraftAccess.player(minecraft);
        if (player == null) {
            return;
        }
        float scale = clamp((float) ModernForgeEventBridge.number("Health", "Scale", 1.0D), 0.65f, 2.0f);
        ModernFontRenderer font = render.font(Math.max(10, Math.round(14.0f * scale)));
        float ratio = healthRatio(player);
        int w = Math.round(96.0f * scale);
        int h = Math.round(18.0f * scale);
        ModernHudEditor.Element box = ModernHudEditor.place("hud.health", "Health",
                "X", "Y", "Scale", 12.0f, height - 82.0f * scale, w, h,
                width, height, scale, 0.65f, 2.0f);
        int x = Math.round(box.x);
        int y = Math.round(box.y);
        render.glass(x, y, w, h, Math.max(4, Math.round(6.0f * scale)), withAlpha(PANEL, 150),
                withAlpha(SAKURA, 32), 0x42000000, false);
        int barX = x + Math.round(7.0f * scale);
        int barY = y + Math.round(13.0f * scale);
        int lineH = Math.max(1, Math.round(2.0f * scale));
        render.rect(barX, barY, x + w - Math.round(7.0f * scale), barY + lineH, withAlpha(0xFFFFD3E3, 38));
        render.rect(barX, barY, barX + Math.round((w - 14.0f * scale) * ratio), barY + lineH, SAKURA_DARK);
        render.text(font, "HP " + Math.round(health(player)), x + 8,
                render.centeredTextY(font, y, Math.max(12, Math.round(14.0f * scale))), withAlpha(TEXT, 235), true);
        ModernHudEditor.drawHint(render, box, SAKURA);
    }

    private static List<String> enabledModules(final ModernRender2D render, final Object font, int fontSize) {
        long now = System.currentTimeMillis();
        if (fontSize == moduleListCacheFontSize && now - moduleListCacheAt <= MODULE_LIST_CACHE_MS) {
            return moduleListCache;
        }
        ArrayList<String> names = new ArrayList<String>();
        for (String module : MODULE_NAMES) {
            if (ModernForgeEventBridge.enabled(module)) {
                names.add(module);
            }
        }
        Collections.sort(names, new Comparator<String>() {
            @Override
            public int compare(String first, String second) {
                int width = render.textWidth(font, second) - render.textWidth(font, first);
                return width != 0 ? width : first.compareToIgnoreCase(second);
            }
        });
        moduleListCacheAt = now;
        moduleListCacheFontSize = fontSize;
        moduleListCache = names.isEmpty() ? Collections.<String>emptyList() : names;
        return moduleListCache;
    }

    private static Object chooseTarget(Object minecraft, long now) {
        Object player = ModernMinecraftAccess.player(minecraft);
        if (player == null) {
            cachedTarget = null;
            return null;
        }
        double rangeSq = TARGET_RANGE * TARGET_RANGE;
        if (isValidTarget(player, cachedTarget, rangeSq)) {
            return cachedTarget;
        }
        cachedTarget = null;
        if (now - lastTargetScanMs < TARGET_SCAN_INTERVAL_MS) {
            return null;
        }
        lastTargetScanMs = now;

        List<Object> entities = ModernMinecraftAccess.livingEntities(minecraft);
        Object best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object entity : entities) {
            if (!isValidTarget(player, entity, rangeSq)) {
                continue;
            }
            double distance = distanceSq(player, entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        cachedTarget = best;
        return best;
    }

    private static boolean isValidTarget(Object player, Object entity, double rangeSq) {
        return entity != null && entity != player && isEntityAlive(entity) && distanceSq(player, entity) <= rangeSq;
    }

    private static boolean isEntityAlive(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "isAlive");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_6084_");
        }
        if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
            return false;
        }
        return health(entity) > 0.0f;
    }

    private static float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastTargetFrame));
        lastTargetFrame = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 220.0D);
    }

    private static void clearTargetHud() {
        cachedTarget = null;
        displayedTarget = null;
        targetVisibility = 0.0f;
        targetHealthAnim = 0.88f;
        targetDamageAnim = 0.88f;
        targetFlowerAnim = 0.88f;
        targetSwitchPulse = 0.0f;
    }

    private static int guiWidth(Object graphics, Object window) {
        int width = intValue(ModernForgeEventBridge.invoke(graphics, "guiWidth"), -1);
        if (width > 0) {
            return width;
        }
        width = intValue(ModernForgeEventBridge.invoke(graphics, "m_280182_"), -1);
        if (width > 0) {
            return width;
        }
        width = intValue(ModernForgeEventBridge.invoke(window, "getGuiScaledWidth"), -1);
        if (width > 0) {
            return width;
        }
        return intValue(ModernForgeEventBridge.invoke(window, "m_85445_"), 854);
    }

    private static int guiHeight(Object graphics, Object window) {
        int height = intValue(ModernForgeEventBridge.invoke(graphics, "guiHeight"), -1);
        if (height > 0) {
            return height;
        }
        height = intValue(ModernForgeEventBridge.invoke(graphics, "m_280206_"), -1);
        if (height > 0) {
            return height;
        }
        height = intValue(ModernForgeEventBridge.invoke(window, "getGuiScaledHeight"), -1);
        if (height > 0) {
            return height;
        }
        return intValue(ModernForgeEventBridge.invoke(window, "m_85446_"), 480);
    }

    private static String entityName(Object entity) {
        Object component = ModernForgeEventBridge.invoke(entity, "getDisplayName");
        if (component == null) {
            component = ModernForgeEventBridge.invoke(entity, "m_5446_");
        }
        if (component == null) {
            component = ModernForgeEventBridge.invoke(entity, "getName");
        }
        if (component == null) {
            component = ModernForgeEventBridge.invoke(entity, "m_7755_");
        }
        String text = componentText(component);
        if (text == null || text.length() == 0) {
            text = "Target";
        }
        return cleanEntityName(text);
    }

    private static String componentText(Object component) {
        if (component == null) {
            return null;
        }
        if (component instanceof String) {
            return (String) component;
        }
        Object value = ModernForgeEventBridge.invoke(component, "getString");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(component, "m_130668_");
        }
        return value instanceof String ? (String) value : String.valueOf(component);
    }

    private static String cleanEntityName(String text) {
        String value = text;
        if (value.length() >= 2 && value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
            value = value.substring(1, value.length() - 1);
        }
        value = value.replace("literal{", "").replace("}", "");
        if (value.startsWith("translation{")) {
            int start = value.indexOf("key='");
            if (start >= 0) {
                start += 5;
                int end = value.indexOf('\'', start);
                if (end > start) {
                    value = formatTranslationKey(value.substring(start, end));
                }
            }
        }
        return value;
    }

    private static String formatTranslationKey(String key) {
        int dot = key == null ? -1 : key.lastIndexOf('.');
        String name = dot >= 0 ? key.substring(dot + 1) : key;
        if (name == null || name.length() == 0) {
            return "Target";
        }
        name = name.replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static float healthRatio(Object entity) {
        float max = Math.max(1.0f, maxHealth(entity));
        return clamp01(health(entity) / max);
    }

    private static float health(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getHealth");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_21223_");
        }
        return value instanceof Number ? ((Number) value).floatValue() : 0.0f;
    }

    private static float maxHealth(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getMaxHealth");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_21233_");
        }
        return value instanceof Number ? ((Number) value).floatValue() : 20.0f;
    }

    private static double distanceToPlayer(Object minecraft, Object entity) {
        Object player = ModernMinecraftAccess.player(minecraft);
        return player == null ? 0.0D : Math.sqrt(distanceSq(player, entity));
    }

    private static double distanceSq(Object first, Object second) {
        double dx = x(first) - x(second);
        double dy = y(first) - y(second);
        double dz = z(first) - z(second);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double x(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getX");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20185_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static double y(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getY");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20186_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static double z(Object entity) {
        Object value = ModernForgeEventBridge.invoke(entity, "getZ");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(entity, "m_20189_");
        }
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static int fps(Object minecraft) {
        Object value = ModernForgeEventBridge.invoke(minecraft, "getFps");
        if (value == null) {
            value = ModernForgeEventBridge.invoke(minecraft, "m_91090_");
        }
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static int moduleAccent(String name, int index) {
        if ("KillAura".equalsIgnoreCase(name) || "TargetHUD".equalsIgnoreCase(name)
                || "TargetESP".equalsIgnoreCase(name) || "ESP".equalsIgnoreCase(name)) {
            return SAKURA_DARK;
        }
        if ("Scaffold".equalsIgnoreCase(name) || "Speed".equalsIgnoreCase(name)
                || "Sprint".equalsIgnoreCase(name)) {
            return CYAN;
        }
        return index % 2 == 0 ? SAKURA : SAKURA_DEEP;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 255;
    }
}
