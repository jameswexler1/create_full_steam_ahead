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

Flywheel/governor blocks are parked inert placeholders. Do not add mechanics, recipes, requirements, or output effects for them unless `PLAN.md` is changed first.

Implement visual presentation while preserving all current gameplay:

1. Do not change steam generation, SU/RPM output, multiblock validation, Aeronautics movement rules, recipes, or config in Phase 8.
2. Keep all Flywheel, renderer, and Ponder code client-only under `dev.gustavo.fullsteamahead.client`.
3. Register the crankshaft Flywheel visual and a vanilla fallback block entity renderer.
4. Reuse Create textures and visual language where possible; add local textures only when Create does not provide a suitable part.
5. Static piston blocks should become visual guides/sleeves; dynamic piston motion should be rendered from the crankshaft.
6. Steam particles and chuff sounds must be tied to running state and crank phase, not emitted every tick blindly.
7. Ponder scenes come after the visual models are stable.

Record automated and manual results in `docs/verification/phase8.md`.

## Testing Guidelines

Minimum automated check: `./gradlew build`. For resource changes also run JSON validation:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
```

For gameplay changes, run `./gradlew runClient`, test in a world, and record results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits, such as `feat: add boiler outlet scaffold` or `docs: update steam fluid plan`. Commit each completed landmark. PRs should include a concise summary, test results, and screenshots or clips for visible in-game changes.
