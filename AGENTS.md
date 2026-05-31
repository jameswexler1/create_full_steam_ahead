# Repository Guidelines

## Project Structure & Module Organization

This is a NeoForge 1.21.1 Create addon for `Create: Full Steam Ahead` (`full_steam_ahead`, package `dev.gustavo.fullsteamahead`).

- `src/main/java/dev/gustavo/fullsteamahead/` — Java source. Entrypoint: `FullSteamAhead.java`.
- `src/main/resources/` — mod metadata and assets.
- `PLAN.md` — authoritative design document. Read it before structural work.
- `docs/verification/` — manual verification notes.
- `build/`, `.gradle/`, `runs/` — generated; do not commit.

## Build Commands

```sh
./gradlew compileJava
./gradlew processResources
./gradlew build
./gradlew runClient
```

Use `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ...` when matching the current local workflow.

## Coding Style

Use Java 21 and four-space indentation. Keep packages under `dev.gustavo.fullsteamahead`. Use `FullSteamAhead.MOD_ID` instead of repeating `"full_steam_ahead"`. Classes use `PascalCase`, methods/fields use `camelCase`, constants use `UPPER_SNAKE_CASE`, and resource paths use `lower_snake_case`.

## Current Task — Phase 8 Rendering and Ponder

The old `flywheel` and `governor` placeholder blocks have been removed. Do not re-add mechanics, recipes, requirements, output effects, textures, models, registration, or assets for them unless `PLAN.md` is changed first. This does not apply to the Flywheel rendering library used by client visuals.

Implement visual presentation while preserving all current gameplay:

1. Do not change steam generation, SU/RPM output, multiblock validation, Aeronautics movement rules, recipes, or config in Phase 8.
2. Do not reintroduce the removed `flywheel` or `governor` block code/assets during Phase 8.
3. Keep all Flywheel, renderer, and Ponder code client-only under `dev.gustavo.fullsteamahead.client`.
4. Register piston-head Flywheel visuals and vanilla fallback block entity renderers only from client code.
5. Reuse Create textures and visual language where possible; add local textures only when Create does not provide a suitable part.
6. Static piston blocks should become visual guides/sleeves; dynamic piston motion should be rendered from the crankshaft.
7. Steam particles and chuff sounds must be tied to running state and crank phase, not emitted every tick blindly.
8. Ponder scenes come after the visual models are stable.

Record automated and manual results in `docs/verification/phase8.md`.

## Testing Guidelines

Minimum automated check: `./gradlew build`. For resource changes also run JSON validation:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
```

For gameplay changes, run `./gradlew runClient`, test in a world, and record results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits, such as `feat: add boiler outlet scaffold` or `docs: update steam fluid plan`. Commit each completed landmark. PRs should include a concise summary, test results, and screenshots or clips for visible in-game changes.
