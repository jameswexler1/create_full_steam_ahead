# Phase 7 Verification

Date: 2026-05-20

Planned scope:

- Keep Aeronautics, Simulated, and Sable optional.
- Register Create movement checks for Full Steam Ahead engine blocks.
- Register Simulated movement checks through reflection when the Simulated API is loaded.
- Preserve important Full Steam Ahead block entity NBT through Create contraption movement.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Automated results:

- `compileJava`: passed
- `processResources`: passed
- `build`: passed
- JSON validation: passed
- Latest automated run on 2026-06-12 after adding Aeronautics steam vent consumption:
  `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and
  `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed.

Implementation notes:

- `neoforge.mods.toml` now declares `simulated` as an optional dependency alongside optional `sable` and `aeronautics`.
- Optional dependency metadata accepts Aeronautics/Simulated `1.2.1` through `<1.4.0` and Sable `1.2.1` through `<3.0.0`, covering the tested Aeronautics `1.3.0`, Simulated `1.3.0`, and Sable `2.0.2` profile without making those mods required.
- Full Steam Ahead registers Create `BlockMovementChecks` during common setup.
- Engine blocks are marked movable, movement-necessary, non-brittle, supportive, and attached to adjacent Full Steam Ahead engine blocks.
- Boiler outlets and relief valves attach to their boiler faces; Phase 20 removed the obsolete cylinder-shell attachment to tanks below.
- `boiler_outlet` attaches to its boiler tank side and output pipe side.
- `steam_inlet` attaches to adjacent Create pipes.
- Create Fluid Tank and Create pipe checks are symmetric for these Full Steam Ahead connections, so assembly can discover the link from either side.
- Simulated compatibility uses guarded reflection against `dev.simulated_team.simulated.index.SimBlockMovementChecks`; there are no hard imports from Simulated, Aeronautics, or Sable.
- Aeronautics steam vent compatibility is guarded by block id/reflection: powered `aeronautics:steam_vent`
  blocks mounted on boilers with FSA outlets consume FSA steam proportional to their live Aeronautics
  `getGasOutput()`. The default conversion is `5000 m³ -> 10 mB/t`, configurable through
  `aeronauticsCompat.steamVentMbPerM3`.
- Pipe-fed Aeronautics steam vent compatibility is guarded by a non-required optional mixin config:
  vents placed above Create fluid pipes can accept FSA `steam`, draw from network pressure, and output
  Aeronautics hot air without requiring a boiler directly below.
- `steam_cylinder`, `piston_head`, `powered_shaft`, `boiler_outlet`, and `steam_inlet` are listed in `create:safe_nbt`.

Runtime status:

- Standalone automated checks pass without Aeronautics, Simulated, or Sable installed.
- Aeronautics/Sable sublevel runtime testing passed in a profile that includes those mods.
- Save/reload verification inside an assembled Aeronautics/Sable sublevel passed.
- 2026-06-18 Prism profile failure diagnosed: old optional ranges rejected Aeronautics `1.3.0`, Simulated `1.3.0`, and Sable `2.0.2` before client events could load.
- 2026-06-12 automated checks for pipe-fed Aeronautics steam vent compatibility passed:
  `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and
  `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`.

Manual runtime checklist:

- [ ] Run without Aeronautics, Simulated, or Sable installed and confirm the game still opens.
- [ ] Run in the Prism Aeronautics 4 profile with Aeronautics `1.3.0`, Simulated `1.3.0`, and Sable `2.0.2` and confirm the dependency gate no longer blocks startup.
- [ ] Confirm upright and inverted pipe-fed engines still assemble and run normally.
- [x] In an Aeronautics/Sable test profile, assemble a ship/sublevel containing a Full Steam Ahead engine.
- [x] Confirm cylinder, inlet, piston, piston head, powered shaft, boiler outlet, and intended pipe/boiler-device connections move together.
- [x] Confirm engine NBT/state survives assembly, disassembly, world reload, and sublevel reload.
- [x] Confirm Create shafts linked to the engine output can power Aeronautics propellers while assembled on the sublevel.
- [ ] With Aeronautics installed, place a powered steam vent on an FSA-fed boiler and confirm outlet/display flow consumption rises by the configured amount.
- [ ] With Aeronautics installed, place a steam vent above a Create fluid pipe carrying FSA `steam`, power it with redstone, and confirm it outputs hot air.
- [ ] Close a valve or open a pipe leak before the pipe-fed vent and confirm output falls with pressure.
- [ ] Change `aeronauticsCompat.steamVentMbPerM3` and confirm the steam vent's consumption changes proportionally.

User report on 2026-05-21: Create Aeronautics was added to the dev runtime, a simulated contraption containing the engine and components was assembled in-game, and the engine worked correctly while assembled.

User report after reload test: the Aeronautics contraption reload test works.
