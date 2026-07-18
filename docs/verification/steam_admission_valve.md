# Steam Admission Valve Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test processResources`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `node tools/admission_valve/export_manual_lever_v1_runtime.js`
- [x] Static face-plane audit reports zero coplanar overlaps in both the item model and a four-port runtime composition.

Dual-mode remodel automated verification completed on 2026-07-19. The valve still exposes one final
`0..15` admission value to the unchanged steam allocator. Manual/telegraph state, receiver state,
block-entity-backed mode migration, model parts, collision, tests, and the distributable build pass;
gameplay and simulated-contraption behavior still require the checks below.

## Manual In-World Checks

### Dual-Mode Remodel

- [ ] A newly placed valve shows the tall controller, manual track, and lever at the fully open top position.
- [ ] Right-click steps the manual lever upward; sneak-right-click steps it downward; both stop at `0` and `15`.
- [ ] Admission, engine RPM, and engine SU follow every manual step without affecting the through-main.
- [ ] A normal wrench click swaps the track/lever for two Redstone Link pads and back without rotating the valve or changing its pipe connections.
- [ ] Frequency item miniatures render centered on the two receiver pads in all four valve facings and cannot be selected in manual mode.
- [ ] In receiver mode, empty frequencies remain a full-open bypass and signals `0`, `5`, `10`, and `15` produce the expected admission.
- [ ] Use an untuned Engine Order Telegraph item on an unlinked manual valve, then place the tuned telegraph; both adopt one channel and matching position.
- [ ] Moving the telegraph moves the built-in lever and engine admission; moving the built-in lever moves and rings the telegraph.
- [ ] A second loaded telegraph or valve on the channel synchronizes without duplicate feedback; an unloaded chunk is not force-loaded.
- [ ] Sneak-use a telegraph item on the valve to clear the valve channel without clearing the item.
- [ ] Save/reload in both modes and confirm mode, manual step, telegraph channel, frequencies, and receiver signal persist.
- [ ] Existing pre-remodel valves load in Redstone Link mode; newly broken/replaced valves start manual and fully open.
- [ ] The model, moving lever, receiver pads, item miniatures, and extended hitbox align in north, south, east, and west facings.
- [ ] Assemble/disassemble a Sable simulated contraption in both modes and confirm controls, channel, topology, and throttle recover.
- [ ] Repaint the provisional admission-valve texture after functional validation without changing the UV islands or model groups.

### Original Throttle Regression

- [ ] The Steam Admission Valve appears in the Full Steam Ahead creative tab and has a correctly scaled inventory model.
- [ ] With exactly one adjacent Steam Inlet, the brass controlled branch points toward that inlet.
- [ ] With no neighbours, the valve renders as a horizontal straight Create Fluid Pipe with endpoint rims.
- [ ] Straight, elbow, tee, and four-way horizontal layouts render every real connection without holes or detached arms.
- [ ] No face flickers or changes texture while the camera moves around a straight, tee, or four-way valve.
- [ ] North/south and east/west straight valves rotate the complete platform by 90 degrees with no support or actuator cubes protruding beneath it.
- [ ] The valve never assumes a vertical axis; pipes above or below do not create a vertical valve arm or carry steam through that face.
- [ ] Endpoint rims appear only at open/non-pipe endpoints and disappear cleanly between compatible pipes.
- [ ] Every live horizontal arm has a clean brass collar with no gap against the copper pipe body.
- [ ] One-, two-, three-, and four-port layouts show every outer and lateral collar face without transparent sections.
- [ ] Brass collars use a consistent metal texture without a bright casing-border streak across their upper face.
- [ ] The raised iron platform and brass border remain unchanged as pipe neighbours are added or removed.
- [ ] The two frequency positions match Create Redstone Link pads in texture and pixel density.
- [ ] Right-click both frequency pads with items; confirm the items render in the correct pads in north/south and east/west orientations.
- [ ] With both frequencies empty, confirm the valve reports `Bypass (100%)` and a linked engine receives normal full admission.
- [ ] Configure both frequencies without a matching transmitter; confirm the resulting signal `0` fully closes admission to the controlled engine.
- [ ] Transmit analogue strengths `0`, `5`, `10`, and `15`; confirm the valve reports approximately `0%`, `33%`, `67%`, and `100%` admission and requested flow scales accordingly.
- [ ] Confirm delivered SU and RPM both scale continuously with admitted steam.
- [ ] Starve two differently commanded valves on one network; confirm both keep the same proportional allocation ratio instead of one engine taking priority.
- [ ] Confirm a through-branch main remains open and continues powering downstream engines when the local valve receives signal `0` and fully closes its engine branch.
- [ ] Place two active inlets beside one valve, or turn the inlet nozzle away; confirm the valve reports no uniquely linked active inlet and controls neither.
- [ ] Confirm an optional passive symmetry inlet is ignored when selecting the controlled active inlet.
- [ ] Confirm Create Fluid Pipes connect visually, steam remains visible, and steam passes through every non-controlled branch.
- [ ] Change the received signal and confirm it plays one restrained Create-style adjustment sound without rendering any detached geometry above the platform.
- [ ] Inspect with goggles and confirm linked state, command, percentage, state, pressure, requested flow, and delivered flow update live.
- [ ] Save/reload and break/replace neighbouring pipes; confirm frequencies and command recover without a stale inlet association.
- [ ] Assemble and disassemble a simulated contraption; confirm both frequencies, receiver operation, displayed command, and inlet association survive.
- [ ] Confirm direct compact engines and pipe-fed engines without an admission valve retain their existing output.
- [ ] Selection and collision outlines follow the raised controls, brass collars, live pipe arms, and visible endpoint rims in every horizontal rotation.
- [ ] Breaking the valve drops itself and does not leave a stale pipe connection.
