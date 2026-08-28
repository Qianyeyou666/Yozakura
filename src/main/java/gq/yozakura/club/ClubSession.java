package gq.yozakura.club;

public final class ClubSession {
    private final String token;
    private final String username;

    public ClubSession(String token, String username) {
        this.token = requireText(token, "Club token");
        this.username = requireText(username, "Club username");
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }
}
