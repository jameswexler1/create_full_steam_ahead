# Repository Guidelines

## Project Structure & Module Organization

This is a NeoForge 1.21.1 Create addon scaffold for `Create: Full Steam Ahead`.

- `src/main/java/dev/gustavo/fullsteamahead/` contains Java source. The current entrypoint is `FullSteamAhead.java`.
- `src/main/resources/` contains mod metadata and assets, including `META-INF/neoforge.mods.toml`, `pack.mcmeta`, and `assets/full_steam_ahead/`.
- `docs/verification/` stores manual verification notes.
- `build/`, `.gradle/`, and `runs/` are generated and should not be committed.

Future tests should live under `src/test/java/` or NeoForge game-test sources when introduced.

## Build, Test, and Development Commands

Use the Gradle wrapper committed in this repo:

```sh
./gradlew compileJava
./gradlew processResources
./gradlew build
./gradlew runClient
```

- `compileJava` checks Java compilation.
- `processResources` expands `neoforge.mods.toml` and resource templates.
- `build` packages the mod jar in `build/libs/`.
- `runClient` launches the NeoForge dev client with this mod and Create loaded.

First runs can take several minutes while NeoForge prepares Minecraft artifacts.

## Coding Style & Naming Conventions

Use Java 21 and four-space indentation. Keep packages under `dev.gustavo.fullsteamahead`. Use `FullSteamAhead.MOD_ID` for the mod id (`full_steam_ahead`) instead of repeating string literals.

Naming patterns:

- Classes: `PascalCase`
- Methods/fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Resource paths: lowercase snake case, e.g. `large_steam_engine_controller`

No formatter is configured yet; keep imports tidy and avoid unrelated reformatting.

## Testing Guidelines

At minimum, run:

```sh
./gradlew build
```

For runtime checks, run `./gradlew runClient` and confirm the main menu opens and `Create: Full Steam Ahead` appears in the Mods list. When gameplay blocks are added, include manual world checks and document results in `docs/verification/`.

## Commit & Pull Request Guidelines

The normal repo currently has one broad commit (`implemented phase one`). Prefer more specific conventional commits going forward, such as:

- `chore: add block registration scaffold`
- `feat: add inert engine casing block`
- `docs: update phase two verification`

Pull requests should include a concise summary, test results, and screenshots or short clips for visible in-game changes. Link related issues or planning sections when applicable.

## Agent-Specific Instructions

Do not commit generated directories such as `build/`, `.gradle/`, or `runs/`. Request elevated access before committing if `.git` is not writable from the current session. Keep changes phase-scoped: phase two should add inert blocks and assets, not multiblock logic.
