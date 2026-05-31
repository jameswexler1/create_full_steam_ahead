# Phase 2 Remodel Verification

Date: 2026-05-20

Implemented:

- Removed the old controller/firebox/boiler/output/casing/piston-rod block set.
- Added the then-current remodel blocks: Steam Cylinder, Steam Piston, Crankshaft, and two inert placeholders that were removed in a later phase.
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

- [x] Run `./gradlew runClient`.
- [x] Confirm the main menu opens.
- [x] Confirm `Create: Full Steam Ahead` appears in the Mods list.
- [x] Open a creative world.
- [x] Confirm the `Create: Full Steam Ahead` creative tab exists.
- [x] Confirm the then-current Phase 2 block items appeared in the creative tab. This checklist was superseded after placeholder removal.
- [x] Place all five blocks and confirm placement works without crashes.
- [x] Confirm none of the removed old blocks appear.

Manual result reported by Gustavo on 2026-05-20.
