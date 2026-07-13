# Expo Velocity 模块静态分析报告

> 更正（2026-07-13）：后续直接检查 `Expo.e8` 的方法签名和调用点后确认，`e8` 不是入站 S12/AntiKB 模块。它没有 `dW`/`dV`/`S12PacketEntityVelocity` 监听方法；`gG.j(Entity)` 只会在本地攻击触发 sprint 击退副作用时调用 `e8.F()`，用来替代 vanilla 的 `motionX/Z *= 0.6` 与 sprint reset。本文下方把 `e8` 归为 Velocity、并推断其处理 `dW/dV` 的段落已过时，不能作为实现 S12 减击退的依据。保留它仅作为“真实 AttackEvent + 本地攻击副作用”时序的参考。

分析对象：`D:\crack\Expo-ForgeMod (1)`

分析方式：仅静态字节码分析，未运行客户端/保护壳。

## 结论

Velocity 主模块基本可以定位为：

- 模块类：`D:\crack\Expo-ForgeMod (1)\Expo\e8.class`
- 真实类名：`Expo.e8 extends Expo.es`
- 关键事件类：
  - `Expo.dW`：Velocity/Knockback 速度事件，可取消，可修改 X/Y/Z
  - `Expo.dV`：速度应用后的空事件
- 关键 hook：
  - `D:\crack\Expo-ForgeMod (1)\Expo\ASM\Hooks\M.class`
  - 真实类名：`Expo.ASM.Hooks.m`
  - 方法：`onHandleEntityVelocity(NetHandlerPlayClient, S12PacketEntityVelocity, Expo.ASM.Hooks.x)`

核心逻辑不是普通包事件里直接 cancel `S12`，而是 ASM hook 替换了原版 `S12PacketEntityVelocity` 处理流程：先创建 `dW` 事件给模块改/取消，然后由 hook 自己调用 `Entity#setVelocity`，最后 cancel 原始 handler，防止原版重复执行。

## 速度包处理链

入口在 `Expo.ASM.Hooks.m.onHandleEntityVelocity(...)`：

1. 取当前世界 `Minecraft.field_71441_e`。
2. 调用原版线程检查：
   - `PacketThreadUtil.func_180031_a(packet, handler, Minecraft)`
3. 用 `S12PacketEntityVelocity.func_149412_c()` 取实体 id。
4. `WorldClient.func_73045_a(id)` 找到实体。
5. 如果包目标是本地玩家：
   - 用包里的三个 int 速度创建 `new Expo.dW(x, y, z)`：
     - X：`func_149411_d()`
     - Y：`func_149410_e()`
     - Z：`func_149409_f()`
   - 通过事件总线 `Expo.e6.l.C(event)` 派发。
   - 如果 `event.H()` 为 true，调用 hook `cancel()` 并直接 return。
   - 否则把事件中的 X/Y/Z 除以 `8000.0` 后写入实体速度：
     - `entity.func_70016_h(event.O()/8000, event.h()/8000, event.P()/8000)`
   - 然后派发 `new Expo.dV()`。
6. 如果包目标不是本地玩家：
   - 不派发 `dW`，直接按原版比例写入目标实体速度。
7. 方法末尾始终 `hook.cancel()`，说明它接管了原始 handler。

所以真正的底层 Velocity 可控点是 `dW`，不是 Forge/Netty 普通包事件。

## dW / dV 事件结构

`Expo.dW extends Expo.Protected.vX`：

- 字段：
  - `H:D`：X velocity int
  - `G:D`：Y velocity int
  - `x:D`：Z velocity int
- getter：
  - `O()` 返回 X
  - `h()` 返回 Y
  - `P()` 返回 Z
- setter：
  - `a(double)` 设置 X
  - `i(double)` 设置 Y
  - `B(double)` 设置 Z
- 继承自 `vX` 的取消能力：
  - `H()` 判断是否 cancelled

`Expo.dV extends Expo.Protected.vX` 是空事件，出现在速度已经写入玩家之后。

## Velocity 模块 `Expo.e8`

`Expo.e8` 是最像 Velocity 的模块类，证据：

- 继承 `Expo.es`，符合模块基类。
- 持有模式/数值配置：
  - `public static Expo.iT k`：模式值，`Q()` 会根据模式返回显示文本。
  - `public static Expo.iw s`：整数设置，`iw.b()` 返回 int，逻辑里按百分比使用。
- 直接修改本地玩家：
  - `EntityPlayerSP.field_70159_w`：motionX
  - `EntityPlayerSP.field_70179_y`：motionZ
- 与 `dV`、`d3`、`dM`、`dr` 等事件共同组成被击退后的状态机。

注意：`javap -p` 没看到显式 `public void onVelocity(Expo.dW)`。这个客户端用了 invokedynamic/反射式事件绑定和大量字符串加密，所以 `dW` 监听点没有以普通 Java 方法名暴露。但 `e8` 的后续状态处理与 `ASM Hooks M -> dW/dV` 速度链高度吻合。

## Mode 数量

`Expo.e8` 里真正的模式值是静态字段 `k:LExpo/iT;`。从 `k` 的字符串比较点看，Velocity 至少/基本就是 3 个 mode：

1. `b(-30570, -16108)`：
   - 出现在 `F()`、`I(Expo.dr)`、`K(Expo.dM)`。
   - 这是主状态机模式，会处理 attack，并在后续 tick/event 里削 motion。
2. `b(-30569, -24272)`：
   - 出现在 `U(Expo.d3)`。
   - 主要做 tick/update 里的玩家 boolean 状态修正。
3. `b(-30572, 15871)`：
   - 出现在 `Q()`。
   - 该模式下显示文本返回 `s.b() + suffix`，也就是带百分比/数值显示的模式。

注意不要把 `N` 当 mode。`N` 是内部状态机，主要有 `0/1/2` 三个阶段；`A` 是 tick 计数器；`w` 是本轮是否已经处理过 attack 的 guard。

## 核心实现逻辑

### 1. `Expo.e8.F()`：速度写入后的削减逻辑

`F()` 是静态方法，结合 `dV` 常量池引用和调用位置判断，它很可能对应 `dV` 后处理或某个 tick 回调。

它根据模式 `k` 和状态 `N` 做两类处理：

#### 百分比削减

典型代码形态：

```java
player.motionX *= (1.0 - s.b() / 100.0);
player.motionZ *= (1.0 - s.b() / 100.0);
```

字节码里对 `field_70159_w` 和 `field_70179_y` 做：

- 读取当前 motion
- 读取 `s.b()` 的整数值
- 转成 double
- 除以常量比例
- 从 `1.0` 中减去
- 再乘回当前 motion

这说明 `s` 大概率是 Velocity 百分比/Reduce 百分比。若 `s = 100`，理论上水平速度会被乘到 0；若 `s = 0`，保留原速度。

#### 固定倍率削减

另一个分支直接：

```java
player.motionX *= const;
player.motionZ *= const;
```

这个像内置模式或状态分支，不走用户百分比设置。

### 2. `Expo.e8.I(Expo.dr)`：攻击事件联动

`Expo.dr` 是攻击事件，字段里保存被攻击实体：

- `new Expo.dr(Entity)`
- `B()` 返回目标实体

`e8.I(dr)` 的处理：

1. 先检查当前模式 `k` 是否匹配某个字符串模式。
2. 如果模块当前内部 flag `w` 已经置位，则直接跳过。
3. 只处理目标是 `EntityPlayer` 的攻击事件。
4. 根据内部状态 `N` 分支：
   - `N == 0`：
     - 如果本地玩家满足某个状态判断，则 cancel 当前攻击事件。
     - 设置 `N` 到下一状态，重置计数 `A`。
     - 否则进入另一个状态。
   - `N == 1`：
     - 设置本地玩家某个 boolean 状态。
     - 重置/切换 `A`、`N`。
5. 末尾把 `w` 置为 true。

这说明 Velocity 不是完全独立处理 knockback，它和 attack 有联动：攻击玩家时会改变 Velocity 状态，某些模式下甚至会取消一次 attack event，用来配合服务器反击退检测或制造 hit timing。

### 3. `Expo.e8.U(Expo.d3)`：更新事件里的状态修正

`d3` 是空事件，常见形态像 Update/Motion tick。

`U(d3)`：

- 只在某个模式字符串匹配时执行。
- 根据 `N` 分支：
  - `N == 1`：设置本地玩家某个 boolean 状态。
  - `N == 2`：先判断玩家状态，再按随机/条件设置 boolean 状态。

这里没有直接改 `motionX/motionZ`，更像配合状态机的 tick 修正，例如 sprint/sneak/onGround/velocity 标记之类。由于具体字段通过 invokedynamic 反射访问，字段名被隐藏，只能确定它是在操作 `EntityPlayerSP` 的 boolean 状态。

### 4. `Expo.e8.K(Expo.dM)`：另一个 tick/后处理状态机

`dM` 也是空事件。

`K(dM)`：

1. 如果计数器 `A` 超过阈值，调用模块 reset。
2. 根据 `N` 分支：
   - `N == 1`：
     - 设置本地玩家 boolean 状态。
     - `A++`
   - `N == 2`：
     - 根据本地玩家状态和随机条件设置 boolean 状态。
     - 重置 `A`
     - 重置 `N`

这说明 `A` 是一个短周期 tick 计数器，`N` 是 Velocity 的内部阶段：

- `N == 0`：初始/待触发
- `N == 1`：第一阶段处理
- `N == 2`：第二阶段处理/收尾

## 和 attack 的关系

Velocity 模块确实处理 attack：

- 方法：`Expo.e8.I(Expo.dr)`
- 事件：`Expo.dr`，也就是攻击事件
- 目标限制：只对 `EntityPlayer` 生效
- 行为：
  - 在某些模式下，攻击玩家会触发/推进 Velocity 状态机。
  - `N == 0` 的某个分支会调用 `dr` 的取消方法，等价于取消这次攻击事件。
  - 后续 `d3` / `dM` 事件继续对本地玩家状态和 motion 做处理。

所以这个 Velocity 不是单纯 “收到击退包就乘百分比”，它还有 attack-based 状态绕过逻辑。

## 是否处理爆炸 Velocity

目前静态搜索没有发现明确的：

- `S27PacketExplosion`
- `Explosion`
- `func_149149_c`
- `func_149144_d`
- `func_149147_e`

因此当前能确认的实现是针对 `S12PacketEntityVelocity`。爆炸击退如果有处理，可能被放在其他受保护/native/动态反射路径里，但在普通 class 字节码中没有直接证据。

## 伪代码总结

```java
// ASM hook: S12PacketEntityVelocity
onHandleEntityVelocity(packet) {
    Entity entity = world.getEntityByID(packet.getEntityID());

    if (entity == mc.thePlayer) {
        dW event = new dW(packet.motionX, packet.motionY, packet.motionZ);
        eventBus.post(event);

        if (event.isCancelled()) {
            callback.cancel();
            return;
        }

        entity.setVelocity(event.x / 8000.0, event.y / 8000.0, event.z / 8000.0);
        eventBus.post(new dV());
    } else {
        entity.setVelocity(packet.motionX / 8000.0,
                           packet.motionY / 8000.0,
                           packet.motionZ / 8000.0);
    }

    callback.cancel();
}
```

```java
// Expo.e8 simplified
onVelocityPostOrTick() {
    if (!modeMatches()) return;

    if (state == SPECIAL_PERCENT_STATE) {
        player.motionX *= 1.0 - percent / 100.0;
        player.motionZ *= 1.0 - percent / 100.0;
    } else {
        player.motionX *= fixedFactorX;
        player.motionZ *= fixedFactorZ;
    }
}

onAttack(AttackEvent e) {
    if (!modeMatches()) return;
    if (alreadyHandledThisCycle) return;
    if (!(e.target instanceof EntityPlayer)) return;

    switch (state) {
        case 0:
            if (playerCondition()) {
                e.cancel();
                state = 1;
                ticks = 0;
            } else {
                state = 2;
                ticks = 0;
            }
            break;

        case 1:
            setPlayerBooleanState();
            ticks = 0;
            state = 2;
            break;
    }

    alreadyHandledThisCycle = true;
}
```

## 文件索引

- `D:\crack\Expo-ForgeMod (1)\Expo\e8.class`：Velocity 模块主体。
- `D:\crack\Expo-ForgeMod (1)\Expo\ASM\Hooks\M.class`：`S12PacketEntityVelocity` hook。
- `D:\crack\Expo-ForgeMod (1)\Expo\dw.class` / real `Expo.dW`：速度事件，可取消可改值。
- `D:\crack\Expo-ForgeMod (1)\Expo\dv.class` / real `Expo.dV`：速度应用后的事件。
- `D:\crack\Expo-ForgeMod (1)\Expo\dr.class`：攻击事件，`Expo.gG.D(Entity)` 发出。
