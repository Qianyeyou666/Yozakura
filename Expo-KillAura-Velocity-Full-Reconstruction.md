# Expo KillAura / AutoBlock / Velocity 逻辑还原报告

> 更正（2026-07-13）：`Expo.e8` 的已确认角色是攻击后 sprint/slowdown 状态机，而非 S12 入站 Velocity 处理器。它不主动发送攻击包，也不监听 `dW`/`dV`/`S12PacketEntityVelocity`；`gG.D(Entity)` 的真实顺序是 `AttackEvent -> C02 ATTACK -> 本地 gG.j`，而 `gG.j` 只在攻击击退分支调用 `e8.F()`。以下将 `e8` 视作 AntiKB 的旧推断不应继续用于实现。

分析目标：`D:\crack\Expo-ForgeMod (1)`

分析方式：静态字节码分析，主要依据 `javap -c -p`。未运行客户端、未加载 JNIC/native 保护代码。

## 结论摘要

KillAura 主模块定位为：

- 文件：`D:\crack\Expo-ForgeMod (1)\Expo\eU.class`
- 类名：`Expo.eU extends Expo.es`

Velocity 主模块定位为：

- 文件：`D:\crack\Expo-ForgeMod (1)\Expo\e8.class`
- 类名：`Expo.e8 extends Expo.es`

两者有关联：KillAura 的攻击辅助 `Expo.gG.D(Entity)` 会发送 `C02PacketUseEntity ATTACK`，随后执行本地攻击副作用 `Expo.gG.j(Entity)`；在本地 sprint/knockback 副作用里，如果 Velocity 模块 `Expo.e8` 启用，会调用 `Expo.e8.F()` 处理本地横向 motion，而不是走普通原版减速逻辑。

AutoBlock 的核心状态机在 `Expo.eU.R(Expo.dO)`，不是 post packet 里临时补包。按行为看它属于 pre/update/motion tick 侧的主循环：每 tick 先算目标、计时、AB 状态，再决定 release、attack、reblock、slot spoof。相关 packet event 只是辅助拦截/取消/同步。

## 保护与字符串限制

这个客户端大量字符串和 invokedynamic 目标受保护：

- `b(int, int)` 字符串解密依赖混淆数组和常量。
- 部分 `$ConstantPool.decrypt()` 由 native/JNIC 保护。
- 反射字段/方法由 invokedynamic 延迟解析。

所以可以还原控制流、包序、状态机、mode 数量/比较点；但不能在纯静态前提下保证还原所有 UI mode 名称。下面用 `KA-M0`、`KA-M1` 这类行为编号代替未解密 mode 名。

## 类与事件映射

### KillAura：`Expo.eU`

关键字段：

```java
private EntityLivingBase p; // 当前目标
private boolean C;          // 内部 blocking/spoof-block 状态
private int Y;              // delay/timer counter
private long a;             // attack timestamp
private long j;             // autoblock timestamp
private int e;              // spoof slot
private boolean P;
private boolean d;

public static iT k;         // mode setting
public static iP v;         // attack timing/rate setting
public static iP F;         // autoblock timing/rate setting
public static iP n;         // hurt/status delay related
public static iP N, h;      // numeric settings
public static iw I;         // integer chance/percent setting

private static boolean q;   // AB/KA active gate
private static boolean V;   // attack key/manual hit flag
public static boolean U;    // slot spoof active flag
public static int Q;        // internal mode/state
```

关键方法：

- `R(Expo.dO)`：KillAura + AutoBlock 主 update/pre-motion 逻辑。
- `O(boolean allowUi, boolean interactBeforeBlock, EntityLivingBase target)`：reblock/start block。
- `z(boolean allowUi)`：release block。
- `j()`：判断当前是否 blocking，支持内部 spoof 状态。
- `U(boolean allowUi)`：发送 `C09PacketHeldItemChange(spoofSlot)`。
- private `O()`：恢复真实 hotbar slot。
- `w(double range, boolean flag)`：选目标。
- `n(EntityLivingBase target, boolean strict)`：目标合法性。
- `d()` / `g()`：AutoBlock 决策 helper，返回 `eZ<Boolean, Boolean>`。
- `C(Expo.ds)`、`Y(Expo.dk)`、`c(Expo.dL)`、`H(Expo.dI)`：辅助事件。

### Velocity：`Expo.e8`

关键字段：

```java
public static iT k;  // mode
public static iw s;  // percent/int setting
public static int N; // state: 0/1/2
public static int A; // tick counter
private boolean w;   // attack guard
```

关键方法：

- `F()`：本地 motionX/motionZ 削减。
- `I(Expo.dr)`：攻击事件联动，推进 Velocity 状态机，可取消攻击。
- `U(Expo.d3)`：update/tick 状态修正。
- `K(Expo.dM)`：tick 状态机收尾。
- `Q()`：显示文本。

### Velocity 底层 hook

类：`Expo.ASM.Hooks.m`

入口：

- `onHandleEntityVelocity(NetHandlerPlayClient, S12PacketEntityVelocity, Hook)`
- `onProcessEntityVelocity(INetHandlerPlayClient, S12PacketEntityVelocity, Hook)` 转调上面的方法。

事件：

- `Expo.dW`：S12 velocity event，可取消、可修改 X/Y/Z。
- `Expo.dV`：速度写入后的空事件。

`dW` 字段和访问器：

```java
private double H; // X
private double G; // Y
private double x; // Z

double O();       // X
double h();       // Y
double P();       // Z
void a(double);   // set X
void i(double);   // set Y
void B(double);   // set Z
```

## Velocity 完整逻辑

### 1. S12 速度包 hook

还原伪代码：

```java
void onHandleEntityVelocity(NetHandlerPlayClient handler, S12PacketEntityVelocity packet, Hook hook) {
    PacketThreadUtil.checkThreadAndEnqueue(packet, handler, mc);

    Entity entity = mc.theWorld.getEntityByID(packet.getEntityID());
    if (entity == null) {
        hook.cancel();
        return;
    }

    if (entity == mc.thePlayer) {
        dW event = new dW(packet.getMotionX(), packet.getMotionY(), packet.getMotionZ());
        eventBus.post(event);

        if (event.isCancelled()) {
            hook.cancel();
            return;
        }

        entity.setVelocity(event.getX() / 8000.0, event.getY() / 8000.0, event.getZ() / 8000.0);
        eventBus.post(new dV());
    } else {
        entity.setVelocity(packet.getMotionX() / 8000.0, packet.getMotionY() / 8000.0, packet.getMotionZ() / 8000.0);
    }

    hook.cancel();
}
```

重点：

- 对本地玩家会派发 `dW`，模块可 cancel 或改值。
- 对非本地实体不派发事件，直接原版比例写速度。
- 最后始终 `hook.cancel()`，说明 hook 接管原版 handler，防止重复处理。

### 2. `e8.F()`：本地横向击退削减

这个方法直接写：

- `EntityPlayerSP.field_70159_w`：motionX
- `EntityPlayerSP.field_70179_y`：motionZ

主要逻辑有两种。

百分比模式：

```java
player.motionX *= 1.0 - s.b() / 100.0;
player.motionZ *= 1.0 - s.b() / 100.0;

if (s.b() == 100) {
    setPlayerBooleanState(false);
}
```

固定倍率分支：

```java
player.motionX *= CONST_X;
player.motionZ *= CONST_Z;
setPlayerBooleanState(false);
```

`s` 是 `iw` 整数设置，按 `s.b() / 100.0` 使用。若 `s=100`，横向 motion 理论上归零；若 `s=0`，保留原速度。

### 3. `e8.I(Expo.dr)`：攻击事件

`Expo.dr` 是攻击事件，`Expo.gG.D(Entity)` 会创建并派发它。

还原伪代码：

```java
void onAttack(dr event) {
    if (!modeEquals(VELOCITY_ATTACK_STATE_MODE) || !moduleEnabled()) {
        reset();
        return;
    }

    if (w) {
        return;
    }

    if (!(event.getTarget() instanceof EntityPlayer)) {
        w = true;
        return;
    }

    switch (N) {
        case 0:
            if (playerBooleanState()) {
                event.cancel();
                N = 1;
                A = 0;
            } else {
                N = 2;
                A = 0;
            }
            break;

        case 1:
            setPlayerBooleanState(false);
            A = 0;
            N = 2;
            break;
    }

    w = true;
}
```

含义：

- Velocity 会监听 attack，并且只对 `EntityPlayer` 目标走状态机。
- `N == 0` 的某个分支会取消本次攻击事件。
- `w` 是本轮 guard，避免一次攻击流程重复推进状态。

### 4. `e8.U(Expo.d3)` 与 `e8.K(Expo.dM)`

`U(d3)` 是 update/tick 修正：

```java
if (!modeEquals(VELOCITY_UPDATE_MODE) || !moduleEnabled()) {
    reset();
    return;
}

switch (N) {
    case 1:
        setPlayerBooleanState(false);
        break;
    case 2:
        if (playerBooleanState() && randomOrCondition()) {
            setPlayerBooleanState(false);
        }
        break;
}
```

`K(dM)` 是另一个 tick/state 收尾：

```java
if (!modeEquals(VELOCITY_ATTACK_STATE_MODE) || !moduleEnabled()) {
    reset();
    return;
}

if (A > threshold) {
    reset();
}

switch (N) {
    case 1:
        setPlayerBooleanState(false);
        A++;
        break;

    case 2:
        if (playerBooleanState()) {
            if (randomOrCondition()) {
                setPlayerBooleanState(false);
            }
        } else {
            setPlayerBooleanState(false);
        }
        A = 0;
        N = 0;
        break;
}
```

`N` 不是 mode，而是内部阶段：

- `0`：待触发。
- `1`：第一阶段处理，可持续 tick。
- `2`：第二阶段/收尾。

### 5. Velocity mode 数量

`Expo.e8.k` 至少有 3 个字符串比较点：

- `b(-30570, -16108)`：攻击联动状态机模式，出现在 `F()`、`I(dr)`、`K(dM)`。
- `b(-30569, -24272)`：update 修正模式，出现在 `U(d3)`。
- `b(-30572, 15871)`：显示百分比/数值模式，出现在 `Q()`。

准确 UI 名称需要运行 native 解密或在运行时 dump `iT` 的字符串数组。

## KillAura / AutoBlock 完整逻辑

### 1. 主事件是 `R(Expo.dO)`

`Expo.eU.R(Expo.dO)` 是核心。它做的不是单一 attack，而是完整 tick 管线：

```java
void onUpdate(dO event) {
    if (Y > 0) {
        Y--;
    }

    if (!canRunModule()) {
        Pair<Boolean, Boolean> ab = gOrD();
        if (!ab.left || !ab.right) {
            event.cancelOrMark();
        }
        return;
    }

    if (!someSettingEnabled()) {
        Q = condition ? STATE_A : STATE_B;
        C = shouldKeepBlocking();
        resetOrSyncUseItem();
        syncHeldItemUseState();
    }

    double range = useExtendedRangeSetting ? otherModuleRange : constantRange;
    boolean strict = someSettingsGate;
    p = getAttackTarget(range, strict);
    q = false;

    int behavior = decodeMode(k.get());
    switch (behavior) {
        case KA_M0:
        case KA_M1:
        case KA_M2:
        case KA_M3:
            resetBlockOrState();
            break;

        case KA_M4:
            if (someCondition()) {
                U(false);          // slot spoof
                Q = SOME_STATE;
            }
            break;

        case KA_M5:
            if (V && U(false)) {
                player.swingOrResetUse();
                Q = SOME_STATE;
            }
            break;
    }

    boolean attackReady = (now() - a) >= v.get() * multiplier || V;
    boolean blockReady = (now() - j) > (1000 / F.get());

    if ((attackReady || V) && Q == READY_STATE && blockReady) {
        boolean shouldAttack = false;
        boolean interactBeforeBlock = false;

        if (V) {
            if (p != null && isValid(p, true)) {
                j = now();
                V = false;
                shouldAttack = true;
                interactBeforeBlock = true;
            } else if (fallbackCondition()) {
                j = now();
                V = false;
                shouldAttack = true;
            }
        }

        if ((shouldAttack || attackReady) && O(true, interactBeforeBlock, p)) {
            switch (decodeMode(k.get())) {
                case KA_M0:
                case KA_M1:
                    Q = POST_ATTACK_STATE;
                    break;

                case KA_M2:
                    releaseOrResetBlock();
                    setSomeGlobalFlag(false);
                    P = false;
                    Q = READY_STATE;
                    break;

                case KA_M3:
                    setSomeGlobalFlag(false);
                    P = false;
                    Q = READY_STATE;
                    break;

                default:
                    Q = READY_STATE;
                    break;
            }
        }
    }

    event.markHandledOrPost();
}
```

注意上面 `decodeMode` 的 mode 名称未解密，但控制流形状确定：

- 前半段先选目标、更新 AB/slot/spoof 状态。
- 中段用 `a` 判断攻击冷却，用 `j` 和 `F` 判断 AB 节流。
- 后半段调用 `O(true, interactBeforeBlock, p)` 做 reblock/start block，并按 mode 更新 `Q/P/V`。

### 2. AutoBlock start/reblock：`O(boolean, boolean, EntityLivingBase)`

字节码能直接还原 packet 顺序。

```java
boolean startBlock(boolean allowUi, boolean interactBeforeBlock, EntityLivingBase target) {
    if (alreadyOrCannotBlock()) {
        return false;
    }

    ItemStack stack = mc.thePlayer.getHeldItem();

    if (uiOrScreenBlocked()) {
        if (!allowUi || al.G || !al.f) {
            return false;
        }
    }

    if (interactBeforeBlock && target != null) {
        MovingObjectPosition hit = rayTrace(target.getEntityBoundingBox(), 6.0);
        if (hit != null) {
            Vec3 relative = new Vec3(
                hit.hitVec.xCoord - target.posX,
                hit.hitVec.yCoord - target.posY,
                hit.hitVec.zCoord - target.posZ
            );

            send(new C02PacketUseEntity(target, relative));
            send(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
        }
    }

    send(new C08PacketPlayerBlockPlacement(stack));
    mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
    C = true;
    return true;
}
```

所以 reblock 包序是：

```text
可选 C02 UseEntity(target, hitVec)
可选 C02 UseEntity(target, INTERACT)
C08 PlayerBlockPlacement(heldStack)
本地 setItemInUse
C = true
```

### 3. AutoBlock release：`z(boolean)`

```java
boolean releaseBlock(boolean allowUi) {
    if (!canRelease()) {
        return false;
    }

    if (uiOrScreenBlocked()) {
        if (!allowUi || al.f || al.G || al.F) {
            return false;
        }
    }

    send(new C07PacketPlayerDigging(
        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
        BlockPos.ORIGIN,
        EnumFacing.DOWN
    ));

    C = false;
    return true;
}
```

release 包固定是：

```text
C07 RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN
C = false
```

### 4. Slot spoof：`U(boolean)` 与 private `O()`

`U(boolean)` 负责切到 spoof slot：

```java
boolean spoofSlot(boolean allowUi) {
    if (cannotSpoofOrUiBlocked()) {
        return false;
    }

    int current = mc.thePlayer.inventory.currentItem;
    int spoof = computeNearbyHotbarSlot(current);

    send(new C09PacketHeldItemChange(spoof));
    e = spoof;
    U = true;
    return true;
}
```

private `O()` 负责恢复真实 slot：

```java
boolean restoreSlot() {
    if (U) {
        send(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        U = false;
        resetSlotState();
        return true;
    }
    return false;
}
```

典型 slot spoof 包序：

```text
C09 HeldItemChange(spoofSlot)
... release/attack/reblock ...
C09 HeldItemChange(realCurrentSlot)
```

### 5. Blocking 判断：`j()`

行为：

```java
boolean isBlocking() {
    if (moduleSpoofBlockModeActive()) {
        return C;
    }
    return mc.thePlayer.isBlocking(); // func_71039_bw()
}
```

也就是说模块能区分：

- 客户端真实按住右键 blocking。
- 只由 AutoBlock 包和内部状态伪造的 blocking。

### 6. 目标选择：`w(double, boolean)`

还原形状：

```java
EntityLivingBase getAttackTarget(double range, boolean strict) {
    if (someExternalTargetModeEnabled) {
        EntityLivingBase t = externalTarget;
        if (!allowOutOfRange && outOfRange(t, range)) {
            return null;
        }
        return isValid(t, range, strict) ? t : null;
    }

    List<EntityLivingBase> list = collectTargets(
        playersEnabled,
        mobsEnabled,
        animalsEnabled,
        invisiblesEnabled,
        teamsOrFriendsFilters,
        deadOrArmorStandFilters,
        ...
    );

    list.removeIf(t -> distanceInvalid(t, range));
    list.removeIf(t -> !isValid(t, range, strict));
    list.sort(Comparator.comparingDouble(t -> priorityScore(t, range, strict)));

    return list.isEmpty() ? null : list.get(0);
}
```

`n(EntityLivingBase, boolean)` 是合法性检查的一部分：

```java
boolean isValid(EntityLivingBase target, boolean strict) {
    if (al.F) return false;

    if (strict && (!someSetting || al.f || al.G)) {
        return false;
    }

    updateOrCheckSomeFlag(false);
    return target passes invokedynamic validity check;
}
```

### 7. hurt/status 延迟：`c(Expo.dL)`

`c(dL)` 监听服务端包：

```java
void onPacket(dL event) {
    if (!wSetting.enabled()) return;

    if (event.packet instanceof S19PacketEntityStatus) {
        S19PacketEntityStatus p = (S19PacketEntityStatus) event.packet;
        if (p.getEntity(mc.theWorld) instanceof EntityPlayerSP
            && p.getOpCode() == HURT_STATUS
            && randomChance(I.b())) {
            Y = (int)(n.get() * CONST);
        }
    }
}
```

用途：本地玩家收到 hurt/status 后，设置 `Y` delay counter。主循环开头会递减 `Y`，`d()` helper 里若 `Y > 0` 会阻止某些 attack/AB 行为。

## 攻击逻辑与包序

### 1. `Expo.gG.D(Entity)`：攻击入口

还原伪代码：

```java
boolean attack(Entity target) {
    dr event = new dr(target);
    eventBus.post(event);

    if (event.isCancelled()) {
        return false;
    }

    Protected.vp.O(mc.playerController);
    send(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

    if (!mc.playerController.isSpectator()) {
        doLocalAttackSideEffects(target);
    }

    return true;
}
```

### 2. `Expo.gG.j(Entity)`：本地攻击副作用

这个方法类似原版 `PlayerControllerMP.attackEntity` 后续副作用，包含：

- 伤害计算。
- enchant/critical/sprint knockback 计算。
- 火焰附加。
- 本地命中反馈。
- 统计和 exhaustion。

关键联动在 sprint/knockback 分支：

```java
if (velocityModuleEnabled()) {
    Expo.e8.F();
} else {
    player.motionX *= vanillaOrConst;
    player.motionZ *= vanillaOrConst;
    player.setSprinting(false);
}
```

因此 KillAura 攻击和 Velocity 的关系不是只靠 S12。攻击本地副作用里也会让 Velocity 接管横向减速。

### 3. AutoBlock + Attack 常见包序

如果当前已 blocking，需要先 release：

```text
C07 RELEASE_USE_ITEM
```

攻击：

```text
C02 UseEntity(target, ATTACK)
```

attack 后 reblock：

```text
可选 C02 UseEntity(target, hitVec)
可选 C02 UseEntity(target, INTERACT)
C08 PlayerBlockPlacement(held sword stack)
```

带 slot spoof 的模式可能包序为：

```text
C09 HeldItemChange(spoofSlot)
C07 RELEASE_USE_ITEM
C02 UseEntity(target, ATTACK)
可选 C02 UseEntity(target, hitVec)
可选 C02 UseEntity(target, INTERACT)
C08 PlayerBlockPlacement(held sword stack)
C09 HeldItemChange(realCurrentSlot)
```

实际是否有 `C07`、是否有 `C02 INTERACT`、是否 slot spoof，由 `R(dO)` 中 mode、`V`、`Q`、`P`、`j/a` timer 和目标合法性共同决定。

## AutoBlock 在哪个事件，pre 还是 post

核心 AB 在：

```java
Expo.eU.R(Expo.dO)
```

从行为判断它是 pre/update/motion tick 侧，不是 post：

- 它在方法开头处理 `Y` delay、模块可运行性和当前 blocking 状态。
- 中间选择目标 `p = w(range, strict)`。
- 然后根据 mode 和 timer 决定 release、slot spoof、reblock。
- 方法末尾对 `dO` event 调用标记/取消方法。

也就是说 AB 决策发生在本 tick 的攻击/发包流程前后安排阶段，属于主 update/pre-motion 控制面；post 侧最多是辅助同步/拦截，不是核心状态机。

辅助事件：

- `C(Expo.ds)`：如果 `q` active 且 mode 属于 4 个 AB mode，并且 `!j()`，则 cancel/gate 该事件。
- `Y(Expo.dk)`：某些 AB setting 开启且 AB state active 时 cancel。
- `c(Expo.dL)`：服务端 `S19PacketEntityStatus` hurt/status 后设置 `Y` 延迟。
- `H(Expo.dI)`：攻击键事件，匹配 `keyBindAttack` 后调用 `Expo.eU.a()`，设置静态 `V`，用于手动/按键触发路径。

## KillAura mode 数量

`Expo.eU.k` 的主 switch 有 6 个比较点：

- `b(-8304, -31009)`
- `b(-8301, 27244)`
- `b(-8293, -3752)`
- `b(-8300, -24428)`
- `b(-8296, -26429)`
- `b(-8299, -1298)`

所以 KillAura/主行为 mode 至少有 6 个。

AB 相关 switch 有 4 个比较点：

- `b(-8297, -295)`
- `b(-8294, 21632)`
- `b(-8303, -17321)`
- `b(-8302, -15019)`

静态字段 `g:[String]` 长度为 11，说明整个 `eU` 的受保护字符串池里至少有 11 个相关字符串槽位；但这不等同于 11 个主 mode，因为包含显示名、mode 名、suffix 或其他配置字符串。

## 可还原与不可还原部分

可以确定：

- Velocity S12 hook 流程。
- `dW` 可取消/可改 X/Y/Z。
- Velocity 有攻击事件联动。
- KillAura 主类、目标字段、主 update 事件。
- AB release/reblock/slot spoof 包序。
- Attack 发 `C02 ATTACK` 前会派发 `dr`，可被 Velocity 或其他模块 cancel。
- KillAura 本地攻击副作用里会调用 Velocity `e8.F()`。

当前不能纯静态保证：

- 每个 UI mode 的明文名称。
- invokedynamic 写入的所有 `EntityPlayerSP` boolean 字段精确字段名。
- `dO/d3/dM/ds/dk` 在事件系统中的官方名字，只能按行为判断 pre/update/tick/packet gate。
- 数字常量的全部明文值，因为部分 int/float/double 经 `b(int,long)` 或 native 常量保护。

若要继续“完全到源码级”，需要运行时 dump：

1. `Expo.iT` mode 字符串数组。
2. `$ConstantPool.decrypt()` 输出。
3. invokedynamic bootstrap 解析后的 MethodHandle 目标。
4. 事件总线中 `dO/d3/dM/ds/dk/dr/dL` 的派发位置。
