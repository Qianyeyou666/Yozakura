package gq.yozakura.core;

public enum ClientLanguage {
    ENGLISH(false, "English"),
    CHINESE(true, "中文");

    private final boolean chinese;
    private final String displayName;

    ClientLanguage(boolean chinese, String displayName) {
        this.chinese = chinese;
        this.displayName = displayName;
    }

    public boolean isChinese() {
        return chinese;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String select(String english, String chinese) {
        return this.chinese ? chinese : english;
    }
}
