package gq.vapulite.Vapu;

public enum ModuleType{
    Combat("Combat","战斗类"),
    Render("Render", "视觉类"),
    Movement("Movement", "移动类"),
    Player("Player", "玩家类"),
    World("World", "世界类"),
    Other("Other", "其他"),
    Config("Global", "全局设置");

    private final String name;
    private final String ChineseName;

    ModuleType(String name, String chineseName) {
        this.name = name;
        this.ChineseName = chineseName;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        if(Client.CHINESE){
            return ChineseName;
        } else {
            return name;
        }
    }
}
