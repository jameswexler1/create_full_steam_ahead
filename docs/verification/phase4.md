# Phase 4 Verification

Date: 2026-05-20

Implemented:

- `crankshaft` now has a `CrankshaftBlockEntity` extending Create `GeneratingKineticBlockEntity`.
- The crankshaft validates the fixed vertical v1 engine shape: four pistons, assembled 3x3x2 cylinder ring, and Create fluid tanks below the ring.
- Valid structures mark all four piston blocks as assembled and assign `piston_section` values.
- Invalid structures clear piston assembly states and stop generated rotation.
- Boiler efficiency is read from the linked Create `FluidTankBlockEntity` through `BoilerData`.
- Create's `BoilerData.evaluate()` now counts valid Full Steam Ahead crankshafts as attached steam engines.
- Attached Full Steam Ahead engines make 3x3x1 tank boilers use compact boiler sizing instead of vanilla tank-size scaling.
- Assembled piston sections now use distinct placeholder models for visible testing.
- Goggles show crankshaft assembly status, boiler efficiency, RPM, SU capacity, and current flywheel placeholder state.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
rg "large_steam_engine_controller|firebox|large_engine_casing|boiler_drum|output_coupling|piston_rod" src/main
```

Results:

- `compileJava`: passed
- `processResources`: passed
- `build`: passed
- JSON validation: passed
- Removed-name scan under `src/main`: passed, no matches

Manual runtime checklist:

- [ ] Run `./gradlew runClient`.
- [ ] Build boiler, cylinder ring, four piston blocks, and crankshaft in the documented vertical order.
- [ ] Confirm piston blocks switch to assembled section variants.
- [ ] Look at the tank boiler and confirm it switches to Create's active boiler visual state.
- [ ] Confirm the boiler size indicator no longer treats the 3x3x1 boiler as undersized for this engine.
- [ ] Pump water into the active boiler and confirm the water supply indicator rises.
- [ ] Add heat and water to the boiler and confirm the crankshaft/attached shaft turns.
- [ ] Check goggles show assembled status, boiler efficiency, RPM, SU capacity, and flywheel absent.
- [ ] Break one piston and confirm the crankshaft stops and piston states clear.
- [ ] Break one cylinder and confirm the crankshaft stops.
- [ ] Break or remove one boiler tank below the ring and confirm the crankshaft stops within one lazy revalidation interval.
- [ ] Restore the structure and confirm generation resumes.
- [ ] Save and reload the world and confirm the crankshaft revalidates.
