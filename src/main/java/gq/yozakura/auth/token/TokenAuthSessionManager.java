package gq.yozakura.auth.token;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TokenAuthSessionManager {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String SESSION_FIELD_MCP = "session";
    private static final String SESSION_FIELD_SRG = "field_71449_j";

    private final Minecraft minecraft;
    private final Session originalSession;

    public TokenAuthSessionManager() {
        this(Minecraft.getMinecraft());
    }

    public TokenAuthSessionManager(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.originalSession = getCurrentSession();
    }

    public Session getCurrentSession() {
        return minecraft.getSession();
    }

    public Session getOriginalSession() {
        return originalSession;
    }

    public void login(String input) throws IOException {
        setCurrentSession(parseSession(input));
    }

    public void restore() throws IOException {
        if (originalSession == null) {
            throw new IOException("No original session was captured");
        }
        setCurrentSession(originalSession);
    }

    private Session parseSession(String input) throws IOException {
        String session = input == null ? "" : input.trim();
        if (session.isEmpty()) {
            throw new IOException("Session is empty");
        }

        if (session.indexOf(':') >= 0) {
            String[] parts = session.split(":", 3);
            if (parts.length != 3 || isBlank(parts[0]) || isBlank(parts[1]) || isBlank(parts[2])) {
                throw new IOException("Expected name:uuid:token");
            }
            return new Session(parts[0].trim(), parts[1].trim(), parts[2].trim(), "mojang");
        }

        return requestProfileSession(session);
    }

    private Session requestProfileSession(String token) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(PROFILE_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            int code = connection.getResponseCode();
            String body = readBody(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Profile request failed: HTTP " + code + describeError(body));
            }

            JsonObject profile = new JsonParser().parse(body).getAsJsonObject();
            String name = requiredString(profile, "name");
            String id = requiredString(profile, "id");
            return new Session(name, id, token, "mojang");
        } catch (IllegalStateException exception) {
            throw new IOException("Profile response was not valid JSON", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void setCurrentSession(Session session) throws IOException {
        Field field = null;
        try {
            field = findSessionField();
            field.setAccessible(true);
            removeFinalModifier(field);
            field.set(minecraft, session);
        } catch (ReflectiveOperationException exception) {
            if (field != null && setWithUnsafe(field, session)) {
                return;
            }
            throw new IOException("Couldn't set Minecraft session", exception);
        } catch (RuntimeException exception) {
            if (field != null && setWithUnsafe(field, session)) {
                return;
            }
            throw new IOException("Couldn't set Minecraft session", exception);
        }
    }

    private Field findSessionField() throws NoSuchFieldException {
        Class<?> type = minecraft.getClass();
        try {
            return type.getDeclaredField(SESSION_FIELD_MCP);
        } catch (NoSuchFieldException ignored) {
        }
        try {
            return type.getDeclaredField(SESSION_FIELD_SRG);
        } catch (NoSuchFieldException ignored) {
        }
        for (Field field : type.getDeclaredFields()) {
            if (Session.class.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        throw new NoSuchFieldException("Minecraft session field");
    }

    private static void removeFinalModifier(Field field) {
        if (!Modifier.isFinal(field.getModifiers())) {
            return;
        }
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private boolean setWithUnsafe(Field field, Session session) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method putObject = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
            long offset = ((Long) objectFieldOffset.invoke(unsafe, field)).longValue();
            putObject.invoke(unsafe, minecraft, offset, session);
            return getCurrentSession() == session;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            throw new IOException("Profile response missing " + name);
        }
        String value = element.getAsString();
        if (isBlank(value)) {
            throw new IOException("Profile response missing " + name);
        }
        return value;
    }

    private static String describeError(String body) {
        if (isBlank(body)) {
            return "";
        }
        try {
            JsonObject error = new JsonParser().parse(body).getAsJsonObject();
            JsonElement message = error.get("errorMessage");
            if (message != null && !message.isJsonNull()) {
                return ": " + trimForStatus(message.getAsString());
            }
        } catch (RuntimeException ignored) {
        }
        return ": " + trimForStatus(body);
    }

    private static String trimForStatus(String value) {
        String status = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return status.length() <= 96 ? status : status.substring(0, 93) + "...";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
