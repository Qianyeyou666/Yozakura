package gq.yozakura.module.render.hud;

public final class ModuleListLabel {
    public final String name;
    public final String parameter;
    public final String key;

    public ModuleListLabel(String name, String parameter, String key) {
        this.name = name == null ? "" : name;
        this.parameter = parameter == null ? "" : parameter;
        this.key = key == null ? "" : key;
    }

    public String fullText() {
        String text = name;
        if (parameter.length() > 0) {
            text += " " + parameter;
        }
        if (key.length() > 0) {
            text += " " + key;
        }
        return text;
    }
}
