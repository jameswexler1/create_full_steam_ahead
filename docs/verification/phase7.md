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

Implementation notes:

- `neoforge.mods.toml` now declares `simulated` as an optional dependency alongside optional `sable` and `aeronautics`.
- Full Steam Ahead registers Create `BlockMovementChecks` during common setup.
- Engine blocks are marked movable, movement-necessary, non-brittle, supportive, and attached to adjacent Full Steam Ahead engine blocks.
- Bottom cylinder and inlet shell blocks attach downward to Create Fluid Tank boilers.
- `boiler_outlet` attaches to its boiler tank side and output pipe side.
- `steam_inlet` attaches to adjacent Create pipes.
- Create Fluid Tank and Create pipe checks are symmetric for these Full Steam Ahead connections, so assembly can discover the link from either side.
- Simulated compatibility uses guarded reflection against `dev.simulated_team.simulated.index.SimBlockMovementChecks`; there are no hard imports from Simulated, Aeronautics, or Sable.
- `steam_cylinder`, `crankshaft`, `boiler_outlet`, and `steam_inlet` are listed in `create:safe_nbt`.

Runtime status:

- Standalone automated checks pass without Aeronautics, Simulated, or Sable installed.
- Aeronautics/Sable sublevel runtime testing passed in a profile that includes those mods.
- Save/reload verification inside an assembled Aeronautics/Sable sublevel passed.

Manual runtime checklist:

- [ ] Run without Aeronautics, Simulated, or Sable installed and confirm the game still opens.
- [ ] Confirm direct compact and pipe-fed engines still assemble and run normally.
- [x] In an Aeronautics/Sable test profile, assemble a ship/sublevel containing a Full Steam Ahead engine.
- [x] Confirm cylinder, inlet, piston, crankshaft, boiler outlet, and attached boiler/pipe connections move together.
- [x] Confirm engine NBT/state survives assembly, disassembly, world reload, and sublevel reload.
- [x] Confirm Create shafts connected to the crankshaft can power Aeronautics propellers while assembled on the sublevel.

User report on 2026-05-21: Create Aeronautics was added to the dev runtime, a simulated contraption containing the engine and components was assembled in-game, and the engine worked correctly while assembled.

User report after reload test: the Aeronautics contraption reload test works.
