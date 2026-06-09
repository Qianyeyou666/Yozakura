# VapuLite 更新报告 - 2026-06-10

## 本次更新重点

本次更新主要修复 ClickGUI/HUD 字体渲染异常，并继续完善 shader 绘制、毛玻璃面板、TargetHUD 和部分渲染模块的 OpenGL 状态管理。

## 主要改动

- 重写并稳定字体 atlas 生成流程，将字体纹理扩展到 1024 尺寸，减少大字号和图标采样越界导致的碎片字。
- 修复字体绘制时 OpenGL active texture 未固定到 texture0 的问题，避免字体采样到 lightmap、屏幕缓存或物品纹理。
- 修复毛玻璃 shader 捕获和绘制后的纹理状态恢复，避免 shader 影响后续字体和图片渲染。
- 去除多个渲染路径中的 `GL_TEXTURE_BIT` 状态恢复，降低 Minecraft `GlStateManager` 缓存与真实 OpenGL 状态不同步的概率。
- 扩展 `ShaderRenderer`，支持圆形、弧线、线段、圆形徽章、毛玻璃等 shader 绘制能力。
- 改进 `RenderUtil` 对 shader 绘制的调用与回退逻辑，让圆角、阴影、徽章、血条等 UI 绘制更稳定。
- 重新设计 TargetHUD 视觉样式，加入毛玻璃背景、圆形徽章、动态血条、头像/实体预览和攻击目标显示逻辑。
- 调整 `TargetESP` 与 `Chams` 的渲染状态保护，避免它们在世界渲染后污染 HUD/ClickGUI 字体纹理。
- 保留 ForgeGradle 旧项目构建方式，未升级 Gradle 或 JDK。

## 修复的问题

- 修复 ClickGUI 中模块名称、HUD 列表、数值项出现碎片化字体的问题。
- 修复大字号字体和图标在固定 512 atlas 下可能越界采样的问题。
- 修复毛玻璃 shader 绘制后绑定 0 号纹理导致后续文字异常的问题。
- 修复部分 3D/实体渲染模块使用 `GL_TEXTURE_BIT` 后可能造成纹理缓存不同步的问题。
- 改善 TargetHUD 血条、徽章和文字层在复杂 GL 状态后的渲染稳定性。

## 验证结果

- `.\gradlew.bat --no-daemon compileJava` 通过。
- `.\gradlew.bat --no-daemon build` 通过。
- `git diff --check` 通过，仅有 Windows 换行提示，无空白错误。
- 已确认新 class 打入 `build\libs\VapuLite.jar`。

## 构建产物

- `build\libs\VapuLite.jar`
- `build\libs\VapuLite-1.5.0.jar`
- SHA256: `9C2570A5EC9A18C05822C82243DD3BDDA511414ABC3E42857C7770DC5342E742`

## 后续建议

- 进游戏重新打开 ClickGUI，重点检查模块列表、右侧 HUD 列表、TargetHUD、图标和数值项字体是否仍有碎片。
- 如果仍出现中文缺字，应继续补充中文字体 fallback 或切换中文模式下的 UI 字体。
- 如果某些显卡上 shader 兼容性异常，可保留现有 GL11 fallback 路线继续加保护。
