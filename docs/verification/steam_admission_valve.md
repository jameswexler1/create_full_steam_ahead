# Steam Admission Valve Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Verified on 2026-07-14. The valve is currently a visual prototype with unrestricted Create fluid-pipe transport. Wireless admission control is not implemented yet.

## Manual In-World Checks

- [ ] The Steam Admission Valve appears in the Full Steam Ahead creative tab and has a correctly scaled inventory model.
- [ ] With exactly one adjacent Steam Inlet, the brass controlled branch points toward that inlet.
- [ ] A pipe opposite the inlet selects the terminal-straight model.
- [ ] A pipe on either lateral side selects the matching elbow model.
- [ ] Pipes on both lateral sides select the through-branch model.
- [ ] Unsupported or ambiguous pipe layouts use the neutral model without crashing or inventing a connection.
- [ ] Create Fluid Pipes connect visually, steam remains visible, and steam passes through the prototype.
- [ ] Selection and collision outlines follow the visible body and pipe arms in every horizontal rotation.
- [ ] Breaking the valve drops itself and does not leave a stale pipe connection.
