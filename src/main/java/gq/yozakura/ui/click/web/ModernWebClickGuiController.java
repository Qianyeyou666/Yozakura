package gq.yozakura.ui.click.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gq.yozakura.engine.render.ui.VisualPalette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ModernWebClickGuiController {
    private static final List<Category> CATEGORIES = Arrays.asList(
            new Category("Combat", "Combat"),
            new Category("Movement", "Movement"),
            new Category("Render", "Render"),
            new Category("Player", "Player"),
            new Category("World", "World"),
            new Category("Other", "Other"),
            new Category("Config", "Global")
    );
    private static final Map<String, ModernModule> MODULES = new LinkedHashMap<String, ModernModule>();

    static {
        add("AntiBot", "Combat", "Exclude server-side bot entities from combat targeting.")
                .mode("Mode", "Hypixel", "Hypixel", "Simple", "Advanced")
                .bool("Players Only", true);
        add("AutoClicker", "Combat", "Click automatically while attack is held.")
                .number("Min CPS", 8.0D, 1.0D, 20.0D, 1.0D)
                .number("Max CPS", 12.0D, 1.0D, 20.0D, 1.0D)
                .bool("Weapon Only", false);
        add("AimAssist", "Combat", "Legit aim assistance settings.")
                .mode("Mode", "Linear", "Linear", "Adaptive", "Bezier")
                .number("Speed", 45.0D, 1.0D, 100.0D, 1.0D)
                .number("Smooth", 18.0D, 2.0D, 60.0D, 1.0D)
                .number("FOV", 90.0D, 15.0D, 180.0D, 1.0D)
                .number("Range", 4.5D, 1.0D, 6.0D, 0.1D)
                .bool("Require Mouse Down", true)
                .bool("Vertical Aim", true)
                .bool("Weapon Only", false)
                .bool("Through Walls", false)
                .bool("Players", true)
                .bool("Mobs", false)
                .bool("Animals", false);
        add("KillAura", "Combat", "Combat automation settings.")
                .mode("Mode", "Single", "Single", "Switch")
                .mode("Sort", "Distance", "Distance", "Health", "Hurt Time", "FOV")
                .mode("Rotations", "Silent", "None", "Legit", "Silent", "Lock View")
                .number("Range", 3.2D, 2.5D, 6.0D, 0.01D)
                .number("FOV", 360.0D, 30.0D, 360.0D, 1.0D)
                .number("Min CPS", 14.0D, 1.0D, 20.0D, 1.0D)
                .number("Max CPS", 14.0D, 1.0D, 20.0D, 1.0D)
                .number("Hurt Time", 10.0D, 0.0D, 10.0D, 1.0D)
                .mode("AutoBlock", "None", "None", "RELEASE", "INTERACT", "SWITCH", "BLINK")
                .number("AutoBlock Aps", 10.0D, 1.0D, 20.0D, 1.0D)
                .mode("Move Fix", "None", "None", "Silent", "Strict")
                .bool("Weapon Only", false)
                .bool("Through Walls", false)
                .bool("Players", true)
                .bool("Mobs", false)
                .bool("Animals", false);
        add("Backtrack", "Combat", "Attack entities at recent historical positions.")
                .mode("Mode", "Hybrid", "Hybrid", "Packet", "Render")
                .number("Range", 3.6D, 3.0D, 6.0D, 0.1D)
                .number("History MS", 180.0D, 50.0D, 600.0D, 10.0D)
                .number("Packet Delay", 120.0D, 0.0D, 400.0D, 10.0D)
                .number("Expand", 0.08D, 0.0D, 1.0D, 0.01D)
                .bool("Attack Only", true)
                .bool("Players", true)
                .bool("Mobs", true)
                .bool("Animals", true)
                .bool("Through Walls", false)
                .bool("Render", true)
                .bool("Trail", true)
                .number("Render Alpha", 82.0D, 25.0D, 160.0D, 5.0D);
        add("Criticals", "Combat", "Force critical hit timing on attack.")
                .mode("Mode", "Packet", "Packet", "MiniJump", "NoGround")
                .bool("Weapon Only", true);
        add("WTap", "Combat", "Reset sprint after landing attacks.")
                .mode("Mode", "Silent", "Silent", "Legit")
                .number("Chance", 100.0D, 0.0D, 100.0D, 1.0D)
                .number("Wait Ticks", 0.0D, 0.0D, 5.0D, 1.0D)
                .number("Action Ticks", 1.0D, 1.0D, 5.0D, 1.0D);
        add("BlockHit", "Combat", "Automatically blockhit.")
                .mode("Mode", "Manual", "Manual", "Auto", "Legit")
                .number("Min Delay", 70.0D, 0.0D, 240.0D, 5.0D)
                .number("Max Delay", 90.0D, 0.0D, 320.0D, 5.0D)
                .number("Chance", 100.0D, 0.0D, 100.0D, 1.0D)
                .bool("Only Sword", true);
        add("FakeLag", "Combat", "Delay outgoing packets to simulate latency.")
                .number("Delay", 120.0D, 0.0D, 600.0D, 10.0D)
                .number("Pulse", 200.0D, 0.0D, 1000.0D, 10.0D)
                .bool("Combat Only", true)
                .bool("Only Moving", false)
                .bool("Release On Attack", true)
                .bool("Movement Only", true);
        add("KnockbackDelay", "Combat", "Delay movement after receiving knockback.")
                .number("Delay MS", 95.0D, 0.0D, 420.0D, 5.0D)
                .number("Jitter MS", 35.0D, 0.0D, 220.0D, 5.0D)
                .number("Chance", 100.0D, 0.0D, 100.0D, 1.0D);
        add("HitSelect", "Combat", "Select smarter attack timing for combat modules.")
                .mode("Mode", "Smart", "Smart", "HurtTime", "Combo")
                .number("Chance", 100.0D, 0.0D, 100.0D, 1.0D);
        add("Velocity", "Combat", "Attack slowdown or server-physics-compatible knockback handling.")
                .mode("Mode", "Reduce", "Attack", "Reduce");
        add("JumpReset", "Combat", "Jump reset after receiving velocity.")
                .bool("Fake Check", false)
                .bool("Force Forward", true)
                .number("Chance", 100.0D, 0.0D, 100.0D, 1.0D);
        add("BowAimBot", "Combat", "Auto aim targets while using a bow.")
                .number("Range", 35.0D, 5.0D, 80.0D, 1.0D)
                .bool("Predict", true);
        add("Reach", "Combat", "Extend attack ray distance.")
                .number("Min Reach", 3.2D, 3.0D, 6.0D, 0.1D)
                .number("Max Reach", 3.6D, 3.0D, 6.0D, 0.1D)
                .number("Expand", 0.08D, 0.0D, 1.0D, 0.01D)
                .bool("Random Reach", true)
                .bool("Weapon Only", false)
                .bool("Moving Only", false)
                .bool("Sprint Only", false)
                .bool("Through Blocks", false)
                .bool("Players", true)
                .bool("Mobs", true)
                .bool("Animals", true);
        add("HitBoxes", "Combat", "Expand entity hit detection.")
                .number("Expand", 0.1D, 0.0D, 1.0D, 0.01D)
                .number("Range", 3.2D, 3.0D, 6.0D, 0.1D)
                .bool("Weapon Only", false)
                .bool("Players", true)
                .bool("Mobs", true)
                .bool("Animals", true)
                .bool("Through Walls", false);
        add("GhostHand", "Combat", "Click through teammate or blocked targets.")
                .bool("Players", true)
                .bool("Blocks", false);

        add("Speed", "Movement", "Movement speed settings.")
                .mode("Mode", "Vanilla", "Vanilla", "Strafe", "Timer")
                .number("Speed", 1.0D, 0.1D, 3.0D, 0.05D);
        add("Sprint", "Movement", "Force sprint while moving.")
                .bool("Omni", true);
        add("KeepSprint", "Movement", "Keep sprint behavior after attacks.")
                .number("Slowdown", 60.0D, 0.0D, 100.0D, 1.0D);
        add("NoJumpDelay", "Movement", "Remove vanilla jump input cooldown.");
        add("LongJump", "Movement", "Boost jump distance while moving.")
                .number("Boost", 0.08D, 0.02D, 0.18D, 0.01D);
        add("InvMove", "Movement", "Allow movement while inventory screens are open.")
                .bool("Sneak", true)
                .bool("Click Delay", false);
        add("NoSlowDown", "Movement", "Reduce item-use movement slowdown.")
                .mode("Mode", "Vanilla", "Vanilla", "NCP", "Watchdog")
                .number("Speed", 1.0D, 0.2D, 1.0D, 0.05D);
        add("Spider", "Movement", "Climb while pushing into walls.");

        add("HUD", "Render", "2D HUD settings.")
                .bool("Watermark", true)
                .bool("ModuleList", true)
                .bool("Notifications", true)
                .bool("Frosted Glass", true)
                .mode("HUD Style", "YOZAKURA", "YOZAKURA", "SAKURA", "SIMPLE")
                .number("Alpha", 128.0D, 45.0D, 180.0D, 5.0D)
                .number("Watermark X", 6.0D, -1.0D, 4000.0D, 1.0D)
                .number("Watermark Y", 6.0D, -1.0D, 2400.0D, 1.0D)
                .number("Watermark Scale", 1.0D, 0.65D, 1.8D, 0.05D)
                .number("ModuleList X", -1.0D, -1.0D, 4000.0D, 1.0D)
                .number("ModuleList Y", 6.0D, -1.0D, 2400.0D, 1.0D)
                .number("ModuleList Scale", 1.0D, 0.65D, 1.8D, 0.05D);
        add("TargetHUD", "Render", "Show target information when aiming at an entity.")
                .bool("Frosted Glass", true)
                .mode("Style", "Yozakura", "Yozakura", "Sakura", "Simple")
                .number("X", -1.0D, -1.0D, 4000.0D, 1.0D)
                .number("Y", -1.0D, -1.0D, 2400.0D, 1.0D)
                .number("Scale", 1.0D, 0.5D, 2.0D, 0.05D);
        add("TargetESP", "Render", "Draw a shader based marker around the current target.")
                .mode("Mode", "Default", "Default", "Hud", "Scan", "Cosmic", "Aurora", "Sakura", "Night Bloom")
                .bool("Glow", true);
        add("KillEffect", "Render", "Play a visual effect when a target dies.")
                .mode("Mode", "Sakura", "Sakura", "Lightning", "Bloom")
                .number("Scale", 1.0D, 0.2D, 2.0D, 0.05D);
        add("KeyboardDisplay", "Render", "Show movement keys and mouse CPS on the HUD.")
                .bool("Movement", true)
                .bool("Mouse", true)
                .number("X", 12.0D, -1.0D, 4000.0D, 1.0D)
                .number("Y", -1.0D, -1.0D, 2400.0D, 1.0D)
                .number("Scale", 1.0D, 0.6D, 1.8D, 0.1D);
        add("FullBright", "Render", "Make dark areas brighter.")
                .mode("Mode", "Gamma", "Gamma", "Potion");
        add("Health", "Render", "Show your health on the screen.")
                .number("X", 12.0D, -1.0D, 4000.0D, 1.0D)
                .number("Y", -1.0D, -1.0D, 2400.0D, 1.0D)
                .number("Scale", 1.0D, 0.65D, 2.0D, 0.05D);
        add("StorageESP", "Render", "Render storage container ESP.")
                .bool("Chests", true)
                .bool("Ender Chests", true);
        add("Chams", "Render", "Render entities with colored chams.")
                .bool("Through Walls", true)
                .bool("Textured", false)
                .number("Alpha", 115.0D, 35.0D, 210.0D, 5.0D);
        add("ESP", "Render", "Draw entity boxes.")
                .mode("Mode", "BOTH", "BOX", "CORNER", "BOTH")
                .bool("Players", true)
                .bool("Mobs", false)
                .number("Alpha", 160.0D, 35.0D, 255.0D, 5.0D);
        add("WebClickGUI", "Render", "Browser based ClickGUI for the modern runtime.")
                .number("Port", 18989.0D, 1024.0D, 65535.0D, 1.0D)
                .mode("Palette", "NIGHT_BLOOM", "NIGHT_BLOOM", "SAKURA", "OCEAN", "GRAPHITE");

        add("AutoTools", "Player", "Switch correct tools when destroying blocks.");
        add("InventoryManager", "Player", "Manage inventory items.")
                .mode("Mode", "SPOOF", "SPOOF", "OPEN", "LEGIT")
                .number("Delay", 80.0D, 0.0D, 500.0D, 10.0D)
                .bool("Clean", true)
                .bool("Sort", true)
                .bool("Auto Armor", true);
        add("ChestStealer", "Player", "Take useful items from chests.")
                .number("Click Delay", 80.0D, 0.0D, 1000.0D, 10.0D)
                .number("Close Delay", 120.0D, 0.0D, 1000.0D, 10.0D)
                .bool("Name Check", true)
                .bool("Smart", true)
                .bool("Auto Close", true);
        add("LTap", "Player", "Send configured chat messages from the player module.");

        add("FastPlace", "World", "Make block placement faster.")
                .number("Delay", 0.0D, 0.0D, 4.0D, 1.0D);
        add("Scaffold", "World", "Block placement settings.")
                .mode("rotations", "Prediction", "None", "Vanilla", "BackWards", "Strafe", "Test", "Prediction")
                .mode("move-fix", "SILENT", "NONE", "SILENT")
                .mode("sprint", "NONE", "NONE", "VANILLA")
                .mode("tower", "NONE", "NONE", "VANILLA", "EXTRA", "TELLY")
                .mode("keep-y", "NONE", "NONE", "VANILLA", "EXTRA", "TELLY")
                .bool("Safe Walk", true)
                .number("Delay", 0.0D, 0.0D, 6.0D, 1.0D);
        add("Clutch", "World", "Place a block under you while falling.")
                .mode("Mode", "SMART", "SMART", "SIMPLE")
                .number("Fall Distance", 0.8D, 0.0D, 8.0D, 0.1D)
                .bool("Auto Place", true)
                .bool("Auto Swap", true);
        add("BridgeAssist", "World", "Assist edge sneaking while bridging.")
                .number("Edge Distance", 0.25D, 0.0D, 0.5D, 0.01D)
                .bool("Only Blocks", true);
        add("AutoMLG", "World", "Auto use a bucket when you fall.")
                .mode("Mode", "Water", "Water", "Packet")
                .number("Fall Distance", 3.0D, 0.0D, 20.0D, 0.5D);
        add("MurderMystery", "World", "Detect murderers in Murder Mystery.")
                .bool("Announce", true)
                .bool("Render", true);
        add("FuckServer", "World", "Packet stress test module.")
                .mode("Mode", "Small", "Small", "Burst")
                .number("Packets", 20.0D, 1.0D, 200.0D, 1.0D);

        add("CopyName", "Config", "Copy your name for party commands.");
        add("LoadConfig", "Config", "Load saved client configuration.");
        add("SaveConfig", "Config", "Save current client configuration.");
        add("Uninject", "Config", "Disable modern runtime state.");
    }

    private ModernWebClickGuiController() {
    }

    static String stateJson(int port) {
        int enabled = 0;
        for (ModernModule module : MODULES.values()) {
            if (module.state) {
                enabled++;
            }
        }

        StringBuilder builder = new StringBuilder(32768);
        builder.append('{');
        appendProperty(builder, "ok", true);
        builder.append(',');
        appendProperty(builder, "name", "Yozakura");
        builder.append(',');
        appendProperty(builder, "version", "1.20.1-modern");
        builder.append(',');
        appendProperty(builder, "username", "Modern Forge");
        builder.append(',');
        appendProperty(builder, "port", port);
        builder.append(',');
        appendProperty(builder, "enabledCount", enabled);
        builder.append(',');
        appendProperty(builder, "moduleCount", MODULES.size());
        builder.append(',');
        appendPalette(builder);
        builder.append(',');
        builder.append("\"categories\":[");
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendCategory(builder, CATEGORIES.get(i));
        }
        builder.append("],\"modules\":[");
        ArrayList<ModernModule> modules = new ArrayList<ModernModule>(MODULES.values());
        modules.sort(new Comparator<ModernModule>() {
            @Override
            public int compare(ModernModule first, ModernModule second) {
                int category = first.category.compareToIgnoreCase(second.category);
                return category != 0 ? category : first.name.compareToIgnoreCase(second.name);
            }
        });
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendModule(builder, modules.get(i));
        }
        builder.append("]}");
        return builder.toString();
    }

    private static void appendPalette(StringBuilder builder) {
        ModernValue value = value("WebClickGUI", "Palette");
        String name = value == null ? "NIGHT_BLOOM" : String.valueOf(value.current);
        VisualPalette palette = palette(name);
        builder.append("\"palette\":{");
        appendProperty(builder, "name", name);
        builder.append(',');
        appendProperty(builder, "canvas", cssColor(palette.getCanvas()));
        builder.append(',');
        appendProperty(builder, "surface", cssColor(palette.getSurface()));
        builder.append(',');
        appendProperty(builder, "raised", cssColor(palette.getSurfaceRaised()));
        builder.append(',');
        appendProperty(builder, "overlay", cssColor(palette.getSurfaceOverlay()));
        builder.append(',');
        appendProperty(builder, "text", cssColor(palette.getTextPrimary()));
        builder.append(',');
        appendProperty(builder, "muted", cssColor(palette.getTextSecondary()));
        builder.append(',');
        appendProperty(builder, "dim", cssColor(palette.getTextDisabled()));
        builder.append(',');
        appendProperty(builder, "line", cssColor(palette.getBorderSubtle()));
        builder.append(',');
        appendProperty(builder, "focus", cssColor(palette.getBorderFocus()));
        builder.append(',');
        appendProperty(builder, "accent", cssColor(palette.getAccentPrimary()));
        builder.append(',');
        appendProperty(builder, "accentSoft", cssColor(palette.getAccentSoft()));
        builder.append(',');
        appendProperty(builder, "accentAlt", cssColor(palette.getAccentAlt()));
        builder.append(',');
        appendProperty(builder, "success", cssColor(palette.getSuccess()));
        builder.append(',');
        appendProperty(builder, "shadow", cssColor(palette.getShadow()));
        builder.append('}');
    }

    private static VisualPalette palette(String name) {
        if ("SAKURA".equals(name)) {
            return VisualPalette.sakura();
        }
        if ("OCEAN".equals(name)) {
            return VisualPalette.ocean();
        }
        if ("GRAPHITE".equals(name)) {
            return VisualPalette.graphite();
        }
        return VisualPalette.nightBloom();
    }

    private static String cssColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }

    static void toggleModule(String body) {
        JsonObject json = parse(body);
        ModernModule module = MODULES.get(normalize(getString(json, "module", "")));
        if (module == null) {
            return;
        }
        module.state = json.has("state") ? json.get("state").getAsBoolean() : !module.state;
    }

    static void setValue(String body) {
        JsonObject json = parse(body);
        ModernModule module = MODULES.get(normalize(getString(json, "module", "")));
        if (module == null) {
            return;
        }
        ModernValue value = module.value(getString(json, "value", ""));
        if (value == null || !json.has("next")) {
            return;
        }
        value.apply(json.get("next"));
    }

    static void setKey(String body) {
        JsonObject json = parse(body);
        ModernModule module = MODULES.get(normalize(getString(json, "module", "")));
        if (module != null) {
            module.keyName = getString(json, "keyName", "None");
        }
    }

    public static boolean isEnabled(String moduleName) {
        ModernModule module = MODULES.get(normalize(moduleName));
        return module != null && module.state;
    }

    public static double numberValue(String moduleName, String valueName, double fallback) {
        ModernValue value = value(moduleName, valueName);
        if (value == null || !(value.current instanceof Number)) {
            return fallback;
        }
        return ((Number) value.current).doubleValue();
    }

    public static boolean booleanValue(String moduleName, String valueName, boolean fallback) {
        ModernValue value = value(moduleName, valueName);
        if (value == null || !(value.current instanceof Boolean)) {
            return fallback;
        }
        return ((Boolean) value.current).booleanValue();
    }

    public static String modeValue(String moduleName, String valueName, String fallback) {
        ModernValue value = value(moduleName, valueName);
        if (value == null || value.current == null) {
            return fallback;
        }
        return String.valueOf(value.current);
    }

    public static void setNumberValue(String moduleName, String valueName, double next) {
        ModernModule module = MODULES.get(normalize(moduleName));
        ModernValue value = module == null ? null : module.value(valueName);
        if (value == null || !"number".equals(value.type)) {
            return;
        }
        value.setNumber(next);
    }

    private static ModernValue value(String moduleName, String valueName) {
        ModernModule module = MODULES.get(normalize(moduleName));
        return module == null ? null : module.value(valueName);
    }

    private static ModernModule add(String name, String category, String description) {
        ModernModule module = new ModernModule(name, categoryId(category), description);
        MODULES.put(normalize(name), module);
        return module;
    }

    private static String categoryId(String category) {
        for (Category current : CATEGORIES) {
            if (current.id.equalsIgnoreCase(category) || current.name.equalsIgnoreCase(category)) {
                return current.id;
            }
        }
        return category == null ? "Other" : category;
    }

    private static void appendCategory(StringBuilder builder, Category category) {
        int count = 0;
        int enabled = 0;
        for (ModernModule module : MODULES.values()) {
            if (category.id.equals(module.category)) {
                count++;
                if (module.state) {
                    enabled++;
                }
            }
        }
        builder.append('{');
        appendProperty(builder, "id", category.id);
        builder.append(',');
        appendProperty(builder, "name", category.name);
        builder.append(',');
        appendProperty(builder, "displayName", category.name);
        builder.append(',');
        appendProperty(builder, "count", count);
        builder.append(',');
        appendProperty(builder, "enabled", enabled);
        builder.append('}');
    }

    private static void appendModule(StringBuilder builder, ModernModule module) {
        builder.append('{');
        appendProperty(builder, "name", module.name);
        builder.append(',');
        appendProperty(builder, "displayName", module.name);
        builder.append(',');
        appendProperty(builder, "description", module.description);
        builder.append(',');
        appendProperty(builder, "category", module.category);
        builder.append(',');
        appendProperty(builder, "categoryName", displayCategory(module.category));
        builder.append(',');
        appendProperty(builder, "state", module.state);
        builder.append(',');
        appendProperty(builder, "key", 0);
        builder.append(',');
        appendProperty(builder, "keyName", module.keyName);
        builder.append(',');
        builder.append("\"values\":[");
        for (int i = 0; i < module.values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            module.values.get(i).appendJson(builder);
        }
        builder.append("]}");
    }

    private static String displayCategory(String id) {
        for (Category category : CATEGORIES) {
            if (category.id.equals(id)) {
                return category.name;
            }
        }
        return id;
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement element = new JsonParser().parse(text == null ? "{}" : text);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Throwable ignored) {
            return new JsonObject();
        }
    }

    private static String getString(JsonObject json, String name, String fallback) {
        if (json == null || !json.has(name) || json.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(name).getAsString();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static void appendProperty(StringBuilder builder, String name, String value) {
        appendString(builder, name);
        builder.append(':');
        appendString(builder, value);
    }

    private static void appendProperty(StringBuilder builder, String name, boolean value) {
        appendString(builder, name);
        builder.append(':').append(value);
    }

    private static void appendProperty(StringBuilder builder, String name, int value) {
        appendString(builder, name);
        builder.append(':').append(value);
    }

    private static void appendProperty(StringBuilder builder, String name, double value) {
        appendString(builder, name);
        builder.append(':').append(String.format(Locale.ROOT, "%.6f", value));
    }

    private static void appendString(StringBuilder builder, String value) {
        builder.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        builder.append("\\\"");
                        break;
                    case '\\':
                        builder.append("\\\\");
                        break;
                    case '\n':
                        builder.append("\\n");
                        break;
                    case '\r':
                        builder.append("\\r");
                        break;
                    case '\t':
                        builder.append("\\t");
                        break;
                    default:
                        if (c < 32) {
                            builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            builder.append(c);
                        }
                }
            }
        }
        builder.append('"');
    }

    private static final class Category {
        final String id;
        final String name;

        Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class ModernModule {
        final String name;
        final String category;
        final String description;
        final List<ModernValue> values = new ArrayList<ModernValue>();
        boolean state;
        String keyName = "None";

        ModernModule(String name, String category, String description) {
            this.name = name;
            this.category = category;
            this.description = description;
        }

        ModernModule bool(String name, boolean current) {
            values.add(ModernValue.bool(name, current));
            return this;
        }

        ModernModule number(String name, double current, double min, double max, double step) {
            values.add(ModernValue.number(name, current, min, max, step));
            return this;
        }

        ModernModule mode(String name, String current, String... options) {
            values.add(ModernValue.mode(name, current, options));
            return this;
        }

        ModernValue value(String name) {
            String target = normalize(name);
            for (ModernValue value : values) {
                if (normalize(value.name).equals(target)) {
                    return value;
                }
            }
            return null;
        }
    }

    private static final class ModernValue {
        final String type;
        final String name;
        final List<String> options;
        Object current;
        double min;
        double max;
        double step;

        private ModernValue(String type, String name, Object current, List<String> options) {
            this.type = type;
            this.name = name;
            this.current = current;
            this.options = options;
        }

        static ModernValue bool(String name, boolean current) {
            return new ModernValue("boolean", name, Boolean.valueOf(current), null);
        }

        static ModernValue number(String name, double current, double min, double max, double step) {
            ModernValue value = new ModernValue("number", name, Double.valueOf(current), null);
            value.min = min;
            value.max = max;
            value.step = step;
            return value;
        }

        static ModernValue mode(String name, String current, String... options) {
            return new ModernValue("mode", name, current, Arrays.asList(options));
        }

        void apply(JsonElement next) {
            try {
                if ("boolean".equals(type)) {
                    current = Boolean.valueOf(next.getAsBoolean());
                } else if ("number".equals(type)) {
                    setNumber(next.getAsDouble());
                } else if ("mode".equals(type)) {
                    String mode = next.getAsString();
                    if (options.contains(mode)) {
                        current = mode;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        void setNumber(double number) {
            if (!"number".equals(type)) {
                return;
            }
            double clamped = Math.max(min, Math.min(max, number));
            if (step > 0.0D) {
                clamped = Math.round(clamped / step) * step;
            }
            current = Double.valueOf(Math.max(min, Math.min(max, Math.round(clamped * 100.0D) / 100.0D)));
        }

        void appendJson(StringBuilder builder) {
            builder.append('{');
            appendProperty(builder, "name", name);
            builder.append(',');
            appendProperty(builder, "displayName", name);
            builder.append(',');
            appendProperty(builder, "type", type);
            builder.append(',');
            if ("boolean".equals(type)) {
                appendProperty(builder, "current", Boolean.TRUE.equals(current));
            } else if ("number".equals(type)) {
                appendProperty(builder, "current", ((Number) current).doubleValue());
                builder.append(',');
                appendProperty(builder, "min", min);
                builder.append(',');
                appendProperty(builder, "max", max);
                builder.append(',');
                appendProperty(builder, "step", step);
            } else if ("mode".equals(type)) {
                appendProperty(builder, "current", String.valueOf(current));
                builder.append(',');
                builder.append("\"options\":[");
                for (int i = 0; i < options.size(); i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    appendString(builder, options.get(i));
                }
                builder.append(']');
            }
            builder.append('}');
        }
    }
}
