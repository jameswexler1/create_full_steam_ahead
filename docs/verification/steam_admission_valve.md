# Steam Admission Valve Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] Static face-plane audit reports zero coplanar overlaps in both the item model and a four-port runtime composition.

Verified on 2026-07-14. The valve uses Create's native Redstone Link receiver behaviour, applies its analogue command only to the uniquely linked active Steam Inlet, and leaves the shared pipe main unrestricted. Automated checks cover compilation and resources; gameplay and simulated-contraption behaviour still require the in-world checks below.

## Manual In-World Checks

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
- [ ] Configure both frequencies without a matching transmitter; confirm the valve closes and only its linked engine loses admission.
- [ ] Transmit analogue strengths `5`, `10`, and `15`; confirm the valve reports approximately `33%`, `67%`, and `100%` and requested flow scales accordingly.
- [ ] Confirm delivered SU scales continuously with admitted steam while RPM remains on the existing `16`, `32`, `48`, and `64` tiers.
- [ ] Starve two differently commanded valves on one network; confirm both keep the same proportional allocation ratio instead of one engine taking priority.
- [ ] Confirm a through-branch main remains open and continues powering downstream engines when the local valve command is `0`.
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
