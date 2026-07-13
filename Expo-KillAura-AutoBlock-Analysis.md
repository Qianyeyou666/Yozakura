# Expo ForgeMod KillAura / AutoBlock Static Analysis Report

## Scope

Target path:

`D:/crack/Expo-ForgeMod (1)/`

The directory is not a normal source tree. It contains compiled `.class` files and resources. Most classes are obfuscated, and some runtime string/method resolution is protected by JNIC/PhantomShield-style logic. This report is based on static bytecode inspection only.

No client code was executed.

## Summary

The KillAura module is located in:

`D:/crack/Expo-ForgeMod (1)/Expo/eu.class`

`javap` reports the real class name as:

`Expo.eU extends Expo.es`

The strongest evidence is that this class contains:

- A `net.minecraft.entity.EntityLivingBase` target field.
- Lambda names such as `lambda$getAttackTarget`.
- References to `C02PacketUseEntity`.
- References to `C07PacketPlayerDigging.Action.RELEASE_USE_ITEM`.
- References to `C08PacketPlayerBlockPlacement`.
- Target selection, attack timing, and blocking state logic in one module class.

An important related class is:

`D:/crack/Expo-ForgeMod (1)/Expo/e4.class`

This appears to be a packet listener/filter helper. It detects outgoing `C02PacketUseEntity`, `C07PacketPlayerDigging` with `RELEASE_USE_ITEM`, and `C08PacketPlayerBlockPlacement` when the placed stack is an `ItemSword`.

## Key Classes

### `Expo.eU`

Likely role: KillAura module.

Relevant fields:

- `private EntityLivingBase p`: current attack target.
- `private boolean C`: local blocking/spoof blocking state.
- `private int Y`: delay/timer counter.
- `private long a`: attack timing timestamp.
- `private long j`: autoblock timing timestamp.
- `private int e`: spoofed slot tracking.
- `static boolean q`, `static boolean V`, `static boolean U`: global state flags used across attack/block handling.
- `static Expo.iT k`: mode/string setting, likely the KillAura/AutoBlock mode selector.
- `static Expo.iP v`, `F`, `n`, etc.: numeric settings, likely range/APS/delay values.
- `static Expo.ib ...`: boolean settings.

Relevant methods:

- `R(Expo.dO)`: main update/tick handler.
- `O(boolean, boolean, EntityLivingBase)`: starts or refreshes autoblock.
- `z(boolean)`: releases autoblock.
- `j()`: checks whether the player is blocking.
- `U(boolean)`: sends a spoofed held item slot change.
- private `O()`: switches back to the real/current slot.
- `w(double, boolean)`: target selection.
- `n(EntityLivingBase, boolean)`: target validity check.
- `d()`: autoblock decision helper returning a pair of booleans.

### `Expo.e4`

Likely role: packet listener/filter for attack/block packets.

Relevant method:

`private boolean D(Packet<?>)`

Detected behavior:

- Returns true for `C02PacketUseEntity`.
- Checks `C07PacketPlayerDigging`; specifically detects `RELEASE_USE_ITEM`.
- Checks `C08PacketPlayerBlockPlacement`; returns true only when the packet stack exists and its item is an `ItemSword`.

This makes it strongly related to autoblock packet tracking.

## AutoBlock Behavior

### Start block / re-block

Core method:

`Expo.eU.O(boolean, boolean, EntityLivingBase)`

Observed bytecode behavior:

1. Reads the player's current held `ItemStack`.
2. If the second boolean argument is true and a target exists, it raytraces against the target hitbox.
3. If the raytrace hits, it sends:

```java
new C02PacketUseEntity(target, hitVecRelativeToTarget)
new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT)
```

4. It then sends:

```java
new C08PacketPlayerBlockPlacement(currentItemStack)
```

5. It updates local client-side use/block state, equivalent to setting the item in use for the current sword stack.
6. It sets the module's internal blocking state.

Interpretation:

The client does not only send a plain block-placement packet. In at least one mode/path, it can add entity interaction packets before `C08PacketPlayerBlockPlacement`. This is usually done to make server-side item use/blocking appear more legitimate or to satisfy server interaction expectations.

### Release block

Core method:

`Expo.eU.z(boolean)`

Observed bytecode behavior:

When conditions allow, it sends:

```java
new C07PacketPlayerDigging(
    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
    BlockPos.ORIGIN,
    EnumFacing.DOWN
)
```

Then it clears internal blocking state.

Interpretation:

The module explicitly releases use-item before some attacks or state transitions. This is a standard 1.8.9 autoblock pattern: release block, attack, then send block placement again.

### Blocking state check

Core method:

`Expo.eU.j()`

Observed behavior:

- If the module is in its own spoof-blocking state, it returns the internal boolean field.
- Otherwise it calls:

```java
EntityPlayerSP.func_71039_bw()
```

In MCP names, this is `isBlocking()`.

Interpretation:

The module can distinguish real client blocking from spoofed/module-managed blocking.

### Slot spoof

Relevant methods:

- `Expo.eU.U(boolean)`
- private `Expo.eU.O()`

Observed behavior:

`U(boolean)` computes a nearby hotbar slot and sends:

```java
new C09PacketHeldItemChange(spoofSlot)
```

It stores the spoof slot and sets a static flag indicating that a slot spoof is active.

The private `O()` method sends:

```java
new C09PacketHeldItemChange(currentRealSlot)
```

Then clears the slot spoof flag.

Interpretation:

Some autoblock mode uses held-item-change spoofing. This is usually used to reset server-side blocking or disguise the release/reblock sequence without visibly changing the player's real selected item.

## Main Tick Flow

The main update handler is:

`Expo.eU.R(Expo.dO)`

High-level flow:

1. Decrements an internal delay counter `Y`.
2. If the module is disabled or cannot operate, it computes autoblock state and may cancel/update the event.
3. Selects a target with `w(range, throughWallsOrVisibilityFlag)`.
4. Reads a mode setting from `k`.
5. Based on mode, it sets internal state `Q` and may:
   - Release block.
   - Start/restart block.
   - Send held-item-change spoof.
   - Trigger a local swing/use animation.
6. Uses two timing gates:
   - Attack timing via timestamp `a` and numeric setting `v`.
   - AutoBlock timing via timestamp `j` and numeric setting `F`.
7. If timing and state checks pass, it calls the attack/block routine and then performs mode-specific post-attack autoblock handling.

Because the mode strings are encrypted and resolved dynamically, this report describes modes by behavior rather than by UI label.

## Event-Level AutoBlock Placement

AutoBlock is mainly driven from the update/tick event, not directly from the packet event.

### Core event: `Expo.dO`

Handled by:

`Expo.eU.R(Expo.dO)`

Likely role:

Update / motion tick event.

Evidence:

- `Expo.dO` extends the common event base `Expo.Protected.vX`.
- It only contains several boolean flags and cancel/state methods, which is typical for a generic update/motion stage event.
- `Expo.eU.R(Expo.dO)` performs the main KillAura loop:
  - decrements internal delay counter `Y`;
  - selects/refreshes the current target;
  - checks attack timer;
  - checks autoblock timer;
  - decides whether to release block, attack, spoof slot, and re-block;
  - calls the autoblock helpers that send `C07`, `C08`, `C09`, and sometimes `C02`.

Conclusion:

The actual AutoBlock state machine runs in `R(Expo.dO)`.

### Pre/Post determination

Short answer:

`Expo.eU.R(Expo.dO)` should be treated as the pre/motion-update AutoBlock path, not the post-update path.

Evidence:

- The ASM hook table exposes separate hook entry points:
  - `EntityPlayerSP$onPreUpdate(Expo.ASM.Hooks.x)`
  - `EntityPlayerSP$onUpdateWalkingPlayer(...)`
  - `EntityPlayerSP$onPostUpdate()`
- The KillAura AutoBlock scheduler is bound to `Expo.dO`, and `Expo.dO` is the motion/update-style event with boolean stage/cancel flags.
- `R(Expo.dO)` performs target selection and immediately sends/queues attack and block packets (`C07`, `C08`, `C09`, sometimes `C02`). That placement matches the motion/update phase where outgoing player state and combat packets are prepared.
- The client has a distinct `EntityPlayerSP$onPostUpdate()` hook, but the observed AutoBlock state machine is not centered there.

Interpretation:

For practical reversing purposes, call the AutoBlock event `PreUpdate` / `PreMotion` / `onUpdateWalkingPlayer` stage. It is not the packet event, and it is not the separate post-update hook. Because `dO` itself carries multiple boolean flags, some other modules may set/read stage flags on the same event class, but the KillAura AB core in `R(dO)` is the pre/motion-side handler.

### Damage/status event: `Expo.dC`

Handled by:

`Expo.eU.V(Expo.dc)`

Class identity:

`javap` reports `Expo.dc` as real class `Expo.dC`.

Fields:

```java
public final DamageSource l;
public final EntityLivingBase M;
```

Likely role:

Living damage / hurt event.

Observed use:

This method does not perform the normal autoblock loop. It adjusts internal state flags such as `P` and `Q` for certain modes. It looks like a secondary state reset/transition hook used after damage/hurt events.

### Generic int event: `Expo.dS`

Handled by:

`Expo.eU.C(Expo.ds)`

Class identity:

`javap` reports `Expo.ds` as real class `Expo.dS`.

Fields:

```java
private int g;
```

Likely role:

A small cancellable phase/event with an integer stage or key/id. Exact meaning is not proven from the class alone.

Observed use:

When global autoblock state `q` is active and the mode matches one of several encrypted mode strings, it calls the event cancel method if the player is not considered blocking via `j()`.

Interpretation:

This is not where block packets are sent. It is a support event used to cancel or gate some behavior when autoblock state and real/spoof blocking are out of sync.

### Empty event: `Expo.dK`

Handled by:

`Expo.eU.Y(Expo.dk)`

Class identity:

`javap` reports `Expo.dk` as real class `Expo.dK`.

Fields:

None.

Observed use:

If autoblock state `q` is active and a boolean setting `b` is enabled, it calls the event cancel method.

Interpretation:

This is another support event. Given the no-field shape and use under blocking state, it may be related to movement slowdown, action interruption, or another tick-stage gate, but the exact name is not recoverable from static class shape alone.

### Key event: `Expo.dI`

Handled by:

`Expo.eU.H(Expo.dI)`

Fields:

```java
public final int E;
```

Observed use:

Compares `E` with:

```java
Minecraft.gameSettings.keyBindAttack.getKeyCode()
```

If it matches, it calls static `Expo.eU.a()`, which sets the `V` state flag.

Interpretation:

This lets the module react to attack-key input and mark state for the next block/attack cycle.

### Packet event: `Expo.dN`

Related helper:

`Expo.e4.W(Expo.dN)`

Fields:

```java
public final Packet<?> T;
```

Observed use:

`Expo.e4` checks outgoing packets and detects:

- `C02PacketUseEntity`
- `C07PacketPlayerDigging` with `RELEASE_USE_ITEM`
- `C08PacketPlayerBlockPlacement` with an `ItemSword`

Conclusion:

The packet event is not the primary AutoBlock scheduler. It is used to observe/filter/track packets associated with attack and autoblock.

## Attack Handling

Attack scheduling is driven from `Expo.eU.R(Expo.dO)`.

Observed flow:

1. `R(dO)` selects the current target into `p`.
2. It checks the attack timer:

```text
now - a >= value(v) * constant
```

Here `a` is the last attack timestamp and `v` is the attack delay / APS-style setting.

3. It separately checks the autoblock timer using timestamp `j` and setting `F`.
4. If attack timing, target validity, mode state, and autoblock state allow it, it enters the attack path.
5. The helper `p()` calls:

```java
Expo.aT.U()
this.a = System.currentTimeMillis()
```

The timestamp reset confirms this is part of the attack execution path.

The concrete attack packet helper is:

`Expo.gG.D(Entity)`

This method does:

```java
new Expo.dr(target)          // attack event
eventBus.C(event)
if (!event.H()) {
    PlayerControllerMP sync/prep helper
    send new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK)
    if not spectator:
        apply local attack side effects
}
```

Local side effects are handled by private `Expo.gG.j(Entity)`, which mirrors vanilla-style attack consequences:

- calculates base attack damage;
- includes enchantment damage;
- checks sprint/fall/critical conditions;
- applies local damage/knockback logic;
- calls critical/enchantment animation hooks such as `func_71009_b` and `func_71047_c`;
- updates stats/exhaustion;
- posts a post-attack-style event `Expo.dP`.

Interpretation:

The attack is not just `PlayerControllerMP.attackEntity`. The client sends a direct `C02PacketUseEntity(..., ATTACK)` packet through `Expo.aT.U(Packet)`, then manually performs local vanilla-like attack side effects unless the player is in spectator mode.

With AutoBlock enabled, the practical order is mode-dependent but follows this general pattern:

```text
if currently blocking:
    C07 RELEASE_USE_ITEM

C02 USE_ENTITY ATTACK
local swing / attack side effects

if autoblock mode wants reblock:
    optional C02 INTERACT / hitVec interact
    C08 BLOCK_PLACEMENT with sword
```

## Packet Sequence Patterns

The class supports at least these patterns:

### Plain block

```text
C08PacketPlayerBlockPlacement(currentSwordStack)
```

### Release then block

```text
C07PacketPlayerDigging(RELEASE_USE_ITEM, ORIGIN, DOWN)
C08PacketPlayerBlockPlacement(currentSwordStack)
```

### Interact block

```text
C02PacketUseEntity(target, hitVec)
C02PacketUseEntity(target, INTERACT)
C08PacketPlayerBlockPlacement(currentSwordStack)
```

### Slot spoof block

```text
C09PacketHeldItemChange(spoofSlot)
... release/block/attack logic ...
C09PacketHeldItemChange(realSlot)
```

## Practical Interpretation

This KillAura's autoblock is not a single simple toggle. It is a state machine combining:

- Target acquisition.
- Attack APS timing.
- AutoBlock APS/timing.
- Release-use-item packets.
- Block-placement packets with sword stack.
- Optional interact packets against the target.
- Optional held-item slot spoofing.
- Local client-side blocking animation/state updates.

The most important packet pair is:

```text
C07 RELEASE_USE_ITEM
C08 BLOCK_PLACEMENT with sword
```

The more advanced path adds:

```text
C02 INTERACT / hit-vector interact
```

before re-blocking.

## Confidence

High confidence:

- `Expo.eU` is the KillAura module.
- `O(boolean, boolean, EntityLivingBase)` is the block-start/reblock method.
- `z(boolean)` is the block-release method.
- `C08PacketPlayerBlockPlacement` is used to start server-side sword blocking.
- `C07PacketPlayerDigging.RELEASE_USE_ITEM` is used to stop blocking.
- `C09PacketHeldItemChange` is used for slot spoofing.

Medium confidence:

- Exact UI names of each AutoBlock mode. The mode strings are encrypted and resolved dynamically.
- Exact meaning of each static boolean/numeric setting field.

Low confidence:

- Whether all branches are reachable in the released client configuration.
- Server-specific intent of each mode, such as whether a branch is named Hypixel, Legit, Spoof, or similar.

## Limitations

- No original Java source is present.
- Class and field names are heavily obfuscated.
- Some strings and invokedynamic targets are runtime-resolved.
- JNIC/PhantomShield protection is present, so the classes were not executed for dynamic inspection.
- This is a static bytecode report, not a runtime trace.
