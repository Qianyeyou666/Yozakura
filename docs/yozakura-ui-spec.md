# YozakuraUI 引擎规格

**状态**：已批准（阶段 0 完成，进入阶段 1）
**目标环境**：Minecraft Forge 1.8.9 / Java 8 / LWJGL 2 / OpenGL 2.1 兼容路径 / Gradle
**责任**：用户 + 本地 `gradlew.bat test` / `build` 校验路径
**阶段总数**：10（阶段 1 已合并原"选择器+ComputedStyle"内容；编号统一，不与早期概述混用）

---

## 1. 目标与非目标

### 1.1 目标

构建名为 **YozakuraUI** 的轻量级保留式 UI 引擎，让开发者用接近 HTML/CSS 的文件编写 Minecraft UI（ClickGUI、HUD、设置面板、通知等），运行时**不依赖** WebView2、Chromium、CEF、JavaFX WebView、QML 或外部浏览器。

引擎只实现 Minecraft UI 所需的 HTML/CSS 子集，不是完整浏览器内核。

### 1.2 非目标（MVP 不做）

- 完整浏览器规范与 DOM compatibility quirks
- 网络页面加载、`iframe`
- 通用浏览器 JavaScript API（第一版用 Java 事件绑定、数据绑定和受控表达式；通用 JavaScript/Rhino 作为后续独立阶段，不阻塞 MVP）
- CSS Grid
- 复杂文本排版
- 视频、音频、WebGL
- 全套 SVG 规范
- CSS filter 的任意组合
- 每帧生成整张 CPU 位图再上传纹理
- SDF/MSDF 字体（MVP 用 Java2D 栅格化 alpha atlas；MSDF 作为后续独立优化阶段）

---

## 2. 支持的 HTML/CSS 子集

### 2.1 HTML 子集

- 标签：`ui`、`div`、`span`、`p`、`button`、`input`、`img`、`label`、`template`（repeater）
- `input` 至少支持 `text`、`range`、`checkbox`
- `svg` 或项目自有矢量图标节点
- 属性：`id`、`class`、`style`、`data-*`、`type`、`src`、`data-repeat`、`data-bind`

### 2.2 CSS 选择器

- 标签选择器
- `.class`、多 class
- `#id`
- 后代选择器（` `）
- 直接子节点选择器（`>`）
- `:hover`、`:active`、`:focus`、`:checked`
- 简单属性选择器（`[name]`、`[name=val]`、`[name^=val]`）
- 优先级（specificity）、继承、source order

### 2.3 CSS 属性

**布局**：`display`（none/block/flex）、`flex-direction`、`flex-grow`、`flex-shrink`、`flex-basis`、`justify-content`、`align-items`、`align-self`、`gap`、`position`（relative/absolute）、`left`/`right`/`top`/`bottom`、`width`/`height`、`min-width`/`max-width`、`min-height`/`max-height`、`margin`、`padding`、`box-sizing`、`overflow`（visible/hidden/auto）、`z-index`

**视觉**：`color`、`background`、`background-color`、`opacity`、`border`、`border-radius`、`box-shadow`、`linear-gradient`、`font-family`、`font-size`、`font-weight`、`line-height`、`text-align`、`white-space`、`text-overflow`、`transform`（translate/scale）、`transform-origin`、`cursor`（由 Minecraft 自绘光标呈现）

### 2.4 单位

`px`、`%`、`vw`、`vh`、`em`、`rem`、`auto`

### 2.5 变量与主题

- 支持 CSS 自定义变量（如 `--accent`）
- 支持 `var(--accent)` 与默认值 `var(--accent, #fff)`
- 支持从 Java 实时修改根节点变量
- 修改变量后只使受影响节点失效

---

## 3. 架构和数据流

### 3.1 保留式管道

```text
HTML -> DOM
CSS -> Stylesheet
DOM + Stylesheet -> ComputedStyle
ComputedStyle -> LayoutBox
LayoutBox -> PaintCommandList
PaintCommandList -> 批量 OpenGL 渲染
```

解析、样式解析、布局、绘制、渲染、输入、动画、Minecraft 集成相互分离。

### 3.2 保留式约束

- DOM、样式、布局、绘制命令需缓存
- 只有相关属性变化时才重新计算
- 每帧可重放已有 GPU 绘制命令
- 静态 UI 不得每帧重新解析、重新布局或上传完整画布
- 字体图集只上传新增或改变的 glyph 区域

### 3.3 目录结构

```text
src/main/java/gq/yozakura/ui/engine/
  api/          UiEngine, UiDocument, UiElement, UiEvent, UiResourceLoader
  dom/          HtmlParser, DomNode, ElementNode, TextNode, AttributeMap
  css/          CssParser, CssTokenizer, Stylesheet, Selector, SelectorMatcher,
                ComputedStyle, StyleResolver, CssValue, CssVariableResolver, Specificity
  layout/       LayoutEngine, LayoutBox, FlexLayout, AbsoluteLayout, BlockLayout,
                MeasureContext, BoxConstraints
  paint/        PaintTreeBuilder, PaintCommand, PaintCommandList, ClipStack, PaintContext
  render/       OpenGlUiRenderer, QuadBatch, TextureAtlas, ImageCache, ShadowRenderer,
                VectorPathRenderer, GlStateSnapshot
  text/         FontManager, FontFace, GlyphAtlas, TextLayout, FontFallback, TextShaper
  input/        HitTester, PointerDispatcher, FocusManager, KeyboardDispatcher, PointerCapture
  animation/    AnimationClock, Transition, AnimatedProperty, TransitionRegistry
  binding/      ObservableValue, DataContext, RepeaterBinding, BindingPath
  minecraft/    MinecraftUiHost, MinecraftInputAdapter, MinecraftResourceLoader,
                MinecraftGlStateGuard, MinecraftViewport

src/main/resources/assets/yozakura/ui/
  clickgui/      index.html, style.css
  components/
  icons/
  shaders/
```

字体继续由资源加载器从 `assets/minecraft/font/` 读取；引擎不硬编码 Minecraft 字体目录，通过 `UiResourceLoader` 接收逻辑资源路径，不为新引擎重复复制已有字体。

测试位于 `src/test/java/gq/yozakura/ui/engine/`，命名 `*Test`（契约测试 `*ContractTest`）。

---

## 4. 公共 API

```java
UiEngine engine = UiEngine.builder()
    .resources(resourceLoader)
    .font("Inter", interBytes)
    .fontFallback("Alibaba Sans", cjkBytes)
    .build();

UiDocument document = engine.load(
    "/assets/yozakura/ui/clickgui/index.html",
    "/assets/yozakura/ui/clickgui/style.css"
);

document.setViewport(width, height, guiScale);
document.setVariable("--accent", "#E98BC1");
document.setDataContext(model);

document.on("module-toggle", event -> {
    model.toggleModule(event.getString("module"));
});

document.pointerMove(x, y);
document.pointerDown(x, y, button);
document.pointerUp(x, y, button);
document.wheel(x, y, delta);
document.key(keyCode, character, modifiers);

document.update(System.nanoTime());
document.render();
```

DOM、布局、渲染、Minecraft Host 必须解耦。

---

## 5. Dirty Propagation

五个独立 flag：

| Flag | 触发原因 | 传播范围 | 处理阶段 |
|---|---|---|---|
| `STYLE_DIRTY` | selector 状态变、CSS 变量变、继承属性变 | 该节点 + 依赖该变量/选择器的后代 | StyleResolver 重算受影响节点 ComputedStyle |
| `LAYOUT_DIRTY` | 尺寸/位置/字体度量/子几何变 | 该节点 + 父链 + 同 flex 容器兄弟 | LayoutEngine 局部重排 |
| `PAINT_DIRTY` | color/opacity/border/shadow/视觉内容变 | 仅该节点 | PaintTreeBuilder 重生成该节点 PaintCommand |
| `COMMANDS_DIRTY` | PaintCommandList 需整体重建 | 整树（罕见，如主题切换） | 全量 PaintTreeBuilder |
| `RESOURCE_DIRTY` | image/glyph/atlas 区域需上传 | 该资源 + 引用它的 Text/Image 命令 | OpenGlUiRenderer 增量上传 |

**传播算法**：
- 状态变化（如 hover）只标记命中节点 `STYLE_DIRTY`，StyleResolver 仅重算该节点及匹配 `:hover` 选择器的节点
- CSS 变量变更：维护 `variable → 使用节点集合` 反向索引，只失效这些节点
- 局部布局：标记后向上冒泡到最近 flex 容器，容器内重排，兄弟尺寸不变则不重算
- 静止帧：所有 flag 清零，`render()` 只重放 `PaintCommandList`

**性能计数器**（必须可见）：style 次数、layout ms、paint ms、draw calls、glyph uploads。

---

## 6. 字体与 Atlas 决策

### 6.1 栅格化

- 用 Java2D `Font`/`GlyphVector` 抗锯齿栅格化单个 glyph
- MVP **不做** SDF/MSDF 字体（MSDF 作为后续独立优化阶段）

### 6.2 Atlas 纹理

- OpenGL 2.1 优先使用 `GL_ALPHA8`/`GL_ALPHA` 单通道纹理，不使用浪费显存的 RGBA atlas
- 默认页面大小 1024×1024
- 使用多个固定页面，不频繁把 1024 纹理扩容复制到 2048
- 初始化时查询 `GL_MAX_TEXTURE_SIZE`
- 2048×2048 可作为可配置页面尺寸，但不硬编码假设所有环境都支持
- 新 glyph 只更新对应小矩形区域
- glyph 位置一旦分配保持稳定

### 6.3 字体管理

- 英文、中文 fallback 共用管理器，但允许分配到不同 atlas page
- 加入最大页数、LRU 或显式清理策略，防止 CJK glyph 无限增长
- 字体缩放优先使用合适字号重新栅格化或字号分级缓存，不依赖把低分辨率 glyph 无限放大

### 6.4 几何效果

- 圆角、边框、阴影使用独立的几何/SDF shader，不依赖字体 atlas
- MVP 不做 SDF 字体

### 6.5 字体系统必须处理

baseline、ascent、descent、line height、kerning、glyph advances、regular/bold faces、英文与 CJK fallback、Minecraft GUI Scale 与物理像素对齐、非整数 UI scale、atlas 增长与有界缓存。

新 glyph 上传只更新变化区域。默认尺寸 UI 文本应清晰映射物理像素。视觉验证必须包含常见 GUI Scale 下的小号文字。

---

## 7. GL 状态边界

### 7.1 线程约束

- 所有 OpenGL 创建/更新/删除在 Minecraft 渲染线程执行
- 资源释放显式、幂等，不依赖 finalizer
- 不在非渲染线程创建/删除 GL 对象

### 7.2 渲染前后必须保存并恢复

- framebuffer
- viewport
- projection/modelview matrix
- texture binding
- active texture unit
- blend
- alpha test
- depth test/depth mask
- scissor
- stencil
- shader program
- color

### 7.3 合批与裁剪

- 相同纹理、着色器、裁剪状态的命令合批
- 支持嵌套 clip/scissor
- 圆角裁剪可用 stencil 或 SDF，但必须保存和恢复状态
- 阴影用缓存 mask、SDF 或局部 FBO，不得模糊整屏
- 背景必须真正透明
- 嵌套裁剪用经过验证的 scissor/stencil 栈；圆角裁剪、阴影、FBO 效果必须限定在 UI 元素内，不得意外模糊或覆盖 Minecraft framebuffer

### 7.4 现有组件复用

- 现有 [GLStateManager](file:///d:/VapuLite-main/src/main/java/gq/yozakura/engine/render/GLStateManager.java) 提供 scissor 栈与部分状态同步，但未覆盖 viewport/projection/shader program
- 阶段 4 新建 `GlStateSnapshot` 显式补齐缺失项，契约测试验证渲染前后 10 项状态完全一致

---

## 8. 性能指标

参考场景：960×640 ClickGUI、100 个模块、约 500 个可见节点。

静止状态：
- 不重新解析 HTML/CSS
- 不重新布局
- 无完整 CPU 画布上传
- 每帧不产生大量临时对象

性能目标（本地开发机，热身后）：
- 普通布局更新 <2ms
- PaintCommand 重放 <2ms
- 鼠标 hover 不触发整棵 DOM 重建
- 切换一个模块只使相关节点和依赖样式失效
- 滚动时不重建不可见模块
- 字形和图片使用 atlas/cache

必须提供性能计数器：style、layout、paint、draw calls、glyph uploads。

优化必须基于证据。引入复杂缓存前先加计数器或基准。

---

## 9. 测试策略

### 9.1 位置与命名

- 测试位于 `src/test/java/gq/yozakura/ui/engine/`
- 命名 `*Test`，跨层不变量用 `*ContractTest`

### 9.2 纯逻辑测试至少覆盖

- HTML 解析与有用语法错误
- CSS tokenizer/parser
- 选择器匹配和优先级
- 继承和 CSS 变量
- margin/padding/border box
- flex row/column
- 百分比与绝对定位
- min/max constraints
- overflow clip
- z-index 与 paint order
- 文本测量缓存
- hover/active/focus 状态
- 左右键事件
- 拖动捕获
- 动画时钟
- dirty propagation
- GUI Scale 坐标映射
- OpenGL 状态守卫契约

不依赖截图快照代替布局数值测试。可生成视觉预览，但核心行为必须有确定性测试。

### 9.3 工作流

每个行为变更：
1. 先加失败测试
2. 跑并确认预期失败
3. 实现最小完整行为
4. 跑测试通过
5. 测试保持绿色时才重构
6. 跑相关测试切片
7. 检查 diff，`git diff --check`

### 9.4 命令

```powershell
# 聚焦测试
.\gradlew.bat test --tests "gq.yozakura.ui.engine.*" --offline --no-daemon
# 全量测试
.\gradlew.bat test --offline --no-daemon
# 测试通过后构建
.\gradlew.bat build -x test --offline --no-daemon
```

不把编译当作游戏内渲染正确性的证明。`runClient`/`runServer` 除非用户明确允许，否则不运行。

---

## 10. 各阶段任务和验收条件

每阶段一个垂直切片，先加失败测试再实现，结束时跑聚焦测试。共 **10 个阶段**。

| 阶段 | 范围 | 验收标准 |
|---|---|---|
| **1** | DOM + CSS 解析 + 选择器 + specificity + 继承 + var() + ComputedStyle（纯离线，无 GL/MC/布局/字体/输入/动画） | HTML 解析正确处理嵌套/属性/自闭合；未知标签报错带行列号；CSS tokenizer 覆盖注释/字符串/单位；标签/class/id/后代/子节点选择器匹配；specificity 排序正确；`:hover` 仅 hover 时匹配；`var()` 跨 `:root` 解析与默认值；基础继承（color/font 等）；修改变量只失效依赖节点（计数器验证） |
| **2** | 布局引擎 | 960×640 / 500 节点 / 100 模块场景单次增量布局 <2ms；flex row/column 主轴/交叉轴对齐正确；min/max 约束生效；overflow:hidden 裁剪 |
| **3** | Paint 树 + 命令列表 | PaintCommand 顺序符合 z-index 与 DOM 顺序；clip 嵌套正确；静止帧命令 generation 不变 |
| **4** | OpenGL 保留渲染 | 重放 500 节点命令 <2ms；draw calls 数 ≤ 期望（按 shader/texture/clip 合批）；GL 状态守卫契约测试：渲染前后 10 项状态完全一致 |
| **5** | 字体 atlas + 文本布局 | glyph 首次出现才上传；重复字符零上传；CJK fallback 链生效；相同字符串二次测量命中缓存 |
| **6** | 输入系统 | 右键事件 button=2 不被过滤；capture 期间移出元素仍收 move/up；Tab 切换焦点顺序正确 |
| **7** | 动画 transition | transition 140ms 在 ~140ms 完成；中途改变目标值可反向；动画期间持续刷新，完成后停止重布局 |
| **8** | 数据绑定 + repeater | repeater 数据变化只重建对应子树；Module.toggle 反映到 checkbox；setVariable(--accent) 只失效使用节点 |
| **9** | Minecraft host 集成 | 背景透明、世界可见；ESC 关闭后焦点/光标恢复；GUI Scale 2/3/4 下坐标一致 |
| **10** | ClickGUI 落地 | 真实 ClickGUI 可用：开关模块、右键设置、搜索过滤、滚动、文本输入、拖拽窗口、缩放；无 WebView2/Skia/全帧 CPU 上传 |

**阶段 1 不包含**：flex/box layout、OpenGL、字体 atlas、输入、动画、Minecraft 集成、通用 JavaScript、ClickGUI 迁移。

每阶段结束运行：
```powershell
.\gradlew.bat test --offline --no-daemon
.\gradlew.bat build -x test --offline --no-daemon
```

---

## 11. 风险与 Not Doing

### 11.1 主要风险

| 风险 | 可能性 | 影响 | 缓解 |
|---|---|---|---|
| 字体无 atlas（现有 `UnicodeGlyphCache` 每 glyph 一个 `DynamicTexture`，无法合批） | 高 | draw calls 爆炸 | 阶段 5 新建 `GlyphAtlas`（单通道 alpha 纹理，按需区域上传） |
| GL 状态守卫不完整（现有 `GLStateManager` 未保存 viewport/projection/shader program） | 中 | 渲染后污染 MC 主渲染 | 阶段 4 `GlStateSnapshot` 显式补齐，契约测试 |
| Java2D 栅格化线程（`GlyphVector` 非渲染线程调用可能触发 bug） | 中 | 偶发崩溃 | 所有 glyph 栅格化在 render thread 首次绘制时惰性触发，不在加载期批量 |
| 局部布局失效边界错误 | 中 | hover 触发全树重排 | 阶段 2 计数器测试：hover 单节点只标记 N 个节点 |
| non-integer GUI Scale 文字模糊 | 中 | scale=1.5 文字糊 | 字号分级缓存，参考 `CFontRenderer.scaleCompensation` |
| stencil 圆角裁剪与 scissor 混用状态泄漏 | 中 | 后续元素渲染异常 | `ClipStack` 严格 push/pop 配对，契约测试每帧结束栈为空 |
| repeater 数据变更触发全子树重建 | 中 | 滚动卡顿 | 阶段 8 key-based diff，仅增删变化项 |
| Java 8 无 `var`/`record`/Stream 性能保证 | 低 | 代码冗长/性能 | 避免 per-frame stream/boxing，用 for 循环与原始数组 |
| AGENTS.md 禁止 runClient | 中 | 无法在游戏内验证 | 阶段 1-8 纯逻辑测试；阶段 9-10 列出需用户手动 MC 验证项 |

### 11.2 Not Doing（MVP）

**架构层**：
- 完整浏览器规范、DOM compatibility quirks
- 网络页面加载、`iframe`、通用 JS API
- WebView2 / Chromium / CEF / JavaFX WebView / QML / Skia / Rhino 通用执行
- CSS Grid
- 任意 CSS filter 组合
- 视频、音频、WebGL
- 全套 SVG 规范
- 每帧整张 CPU 位图上传
- SDF/MSDF 字体（后续独立阶段）

**实现层**：
- 不复用 `qml4j`/`skija` 作为引擎后端（依赖保留但引擎不调用）
- 不复用 `UnicodeGlyphCache` 的 per-glyph-texture 模式（阶段 5 替换为 atlas）
- 不在加载期批量栅格化所有 glyph（惰性按需）
- 不用 finalizer 释放 OpenGL 资源
- 不在非渲染线程创建/删除 GL 对象
- 不把 GUI Scale 转换散落到组件（集中在 `MinecraftViewport`）
- 不把右键当左键处理
- 不加静默降级（字体/着色器/布局/资源加载失败 → 明确错误）
- 不删除或覆盖用户已有的无关修改
- 不用 RGBA 字体 atlas 浪费显存（用 `GL_ALPHA8`）

### 11.3 依赖策略

MVP 零新增依赖。所有解析器、布局、glyph atlas 自研并放入 `gq.yozakura.ui.engine`。引入任何依赖前必须报告：精确 artifact 与版本、Java 8 兼容性、传递依赖、运行时与 JAR 体积影响、为何内部实现不足、license 与资源加载影响，并在 `build.gradle` 锁定版本。

---

## 12. 旧 GUI 的迁移/清理策略

### 12.1 开发与验收期

- 保留旧 ClickGUI 代码（`YozakuraClickGui`、QML、WebView2），用于人工对照和回归排查
- 默认入口最终切换到 YozakuraUI
- **不允许**初始化失败后自动切换到旧 ClickGUI
- 新引擎失败时显示明确错误并关闭当前界面
- 旧实现只能通过显式开发选项或独立调试入口打开
- 不把旧 GUI 称为运行时 fallback
- 不在实现新引擎过程中删除 QML、WebView2 或旧 `YozakuraClickGui`

### 12.2 验收后

新引擎完成实机验收后，建立独立清理任务决定删除哪些旧实现。清理任务不阻塞引擎落地。

### 12.3 Fallback Policy

不添加静默降级。解析器、着色器、字体、纹理、渲染器失败必须识别资源与根因。不悄悄从自定义引擎切换到 QML 或 WebView2。

---

## 13. 定义完成

一个阶段完成仅当：
- 已批准的验收标准满足
- 新行为有聚焦测试
- 相关测试通过
- 项目构建成功
- 未引入不支持 fallback
- 未覆盖无关用户修改
- 修改文件与已知限制已报告
- 任何未验证的 Minecraft/OpenGL 行为已明确列出待手动测试

最终引擎完成仅当：真实 Minecraft ClickGUI 可用分离的 HTML/CSS 文件创作，支持透明渲染、真实模块数据、开关、右键设置、搜索、滚动、文本输入、拖拽、缩放、动画、字体 fallback 与配置持久化，且不依赖 WebView2 或全帧 CPU 纹理上传。
