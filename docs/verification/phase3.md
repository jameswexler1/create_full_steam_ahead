# Phase 3 Verification

Date: 2026-05-20

Implemented:

- `SteamCylinderBlock` now implements Create `IBE<SteamCylinderBlockEntity>`.
- `SteamCylinderBlockEntity` extends Create `SmartBlockEntity` and provides goggle tooltip status.
- `CylinderConnectivity` validates vertical 3x3x2 hollow rings with bounded placement/removal scans.
- Valid rings flip all 16 `steam_cylinder` blocks to `assembled=true`.
- Invalid or broken rings clear `assembled` state and cached ring data.
- Boiler detection links to Create `FluidTankBlockEntity` blocks directly below the shell footprint.
- The assembled cylinder uses a distinct placeholder model for visible testing.

Automated checks run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
rg "com\.simibubi\.create\.foundation\.block\.SmartBlock|large_steam_engine_controller|firebox|large_engine_casing|boiler_drum|output_coupling|piston_rod" src/main
```

Results:

- `compileJava`: passed
- `processResources`: passed
- `build`: passed
- JSON validation: passed
- Removed-name and missing `SmartBlock` import scan under `src/main`: passed, no matches

Manual runtime checklist:

- [x] Run `./gradlew runClient`.
- [x] Build fewer than 16 cylinder blocks and confirm no assembly.
- [x] Complete a 3x3x2 hollow cylinder ring and confirm all 16 blocks change to the assembled placeholder model.
- [x] Break one cylinder block and confirm the remaining ring disassembles.
- [x] Fill a center position with a non-piston block and confirm the ring does not assemble.
- [x] Build the ring above a 3x3 Create Fluid Tank layer and confirm goggles show boiler linked.
- [x] Build the ring without tanks below and confirm goggles show no steam source.
- [x] Save and reload the world and confirm the ring revalidates.

Manual result reported by Gustavo on 2026-05-20. Negative cases were confirmed in-world after the initial Phase 3 build.
