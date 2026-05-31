# Create: Full Steam Ahead — Design Plan

Last updated: 2026-05-31

## Goal

Build a NeoForge 1.21.1 Create addon that adds a large, multiblock reciprocating steam engine. The engine should feel like a believable ship or aircraft power plant: visually large, mechanically grounded in how real steam engines work, and fully compatible with Create shaft networks and Create Aeronautics/Sable moving sublevels.

The engine produces Create rotational power. It does not directly push aircraft or ships in v1. Aeronautics propellers already consume Create rotation and apply Sable point forces:

`large steam engine → Create shaft network → Aeronautics propeller/thruster → Sable physics`

---

## Research Snapshot

Verified target stack:

- Minecraft: `1.21.1`
- Loader: `NeoForge`
- Java: `21`
- Create: `6.0.10` (Maven dev dep: `6.0.10-280`)
- Create Aeronautics: `1.2.1+mc1.21.1`
- Sable: required by Aeronautics; provides moving block sublevels and physics pipeline.

Important source findings:

- Create's `SteamEngineBlockEntity` reads a neighbouring `FluidTankBlockEntity` boiler and updates a hidden `PoweredShaftBlockEntity`. We follow the same player-facing pattern: the player places a normal Create shaft, and the assembled Full Steam Ahead engine swaps it to a hidden `full_steam_ahead:powered_shaft`.
- The hidden `full_steam_ahead:powered_shaft` extends Create's powered shaft block but uses our own `GeneratingKineticBlockEntity` implementation so we can preserve the exact Full Steam Ahead SU/RPM balance.
- Create exposes `BlockStressValues` for stress tooltip metadata.
- NeoForge `BaseFlowingFluid`, `FluidType`, `FluidTank`, `IFluidHandler`, and `Capabilities.FluidHandler.BLOCK` are present in the 21.1.230 dev classpath and are the correct baseline for a storable `steam` fluid.
- Create's fluid pipes ultimately interact with NeoForge `IFluidHandler`; Create also exposes internal pipe pressure helpers through `FluidTransportBehaviour`, `FluidNetwork`, `FluidPropagator.getPumpRange()`, and `PipeConnection`.
- Create's boiler water path uses `BoilerData.BoilerFluidHandler` to record water supply rate. A steam outlet must read/generate from `BoilerData`; it must not drain stored steam from neighbouring tanks.
- Create 6.0.10 exposes `com.simibubi.create.api.contraption.BlockMovementChecks` for movement permission, brittleness, support, and attached-block checks.
- The current Simulated Project source exposes `dev.simulated_team.simulated.index.SimBlockMovementChecks` with attached-block and additional-block registration hooks. Aeronautics uses that API from `AeroBlockMovementChecks`.
- The local dev classpath intentionally does not include Aeronautics, Simulated, or Sable jars. Compatibility must therefore remain optional at compile time and guarded at runtime.
- Flywheel 1.0.6 exposes `VisualizerRegistry`, `SimpleBlockEntityVisualizer`, `AbstractBlockEntityVisual`, `TransformedInstance`, and `PartialModel`. Create's own `SteamEngineVisual` uses this stack for piston/linkage animation.
- Create exposes `KineticBlockEntityRenderer.getAngleForBe(...)`, which drives the piston animation phase from the linked powered shaft so visuals stay synchronized with Create kinetic rotation.
- Ponder 1.0.82 exposes `PonderPlugin`, `PonderSceneRegistrationHelper`, and `PonderIndex.addPlugin(...)`. Ponder scenes should be registered from a client-side plugin after the visual models are stable.

Primary references:

- Create developer docs: https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1
- Create source: https://github.com/Creators-of-Create/Create
- Create Aeronautics: https://modrinth.com/mod/create-aeronautics
- Simulated Project source: https://github.com/Creators-of-Aeronautics/Simulated-Project
- Sable: https://github.com/ryanhcode/sable

---

## Core Design Decisions (locked)

These are final and must not be revisited without updating this document.

### No controller block

The engine auto-assembles. There is no block the player right-clicks to "form" the structure. Assembly is triggered automatically when blocks are placed, exactly as Create's fluid tanks connect when placed adjacent to each other. This is the Create philosophy: machines emerge from block placement, not from explicit activation.

### The boiler is Create's vanilla Fluid Tank

We do not add a custom boiler block. The player builds a standard Create Fluid Tank structure, heats it with Create's Blaze Burners placed below it, and pipes water in using Create's own pumps and pipes. Create's own heat and water indicators appear on the tank.

Two boiler connection modes are supported in the plan:

1. **Direct compact mode** — the current Phase 4 engine still works with the cylinder sitting directly on a 3×3×1 Create Fluid Tank boiler.
2. **Pipe-fed mode** — Phase 5 adds a `boiler_outlet` block that converts valid Create boiler output into storable `steam` fluid and pushes that steam into Create fluid pipes.

The direct compact boiler must be at least 3×3×1 (9 fluid tank blocks) to support a full cylinder frame above it.

### Steam is a storable fluid

`steam` becomes a real NeoForge/Create-compatible fluid. It can be stored in Create Fluid Tanks and moved through Create Fluid Pipes. It is not a replacement for water, and it is not produced by ordinary tanks full of stored steam.

The only automatic pressure source for steam is `boiler_outlet`, and it must only generate/push steam when attached to a valid active Create boiler. Stored steam in normal tanks may be moved by normal Create logistics, but it must not get free boiler-outlet pressure.

For v1, `steam` should be non-placeable and should not need a bucket. It exists for tanks, pipes, gauges, and engine consumption.

### Flywheel and governor removed

The old inert `flywheel` and `governor` placeholders have been removed from registration, creative inventory, Java source, assets, tags, loot tables, and lang. Do not re-add them without a new design pass in this document.

### Engine orientation: vertical only in v1

The cylinder, piston column, and shaft link remain vertical in v1:

- Steam Cylinder frame
- Piston column rising through the cylinder
- A regular horizontal Create shaft above the piston stroke space

In direct compact mode, the Create Fluid Tank boiler still sits directly below the cylinder and Blaze Burners still heat upward below the tank. In pipe-fed mode, the boiler can be remote, but the engine assembly itself is still vertical.

Other orientations (horizontal, inverted) are deferred to a future version.

### Block list

| Block | Class base | Role |
|---|---|---|
| `steam_cylinder` | `Block + IBE<SteamCylinderBlockEntity>` | Forms the 3×3×2 hollow casing ring around the piston. Self-assembles. |
| `piston_head` | `Block + IBE<PistonHeadBlockEntity>` | Physical piston head and engine brain. Validates the stack, reads steam/boiler state, and powers the linked shaft. |
| `piston` | `Block` | Physical piston body block above the piston head. Animated when running. |
| `powered_shaft` | `PoweredShaftBlock + FullSteamPoweredShaftBlockEntity` | Hidden internal replacement for a player-placed Create shaft. Provides kinetic output while cloning/dropping as a normal shaft. |
| `boiler_outlet` | `Block + SmartBlockEntity` | Attaches to a Create Fluid Tank boiler, generates `steam`, and provides pressure into pipes. |
| `steam_inlet` | `Block + SmartBlockEntity` | Phase 6 block. Replaces one cylinder shell block in the 3×3×2 ring and accepts `steam` from pipes. |
| `engine_telegraph` | `HorizontalDirectionalBlock` | Inert decorative/control block for ship bridge theming. No engine control mechanics yet. |
| `stepped_lever` | `FaceAttachedHorizontalDirectionalBlock + IBE<SteppedLeverBlockEntity>` | Create-style stepped analog redstone lever for ship controls and future bridge panels. |

Removed from the old plan (do not re-add without discussion):

- `large_steam_engine_controller` — no controller
- `firebox` — Blaze Burners go directly under Create's fluid tank
- `large_engine_casing` — cylinder block handles casing
- `boiler_drum` — Create's fluid tank is the boiler
- `output_coupling` — normal Create shafts are the player-facing kinetic output
- `piston_rod` — renamed and redesigned as `piston`
- `crankshaft` — removed in favor of vanilla Create shaft placement and a hidden powered shaft, matching Create's own steam engine pattern
- `flywheel` — removed inert placeholder
- `governor` — removed inert placeholder

---

## Engine Structure

A minimal working engine (vertical, default orientation):

```
        [Create Shaft]            ← player-placed horizontal shaft; internally becomes powered_shaft
        [          ]              ← empty stroke space
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ← top of cylinder frame (assembled texture: cylinder head cap)
[Cyl]   [  Piston  ]   [Cyl]     ← upper cylinder layer (inner face texture active)
[Cyl]   [          ]   [Cyl]
[Cyl]   [PistonHead]   [Cyl]     ← lower cylinder layer
[Cyl]   [          ]   [Cyl]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ← top of Create Fluid Tank (boiler)
[Tank]  [  Tank    ]   [Tank]
[Tank]  [  Tank    ]   [Tank]    ← Create Fluid Tank blocks (player-placed, not our blocks)
[Tank]  [  Tank    ]   [Tank]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BB  ]  [   BB     ]   [BB  ]    ← Blaze Burners (player-placed, Create blocks)
```

Block counts for a minimal engine:
- Direct compact mode: 16 × `steam_cylinder` (8 per layer × 2 layers)
- Pipe-fed mode: 15 × `steam_cylinder` + 1 × `steam_inlet` occupying any cylinder shell slot
- 1 × `piston_head` in the lower cylinder bore center
- 1 × `piston` above it, in the upper cylinder bore
- 1 × empty stroke block above the piston
- 1 × regular horizontal Create `shaft` above the empty stroke space
- At least 9 × Create `fluid_tank` (3×3×1 minimum)
- At least 1 × Create `blaze_burner`

Pipe-fed ship/airship layout (planned):

```
[Boiler Room]
[Blaze Burners] → [Create Fluid Tank Boiler] → [Boiler Outlet]
                                                ||
                                                || steam fluid, pressure-assisted
                                                \/
                                          [Create Fluid Pipes]
                                                ||
                                                \/
[Engine Room]
[Steam Cylinder Ring with one Steam Inlet] → [Piston Head + Piston + Create Shaft] → Create shafts/gearboxes/propellers
```

The current direct compact stack remains valid while the pipe-fed path is added.

---

## Three-Layer Assembly

Assembly is layered. Each layer has its own validity and its own visual state.

### Layer 1 — The Boiler (Create's system, untouched)

The player builds a Create Fluid Tank structure and heats it with Blaze Burners. Create handles heat indicator, water indicator, water supply tracking, and boiler visuals.

In direct compact mode, the `piston_head` block entity reads this boiler's `BoilerData` directly.

In pipe-fed mode, `boiler_outlet` reads this boiler's `BoilerData`, creates `steam` fluid from valid active heat and water supply, and pushes that steam into Create pipes. This still does not replace Create's boiler; it is an outlet/valve attached to it.

### Layer 2 — The Cylinder Ring (our auto-assembly)

The 3×3×2 ring self-assembles when all 16 shell positions are filled correctly. In direct compact mode all 16 positions are `steam_cylinder`. In pipe-fed mode exactly one shell position may be `steam_inlet`, with the other 15 positions being `steam_cylinder`. The inlet can occupy any of the 16 shell slots. The moment the 16th shell block is placed and the ring is complete:

- All 16 shell blocks flip `ASSEMBLED = true`
- Connected textures activate (inner bore faces appear, top ring shows cylinder head cap)
- The cylinder block entity designated as root scans the 9 blocks directly below the bottom ring layer for Create `FluidTankBlockEntity` instances
- If a valid boiler is found, the cylinder root caches the boiler position and starts reading `BoilerData`
- If no valid boiler is below, the cylinder assembles visually but shows a "no steam source" indicator (goggle overlay, no particles)

The cylinder ring also disassembles visually if any of the 16 shell blocks is removed, even if it has a valid boiler or inlet.

**Shape constraint**: The ring must be exactly 3×3 in cross-section with the centre 1×1 column hollow. No other shape is accepted in v1. Multiple inlets in one ring are invalid for v1.

**Boiler detection**: The bottom ring layer checks positions (0,−1,0) through (2,−1,2) relative to its south-west corner for `FluidTankBlockEntity`. It needs at least the 8 positions directly below the ring blocks (positions matching the cylinder shell footprint) to be valid fluid tanks. The boiler does not need to be assembled in any Create-specific sense; it just needs to be fluid tanks with Blaze Burners below.

### Layer 3 — The Piston Head and Shaft Link (kinetic assembly)

When the `piston_head` block entity revalidates, it scans upward along its Y axis:

1. Expects one `piston` block directly above it
2. Expects one empty stroke block above the piston
3. Expects a horizontal regular Create shaft or hidden Full Steam Ahead powered shaft above the empty stroke space
4. Expects a valid assembled `SteamCylinder` ring around the lower piston head and upper piston body
5. Expects either a valid Create fluid tank layer directly below the ring's bottom layer, or one assembled `steam_inlet` occupying a cylinder shell slot

If all checks pass: the piston head stores references to the cylinder root, inlet, boiler, and shaft. If the player placed a normal Create shaft, it is swapped to `full_steam_ahead:powered_shaft` and powered from our exact steam output calculation. Direct compact mode reads boiler heat/water. Pipe-fed mode consumes stored `steam` from the inlet. If both sources exist, piped steam is preferred while available, with direct boiler output as fallback.

If any check fails: the piston head clears assembly, restores the hidden powered shaft back to a normal Create shaft when it owns that shaft, and shows an incomplete-structure goggle overlay.

**Revalidation triggers:**
- Any `piston` or `piston_head` block placed or removed
- Any `steam_cylinder` block placed or removed within the expected positions
- Any Create fluid tank block placed or removed directly below the cylinder's bottom ring
- Piston head block entity loads from disk or lazy-ticks after the player places the shaft

Pipe-fed mode accepts either the direct boiler below the ring or a valid steam inlet occupying one assembled cylinder shell slot. Direct compact mode must remain working during the transition.

---

## Block Details

### `SteamCylinder`

- Extends vanilla `Block` and implements Create `IBE<SteamCylinderBlockEntity>` because Create 6.0.10-280 does not expose `SmartBlock`
- Block entity: `SteamCylinderBlockEntity extends SmartBlockEntity`
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
  - `INNER_FACE: EnumProperty<Direction>` — which face is the inner bore face (used for rendering the bore texture on the correct face toward the hollow centre)
- On placement: triggers connectivity scan among adjacent `SteamCylinder` blocks. If a complete 3×3×2 ring with hollow centre is detected, assembly fires.
- On removal: broadcasts disassembly to all cylinder blocks sharing the same ring; they all flip `ASSEMBLED = false`.
- One cylinder block is designated the ring root (deterministically: lowest BlockPos by Y then X then Z). The root block entity holds: boiler position cache, steam data cache, assembled flag.
- Implements `IHaveGoggleInformation`: shows boiler link status, heat level, water level.

### `PistonHead` (registered as `piston_head`)

- Extends vanilla `Block` and implements Create `IBE<PistonHeadBlockEntity>`
- Block entity: `PistonHeadBlockEntity extends SmartBlockEntity`
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
- Required at the lower center of the cylinder bore, directly below the piston body.
- Validates the complete engine and owns the hidden powered shaft link.
- Animation: when `ASSEMBLED = true`, the static block model is hidden and the piston head block entity renders the piston head as a dynamic partial. It remains visible at rest and reciprocates while the linked shaft is running.

### `SteamPiston` (registered as `piston`)

- Extends vanilla `Block`
- No block entity in v1 (purely structural and visual)
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
  - `PISTON_SECTION: EnumProperty<PistonSection>` where `PistonSection` has values `INSIDE_LOW`, `INSIDE_HIGH`, `PROTRUDE_LOW`, `PROTRUDE_HIGH` — determines which texture variant and animation offset to use
- When the piston head validates the structure, it sets the assembled state and piston section on the one piston body block.
- Animation: when `ASSEMBLED = true`, the static piston block model is hidden and the piston head block entity renders the moving piston body partial. It remains visible at rest and reciprocates while the linked shaft is running. No block actually moves; the animation is purely rendered via Flywheel/fallback block entity rendering.

### Hidden `PoweredShaft`

- Registered as `full_steam_ahead:powered_shaft`.
- Hidden from the creative tab and has no block item.
- Extends Create `PoweredShaftBlock`.
- Block entity: `FullSteamPoweredShaftBlockEntity extends GeneratingKineticBlockEntity`.
- Axis: inherited from the player-placed Create shaft. Only the shaft's own axis transmits rotation.
- Clones and drops as `create:shaft`, so players continue to interact with it like a normal shaft.
- `getGeneratedSpeed()`: returns the active burner-count or steam-rate RPM tier when assembled and steam is available; 0 otherwise.
- `calculateAddedStressCapacity()`: returns capacity proportional to available direct boiler heat or consumed `steam`.
- If the engine becomes invalid, the piston head clears shaft power and restores this block to a normal Create shaft.

### `BoilerOutlet` (planned as `boiler_outlet`)

- Block entity: `BoilerOutletBlockEntity extends SmartBlockEntity`
- Placed on a Create Fluid Tank block face. The block's back side must touch a `FluidTankBlockEntity`; the front side outputs steam.
- Validates the attached tank's controller and reads its `BoilerData`.
- Counts as an attached boiler device in our Create boiler integration so Create's own tank visuals and compact boiler sizing activate even for small outlet-only boilers.
- Generates `steam` fluid only from valid boiler heat and water supply. It must never drain or re-pressurize steam from normal storage tanks.
- Steam unit model:
  - `1 steam unit = 10 mB/t steam`
  - Normal active burner heat contributes 1 burner unit; Blaze Cake heat contributes 2 burner units
  - Total boiler steam units = `min(active burner units, water supply heat level) × boiler height`
  - `3x3x1` with 9 normal burners produces 9 units, or 90 mB/t
  - `3x3x6` with 9 normal burners produces 54 units, enough for six full normal engines
  - `3x3x6` with 9 Blaze Cake burners produces 108 units, enough for twelve full pipe-fed engines
- Pipe-fed steam is generic stored steam in v1: one engine consumes at most 9 units or 90 mB/t and produces at most 147,456 SU. Blaze Cakes increase total steam-stream capacity; they do not make one pipe-fed engine exceed normal full output without a future superheated-steam design.
- Multiple `boiler_outlet` blocks attached to one boiler split the same total steam unit budget in a stable position order; they must not duplicate steam.
- Exposes an output-only `IFluidHandler` for `steam`.
- Applies pressure to the connected Create pipe network so the player does not need a mechanical pump directly at the boiler outlet.
- Default pressure range target: 30 blocks. This must become a server config value.
- Goggle overlay: boiler linked/missing, outlet steam units, total boiler steam units, attached outlet count, steam production rate, internal buffer, output pressure state.

### `SteamInlet` (`steam_inlet`)

- Block entity: `SteamInletBlockEntity extends SmartBlockEntity`
- Occupies one shell slot in the assembled cylinder ring, replacing one `steam_cylinder` block. It can be placed in any of the 16 shell positions.
- A v1 ring accepts either 0 inlets (direct compact mode) or exactly 1 inlet (pipe-fed mode). Multiple inlets are invalid.
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
- Accepts only `steam` through an input-only `IFluidHandler`.
- Stores a small local steam buffer. The buffer is not a pressure source and cannot be drained by external blocks.
- When the ring assembles, the inlet caches the ring origin and cylinder root. When the ring disassembles, it clears that link and stops accepting steam.
- The piston head prefers consuming steam from the linked inlet. If no usable inlet steam exists and a direct boiler is present, direct compact mode remains the fallback.
- Pipe-fed balance maps consumed steam rate to output:
  - 10 mB/t consumed steam = 1 heat unit = 16,384 SU
  - Maximum consumed steam for one pipe-fed engine = 90 mB/t = 9 heat units = 147,456 SU
  - RPM uses burner-equivalent tiers from consumed steam units: 1-2 = 16 rpm, 3-4 = 32 rpm, 5-8 = 48 rpm, 9 = 64 rpm
- Goggle overlay: ring link status, steam buffer, accepted steam rate, engine link.

### Removed placeholders

- The old inert `flywheel` and `governor` block placeholders are removed.
- They have no registration, item forms, assets, loot tables, mining tags, lang entries, recipes, or movement rules.
- Any future inertia/regulator feature needs a fresh design section before new blocks are added.

---

## Package Layout

```
src/main/java/dev/gustavo/fullsteamahead/
  FullSteamAhead.java
  registry/
    ModBlocks.java
    ModBlockEntities.java
    ModFluids.java
    ModItems.java
    ModCreativeTabs.java
  content/
    cylinder/
      SteamCylinderBlock.java
      SteamCylinderBlockEntity.java
      CylinderConnectivity.java        ← ring detection and root election logic
    piston/
      SteamPistonBlock.java
      PistonHeadBlock.java
      PistonHeadBlockEntity.java       ← validates the engine, reads direct boiler or steam inlet, owns powered shaft
      EngineValidator.java             ← fixed vertical engine validation
      PistonSection.java               ← enum: INSIDE_LOW, INSIDE_HIGH, PROTRUDE_LOW, PROTRUDE_HIGH
    shaft/
      FullSteamPoweredShaftBlock.java  ← hidden replacement for player-placed Create shaft
      FullSteamPoweredShaftBlockEntity.java
    steam/
      BoilerOutletBlock.java
      BoilerOutletBlockEntity.java
      SteamInletBlock.java
      SteamInletBlockEntity.java
    telegraph/
      EngineTelegraphBlock.java        ← inert directional ship control block
  compat/
    create/
      FullSteamBoilerIntegration.java
      CreateMovementCompat.java
    movement/
      FullSteamMovementRules.java
    simulated/
      SimulatedMovementCompat.java
  client/
    FullSteamAheadClient.java
    FullSteamPartialModels.java
    render/
      PistonHeadVisual.java
      PistonHeadRenderer.java
      PistonHeadAnimation.java
    ponder/
      FullSteamPonderPlugin.java
      FullSteamPonderScenes.java
      FullSteamPonderTags.java
```

---

## Balance

Boiler reference: vanilla Create steam engine baseline.

| Parameter | Value |
|---|---|
| RPM tiers | 16, 32, 48, 64 |
| Default/max RPM | 64 |
| Active burner count | 0-9 fired Blaze Burners under the 3x3 boiler footprint |
| Heat units | 1 per normal fired burner, 2 per Blaze Cake burner |
| Full engine at regular max heat | 147,456 SU at 64 RPM |
| Full engine at full Blaze Cake heat | 294,912 SU at 64 RPM |

Direct compact formula:

```
active_burners   = count of Blaze Burners with heat >= FADING under the 3x3 boiler
cake_burners     = count of those burners with heat >= SEETHING
heat_units       = active_burners + cake_burners
capacity_su      = heat_units * 16384
output_speed_rpm = tier_by_active_burner_count(active_burners)
```

Steam fluid formula (planned):

```
boiler_heat_units     = min(active_heat, water_limited_heat, size_limited_heat)
steam_production_mbpt = boiler_heat_units * 10
steam_consumed_mbpt   = min(available_steam_rate, engine_max_consumption)
engine_heat_units     = floor(steam_consumed_mbpt / 10)
capacity_su           = engine_heat_units * 16384
output_speed_rpm      = tier_by_steam_consumption(steam_consumed_mbpt)
```

The `10 mB/t per heat unit` value intentionally mirrors Create's boiler water-supply threshold (`BoilerData.getMaxHeatLevelForWaterSupply()` is based on 10 mB/t per heat level). It keeps direct and pipe-fed output comparable: 9 normal heat units produce 90 mB/t steam and 147,456 SU; 18 Blaze Cake heat units produce 180 mB/t steam and 294,912 SU.

Important balance distinction: stored steam does not remember how many burners produced it. Direct compact mode keeps the exact active-burner RPM table. Pipe-fed mode should start by tiering RPM from delivered steam rate, treating pipes and tanks like a pressure manifold. If this feels wrong in testing, add a pressure/quality layer later rather than encoding source metadata into the fluid.

RPM tiers:

| Active fired burners | RPM |
|---:|---:|
| 0 | 0 |
| 1-2 | 16 |
| 3-4 | 32 |
| 5-8 | 48 |
| 9 | 64 |

Planned pipe-fed RPM tiers should map equivalent steam rate to the same feel:

| Delivered steam | RPM |
|---:|---:|
| 0-9 mB/t | 0 |
| 10-29 mB/t | 16 |
| 30-49 mB/t | 32 |
| 50-89 mB/t | 48 |
| 90+ mB/t | 64 |

Unfired Blaze Burners only provide passive heat in Create and must produce `0` output for this engine. Blaze Cakes double the SU contribution of each individual burner without increasing RPM above the active-burner-count tier. Water supply remains required for output.

Pipe-fed mode must not produce more power than the same boiler would produce in direct compact mode. Steam storage is a buffer/logistics feature, not a free multiplier.

All constants must become server config values before release.

---

## Development Phases

### Phase 0: Decisions — Complete
### Phase 1: Project Scaffold — Complete

### Phase 2: Block Registration Remodel — Complete

**Goal**: Replace the old block set with the new design. Bootable with correct blocks in-game.

Tasks:
- [x] Delete: `large_steam_engine_controller`, `firebox`, `large_engine_casing`, `boiler_drum`, `output_coupling`, `piston_rod` — all Java classes, blockstates, models (block + item), loot tables, lang entries
- [x] Delete: `EnginePartBlock.java`, `HorizontalEnginePartBlock.java`, `AxialEnginePartBlock.java`
- [x] Add: `SteamCylinderBlock` with `ASSEMBLED` BooleanProperty blockstate
- [x] Add: `SteamPistonBlock` with `ASSEMBLED` BooleanProperty and `PISTON_SECTION` EnumProperty blockstate
- [x] Add current shaft output: player-facing Create shaft with hidden `full_steam_ahead:powered_shaft` kinetic generator
- [x] Superseded: the old inert `flywheel` and `governor` stubs were later removed entirely
- [x] Add: `PistonSection.java` enum
- [x] Add: `ModBlockEntities.java` stub register, no block entity types yet
- [x] Rewrite `ModBlocks.java` to register only the 5 new blocks with correct base classes
- [x] Add placeholder blockstates, models, loot tables, lang for all 5 blocks
- [x] Update creative tab
- [x] Update mining tags
- [x] Verify `./gradlew build` passes
- [x] Verify all 5 blocks appear in-game

Implementation note: Create `6.0.10-280` for NeoForge 1.21.1 does not expose `com.simibubi.create.foundation.block.SmartBlock`; the correct Phase 3 integration is vanilla `Block` plus Create's `IBE<T>` interface and `SmartBlockEntity`.

### Phase 3: Cylinder Ring Auto-Assembly — Complete

**Goal**: Placing 16 `SteamCylinder` blocks in the correct 3×3×2 hollow ring shape triggers visual assembly.

Tasks:
- [x] Implement `CylinderConnectivity` — scans from placed block, validates ring shape, elects root
- [x] Implement `SteamCylinderBlockEntity` — holds assembled state, boiler cache, root flag
- [x] On assembly: flip all 16 blocks to `ASSEMBLED = true`, fire connected texture update
- [x] On disassembly (any block removed): flip all back to `ASSEMBLED = false`
- [x] Boiler detection: root block entity checks shell positions below bottom ring layer for `FluidTankBlockEntity`
- [x] Add goggle overlay: assembly status, boiler link, heat level, water level
- [x] Add "no steam source" goggle overlay when assembled but no boiler below
- [x] Verify: place ring → assembles; remove one block → disassembles; place on boiler → boiler detected; reload preserves assembled state
- [x] Verify negative cases: incomplete ring stays unassembled; blocked center prevents assembly; assembled ring without boiler shows no steam source

Implementation note: Phase 3 uses `Block implements IBE<SteamCylinderBlockEntity>` plus `SmartBlockEntity`, matching Create 6.0.10's available API. `SmartBlock` is still not used because it is not present in this classpath.

### Phase 4: Piston Head Validation and Kinetic Output — Complete

**Goal**: the engine validates the vertical piston/cylinder stack and generates rotation into a Create shaft.

Tasks:
- [x] Initially implemented `CrankshaftBlockEntity extends GeneratingKineticBlockEntity`
- [x] Refactored output ownership to `PistonHeadBlockEntity` plus hidden `FullSteamPoweredShaftBlockEntity`, matching Create's vanilla shaft-grab pattern
- [x] Upward scan from piston head: piston body → empty stroke → horizontal Create shaft; ring and boiler/inlet are validated around/below the piston head
- [x] If valid: store cylinder root, inlet, boiler, and shaft refs; power the hidden shaft block entity
- [x] `getGeneratedSpeed()`: follows exact active-burner RPM tiers: 1-2 = 16 RPM, 3-4 = 32 RPM, 5-8 = 48 RPM, 9 = 64 RPM
- [x] `calculateAddedStressCapacity()`: follows exact SU output: 16,384 SU per normal fired burner, doubled per Blaze Cake burner, up to 294,912 SU
- [x] Read `BoilerData` from the `FluidTankBlockEntity` at boiler position each server tick
- [x] Make Create's own `BoilerData.evaluate()` count assembled Full Steam Ahead engines
- [x] Treat 3x3x1 tank boilers as the compact optimal size when a Full Steam Ahead engine is attached
- [x] Require active fired Blaze Burner heat; passive/unfired heat produces no rotation
- [x] Scan the 3x3 Blaze Burner footprint directly so mixed normal/Blaze Cake burners contribute exact per-burner SU
- [x] Add piston block state updates: set `ASSEMBLED` and section/head state on the moving piston stack when piston head validates
- [x] Add visible placeholder models for assembled piston section states
- [x] Add revalidation on neighbour changes
- [x] Add goggle overlay: assembly status, active burners, heat units, water supply, RPM, SU
- [x] Verify exact output tiers: no passive output, 1-9 normal fired burners match SU/RPM table, and Blaze Cake burners double SU individually
- [ ] Optional hardening verify: break piston → shaft stops; break boiler → shaft stops; restore/reload → state recovers

Implementation note: Phase 4 uses a small Create compatibility mixin so `BoilerData.evaluate()` recognizes valid Full Steam Ahead piston-head engines as attached steam engines. This lets Create's own Fluid Tank switch to active boiler visuals/capabilities and lets the compact 3x3x1 boiler footprint behave as the intended v1 boiler size.

### Phase 5: Steam Fluid and Boiler Outlet

**Goal**: Add storable `steam` fluid and a boiler-attached outlet that generates and pressure-feeds steam into Create pipes. The current direct compact engine must continue working.

- [x] Add `ModFluids.java` and register `steam` as a storable NeoForge fluid compatible with `FluidStack`, Create tanks, and Create pipes
- [x] Keep `steam` non-placeable and no-bucket unless a later gameplay reason requires otherwise
- [x] Add `boiler_outlet` block, item, block entity, blockstate/model/item model/loot/lang/tags/creative entry
- [x] `boiler_outlet` placement: back side must touch a Create `FluidTankBlockEntity`; front side outputs to pipes/tanks
- [x] `BoilerOutletBlockEntity` validates the attached tank controller and reads `BoilerData`
- [x] Extend `FullSteamBoilerIntegration`/mixin logic so boiler outlets count as attached boiler devices and keep Create boiler visuals active
- [x] Generate steam only from active Create boiler heat and water supply; do not drain or pump stored steam from normal tanks
- [x] Implement output-only `IFluidHandler` for generated `steam`
- [x] Implement pressure-assisted output with default 30-block range; prefer Create `FluidTransportBehaviour`/`FluidNetwork` integration, fallback to bounded `IFluidHandler` push if needed
- [x] Add goggle overlay for boiler link, heat, water, steam production rate, buffer, and output pressure state
- [x] Verify: direct compact engine still works exactly as Phase 4
- [x] Verify: boiler outlet produces steam only on valid active boilers, fills Create tanks through pipes, and does not auto-pump from stored steam tanks
- [x] Apply Create `FluidTransportBehaviour` pressure so generated steam is visible in connected Create pipes
- [x] Register steam open-pipe effect and outlet vent particles for open/unconnected steam leaks
- [x] Verify: steam visibly flows through pipes and open pipe ends vent steam particles
- [x] Scale boiler outlet production by boiler height: active burner units × tank height
- [x] Gate scaled steam production by measured water supply at 10 mB/t per steam unit
- [x] Split one boiler's steam budget across all attached boiler outlets so multiple outlets cannot duplicate output

Implementation notes:

- Create's `BoilerData.BoilerFluidHandler` records water supply rate; it does not expose a stored steam inventory. Use `BoilerData` as the source of truth.
- Create's mechanical pump range is exposed through `FluidPropagator.getPumpRange()`, but our outlet should have its own configurable default target of 30 blocks.
- The outlet is a boiler pressure source, not a general-purpose pump.
- The outlet applies Create pipe pressure for normal pipe flow rendering and keeps a bounded `IFluidHandler` transfer as a no-drain fallback.
- The outlet production model is unit-based: `10 mB/t = 1 steam unit = 16,384 SU when consumed by an engine`.
- `BoilerData.activeHeat` and `BoilerData.getMaxHeatLevelForWaterSupply()` are multiplied by boiler controller height for pipe-fed steam production. This keeps taller boilers from being incorrectly capped by a single-layer water budget.

### Phase 6: Steam Inlet and Pipe-Fed Engine

**Goal**: Let cylinders consume piped `steam` and generate rotation remotely from the boiler room, while preserving direct compact mode as a fallback.

- [x] Add `steam_inlet` block, item, block entity, blockstate/model/item model/loot/lang/tags/creative entry
- [x] Allow a valid cylinder ring to be either 16 `steam_cylinder` blocks or 15 `steam_cylinder` blocks plus exactly 1 `steam_inlet` occupying any shell slot
- [x] `SteamInletBlockEntity` accepts only `steam` through an input-only `IFluidHandler` while assembled
- [x] Inlet caches assembled ring origin and cylinder root, and clears its link on disassembly
- [x] Piston head detects the linked steam inlet from the assembled ring
- [x] If usable inlet steam is available, piston head consumes `steam` and calculates output from steam consumption rate
- [x] If no valid inlet is present, existing direct compact boiler mode remains unchanged
- [x] Add goggle overlays for source mode: direct boiler vs piped steam
- [x] Verify: remote boiler outlet → Create pipes → steam inlet → engine rotation
- [x] Verify: steam storage buffer can run the engine briefly after boiler output stops
- [x] Verify: no steam/no inlet stops pipe-fed output without breaking direct compact mode

### Phase 7: Aeronautics/Sable Compatibility

**Goal**: make Full Steam Ahead engine blocks assemble and move as one coherent Create/Simulated structure without making Aeronautics, Simulated, or Sable required dependencies.

- [x] Keep Aeronautics, Simulated, and Sable optional at runtime; no hard compile dependency
- [x] Add optional `simulated` dependency metadata alongside the existing optional Aeronautics/Sable entries
- [x] Add `create:safe_nbt` entries for Full Steam Ahead block entities that need saved boiler, inlet, steam, or kinetic state preserved through contraption movement
- [x] Register Create `BlockMovementChecks` for all Full Steam Ahead engine blocks
- [x] Movement rules: engine blocks are movable, not brittle, supportive, and attached to adjacent Full Steam Ahead engine blocks
- [x] Movement rules: bottom cylinder/inlet shell blocks attach downward to Create Fluid Tank boilers; `boiler_outlet` attaches to its tank side and output pipe side
- [x] Register Simulated `SimBlockMovementChecks` reflectively when the `simulated` API is present, using the same attachment rules and an additional-block hook for assembly robustness
- [x] Verify automated build/resources/JSON checks without Aeronautics, Simulated, or Sable installed
- [x] Test engine inside a Sable/Aeronautics sublevel powering Aeronautics propellers
- [x] Verify NBT, kinetic state, steam buffer state, and boiler/inlet links survive assembly/disassembly and world reload

### Phase 8: Rendering and Ponder

**Goal**: turn the working machine into a readable Create-style machine without changing balance or mechanics.

Phase 8 is visual/presentation only. It must not change steam generation, output tables, multiblock rules, movement compatibility, recipes, or config.

- [x] Remove the old inert `flywheel` and `governor` placeholders from registration, creative tab, source, assets, loot tables, tags, and lang
- [x] Add client-only bootstrap under `dev.gustavo.fullsteamahead.client`; never load client/Flywheel/Ponder classes from dedicated-server common code
- [x] Add `FullSteamPartialModels` for dynamic piston/crank partials under `assets/full_steam_ahead/models/block/partial/`
- [x] Initial Create-style model pass using Create copper/brass/andesite/shaft textures where possible
- [x] Replace flickering multipart placeholders with stable non-overlapping proxy models
- [x] Add Blockbench handoff guide for final static models and animated partials
- [ ] Add cylinder visual states that identify ring position clearly: unassembled shell, assembled lower shell, assembled upper cap, inlet face, and bore-facing side pieces
- [x] Add piston static models for unassembled placement; assembled piston/head block geometry is rendered dynamically from the piston head block entity
- [x] Add a `PistonHeadAnimation` math helper shared by Flywheel and fallback renderer
- [x] Add `PistonHeadVisual` using Flywheel `SimpleBlockEntityVisualizer`, `TransformedInstance`, and `PartialModel`; drive it from `KineticBlockEntityRenderer.getAngleForBe(...)` on the linked shaft
- [x] Add `PistonHeadRenderer` fallback for non-visualized rendering so piston motion is still visible if Flywheel visualization is disabled
- [x] Remove the custom `crankshaft` block and use a regular Create shaft as the player-facing output
- [x] Expose minimal client-safe getters on `PistonHeadBlockEntity`: assembled state, source mode/running state, active speed, ring origin, inlet position, and shaft position
- [x] Hide or simplify static assembled piston block geometry so it does not fight the moving visual
- [x] Add `piston_head` as a separate structural block in the lower cylinder bore
- [x] Animate the actual `piston_head` and `piston` body up and down from linked shaft phase, using Flywheel and fallback rendering
- [x] Add horizontal piston orientation so the piston can be placed north-south or east-west and assembled piston visuals align their bolt axis to the linked shaft axis
- [x] Apply the textured v3 Blockbench `piston_head` model and embedded texture while preserving the existing hitbox
- [x] Replace `piston_head` with the `Steam_Piston_Head.bbmodel` visual and matching model-derived hitbox
- [x] Replace the piston body with `Steam_Piston_Body.bbmodel` and add animated `Steam_Connecting_Rod` plus `Steam_Crank` partials using shaft-phase slider-crank motion
- [x] Correct modeled crank/linkage phase so `Steam_Crank` rotates from its authored pin-below-shaft pose using the raw linked shaft angle
- [x] Correct crank/linkage rotation plane to the Create-style frontal plane instead of switching to lateral shaft-axis spin
- [x] Apply linkage-frame yaw before local crank/rod rotation so the throw moves front/back relative to an X-axis shaft instead of sliding along the shaft
- [x] Replace piston body, connecting rod, and crank visuals with the `new_models/` Blockbench set; retune hitbox, crank radius, and local-X linkage transforms to match the new geometry
- [x] Render the hidden powered shaft with Create's caps-only `POWERED_SHAFT` partial instead of the full shaft model inside the crank
- [x] Rotate the piston model texture mapping 90 degrees at the asset level so static and animated piston visuals align with the connecting rod
- [x] Replace boiler outlet placeholder art with `Steam_outlet.bbmodel`, including embedded texture, directional blockstate rotations, and model-derived hitbox
- [x] Replace piston head art with `piston_head_LATEST.bbmodel` while preserving the existing model-derived hitbox
- [x] Correct Blockbench texture UV conversion for 32x32 and 64x64 model textures so the outlet and piston head sample their embedded textures instead of atlas spillover
- [x] Fix dynamic piston/head lighting by relighting each moving partial at its own world position instead of using one block entity light value
- [x] Add running steam puffs from the cylinder top, timed to crank phase and scaled by RPM/source mode
- [x] Add rhythmic steam sound using Create's normal `STEAM` sound event, slightly louder than the vanilla Create steam engine
- [x] Offset adjacent engine piston animation phases by shaft-line position so multi-cylinder rows alternate instead of moving in lockstep
- [x] Add inert `engine_telegraph` block scaffold, model, textures, loot, lang, creative entry, mining tags, and directional placement
- [x] Polish `engine_telegraph` contraption rendering with cutout/AO model settings and a detailed model-derived hitbox
- [x] Add `stepped_lever` scaffold with analog redstone state, goggle tooltip, block entity renderer, model assets, loot, recipe, tags, lang, and creative entry
- [x] Apply the first custom Blockbench `steam_cylinder` texture/model prototype to both unassembled and assembled cylinder shell models
- [x] Split `Assembled_cylinder_ring_prototype.bbmodel` into section-aware assembled `steam_cylinder` models and matching slim assembled hitboxes
- [x] Regenerate assembled cylinder section models, texture, and slim hitboxes from the symmetrical Wednesday Blockbench revision
- [x] Correct assembled cylinder section UV clipping so the split in-game models match the Blockbench texture orientation
- [x] Apply the latest hand-authored assembled cylinder ring texture PNG to the implemented runtime atlas
- [x] Apply the `_v2` hand-authored assembled cylinder ring texture PNG revision
- [x] Reuse the 16 assembled cylinder subunit models for progressive `Cylinder Wall` construction visuals
- [x] Infer partial cylinder-wall section visuals from connected wall/inlet groups without enabling engine mechanics
- [x] Apply the exposed-parts fix: v3 assembled cylinder atlas plus generated cut faces for all 16 section models
- [x] Replace the standalone `Cylinder Wall` block with the v1 textured wall model and matching hitbox
- [x] Refresh adjacent Create pipe connections when `steam_inlet` fluid capability changes during cylinder ring disassembly/reassembly
- [x] Add straight-wall partial construction mode so connected cylinder walls stay fence-like until a horizontal turn implies ring intent
- [x] Replace assembled cylinder ring section models and hitboxes with the fixed `Steam_Cylinder_all_faces_FIXED_V2.bbmodel` geometry
- [x] Replace `steam_inlet` placeholder block/assembled models with the `Steam_Inlet.bbmodel` textured model and matching directional hitboxes
- [x] Protect already-assembled neighboring cylinder rings during local connectivity refresh so adjacent full engines do not deform each other
- [ ] Add Ponder plugin and scenes after visual models settle: direct compact engine, boiler outlet pressure, steam storage/pipes, steam inlet, Aeronautics ship use
- [ ] Verify visuals on standalone world, pipe-fed world, and Aeronautics assembled sublevel
- [ ] Verify dedicated server startup remains clean with no client-class loading
- [ ] Verify resource reload/F3+T does not break partial models or visuals
- [ ] Verify old worlds with existing engines still load and animate

Deferred idea after visuals: inline shared-wall cylinder banks, where adjacent cylinders can share one cylinder wall block instead of requiring independent 3x3 rings.

### Phase 9: Balance, Config, Recipes

- [ ] Server config for: base capacity, steam production rate, boiler outlet pressure range, direct mode enablement, max piston height
- [ ] Recipes for active blocks balanced against vanilla Create steam engine
- [ ] JEI/EMI display

### Phase 10: Hardening and Release

- [ ] Dedicated server startup test
- [ ] World reload test
- [ ] Schematic placement test
- [ ] Save/load with kinetic state
- [ ] Performance: no rescan every tick; scan bounded and cached
- [ ] Crash audit: null levels, unloaded chunks, missing optional mods, invalid NBT

---

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Create `BoilerData` API changes | Read through `FluidTankBlockEntity`; isolate in one method |
| Create pipe pressure internals are brittle | Keep boiler outlet pressure code isolated; fallback to bounded `IFluidHandler` push |
| Steam storage becomes an exploit | `boiler_outlet` only generates from valid active boilers and never auto-pumps stored steam |
| Pipe-fed mode loses burner-count metadata | Treat steam as a pressure manifold in pipe-fed mode; keep exact burner-count RPM table for direct compact mode |
| Cylinder ring scan too expensive | Run only on placement/removal, not every tick; cache result |
| Piston animation desync | Drive animation entirely from linked shaft rotation angle on client |
| Sable assembly splits engine parts | Register Create and Simulated movement checks early |

---

## Art Direction

Stay as close as possible to base Create's visual language:

- Copper and brass for the cylinder casing (warm, industrial)
- Dark iron/steel for the piston rod
- Exposed normal Create shaft geometry at the top output
- Riveted panel texture language on the cylinder outer faces
- Goggle overlays follow Create's own overlay style exactly
