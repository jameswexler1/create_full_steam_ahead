# Phase 2 Remodel Verification

Date: 2026-05-20

Implemented:

- Removed the old controller/firebox/boiler/output/casing/piston-rod block set.
- Added the five remodel blocks: Steam Cylinder, Steam Piston, Crankshaft, Flywheel, Governor.
- Updated block registration, item registration, creative tab ordering, lang, mining tags, blockstates, models, and self-drop loot tables.
- Added a `ModBlockEntities` deferred-register stub with no block entity types yet.

Compatibility note:

- Create `6.0.10-280` does not include `com.simibubi.create.foundation.block.SmartBlock`. The non-kinetic inert stubs use vanilla `Block` for Phase 2; the crankshaft uses Create `KineticBlock`.

Automated checks run:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew processResources
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
rg "large_steam_engine_controller|firebox|large_engine_casing|boiler_drum|output_coupling|piston_rod" src/main
```

Results:

- `compileJava`: passed
- `processResources`: passed
- `build`: passed
- Removed-name scan under `src/main`: passed, no matches

Manual runtime checklist:

- [ ] Run `./gradlew runClient`.
- [ ] Confirm the main menu opens.
- [ ] Confirm `Create: Full Steam Ahead` appears in the Mods list.
- [ ] Open a creative world.
- [ ] Confirm the `Create: Full Steam Ahead` creative tab exists.
- [ ] Confirm exactly five block items appear in order: Steam Cylinder, Steam Piston, Crankshaft, Flywheel, Governor.
- [ ] Place all five blocks and confirm placement works without crashes.
- [ ] Confirm none of the removed old blocks appear.
