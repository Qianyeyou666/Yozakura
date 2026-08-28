package gq.yozakura.club;

public final class ClubConfigSummary {
    private final String id;
    private final String name;
    private final String visibility;
    private final String owner;
    private final String createdAt;
    private final String updatedAt;

    public ClubConfigSummary(String id, String name, String visibility,
                             String owner, String createdAt, String updatedAt) {
        this.id = id;
        this.name = name;
        this.visibility = visibility;
        this.owner = owner;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getOwner() {
        return owner;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
