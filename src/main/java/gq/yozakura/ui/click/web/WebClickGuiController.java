package gq.yozakura.ui.click.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gq.yozakura.core.Client;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.render.ClickGUI;
import gq.yozakura.engine.render.ui.VisualPalette;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class WebClickGuiController {
    private WebClickGuiController() {
    }

    static String stateJson(int port) {
        StringBuilder builder = new StringBuilder(32768);
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module != null && module.getState()) {
                enabled++;
            }
        }

        builder.append('{');
        appendProperty(builder, "ok", true);
        builder.append(',');
        appendProperty(builder, "name", Client.name);
        builder.append(',');
        appendProperty(builder, "version", Client.version);
        builder.append(',');
        appendProperty(builder, "username", Client.username);
        builder.append(',');
        appendProperty(builder, "port", port);
        builder.append(',');
        appendProperty(builder, "enabledCount", enabled);
        builder.append(',');
        appendProperty(builder, "moduleCount", ModuleManager.getModules().size());
        builder.append(',');
        appendPalette(builder);
        builder.append(',');
        builder.append("\"categories\":[");
        ModuleType[] types = ModuleType.values();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendCategory(builder, types[i]);
        }
        builder.append("],");
        builder.append("\"modules\":[");
        ArrayList<Module> modules = new ArrayList<Module>(ModuleManager.getModules());
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module first, Module second) {
                String leftCategory = first == null || first.getCategory() == null ? "" : first.getCategory().toString();
                String rightCategory = second == null || second.getCategory() == null ? "" : second.getCategory().toString();
                int category = leftCategory.compareToIgnoreCase(rightCategory);
                if (category != 0) {
                    return category;
                }
                return nameOf(first).compareToIgnoreCase(nameOf(second));
            }
        });
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendModule(builder, modules.get(i));
        }
        builder.append(']');
        builder.append('}');
        return builder.toString();
    }

    private static void appendPalette(StringBuilder builder) {
        VisualPalette palette = ClickGUI.currentPalette();
        builder.append("\"palette\":{");
        appendProperty(builder, "name", ClickGUI.palette.getModeAsString());
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

    private static String cssColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }

    static void toggleModule(String body) {
        JsonObject json = parse(body);
        String moduleName = getString(json, "module", "");
        Module module = ModuleManager.getModule(moduleName);
        if (module == null) {
            return;
        }
        if (json.has("state")) {
            module.setState(json.get("state").getAsBoolean());
        } else {
            module.toggle();
        }
    }

    static void setValue(String body) {
        JsonObject json = parse(body);
        Module module = ModuleManager.getModule(getString(json, "module", ""));
        if (module == null) {
            return;
        }
        Value value = findValue(module, getString(json, "value", ""));
        if (value == null) {
            return;
        }
        applyValue(module, value, json.get("next"));
    }

    static void setKey(String body) {
        JsonObject json = parse(body);
        Module module = ModuleManager.getModule(getString(json, "module", ""));
        if (module == null) {
            return;
        }
        if (json.has("keyName")) {
            String keyName = getString(json, "keyName", "NONE").toUpperCase(Locale.ROOT);
            module.setKey("NONE".equals(keyName) ? Keyboard.KEY_NONE : Keyboard.getKeyIndex(keyName));
        } else {
            module.setKey(json.has("key") ? json.get("key").getAsInt() : Keyboard.KEY_NONE);
        }
    }

    private static void appendCategory(StringBuilder builder, ModuleType type) {
        int count = 0;
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module != null && module.getCategory() == type) {
                count++;
                if (module.getState()) {
                    enabled++;
                }
            }
        }
        builder.append('{');
        appendProperty(builder, "id", type.name());
        builder.append(',');
        appendProperty(builder, "name", type.toString());
        builder.append(',');
        appendProperty(builder, "displayName", type.getName());
        builder.append(',');
        appendProperty(builder, "count", count);
        builder.append(',');
        appendProperty(builder, "enabled", enabled);
        builder.append('}');
    }

    private static void appendModule(StringBuilder builder, Module module) {
        builder.append('{');
        appendProperty(builder, "name", nameOf(module));
        builder.append(',');
        appendProperty(builder, "displayName", displayNameOf(module));
        builder.append(',');
        appendProperty(builder, "description", module == null ? "" : module.getDescription());
        builder.append(',');
        appendProperty(builder, "category", module == null || module.getCategory() == null ? "" : module.getCategory().name());
        builder.append(',');
        appendProperty(builder, "categoryName", module == null || module.getCategory() == null ? "" : module.getCategory().getName());
        builder.append(',');
        appendProperty(builder, "state", module != null && module.getState());
        builder.append(',');
        appendProperty(builder, "key", module == null ? Keyboard.KEY_NONE : module.getKey());
        builder.append(',');
        appendProperty(builder, "keyName", keyName(module == null ? Keyboard.KEY_NONE : module.getKey()));
        builder.append(',');
        builder.append("\"values\":[");
        if (module != null) {
            List<Value> values = module.getValues();
            boolean first = true;
            for (Value value : values) {
                if (value == null || !value.isVisible()) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendValue(builder, value);
            }
        }
        builder.append(']');
        builder.append('}');
    }

    private static void appendValue(StringBuilder builder, Value value) {
        builder.append('{');
        appendProperty(builder, "name", value.getName());
        builder.append(',');
        appendProperty(builder, "displayName", value.getDisplayName());
        builder.append(',');
        if (value instanceof ModeProperty) {
            ModeProperty mode = (ModeProperty) value;
            appendProperty(builder, "type", "mode");
            builder.append(',');
            appendProperty(builder, "current", mode.getModeString());
            builder.append(',');
            appendProperty(builder, "index", mode.getValue());
            builder.append(',');
            builder.append("\"options\":[");
            String[] modes = mode.getModes();
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                appendString(builder, modes[i]);
            }
            builder.append(']');
        } else if (value instanceof Mode) {
            Mode mode = (Mode) value;
            appendProperty(builder, "type", "mode");
            builder.append(',');
            appendProperty(builder, "current", mode.getModeAsString());
            builder.append(',');
            appendProperty(builder, "index", modeIndex(mode));
            builder.append(',');
            builder.append("\"options\":[");
            Enum[] modes = mode.getModes();
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                appendString(builder, modes[i].name());
            }
            builder.append(']');
        } else if (value instanceof Option && value.getValue() instanceof Boolean) {
            appendProperty(builder, "type", "boolean");
            builder.append(',');
            appendProperty(builder, "current", Boolean.TRUE.equals(value.getValue()));
        } else if (value instanceof Numbers && value.getValue() instanceof Number) {
            Numbers number = (Numbers) value;
            appendProperty(builder, "type", "number");
            builder.append(',');
            appendProperty(builder, "current", ((Number) value.getValue()).doubleValue());
            builder.append(',');
            appendProperty(builder, "min", number.getMinimum() == null ? 0.0D : number.getMinimum().doubleValue());
            builder.append(',');
            appendProperty(builder, "max", number.getMaximum() == null ? 1.0D : number.getMaximum().doubleValue());
            builder.append(',');
            appendProperty(builder, "step", number.getIncrement() == null ? 1.0D : number.getIncrement().doubleValue());
            builder.append(',');
            appendProperty(builder, "integer", isIntegerNumber(number));
        } else {
            appendProperty(builder, "type", "text");
            builder.append(',');
            appendProperty(builder, "current", value.getValue() == null ? "" : String.valueOf(value.getValue()));
        }
        builder.append('}');
    }

    private static void applyValue(Module module, Value value, JsonElement next) {
        if (next == null || next.isJsonNull()) {
            return;
        }
        try {
            if (value instanceof ModeProperty) {
                ModeProperty mode = (ModeProperty) value;
                if (next.isJsonPrimitive() && next.getAsJsonPrimitive().isNumber()) {
                    mode.setNumberValue(next.getAsDouble());
                } else {
                    mode.setMode(next.getAsString());
                }
            } else if (value instanceof Mode) {
                ((Mode) value).setMode(next.getAsString());
            } else if (value instanceof Option && value.getValue() instanceof Boolean) {
                value.setValue(next.getAsBoolean());
            } else if (value instanceof Numbers) {
                ((Numbers) value).setNumberValue(next.getAsDouble());
            }
            try {
                if (module instanceof gq.yozakura.module.runtime.Module) {
                    ((gq.yozakura.module.runtime.Module) module).verifyValue(value.getName());
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static int modeIndex(Mode mode) {
        Enum current = (Enum) mode.getValue();
        Enum[] modes = mode.getModes();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current || modes[i].name().equalsIgnoreCase(current.name())) {
                return i;
            }
        }
        return 0;
    }

    private static boolean isIntegerNumber(Numbers number) {
        Object value = number.getValue();
        Object inc = number.getIncrement();
        return value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
                || inc instanceof Integer || inc instanceof Long || inc instanceof Short || inc instanceof Byte;
    }

    private static Value findValue(Module module, String name) {
        String target = normalize(name);
        for (Value value : module.getValues()) {
            if (normalize(value.getName()).equals(target) || normalize(value.getDisplayName()).equals(target)) {
                return value;
            }
        }
        return null;
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

    private static String nameOf(Module module) {
        return module == null ? "" : module.getName();
    }

    private static String displayNameOf(Module module) {
        if (module == null) {
            return "";
        }
        return Client.CHINESE ? module.getChinese() : module.getName();
    }

    private static String keyName(int key) {
        if (key == Keyboard.KEY_NONE) {
            return "None";
        }
        try {
            return Keyboard.getKeyName(key);
        } catch (Throwable ignored) {
            return String.valueOf(key);
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
        builder.append(':');
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            builder.append('0');
        } else {
            builder.append(String.format(Locale.ROOT, "%.6f", value));
        }
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
                    case '\b':
                        builder.append("\\b");
                        break;
                    case '\f':
                        builder.append("\\f");
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
}
