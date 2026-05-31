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

## Current Task — Steam Scaling

The old `flywheel` and `governor` placeholder blocks have been removed. Do not re-add mechanics, recipes, requirements, output effects, textures, models, registration, or assets for them unless `PLAN.md` is changed first. This does not apply to the Flywheel rendering library used by client visuals.

Implement steam output using the unit rules in `PLAN.md` and `README.md`:

1. `1 steam unit = 10 mB/t steam = 16,384 SU when consumed`.
2. `boiler_outlet` production scales as active burner units multiplied by Create Fluid Tank boiler height.
3. Water supply gates the scaled budget at 10 mB/t per steam unit.
4. Multiple outlets attached to one boiler split one shared budget and must not duplicate steam.
5. Preserve direct compact engine mode and current piston/cylinder validation.

Record automated and manual results in `docs/verification/phase5.md`.

## Testing Guidelines

Minimum automated check: `./gradlew build`. For resource changes also run JSON validation:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
```

For gameplay changes, run `./gradlew runClient`, test in a world, and record results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits, such as `feat: add boiler outlet scaffold` or `docs: update steam fluid plan`. Commit each completed landmark. PRs should include a concise summary, test results, and screenshots or clips for visible in-game changes.
