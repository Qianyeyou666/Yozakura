# Spec: AutoClicker, BlockHit, and Velocity Rewrite

## Objective

Replace the current combat input implementations with focused state machines based on the local
OpenMyau Plus, LeaderClient, and Raven BS references.

- AutoClicker is triggered only by the physical left mouse button. It never presses, releases, or
  cancels the use-item/right-click binding.
- BlockHit exposes Helper, Auto, and Lag behavior without globally cancelling right clicks.
- Velocity exposes only Attack and Reduce. Attack opens a short post-S12 window and consumes one
  existing real attack event (custom or Forge); it never manufactures an animation or attack
  packet. Reduce preserves the local S12 packet unchanged so vanilla and the server simulate the
  same knockback.

## Tech Stack

Java 8 source compatibility, Minecraft Forge 1.8.9, the existing custom event bus, and JUnit 4.

## Commands

- Tests: `gradlew.bat test`
- Build: `gradlew.bat build`
- Targeted combat tests: `gradlew.bat test --tests "gq.yozakura.module.combat.*"`

## Project Structure

- Combat modules: `src/main/java/gq/yozakura/module/combat`
- Pure combat state controllers: `src/main/java/gq/yozakura/module/combat`
- Regression tests: `src/test/java/gq/yozakura/module/combat`

## Code Style

```java
if (!isInGame() || mc.currentScreen != null) {
    resetState();
    return;
}
```

Use four-space indentation, explicit Java 8 types, focused helpers, and no silent fallback modes.

## Testing Strategy

- Unit-test click scheduling and Velocity attack/reduction state independently from Minecraft.
- Add source-level contracts for the input boundary and the public mode list.
- Use the repository build as the integration gate. Minecraft is not launched without approval.

## Boundaries

- Always: preserve unrelated working-tree edits and keep non-local entity velocity packets untouched.
- Ask first: launching Minecraft or changing dependencies.
- Never: globally cancel right clicks from AutoClicker/BlockHit or reintroduce hidden fallback modes.

## Success Criteria

- Right click alone cannot arm or fire AutoClicker.
- AutoClicker contains no use-item key mutation.
- BlockHit has Helper, Auto, and Lag modes and no unconditional right-click cancellation listener.
- Velocity has exactly Attack and Reduce modes.
- Attack mode accepts at most one valid real attack inside its timeout and applies the configured
  vanilla sprint slowdown through the existing local attack-sprint hook.
- Reduce mode preserves the local player's server-sent S12 motion without cancelling, scaling, or
  asynchronously replaying the packet.
- Targeted combat tests pass and the project compiles; pre-existing unrelated test failures are reported.

## Open Questions

None. The reference repositories supplied by the user define the intended behavior family.
