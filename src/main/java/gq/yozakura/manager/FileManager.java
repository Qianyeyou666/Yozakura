package gq.yozakura.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gq.yozakura.core.YozakuraClientState;
import gq.yozakura.module.combat.VelocityConfigMigration;
import gq.yozakura.module.combat.aim.AimAssistConfigMigration;
import gq.yozakura.module.Module;
import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import gq.yozakura.value.Value;
import gq.yozakura.value.properties.ModeProperty;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class FileManager {
    private static final long AUTO_SAVE_DELAY_MS = 1500L;

    private final File dir = new File(System.getenv("APPDATA"), YozakuraClientState.getName());
    private final File modules = new File(dir, YozakuraClientState.getConfig() + ".json");
    private final File backup = new File(dir, YozakuraClientState.getConfig() + ".json.bak");
    private final ConfigFileStore configStore = new ConfigFileStore(modules, backup);
    private final ConfigProfileStore profileStore = new ConfigProfileStore(new File(dir, "configs"));
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
            applySnapshot(snapshot);

        } finally {
            loading = false;
            captureCurrentSnapshot();
        }
    }

    public synchronized List<String> listProfiles() throws IOException {
        return profileStore.list();
    }

    public synchronized File getProfileDirectory() throws IOException {
        return profileStore.getDirectory();
    }

    public synchronized void saveProfile(String name) throws IOException {
        profileStore.save(name, createSnapshot());
    }

    public synchronized String readProfileSnapshot(String name) throws IOException {
        String snapshot = profileStore.load(name);
        validateSnapshotObject(snapshot);
        return snapshot;
    }

    public synchronized void saveProfileSnapshot(String name, String snapshot) throws IOException {
        validateSnapshotObject(snapshot);
        profileStore.save(name, snapshot);
    }

    public synchronized void loadProfile(String name) throws IOException {
        loading = true;
        try {
            applySnapshot(profileStore.load(name));
            String snapshot = createSnapshot();
            writeSnapshot(snapshot);
            lastSavedSnapshot = snapshot;
            dirty = false;
        } finally {
            loading = false;
        }
    }

    public synchronized String exportSnapshot() {
        return createSnapshot();
    }

    public synchronized void importSnapshot(String snapshot) throws IOException {
        validateSnapshotObject(snapshot);
        loading = true;
        try {
            applySnapshot(snapshot);
            String appliedSnapshot = createSnapshot();
            writeSnapshot(appliedSnapshot);
            lastSavedSnapshot = appliedSnapshot;
            dirty = false;
        } finally {
            loading = false;
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

    private void validateSnapshotObject(String snapshot) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(snapshot);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Configuration root must be a JSON object");
            }
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Malformed configuration JSON", exception);
        }
    }

    private void applySnapshot(String snapshot) {
        JsonObject jsonObject = new JsonParser().parse(snapshot).getAsJsonObject();
        for (final Module module : ModuleManager.getModules()) {
            try {
                final JsonElement moduleElement = getModuleElement(jsonObject, module);
                if (moduleElement == null || moduleElement instanceof JsonNull || !moduleElement.isJsonObject()) {
                    continue;
                }

                final JsonObject moduleJson = (JsonObject) moduleElement;
                migrateLegacyAimAssistValues(module, moduleJson);
                migrateLegacyVelocityValues(module, moduleJson);

                if (moduleJson.has("key")) {
                    module.setKey(resolveModuleKey(module, moduleJson.get("key").getAsInt()));
                }

                if (moduleJson.has("bindMode")) {
                    try {
                        module.setBindMode(Module.BindMode.valueOf(
                                moduleJson.get("bindMode").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown bind mode in config — keep the TOGGLE default.
                    }
                }
                if (moduleJson.has("hidden")) {
                    module.setHidden(moduleJson.get("hidden").getAsBoolean());
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
                                ((ModeProperty) value).setStoredNumberValue(element.getAsDouble());
                            } else {
                                ((ModeProperty) value).setStoredMode(element.getAsString());
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
    }

    /**
     * Legacy configs may contain {@code ClickGUI.key = 0}, overriding the
     * constructor's RSHIFT default and making every ClickGUI style impossible
     * to open. Preserve NONE for ordinary modules, but keep this UI entrypoint
     * reachable.
     */
    private int resolveModuleKey(Module module, int configuredKey) {
        if ("ClickGUI".equalsIgnoreCase(module.getName())
                && configuredKey == Keyboard.KEY_NONE) {
            return Keyboard.KEY_RSHIFT;
        }
        return configuredKey;
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
        if (!"AimAssist".equalsIgnoreCase(module.getName())) {
            return;
        }
        JsonElement mode = moduleJson.get("Mode");
        JsonElement vapeMode = moduleJson.get("VapeMode");
        if (isStringPrimitive(mode)) {
            String mergedMode = AimAssistConfigMigration.resolveMode(mode.getAsString(),
                    isStringPrimitive(vapeMode) ? vapeMode.getAsString() : null);
            if (mergedMode != null) {
                moduleJson.addProperty("Mode", mergedMode);
            }
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
            moduleJson.addProperty("bindMode", module.getBindMode().name());
            moduleJson.addProperty("hidden", module.isHidden());

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
