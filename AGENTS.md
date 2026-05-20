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

## Current Task — Phase 6 Steam Inlet

Flywheel/governor blocks are parked inert placeholders. Do not add mechanics, recipes, requirements, or output effects for them unless `PLAN.md` is changed first.

Implement the pipe-fed engine inlet while preserving the current direct compact engine:

1. `steam_inlet` occupies one of the 16 cylinder shell slots; it is part of the multiblock, not an external adapter.
2. A valid v1 ring is either 16 `steam_cylinder` blocks or 15 `steam_cylinder` blocks plus exactly 1 `steam_inlet`. More than one inlet is invalid.
3. The inlet accepts only `steam` through an input-only fluid capability while assembled and stores it in a small local buffer.
4. The crankshaft consumes inlet steam when available and maps 10 mB/t to one heat unit, up to 180 mB/t for 294,912 SU. Direct compact boiler mode remains the fallback.
5. Keep the Phase 4 direct stack working: 3x3x1 Create boiler below cylinder, exact burner SU/RPM table, no passive heat output.
6. Do not add new flywheel/governor mechanics.

Record automated and manual results in `docs/verification/phase6.md`.

## Testing Guidelines

Minimum automated check: `./gradlew build`. For resource changes also run JSON validation:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
```

For gameplay changes, run `./gradlew runClient`, test in a world, and record results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits, such as `feat: add boiler outlet scaffold` or `docs: update steam fluid plan`. Commit each completed landmark. PRs should include a concise summary, test results, and screenshots or clips for visible in-game changes.
