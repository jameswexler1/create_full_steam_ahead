# Engine Telegraph Verification

Date: 2026-05-24

Scope:

- Keep `engine_telegraph` inert for now.
- Register it as a normal placeable block item.
- Preserve directional placement, model resources, loot, lang, and mining tags.

Automated results:

- [x] `find src/main/resources -name '*.json' -exec jq empty {} +` passed on 2026-05-24.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` passed on 2026-05-24.
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` passed on 2026-05-24.

Manual checklist:

- [ ] Confirm Engine Order Telegraph appears in the creative tab.
- [ ] Place it facing north, south, east, and west.
- [ ] Confirm the model rotates with placement direction.
- [ ] Confirm the outline/collision shape covers the tall model well enough for normal interaction.
- [ ] Break it with a pickaxe and confirm it drops itself.
