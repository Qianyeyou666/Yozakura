# AimAssist / Aimbot 完整重写方案

这份文档是交给 Terra 模型的实现规格。目标是完整重写 Forge 1.8.9 的
`src/main/java/gq/yozakura/module/combat/Aimbot.java`，做出接近 Vape、Drip Lite、Slinky
这类客户端的手感，但不尝试复制任何闭源实现。

## 0. 当前交接状态（2026-07-13）

已完成并保留在工作区：

- `AimAssistController`：纯 Java 的时间制速度/加速度、reaction delay、最短 yaw、
  sensitivity quantum residual、外部鼠标优先、vertical toggle 和 reset 状态。
- `AimAssistTargetSelector`：玩家/动物/Mob 三类候选过滤、可见点、一次候选对象缓存、150ms
  sticky lock、sort-specific hysteresis，以及 blocker AABB 快照。
- `AimAssistTargetLock`：无 Minecraft 依赖的纯切换判定。
- `AimAssistAimPoint`：hitbox anchor 与 partial-tick 位置插值。
- 已完整接线 `Aimbot`：Tick 选目标、RenderTick Normal `setAngles(...)`、独立 Silent
  controller、非零 move-fix priority 和 Silent return。
- JUnit 4.13.2 测试依赖及 18 个 focused tests。

已验证：

```text
gradlew.bat test --tests 'gq.yozakura.module.combat.aim.*'
BUILD SUCCESSFUL（18 tests）
```

仍需人工 Minecraft 手测与后续调参：

- 60/144/240 FPS 下的实际手感和 Speed 映射常数。
- Forge/standalone 两套 RenderTick 派发的真实运行时确认。
- Silent return 在具体服务器 packet interceptor 下的朝向回正。

## 1. 范围

只改 1.8.9 `Aimbot` 模块及其专用辅助类。

必须保留：

- Java 类型名 `Aimbot`。
- 对外模块名 `AimAssist`。
- `ModuleManager` 中的旧工厂键 `Aimbot`。
- `FileManager` 对旧配置模块名 `Aimbot` 的兼容读取。
- 所有仍在使用的 Value ID：`Mode`、`Speed`、`VerticalSpeed`、
  `ReactionDelay`、`UpdateRate`、`MultipointHorizontal`、`MultipointVertical`、
  `Randomization`、`FOV`、`Range`、`Sort`、`IgnoreBehindWalls`、
  `IgnoreBehindEntities`、`AimInvis`、`RequireMouse`、`RequireTarget`、
  `VerticalAim`、`IgnoreTeammates`、`BotCheck`、`StopWhenBreaking`、
  `KeepMoveDirection`、`HoverDelay`、`WeaponOnly`；新增 `Players`、`Animals`、`Mobs`。
- 公共兼容方法 `getEntityList()`、`assistFaceEntity(...)`、`updateRotation(...)`；
  `BowAimBot` 仍在调用 `getEntityList()`。

不要顺带修改：

- `ModernCombatBridge` 及 modern ClickGUI 的独立 AimAssist。
- `MovementInputBridge`、`RotationState`、`RotationManager`、全局发包桥。
- KillAura、Scaffold、Clutch、BridgeAssist 或其他模块。
- vendored/native 代码。

## 2. 当前实现的确定性问题

### 2.1 Normal 镜头被服务器旋转污染

当前 `Aimbot.onUpdate` 使用 `event.getNewYaw()/getNewPitch()` 作为第一人称起点。
但 UpdateEvent 按 HIGHEST 到 LOWEST 调度，Scaffold、Clutch、BridgeAssist 等会先把
silent/server yaw 写进 `newYaw`。因此 AimAssist Normal 实际在追随服务器模拟角度，
而不是玩家真实相机角度。

当前 Normal 随后还调用 `event.setRotation(...)`，这会激活 `RotationState`、C03 重写、
move-fix 和若干读取 `RotationState.getSmoothedYaw()` 的移动逻辑。纯可见 AimAssist 不应进入
这些通道。

### 2.2 第一人称只有 20 TPS 且写法不等同鼠标

当前代码只在 UpdateEvent 改绝对 `rotationYaw/rotationPitch`，没有按渲染帧更新，也没有像
vanilla `Entity.setAngles(...)` 一样同步调整 prev rotation。高帧率下会出现明显阶梯、滞后和
插值不一致。

### 2.3 旋转算法的问题

- 算法没有 delta-time，同一配置会随 FPS/TPS 表现不同。
- `ReactionDelay` 没有真正延迟首次辅助，只参与了一个基本无效的过期时间。
- `UpdateRate=20` 又附加随机延迟，20 TPS 下经常退化到约 10 Hz。
- 随机偏移每次独立跳变，不是连续噪声，接近目标时更容易抖。
- mouse counts 是浮点状态，最终没有真正按 sensitivity quantum 输出。
- 接近目标会直接 return target，产生 snap。
- 切换目标时没有清旧速度，旧目标动量会带到新目标。
- 玩家手动移动鼠标时，内部绝对角状态可能把镜头拉回旧轨迹。

### 2.4 目标系统的问题

- TickEvent 和 UpdateEvent 重复扫描、排序、可见点查询。
- 没有 sticky lock、最短锁定时间或切换 hysteresis，相邻目标会来回跳。
- 可见性检查最坏会对每名玩家的多个点反复遍历 loaded entities，热路径成本很高。
- 目标点只按 20 TPS 更新，没有利用实体 tick 插值。
- `RequireTarget` 只验证准星命中任意 LivingBase，不保证命中当前选择对象。

### 2.5 Silent 生命周期的问题

- Normal 与 Silent 共用一套旋转状态，模式切换容易继承残留速度。
- 当前 `KeepMoveDirection` 条件语义疑似反向。
- standalone bridge 会把 `setPervRotation(..., priority=0)` 的结果写回本地镜头，导致
  Silent 在 Forge 与 standalone 下表现不一致。
- Silent 退出时没有可靠地把服务器角度归还给相机角度；如果 vanilla 后续发送无 look 的包，
  服务端可能停留在最后一个 silent yaw。

## 3. 重写后的文件职责

建议结构：

```text
src/main/java/gq/yozakura/module/combat/Aimbot.java
src/main/java/gq/yozakura/module/combat/aim/AimAssistController.java
src/main/java/gq/yozakura/module/combat/aim/AimAssistTargetSelector.java
src/main/java/gq/yozakura/module/combat/aim/AimAssistAimPoint.java
src/test/java/gq/yozakura/module/combat/aim/AimAssistControllerTest.java
src/test/java/gq/yozakura/module/combat/aim/AimAssistTargetSelectorTest.java
```

职责必须清晰：

- `Aimbot`：设置、事件订阅、启停条件、Normal/Silent 通道编排。
- `AimAssistController`：纯 Java、可测试的时间制速度/加速度/残差量化状态机。
- `AimAssistTargetSelector`：候选过滤、排序、sticky lock、切换 hysteresis、一次扫描缓存。
- `AimAssistAimPoint`：保存目标 hitbox 内的归一化 anchor，并根据实体插值位置还原目标点。

不要重新做一个全局 RotationManager，也不要让专用类依赖 UI 或配置文件。

## 4. 总体状态机

```text
DISABLED
   -> IDLE
   -> ACQUIRED_WAIT   新目标，等待 ReactionDelay
   -> TRACKING        正常输出辅助
   -> SILENT_RETURN   仅 Silent：服务器角度平滑/强制归还相机
   -> IDLE
```

必须在以下情况完整清理目标、速度、残差、noise、计时器和 anchor：

- 模块关闭。
- 世界或玩家为空。
- Normal/Silent 模式切换。
- 目标失效或超出范围/FOV。
- GUI 打开、按键条件失效、开始受设置限制的挖掘。

目标切换不能沿用旧目标的 yaw/pitch velocity。

## 5. 事件流设计

### 5.1 TickEvent PRE：唯一目标扫描入口

每个逻辑 tick 只做一次：

1. 检查运行条件。
2. 用真实本地相机 `mc.thePlayer.rotationYaw/rotationPitch` 做 FOV 与目标选择基准。
3. 构建候选并做一次可见点计算。
4. 更新 sticky target。
5. 新目标进入 `ACQUIRED_WAIT`，设置 `engageAt = now + ReactionDelay`。
6. 到达 `UpdateRate` 周期时才更新 anchor、目标旋转和下一段 coherent noise。

禁止在 UpdateEvent 内再次遍历整个世界。

### 5.2 RenderTick START：Normal 的唯一输出入口

Normal 模式逐渲染帧执行：

1. 当前角必须读取 `mc.thePlayer.rotationYaw/rotationPitch`。
2. 根据 `renderTickTime` 或实体 `lastTickPos -> pos` 重建插值后的目标点。
3. 用 `System.nanoTime()` 计算 frame delta，并限制在合理范围，例如
   `0.0001s <= dt <= 0.05s`。
4. Controller 返回相对角增量 `yawDelta/pitchDelta`。
5. 用 vanilla 相对鼠标入口输出：

```java
mc.thePlayer.setAngles(yawDelta / 0.15F, -pitchDelta / 0.15F);
```

Normal 模式明确禁止：

```java
event.setRotation(...);
event.setPervRotation(...);
mc.thePlayer.rotationYaw = ...;
mc.thePlayer.rotationPitch = ...;
RotationUtil.syncHead(...);
```

这样 Normal 不会创建 server rotation intent，不会激活 `RotationState`，也不会触发
movement simulation。vanilla 自己会把最终相机角自然发送给服务端。

Forge 与 standalone 都已有 RenderTick 双路径；参考 `AutoClicker.onRenderTick` 和
`StandaloneGuiIngame`。不要再增加一个静默 fallback 事件，若 RenderTick 未派发应明确报错并修桥。

### 5.3 UpdateEvent PRE：Silent 的唯一输出入口

Silent 模式才允许进入 UpdateEvent：

1. 目标选择仍以本地相机为基准，不以 `event.getNewYaw()` 为基准。
2. Aimbot 运行优先级低于旋转型移动模块；若本事件已经 `event.isRotated()`，本 tick 让步，
   不推进 Silent controller。
3. Silent controller 使用自己的 last server yaw/pitch，不读取受其他模块污染的 newYaw 作为
   持久状态。
4. 输出 `event.setRotation(serverYaw, serverPitch, AIM_PRIORITY)`。
5. `KeepMoveDirection=true` 才请求 movement fix；语义定义为“保持本地相机对应的世界移动方向”。
6. 避免使用 `setPervRotation(..., priority=0)`；standalone 有针对 0 的本地镜头写回特例。
   在确认全局优先级合同后使用非 0 的专用常量，并且先执行 `event.isRotated()` 让步检查。

Silent 绝不能写 `mc.thePlayer.rotationYaw/rotationPitch`。

### 5.4 Silent 退出

必须记录是否曾发布 Silent rotation 以及最后一次 server yaw/pitch。

推荐实现：

- 目标丢失但模块仍启用：进入 `SILENT_RETURN`，用 2 到 4 个 UpdateEvent 把服务器角度平滑
  归还到当前相机角。
- 模块关闭或世界退出：如果仍在游戏且发过 Silent rotation，调用项目现有的明确
  rotation-exit 机制，使回正仍经过正常的事件、Blink 和封包链路。
- Silent 切换到 Normal：取消专用 Silent return，避免 Normal 进入 `UpdateEvent` / `RotationState`；
  原生本地相机的下一次 look 包会自然把服务端朝向带回当前 camera yaw/pitch。
- 完成后再清 controller，不能先清状态再丢失最后 server yaw。

不要依赖“玩家之后移动鼠标自然会修正”这种隐式 fallback。

## 6. 旋转控制器算法

Controller 必须是纯 Java，不引用 Minecraft 类，Normal 和 Silent 使用两个独立实例。

### 6.1 基本算法

每轴计算：

```text
errorYaw = wrap180(targetYaw - currentYaw)
errorPitch = targetPitch - currentPitch

desiredVelocity = clamp(error * response, -maxSpeed, maxSpeed)
velocityBlend = 1 - exp(-acceleration * dt)
velocity += (desiredVelocity - velocity) * velocityBlend
rawDelta = velocity * dt
```

然后：

- yaw 始终走最短路径。
- pitch 限制到 `[-90, 90]`。
- delta 不得越过目标。
- 在 dead-zone 内让 velocity 平滑衰减，不直接 snap 到目标。

建议 profile：

| Profile | response | acceleration | 手感 |
|---|---:|---:|---|
| REGULAR | 约 8.0-9.0 | 约 9.0-11.0 | 柔和、玩家输入优先 |
| BLATANT | 约 13.0-15.0 | 约 17.0-20.0 | 更快但仍无硬 snap |

### 6.2 旧 Speed 配置的映射

现有默认 `Speed=10`、`VerticalSpeed=5`，不能直接解释成 10°/s 和 5°/s，否则过慢。
建议保留原滑条和配置值，在 controller snapshot 中映射为实际角速度：

```text
effectiveYawSpeed   = clamp(22 + Speed * 4.8, 25, 420) degrees/second
effectivePitchSpeed = clamp(14 + VerticalSpeed * 3.4, 16, 260) degrees/second
```

BLATANT 可在此基础上乘约 `1.15`，最终仍要 clamp。具体常数可以通过手测微调，但不要再让
速度隐式依赖 FPS/TPS。

### 6.3 真实 sensitivity quantum

使用 1.8.9 的角度 quantum：

```text
scaled = sensitivity * 0.6 + 0.2
quantum = scaled^3 * 8.0 * 0.15
```

必须量化“相对 delta”，而不是把绝对 yaw/pitch round 到网格：

```text
total = rawDelta + residual
counts = round(total / quantum)
quantizedDelta = counts * quantum
residual = total - quantizedDelta
```

若量化结果会越过目标，则减少 counts；目标已落入一个 quantum 的 terminal dead-zone 时停止并
清除 residual，避免在 hitbox 上来回抖动。其他 sub-quantum 累积继续保留 residual。

### 6.4 玩家鼠标优先

Controller 不保存并强行输出一个绝对“内部镜头”。每帧以实际 player camera 为权威输入。

记录上帧输出，仅用于检测外部增量：

```text
externalDelta = actualCamera - lastControllerOutput
```

当 externalDelta 超过约 `max(0.08°, 1.5 * quantum)` 时，认为玩家主动移动了鼠标：

- REGULAR 将 velocity/residual 衰减到约 20%-30%。
- BLATANT 衰减到约 40%-50%。
- 下一步仍从 actual camera 继续，不得拉回旧内部角度。

这也是 Normal 不受 server/silent 模拟回写影响的第二层保证。

## 7. Target selector 与锁定策略

候选过滤保持现有功能：

- 排除自己、死亡、0 health。
- friend、team、invisible、bot 设置。
- eye 到目标 AABB 最近点的 Range。
- 以本地相机 yaw 计算 FOV/2。
- 按 Health、Angle、Hurt Time、Distance 排序。
- 按设置处理墙体和其他实体遮挡。

注意：当前上游 `FriendManager/TargetManager` 的 friend 判定实际上可能恒 false；这是上游缺陷，
不要在本次 AimAssist 重写里伪造 friend fallback。`AntiBot.isServerBot` 也受 AntiBot 模块状态影响，
应在文档/手测结果中如实说明。

### 7.1 Sticky lock

保留当前目标，只在以下情况切换：

- 当前目标失效；或
- 已锁定至少约 150 ms，挑战者明显优于当前目标。

推荐 hysteresis：

| Sort | 挑战者至少要领先 |
|---|---:|
| ANGLE | 约 2.0°-2.5° |
| DISTANCE | 约 0.25 blocks |
| HEALTH | 约 1.0 health |
| HURT_TIME | 约 1 tick |

切换后重新执行 ReactionDelay，并清空两个轴的 velocity/residual/noise。

### 7.2 一次扫描缓存

一次扫描生成 `Candidate(target, score, distance, AimAssistAimPoint)`，排序和最终输出复用同一个
aim point。不要在选择目标、准备任务和输出旋转时重复跑 `findVisiblePoint`。

当启用 entity obstruction 时，对本次扫描缓存每个候选的可见性结果；不要在同 tick 对同一
候选重复遍历 `loadedEntityList`。

## 8. Aim point 与移动目标平滑

Aim point 不应只存世界坐标，而应存目标 AABB 内的归一化 anchor：

```text
u = (x - minX) / width
v = (y - minY) / height
w = (z - minZ) / depth
```

扫描/UpdateRate 到期时：

1. 根据 Multipoint Horizontal/Vertical 生成 primary anchor。
2. 如果启用墙体/实体遮挡，按离 primary 最近的顺序检查有限候选点。
3. 保存第一个或代价最小的合法 anchor。

每个 RenderTick：

1. 用 `lastTickPos + (pos - lastTickPos) * partialTicks` 得到目标插值位置。
2. 将 anchor 还原为本帧世界坐标。
3. 从玩家眼睛计算 target yaw/pitch。

可见性 ray trace 仍只按 UpdateRate 更新，不要每渲染帧遍历所有实体。

## 9. Randomization 的正确语义

`Randomization` 应是低幅度、连续、相关的瞄点漂移，不是每 tick 独立跳角。

建议：

```text
strength = Randomization / 100
maxYawNoise   = 0.8° * strength
maxPitchNoise = 0.45° * strength
```

- 每 140-260 ms 生成一个新的 noise target。
- noise 自身通过低通或同类 spring 平滑接近下一目标。
- 接近 hitbox 边缘时缩小 noise，不能把瞄点随机到 AABB 外。
- 目标切换时 noise 清零并重新起步。
- UpdateRate 控制目标采样；不要再额外加会让 20 Hz 退化成 10 Hz 的随机 delay。

## 10. 各设置的明确语义

- `Mode=NORMAL`：只操作本地相机；不创建 server rotation intent。
- `Mode=SILENT`：只操作 UpdateEvent/server rotation；不写本地相机。
- `Mode=BLATANT`：Normal 本地相机通道，使用更快的 Blatant controller profile。
- `Mode=SILENT_BLATANT`：Silent server rotation 通道，使用更快的 Blatant controller profile。
- 不再暴露独立的 `VapeMode` Value；四个组合模式共用同一套 controller/selector。
- `Speed/VerticalSpeed`：映射后的最大角速度。
- `ReactionDelay`：新目标获取后真正等待的毫秒数。
- `UpdateRate`：昂贵的目标/可见性/anchor 更新频率；frame smoothing 不受它限制。已锁目标的
  类型、存活、Range 与 FOV 每 tick 轻量复核，不能因低 UpdateRate 继续瞄准失效目标。
- `Multipoint*`：决定 AABB anchor 偏向中心还是最近点。
- `Randomization`：coherent anchor/angle drift。
- `FOV`：始终相对本地相机，不相对 server yaw。
- `IgnoreBehindWalls=true`：排除被方块遮挡的目标。
- `IgnoreBehindEntities=true`：排除被其他 LivingEntity 挡住的点。
- `RequireMouse=true`：仅攻击键真实按下时辅助。
- `RequireTarget=true`：建议明确为准星当前命中一个通过基础过滤的 LivingBase；如果改成必须
  命中 selected target，属于行为变化，需要在提交说明中写明。
- `StopWhenBreaking=true`：开始挖掘后允许 `HoverDelay` 毫秒 grace，之后暂停直到停止挖掘。
- `KeepMoveDirection`：仅 Silent 可见；true 表示启用 movement correction。
- `WeaponOnly`：继续复用 `CombatUtil.isHoldingWeapon()`。
- `Players`、`Animals`、`Mobs`：分别决定是否将玩家、被动生物和其他 LivingBase 候选纳入筛选，
  默认是 true、false、false。

可以用 `Value.visibleWhen(...)` 隐藏 Normal 模式下无意义的 `KeepMoveDirection`，但不得改 Value ID。

### 10.1 Mode 合并与旧配置迁移

旧版配置同时保存 `Mode`（`NORMAL` / `SILENT`）和 `VapeMode`（`REGULAR` / `BLATANT`）。读取时，
`FileManager` 仅在检测到旧 `VapeMode` 键时合并为新值：

| 旧 `Mode` | 旧 `VapeMode` | 新 `Mode` |
| --- | --- | --- |
| `NORMAL` | `REGULAR` | `NORMAL` |
| `SILENT` | `REGULAR` | `SILENT` |
| `NORMAL` | `BLATANT` | `BLATANT` |
| `SILENT` | `BLATANT` | `SILENT_BLATANT` |

新配置中的 `BLATANT` 和 `SILENT_BLATANT` 优先保留，未知旧值不做猜测。旧实现的
`KeepMoveDirection` 条件与设置名称相反，因此同一旧键存在时也反转该布尔值，以保持已有配置的实际
移动行为；新语义为 `true` 启用 movement correction。下一次保存只写新的 `Mode`，不再写 `VapeMode`。

## 11. Aimbot 编排伪代码

```java
onEnable() {
    resetAll();
    lastMode = mode.getValue();
}

onTickPre() {
    if (modeChanged()) {
        finishSilentIfNecessary();
        resetAll();
    }
    if (!conditionsMet()) {
        releaseOrReturnSilent();
        return;
    }

    Selection selection = selector.select(mc, mc.thePlayer.rotationYaw, settingsSnapshot(), now);
    if (selection == null) {
        releaseOrReturnSilent();
        return;
    }

    target = selection.target;
    if (controllerTargetChanged()) {
        acquireTargetWithReactionDelay();
    }
    if (now >= nextSampleAt) {
        updateAnchorAndNoiseTarget(selection);
        nextSampleAt = now + 1000 / UpdateRate;
    }
}

onRenderTickStart() {
    if (mode != NORMAL || !trackingAndEngaged()) return;
    float[] targetRotation = interpolatedTargetRotation();
    viewController.setTargetRotation(targetRotation[0], targetRotation[1], targetBlend);
    Rotation result = viewController.step(
        mc.thePlayer.rotationYaw,
        mc.thePlayer.rotationPitch,
        frameDelta,
        now,
        controllerSettings
    );
    mc.thePlayer.setAngles(result.yawDelta / 0.15F, -result.pitchDelta / 0.15F);
}

onUpdatePre(UpdateEvent event) {
    if (mode != SILENT) return;
    if (returningToCamera()) {
        publishSilentReturn(event);
        return;
    }
    if (!trackingAndEngaged() || event.isRotated()) return;

    packetController.setTargetRotation(sampledTargetYaw, sampledTargetPitch, targetBlend);
    Rotation result = packetController.step(lastServerYaw, lastServerPitch, 0.05F, now, settings);
    event.setRotation(result.yaw, result.pitch, AIM_PRIORITY);
    if (keepMoveDirection) {
        event.setPervRotation(result.yaw, MOVE_FIX_PRIORITY_NON_ZERO);
    }
    rememberPublishedServerRotation(result);
}
```

## 12. 测试要求

如果仓库仍无测试依赖，可在 `build.gradle` 加 JUnit 4.13.2，并按仓库规范记录依赖。

Controller 单测至少覆盖：

1. yaw 跨 `179 -> -179` 走最短方向。
2. 不超过配置角速度，且不 overshoot。
3. 60 FPS 与 144 FPS 模拟一秒后的角度接近。
4. ReactionDelay 到期前完全不输出。
5. sub-quantum residual 最终形成合法 mouse quantum step。
6. 玩家外部移动相机后，从实际相机继续，不回到旧内部角。
7. VerticalAim=false 时 pitch 不变。
8. target switch/release 清 velocity、residual 和 noise。

Selector 单测至少覆盖：

1. 最短锁定窗口内不切换。
2. 挑战者未超过 hysteresis 时不切换。
3. 当前目标失效时立即切换。
4. FOV 使用 client camera yaw，而不是 server/update yaw。

集成/代码审查必须确认：

- Normal 代码路径中不存在 `UpdateEvent.setRotation/setPervRotation`。
- Silent 代码路径中不存在本地 `rotationYaw/rotationPitch` 写入。
- 每 tick 只扫描一次候选。
- `ReactionDelay` 不是死设置。
- Silent disable/mode switch 会归还 server rotation。

验证命令：

```powershell
gradlew.bat test --tests gq.yozakura.module.combat.aim.*
gradlew.bat build
```

未经用户明确许可不要运行 `runClient`。如获准手测，至少覆盖：

- 60/144/240 FPS 的第一人称跟随。
- 同时开启 Scaffold/Clutch/BridgeAssist 时 Normal 镜头不跟 server yaw。
- 手动快速反向拉鼠标时辅助让出控制。
- 两个相近目标交叉时不抖切。
- 目标进出墙体、实体遮挡、FOV、Range。
- Normal/Silent 切换与 Silent disable 后服务端朝向恢复。
- Forge 与 standalone 两种桥路径。

## 13. 推荐实施顺序

1. 保存当前工作树差异并跑基线 build；不得覆盖已有旋转桥改动。
2. 先写 `AimAssistControllerTest` 的 RED 测试。
3. 实现纯 controller，让所有数学测试 GREEN。
4. 写 selector hysteresis 测试并实现一次扫描缓存与 anchor。
5. 重写 Aimbot 的 TickEvent 目标生命周期，先不输出旋转。
6. 接入 Normal RenderTick + `setAngles`；确认 Normal 完全绕开 UpdateEvent rotation。
7. 接入独立 Silent controller、优先级让步和 server rotation return。
8. 跑 focused tests、完整 build，再做五轴代码审查：正确性、可读性、架构、安全、性能。
9. 只有用户许可后才启动 Minecraft 做手感调参。

## 14. 交给 Terra 的执行约束

可以直接把下面这段连同本文档路径交给 Terra：

```text
请严格按 docs/aimassist-rewrite-spec.md 重写 Forge 1.8.9 的整个 Aimbot/AimAssist 模块。
只修改 Aimbot 及其专用 aim helper/test；保留所有 Value ID、旧配置兼容和公共 API。
Normal 必须逐 RenderTick 使用 player.setAngles 输出相对鼠标增量，禁止进入 UpdateEvent、
RotationState 或 move-fix。Silent 必须使用独立 controller，仅写 server rotation，并实现可靠退场。
先写失败测试，再分增量实现；不要覆盖当前工作树的其他未提交改动，不要运行 runClient。
完成后运行 focused tests 和 gradlew.bat build，并报告无法自动验证的 Minecraft 手感项。
```
