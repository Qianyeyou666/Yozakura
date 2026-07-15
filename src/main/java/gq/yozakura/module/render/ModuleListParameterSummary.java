package gq.yozakura.module.render;

import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;

import java.util.List;
import java.util.Locale;

final class ModuleListParameterSummary {
    private ModuleListParameterSummary() {
    }

    static String summarize(List<? extends Value> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        String cps = summarizeCps(values);
        if (cps.length() > 0) {
            return cps;
        }

        String mode = summarizeMode(values);
        if (mode.length() > 0) {
            return mode;
        }

        NumberValue number = findPreferredNumber(values);
        return number == null ? "" : formatNumber(number.value) + number.suffix;
    }

    static String summarizeExplicit(String[] suffixes) {
        if (suffixes == null || suffixes.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (String suffix : suffixes) {
            if (suffix == null) {
                continue;
            }
            String text = suffix.trim();
            if (text.length() == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(text);
            count++;
            if (count == 2) {
                break;
            }
        }
        return result.toString();
    }

    private static String summarizeCps(List<? extends Value> values) {
        NumberValue min = namedNumber(values, "", "Min CPS", "MinCPS");
        NumberValue max = namedNumber(values, "", "Max CPS", "MaxCPS", "CPS");
        if (min == null || max == null) {
            return "";
        }
        double low = Math.min(min.value, max.value);
        double high = Math.max(min.value, max.value);
        return formatNumber(low) + "-" + formatNumber(high) + " CPS";
    }

    private static String summarizeMode(List<? extends Value> values) {
        Value exact = findValue(values, "Mode");
        String text = modeText(exact);
        if (text.length() > 0 && !isIgnoredMode(exact)) {
            return text;
        }
        for (Value value : values) {
            text = modeText(value);
            if (text.length() > 0 && !isIgnoredMode(value)) {
                return text;
            }
        }
        return "";
    }

    private static String modeText(Value value) {
        if (value instanceof ModeProperty) {
            return formatModeName(((ModeProperty) value).getModeString());
        }
        if (value instanceof Mode) {
            Object current = value.getValue();
            if (current instanceof Enum) {
                return formatModeName(((Enum) current).name());
            }
        }
        return "";
    }

    private static boolean isIgnoredMode(Value value) {
        String name = normalizedValueName(value);
        return "priority".equals(name) || "sort".equals(name) || "sortmode".equals(name)
                || "aimpoint".equals(name) || "autoblockmode".equals(name) || "hudstyle".equals(name)
                || "theme".equals(name) || "arraylisttheme".equals(name)
                || "notificationtheme".equals(name) || "displaymode".equals(name)
                || "showtarget".equals(name);
    }

    private static NumberValue findPreferredNumber(List<? extends Value> values) {
        NumberValue range = namedNumber(values, "r", "Range", "Reach", "Attack Range", "Swing Range");
        if (range != null) {
            return range;
        }
        NumberValue delay = namedNumber(values, "ms", "Delay MS", "DelayMS", "Delay", "Packet Delay",
                "PacketDelay", "History MS", "HistoryMS", "Pulse MS", "PulseMS", "Jitter MS", "JitterMS",
                "Click Delay", "Close Delay", "Start Delay", "Reaction Delay", "Hover Delay", "Post Delay",
                "Switch Delay", "Unsneak Delay", "Sneak On Jump", "Maximum Delay");
        if (delay != null) {
            return delay;
        }
        NumberValue scale = namedNumber(values, "x", "Scale", "Size", "Radius");
        if (scale != null) {
            return scale;
        }
        NumberValue chance = namedNumber(values, "%", "Chance", "Slowdown", "Horizontal");
        if (chance != null) {
            return chance;
        }
        return namedNumber(values, "", "Expand", "Height", "Line Width", "LineWidth");
    }

    private static NumberValue namedNumber(List<? extends Value> values, String suffix, String... names) {
        Value value = findValue(values, names);
        if (!(value instanceof Numbers) || !isVisible(value) || !(value.getValue() instanceof Number)) {
            return null;
        }
        return new NumberValue(((Number) value.getValue()).doubleValue(), suffix);
    }

    private static Value findValue(List<? extends Value> values, String... names) {
        if (values == null || names == null) {
            return null;
        }
        for (Value value : values) {
            if (value == null) {
                continue;
            }
            for (String name : names) {
                if (matches(value, name)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean matches(Value value, String name) {
        String expected = normalize(name);
        return expected.equals(normalize(value.getDisplayName())) || expected.equals(normalize(value.getName()));
    }

    private static boolean isVisible(Value value) {
        try {
            return value.isVisible();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static String normalizedValueName(Value value) {
        if (value == null) {
            return "";
        }
        String display = normalize(value.getDisplayName());
        return display.length() > 0 ? display : normalize(value.getName());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001D) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format(Locale.US, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String formatModeName(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return "";
        }
        String source = raw.trim();
        if (containsLowerCase(source) && source.indexOf('_') < 0) {
            return source;
        }
        String[] words = source.split("[_\\s-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            String formatted = formatModeWord(word);
            if (formatted.length() == 0) {
                continue;
            }
            if ("D".equals(formatted) && result.length() > 0
                    && (result.toString().endsWith("2") || result.toString().endsWith("3"))) {
                result.append('D');
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(formatted);
        }
        return result.toString();
    }

    private static boolean containsLowerCase(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLowerCase(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String formatModeWord(String word) {
        if (word == null || word.length() == 0) {
            return "";
        }
        String upper = word.toUpperCase(Locale.ROOT);
        if ("TWO".equals(upper)) {
            return "2";
        }
        if ("THREE".equals(upper)) {
            return "3";
        }
        if ("GLOWESP".equals(upper)) {
            return "GlowESP";
        }
        if ("ESP".equals(upper) || "HUD".equals(upper) || "CPS".equals(upper)
                || "FOV".equals(upper) || "GUI".equals(upper) || "RGB".equals(upper)
                || "D".equals(upper)) {
            return upper;
        }
        String lower = upper.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static final class NumberValue {
        private final double value;
        private final String suffix;

        private NumberValue(double value, String suffix) {
            this.value = value;
            this.suffix = suffix == null ? "" : suffix;
        }
    }
}
