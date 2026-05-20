# Repository Guidelines

## Project Structure & Module Organization

This is a NeoForge 1.21.1 Create addon for `Create: Full Steam Ahead` (mod id: `full_steam_ahead`, package: `dev.gustavo.fullsteamahead`).

- `src/main/java/dev/gustavo/fullsteamahead/` — Java source. Entrypoint: `FullSteamAhead.java`.
- `src/main/resources/` — mod metadata and assets: `META-INF/neoforge.mods.toml`, `pack.mcmeta`, `assets/full_steam_ahead/`.
- `PLAN.md` — authoritative design document. Read it before making any structural decisions.
- `docs/verification/` — manual verification notes.
- `build/`, `.gradle/`, `runs/` — generated, do not commit.

## Build Commands

```sh
./gradlew compileJava       # check compilation
./gradlew processResources  # expand resource templates
./gradlew build             # package mod jar to build/libs/
./gradlew runClient         # launch dev client with Create loaded
```

First runs can take several minutes while NeoForge prepares Minecraft artifacts.

## Coding Style

- Java 21, four-space indentation
- Always use `FullSteamAhead.MOD_ID` instead of the string `"full_steam_ahead"`
- Classes: `PascalCase` | Methods/fields: `camelCase` | Constants: `UPPER_SNAKE_CASE` | Resources: `lower_snake_case`
- Keep imports tidy; avoid unrelated reformatting

## Commit Style

Prefer specific conventional commits:

- `chore: remove old controller and firebox blocks`
- `feat: add SteamCylinderBlock with assembled blockstate`
- `refactor: rewrite ModBlocks for new five-block design`

---

## Current Task — Phase 2 Remodel

The design has changed significantly since Phase 2 was originally scoped. Read `PLAN.md` fully before starting. The following is the complete remodel task.

### Context summary

The original Phase 2 registered nine blocks with an old design that included a controller block, a firebox, a boiler drum, and an output coupling. All of those are wrong. The design is now:

- **No controller block.** The engine auto-assembles, like Create's fluid tanks.
- **No firebox.** Blaze Burners (Create's own block) heat directly below Create's vanilla Fluid Tank. We do not need a heat adapter block.
- **No boiler drum.** The boiler is Create's vanilla Fluid Tank. Players build it themselves. We read its data; we do not replace it.
- **No output coupling.** The crankshaft IS the kinetic output block.
- **No large_engine_casing.** The steam_cylinder block serves as the casing ring.
- **No piston_rod.** Redesigned as a physical `piston` block (single type, 4 per engine).

The five blocks that must exist after this remodel: `steam_cylinder`, `piston`, `crankshaft`, `flywheel`, `governor`.

---

### Step 1 — Delete old Java source files

Delete these files entirely:

```
src/main/java/dev/gustavo/fullsteamahead/content/engine/block/EnginePartBlock.java
src/main/java/dev/gustavo/fullsteamahead/content/engine/block/HorizontalEnginePartBlock.java
src/main/java/dev/gustavo/fullsteamahead/content/engine/block/AxialEnginePartBlock.java
```

Delete the now-empty directory `content/engine/block/` and `content/engine/` if empty.

---

### Step 2 — Delete old resource files

Delete all files associated with the six removed blocks. For each of these block names — `large_steam_engine_controller`, `firebox`, `large_engine_casing`, `boiler_drum`, `output_coupling`, `piston_rod` — delete:

```
src/main/resources/assets/full_steam_ahead/blockstates/<name>.json
src/main/resources/assets/full_steam_ahead/models/block/<name>.json
src/main/resources/assets/full_steam_ahead/models/item/<name>.json
src/main/resources/data/full_steam_ahead/loot_table/blocks/<name>.json
```

Also remove their lang entries from `assets/full_steam_ahead/lang/en_us.json` and their entries from `data/minecraft/tags/blocks/mineable/pickaxe.json` and `data/minecraft/tags/blocks/needs_stone_tool.json`.

---

### Step 3 — Create new package structure

Create these directories (and placeholder `.gitkeep` or initial Java files):

```
src/main/java/dev/gustavo/fullsteamahead/content/cylinder/
src/main/java/dev/gustavo/fullsteamahead/content/piston/
src/main/java/dev/gustavo/fullsteamahead/content/crankshaft/
src/main/java/dev/gustavo/fullsteamahead/content/flywheel/
src/main/java/dev/gustavo/fullsteamahead/content/governor/
src/main/java/dev/gustavo/fullsteamahead/registry/   (already exists)
```

---

### Step 4 — Add new Java classes (inert stubs for now)

#### `PistonSection.java`

```
package dev.gustavo.fullsteamahead.content.piston;

import net.minecraft.util.StringRepresentable;

public enum PistonSection implements StringRepresentable {
    INSIDE_LOW("inside_low"),
    INSIDE_HIGH("inside_high"),
    PROTRUDE_LOW("protrude_low"),
    PROTRUDE_HIGH("protrude_high");

    private final String name;

    PistonSection(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
```

#### `SteamCylinderBlock.java`

- Package: `dev.gustavo.fullsteamahead.content.cylinder`
- Extends: `com.simibubi.create.foundation.block.SmartBlock` (Create's SmartBlock, not vanilla Block)
- Blockstate properties:
  - `ASSEMBLED = BooleanProperty.create("assembled")`
- Default state: `assembled = false`
- `createBlockStateDefinition`: adds `ASSEMBLED`
- Constructor body: calls `registerDefaultState(stateDefinition.any().setValue(ASSEMBLED, false))`
- `codec()`: use `simpleCodec(SteamCylinderBlock::new)`
- No other logic yet (connectivity and block entity come in Phase 3)

#### `SteamPistonBlock.java`

- Package: `dev.gustavo.fullsteamahead.content.piston`
- Extends: `com.simibubi.create.foundation.block.SmartBlock`
- Blockstate properties:
  - `ASSEMBLED = BooleanProperty.create("assembled")`
  - `PISTON_SECTION = EnumProperty.create("piston_section", PistonSection.class)`
- Default state: `assembled = false`, `piston_section = PistonSection.INSIDE_LOW`
- `createBlockStateDefinition`: adds both properties
- `codec()`: use `simpleCodec(SteamPistonBlock::new)`
- No other logic yet

#### `CrankshaftBlock.java`

- Package: `dev.gustavo.fullsteamahead.content.crankshaft`
- Extends: `com.simibubi.create.content.kinetics.base.KineticBlock`
- The crankshaft axis is always `Direction.Axis.Y` (vertical only, v1)
- Shaft ports are on the four horizontal faces (N, S, E, W). Top and bottom are capped.
- Override `getRotationAxis()` to return the axis stored in the blockstate (for now, always Y)
- Override `hasShaftTowards(Level, BlockPos, BlockState, Direction)`: return true for N/S/E/W, false for UP/DOWN
- `codec()`: use `simpleCodec(CrankshaftBlock::new)`
- No block entity registration yet (Phase 4)

#### `FlywheelBlock.java`

- Package: `dev.gustavo.fullsteamahead.content.flywheel`
- Extends: `com.simibubi.create.foundation.block.SmartBlock`
- Inert stub for now
- `codec()`: use `simpleCodec(FlywheelBlock::new)`

#### `GovernorBlock.java`

- Package: `dev.gustavo.fullsteamahead.content.governor`
- Extends: `com.simibubi.create.foundation.block.SmartBlock`
- Inert stub for now
- `codec()`: use `simpleCodec(GovernorBlock::new)`

---

### Step 5 — Add `ModBlockEntities.java`

Create `src/main/java/dev/gustavo/fullsteamahead/registry/ModBlockEntities.java`.

For now it is a stub that registers no block entity types yet (Phase 3 will add `SteamCylinderBlockEntity`, Phase 4 will add `CrankshaftBlockEntity`). Include the `DeferredRegister` setup and a `register(IEventBus)` method so `FullSteamAhead.java` can call it. Do not omit this call.

---

### Step 6 — Rewrite `ModBlocks.java`

Replace the entire file. Register exactly these five blocks:

```java
public static final DeferredBlock<SteamCylinderBlock> STEAM_CYLINDER =
    registerBlock("steam_cylinder", SteamCylinderBlock::new, cylinderProperties());

public static final DeferredBlock<SteamPistonBlock> PISTON =
    registerBlock("piston", SteamPistonBlock::new, metalProperties());

public static final DeferredBlock<CrankshaftBlock> CRANKSHAFT =
    registerBlock("crankshaft", CrankshaftBlock::new, metalProperties());

public static final DeferredBlock<FlywheelBlock> FLYWHEEL =
    registerBlock("flywheel", FlywheelBlock::new, metalProperties());

public static final DeferredBlock<GovernorBlock> GOVERNOR =
    registerBlock("governor", GovernorBlock::new, copperProperties());
```

Property helpers:
- `cylinderProperties()`: strength 4.0F / 8.0F, `SoundType.COPPER`, `requiresCorrectToolForDrops()`
- `metalProperties()`: strength 3.5F / 7.0F, `SoundType.METAL`, `requiresCorrectToolForDrops()`
- `copperProperties()`: strength 3.0F / 6.0F, `SoundType.COPPER`, `requiresCorrectToolForDrops()`

Keep the `registerBlock` helper that auto-registers a simple block item.

---

### Step 7 — Update `FullSteamAhead.java`

Add `ModBlockEntities.register(modEventBus)` call in the constructor, after `ModBlocks.register(...)`.

---

### Step 8 — Add resource files for the five new blocks

For each of the five blocks, add:

**Blockstate** (`assets/full_steam_ahead/blockstates/<name>.json`):

`steam_cylinder`: two variants on `assembled` — both use the same model path for now (`full_steam_ahead:block/steam_cylinder`), assembled variant can add `y: 0` as a placeholder.

`piston`: variants on `piston_section` — four values, all pointing to `full_steam_ahead:block/piston` for now.

`crankshaft`: single variant pointing to `full_steam_ahead:block/crankshaft`.

`flywheel`, `governor`: single variant each.

**Block model** (`assets/full_steam_ahead/models/block/<name>.json`):

Use Create placeholder textures as stand-ins:
- `steam_cylinder`: `"parent": "minecraft:block/cube_all"`, texture `"all": "create:block/copper_casing_connected"`
- `piston`: `"parent": "minecraft:block/cube_all"`, texture `"all": "create:block/andesite_casing_connected"`
- `crankshaft`: `"parent": "create:block/shaft"` (reuse Create's shaft model as placeholder), axis Y
- `flywheel`: `"parent": "minecraft:block/cube_all"`, texture `"all": "create:block/andesite_casing_connected"`
- `governor`: `"parent": "minecraft:block/cube_all"`, texture `"all": "create:block/brass_casing_connected"`

**Item model** (`assets/full_steam_ahead/models/item/<name>.json`):

All five: `{ "parent": "full_steam_ahead:block/<name>" }`

**Loot table** (`data/full_steam_ahead/loot_table/blocks/<name>.json`):

All five: standard self-drop loot table (copy the existing pattern from any surviving loot table, updating the item name).

---

### Step 9 — Update lang, tags, and creative tab

**`en_us.json`** — replace all old entries with:

```json
{
  "block.full_steam_ahead.steam_cylinder": "Steam Cylinder",
  "block.full_steam_ahead.piston": "Steam Piston",
  "block.full_steam_ahead.crankshaft": "Crankshaft",
  "block.full_steam_ahead.flywheel": "Flywheel",
  "block.full_steam_ahead.governor": "Governor"
}
```

**`data/minecraft/tags/blocks/mineable/pickaxe.json`** — replace old block list with the five new block ids.

**`data/minecraft/tags/blocks/needs_stone_tool.json`** — same replacement.

**`ModCreativeTabs.java`** — replace the old block references with the five new `ModBlocks` entries.

---

### Step 10 — Verify

Run `./gradlew build`. It must pass with zero errors.

Run `./gradlew runClient`. Confirm:

1. Game launches and reaches the main menu without errors.
2. `Create: Full Steam Ahead` appears in the Mods list.
3. All five blocks appear in the creative tab: Steam Cylinder, Steam Piston, Crankshaft, Flywheel, Governor.
4. All five blocks can be placed in a world without crashes.
5. None of the old nine blocks appear anywhere.

Document results in `docs/verification/phase2_remodel.md`.

---

## Testing Guidelines

Minimum for every PR: `./gradlew build` must pass. For in-game changes: `./gradlew runClient` and manual world check with results in `docs/verification/`.

## Commit & PR Guidelines

Prefer specific conventional commits. PRs must include test results and screenshots for visible changes.
