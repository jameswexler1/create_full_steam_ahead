# Phase 9 Verification — Balance, Config, Recipes

Date: 2026-06-02

## Implemented

- **Server config** (`config/FullSteamConfig.java`, `ModConfigSpec`, `ModConfig.Type.SERVER`,
  registered in `FullSteamAhead`):
  - `balance.baseEngineCapacity` (default 147456) — full-engine SU cap.
  - `balance.steamPerHeatUnit` (default 10) — mB/t per heat unit; drives outlet production, inlet
    intake cap, pipe pressure flow reserve, and piped engine SU together via derived accessors
    (`suPerSteamMb()`, `maxPipedSteamPerTick()`).
  - `balance.boilerOutletPressureRange` (default 30) — pipe pressurization range in blocks.
  - `balance.enableDirectCompactMode` (default true) — when false, `PistonHeadBlockEntity`
    skips the direct compact-boiler path so engines require piped steam.
  - Accessors guard on `SPEC.isLoaded()` and fall back to the defaults; all reads occur in
    block-entity ticks (after the world's server config loads). Defaults reproduce the prior
    hardcoded values exactly, so out-of-the-box balance is unchanged.
  - Wired through `PistonHeadBlockEntity`, `BoilerOutletBlockEntity`,
    `SteamPipePressureCoordinator`, and `SteamInletBlockEntity` (removed the corresponding
    `static final` constants / derived class-init values).
- **Recipes**: already implemented (commit `dba10d5`); they are vanilla `crafting_shaped` /
  `crafting_shapeless` types.
- **JEI/EMI display**: vanilla recipe types display automatically. Added JEI as a runtime-only
  test dependency (`mezz.jei:jei-1.21.1-neoforge:19.27.0.340`, BlameJared maven) so recipes can be
  browsed in `runClient`; it is not part of the shipped jar and there is no custom plugin.
- **Max piston height**: deferred. The v1 engine column is fixed (`EngineValidator.pistonPositions`
  head → piston → empty → shaft), so there is no configurable height yet.

## Automated checks

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew dependencies --configuration runtimeClasspath`
  resolves `mezz.jei:jei-1.21.1-neoforge:19.27.0.340`.

## Manual runtime checklist

- [ ] `runClient`: open JEI, confirm crafting recipes show for steam cylinder, piston, piston head,
  boiler outlet, steam inlet, engine telegraph, and stepped lever.
- [ ] With default config, build a boiler + pipe-fed engine; confirm goggle readouts (outlet
  production mB/t, engine SU/RPM, inlet accepted/consumed) match pre-config behavior.
- [ ] Edit `runs/client/saves/<world>/serverconfig/full_steam_ahead-server.toml`: set
  `steamPerHeatUnit=20`, lower `boilerOutletPressureRange`, set `enableDirectCompactMode=false`;
  reload the world and confirm production scales, pipe pressure range shortens, and an upright
  compact-boiler engine no longer runs without piped steam.
