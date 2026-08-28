package gq.yozakura.ui.click.qml;

import gq.yozakura.core.Client;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.math.NumberPrecision;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import io.github.timer_err.qml4j.engine.binding.ObservableList;
import io.github.timer_err.qml4j.engine.binding.Property;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live, reload-free bridge between Minecraft modules and the QML scene. */
public final class QmlClickGuiModel {
    public final Property<String> activeCategory = new Property<String>(ModuleType.Combat.name());
    public final Property<String> title = new Property<String>(ModuleType.Combat.toString());
    public final Property<Boolean> chinese = new Property<Boolean>(Boolean.FALSE);
    public final Property<String> search = new Property<String>("");
    public final Property<String> expandedModule = new Property<String>("");
    public final Property<String> username = new Property<String>("YozakuraUser");
    public final Property<String> version = new Property<String>("1.52");
    public final ObservableList<Map<String, Object>> categories = new ObservableList<Map<String, Object>>();
    public final ObservableList<Map<String, Object>> modules = new ObservableList<Map<String, Object>>();
    public final ObservableList<Map<String, Object>> settings = new ObservableList<Map<String, Object>>();

    private final List<Entry> source;
    private final boolean persistLanguage;
    private boolean closeRequested;

    public QmlClickGuiModel() {
        this(liveEntries(), Client.CHINESE, true);
        username.set(emptyTo(Client.username, "YozakuraUser"));
        version.set(emptyTo(Client.version, "1.52"));
    }

    QmlClickGuiModel(List<Entry> source, boolean chinese) {
        this(source, chinese, false);
    }

    private QmlClickGuiModel(List<Entry> source, boolean chinese, boolean persistLanguage) {
        this.source = source == null ? new ArrayList<Entry>() : source;
        this.persistLanguage = persistLanguage;
        this.chinese.set(chinese);
        rebuildCategories();
        rebuildModules();
    }

    public void selectCategory(String category) {
        ModuleType type = findCategory(category);
        if (type == null || type.name().equals(activeCategory.peek())) return;
        activeCategory.set(type.name());
        title.set(categoryName(type));
        expandedModule.set("");
        settings.clear();
        rebuildModules();
    }

    public void setModuleEnabled(String moduleName, boolean enabled) {
        Entry module = findModule(moduleName);
        if (module == null) return;
        module.setState(enabled);
        replaceModuleRow(module);
    }

    public void setChinese(boolean next) {
        if (Boolean.valueOf(next).equals(chinese.peek())) return;
        chinese.set(next);
        if (persistLanguage) Client.CHINESE = next;
        ModuleType selected = findCategory(activeCategory.peek());
        title.set(selected == null ? activeCategory.peek() : categoryName(selected));
        rebuildCategories();
        rebuildModules();
        rebuildSettings();
    }

    public void toggleLanguage() {
        setChinese(!Boolean.TRUE.equals(chinese.peek()));
    }

    public void setSearch(String query) {
        String next = query == null ? "" : query.trim();
        if (next.equals(search.peek())) return;
        search.set(next);
        rebuildModules();
    }

    public void requestClose() {
        closeRequested = true;
    }

    public boolean consumeCloseRequest() {
        boolean requested = closeRequested;
        closeRequested = false;
        return requested;
    }

    public void toggleSettings(String moduleName) {
        Entry module = findModule(moduleName);
        if (module == null || !module.hasSettings()) return;
        String next = normalize(module.name()).equals(normalize(expandedModule.peek()))
                ? "" : module.name();
        expandedModule.set(next);
        rebuildSettings();
    }

    public void cycleMode(String moduleName, String valueName, int direction) {
        Setting setting = findSetting(moduleName, valueName);
        if (setting == null || !"mode".equals(setting.type()) || setting.options().isEmpty()) return;
        List<String> options = setting.options();
        int current = options.indexOf(String.valueOf(setting.current()));
        int next = (current + (direction < 0 ? -1 : 1) + options.size()) % options.size();
        setting.setMode(options.get(next));
        rebuildModules();
        rebuildSettings();
    }

    public void setBoolean(String moduleName, String valueName, boolean next) {
        Setting setting = findSetting(moduleName, valueName);
        if (setting == null || !"boolean".equals(setting.type())) return;
        setting.setBoolean(next);
        rebuildSettings();
    }

    public void setNumberByRatio(String moduleName, String valueName, double ratio) {
        Setting setting = findSetting(moduleName, valueName);
        if (setting == null || !"number".equals(setting.type())) return;
        double clamped = Math.max(0.0D, Math.min(1.0D, ratio));
        setting.setNumber(setting.minimum() + (setting.maximum() - setting.minimum()) * clamped);
        rebuildSettings();
    }

    public void setColorHex(String moduleName, String valueName, String color) {
        Setting setting = findSetting(moduleName, valueName);
        if (!(setting instanceof ColorSetting) || color == null) return;
        try {
            int rgb = Integer.parseInt(color.replace("#", ""), 16) & 0x00FFFFFF;
            ((ColorSetting) setting).setRgb(rgb);
            rebuildSettings();
        } catch (NumberFormatException ignored) {
        }
    }

    /** Pulls module changes made outside this screen without rebuilding the QML document. */
    public boolean sync() {
        boolean changed = false;
        for (int i = 0; i < modules.size(); i++) {
            Map<String, Object> row = modules.get(i);
            Entry module = findModule(String.valueOf(row.get("name")));
            if (module != null && !Boolean.valueOf(module.state()).equals(row.get("enabled"))) {
                modules.set(i, moduleRow(module));
                changed = true;
            }
        }
        return changed;
    }

    private void rebuildCategories() {
        categories.clear();
        for (ModuleType type : ModuleType.values()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("key", type.name());
            row.put("displayName", categoryName(type));
            row.put("icon", categoryIcon(type));
            categories.add(row);
        }
    }

    private void rebuildModules() {
        modules.clear();
        ModuleType selected = findCategory(activeCategory.peek());
        ArrayList<Entry> visible = new ArrayList<Entry>();
        for (Entry module : source) {
            if (module != null && module.category() == selected && matchesSearch(module)) visible.add(module);
        }
        visible.sort(new Comparator<Entry>() {
            @Override public int compare(Entry left, Entry right) {
                return safe(left.name()).compareToIgnoreCase(safe(right.name()));
            }
        });
        for (Entry module : visible) modules.add(moduleRow(module));
    }

    private void rebuildSettings() {
        settings.clear();
        Entry module = findModule(expandedModule.peek());
        if (module == null) return;
        for (Setting setting : module.settings()) {
            if (setting == null) continue;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("name", setting.name());
            row.put("displayName", setting.displayName());
            row.put("type", setting.type());
            row.put("current", setting.current());
            row.put("min", setting.minimum());
            row.put("max", setting.maximum());
            double range = setting.maximum() - setting.minimum();
            double value = setting.current() instanceof Number
                    ? ((Number) setting.current()).doubleValue() : setting.minimum();
            row.put("ratio", range <= 0.0D ? 0.0D : (value - setting.minimum()) / range);
            row.put("options", setting.options());
            settings.add(row);
        }
    }

    private boolean matchesSearch(Entry module) {
        String query = normalize(search.peek());
        return query.isEmpty() || normalize(module.name()).contains(query)
                || normalize(module.chineseName()).contains(query)
                || normalize(module.description()).contains(query);
    }

    private void replaceModuleRow(Entry module) {
        for (int i = 0; i < modules.size(); i++) {
            if (safe(module.name()).equalsIgnoreCase(String.valueOf(modules.get(i).get("name")))) {
                modules.set(i, moduleRow(module));
                return;
            }
        }
    }

    private Map<String, Object> moduleRow(Entry module) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", safe(module.name()));
        row.put("displayName", Boolean.TRUE.equals(chinese.peek()) ? safe(module.chineseName()) : safe(module.name()));
        row.put("description", safe(module.description()));
        row.put("icon", moduleIcon(module));
        row.put("enabled", module.state());
        row.put("keyName", keyName(module.key()));
        row.put("hasSettings", module.hasSettings());
        return row;
    }

    private Entry findModule(String name) {
        String target = normalize(name);
        for (Entry module : source) {
            if (module != null && normalize(module.name()).equals(target)) return module;
        }
        return null;
    }

    private Setting findSetting(String moduleName, String valueName) {
        Entry module = findModule(moduleName);
        if (module == null) return null;
        String target = normalize(valueName);
        for (Setting setting : module.settings()) {
            if (setting != null && (normalize(setting.name()).equals(target)
                    || normalize(setting.displayName()).equals(target))) return setting;
        }
        return null;
    }

    private static ModuleType findCategory(String value) {
        String target = normalize(value);
        for (ModuleType type : ModuleType.values()) {
            if (normalize(type.name()).equals(target) || normalize(type.toString()).equals(target)) return type;
        }
        return null;
    }

    private String categoryName(ModuleType type) {
        if (!Boolean.TRUE.equals(chinese.peek())) return type.toString();
        switch (type) {
            case Combat: return "战斗类";
            case Render: return "视觉类";
            case Movement: return "移动类";
            case Player: return "玩家类";
            case World: return "世界类";
            case Other: return "其他";
            case Config: return "全局设置";
            default: return type.toString();
        }
    }

    private static String categoryIcon(ModuleType type) {
        switch (type) {
            case Combat: return "combat";
            case Render: return "render";
            case Movement: return "movement";
            case Player: return "player";
            case World: return "world";
            case Config: return "settings";
            default: return "misc";
        }
    }

    private static String moduleIcon(Entry module) {
        String name = normalize(module.name());
        if (name.contains("killaura")) return "plus";
        if (name.contains("aim") || name.contains("aura")) return "aim";
        if (name.contains("bot")) return "bot";
        if (name.contains("click") || name.contains("hit")) return "click";
        if (name.contains("critical") || name.equals("speed")) return "bolt";
        if (name.contains("reach")) return "reach";
        if (name.contains("bright")) return "sun";
        if (name.contains("armor") || name.contains("nofall")) return "shield";
        if (name.contains("chest") || name.contains("inventory") || name.contains("storage")) return "chest";
        if (name.contains("nametag")) return "tag";
        if (name.contains("backtrack") || name.contains("lag")) return "clock";
        if (name.contains("slow")) return "ban";
        if (name.contains("hud") || name.contains("keyboard")) return "box";
        return categoryIcon(module.category());
    }

    private static List<Entry> liveEntries() {
        ArrayList<Entry> entries = new ArrayList<Entry>();
        for (Module module : ModuleManager.getModules()) {
            if (module != null) entries.add(new ModuleEntry(module));
        }
        return entries;
    }

    interface Entry {
        String name();
        String chineseName();
        String description();
        ModuleType category();
        boolean state();
        void setState(boolean state);
        int key();
        boolean hasSettings();
        List<Setting> settings();
    }

    interface Setting {
        String name();
        String displayName();
        String type();
        Object current();
        double minimum();
        double maximum();
        List<String> options();
        void setMode(String value);
        void setBoolean(boolean value);
        void setNumber(double value);
    }

    private static final class ModuleEntry implements Entry {
        private final Module module;
        private ModuleEntry(Module module) { this.module = module; }
        @Override public String name() { return module.getName(); }
        @Override public String chineseName() { return module.getChinese(); }
        @Override public String description() { return module.getDescription(); }
        @Override public ModuleType category() { return module.getCategory(); }
        @Override public boolean state() { return module.getState(); }
        @Override public void setState(boolean state) { module.setState(state); }
        @Override public int key() { return module.getKey(); }
        @Override public boolean hasSettings() { return module.getValues() != null && !module.getValues().isEmpty(); }
        @Override public List<Setting> settings() {
            ArrayList<Setting> result = new ArrayList<Setting>();
            if (module.getValues() == null) return result;
            List<Value> values = module.getValues();
            for (int i = 0; i < values.size(); i++) {
                Value value = values.get(i);
                if (value == null || !value.isVisible()) continue;
                if (i + 2 < values.size() && isColorTriple(value, values.get(i + 1), values.get(i + 2))) {
                    result.add(new ColorSetting(module, (Numbers) value,
                            (Numbers) values.get(i + 1), (Numbers) values.get(i + 2)));
                    i += 2;
                } else {
                    result.add(new ValueSetting(module, value));
                }
            }
            return result;
        }

        private static boolean isColorTriple(Value red, Value green, Value blue) {
            if (!(red instanceof Numbers) || !(green instanceof Numbers) || !(blue instanceof Numbers)) return false;
            String redName = safe(red.getName());
            String greenName = safe(green.getName());
            String blueName = safe(blue.getName());
            if (!redName.toLowerCase(Locale.ROOT).endsWith("red")
                    || !greenName.toLowerCase(Locale.ROOT).endsWith("green")
                    || !blueName.toLowerCase(Locale.ROOT).endsWith("blue")) return false;
            String base = redName.substring(0, redName.length() - 3);
            return greenName.substring(0, greenName.length() - 5).equalsIgnoreCase(base)
                    && blueName.substring(0, blueName.length() - 4).equalsIgnoreCase(base);
        }
    }

    private static final class ColorSetting implements Setting {
        private final Module module;
        private final Numbers red;
        private final Numbers green;
        private final Numbers blue;
        private final String name;
        private final String displayName;

        private ColorSetting(Module module, Numbers red, Numbers green, Numbers blue) {
            this.module = module;
            this.red = red;
            this.green = green;
            this.blue = blue;
            String rawName = safe(red.getName());
            this.name = rawName.substring(0, Math.max(0, rawName.length() - 3));
            String rawDisplay = safe(red.getDisplayName());
            this.displayName = rawDisplay.replaceFirst("(?i)\\s*red$", "");
        }

        @Override public String name() { return name; }
        @Override public String displayName() { return displayName; }
        @Override public String type() { return "color"; }
        @Override public Object current() {
            return String.format(Locale.ROOT, "#%02X%02X%02X", channel(red), channel(green), channel(blue));
        }
        @Override public double minimum() { return 0.0D; }
        @Override public double maximum() { return 1.0D; }
        @Override public List<String> options() { return new ArrayList<String>(); }
        @Override public void setMode(String value) { }
        @Override public void setBoolean(boolean value) { }
        @Override public void setNumber(double value) { }
        private void setRgb(int rgb) {
            red.setNumberValue((rgb >> 16) & 0xFF);
            green.setNumberValue((rgb >> 8) & 0xFF);
            blue.setNumberValue(rgb & 0xFF);
            verify(red);
            verify(green);
            verify(blue);
        }
        private void verify(Value value) {
            if (module instanceof gq.yozakura.module.runtime.Module) {
                ((gq.yozakura.module.runtime.Module) module).verifyValue(value.getName());
            }
        }
        private static int channel(Numbers value) {
            Object current = value.getValue();
            return current instanceof Number ? Math.max(0, Math.min(255, ((Number) current).intValue())) : 0;
        }
    }

    private static final class ValueSetting implements Setting {
        private final Module module;
        private final Value value;
        private ValueSetting(Module module, Value value) { this.module = module; this.value = value; }
        @Override public String name() { return safe(value.getName()); }
        @Override public String displayName() { return safe(value.getDisplayName()); }
        @Override public String type() {
            if (value instanceof ModeProperty || value instanceof Mode) return "mode";
            if (value instanceof Option && value.getValue() instanceof Boolean) return "boolean";
            if (value instanceof Numbers && value.getValue() instanceof Number) return "number";
            return "text";
        }
        @Override public Object current() {
            if (value instanceof ModeProperty) return ((ModeProperty) value).getModeString();
            if (value instanceof Mode) return ((Mode) value).getModeAsString();
            return value.getValue();
        }
        @Override public double minimum() {
            Number number = value instanceof Numbers ? ((Numbers) value).getMinimum() : null;
            return number == null ? 0.0D : number.doubleValue();
        }
        @Override public double maximum() {
            Number number = value instanceof Numbers ? ((Numbers) value).getMaximum() : null;
            return number == null ? 1.0D : number.doubleValue();
        }
        @Override public List<String> options() {
            ArrayList<String> result = new ArrayList<String>();
            if (value instanceof ModeProperty) {
                for (String option : ((ModeProperty) value).getModes()) result.add(option);
            } else if (value instanceof Mode) {
                for (Enum option : ((Mode) value).getModes()) result.add(option.name());
            }
            return result;
        }
        @Override public void setMode(String next) {
            if (value instanceof ModeProperty) ((ModeProperty) value).setMode(next);
            else if (value instanceof Mode) ((Mode) value).setMode(next);
            verify();
        }
        @SuppressWarnings("unchecked")
        @Override public void setBoolean(boolean next) {
            if (value instanceof Option) value.setValue(next);
            verify();
        }
        @Override public void setNumber(double next) {
            if (value instanceof Numbers) {
                Numbers number = (Numbers) value;
                double step = NumberPrecision.uiIncrement(number.getIncrement());
                number.setNumberValue(NumberPrecision.snap(next, minimum(), maximum(), step));
            }
            verify();
        }
        private void verify() {
            if (module instanceof gq.yozakura.module.runtime.Module) {
                ((gq.yozakura.module.runtime.Module) module).verifyValue(value.getName());
            }
        }
    }

    private static String keyName(int key) {
        if (key == Keyboard.KEY_NONE) return "None";
        String name = Keyboard.getKeyName(key);
        return name == null || name.isEmpty() ? String.valueOf(key) : name;
    }

    private static String normalize(String value) {
        return safe(value).replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
