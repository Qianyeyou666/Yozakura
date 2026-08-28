package gq.yozakura.club;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ClubSessionStore {
    private final File file;

    public ClubSessionStore(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Club session file is required");
        }
        this.file = file;
    }

    public synchronized ClubSession load() {
        if (!file.isFile()) {
            return null;
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonElement element = new JsonParser().parse(json);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            String token = string(object, "token");
            String username = string(object, "username");
            if (token.isEmpty() || username.isEmpty()) {
                return null;
            }
            return new ClubSession(token, username);
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void save(ClubSession session) throws IOException {
        if (session == null) {
            throw new IllegalArgumentException("Club session is required");
        }
        JsonObject object = new JsonObject();
        object.addProperty("token", session.getToken());
        object.addProperty("username", session.getUsername());
        replaceAtomically(object.toString());
    }

    public synchronized void clear() throws IOException {
        Files.deleteIfExists(file.toPath());
        File temp = tempFile();
        Files.deleteIfExists(temp.toPath());
    }

    private void replaceAtomically(String content) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create Club session directory: " + parent.getAbsolutePath());
        }
        File temp = tempFile();
        try {
            Files.write(temp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private File tempFile() {
        return new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".tmp");
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }
}
