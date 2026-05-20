# Phase 6 Verification

Date: 2026-05-20

Planned scope:

- Add `steam_inlet` as a block entity that occupies one cylinder shell slot.
- Allow assembled rings with either 16 cylinder blocks or 15 cylinder blocks plus 1 inlet.
- Accept only `steam` into the inlet while the ring is assembled.
- Let the crankshaft consume inlet steam for pipe-fed output while preserving direct compact mode.
- Keep multiple inlets invalid for v1.

Automated checks to run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
find src/main/resources -name '*.json' -exec jq empty {} +
```

Results:

- `compileJava`: pending
- `processResources`: pending
- `build`: pending
- JSON validation: pending

Manual runtime checklist:

- [ ] `Steam Inlet` appears in the creative tab.
- [ ] A 15 cylinder + 1 inlet ring assembles visually with the inlet in any shell slot.
- [ ] More than one inlet prevents assembly.
- [ ] The inlet accepts steam from Create pipes only when assembled.
- [ ] Remote boiler outlet -> pipes -> inlet runs the crankshaft without a direct boiler below.
- [ ] Stored steam in the inlet buffer runs the engine briefly after boiler output stops.
- [ ] Direct compact boiler mode still works without an inlet.
