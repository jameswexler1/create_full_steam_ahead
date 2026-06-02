# Shaft Activation Race Fix

Date: 2026-06-02

## Symptom

After assembling the engine and supplying steam, placing the output shaft sometimes
failed to start the engine. The linkage visuals appeared but the shaft never turned and
no SU was produced. Breaking and replacing the shaft fixed it only sometimes; the same
shaft run could power one engine and not another. The behaviour was random.

## Root cause

`PistonHeadBlockEntity` pushes generated speed to the shaft synchronously from inside the
block-place event (`EngineShaftEventHandler` -> `revalidateStructure` ->
`ensurePoweredShaft` converts the placed shaft and `updateShaftOutput` calls
`FullSteamPoweredShaftBlockEntity.update`). At that moment the freshly created powered
shaft block entity has not yet been attached to the kinetic network, so the
`updateGeneratedRotation()` triggered by that push can be lost.

`PistonHeadBlockEntity.tick()` only re-pushes to the shaft when the engine's own computed
steam output *changes*. Once the output stabilises it never pushes again, and
`FullSteamPoweredShaftBlockEntity.update()` itself early-outs when the stored speed and
capacity are unchanged. So a lost initial attach was never corrected, leaving the engine
assembled but stuck until a new placement event rolled the dice again.

Create's own `PoweredShaftBlockEntity` avoids this with an `initialTicks` startup window
and a `SteamEngineBlockEntity.tick()` that re-asserts the shaft every tick.

## Fix

`FullSteamPoweredShaftBlockEntity` now self-heals during a short window after it is placed
or loaded (`INITIAL_SYNC_TICKS`). On each of those server ticks, if the shaft should be
generating (`getGeneratedSpeed() != 0`) but the network still reports a standstill
(`getSpeed() == 0`), it re-runs `updateGeneratedRotation()`. By then the entity is attached
to the network, so the source takes effect. The window is bounded, so legitimate later
standstills (for example an overstressed network) do not cause repeated re-attachment.

## Checks

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [ ] In-world: assemble an engine, supply steam, place the shaft repeatedly (break/replace
  many times) and confirm it now starts every time, both upright and upside down.
- [ ] In-world: chain the same shaft run across several engines and confirm all of them
  start.
- [ ] In-world: save and reload the world with a running engine and confirm it resumes.
