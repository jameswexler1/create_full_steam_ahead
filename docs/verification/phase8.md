# Phase 8 Verification

Date: 2026-05-21

Planned scope:

- Replace placeholder block models with Create-style visual models.
- Add linked-shaft-driven piston animation through Flywheel plus a fallback renderer.
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

- [x] Added technical partial models for piston animation and crank pin rendering.
- [x] Added `CrankshaftAnimation`, Flywheel `CrankshaftVisual`, and fallback `CrankshaftRenderer` for the first animation slice.
- [x] Registered the crankshaft block entity renderer and Flywheel visual from client-only code for the first animation slice.
- [x] Exposed client-safe crankshaft state getters for rendering in the first animation slice.
- [x] Fixed early partial-model initialization that caused a startup crash before the title screen.
- [x] Corrected placeholder inlet/outlet pipe texture references to `create:block/pipes`.
- [x] Converted the crankshaft from four-way horizontal output to one axial horizontal shaft axis.
- [x] Added Create steam jet particles and Create `STEAM` sounds, emitted only on crank phase while the engine is running.
- [x] Added `piston_head` technical block and updated the crankshaft validator for the new moving-column stack.
- [x] Corrected the moving-column stack to `piston_head -> piston -> piston -> crankshaft`.
- [x] Applied the v2 `piston_head` model and matched the block outline/collision shape to its cuboids.
- [x] Applied the textured v3 `piston_head` model from `piston_head_for_testing_v3_textured.bbmodel`; existing v2-derived hitbox still matches the unchanged cuboids.
- [x] Applied the v1 `piston` body model to base and assembled piston section models, with matching 6x16x6 outline/collision shape.
- [x] Replaced piston/head proxy animation with dynamic rendering of the actual `piston` and `piston_head` models.
- [x] Hid assembled static piston/head block models so the moving dynamic visuals do not overlap fixed geometry.

Completed shaft-link remodel slice:

- [x] Removed the custom `crankshaft` block from registration, creative tab, loot, blockstates, item models, mining tags, and lang.
- [x] Added hidden `powered_shaft`, which replaces a player-placed Create shaft when the piston head validates the engine.
- [x] Moved validation and steam output ownership to `PistonHeadBlockEntity`.
- [x] Updated the current stack to `piston_head -> piston -> empty stroke -> Create shaft`.
- [x] Replaced crankshaft renderer/visual/animation with piston-head renderer/visual/animation driven from the linked shaft angle.
- [x] Fixed dynamic piston/head lighting by relighting the head, piston body, and shaft partial at their own world positions.

Completed static cylinder art slice:

- [x] Converted `Steam_Cylinder_all_faces.bbmodel` into a local 16x16 cylinder texture.
- [x] Applied the custom cylinder texture to both unassembled and assembled `steam_cylinder` block models.
- [x] Converted `Assembled_cylinder_ring_prototype.bbmodel` into 16 section-specific assembled cylinder models.
- [x] Added a `section` blockstate property and model-derived slim assembled hitboxes for `steam_cylinder` and `steam_inlet`.
- [x] Regenerated the assembled cylinder ring models and texture from `Steam_Cylinder_all_faces_claude_tuesday_1.bbmodel`, including the new bottom detail and cutout render type.
- [x] Corrected assembled cylinder model UVs from 256px Blockbench coordinates into Minecraft's 0-16 UV range to prevent texture-atlas sampling artifacts.
- [x] Regenerated the assembled cylinder ring models, texture, and model-derived slim hitboxes from `Steam_Cylinder_all_faces_claude_wednesday.bbmodel`.
- [x] Corrected assembled cylinder section UV clipping to follow Minecraft's baked face orientation so split models match the Blockbench source.
- [x] Applied the latest hand-authored `assembled_cylinder_ring.png` runtime texture for the assembled multiblock cylinder ring.
- [x] Replaced the assembled multiblock cylinder runtime texture with the `_v2` hand-authored PNG revision.
- [x] Reused the 16 assembled cylinder section models as progressive `Cylinder Wall` construction visuals.
- [x] Added best-fit partial section assignment for connected cylinder wall/inlet groups while keeping `assembled=true` reserved for complete valid rings.
- [x] Applied `cylinder_exposed_parts_fix.zip`: v3 assembled cylinder atlas plus exposed cut faces for all 16 section models.
- [x] Applied `Stean_cylinder_wall_v1.bbmodel` as the standalone `Cylinder Wall` model and matched the hitbox to its two cuboids.

Completed stepped lever slice:

- [x] Added `stepped_lever` as a face-attached analog redstone control block.
- [x] Ported the stepped lever block entity, goggle tooltip, renderer, handle partial model, block/item models, texture, loot, recipe, mining tags, safe-NBT tag, lang entry, and creative tab entry.

Completed telegraph polish slice:

- [x] Set `engine_telegraph` to `minecraft:cutout_mipped` with ambient occlusion disabled for cleaner moving/simulated contraption rendering.
- [x] Replaced the coarse telegraph hitbox with a model-derived, direction-aware union of its Blockbench cuboids.

Completed inlet/pipe reliability slice:

- [x] Diagnosed stale Create pipe connection state after a `steam_inlet` disassembled and reassembled.
- [x] Exposed/cleared the inlet fluid capability before ring blockstate updates so Create pipe shape recalculation sees the correct handler state.
- [x] Refreshed adjacent Create pipes and their steam pressure cache when inlet capability availability changes without block replacement.

Completed cylinder wall partial visual slice:

- [x] Added a `wall_shape` visual state for standalone, straight-X, and straight-Z cylinder wall chains.
- [x] Kept straight connected wall chains out of ring-section visuals until the component contains both X and Z horizontal adjacency.
- [x] Preserved complete ring assembly by continuing to use section-aware models when a valid 3x3x2 ring is present.

Completed fixed assembled cylinder model slice:

- [x] Regenerated all 16 assembled cylinder section models from `Steam_Cylinder_all_faces_FIXED_V2.bbmodel`.
- [x] Updated `CylinderRingShapes` from the fixed V2 model's 60 cuboids so outlines/collision match the new geometry.
- [x] Reused the embedded assembled ring texture from the fixed model; it matches the current v3 runtime atlas.

Automated results:

- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-21 after technical partials, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-21 after renderer/visual code, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources` passed on 2026-05-21 after technical partials, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-21 after renderer/visual code, the axial crankshaft fix, crank-phase steam effects, and the steam sound correction.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-23 after adding `piston_head`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-23 after adding `piston_head`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources` passed on 2026-05-23 after adding `piston_head`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after adding `piston_head`.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-23 after correcting the piston-head stack and applying the v2 model.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-23 after correcting the piston-head stack and applying the v2 model.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after correcting the piston-head stack and applying the v2 model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after applying the piston body v1 model and hitbox.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-27 after applying the textured v3 piston head model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after replacing proxy piston animation with dynamic actual piston/head partials.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-23 after the shaft-link remodel.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-23 after the shaft-link remodel.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-23 after the shaft-link remodel.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-24 after applying the custom steam cylinder texture.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-24 after applying the custom steam cylinder texture.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-25 after adding section-aware assembled cylinder models and hitboxes.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-26 after applying the Tuesday assembled cylinder texture revision.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-26 after normalizing the assembled cylinder UVs.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-27 after applying the Wednesday assembled cylinder revision and matching hitboxes.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-27 after correcting assembled cylinder section UV orientation.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-28 after applying the latest assembled cylinder ring PNG.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-28 after applying the assembled cylinder ring `_v2` PNG.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-28 after adding progressive cylinder wall subunit visuals.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-28 after applying the exposed cylinder section face fix.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-28 after replacing the standalone `Cylinder Wall` model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-28 after refreshing adjacent pipes on steam inlet capability changes.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-29 after adding straight-wall partial construction visuals.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-29 after regenerating assembled cylinder sections from the fixed Blockbench model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-29 after regenerating assembled cylinder sections from the fixed V2 Blockbench model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-24 after adding `stepped_lever`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-24 after adding `stepped_lever`.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-24 after adding `stepped_lever`.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-24 after setting `stepped_lever` static models to `minecraft:cutout_mipped`.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-24 after polishing `engine_telegraph` rendering and hitbox.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` reached an integrated world on 2026-05-21 after lazy partial registration.

Manual runtime checklist:

- [x] Existing direct compact engines still assemble and run.
- [x] `piston_head` appears in the creative tab and is placeable.
- [x] Previous stack assembled as `piston_head -> piston -> piston -> crankshaft`.
- [ ] New stack assembles as `piston_head -> piston -> empty stroke -> Create shaft`.
- [x] `piston_head` v2 model renders correctly and uses the stepped non-full-block hitbox.
- [x] `piston` v1 body model renders correctly in unassembled placement and uses the narrow 6x16x6 hitbox.
- [ ] `steam_cylinder` renders with the custom cylinder texture in both unassembled placement and assembled ring state.
- [ ] Two or more `Cylinder Wall` blocks in a straight line stay in straight-wall/fence-like mode instead of guessing a ring section.
- [ ] Adding a 90-degree horizontal turn to a connected `Cylinder Wall` group switches that partial structure into ring-section visuals.
- [ ] Assembled `steam_cylinder` ring uses the slim section-aware model in all 16 positions, disassembles back to the standalone block model, and has a slim outline/collision shape.
- [ ] Fixed assembled cylinder ring geometry renders without the previous overlapping/flickering parts.
- [ ] Assembled `piston_head` and the `piston` body remain visible at rest and reciprocate while the linked shaft is running.
- [x] Existing pipe-fed engines still assemble and run.
- [ ] Pipe connected to a `steam_inlet` reconnects automatically after breaking and repairing one cylinder wall block in the ring.
- [x] Old worlds with existing engines load without blockstate/model errors.
- [x] Piston motion was synchronized with crankshaft rotation at 16, 32, 48, and 64 RPM before the shaft-link remodel.
- [ ] Piston motion is synchronized with linked Create shaft rotation at 16, 32, 48, and 64 RPM after the shaft-link remodel.
- [ ] The custom crankshaft item no longer appears in the creative tab, and the top output behaves as a normal Create shaft.
- [x] Piston motion stops when the engine has no steam.
- [x] Steam particles appear only while running and scale reasonably with speed.
- [x] Steam sound matches Create's vanilla steam-engine style, is slightly louder, and only plays while running.
- [ ] `engine_telegraph` renders without bad black/shadow artifacts on a Simulated contraption and has a selection/collision shape that follows the model in all four facings.
- [ ] `stepped_lever` appears in the creative tab, places on floor/wall/ceiling, changes redstone strength 0-15 with normal/shift right-click, animates its handle, and keeps its state after save/reload.
- [x] Resource reload (`F3+T`) keeps partial models and textures intact.
- [ ] Dedicated server starts without client-class loading errors.
- [x] Aeronautics/Sable assembled sublevel still moves and powers propellers with visuals active.
- [ ] Ponder entries appear and teach direct compact and pipe-fed setups.

Manual result reported by Gustavo on 2026-05-21: after the axial crankshaft fix, the engine still worked and the crankshaft output behaved correctly.

Manual result reported by Gustavo on 2026-05-22: crank-phase steam particles worked, and the corrected Create-style steam sound worked perfectly.

Manual result reported by Gustavo on 2026-05-22: remaining in-world Phase 8 runtime checks worked.
