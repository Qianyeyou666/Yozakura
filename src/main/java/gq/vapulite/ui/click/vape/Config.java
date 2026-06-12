package gq.vapulite.ui.click.vape;

/**
 * 配置数据模型类，用于保存和描述一个模块的配置状态。
 * <p>
 * 包含配置的名称、描述以及是否为位置类型的标记。
 * 配置可被序列化到 JSON 文件中进行持久化存储。
 */
public class Config {
    /** 配置项名称 */
    public String name;
    /** 配置项描述文本 */
    public String description;
    /** 是否为位置类型配置（如窗口坐标），默认 false */
    public boolean isLocation = false;

    /**
     * 构造一个配置对象。
     *
     * @param name        配置名称
     * @param description 配置描述
     * @param isLocation  是否为位置类型
     */
    public Config(String name, String description, Boolean isLocation) {
        this.name = name;
        this.description = description;
        this.isLocation = isLocation;
    }

    /** @return 配置描述文本 */
    public String getDescription() {
        return description;
    }

    /** @return 配置名称 */
    public String getName() {
        return name;
    }

    /** @param description 新的配置描述 */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @param name 新的配置名称 */
    public void setName(String name) {
        this.name = name;
    }
}
