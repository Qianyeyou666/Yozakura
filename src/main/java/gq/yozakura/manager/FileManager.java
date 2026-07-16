package gq.yozakura.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gq.yozakura.core.YozakuraClientState;
import gq.yozakura.module.combat.VelocityConfigMigration;
import gq.yozakura.module.combat.aim.AimAssistConfigMigration;
import gq.yozakura.ui.click.yozakura.YozakuraClickGui;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileManager {
    private static final long AUTO_SAVE_DELAY_MS = 1500L;

    private final File dir = new File(System.getenv("APPDATA"), YozakuraClientState.getName());
    private final File modules = new File(dir, YozakuraClientState.getConfig() + ".json");
    private final File backup = new File(dir, YozakuraClientState.getConfig() + ".json.bak");
    private final ConfigFileStore configStore = new ConfigFileStore(modules, backup);
    private final Gson gson = new Gson();

    private boolean loading;
    private boolean autoSaveSuspended;
    private boolean dirty;
    private long lastDirtyMS;
    private String lastSavedSnapshot = "";

    public FileManager() {
        dir.mkdirs();
    }

    public synchronized void saveModules() throws IOException {
        String snapshot = createSnapshot();
        writeSnapshot(snapshot);
        lastSavedSnapshot = snapshot;
        dirty = false;
    }

    public synchronized void saveModulesQuietly() {
        try {
            saveModules();
        } catch (Exception exception) {
            logConfigFailure("Config save failed", exception);
        }
    }

    public synchronized void saveIfDirtyQuietly() {
        if (!dirty || loading || autoSaveSuspended) {
            return;
        }
        saveModulesQuietly();
    }

    public synchronized void loadModules() throws IOException {
        loadModules(false);
    }

    public synchronized void loadModules(boolean quiet) throws IOException {
        loading = true;
        try {
            String snapshot = configStore.load();
            if (snapshot == null) {
                captureCurrentSnapshot();
                if (!quiet) {
                    Helper.sendMessage("No Configs Found!");
                }
                return;
            }

            if (configStore.wasBackupRecovered()) {
                logConfigFailure("Recovered configuration from backup: " + backup.getAbsolutePath(), null);
            }
            JsonObject jsonObject = new JsonParser().parse(snapshot).getAsJsonObject();
            for (final Module module : ModuleManager.getModules()) {
                try {
                    final JsonElement moduleElement = getModuleElement(jsonObject, module);
                    if (moduleElement == null) {
                        continue;
                    }

                    if (moduleElement == null || moduleElement instanceof JsonNull || !moduleElement.isJsonObject()) {
                        continue;
                    }

                    final JsonObject moduleJson = (JsonObject) moduleElement;
                    migrateLegacyAimAssistValues(module, moduleJson);
                    migrateLegacyVelocityValues(module, moduleJson);

                    if (moduleJson.has("key")) {
                        module.setKey(moduleJson.get("key").getAsInt());
                    }

                    for (final Value value : module.getValues()) {
                        if (!moduleJson.has(value.getName())) {
                            continue;
                        }

                        try {
                            if (value instanceof Option) {
                                value.setValue(moduleJson.get(value.getName()).getAsBoolean());
                            } else if (value instanceof ModeProperty) {
                                JsonElement element = moduleJson.get(value.getName());
                                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                                    ((ModeProperty) value).setNumberValue(element.getAsDouble());
                                } else {
                                    ((ModeProperty) value).setMode(element.getAsString());
                                }
                            } else if (value instanceof Mode) {
                                ((Mode) value).setMode(moduleJson.get(value.getName()).getAsString());
                            } else if (value instanceof Numbers) {
                                ((Numbers) value).setNumberValue(moduleJson.get(value.getName()).getAsDouble());
                            }
                        } catch (Throwable throwable) {
                            logConfigFailure("Failed to load value " + module.getName() + "." + value.getName(), throwable);
                        }
                    }

                    if (!module.NoToggle && moduleJson.has("state")) {
                        module.setState(moduleJson.get("state").getAsBoolean(), false);
                    }
                } catch (Throwable throwable) {
                    logConfigFailure("Failed to load module " + module.getName(), throwable);
                }
            }

            // 恢复ClickGUI界面状态
            if (jsonObject.has("_gui") && jsonObject.get("_gui").isJsonObject()) {
                try {
                    YozakuraClickGui.loadGuiState((JsonObject) jsonObject.get("_gui"));
                } catch (Throwable throwable) {
                    logConfigFailure("Failed to load ClickGUI state", throwable);
                }
            }
        } finally {
            loading = false;
            captureCurrentSnapshot();
        }
    }

    public synchronized void markDirty() {
        if (loading || autoSaveSuspended) {
            return;
        }
        dirty = true;
        lastDirtyMS = System.currentTimeMillis();
    }

    public synchronized void autoSaveTick() {
        if (!dirty || loading || autoSaveSuspended) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDirtyMS < AUTO_SAVE_DELAY_MS) {
            return;
        }
        try {
            String snapshot = createSnapshot();
            if (!snapshot.equals(lastSavedSnapshot)) {
                writeSnapshot(snapshot);
                lastSavedSnapshot = snapshot;
            }
            dirty = false;
        } catch (Exception exception) {
            logConfigFailure("Config auto-save failed", exception);
            lastDirtyMS = now;
        }
    }

    public synchronized void setAutoSaveSuspended(boolean suspended) {
        this.autoSaveSuspended = suspended;
    }

    public synchronized boolean isLoading() {
        return loading;
    }

    private void captureCurrentSnapshot() {
        lastSavedSnapshot = createSnapshot();
        dirty = false;
    }

    private JsonElement getModuleElement(JsonObject jsonObject, Module module) {
        if (jsonObject.has(module.name)) {
            return jsonObject.get(module.name);
        }
        if ("AimAssist".equalsIgnoreCase(module.getName()) && jsonObject.has("Aimbot")) {
            return jsonObject.get("Aimbot");
        }
        return null;
    }

    private void migrateLegacyAimAssistValues(Module module, JsonObject moduleJson) {
        if (!"AimAssist".equalsIgnoreCase(module.getName()) || !moduleJson.has("VapeMode")) {
            return;
        }
        JsonElement mode = moduleJson.get("Mode");
        JsonElement vapeMode = moduleJson.get("VapeMode");
        if (isStringPrimitive(mode) && isStringPrimitive(vapeMode)) {
            String mergedMode = AimAssistConfigMigration.resolveMode(mode.getAsString(), vapeMode.getAsString());
            if (mergedMode != null) {
                moduleJson.addProperty("Mode", mergedMode);
            }
        }

        JsonElement keepMoveDirection = moduleJson.get("KeepMoveDirection");
        if (isBooleanPrimitive(keepMoveDirection)) {
            moduleJson.addProperty("KeepMoveDirection",
                    AimAssistConfigMigration.migrateKeepMoveDirection(keepMoveDirection.getAsBoolean()));
        }
    }

    private void migrateLegacyVelocityValues(Module module, JsonObject moduleJson) {
        if (!"Velocity".equalsIgnoreCase(module.getName())) {
            return;
        }
        JsonElement mode = moduleJson.get("Mode");
        JsonElement legacyReduceToggle = moduleJson.get("Reduce");
        JsonElement horizontal = moduleJson.get("Horizontal");
        if (!isStringPrimitive(mode)
                || !isBooleanPrimitive(legacyReduceToggle)
                || !isNumberPrimitive(horizontal)) {
            return;
        }
        Integer migratedHorizontal = VelocityConfigMigration.migrateLegacyReduceHorizontal(
                mode.getAsString(), horizontal.getAsDouble());
        if (migratedHorizontal != null) {
            moduleJson.addProperty("Horizontal", migratedHorizontal);
        }
    }

    private boolean isStringPrimitive(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private boolean isBooleanPrimitive(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    private boolean isNumberPrimitive(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private String createSnapshot() {
        final JsonObject jsonObject = new JsonObject();

        for (final Module module : ModuleManager.getModules()) {
            final JsonObject moduleJson = new JsonObject();

            moduleJson.addProperty("state", module.getState());
            moduleJson.addProperty("key", module.getKey());

            for (final Value value : module.getValues()) {
                if (value instanceof ModeProperty) {
                    moduleJson.addProperty(value.getName(), ((ModeProperty) value).getModeString());
                } else if (value instanceof Numbers) {
                    moduleJson.addProperty(value.getName(), (Number) value.getValue());
                } else if (value instanceof Mode) {
                    moduleJson.addProperty(value.getName(), ((Mode) value).getModeAsString());
                } else if (value instanceof Option) {
                    moduleJson.addProperty(value.getName(), (Boolean) value.getValue());
                }
            }

            jsonObject.add(module.name, moduleJson);
        }

        // 保存ClickGUI界面状态（标签页/选中模块/详情子标签等）
        jsonObject.add("_gui", YozakuraClickGui.saveGuiState());

        return gson.toJson(jsonObject);
    }

    private void writeSnapshot(String snapshot) throws IOException {
        configStore.save(snapshot);
    }

    public static void logConfigFailure(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraConfig.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
