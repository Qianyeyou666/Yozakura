package gq.yozakura.club;

public final class ClubUser {
    private final String id;
    private final String username;
    private final String createdAt;

    public ClubUser(String id, String username, String createdAt) {
        this.id = id;
        this.username = username;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
