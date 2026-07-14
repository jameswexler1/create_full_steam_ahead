# Steam Admission Valve Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Verified on 2026-07-14. The valve is currently a visual prototype with unrestricted Create fluid-pipe transport. Wireless admission control is not implemented yet.

The native-pipe visual rebuild passed `compileJava`, JSON validation, and `build`. `runClient` completed resource/model baking and reached the title screen with no Full Steam Ahead missing-model or missing-texture warnings; it was then stopped intentionally.

## Manual In-World Checks

- [ ] The Steam Admission Valve appears in the Full Steam Ahead creative tab and has a correctly scaled inventory model.
- [ ] With exactly one adjacent Steam Inlet, the brass controlled branch points toward that inlet.
- [ ] With no neighbours, the valve renders as a normal straight Create Fluid Pipe with endpoint rims.
- [ ] Straight, elbow, tee, and four-way horizontal layouts render every real connection without holes or detached arms.
- [ ] Up/down pipe neighbours render matching vertical arms without changing or suppressing horizontal connections.
- [ ] Endpoint rims appear only at open/non-pipe endpoints and disappear cleanly between compatible pipes.
- [ ] The brass saddle remains compact and its texture density matches nearby Create blocks.
- [ ] The two frequency positions look like raised Create Redstone Link pads rather than deep sockets.
- [ ] Create Fluid Pipes connect visually, steam remains visible, and steam passes through the prototype.
- [ ] Selection and collision outlines follow the compact controls, live pipe arms, and visible endpoint rims in every horizontal rotation.
- [ ] Breaking the valve drops itself and does not leave a stale pipe connection.
