# Phase 8 Verification

Date: 2026-05-21

Planned scope:

- Replace placeholder block models with Create-style visual models.
- Add crankshaft-driven piston animation through Flywheel plus a fallback renderer.
- Add running steam particles and rhythmic chuff sound.
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

Automated results:

- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-21 after proxy partials and the axial crankshaft fix.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-21 after renderer/visual code and the axial crankshaft fix.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources` passed on 2026-05-21 after proxy partials and the axial crankshaft fix.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-21 after renderer/visual code and the axial crankshaft fix.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` reached an integrated world on 2026-05-21 after lazy partial registration.

Manual runtime checklist:

- [ ] Existing direct compact engines still assemble and run.
- [ ] Existing pipe-fed engines still assemble and run.
- [ ] Old worlds with existing engines load without blockstate/model errors.
- [ ] Piston motion is synchronized with crankshaft rotation at 16, 32, 48, and 64 RPM.
- [ ] Crankshaft only connects and transfers rotation through the two opposite faces on its selected axis.
- [ ] Piston motion stops when the engine has no steam.
- [ ] Steam particles appear only while running and scale reasonably with speed.
- [ ] Chuff sound is audible but not spammy or overlapping harshly.
- [ ] Resource reload (`F3+T`) keeps partial models and textures intact.
- [ ] Dedicated server starts without client-class loading errors.
- [ ] Aeronautics/Sable assembled sublevel still moves and powers propellers with visuals active.
- [ ] Ponder entries appear and teach direct compact and pipe-fed setups.
