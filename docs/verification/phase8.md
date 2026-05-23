# Phase 8 Verification

Date: 2026-05-21

Planned scope:

- Replace placeholder block models with Create-style visual models.
- Add crankshaft-driven piston animation through Flywheel plus a fallback renderer.
- Add running steam particles and Create-style steam-engine sound.
- Add Ponder scenes after visuals are stable.
- Preserve all Phase 7 mechanics and Aeronautics compatibility.
- Exclude `flywheel` from this phase; leave its placeholder code and assets untouched.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Completed first slice:

- [x] Excluded `flywheel` from Phase 8 code and asset work.
- [x] Added client-only event bootstrap under `dev.gustavo.fullsteamahead.client`.
- [x] Replaced active engine cube placeholders with Create-style multipart JSON models.
- [x] Updated static piston models to read more like guide/sleeve blocks.
- [x] Replaced flickering multipart placeholders with stable non-overlapping proxy models.
- [x] Added `docs/blockbench_phase8.md` for final art handoff.

Completed animation proxy slice:

- [x] Added proxy partial models for piston rod, piston head, and crank pin.
- [x] Added `CrankshaftAnimation`, Flywheel `CrankshaftVisual`, and fallback `CrankshaftRenderer`.
- [x] Registered the crankshaft block entity renderer and Flywheel visual from client-only code.
- [x] Exposed client-safe crankshaft state getters for rendering.
- [x] Fixed early partial-model initialization that caused a startup crash before the title screen.
- [x] Corrected placeholder inlet/outlet pipe texture references to `create:block/pipes`.
- [x] Converted the crankshaft from four-way horizontal output to one axial horizontal shaft axis.
- [x] Added Create steam jet particles and Create `STEAM` sounds, emitted only on crank phase while the engine is running.
- [x] Added `piston_head` technical block and updated the crankshaft validator for the new moving-column stack.
- [x] Corrected the moving-column stack to `piston_head -> piston -> piston -> crankshaft`.
- [x] Applied the v2 `piston_head` model and matched the block outline/collision shape to its cuboids.
- [x] Applied the v1 `piston` body model to base and assembled piston section models, with matching 6x16x6 outline/collision shape.

Automated results:

- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-21 after proxy partials, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-21 after renderer/visual code, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources` passed on 2026-05-21 after proxy partials, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-21 after renderer/visual code, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-23 after adding `piston_head`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-23 after adding `piston_head`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources` passed on 2026-05-23 after adding `piston_head`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after adding `piston_head`.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-23 after correcting the piston-head stack and applying the v2 model.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-23 after correcting the piston-head stack and applying the v2 model.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after correcting the piston-head stack and applying the v2 model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after applying the piston body v1 model and hitbox.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` reached an integrated world on 2026-05-21 after lazy partial registration.

Manual runtime checklist:

- [x] Existing direct compact engines still assemble and run.
- [ ] `piston_head` appears in the creative tab and is placeable.
- [ ] New stack assembles as `piston_head -> piston -> piston -> crankshaft`.
- [ ] `piston_head` v2 model renders correctly and uses the stepped non-full-block hitbox.
- [ ] `piston` v1 body model renders correctly in unassembled and assembled states and uses the narrow 6x16x6 hitbox.
- [x] Existing pipe-fed engines still assemble and run.
- [x] Old worlds with existing engines load without blockstate/model errors.
- [x] Piston motion is synchronized with crankshaft rotation at 16, 32, 48, and 64 RPM.
- [x] Crankshaft only connects and transfers rotation through the two opposite faces on its selected axis.
- [x] Piston motion stops when the engine has no steam.
- [x] Steam particles appear only while running and scale reasonably with speed.
- [x] Steam sound matches Create's vanilla steam-engine style, is slightly louder, and only plays while running.
- [x] Resource reload (`F3+T`) keeps partial models and textures intact.
- [ ] Dedicated server starts without client-class loading errors.
- [x] Aeronautics/Sable assembled sublevel still moves and powers propellers with visuals active.
- [ ] Ponder entries appear and teach direct compact and pipe-fed setups.

Manual result reported by Gustavo on 2026-05-21: after the axial crankshaft fix, the engine still worked and the crankshaft output behaved correctly.

Manual result reported by Gustavo on 2026-05-22: crank-phase steam particles worked, and the corrected Create-style steam sound worked perfectly.

Manual result reported by Gustavo on 2026-05-22: remaining in-world Phase 8 runtime checks worked.
