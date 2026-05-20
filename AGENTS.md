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

## Current Task — Phase 5 Steam Fluid Architecture

Phase 5 is no longer flywheel/governor work. Those blocks are parked inert placeholders. Do not add mechanics, recipes, requirements, or output effects for them unless `PLAN.md` is changed first.

Implement toward the pipe-fed steam architecture while preserving the current direct compact engine:

1. Keep the Phase 4 direct stack working: 3x3x1 Create boiler below cylinder, exact burner SU/RPM table, no passive heat output.
2. Add a storable `steam` fluid via NeoForge fluid APIs. It should be compatible with `FluidStack`, Create tanks, and Create pipes. For now it should be non-placeable and no-bucket.
3. Add `boiler_outlet`: attaches to a Create `FluidTankBlockEntity` boiler, reads controller `BoilerData`, generates steam from active heat and water supply, and outputs only generated steam.
4. The outlet must not accept steam input, drain stored steam, or provide free pressure to normal steam tanks. It is a boiler pressure source, not a general pump.
5. Prefer Create pipe pressure integration through `FluidTransportBehaviour`/`FluidNetwork`; isolate that code and keep a bounded `IFluidHandler` push fallback if needed. Default pressure range target: 30 blocks.
6. Extend our Create boiler integration so valid boiler outlets count as attached boiler devices for active boiler visuals and compact sizing.
7. `steam_inlet` and pipe-fed crankshaft consumption are Phase 6 unless explicitly pulled forward.

## Testing Guidelines

Minimum automated check: `./gradlew build`. For resource changes also run JSON validation:

```sh
find src/main/resources -name '*.json' -exec jq empty {} +
```

For gameplay changes, run `./gradlew runClient`, test in a world, and record results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits, such as `feat: add boiler outlet scaffold` or `docs: update steam fluid plan`. Commit each completed landmark. PRs should include a concise summary, test results, and screenshots or clips for visible in-game changes.
