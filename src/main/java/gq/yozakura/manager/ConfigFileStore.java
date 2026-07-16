package gq.yozakura.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class ConfigFileStore {
    private final File primary;
    private final File backup;
    private boolean backupRecovered;

    ConfigFileStore(File primary, File backup) {
        this.primary = primary;
        this.backup = backup;
    }

    String load() throws IOException {
        backupRecovered = false;
        IOException primaryFailure = null;

        if (primary.exists()) {
            try {
                return readValidObject(primary);
            } catch (IOException exception) {
                primaryFailure = exception;
            }
        }

        if (backup.exists()) {
            try {
                String snapshot = readValidObject(backup);
                replaceAtomically(primary, snapshot);
                backupRecovered = true;
                return snapshot;
            } catch (IOException backupFailure) {
                IOException failure = new IOException("Unable to load a valid configuration from "
                        + primary.getAbsolutePath(), backupFailure);
                if (primaryFailure != null) {
                    failure.addSuppressed(primaryFailure);
                }
                throw failure;
            }
        }

        if (primaryFailure != null) {
            throw new IOException("Unable to load a valid configuration from "
                    + primary.getAbsolutePath(), primaryFailure);
        }
        return null;
    }

    void save(String snapshot) throws IOException {
        validateObject(snapshot, primary);
        if (primary.exists()) {
            try {
                replaceAtomically(backup, readValidObject(primary));
            } catch (InvalidConfigException ignored) {
                // Keep the last known-good backup when the primary file is malformed.
            }
        }
        replaceAtomically(primary, snapshot);
    }

    boolean wasBackupRecovered() {
        return backupRecovered;
    }

    private String readValidObject(File file) throws IOException {
        String snapshot = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        validateObject(snapshot, file);
        return snapshot;
    }

    private void validateObject(String snapshot, File source) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(snapshot);
            if (element == null || !element.isJsonObject()) {
                throw new InvalidConfigException("Configuration root must be a JSON object: "
                        + source.getAbsolutePath());
            }
        } catch (InvalidConfigException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidConfigException("Malformed configuration JSON: "
                    + source.getAbsolutePath(), exception);
        }
    }

    private void replaceAtomically(File target, String snapshot) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create configuration directory: " + parent.getAbsolutePath());
        }

        File temp = new File(parent, target.getName() + ".tmp");
        try {
            Files.write(temp.toPath(), snapshot.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private static final class InvalidConfigException extends IOException {
        private InvalidConfigException(String message) {
            super(message);
        }

        private InvalidConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
