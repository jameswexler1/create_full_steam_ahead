# Steam Admission Valve Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Verified on 2026-07-14. The valve is currently a visual prototype with unrestricted Create fluid-pipe transport. Wireless admission control is not implemented yet.

The horizontal visual rebuild passed `compileJava`, JSON validation, and `build`. A `runClient` smoke launch reached Full Steam Ahead initialization without load errors, but the slow development client was stopped before resource/model baking completed. The current models still require the in-world checks below.

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
- [ ] Brass collars use a consistent metal texture without a bright casing-border streak across their upper face.
- [ ] The raised iron platform and brass border remain unchanged as pipe neighbours are added or removed.
- [ ] The two frequency positions match Create Redstone Link pads in texture and pixel density.
- [ ] Create Fluid Pipes connect visually, steam remains visible, and steam passes through the prototype.
- [ ] Selection and collision outlines follow the raised controls, brass collars, live pipe arms, and visible endpoint rims in every horizontal rotation.
- [ ] Breaking the valve drops itself and does not leave a stale pipe connection.
