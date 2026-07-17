# Create: Full Steam Ahead — Design Plan

Last updated: 2026-07-16

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
2. **Pipe-fed mode** — Phase 5 adds storable `steam` fluid. Valid Create boilers can feed that steam through either a `boiler_outlet` block or direct Create Fluid Pipe connections on top-layer tank faces.

The direct compact boiler must be at least 3×3×1 (9 fluid tank blocks) to support a full cylinder frame above it.

### Steam is a storable fluid

`steam` becomes a real NeoForge/Create-compatible fluid. It can be stored in Create Fluid Tanks and moved through Create Fluid Pipes. It is not a replacement for water, and it is not produced by ordinary tanks full of stored steam.

Current v1 rule: the pressure source is the active Create Fluid Tank boiler itself. Steam ports only expose that boiler vessel to pipes. A steam port is either a `boiler_outlet` block or a direct Create Fluid Pipe connection on a top-layer boiler tank face. A sealed active boiler with no ports still stores generated steam, builds pressure, can be read by goggles/Display Links, can be relieved by boiler-mounted relief valves, and can burst. Stored steam in normal tanks may be moved by normal Create logistics, but it must not get free boiler pressure.

Direct boiler pipe output is implemented as a wrapped Fluid Tank capability on eligible active boiler faces. The wrapper exposes the boiler vessel's stored `steam` while preserving Create's boiler water-supply accounting, Fluid Tank water fill behavior, pipe valves, stored steam tanks, and boiler visuals. Valid direct faces are the `UP` face and horizontal faces of top-layer tank blocks. Bottom faces and lower tank layers are not steam outputs. All physical outlets and direct pipe ports attached to one boiler split that boiler's shared production budget.

For v1, `steam` should be non-placeable and should not need a bucket. It exists for tanks, pipes, gauges, and engine consumption.

### Flywheel and governor removed

The old inert `flywheel` and `governor` placeholders have been removed from registration, creative inventory, Java source, assets, tags, loot tables, and lang. Do not re-add them without a new design pass in this document.

### Engine orientation: vertical upright or inverted in v1

The cylinder, piston column, and shaft link remain vertical in v1. Upright engines support both direct compact boiler mode and pipe-fed mode. Upside-down engines are pipe-fed only and require one active assembled `steam_inlet`.

- Steam Cylinder frame
- Piston column running vertically through the cylinder
- A regular horizontal Create shaft at the stroke end above upright engines or below inverted engines

In direct compact mode, the Create Fluid Tank boiler still sits directly below the cylinder and Blaze Burners still heat upward below the tank. In pipe-fed mode, the boiler can be remote, but the engine assembly itself is still vertical.

Horizontal orientations are deferred to a future version.

### Block list

| Block | Class base | Role |
|---|---|---|
| `steam_cylinder` | `Block + IBE<SteamCylinderBlockEntity>` | Forms the 3×3×2 hollow casing ring around the piston. Self-assembles. |
| `piston_head` | `Block + IBE<PistonHeadBlockEntity>` | Physical piston head and engine brain. Validates the stack, reads steam/boiler state, and powers the linked shaft. |
| `piston` | `Block` | Physical piston body block above the piston head. Animated when running. |
| `powered_shaft` | `PoweredShaftBlock + FullSteamPoweredShaftBlockEntity` | Hidden internal replacement for a player-placed Create shaft. Provides kinetic output while cloning/dropping as a normal shaft. |
| `boiler_outlet` | `Block + SmartBlockEntity` | Optional explicit port attached to a Create Fluid Tank boiler. Generates `steam` and provides pressure into pipes while sharing budget with any direct boiler pipe ports. |
| `steam_relief_valve` | `Block + SmartBlockEntity` | Top/side-mounted boiler safety valve. Auto-vents near burst pressure or vents on redstone command. |
| `steam_inlet` | `Block + SmartBlockEntity` | Phase 6 block. Replaces a cylinder shell block in the 3×3×2 ring. One inlet is active and accepts `steam`; one optional second inlet can be passive for visual symmetry. |
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
- Pipe-fed mode: 14-15 × `steam_cylinder` + 1 active `steam_inlet`, plus up to 1 passive `steam_inlet` for visual symmetry
- 1 × `piston_head` in the lower cylinder bore center
- 1-3 × `piston` above it, with the highest piston body carrying the rod connection
- 1-3 × empty stroke blocks above the highest piston body
- 1 × regular horizontal Create `shaft` above the empty stroke space stack
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

In pipe-fed mode, the Create Fluid Tank boiler controller reads its own `BoilerData`, creates stored `steam` from valid active heat and water supply, and tracks pressure even when sealed. Steam ports can be explicit `boiler_outlet` blocks or direct pipes attached to valid top-layer tank faces. They expose the existing Create boiler vessel to Create pipes; they do not create independent steam.

Create Fluid Tank boiler goggles may show Full Steam Ahead pressure, stored steam, production, and status lines, but they must not label those lines with a separate mod heading and must not report Full Steam Ahead multiblock engines as vanilla Create steam-engine stress capacity. Boiler-mounted Display Links expose the same `Steam Network` readout modes as a `boiler_outlet`.

### Layer 2 — The Cylinder Ring (our auto-assembly)

The 3×3×2 ring self-assembles when all 16 shell positions are filled correctly. In direct compact mode all 16 positions may be `steam_cylinder`. In pipe-fed mode one shell position is the active `steam_inlet`, with an optional second passive `steam_inlet` for symmetry and the remaining shell positions being `steam_cylinder`. Inlets can occupy any non-shared shell slot. The moment the 16th shell block is placed and the ring is complete:

- All 16 shell blocks flip `ASSEMBLED = true`
- Connected textures activate (inner bore faces appear, top ring shows cylinder head cap)
- The cylinder block entity designated as root scans the 9 blocks directly below the bottom ring layer for Create `FluidTankBlockEntity` instances
- If a valid boiler is found, the cylinder root caches the boiler position and starts reading `BoilerData`
- If no valid boiler is below, the cylinder assembles visually but shows a "no steam source" indicator (goggle overlay, no particles)

The cylinder ring also disassembles visually if any of the 16 shell blocks is removed, even if it has a valid boiler or inlet.

**Shape constraint**: The ring must be exactly 3×3 in cross-section with the centre 1×1 column hollow. No other shape is accepted in v1. More than two inlets in one ring are invalid. Inlets cannot occupy shared-wall positions.

**Boiler detection**: The bottom ring layer checks positions (0,−1,0) through (2,−1,2) relative to its south-west corner for `FluidTankBlockEntity`. It needs at least the 8 positions directly below the ring blocks (positions matching the cylinder shell footprint) to be valid fluid tanks. The boiler does not need to be assembled in any Create-specific sense; it just needs to be fluid tanks with Blaze Burners below.

### Layer 3 — The Piston Head and Shaft Link (kinetic assembly)

When the `piston_head` block entity revalidates, it scans upward along its Y axis:

1. Expects one to three contiguous `piston` blocks directly above it
2. Expects one to three empty stroke blocks above the highest piston body
3. Expects a horizontal regular Create shaft or hidden Full Steam Ahead powered shaft above the empty stroke space stack
4. Expects a valid assembled `SteamCylinder` ring around the lower piston head and upper piston body
5. Checks for either a valid Create fluid tank layer directly below the ring's bottom layer, or one active assembled `steam_inlet` occupying a cylinder shell slot, but a missing steam source does not block mechanical assembly

By default, the shaft gap matches the piston body count: one piston body uses one empty stroke space, two bodies use two, and three bodies use three. Manually placed shafts are also accepted at any one-to-three-block stroke gap after the final piston body. The connecting rod and crank visual tier follow the actual shaft gap, while the piston body count only controls how many piston body blocks render.

If all mechanical checks pass: the piston head stores references to the cylinder root, inlet, boiler, and shaft. If the player placed a normal Create shaft, it is swapped to `full_steam_ahead:powered_shaft`. The piston/head/linkage visuals assemble around the shaft even with no active steam source, matching Create's own passive linkage behavior. Direct compact mode reads boiler heat/water when present. Pipe-fed mode consumes stored `steam` from the inlet when present. If both sources exist, piped steam is preferred while available, with direct boiler output as fallback. If neither source can supply steam, the engine remains assembled but generates 0 RPM and 0 SU.

If any mechanical check fails: the piston head clears assembly, restores the hidden powered shaft back to a normal Create shaft when it owns that shaft, and shows an incomplete-structure goggle overlay.

**Revalidation triggers:**
- Any `piston` or `piston_head` block placed or removed
- Any `steam_cylinder` block placed or removed within the expected positions
- Any Create fluid tank block placed or removed directly below the cylinder's bottom ring
- Piston head block entity loads from disk or lazy-ticks after the player places the shaft
- Looking at the completed piston body with a Create shaft in hand uses Create's placement-helper preview to show the default shaft ghost at the top-link position, then places the shaft into that position on right-click

Pipe-fed mode accepts either the direct boiler below the ring or a valid active steam inlet occupying one assembled cylinder shell slot. Direct compact mode must remain working during the transition.

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
- `getGeneratedSpeed()`: returns the global physics-derived linear RPM when assembled and steam is available; 0 otherwise.
- `calculateAddedStressCapacity()`: returns capacity proportional to available direct boiler heat or consumed `steam`.
- If the engine becomes invalid, the piston head clears shaft power and restores this block to a normal Create shaft.

### `BoilerOutlet` (`boiler_outlet`)

- Block entity: `BoilerOutletBlockEntity extends SmartBlockEntity`
- Placed on a Create Fluid Tank block face. The block's back side must touch a `FluidTankBlockEntity`; the front side outputs steam. It remains useful as an explicit port, visual device, and easy display/goggle target even though direct boiler pipe ports now exist.
- Validates the attached tank's controller and reads its `BoilerData`.
- Counts as an attached boiler device in our Create boiler integration so Create's own tank visuals and compact boiler sizing activate even for small outlet-only boilers.
- The physical Create Fluid Tank boiler vessel generates `steam` fluid only from valid boiler heat and water supply. Steam ports expose that vessel. They must never drain or re-pressurize steam from normal storage tanks.
- Steam unit model:
  - `1 steam unit = 10 mB/t steam`
  - Normal active burner heat contributes 1 burner unit; Blaze Cake heat contributes 2 burner units
  - Total boiler steam units = `min(active burner units, water supply heat level) × boiler height`
  - `3x3x1` with 9 normal burners produces 9 units, or 90 mB/t
  - `3x3x6` with 9 normal burners produces 54 units, enough for six full normal engines
  - `3x3x6` with 9 Blaze Cake burners produces 108 units, enough for twelve full pipe-fed engines
- Pipe-fed steam is generic stored steam in v1: one engine consumes at most 9 units or 90 mB/t and produces at most 147,456 SU. Blaze Cakes increase total steam-stream capacity; they do not make one pipe-fed engine exceed normal full output without a future superheated-steam design.
- Multiple steam ports attached to one boiler split the same total steam unit budget in a stable position order; they must not duplicate steam. Steam ports are `boiler_outlet` blocks plus eligible direct boiler pipe faces. With no ports, the full budget enters the sealed boiler vessel and raises pressure there.
- Direct boiler pipe ports are valid only on the `UP` face or horizontal faces of top-layer Create Fluid Tank boiler blocks. They expose steam through a wrapped Fluid Tank capability, reject steam insertion, and keep ordinary water fill delegated to Create's own boiler handler.
- Connected pipe networks split active boiler steam evenly across reachable active `steam_inlet` blocks, capped at 90 mB/t per active inlet from all sources combined, before sending surplus to passive storage. Passive decorative inlets are closed visual endpoints and do not add demand, storage, or pressure volume. Multiple boilers on the same pipe network contribute additive budgets; no single boiler or steam port may duplicate steam.
- Network pressure is computed from stored steam mass, weighted steam temperature, and network volume. Active boiler tank controllers expose pressure/status to goggles and Display Links whether sealed, connected by direct pipes, or connected through `boiler_outlet` blocks.
- Boiler outlet pressure traversal respects Create pipe blockers such as closed fluid valves by consulting each pipe behaviour's flow permission before crossing a side. A closed valve blocks steam; it is not treated as an open vent.
- Exposes an output-only `IFluidHandler` for `steam`.
- Applies pressure to the connected Create pipe network so the player does not need a mechanical pump directly at the boiler outlet.
- Open-pipe visual: when outlet or pipe end vents to air, spawn custom translucent steam leak particles inspired by TFMG-style gas visuals.
- Steam fluid visual: keep the tinted vanilla water render path for tanks and pipes, with explicit stack/world render overrides and enough outlet buffer reserve for Create's native pipe flow renderer to keep showing steam.
- Default pressure range target: 30 blocks, controlled by server config.
- Goggle overlay: boiler linked/missing, port steam units, total boiler steam units, attached steam port count, steam production rate, internal buffer, output pressure state.

### `SteamReliefValve` (`steam_relief_valve`)

- Block entity: `SteamReliefValveBlockEntity extends SmartBlockEntity`
- Placed directly on the top or any horizontal side of a Create Fluid Tank boiler block. It links to the tank controller behind its attached face and protects every steam pipe network fed by boiler outlets on that same physical boiler. Bottom mounting is intentionally unsupported so it does not conflict with Blaze Burners.
- It is not a pipe endpoint and does not replace `boiler_outlet`; it is a boiler-mounted safety device.
- If the boiler has no active outlet/network, it still sees the sealed boiler vessel pressure and can relieve it before burst.
- Automatic mode opens at `steamReliefValve.openPressure` and stays open until pressure falls below `steamReliefValve.closePressure`.
- Redstone power forces the valve open and drains toward the same atmospheric target used by open pipe ends.
- One valve starts from `steamReliefValve.ventRateMb` baseline vent capacity, then scales effective relief capacity with the active boiler network's production so a valid safety valve can outrun the boiler before burst pressure. Multiple valves on the same boiler share the same physical boiler/network pressure and add relief capacity without duplicating steam production.
- Visuals: cap lifts, handwheel spins while venting, Create-style steam particles/sound emit from the vent collar.
- Goggle overlay: boiler link, valve state, current pressure, open threshold, last vented amount, and peak pressure while sneaking.

### `SteamInlet` (`steam_inlet`)

- Block entity: `SteamInletBlockEntity extends SmartBlockEntity`
- Occupies one shell slot in the assembled cylinder ring, replacing one `steam_cylinder` block. It can be placed in any of the 16 shell positions.
- A v1 ring accepts 0, 1, or 2 inlets. At most one inlet is active; the optional second inlet is passive for visual symmetry.
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
- The active inlet accepts only `steam` through an input-only `IFluidHandler`. A passive inlet exposes an inert no-fill handler so pipes can connect visually without becoming a second consumer.
- The active inlet stores a small local steam buffer. The buffer is not a pressure source and cannot be drained by external blocks.
- When the ring assembles, all inlets cache the ring origin and cylinder root; deterministic selection marks one inlet active and any second inlet passive. When the ring disassembles, each inlet clears that link and stops accepting steam.
- The piston head prefers consuming steam from the active linked inlet. If no usable inlet steam exists and a direct boiler is present, direct compact mode remains the fallback.
- Pipe-fed balance maps network pressure and consumed steam rate to output:
  - 10 mB/t consumed steam = 1 steam unit = 16,384 SU at rated pressure, with partial mB/t contributing proportional SU
  - Maximum consumed steam for one pipe-fed engine = 90 mB/t = 9 heat units = 147,456 SU
  - Output factor is `min(pressureFactor, flowFactor)`
  - RPM scales linearly from a 1 RPM positive-output floor to the configured 64 RPM maximum
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
      EngineValidator.java             ← vertical upright/inverted engine validation
      PistonSection.java               ← enum: INSIDE_LOW, INSIDE_HIGH, PROTRUDE_LOW, PROTRUDE_HIGH
    shaft/
      FullSteamPoweredShaftBlock.java  ← hidden replacement for player-placed Create shaft
      FullSteamPoweredShaftBlockEntity.java
    steam/
      BoilerOutletBlock.java
      BoilerOutletBlockEntity.java
      SteamReliefValveBlock.java
      SteamReliefValveBlockEntity.java
      SteamInletBlock.java
      SteamInletBlockEntity.java
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
| RPM range | 1-64 for positive output; 0 with no output |
| Default/max RPM | 64 |
| Active burner count | 0-9 fired Blaze Burners under the 3x3 boiler footprint |
| Heat units | 1 per normal fired burner, 2 per Blaze Cake burner |
| Full engine rating | 147,456 SU at 64 RPM |
| Full engine flow | 90 mB/t steam |
| Rated pressure | 1.0 MpN/m² |
| Warning pressure | 1.5 MpN/m² |
| Relief valve opens | 2.2 MpN/m² |
| Relief valve closes | 1.7 MpN/m² |
| Relief valve baseline vent rate | 720 mB/t per valve |
| Burst pressure | 2.5 MpN/m² |

Direct compact formula:

```
usable_heat       = min(active_heat, water_limited_heat, size_limited_heat)
production_mbpt   = usable_heat * boiler_height * steam_per_heat_unit
consumed_mbpt     = min(production_mbpt, full_engine_flow_mb)
pressure_factor   = clamp(production_mbpt / full_engine_flow_mb, 0, 1)
flow_factor       = clamp(consumed_mbpt / full_engine_flow_mb, 0, 1)
output_factor     = min(pressure_factor, flow_factor)
capacity_su       = full_engine_su * output_factor
output_speed_rpm  = output_factor == 0 ? 0 : max(1, max_rpm * output_factor)
```

Direct compact mode is a simplified compatibility mode for upright engines sitting on a boiler. It does not store pressure, burst, or let Blaze Cakes overdrive one engine past the normal full-engine rating. Blaze Cake surplus belongs to pipe-fed networks, where it can feed additional engines or build pressure if trapped.

Pipe-fed steam production:

```
boiler_heat_units     = min(active_heat, water_limited_heat, size_limited_heat)
steam_production_mbpt = boiler_heat_units * boiler_height * steam_per_heat_unit
```

The `10 mB/t per heat unit` value intentionally mirrors Create's boiler water-supply threshold (`BoilerData.getMaxHeatLevelForWaterSupply()` is based on 10 mB/t per heat level). It keeps direct and pipe-fed output comparable: 9 normal heat units at height 1 produce 90 mB/t steam, enough for one full 147,456 SU engine. A taller boiler or Blaze Cake burners produce surplus steam for more engines or for pressure buildup in closed networks.

Pipe-fed pressure formula:

```
pressure_pn_m2    = gas_constant * stored_steam_mb * weighted_temperature_k / network_volume_m3
pressure_factor   = clamp(pressure / rated_pressure, 0, 1)
requested_flow    = full_engine_flow_mb * pressure_factor
fair_flow_cap     = steam_available_this_tick / reachable_engine_count
consumed_mbpt     = min(requested_flow, fair_flow_cap, inlet_buffer)
flow_factor       = clamp(consumed_mbpt / full_engine_flow_mb, 0, 1)
output_factor     = min(pressure_factor, flow_factor)
capacity_su       = full_engine_su * output_factor
output_speed_rpm  = output_factor == 0 ? 0 : max(1, max_rpm * output_factor)
```

Important balance distinction: stored steam does not remember which burner produced it. The pipe network stores steam mass, network volume, and weighted temperature. Multiple boilers can feed one network, but one boiler's production is split across all steam ports attached to that boiler and cannot be duplicated.

Linear RPM reference with the default `max_rpm = 64`:

| Output factor | RPM |
|---:|---:|
| 0 | 0 |
| >0-0.015625 | 1 |
| 0.25 | 16 |
| 0.50 | 32 |
| 0.75 | 48 |
| 1.00 | 64 |

Unfired Blaze Burners only provide passive heat in Create and must produce `0` output for this engine. Blaze Cakes count as 2 heat units, increasing steam production and temperature; they do not let a single engine exceed the configured full-engine rating. Water supply remains required for output.

Pipe-fed mode must not make one engine exceed the configured full-engine rating. Steam storage is a buffer/logistics feature, not a free multiplier. Surplus steam should power additional engines, fill passive storage, vent, or build pressure depending on the pipe network.

Most key balance constants are now server config values. Any new pressure, output, hazard, or vent constants added later should also be configurable before release.

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
- [x] Upward scan from piston head: one to three piston bodies → one to three empty stroke spaces → horizontal Create shaft; ring and boiler/inlet are validated around/below the piston head
- [x] If valid: store cylinder root, inlet, boiler, and shaft refs; power the hidden shaft block entity
- [x] Initial Phase 4 `getGeneratedSpeed()` followed exact active-burner RPM tiers: 1-2 = 16 RPM, 3-4 = 32 RPM, 5-8 = 48 RPM, 9 = 64 RPM. Phase 12 superseded burner-count output with pressure/flow factors, and Phase 18 replaced the remaining RPM tiers with one linear mapping.
- [x] Initial Phase 4 `calculateAddedStressCapacity()` followed exact burner SU output, including Blaze Cake doubling. Phase 12 later capped one engine at the configured full-engine rating and moved surplus heat into pipe-fed production/pressure.
- [x] Read `BoilerData` from the `FluidTankBlockEntity` at boiler position each server tick
- [x] Make Create's own `BoilerData.evaluate()` count assembled Full Steam Ahead engines
- [x] Treat 3x3x1 tank boilers as the compact optimal size when a Full Steam Ahead engine is attached
- [x] Require active fired Blaze Burner heat; passive/unfired heat produces no rotation
- [x] Scan the 3x3 Blaze Burner footprint directly so mixed normal/Blaze Cake burners contribute exact per-burner SU
- [x] Add piston block state updates: set `ASSEMBLED` and section/head state on the moving piston stack when piston head validates
- [x] Add visible placeholder models for assembled piston section states
- [x] Add revalidation on neighbour changes
- [x] Add goggle overlay: assembly status, active burners, heat units, water supply, RPM, SU
- [x] Historical Phase 4 verification: no passive output, 1-9 normal fired burners matched the original SU/RPM table, and Blaze Cake burners doubled SU individually
- [ ] Optional hardening verify: break piston → shaft stops; break boiler → shaft stops; restore/reload → state recovers

Implementation note: Phase 4 uses a small Create compatibility mixin so `BoilerData.evaluate()` recognizes valid Full Steam Ahead piston-head engines as attached steam engines. This lets Create's own Fluid Tank switch to active boiler visuals/capabilities and lets the compact 3x3x1 boiler footprint behave as the intended v1 boiler size.

### Phase 5: Steam Fluid and Boiler Outlet

**Goal**: Add storable `steam` fluid and boiler steam ports that generate and pressure-feed steam into Create pipes. The current direct compact engine must continue working.

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
- [x] Verify: direct compact engine still works after introducing pipe-fed steam
- [x] Verify: boiler outlet produces steam only on valid active boilers, fills Create tanks through pipes, and does not auto-pump from stored steam tanks
- [x] Apply Create `FluidTransportBehaviour` pressure so generated steam is visible in connected Create pipes
- [x] Register steam open-pipe effect and outlet vent particles for open/unconnected steam leaks
- [x] Verify: steam visibly flows through pipes and open pipe ends vent steam particles
- [x] Enhance steam leak visuals with a custom translucent particle sheet for boiler outlets and open pipe ends
- [x] Restore tinted vanilla water fluid visuals for non-placeable steam in tanks and pipes
- [x] Make boiler outlet pressure traversal respect Create fluid valves so closed valves block steam instead of being bypassed
- [x] Scale boiler outlet production by boiler height: active burner units × tank height
- [x] Gate scaled steam production by measured water supply at 10 mB/t per steam unit
- [x] Split one boiler's steam budget across all attached boiler outlets so multiple outlets cannot duplicate output
- [x] Add direct boiler pipe output from active Create Fluid Tank boilers on top-layer `UP` and horizontal faces
- [x] Split one boiler's steam budget across both `boiler_outlet` blocks and direct boiler pipe ports so direct pipes cannot duplicate output
- [x] Register active boiler controllers as `Steam Network` Display Link sources for outlet-free direct pipe setups
- [x] Make active boiler controllers store steam and build pressure even with no attached outlet or direct pipe
- [x] Append Full Steam Ahead pressure, stored steam, production, and status lines to Create Fluid Tank boiler goggle tooltips
- [x] Resolve Display Link sources on any Fluid Tank block to the tank controller so multiblock child tanks do not report empty local state

Implementation notes:

- Create's `BoilerData.BoilerFluidHandler` records water supply rate; it does not expose a stored steam inventory. Use `BoilerData` as the source of truth.
- Create's mechanical pump range is exposed through `FluidPropagator.getPumpRange()`, but our outlet should have its own configurable default target of 30 blocks.
- Steam ports are boiler pressure sources, not general-purpose pumps.
- The outlet applies Create pipe pressure for normal pipe flow rendering and keeps a bounded `IFluidHandler` transfer as a no-drain fallback. The fallback must not drain the outlet below its pipe-flow reserve, because Create needs live source fluid to keep pipe/tank contents visible.
- Direct boiler pipe ports wrap the Fluid Tank's existing fluid capability only on valid active-boiler faces. Non-steam fill/drain delegates to Create's handler, steam insertion is rejected, and steam drain comes from the boiler controller vessel.
- With no steam ports, the boiler controller creates a sealed one-boiler pressure network. Relief valves, overpressure warnings, Display Links, goggles, projectile rupture, and burst logic use that same pressure state.
- The outlet production model is unit-based: `10 mB/t = 1 steam unit = 16,384 SU when consumed by an engine`.
- `BoilerData.activeHeat` and `BoilerData.getMaxHeatLevelForWaterSupply()` are multiplied by boiler controller height for pipe-fed steam production. This keeps taller boilers from being incorrectly capped by a single-layer water budget.

### Phase 6: Steam Inlet and Pipe-Fed Engine

**Goal**: Let cylinders consume piped `steam` and generate rotation remotely from the boiler room, while preserving direct compact mode as a fallback.

- [x] Add `steam_inlet` block, item, block entity, blockstate/model/item model/loot/lang/tags/creative entry
- [x] Allow a valid cylinder ring to be 16 `steam_cylinder` blocks, or 14-15 `steam_cylinder` blocks plus 1 active `steam_inlet` and up to 1 passive visual `steam_inlet`
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
- [x] Widen optional dependency metadata for current Aeronautics/Simulated `1.3.x` and Sable `2.x` packs while keeping the integrations guarded
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
- [x] Add a Create-style shaft placement helper: looking at a mechanically complete piston body with a Create shaft in hand previews and places the shaft at the required top-link position
- [x] Allow the shaft to be placed manually at one to three empty stroke spaces beyond the final piston body; rod/crank visuals now scale from that actual shaft gap
- [x] Allow a mechanically complete piston/head/ring/shaft structure to assemble its linkage with no steam source; steam availability only controls generated RPM/SU
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
- [x] Add a paintable bottom face UV island to the piston body source/runtime model
- [x] Apply the repainted embedded piston body texture from `steam_engine_piston_MINE_PERFECT.bbmodel`
- [x] Render the hidden powered shaft with Create's caps-only `POWERED_SHAFT` partial instead of the full shaft model inside the crank
- [x] Rotate the piston model texture mapping 90 degrees at the asset level so static and animated piston visuals align with the connecting rod
- [x] Replace boiler outlet placeholder art with `Steam_outlet.bbmodel`, including embedded texture, directional blockstate rotations, and model-derived hitbox
- [x] Replace piston head art with `piston_head_LATEST.bbmodel` while preserving the existing model-derived hitbox
- [x] Correct Blockbench texture UV conversion for 32x32 and 64x64 model textures so the outlet and piston head sample their embedded textures instead of atlas spillover
- [x] Fix dynamic piston/head lighting by relighting each moving partial at its own world position instead of using one block entity light value
- [x] Add running steam puffs from the cylinder top, timed to crank phase and scaled by RPM/source mode
- [x] Add rhythmic steam sound using Create's normal `STEAM` sound event, slightly louder than the vanilla Create steam engine
- [x] Offset adjacent engine piston animation phases by ordered powered-shaft bank index so engines sharing one shaft alternate one-up/one-down for both separated and shared-wall spacing
- [x] Remove stale inert `engine_telegraph` / Telegraph Stand block now that `stepped_lever` is the Engine Order Telegraph
- [x] Add `stepped_lever` scaffold with analog redstone state, goggle tooltip, block entity renderer, model assets, loot, recipe, tags, lang, and creative entry
- [x] Apply the first custom Blockbench `steam_cylinder` texture/model prototype to both unassembled and assembled cylinder shell models
- [x] Split `Assembled_cylinder_ring_prototype.bbmodel` into section-aware assembled `steam_cylinder` models and matching slim assembled hitboxes
- [x] Regenerate assembled cylinder section models, texture, and slim hitboxes from the symmetrical Wednesday Blockbench revision
- [x] Correct assembled cylinder section UV clipping so the split in-game models match the Blockbench texture orientation
- [x] Apply the latest hand-authored assembled cylinder ring texture PNG to the implemented runtime atlas
- [x] Apply the `_v2` hand-authored assembled cylinder ring texture PNG revision
- [x] Use a hidden assembled cylinder ring item model as the creative tab icon and tune its display scale to match neighboring tab icons
- [x] Reuse the 16 assembled cylinder subunit models for progressive `Cylinder Wall` construction visuals
- [x] Infer partial cylinder-wall section visuals from connected wall groups, including up to two inlets, without enabling duplicate engine mechanics
- [x] Apply the exposed-parts fix: v3 assembled cylinder atlas plus generated cut faces for all 16 section models
- [x] Replace the standalone `Cylinder Wall` block with the v1 textured wall model and matching hitbox
- [x] Refresh adjacent Create pipe connections when `steam_inlet` fluid capability changes during cylinder ring disassembly/reassembly
- [x] Add straight-wall partial construction mode so connected cylinder walls stay fence-like until a horizontal turn implies ring intent
- [x] Replace assembled cylinder ring section models and hitboxes with the fixed `Steam_Cylinder_all_faces_FIXED_V2.bbmodel` geometry
- [x] Replace assembled cylinder ring section models and runtime atlas with `Steam_Cylinder_all_faces_manually_painted_monday.bbmodel`
- [x] Apply the revised embedded texture from `Steam_Cylinder_all_faces_manually_painted_monday.bbmodel` while preserving the existing matching section geometry
- [x] Replace `steam_inlet` placeholder block/assembled models with the `Steam_Inlet.bbmodel` textured model and matching directional hitboxes
- [x] Protect already-assembled neighboring cylinder rings during local connectivity refresh so adjacent full engines do not deform each other
- [x] Separate per-block cylinder-wall UVs so each assembled section/partial is independently paintable (upper corners de-linked from lower corners; shared-wall end pieces de-linked) and normalize sub-pixel cap-face density
- [x] Apply the hand-repainted assembled cylinder ring and shared-wall atlases
- [x] Add an offline Blockbench explode/recompile tooling workflow (`tools/cylinder/`) for editing the sliced cylinder atlases without altering geometry
- [x] Add Ponder plugin (`FullSteamPonderPlugin`) and scenes on the Steam Cylinder: a staged pipe-fed engine assembly scene (`testing_ponder_v2`) and a shared-wall cylinder-bank scene (`scene_3`) revealing single → shared → bank with walls merging only as neighbours are placed
- [ ] Add remaining Ponder scenes: boiler outlet pressure, steam storage/pipes, steam inlet, Aeronautics ship use
- [ ] Verify visuals on standalone world, pipe-fed world, and Aeronautics assembled sublevel
- [ ] Verify dedicated server startup remains clean with no client-class loading
- [ ] Verify resource reload/F3+T does not break partial models or visuals
- [ ] Verify old worlds with existing engines still load and animate

### Phase 8.5: Upside-Down Pipe-Fed Engines

**Goal**: Allow the same vertical engine to assemble upside down for ship and aircraft builds while preserving all existing upright behavior.

- [x] Add vertical `facing=up/down` state to piston head, piston body, cylinder wall, and steam inlet blocks
- [x] Piston head and piston placement use stair-like vertical placement: underside/upper-half side placement faces down; otherwise faces up
- [x] Refactor engine validation around stroke direction so upright engines use head → piston → empty → shaft upward and inverted engines use the same sequence downward
- [x] Keep inverted engines pipe-fed only; inverted validation requires one active assembled `steam_inlet` and never direct-reads a compact boiler
- [x] Keep upright direct compact mode and upright pipe-fed mode unchanged
- [x] Make shaft placement helper, hidden powered shaft survival, movement checks, lighting, particles, and piston/linkage rendering direction-aware
- [x] Make the shaft placement helper derive the desired horizontal shaft axis from the clicked/player-facing side instead of the piston body's stale stored axis
- [x] Revalidate nearby engines when horizontal shafts notify neighbors, so inverted engines can claim shafts added through an existing shaft line or changed with a wrench
- [x] Mirror assembled cylinder ring hitboxes and reuse mirrored existing assembled cylinder section models for inverted visuals
- [x] Verify inverted pipe-fed engine in a standalone world and on an Aeronautics/Simulated contraption

### Phase 8.6: Shared-Wall Cylinder Banks

**Goal**: Allow adjacent vertical cylinders to form inline engine banks by sharing physical cylinder wall blocks instead of requiring separate overlapping 3×3 rings.

- [x] Keep shared walls as a `steam_cylinder` blockstate, not a new block type
- [x] Add cylinder-only `shared_wall` visual state for X- and Z-running shared wall strips
- [x] Rework cylinder connectivity into a two-pass resolver so all valid nearby rings are found before blockstates are written
- [x] Allow one cylinder wall block to belong to two adjacent same-orientation rings whose origins are offset by exactly two blocks on one horizontal axis
- [x] Reject unsupported overlaps, grids, T-junctions, and shared `steam_inlet` blocks
- [x] Store primary and secondary ring origins on shared `SteamCylinderBlockEntity` instances
- [x] Update engine validation so a cylinder block must belong to the specific ring origin being validated
- [x] Generate shared-wall section models, texture, and model-derived hitboxes from `Steam_Cylinder_SHARED_WALL.bbmodel`
- [x] Replace score-based partial ring guessing with stable local-corner origin inference
- [x] Keep using the existing assembled partial section models when a local corner implies a ring section
- [x] Keep straight-only cylinder wall runs in fence-like `section=none` wall mode until a corner appears
- [x] Resolve partial shared-wall visuals from current local corner topology instead of stale shared-wall blockstates
- [x] Use tracked partial/shared blockstates only to expand refresh scope so stale shared states can be cleared
- [x] Require a complete three-block shared strip and outside corner evidence on both candidates before partial shared-wall visuals appear
- [x] Keep shared-wall visuals when one adjacent ring is complete and the other adjacent ring is still partial, using the same shared-strip rule
- [x] Keep shared-wall corner visuals on the same axis and canonical model side during complete/partial transitions
- [x] Reject false partial-owner corner candidates unless they have real outside-block and shared-strip evidence
- [x] Keep the cylinder casing assembled when the bore/center column is obstructed; piston validation handles the obstruction separately
- [x] Protect already-assembled ring origins from false overlapping ring candidates generated by a misplaced bore block
- [x] Protect partial ring bores from misplaced ring-member blocks before the cylinder casing is fully assembled without treating shared-strip middles as bore blockers
- [x] Correct north-south shared-wall corner model ordering without changing east-west shared-wall visuals
- [x] Align north-south shared-wall hitbox segment ordering with the corrected visuals
- [x] Correct upside-down north-south shared-wall corner model and hitbox ordering without changing upright banks
- [ ] Verify two-engine and three-engine inline banks in X and Z directions in game
- [ ] Verify breaking a shared wall disassembles both affected engines, while breaking a non-shared wall leaves neighbors intact
- [ ] Verify shared-wall banks on Aeronautics/Simulated contraptions

### Phase 8.7: Cylinder Placement Quality of Life

- [x] Add Create-style standalone cylinder layer autocomplete when one hollow 3x3x1 shell layer is complete
- [x] Match Create Fluid Tank placement constraints: no sneaking, no Symmetry Wand, vertical-face placement only, survival inventory checked before autofill
- [x] Preserve inlets, reject ambiguous shared markers, and autocomplete shared-wall bank upper layers once the shared strip already exists

### Phase 9: Balance, Config, Recipes

- [x] Server config for: base capacity, steam production rate, boiler outlet pressure range, direct boiler pipe output enablement, direct mode enablement (max piston height deferred — v1 uses a fixed single-piston stroke, so there is no column height to cap yet)
- [x] Recipes for active blocks balanced against vanilla Create steam engine
- [x] JEI/EMI display (crafting recipes are vanilla recipe types and display automatically; JEI added as a runtime-only test dependency to verify in-game, no shipped plugin)

### Phase 10: Hardening and Release

- [ ] Dedicated server startup test
- [ ] World reload test
- [ ] Schematic placement test
- [ ] Save/load with kinetic state
- [ ] Performance: no rescan every tick; scan bounded and cached
- [ ] Crash audit: null levels, unloaded chunks, missing optional mods, invalid NBT

### Phase 11: Steam Leak Hazard — Complete

**Goal**: make steam leaking from an open pipe end dangerous, like real escaping steam, without
changing engine balance.

- [x] Scald living entities standing in an open-pipe steam cloud by extending the existing
  `SteamOpenPipeEffectHandler.apply` (Create's `OpenPipeEffectHandler` registry; no new hooks/mixins)
- [x] Add a custom `full_steam_ahead:steam` damage type (datapack `damage_type/steam.json` +
  `ModDamageTypes`) with a "scalded by steam" death message
- [x] Tag the steam damage type `#minecraft:no_knockback` so the gas damages without punching
  entities away
- [x] Apply damage on a game-time-gated cadence over the inflated leak AABB, scaling from a
  configurable floor (default 6.0 = 3 hearts/hit) up to a cap by how much steam is venting
- [x] Add `steamLeak` server config knobs: enabled, interval, radius, base/reference-mB/max damage

---

### Phase 12: Pressure/Volume/Temperature Steam Model — Complete

**Goal**: replace the flow-only output with a real pipe-network pressure model that stays readable in Create terms.

- [x] `SteamPhysics` computes pressure as `P = gasConstant * storedSteamMb * temperatureK / networkVolumeM3`, with pressure in Sable-style `pN/m²`.
- [x] Boiler outlet production remains gameplay-readable: `usableHeatUnits * boilerHeight * steamPerHeatUnit`, where `steamPerHeatUnit = 10 mB/t`.
- [x] Network volume includes boiler volume, pipe volume, inlet volume, outlet buffers, and passive steam storage tanks. Passive Create Fluid Tanks use their configured fluid capacity as pressure volume, not only their block count.
- [x] Network temperature is weighted by contributed steam, not copied from the hottest boiler; passive tanks and inlet buffers contribute at base steam temperature.
- [x] Pipe-fed engine output is capped per engine: full output is `90 mB/t`, `147,456 SU`, and `64 RPM`.
- [x] Engine output factor is `min(pressureFactor, flowFactor)`, so weak pressure or insufficient fair-flow share both reduce output.
- [x] Pipe networks distribute usable steam fairly across reachable active `steam_inlet` blocks. A short network gives every engine a proportional share instead of powering all-or-nothing.
- [x] Multiple boilers can feed one pipe network; multiple steam ports on one physical boiler split one shared boiler budget and cannot duplicate steam.
- [x] Create fluid valves are pressure blockers. Closed valves split networks instead of being bypassed by outlet pressure traversal.
- [x] Direct compact mode is retained as an upright-only compatibility shortcut. It derives a rated-pressure factor from local boiler production, but does not store steam, burst, or overdrive one engine.
- [x] `steamPhysics` server config covers gas constant, rated/warn/burst pressure, steam temperature, full engine flow/SU/RPM, vent coefficient, open-pipe target pressure, and buffer cap.
- [x] Goggles surface pressure/volume/production on the outlet and pressure/RPM/SU on the cylinder ring.

### Phase 13: Boiler Overpressure — Complete

**Goal**: stored steam in a closed pressure network builds pressure until it vents or explodes.

- [x] `SteamNetworkManager` computes one network pressure from stored steam, temperature, and network volume.
- [x] Open pipe ends and unconnected outlets vent first. Broken pipe ends drain enough steam to move the network toward the configured atmospheric target before burst checks, following pressure smoothing when enabled so engine output decays with the pressure instead of snapping off.
- [x] Pressure is recomputed after venting so sufficient relief can prevent a burst.
- [x] Past `steamPhysics.warnPressure`: status flips to "Overpressure!", with hiss and steam particles at the boiler center on a cadence.
- [x] Past `steamPhysics.burstPressure`: each physical boiler bursts at most once even if several outlets are attached to it.
- [x] A burst drains/depressurizes the whole connected steam network so pressure does not survive the explosion.
- [x] Explosion power = `min(maxPower, basePower + powerPerVolume · networkVolume) · powerScale`, with block-breaking configurable. Defaults are `basePower=12.0`, `powerPerVolume=0.45`, `maxPower=36.0`, `powerScale=1.0`.
- [x] Create Big Cannons projectiles that hit a Create Fluid Tank belonging to an active steam boiler trigger a boiler rupture. Connected and sealed pressure networks use current pressure and depressurize the connected steam network; active boilers without stored pressure still rupture at full burst pressure.
- [x] Bursts also send a client-side visual/audio packet for a large steam cloud, layered placeholder boom/hiss sounds, and configurable screen shake.
- [x] Client burst sound radius is 200 blocks; screen shake radius is 150 blocks.
- [x] Optional Sable/Aeronautics compat projects simulated-contraption burst effects into world coordinates and damages nearby sublevel blocks locally.
- [x] Simulated-contraption sublevel damage uses a bounded local `sublevelDamageRadius`, sparse explosion-like drops instead of dropping every destroyed block, quiet block removal, neighbor updates, and vanilla-style checks for unbreakable/blast-resistant blocks.
- [x] `steamOverpressure` config group: enabled, explosion base/per-volume/max power, final power scale, breaksBlocks, client effect packet radius, and Sable sublevel damage radius.
- [x] Client config group: boiler burst visuals, sound volume scale/radius, steam cloud scale, screen shake enable/scale/radius, blast wave speed.
- [x] Add `steam_relief_valve` as a top/side-mounted Create boiler safety block with block entity, model, item, recipe, loot, lang, tags, creative entry, movement rules, goggle tooltip, and client renderer.
- [x] Relief valves attach by Create Fluid Tank boiler controller, not by pipe network position, so one valve protects all steam-port-fed networks on that physical boiler.
- [x] Automatic relief opens at `2.2 MpN/m²`, closes below `1.7 MpN/m²`, and uses `720 mB/t` as the baseline per-valve vent rate while scaling effective safety relief with active boiler production.
- [x] Redstone-powered relief valves force open and drain toward the configured open-pipe atmospheric target.
- [x] Relief valve venting uses Create-style cap lift, handwheel spin, steam particles, scald hazard, and steam sound.
- [x] Relief valves support top and horizontal side placement; bottom placement remains unsupported to avoid Blaze Burner/floor conflicts.
- [ ] Follow-up: local Sable crater pass should skip fluid-only blocks without making waterlogged solid blocks immune.

### Phase 14: Display Link Pressure Readouts — Implemented (manual verification pending)

**Goal**: make steam pressure readable through Create's Display Link system, using the same language as goggles.

- [x] Research Create 6.0.10 display source registration and target filtering from the local Create dev jar/source before implementation.
- [x] Add a Display Link source for `boiler_outlet` first, because it already owns current network pressure, production, volume, venting, warning, and burst state.
- [x] Display useful single-line modes: current pressure, pressure status, rated/warn/burst thresholds, network volume, production rate, reachable engine count, and venting/burst risk.
- [x] Make the source a Create `SingleLineDisplaySource` so multiple Display Links can target different rows on one Display Board without clearing or overwriting each other.
- [x] Format pressure through `SteamPressure.format(...)` so displays show `pN/m²`, `kpN/m²`, or `MpN/m²`, never `bar`.
- [x] Keep the source passive and server-authoritative; Display Links must only read pressure state and must not tick or mutate the steam network.
- [x] Add an equivalent Create Fluid Tank boiler display source so players can read pressure directly from the boiler without requiring an outlet.
- [ ] Verify with a Display Link and Display Board: stable pressure line while running, warning text under overpressure, no crash when the outlet unloads or loses its boiler.

### Phase 15: Aeronautics Steam Vent Consumption — Implemented (manual verification pending)

**Goal**: make Create Aeronautics steam vents consume Full Steam Ahead steam when mounted on boilers that feed FSA steam networks.

- [x] Keep Aeronautics optional: no compile dependency and no direct Aeronautics imports.
- [x] Detect `aeronautics:steam_vent` blocks mounted on top of physical Create Fluid Tank boilers already feeding an FSA `boiler_outlet` network.
- [x] Read each vent's live Aeronautics `getGasOutput()` reflectively, gated by `canOutputGas()`.
- [x] Convert Aeronautics gas output to FSA steam demand through server config:
  `aeronauticsCompat.steamVentMbPerM3`.
- [x] Default conversion is `0.002 mB/t per m³`, so a default `5000 m³` steam vent consumes `10 mB/t`, or one FSA steam unit.
- [x] Split one boiler's vent demand across that boiler's attached FSA outlets using the same stable outlet-share rule as production, so multiple outlets do not duplicate consumption.
- [x] Drain vent consumption before final pressure and engine draw-cap calculation, so pressure and engine output reflect the Aeronautics load immediately.
- [ ] Manual test: one boiler + one powered Aeronautics steam vent should show roughly +10 mB/t network consumption at default vent settings.
- [ ] Manual test: changing `aeronauticsCompat.steamVentMbPerM3` should scale vent demand without a code change.

### Phase 16: Pipe-Fed Aeronautics Steam Vents — Implemented (manual verification pending)

**Goal**: let Aeronautics steam vents consume FSA steam from Create fluid pipes, not only by sitting directly on boilers.

- [x] Keep Aeronautics optional through a separate non-required mixin config and no hard Aeronautics imports.
- [x] Register a dynamic FSA steam fluid capability on `aeronautics:steam_vent` when Aeronautics is loaded.
- [x] Allow pipe-fed vents to survive when placed directly on top of a Create fluid pipe while preserving normal boiler-mounted placement.
- [x] Add pressure-limited pipe-fed vent output: rated FSA pressure gives full Aeronautics hot-air output, low pressure reduces output, and no pressure gives no output.
- [x] Add pipe-fed vents to `SteamNetworkManager` as fair steam consumers with a small internal buffer.
- [x] Preserve boiler-mounted vent behavior and current boiler-mounted FSA consumption.
- [ ] Manual test: pipe `steam` from an FSA boiler outlet into an Aeronautics steam vent placed above a Create fluid pipe, then power the vent and confirm it outputs hot air.
- [ ] Manual test: close a valve or open a leak before the vent and confirm vent output drops with network pressure.
- [ ] Manual test: changing `aeronauticsCompat.steamVentMbPerM3` changes pipe-fed vent load.

---

### Phase 17: Steam Admission Valve — Implemented (manual verification pending)

**Goal**: add a pipe-shaped, remotely controlled admission throttle between a shared steam main and one engine `steam_inlet`, while allowing valves in a cylinder bank to remain part of a continuous pipe run.

- [x] Register `steam_admission_valve` as a Create-compatible fluid pipe block with its own pipe block entity, item, loot, tags, language entry, and creative-tab entry.
- [x] Render the valve with Create's native context-sensitive Fluid Pipe core models and `PipeAttachmentModel` arms/rims, including isolated straight pipes and gap-free multi-side horizontal connections.
- [x] Keep the controls on a topology-independent raised industrial-iron platform with brass trim and two pads using Create's exact Redstone Link geometry, texture, and normalized UV density.
- [x] Add a complete one-pixel brass collar around every live horizontal port, separated from Create's endpoint rim and neighbouring collars with no coplanar or overlapping faces.
- [x] Align the platform and its contained pedestal to the detected straight/main pipe axis whenever no unique Steam Inlet controls its facing.
- [x] Point the future controlled branch toward exactly one adjacent `steam_inlet` while deriving every visible connection from the actual four-direction horizontal pipe state.
- [x] Prevent vertical valve orientation, clear up/down properties after placement and neighbour updates, and reject one-sided vertical connections initiated by adjacent Create pipes.
- [x] Match selection and collision shapes to the raised controls, horizontal pipe arms, brass collars, and endpoint rims.
- [x] Preserve normal Create fluid transport at full flow through non-controlled branches so the valve remains part of a shared steam main.
- [x] Add two built-in Redstone Link frequency slots and receive an analogue `0..15` command without a separate Redstone Link block; signal `0` is fully closed, signal `15` is fully open, and an empty frequency pair is a full-open bypass.
- [x] Throttle only the uniquely linked active `steam_inlet`; a through-branch main remains open to downstream engines and network shortages preserve throttle ratios.
- [x] Add Create-style adjustment sound and live goggle feedback, and persist receiver state through normal block-entity NBT without adding detached actuator geometry above the control platform.
- [x] Add a survival recipe using a Create Fluid Pipe, Redstone Link, and brass plate.
- [ ] Re-test direct signal control in a static world at strengths `0`, `5`, `10`, and `15`; all previously validated geometry, connectivity, collision, inventory rendering, frequency sizing, and live goggle feedback remain complete.
- [ ] Manual simulated-contraption test: frequencies, received signal, inlet association, and throttle output survive assembly and disassembly.

---

### Phase 18: Linear Steam Engine RPM — Implemented (manual verification pending)

**Goal**: replace the four RPM plateaus with one global speed response driven by the same pressure-and-flow output factor as SU, regardless of how the engine receives steam.

- [x] Implement RPM once in `SteamPhysics.rpm(outputFactor)` so direct compact, ordinary pipe-fed, admission-controlled, shortage-limited, and leak-limited engines share the same mapping.
- [x] Return `0 RPM` at zero output and `max(1, maxRpm * outputFactor)` for positive output, preserving the configured `64 RPM` full-output default.
- [x] Keep SU unchanged as `fullEngineSu * outputFactor`, so pressure and delivered flow remain the only engine-output inputs.
- [x] Remove the obsolete tier helper and tier-specific config wording.
- [x] Use configured maximum RPM for legacy power migration and particle/sound intensity instead of a hard-coded `64`.
- [x] Preserve client animation phase when an FSA-powered kinetic network changes RPM by compensating Create's absolute-time rotation formula through the shared `getRotationAngleOffset` hook used by both Flywheel and fallback rendering.
- [x] Avoid detaching and rebuilding the kinetic source for capacity-only changes; coalesce active RPM ramps into shared 10-tick propagation windows with a `0.5 RPM` accumulated deadband, then apply the exact final target after it settles. Starts, stops, reversals, ownership changes, SU updates, and animation phase continuity remain immediate.
- [x] Prevent self-fed engine RPM ramps from repeatedly resetting Create pump fluid networks, and compare final post-FSA boiler device counts so stable boilers no longer report a false attachment change every tick.
- [x] Hold the last linkage pose across Create's brief zero-speed propagation frame so piston, connecting rod, and crank visuals do not flash to their resting pose during a live RPM ramp.
- [x] User validation (2026-07-14): normal-gameplay RPM transitions preserve piston, linkage, and shaft phase without restarting, flickering, or jumping.
- [ ] Manual test direct and pipe-fed engines at low, quarter, half, three-quarter, and full output; confirm RPM changes progressively while SU remains proportional.
- [ ] Manual test an admission valve at signals `0`, `5`, `10`, and `15`; expect approximately `0`, `21.3`, `42.7`, and `64 RPM` at rated pressure.
- [ ] Manual test adjacent engines on one shaft at equal and unequal admission settings; confirm stable kinetic-network behavior and passive linkage animation.
- [ ] Manual test slow and abrupt acceleration/deceleration with Flywheel enabled and disabled; piston/linkage and every attached shaft, gear, belt, and gauge must preserve phase without flicker or backward jumps.
- [ ] Manual test world reload, chunk reload, and Sable simulated-contraption assembly while an engine is running; the first rendered frame may adopt the loaded phase, but subsequent RPM changes must remain continuous.

---

### Phase 19: Linked Steam Pressure Gauge — Implemented (manual verification pending)

**Goal**: provide a readable Victorian-style analogue pressure instrument for remote boiler monitoring on ships and aircraft without requiring an admission valve or consuming steam.

- [x] Register `steam_pressure_gauge` as a horizontally directional brass instrument block with a custom item, block entity, recipe, loot table, tags, language, creative-tab entry, and model-derived hitbox.
- [x] Let a held gauge item sneak-right-click any tank block in an active Create Fluid Tank boiler to capture that exact tank as its source; keep the selection on the stack for placing multiple gauges and allow sneak-use in air to clear it.
- [x] Store the item source as a dimension-aware `GlobalPos`, then convert it to a relative block offset when placed so the gauge has no global registry and never force-loads source chunks.
- [x] Resolve the selected tank's current Create controller at read time, so tank resizing or controller changes do not invalidate a surviving selected tank block.
- [x] Preserve links through Sable/Aeronautics assembly rotation by rotating the relative source offset from its stored link-facing to the gauge's transformed facing; manual wrench rotation rebases facing without moving the world-space source.
- [x] Read `SteamNetworkReadout.getNetworkPressurePn()` from the boiler controller, matching boiler goggles and Display Links without adding a second pressure calculation or consuming steam.
- [x] Synchronize pressure, source availability, and the configured burst threshold to clients on a bounded cadence, then smooth needle motion client-side between readings.
- [x] Map zero pressure to the low needle stop and configured burst pressure to the high stop; clamp overpressure at the end stop.
- [x] Render the approved Blockbench housing and fixed hub as the baked model and its separate needle/counterweight group as an animated partial model using the authored UVs and 16-pixel texture density.
- [x] Show linked/unlinked/unavailable state and live pressure in Create goggles; missing or unloaded sources ease the needle to zero instead of loading chunks.
- [ ] Manual test source selection, repeated placement, clearing, tank-controller changes, cooling to zero, missing/unloaded sources, all four rotations, save/reload, and simulated-contraption assembly/disassembly.

---

### Optional Phase: Volumetric Steam Clouds

**Goal**: upgrade current leak/exhaust particles from instant local effects into a sparse gas simulation for enclosed spaces.

- [ ] Add a bounded server-side `SteamGasField` manager keyed by level/chunk/block position.
- [ ] Represent steam as temporary gas cells in passable air blocks, storing steam mass, temperature, age, and derived pressure/density.
- [ ] Feed cells from open pipe leaks, boiler vents, and cylinder bore exhaust while preserving the existing particle art style.
- [ ] Diffuse gas to neighbouring passable cells every few ticks; solid blocks block diffusion so closed rooms can trap steam.
- [ ] Cool cells over time toward ambient temperature; lower density/temperature reduces damage and particle opacity until the cell expires.
- [ ] Apply scald damage from gas cells rather than only from the immediate leak AABB.
- [ ] Add strict performance caps: max cells per chunk/level, low-density expiry, bounded spread radius, and server config for damage/lifetime/diffusion.
- [ ] Keep particles client-side and visual only; gameplay state lives in the gas field.

---

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Create `BoilerData` API changes | Read through `FluidTankBlockEntity`; isolate in one method |
| Create pipe pressure internals are brittle | Keep steam-port pressure code isolated; fallback to bounded `IFluidHandler` push |
| Steam storage becomes an exploit | Steam ports only generate from valid active boilers and never auto-pump stored steam |
| Pipe-fed mode loses burner-count metadata | Store network steam mass, weighted temperature, and volume; piston derives RPM/SU from pressure and fair delivered flow (`SteamPhysics`) |
| Linear generators on one shaft request different speeds | Use matching admission commands for normal engine banks and explicitly verify mixed-command kinetic behavior before treating it as supported control practice |
| Cylinder ring scan too expensive | Run only on placement/removal, not every tick; cache result |
| Piston animation desync | Drive animation from linked shaft rotation and compensate Create's absolute-time angle offset whenever an FSA-driven kinetic speed changes; cover both Flywheel and fallback rendering |
| Sable assembly splits engine parts | Register Create and Simulated movement checks early |

---

## Art Direction

Stay as close as possible to base Create's visual language:

- Copper and brass for the cylinder casing (warm, industrial)
- Dark iron/steel for the piston rod
- Exposed normal Create shaft geometry at the top output
- Riveted panel texture language on the cylinder outer faces
- Goggle overlays follow Create's own overlay style exactly
