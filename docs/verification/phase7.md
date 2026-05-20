# Phase 7 Verification

Date: 2026-05-20

Planned scope:

- Keep Aeronautics, Simulated, and Sable optional.
- Register Create movement checks for Full Steam Ahead engine blocks.
- Register Simulated movement checks through reflection when the Simulated API is loaded.
- Preserve important Full Steam Ahead block entity NBT through Create contraption movement.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Automated results:

- `compileJava`: pending
- `processResources`: pending
- `build`: pending
- JSON validation: pending

Manual runtime checklist:

- [ ] Run without Aeronautics, Simulated, or Sable installed and confirm the game still opens.
- [ ] Confirm direct compact and pipe-fed engines still assemble and run normally.
- [ ] In an Aeronautics/Sable test profile, assemble a ship/sublevel containing a Full Steam Ahead engine.
- [ ] Confirm cylinder, inlet, piston, crankshaft, boiler outlet, and attached boiler/pipe connections move together.
- [ ] Confirm engine NBT/state survives assembly, disassembly, world reload, and sublevel reload.
- [ ] Confirm Create shafts connected to the crankshaft can power Aeronautics propellers while assembled on the sublevel.
