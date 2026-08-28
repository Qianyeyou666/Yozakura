package gq.yozakura.module.combat;

enum AutoClickRandomization {
    NORMAL("Normal"),
    EXTRA("Extra"),
    EXTRA_PLUS("Extra+");

    private final String displayName;

    AutoClickRandomization(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
