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

## Current Task — Phase 7 Aeronautics/Sable Compatibility

Flywheel/governor blocks are parked inert placeholders. Do not add mechanics, recipes, requirements, or output effects for them unless `PLAN.md` is changed first.

Implement movement compatibility while preserving normal standalone play:

1. Aeronautics, Simulated, and Sable stay optional runtime dependencies. Do not add hard Java imports for their classes.
2. Register Create `BlockMovementChecks` for all Full Steam Ahead engine blocks.
3. Full Steam Ahead engine blocks should be movable, non-brittle, supportive, and attached to adjacent Full Steam Ahead engine blocks.
4. Bottom cylinder/inlet blocks attach downward to Create Fluid Tank boilers.
5. `boiler_outlet` attaches to its tank side and output pipe side.
6. Register Simulated `SimBlockMovementChecks` only through guarded reflection when the API is present.
7. Add `create:safe_nbt` tags for block entities whose saved state must survive contraption movement.

Record automated and manual results in `docs/verification/phase7.md`.

## Testing Guidelines

Minimum automated check: `./gradlew build`. For resource changes also run JSON validation:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
```

For gameplay changes, run `./gradlew runClient`, test in a world, and record results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits, such as `feat: add boiler outlet scaffold` or `docs: update steam fluid plan`. Commit each completed landmark. PRs should include a concise summary, test results, and screenshots or clips for visible in-game changes.
