# Block Directionality: shift-place + wrench

Date: 2026-06-02

## Goal

Make the addon's oriented blocks behave like vanilla Create:

- Holding **shift** while placing flips the facing (opposite of a normal place).
- The Create **wrench** re-orients the block (with the rotate sound); **sneak-wrench** removes it.
- Works on the multiblock parts too (piston head/body, steam inlet): wrenching re-orients and
  the engine re-validates, rebuilding if the new orientation is still valid or disassembling
  cleanly if not.

## Implementation

New shared interface `content/common/FullSteamWrenchable` (extends Create's `IWrenchable`):

- Custom `onWrenched`: `getRotatedBlockState` → `canSurvive` → server `setBlock(UPDATE_ALL)` →
  rotate sound → `onAfterWrench` hook. Plain `setBlock` (not Create's kinetic
  `switchToBlockState`) so our non-kinetic block entities keep their data.
- Default `onSneakWrenched` from `IWrenchable` is reused (drops + destroys = pick up).
- `flipIfShifted(ctx, dir)` / `isPlacingShifted(ctx)` helpers for shift-place.

Per block:

| Block | wrench rotation | shift-place |
|---|---|---|
| `PistonHeadBlock` | flip `FACING` up↔down | flip stair-like facing, ignore auto-connect |
| `SteamPistonBlock` | flip `FACING` up↔down | flip stair-like facing, ignore auto-connect |
| `SteamInletBlock` | flip `FACING` up↔down | new stair-like placement + flip |
| `BoilerOutletBlock` | rotate 6-way around clicked-face axis | opposite of clicked face |
| `EngineTelegraphBlock` | rotate horizontal (Y, clockwise) | face away instead of toward player |
| `SteppedLeverBlock` | rotate horizontal (Y, clockwise) | new face-attached placement + flip |

Engine parts override `onAfterWrench` to call `PistonHeadBlockEntity.revalidateNearbyEngines`
(piston head/body/inlet) or `BoilerOutletBlockEntity::refreshBoilerState` (outlet), reusing the
existing rebuild entry points. `PistonHeadBlock.placementFacing` was made public for reuse.

The stepped lever's `useWithoutItem` toggles it, which would otherwise swallow a non-sneaking
wrench click; its `useItemOn` now returns `SKIP_DEFAULT_BLOCK_INTERACTION` for a `WrenchItem`
so the wrench rotates/removes instead of toggling.

No asset changes: every facing value used already exists in the blockstate JSONs
(piston parts/inlet up+down, outlet all 6, telegraph 4 horizontal, lever all 12 FACE×FACING).

## Out of scope

Cylinder wall blocks (facing is set automatically during ring assembly) and the powered shaft
(already Create's `PoweredShaftBlock`).

## Checks

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [ ] In-world (`runClient`): for each block, place normally then while sneaking and confirm the
  facing is opposite.
- [ ] Wrench each block — confirm the orientation cycles as in the table and the rotate sound
  plays; sneak-wrench returns the block to the inventory.
- [ ] Wrenching the lever rotates it (does not toggle); empty-hand still toggles it.
- [ ] Assemble an engine, supply steam, place the shaft, then wrench the piston head up↔down:
  confirm it disassembles/rebuilds and re-validates with no leftover state, and re-assembles
  and starts after flipping back.
