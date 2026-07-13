# Yozakura 视觉优化与 Glow 重构规格

## 1. 目标

本规格用于指导 Yozakura 的视觉系统优化。范围包括 HUD、ClickGUI、TargetHUD、TargetESP、ESP、StorageESP、通知、药水、键盘显示以及相关 2D/3D 视觉模块；KillEffect 按当前需求不纳入本轮。

目标不是复制 Drip Lite 或其它客户端，而是吸收其优秀的信息层级、连续字体光晕、克制的暗色胶囊、稳定圆角边缘和紧凑排版，并保留 Yozakura 的樱花辨识度、已有配置和交互习惯。

本轮实际代码范围：

- 重写 2D glow 基础设施。
- 修复导致软边被裁掉的 Alpha Test 状态。
- 给现有 HUD/字体提供可复用 glow API 和最小接线。
- 其余视觉改造按本文任务清单交由 Terra 分阶段实施。

## 2. 已确认的问题

### 2.1 当前字体 glow 不是模糊

`HUD`、`SakuraClickGui`、`Notification`、`Scaffold` 和 `InjectionSuccessAnimation` 通过在多个偏移位置重复绘制同一段文字制造 glow。该做法会产生十字或对角重影、字形发胖和离散锯齿，无法得到连续 halo。

`CFontRenderer` 还会把坐标吸附到 0.5 像素网格，因此 `0.65/0.88` 一类偏移会被量化，重影更明显。

### 2.2 低透明 glow 会被裁掉或闪亮

- `ShaderRenderer.beginProgram()` 和 `CFontRenderer` 当前启用了 Alpha Test。
- Minecraft 1.8.9 常用阈值为 `GL_GREATER, 0.1`，alpha 小于约 26/255 的软边会被直接 discard。
- `CFontRenderer` 为兼容 24-bit RGB 颜色，会把 alpha 0..3 当作未指定 alpha 并提升为 255；动画尾段可能出现瞬时不透明副本。

新 glow 必须使用不透明字形 mask，并在 mask/composite uniform 中连续乘动画强度，不能继续用低 alpha 重绘字形。

### 2.3 面板 glow 与 shadow 混用

`drawGlowAround()` 实际调用的是 rounded shadow。Shader 路径只使用 spread，`layers` 参数无效；fallback 路径则重复绘制多个实心圆角矩形，两条路径外观不一致。

后续语义必须拆开：

- `shadow`：有方向、用于层级与悬浮关系，通常为黑色或中性色。
- `glow`：无方向、跟随发光源轮廓，通常使用 accent 或 semantic color。
- `outline`：1 physical px 左右的清晰边界，不负责扩散。

### 2.4 RoundRect 软边随 GUI scale 改变

现有 `EDGE_SOFTNESS = 0.75` 使用逻辑坐标，GUI scale 改变后物理软边宽度不稳定。圆角覆盖率应围绕轮廓中心计算，并优先使用 derivative-aware AA：

```glsl
float aa = max(fwidth(distance), 0.35);
float coverage = 1.0 - smoothstep(-aa, aa, distance);
```

如果目标驱动不支持可靠 derivative，则由 Java 传入基于 scale factor 的 `aaWidth`，但不能继续固定逻辑像素 softness。

## 3. 技术栈与命令

- Minecraft Forge 1.8.9
- Java 源码兼容 Java 8 bytecode
- LWJGL 2 / OpenGL 2.0 / GLSL 1.20
- Minecraft `Framebuffer`，优先兼容 ARB/EXT FBO，不把 OpenGL 3.0 作为唯一条件
- JUnit 4.13.2 用于纯 Java 数学和资源契约测试

验证命令：

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.gradle\jdks\eclipse_adoptium-21-amd64-windows\jdk-21.0.11+10'
.\gradlew.bat test --tests 'gq.yozakura.engine.render.glow.*' --console=plain
.\gradlew.bat build --console=plain
```

禁止在未获用户明确许可时运行 `runClient` 或 `runServer`。

## 4. 新 Glow 架构

### 4.1 渲染流程

```text
正常绘制 HUD / ClickGUI，同时 queue glow source
                    ↓
RGBA mask FBO 重放 text / rounded rect / texture mask
                    ↓
            horizontal Gaussian blur
                    ↓
             vertical Gaussian blur
                    ↓
composite 到调用前绑定的 framebuffer，并抑制原始 mask core
```

核心约束：

- 一帧按 profile/radius 分组批处理，不能每段文字单独创建并模糊全屏 FBO。
- mask 使用 premultiplied RGBA，使一批命令可以拥有不同颜色。
- blur 禁用 Alpha Test 和 blending，卷积 RGBA/alpha，输出不能强制 alpha=1。
- composite 使用原始 mask 抑制清晰字形核心，允许在正常 HUD 之后合成而不洗白字体。
- FBO 纹理必须使用 `GL_LINEAR + GL_CLAMP_TO_EDGE`，避免屏幕边缘镜像 ghost。
- 保存并恢复 framebuffer、viewport、program、active texture、texture binding、matrix、scissor、blend/alpha/depth 状态。
- shader/FBO 初始化失败必须明确报错，不允许静默切回旧的 offset glow。

### 4.2 API 合同

建议公共入口：

```java
RenderServices.glow().beginFrame();

font.drawGlowString(text, x, y, glowColor, strength, GlowProfile.TEXT);
font.drawStringWithGlow(text, x, y, textColor, glowColor, strength, GlowProfile.TEXT);

RenderServices.glow().queueRoundedRect(
        x, y, x2, y2, radius,
        glowColor, strength, GlowProfile.ACCENT);

RenderServices.glow().flush();
```

`GlowProfile` 只描述视觉意图，不暴露底层 pass：

- `TEXT`：字体、图标，逻辑半径约 2.5-3.5。
- `ACCENT`：状态点、滑块 thumb、焦点描边，逻辑半径约 3.5-5。
- `PANEL`：可选的大范围面板 halo，逻辑半径约 6-10，必须降采样。

质量档位只影响内部 downscale、采样半径或 pass 数量：

- `LOW`：0.35-0.50 downscale，单层 Gaussian。
- `MEDIUM`：0.50 downscale，推荐默认。
- `HIGH`：0.65-0.75 downscale，最多 near/far 两层。

模块不得直接持有 FBO 或 shader program。

### 4.3 Gaussian kernel

- kernel 必须正规化：`w0 + 2 * sum(w1..wr) = 1`。
- 半径 0 为 impulse。
- 权重非负、从中心单调递减。
- 建议 `sigma = radius / 2`；最大物理半径限制在 24-32，超大 halo 改用 downsample/Kawase，而不是 256 权重大循环。

### 4.4 与动画的关系

Glow 强度必须直接使用连续动画值：

```text
glowStrength = visibility ^ 1.35 * configuredStrength
```

`0.001` 不能被抬升为 `0.1`；alpha=0 的命令不能分配 target 或进入 pass。动画淡出尾段应连续归零，不得闪烁。

## 5. 视觉系统：Yozakura Night Bloom

### 5.1 设计原则

- 紧凑而不是拥挤：信息密度高，但同类信息使用稳定 baseline 和间距。
- 发光是强调，不是底色：只让品牌名、启用模块名、焦点状态、关键数值发光。
- 阴影、描边、glow 三层分工明确。
- 圆角半径由组件高度决定，不允许所有组件统一大圆角。
- 动画表达状态变化，不做无意义持续漂浮。
- 2D HUD 在场景后处理之后绘制，不能被世界 motion blur 再次模糊。

### 5.2 Geometry tokens

```text
spacing: 2 / 4 / 6 / 8 / 12 / 16
radius-xs: 3
radius-sm: 5
radius-md: 7
radius-lg: 9
pill: height / 2
border: 1 physical px（逻辑宽度 = 1 / scaleFactor）
```

圆角卡片建议分层：

1. 中性 drop shadow：低 alpha、轻微 y offset。
2. 半透明 surface。
3. 1 physical px subtle border。
4. 顶部或左上极弱 inner highlight。
5. 仅在 active/focus 时添加 accent glow。

### 5.3 Typography

- 品牌/模块主名：Tenacity Bold 或项目内可用的 semibold 字体。
- 正文/参数/数值：Inter。
- 中文 fallback：Alibaba Sans。
- icon font 只用于已存在且对齐可靠的 glyph。
- 文字大小采用 12 / 14 / 16 / 18 的有限层级。
- 参数与 key 使用 muted color，不和 module name 同强度 glow。

## 6. 全局 Palette

### 6.1 语义 token

新增统一 `VisualPalette`，替代 HUD、UiTheme、MaterialClickTheme、GuiPalette 中重复硬编码。至少包含：

```text
canvas
surface
surfaceRaised
surfaceOverlay
textPrimary
textSecondary
textDisabled
borderSubtle
borderFocus
accentPrimary
accentSoft
accentAlt
info
success
warning
danger
healthHigh
healthMid
healthLow
healthDamageTrail
entityPlayer
entityMob
entityAnimal
entityInvisible
entityHurt
storageChest
storageEnderChest
glowPrimary
glowSecondary
shadow
```

Night Bloom 默认建议：

```text
canvas          #08090D
surface         #0E1016
surfaceRaised   #161821
textPrimary     #F6F3F8
textSecondary   #AAA4AF
accentPrimary   #FF55B5
accentSoft      #FF9BD5
accentAlt       #78D8FF
success         #62E6A7
warning         #FFC766
danger          #FF667D
```

### 6.2 兼容现有颜色配置

可着色模块增加：

```java
enum PaletteBinding {
    LEGACY,
    FOLLOW_GLOBAL,
    CUSTOM,
    RAINBOW
}
```

- 新 key 缺失时默认 `LEGACY`，旧配置行为不变。
- `CUSTOM` 复用现有 Red/Green/Blue/Alpha values。
- 不删除现有 `rainbow` key。
- Night Bloom preset 可以显式切为 `FOLLOW_GLOBAL`。
- 模块详情只在 `CUSTOM` 时展开 RGB/alpha。

### 6.3 Palette 编辑器

ClickGUI 主题页增加：

- 预设 swatches：Night Bloom / Sakura Light / Mono / Custom。
- 当前主色、辅色、危险色、信息色和 surface 实时预览。
- 拖动 color picker 时写 preview state；mouse-up 才提交并保存。
- Escape/取消恢复进入编辑前 snapshot。
- 支持“跟随全局 / 模块独立覆盖”。
- 可选保存最近 6 个颜色历史；不要复制参考客户端的品牌资产。

## 7. 动画系统

### 7.1 基础设施

新增单一 `UiClock`，每帧只计算一次：

```text
dt = clamp(realDeltaSeconds, 0, 0.05)
```

`MotionValue` 必须支持：

- tween 和 critically damped spring。
- 从当前值连续反向，不能重置到 0/1。
- 相同目标不重复启动。
- 页面暂停或卡顿后最大只推进 50ms。
- `FULL / REDUCED / OFF` 三档动画强度。

禁止按固定每帧增量推进；60/144/240 FPS 下相同时刻的状态应接近。

### 7.2 Motion tokens

```text
hover/color             90-120ms  OUT_CUBIC
toggle                  140ms     IN_OUT_CUBIC
selection indicator     160-180ms OUT_CUBIC
dropdown/expand         180-220ms OUT_CUBIC
page transition         200-240ms OUT_CUBIC
window open             220ms     OUT_CUBIC，scale 0.97 -> 1
window close            150-170ms IN_CUBIC
notification enter      180ms     OUT_CUBIC
notification exit       140ms     IN_CUBIC
target switch           120-160ms crossfade
reorder                 spring    no overshoot 或极轻 overshoot
```

### 7.3 ArrayList 动画

每个模块保留独立状态：

```text
visibility
xOffset
rowHeight
currentY
targetY
currentWidth
targetWidth
```

- enable：从右侧 8-12px 滑入，alpha 和 row height 同步展开。
- disable：先执行约 140ms 退出，再从渲染列表删除。
- 排序变化：Y 使用 spring，不瞬移。
- width 改变：胶囊宽度平滑调整，避免参数变化抖动。
- glow 强度跟随 `visibility ^ 1.35`。

## 8. HUD 方案

### 8.1 新增可选 `HudStyle.NIGHT_BLOOM`

保留 `YOZAKURA` 和 `OLD`，新 enum 只追加，不重命名旧值。

#### 左上 Watermark

参考 Drip Lite 的信息拆分，但使用 Yozakura 品牌：

- logo chip：约 20-22 高。
- brand/version chip：品牌 accent semibold，版本 muted。
- FPS/延迟 chip：中性文本，数值变化时宽度 spring。
- chip gap 4，屏幕 inset 6。
- 暗色半透明 surface + subtle inner highlight。
- 只给 logo/品牌名 accent glow；版本与 FPS 不强 glow。

#### 右上 Module List

- 整体右对齐，按内容宽度形成阶梯。
- 每行 17-19 高，gap 1-2。
- module name 使用 accent；parameter/key 使用 muted。
- 背景为独立小胶囊，不使用一个覆盖整列的大面板。
- 可选右侧 1-1.5px accent rail 或状态点，二者只选一种。
- 同屏最多建议 22 条；超过后按优先级或滚动/裁剪处理。

#### 其它 HUD 组件

- Potion：图标、名称、时间三列对齐；剩余时间低于阈值才使用 warning。
- Inventory：保留现有拖拽与缩放，槽位 hover/低耐久使用 semantic color。
- KeyboardDisplay：pressed 使用 accentPrimary，released 使用 surface/borderSubtle。
- Health：high/mid/low 使用 palette health token，伤害延迟条单独使用 healthDamageTrail。
- Notification：图标 well + title/message + 底部进度线；不同类型用 info/success/warning/danger。

建议把 `HUD.java` 保留为 settings + lifecycle，逐步拆出：

```text
HudContext
HudLayoutManager
NightBloomWatermarkRenderer
NightBloomArrayListRenderer
PotionHudRenderer
InventoryHudRenderer
```

## 9. ClickGUI 方案

### 9.1 约束

- 以现有设计精修为主，不大幅改变信息架构。
- 保留导航、搜索、模块列表、详情面板、侧栏、设置交互和所有配置 ID。
- 不把 Material、Yozakura、Sakura 三套现有样式合并成一种。
- 较大创意只能作为新的 `GuiStyle.NIGHT_BLOOM` 可选 mode。

### 9.2 Yozakura 精修

- 统一 `GuiPalette/UiTheme/HudPalette` 到 `VisualPalette` snapshot。
- 导航 indicator 使用 160-180ms spring/tween，不瞬移。
- 模块卡片降低无意义阴影层数，hover 只改变 surface、border 和 1-2px y/scale。
- 选中卡片使用 borderFocus + 轻 glow，不使用大面积高饱和底色。
- Detail values 统一 30px rhythm；label/value baseline 一致。
- Dropdown 展开从当前高度连续动画，关闭时仍保留内容到动画结束。
- 滚动使用速度 + 阻尼，边界做 soft clamp，不回弹穿透。

### 9.3 Material 精修

- 保留侧栏、网格、展开卡片。
- 去除固定 MD3 紫色依赖，改为 palette accent。
- active/hover 卡片不同时叠加重阴影、亮边和大色块。
- 展开内容高度、scroll range 和 hitbox 使用同一个 animated height。

### 9.4 Sakura 精修

- 保留当前布局、花瓣和 glass identity。
- 所有手写 offset text glow 改用新 queue。
- 花瓣数量随 quality/animation level 调整；Reduced Motion 下只保留静态角落装饰。
- glow 只用于标题、selected module、active toggle 和焦点 icon。

### 9.5 Web ClickGUI

- CSS token 从游戏 palette 同步。
- focus visible 必须清晰。
- 遵循 `prefers-reduced-motion`。
- 不在每个控件上堆叠 blur、shadow、gradient。

## 10. TargetHUD

保留现有 target resolve、拖拽、scale、Attack/Aura target 生命周期。新增 `TargetHudStyle.NIGHT_BLOOM`（与旧样式并存，不改旧 enum 语义），不把逻辑重新塞回 KillAura。Night Bloom 不是只给左上/右上 HUD 的皮肤：TargetHUD 必须使用同一套 `VisualPalette`、surface/border/glow 层级、字体层级和 motion tokens。

建议尺寸约 190x48：

- 左：26-30px avatar，hurt 时短暂 danger tint。
- 中：姓名、距离/延迟、主 health bar + damage trail。
- 右：可选 health 数字或 armor indicator。
- 进入：alpha + y(6->0) + scale(0.96->1)，180ms。
- 退出：140ms，旧 target snapshot 保留到完成。
- target switch：old/new snapshot 120-160ms crossfade，health motion 不从 0 重置。
- health、damage trail、distance 数字分别保留连续状态：health/damage 用无过冲 spring，数值宽度用 100-140ms tween，portrait hurt tint 在 140ms 内淡回基础色。
- 仅姓名/焦点 accent glow；普通白字不 glow。

### 10.1 HUD / TargetHUD 拖拽升级

- 保留现有 `HudDrag`、TargetHUD 坐标、scale 和配置 key；不把位置存储格式改成另一套。
- 引入 `DragState {IDLE, ARMED, DRAGGING, SNAP_PREVIEW, RELEASE}`：pointer-down 只记录 snapshot，移动超过 3 logical px 才开始拖拽，Escape 恢复 snapshot，mouse-up 一次性提交并保存。
- 拖拽时显示 1 physical px `borderFocus`、轻量 accent glow 和半透明原始位置 ghost；`REDUCED/OFF` 动画模式仍保留边界与 snap 反馈，只取消回弹动画。
- 吸附目标包括屏幕 safe inset、水平/垂直中心线、同类 HUD widget 边缘；距离不超过 6 logical px 时进入 preview，继续拖动可立即脱离，不能锁死鼠标。
- release 使用 100-140ms 无过冲 spring 回到最终 snap 位置；每帧按 real `dt` 更新，不能用固定帧增量。坐标保存/恢复必须在 GUI scale 1-4 间无漂移。

## 11. TargetESP

保留现有 mode、目标筛选、through-walls、Aura/Crosshair/Backtrack 选项。新增 `WorldVisualStyle.NIGHT_BLOOM`，将 `TargetESP`、`ESP`、`StorageESP` 与 `Chams` 统一接入同一语义 palette 和强度预算；2D glow queue 不用于世界特效，世界 glow 继续由独立 billboard/ribbon renderer 负责。

颜色映射：

- VAPE/RINGS：accentPrimary + health semantic。
- COSMIC：accentPrimary -> accentAlt。
- AURORA：info -> accentAlt。
- SAKURA：accentPrimary -> accentSoft。
- hurt：始终 danger。

动画与性能：

- visibility 使用 `MotionValue`，目标丢失后完成退出再清理。
- acquire：120ms alpha/scale(0.92->1)；lost：140ms fade；target switch：旧目标在 120ms 内淡出、新目标从当前 visibility 连续接管，不能闪回 0。
- ring/ribbon 相位连续推进，半径、线宽、emissive strength 与 health/distance 用 spring 或 tween 平滑过渡；目标瞬移时位置仍由 MC partialTicks 插值，不引入额外世界坐标滞后。
- 粒子相位由 entity id + normalized time 决定，不每帧重新随机。
- segment LOD：近 64、中过渡 32、远 16。
- 同时出现的 ribbon、ring、orb 必须有 overdraw budget；不能所有层满强度。
- health arc 在 low health 时切换 warning/danger，而不是全程同色。

## 12. 不纳入本轮：KillEffect

按当前需求，KillEffect 不新增 Night Bloom 样式、动画或配置；保留现有行为，避免把它混入 HUD / TargetESP 的视觉重构范围。

## 13. ESP / StorageESP / Chams

### ESP

- 保留 Outline/Filled/Both 和现有目标筛选。
- 在现有 ESP mode enum 末尾追加 `GLOW` / `GlowESP` mode，不重命名或移除旧值。它先将已筛选实体写入世界空间 mask，再做 outline + bloom composite；不得通过重复 offset box 或重复实体渲染伪造发光。
- Player/Mob/Animal/Invisible/Hurt 使用 palette semantic token。
- fill alpha、outline alpha、line width 分开配置。
- 可后续增加 `Elements {BOX, HEALTH, NAME, ARMOR, HAND}`，但不在本轮强行扩张。
- entity animation 以 entity id 为 key；进出视野、死亡、世界切换必须清理。
- hurt pulse 只短暂覆盖为 danger，不永久改变基础颜色。
- `EntityVisualMotion` 至少持有 visibility、fillAlpha、outlineAlpha、healthDisplay 和 lastSeen；首次可见 140ms fade/scale，离开视野 120ms fade 后释放，health bar 180ms 无过冲 spring，name/armor 按同一 visibility 连续淡入淡出。
- Box/2D projection 只使用实体已有 prev/current position + partialTicks；不做每帧随机抖动。HUD-only animation 设置为 OFF 时，状态立即收敛但资源回收规则不变。
- GlowESP 只对经过 frustum/distance/目标筛选后的实体提交 mask；按近中远 LOD 降低 blur 半径、outline thickness 和采样次数，Glow 关闭或 mode 切换时平滑淡出当前 visibility 后释放 target。

### StorageESP

当前 Chest/EnderChest 共用颜色的做法应优先修正：

- Chest：storageChest / warning amber。
- EnderChest：storageEnderChest / accentAlt cyan-violet。
- 新增可选 `StorageStyle {LEGACY, OUTLINE, FILLED, BOTH, GLOW}`，只追加 enum；`GLOW` 是 StorageESP 的 GlowESP 路径，复用同一个 world mask/outline/bloom renderer 和 LOD budget，不复制一套 FBO。
- through-wall pass 使用较低 alpha，正常可见 pass 使用清晰 outline。
- 复用独立 `WorldBoxRenderer`，不要继续让 `ESP` 通过混杂的 `StorageESP.ee()` 绘制实体。
- 以 `dimension + BlockPos` 为稳定 key 保留 `StorageVisualMotion`：首次发现 160ms alpha/outline reveal，移除或区块卸载 120ms fade；箱子类别/调色板切换 120ms color crossfade，不能硬跳色。
- 新发现方块可有一次 180ms 的低强度 accent pulse；静止存储箱不做持续呼吸动画，以免世界画面噪声和 overdraw 上升。
- Storage GlowESP 在方块集合变化时按 mask batch 一次处理；不为每个箱子创建 FBO、shader 或单独 full-screen pass。

### Chams

- 与 ESP 共享 `EntitySemanticColorResolver`。
- through-wall pass 降 alpha，visible pass 保留材质层次。
- hurt 使用 danger，不能和 TargetESP 各自硬编码另一套红色。

## 14. 从 `D:\Nymphilila\main\java` 吸收的内容

可融入：

- Overlay 的 `STATIC/PULSE/SWITCH/RAINBOW` color mode 和第二色条件显示。
- ESP 的 Elements 组合、AABB 到 2D 投影和 health/name/armor 信息布局。
- GlowESP 的“收集目标 -> mask/outline -> blur/composite”分阶段思路。
- 旧 TargetHUD 的目标 snapshot、opacity + scale 和聊天界面拖拽。
- ColorPicker 拖动实时预览。

不能照搬：

- 把 HUD/TargetHUD 逻辑塞进 Overlay/KillAura。
- dummy rect 触发 bloom 或正常/bloom 重复跑整套布局。
- 每帧新建全分辨率 FBO/FloatBuffer。
- 500ms 的迟钝 UI 动画和固定每帧插值。
- 大量 Immediate Mode 逐实体绘制且无 LOD/cull。
- 硬编码红绿阈值、品牌资源、字体和未经确认许可的 shader。
- ColorPicker 每像素绘制 hue、无 alpha/hex/preset/history。

参考目录中的通用 Gaussian/FBO 思路可以独立重写；不要复制未知许可证的源码、shader、字体或图片资产。

## 15. 性能预算

- 2D glow：每个 radius/profile 每帧一次 mask + H + V + composite；默认最多 2 个 profile batch。
- 默认 downscale 0.5；1080p 不创建超过 3 张全分辨率 RGBA glow target。
- 无 glow command 时不清空/模糊 FBO。
- kernel、uniform location、FBO 和 command list 复用。
- ArrayList 文本测量与排序沿用缓存，动画对象不每帧分配。
- 3D 特效按距离 LOD、视锥裁剪和最大实体/粒子预算。
- 不使用 `GL_MIRRORED_REPEAT` 处理屏幕空间 glow。

## 16. 测试策略

无需启动 Minecraft 的自动测试：

1. `GaussianKernelTest`
   - 正规化、非负、单调、radius=0、非法输入。
2. `GlowProfileTest`
   - radius/intensity clamp 或拒绝规则、GUI scale/quality 下物理半径。
3. `GlowShaderResourceContractTest`
   - shader 资源存在。
   - blur 处理 alpha/RGBA，不能写死 alpha=1。
   - composite 同时声明 mask/blur sampler 和 core suppression。
4. Build gate
   - `gradlew.bat test`
   - `gradlew.bat build`

经用户许可后的手测：

- GUI scale 1/2/3/4 的 roundrect 边缘粗细。
- 60/144/240 FPS 动画时长和中断反向。
- 左上 watermark、右上 ArrayList、TargetHUD 的 glow 连续性。
- 深色/浅色/樱花 palette 的对比度。
- 窗口 resize、全屏切换、Alt-Tab 后 FBO 重建和状态恢复。
- 屏幕四边 HUD 是否出现 wrap/mirror ghost。
- glow disabled 时是否完全不提交 pass。

## 17. 实施顺序（Terra）

### Phase 1：基础

- [ ] 建立 `VisualPalette/VisualTokens/VisualThemeResolver/PaletteBinding`。
- [ ] 建立 `UiClock/MotionValue/MotionRegistry`。
- [ ] 将 roundrect AA 改为 scale-aware centered coverage。
- [ ] 使用本轮新 glow API，删除各处 offset text glow。

检查点：focused tests 和 build 通过；现有配置仍能加载。

### Phase 2：HUD

- [ ] 追加 `HudStyle.NIGHT_BLOOM`。
- [ ] 实现 watermark chips。
- [ ] 实现右对齐胶囊 ArrayList 与完整退出/reorder 动画。
- [ ] 迁移 Potion/Inventory/Notification/KeyboardDisplay/Health 到 palette + motion。

检查点：60/144/240 FPS 时间一致；HudDrag/scale/config key 不变。

### Phase 3：ClickGUI 精修

- [ ] Yozakura token/圆角/hover/select/scroll/expand 统一。
- [ ] Material 去固定紫色并接全局 palette。
- [ ] Sakura 接新 glow 和 Reduced Motion。
- [ ] Palette 编辑器与 preview/commit/cancel。
- [ ] 如实现大改，只追加 `GuiStyle.NIGHT_BLOOM`。

检查点：导航、搜索、拖拽、滚动、绑定、颜色选择和所有 value 类型可用。

### Phase 4：TargetHUD 与世界视觉

- [ ] TargetHUD snapshot + crossfade + damage trail。
- [ ] TargetESP palette/LOD/visibility motion。
- [ ] ESP/StorageESP/Chams 共享 semantic color 和 WorldBoxRenderer。
- [ ] ESP / StorageESP 追加 GlowESP mode，并验证 mode 切换、LOD、world unload 与目标数量上限。
- [ ] TargetHUD / HUD widget 拖拽状态、snap preview、commit/cancel 和 GUI-scale 无漂移验证。
- [ ] TargetESP、ESP、StorageESP 的 visibility / color / health motion 与清理策略。

检查点：不改变目标选择/战斗逻辑；世界切换清理所有 motion/effect state。

### Phase 5：验证与调参

- [ ] 自动测试与 build。
- [ ] 用户许可后游戏内截图/录像对比。
- [ ] 1080p/1440p、GUI scale 1-4 和常见 FPS 档位。
- [ ] 记录最终 palette、motion、glow 参数，不把调参散落回模块类。

## 18. 边界

Always：

- 保留现有 module/value/config key。
- 新 enum 值只追加。
- 动画基于真实时间并支持从当前状态反向。
- glow、shadow、outline 使用不同 API。
- 视觉模块遵守 palette 和性能预算。

Ask first：

- 删除旧 ClickGUI mode。
- 改变默认 GUI/HUD style。
- 启动 Minecraft 做手测。
- 引入新字体、图片或第三方 shader 资源。

Never：

- 静默关闭 shader 或换回 offset glow。
- 修改 `third_party/imgui`。
- 覆盖用户当前未提交改动。
- 把 TargetHUD 重新耦合进 KillAura。
- 直接复制 `D:\Nymphilila` 的品牌资源或未知许可实现。

## 19. 成功标准

- 字体 glow 为连续 Gaussian halo，无 4/8 方向副本和淡出闪亮。
- glow strength 从 0 到 1 连续，0 时无 pass。
- roundrect 在 GUI scale 1-4 保持约 1 physical px 的稳定软边和 border。
- ClickGUI 主体布局与交互不被大幅改变。
- HUD、TargetHUD、TargetESP、ESP、StorageESP 均提供 Night Bloom 样式，并使用统一 palette 语义；旧样式和既有配置保持可选、可迁移。
- HUD / TargetHUD 拖拽在缩放、吸附、取消、松手保存和 Reduced Motion 下均保持连续且无 GUI-scale 坐标漂移。
- 所有 UI 动画 FPS-independent、可中断、可反向。
- focused tests 与 `gradlew.bat build` 通过。
- 未经许可不运行 Minecraft；无法自动证明的视觉项在交付中明确列出。

## 20. 参考依据

- Khronos GLSL 1.20 specification: https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.1.20.pdf
- Khronos EXT framebuffer object specification: https://registry.khronos.org/OpenGL/extensions/EXT/EXT_framebuffer_object.txt
- OpenGL 2.0 specification: https://registry.khronos.org/OpenGL/specs/gl/glspec20.pdf
- LWJGL 2 GL20 API: https://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/GL20.html
