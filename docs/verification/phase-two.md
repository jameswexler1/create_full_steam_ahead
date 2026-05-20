# Phase Two Verification

Date: 2026-05-19

This file records the original nine-block Phase 2 verification. That design has been superseded by the Phase 2 remodel.

Current verification is tracked in `docs/verification/phase2_remodel.md`.

Superseded implementation:

- Block/item/creative tab registry infrastructure.
- Nine inert engine blocks.
- Manual blockstates, item models, block models, lang entries, mining tags, and self-drop loot tables.

Automated checks run:

```sh
find src/main/resources -name '*.json' -print0 | xargs -0 -n1 jq empty
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
```

Results:

- JSON validation: passed
- `compileJava`: passed
- `processResources`: passed
- `build`: passed

Manual runtime checklist:

- [x] Run `./gradlew runClient`.
- [x] Confirm the main menu opens.
- [x] Confirm `Create: Full Steam Ahead` appears in the Mods list.
- [x] Open a creative world.
- [x] Confirm the `Create: Full Steam Ahead` creative tab exists.
- [x] Confirm all nine block items appear in order.
- [x] Place every block and confirm placement works.
- [x] Note placeholder visual quality is acceptable for phase two.
- [ ] Place facing and axis blocks in multiple orientations.
