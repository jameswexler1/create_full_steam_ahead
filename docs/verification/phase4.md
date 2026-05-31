# Phase 4 Verification

Date: 2026-05-20

Implemented:

- `crankshaft` now has a `CrankshaftBlockEntity` extending Create `GeneratingKineticBlockEntity`.
- The crankshaft validates the fixed vertical v1 engine shape: four pistons, assembled 3x3x2 cylinder ring, and Create fluid tanks below the ring.
- Valid structures mark all four piston blocks as assembled and assign `piston_section` values.
- Invalid structures clear piston assembly states and stop generated rotation.
- Steam output is gated by the linked Create `FluidTankBlockEntity` boiler and its water supply.
- Fired Blaze Burners are counted directly from the 3x3 footprint under the compact boiler.
- Create's `BoilerData.evaluate()` now counts valid Full Steam Ahead crankshafts as attached steam engines.
- Attached Full Steam Ahead engines make 3x3x1 tank boilers use compact boiler sizing instead of vanilla tank-size scaling.
- Crankshaft output now requires active heat: unfired Blaze Burners/passive heat produce no rotation.
- Crankshaft RPM now follows active burner count exactly: 1-2 burners = 16 RPM, 3-4 = 32 RPM, 5-8 = 48 RPM, 9 = 64 RPM.
- Crankshaft SU now follows exact heat units: each normal fired burner adds 16,384 SU, each Blaze Cake burner adds 32,768 SU, up to 294,912 SU at 9 Blaze Cake burners.
- Assembled piston sections now use distinct placeholder models for visible testing.
- Goggles show crankshaft assembly status, active burners, heat units, water supply, RPM, and SU capacity.

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

Latest automated run after exact burner-tier fix on 2026-05-20: all checks above passed again.

Manual runtime checklist:

- [x] Run `./gradlew runClient`.
- [x] Build boiler, cylinder ring, four piston blocks, and crankshaft in the documented vertical order.
- [x] Confirm piston blocks switch to assembled section variants.
- [x] Look at the tank boiler and confirm it switches to Create's active boiler visual state.
- [x] Pump water into the active boiler and confirm the water supply indicator rises after the latest heat scaling fix.
- [x] Add heat and water to the boiler and confirm the crankshaft/attached shaft turns.
- [x] Confirm unfired/smouldering Blaze Burners do not produce crankshaft rotation.
- [x] Confirm 1 normal fired Blaze Burner produces 16,384 SU at 16 RPM.
- [x] Confirm 2 normal fired Blaze Burners produce 32,768 SU at 16 RPM.
- [x] Confirm 3 normal fired Blaze Burners produce 49,152 SU at 32 RPM.
- [x] Confirm 4 normal fired Blaze Burners produce 65,536 SU at 32 RPM.
- [x] Confirm 5 normal fired Blaze Burners produce 81,920 SU at 48 RPM.
- [x] Confirm 6 normal fired Blaze Burners produce 98,304 SU at 48 RPM.
- [x] Confirm 7 normal fired Blaze Burners produce 114,688 SU at 48 RPM.
- [x] Confirm 8 normal fired Blaze Burners produce 131,072 SU at 48 RPM.
- [x] Confirm 9 normal fired Blaze Burners produce 147,456 SU at 64 RPM.
- [x] Confirm 1 Blaze Cake burner produces 32,768 SU while RPM still follows active burner count.
- [x] Confirm 9 Blaze Cake burners produce 294,912 SU at 64 RPM.
- [x] Check goggles show assembled status, active burners, heat units, water supply, RPM, and SU capacity.
- [ ] Break one piston and confirm the crankshaft stops and piston states clear.
- [ ] Break one cylinder and confirm the crankshaft stops.
- [ ] Break or remove one boiler tank below the ring and confirm the crankshaft stops within one lazy revalidation interval.
- [ ] Restore the structure and confirm generation resumes.
- [ ] Save and reload the world and confirm the crankshaft revalidates.

Manual result reported by Gustavo on 2026-05-20: boiler texture changed, pistons visibly changed when assembled, the crankshaft generated rotation, passive heat rejection worked, the exact 1-9 normal burner output table matched, and individual Blaze Cake SU doubling reached 294,912 SU at 9 burners.
