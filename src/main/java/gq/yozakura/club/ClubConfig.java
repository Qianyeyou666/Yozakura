package gq.yozakura.club;

import com.google.gson.JsonObject;

public final class ClubConfig {
    private final ClubConfigSummary summary;
    private final JsonObject payload;

    public ClubConfig(ClubConfigSummary summary, JsonObject payload) {
        if (summary == null || payload == null) {
            throw new IllegalArgumentException("Cloud config summary and payload are required");
        }
        this.summary = summary;
        this.payload = payload;
    }

    public ClubConfigSummary getSummary() {
        return summary;
    }

    public JsonObject getPayload() {
        return payload;
    }
}
