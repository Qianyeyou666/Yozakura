package gq.yozakura.ui.engine.api;

/**
 * 资源加载异常：HTML/CSS 文件加载或解析失败时抛出。
 *
 * <p>AGENTS.md "Fallback Policy"："Do not add silent fallbacks. A parser, shader, font,
 * texture or renderer failure must identify the resource and root cause."
 *
 * <p>异常消息始终包含资源路径，便于定位。
 */
public class ResourceLoadException extends RuntimeException {

    private final String resourcePath;

    public ResourceLoadException(String resourcePath, String message) {
        super(message);
        this.resourcePath = resourcePath;
    }

    public ResourceLoadException(String resourcePath, String message, Throwable cause) {
        super(message, cause);
        this.resourcePath = resourcePath;
    }

    /** 触发异常的资源路径。 */
    public String resourcePath() {
        return resourcePath;
    }
}
