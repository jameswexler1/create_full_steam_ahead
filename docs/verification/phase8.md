# Phase 8 Verification

Date: 2026-05-21

Planned scope:

- Replace placeholder block models with Create-style visual models.
- Add linked-shaft-driven piston animation through Flywheel plus a fallback renderer.
- Add running steam particles and Create-style steam-engine sound.
- Add Ponder scenes after visuals are stable.
- Preserve all Phase 7 mechanics and Aeronautics compatibility.
- Remove the old inert `flywheel` and `governor` placeholder blocks.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Completed first slice:

- [x] Removed `flywheel` and `governor` block registration, creative entries, source files, blockstates, models, item models, loot tables, mining tags, and lang entries.
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
- [x] Replaced the `piston_head` model and embedded texture from `Steam_Piston_Head.bbmodel`; matched outline/collision to the new crown/step/collar/rod cuboids.
- [x] Applied the v1 `piston` body model to base and assembled piston section models, with matching 6x16x6 outline/collision shape.
- [x] Replaced the piston body with `Steam_Piston_Body.bbmodel`; matched its block outline/collision to the rod, fork cheeks, and wrist pins.
- [x] Added dynamic `Steam_Connecting_Rod` and `Steam_Crank` partials and drove them from the linked shaft phase with slider-crank geometry.
- [x] Corrected `Steam_Crank` and `Steam_Connecting_Rod` phase from the modeled geometry: the crank's authored pin starts below the shaft, then rotates from the raw linked shaft angle.
- [x] Corrected the crank/linkage rotation plane after video comparison: the connector now uses the same frontal-plane rotation style as Create instead of lateral shaft-axis spin.
- [x] Fixed the transform order for the modeled linkage: shaft-frame yaw is now applied before local crank/rod rotation in the Flywheel visual and fallback renderer, so an X-axis shaft throws front/back instead of along the shaft.
- [x] Added alternating 180-degree animation phase offsets for adjacent engines along the same shaft axis.
- [x] Replaced the piston body, connecting rod, and crank with the `new_models/` Blockbench set, including extracted local textures, the model-derived piston hitbox, and local-X linkage transforms.
- [x] Replaced the powered shaft's full-shaft Flywheel visual with Create's caps-only `POWERED_SHAFT` partial; fallback rendering already uses the powered-shaft block model.
- [x] Rotated the piston model texture mapping 90 degrees at the asset level so the static block, item parent, and animated partial share the same orientation.
- [x] Replaced the boiler outlet placeholder with `Steam_outlet.bbmodel`, extracted its 64x64 texture, updated directional blockstate rotations, and matched outline/collision to the modeled cuboids.
- [x] Replaced the piston head model and embedded texture with `piston_head_LATEST.bbmodel`; the existing cuboid-derived hitbox still matches the latest geometry.
- [x] Corrected Blockbench UV conversion for the 64x64 boiler outlet texture and 32x32 piston head texture so Minecraft model UVs stay inside the required 0-16 range.
- [x] Removed the old inert `flywheel` and `governor` placeholder blocks entirely from active source/resources.
- [x] Added piston horizontal-axis blockstate and rotated the dynamic piston body partial so its bolt faces align with X-axis and Z-axis shafts.
- [x] Corrected the piston bolt-face interpretation so the actual bolt-textured faces align with the linked shaft axis.
- [x] Replaced piston/head proxy animation with dynamic rendering of the actual `piston` and `piston_head` models.
- [x] Hid assembled static piston/head block models so the moving dynamic visuals do not overlap fixed geometry.

Completed shaft-link remodel slice:

- [x] Removed the custom `crankshaft` block from registration, creative tab, loot, blockstates, item models, mining tags, and lang.
- [x] Added hidden `powered_shaft`, which replaces a player-placed Create shaft when the piston head validates the engine.
- [x] Added Create-style shaft placement helper for the piston body: holding a Create shaft previews the required top-link shaft position and right-click places it there.
- [x] Allowed the mechanically complete piston/head/ring/shaft structure to assemble its linkage with no steam source; no source still means zero generated RPM/SU.
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
- [x] Replaced best-fit partial section guessing with local-corner partial section assignment for connected cylinder wall/inlet groups while keeping `assembled=true` reserved for complete valid rings.
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
- [x] Regenerated all 16 assembled cylinder section models and the assembled-ring icon model from `Steam_Cylinder_all_faces_manually_painted_monday.bbmodel`.
- [x] Replaced the assembled cylinder ring runtime atlas with the manually painted embedded 256x256 texture; cuboid bounds match the existing fixed V2 hitboxes, so `CylinderRingShapes` did not need changes.
- [x] Applied the revised embedded texture from `Steam_Cylinder_all_faces_manually_painted_monday.bbmodel`; geometry, UV layout, and hitboxes still match the implemented section models.

Completed piston body paintability slice:

- [x] Diagnosed the piston body's missing bottom texture as a `down` face with `texture: null` and zero UV area in the source Blockbench model.
- [x] Added a dedicated paintable bottom-face UV island to `steam_engine_piston_MINE_PERFECT.bbmodel` and the runtime `piston.json` model.
- [x] Replaced the runtime `piston_body.png` with the repainted embedded texture from `steam_engine_piston_MINE_PERFECT.bbmodel`.

Completed steam inlet model slice:

- [x] Converted `Steam_Inlet.bbmodel` into the runtime `steam_inlet` block model and embedded texture.
- [x] Replaced the assembled inlet placeholder with the same textured model, rotated from the cylinder section.
- [x] Matched standalone and assembled inlet outlines/collision to the model's directional cuboids.

Completed adjacent cylinder reliability slice:

- [x] Diagnosed adjacent full cylinder rings deforming each other as a connectivity refresh scope issue.
- [x] Expanded refresh candidates with existing assembled ring origins before clearing partial states.
- [x] Preserved neighboring valid rings while still clearing invalid/stale rings when their own structure is broken.

Completed shared-wall cylinder bank slice:

- [x] Added `shared_wall` as a cylinder-only blockstate for shared X/Z strip visuals.
- [x] Reworked cylinder connectivity to resolve all valid rings before writing final blockstates, allowing one cylinder wall to belong to two adjacent same-orientation rings.
- [x] Blocked unsupported overlaps, grids, T-junctions, and shared steam inlets.
- [x] Stored secondary ring origins on shared cylinder block entities and made engine validation check ring membership explicitly.
- [x] Generated shared-wall runtime texture, split block models, and model-derived hitboxes from `Steam_Cylinder_SHARED_WALL.bbmodel`.
- [x] Preserved legal partial shared-wall pairs as a paired selection during construction, so adding another nearby corner does not steal one side of the shared strip.
- [x] Recovered implied partial ring origins from `section`/`shared_wall` blockstates and allowed already-selected partial rings to accept another shared neighbor, fixing inline-bank construction where the middle ring shares on both sides.
- [x] Seeded partial ring candidates from existing shared-wall blockstates, so upper-layer shared corners keep both ring origins when a later refresh cannot infer the pair from the new block alone.
- [x] Split mechanical ring ownership from visual shared-wall ownership so a completed cylinder can keep rendering a shared strip while the adjacent cylinder is still only partially built.
- [x] Constrained completed/partial shared-wall visual selection to the existing shared-wall axis and canonical shared model side, preventing shared corner segments from rotating to a competing inferred corner.

Completed steam inlet partial-visual reliability slice:

- [x] Diagnosed side-by-side `steam_inlet` blocks entering partial ring-section visuals through the generic cylinder-wall visual inference path.
- [x] Kept complete rings valid with exactly one inlet, but blocked partial section and straight-wall visual inference for connected components containing multiple inlets.

Completed experimental Ponder scene adjustment:

- [x] Rotated the testing Ponder camera toward the Steam Inlet side of the engine.
- [x] Reworked the reveal order so the piston head, piston body, cylinder walls, and Steam Inlet assemble first, followed by the adjacent boiler, then the Boiler Outlet and connecting pipes.
- [x] Rewrote the testing Ponder text to explain the pipe-fed engine flow more clearly.
- [x] Replaced the animated camera rotation with an initial camera transform aimed at the Steam Inlet side, and hid/restored the outlet and pipe blocks so they only render during their reveal step.
- [x] Reworked the testing Ponder to use Create's `CreateSceneBuilder`, clear staged machine blocks before showing the base plate, restore/reveal components as independent sections, add a scripted checkered floor, remove stray schematic item entities, and localize all testing Ponder text keys.

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
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-29 after replacing the piston head with `Steam_Piston_Head.bbmodel`.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-30 after adding the modeled piston body, connecting rod, and crank animation.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-30 after correcting the modeled crank/linkage rotation phase.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-30 after correcting the crank/linkage rotation plane from the comparison videos.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-30 after correcting the modeled linkage transform order for X-axis shafts.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-30 after replacing piston, connecting rod, and crank visuals from `new_models/`.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `./gradlew build` passed on 2026-05-31 after correcting powered-shaft end-cap rendering and rotating the piston texture mapping at the asset level.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `./gradlew build` passed on 2026-05-31 after replacing boiler outlet and piston head Blockbench models.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `./gradlew build` passed on 2026-05-31 after correcting scaled UVs for the boiler outlet and piston head textures.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `./gradlew build` passed on 2026-05-31 after removing the old `flywheel` and `governor` block registrations/assets.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `./gradlew build` passed on 2026-05-31 after adding piston axis state and shaft-aligned dynamic piston rotation.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `./gradlew build` passed on 2026-05-31 after correcting the piston bolt-face axis mapping.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-31 after making unpowered engine linkages follow a rotating linked shaft passively.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-31 after adding scaled inventory/hand display transforms for oversized block items.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-31 after retuning oversized item displays to Create-style block item proportions.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-31 after switching the creative tab icon to a hidden assembled cylinder ring item model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-31 after enlarging the creative tab icon, adding piston-body shaft ghost placement, and allowing source-less mechanical linkage assembly.
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
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-01 after regenerating assembled cylinder sections from the manually painted Monday Blockbench model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-29 after replacing the steam inlet placeholder model.
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-29 after protecting adjacent assembled cylinder rings during connectivity refresh.
- [x] `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-01 after blocking multi-inlet partial ring visuals.
- [x] `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after applying the revised manually painted cylinder ring texture.
- [x] `jq empty new_models/steam_engine_piston_MINE_PERFECT.bbmodel`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after adding the piston body bottom-face UV island.
- [x] `jq empty new_models/steam_engine_piston_MINE_PERFECT.bbmodel`, embedded/runtime texture hash comparison, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after importing the repainted piston body texture.
- [x] `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after adjusting the experimental testing Ponder camera, reveal order, and text.
- [x] `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +` and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after removing the animated testing Ponder camera turn and staging the outlet/pipe reveal explicitly.
- [x] `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after rebuilding the testing Ponder staging around Create-style independent section reveals, a checkered base plate, and localized text.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after adding shared-wall cylinder bank support.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after fixing upside-down shaft placement axis selection and shaft-line revalidation.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after replacing guessed partial cylinder-ring visuals with local construction topology.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after restoring existing assembled/shared partial models with stable local-corner section assignment.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after hardening partial ring candidate selection and adding exposed top faces to lower shared-wall partials.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-02 after preserving partial shared-wall pairs when extra nearby corners are placed.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-03 after preserving inline partial shared-wall banks when extra corners make the middle ring share on both sides.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-03 after seeding partial candidates from existing upper-layer shared-wall blockstates.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-03 after keeping shared-wall visuals between one complete ring and one incomplete adjacent ring.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`, `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`, `git diff --check`, and `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-06-03 after locking shared corner visuals to the existing axis and canonical model side.
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
- [ ] `Steam_Piston_Head` replacement model renders correctly as an item, standalone block, and moving animated partial with the updated hitbox.
- [x] `piston` v1 body model renders correctly in unassembled placement and uses the narrow 6x16x6 hitbox.
- [ ] `Steam_Piston_Body` replacement model renders correctly as an item, standalone block, and moving animated partial with the updated fork/wrist-pin hitbox.
- [ ] The modeled connecting rod stays attached between the piston wrist pin and crank pin through a full rotation.
- [ ] The modeled crank rotates around the linked Create shaft axis for both X-axis and Z-axis shaft placements.
- [ ] `steam_cylinder` renders with the custom cylinder texture in both unassembled placement and assembled ring state.
- [ ] Two or more `Cylinder Wall` blocks in a straight line stay in straight-wall/fence-like mode instead of guessing a ring section.
- [ ] Adding a 90-degree horizontal turn to a connected `Cylinder Wall` group switches the implied blocks into stable existing assembled partials, not a jumping guessed ring.
- [ ] Early shared-wall patterns use the existing shared-wall partial models with correct orientation, not temporary construction connector models.
- [ ] Straight-only `Cylinder Wall` runs remain in `section=none` fence-like wall mode until a corner implies a ring section.
- [ ] Assembled `steam_cylinder` ring uses the slim section-aware model in all 16 positions, disassembles back to the standalone block model, and has a slim outline/collision shape.
- [ ] Fixed assembled cylinder ring geometry renders without the previous overlapping/flickering parts.
- [ ] `steam_inlet` renders with the new textured model as an item, standalone block, and assembled ring member in north/east/south/west-facing shell positions.
- [ ] Oversized block items render at usable miniature scale in inventory, hotbar, first-person hand, third-person hand, dropped item form, and item frames.
- [ ] The `Create: Full Steam Ahead` creative tab icon renders as the assembled cylinder ring and the icon-only item does not appear in the tab item list.
- [ ] The creative tab icon now reads slightly larger and visually matches neighboring Create-style tab icons.
- [ ] Holding a Create shaft while looking at the completed piston body previews a ghost shaft at the required top-link position.
- [ ] Right-clicking the piston body with that Create shaft places the shaft at the top-link position, converts it to the hidden powered shaft, and forms the rod/crank linkage even with no boiler or steam inlet.
- [ ] Upside-down pipe-fed engines accept both X-axis and Z-axis horizontal shafts from the piston-body shaft placement helper.
- [ ] Upside-down pipe-fed engines claim a shaft brought in through an existing shaft line without requiring the piston-body shaft placement helper.
- [ ] Wrench-rotating the linked shaft near an upside-down engine revalidates the engine without breaking and replacing the shaft.
- [ ] Two complete cylinder rings can be built directly adjacent without either ring deforming or stealing the other's section assignments.
- [ ] Two adjacent engines along X assemble with the shared-wall visual and both engines validate independently.
- [ ] Two adjacent engines along Z assemble with the rotated shared-wall visual and both engines validate independently.
- [ ] Three inline engines form a continuous shared-wall bank without invalidating the middle engine.
- [ ] Placing a `steam_inlet` on a would-be shared wall prevents shared-wall assembly instead of making the inlet belong to two rings.
- [ ] Breaking one shared wall disassembles both affected engines; breaking one outer wall disassembles only that engine.
- [ ] Assembled `piston_head` and the `piston` body remain visible at rest and reciprocate while the linked shaft is running.
- [ ] An assembled engine with no steam output still animates passively when its linked shaft is rotated by another engine on the same shaft network.
- [x] Existing pipe-fed engines still assemble and run.
- [ ] Pipe connected to a `steam_inlet` reconnects automatically after breaking and repairing one cylinder wall block in the ring.
- [x] Old worlds with existing engines load without blockstate/model errors.
- [x] Piston motion was synchronized with crankshaft rotation at 16, 32, 48, and 64 RPM before the shaft-link remodel.
- [ ] Piston motion is synchronized with linked Create shaft rotation at 16, 32, 48, and 64 RPM after the shaft-link remodel.
- [ ] The custom crankshaft item no longer appears in the creative tab, and the top output behaves as a normal Create shaft.
- [x] Piston motion stops when the engine has no steam and the linked shaft is not being driven by another source.
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
