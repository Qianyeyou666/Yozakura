package gq.vapulite.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gq.vapulite.core.VapuClientState;
import gq.vapulite.ui.click.vape.VapeClickGui;
import gq.vapulite.module.Module;
import gq.vapulite.util.minecraft.Helper;
import gq.vapulite.value.Mode;
import gq.vapulite.value.Numbers;
import gq.vapulite.value.Option;
import gq.vapulite.value.Value;
import gq.vapulite.value.properties.ModeProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileManager {
    private static final long AUTO_SAVE_DELAY_MS = 1500L;

    private final File dir = new File(System.getenv("APPDATA"), VapuClientState.getName());
    private final File modules = new File(dir, VapuClientState.getConfig() + ".json");
    private final File backup = new File(dir, VapuClientState.getConfig() + ".json.bak");
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
            exception.printStackTrace();
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
            if (!modules.exists()) {
                captureCurrentSnapshot();
                if (!quiet) {
                    Helper.sendMessage("No Configs Found!");
                }
                return;
            }

            JsonElement jsonElement = readJson(modules);
            if ((jsonElement == null || jsonElement instanceof JsonNull || !jsonElement.isJsonObject()) && backup.exists()) {
                jsonElement = readJson(backup);
            }
            if (jsonElement == null || jsonElement instanceof JsonNull || !jsonElement.isJsonObject()) {
                captureCurrentSnapshot();
                return;
            }

            JsonObject jsonObject = (JsonObject) jsonElement;
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
                    VapeClickGui.loadGuiState((JsonObject) jsonObject.get("_gui"));
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
            exception.printStackTrace();
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
        jsonObject.add("_gui", VapeClickGui.saveGuiState());

        return gson.toJson(jsonObject);
    }

    private JsonElement readJson(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            return new JsonParser().parse(reader);
        } finally {
            reader.close();
        }
    }

    private void writeSnapshot(String snapshot) throws IOException {
        dir.mkdirs();
        File temp = new File(dir, modules.getName() + ".tmp");
        Files.write(temp.toPath(), snapshot.getBytes(StandardCharsets.UTF_8));
        if (modules.exists()) {
            Files.copy(modules.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp.toPath(), modules.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), modules.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void logConfigFailure(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "VapuLiteConfig.log");
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
