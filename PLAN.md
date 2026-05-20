# Create: Full Steam Ahead — Design Plan

Last updated: 2026-05-20

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

- Create's `SteamEngineBlockEntity` reads a neighbouring `FluidTankBlockEntity` boiler and updates a `PoweredShaftBlockEntity`. We follow this same pattern: our `CrankshaftBlockEntity` reads the `FluidTankBlockEntity` boiler below the cylinder via the same `BoilerData` path.
- Create's `GeneratingKineticBlockEntity` with dynamic `getGeneratedSpeed()` and `calculateAddedStressCapacity()` is the correct base for the crankshaft output. Do not subclass `PoweredShaftBlockEntity` directly.
- Create exposes `BlockStressValues` for stress tooltip metadata.
- Sable exposes `BlockEntitySubLevelActor` for per-tick block entity behaviour on sublevels.
- `SimBlockMovementChecks` (Aeronautics) must be registered so Sable assembly treats engine parts as one connected structure.

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

We do not add a custom boiler block. The player builds a standard Create Fluid Tank structure (minimum 3×3×1 at the base of the engine), heats it with Create's Blaze Burners placed below it, and pipes water in using Create's own pumps and pipes. Create's own heat and water indicators appear on the tank. Our cylinder reads that tank's `BoilerData` the same way `SteamEngineBlockEntity` does — we are a different kind of steam engine consumer, not a replacement boiler system.

The boiler must be at least 3×3×1 (9 fluid tank blocks) to support a full cylinder frame above it.

### Engine orientation: vertical only in v1

Blaze Burners heat upward. The engine is always vertical:

- Blaze Burners at the bottom (player-placed, Create blocks)
- Create Fluid Tank layer(s) above them (the boiler)
- Steam Cylinder frame on top of the boiler
- Piston column rising through the cylinder
- Crankshaft at the top of the piston

Other orientations (horizontal, inverted) are deferred to a future version.

### Block list (v1)

Five blocks. No more, no less for v1.

| Block | Class base | Role |
|---|---|---|
| `steam_cylinder` | `SmartBlock` | Forms the 3×3×2 hollow casing ring around the piston. Self-assembles. |
| `piston` | `SmartBlock` | Physical piston block. 4 blocks per engine: 2 inside the cylinder, 2 protruding above. Animated when running. |
| `crankshaft` | `KineticBlock` | Sits at the tip of the piston. The kinetic output. Triggers full structure validation. |
| `flywheel` | `SmartBlock` | Attaches axially to the crankshaft. Required for full efficiency. |
| `governor` | `SmartBlock` | Attaches to the crankshaft. Controls target RPM. Deferred to Phase 5. |

Removed from the old plan (do not re-add without discussion):

- `large_steam_engine_controller` — no controller
- `firebox` — Blaze Burners go directly under Create's fluid tank
- `large_engine_casing` — cylinder block handles casing
- `boiler_drum` — Create's fluid tank is the boiler
- `output_coupling` — crankshaft is the kinetic output
- `piston_rod` — renamed and redesigned as `piston`

---

## Engine Structure

A minimal working engine (vertical, default orientation):

```
        [Crankshaft]              ← KineticBlock; shaft ports on N/S/E/W faces
        [  Piston  ]              ← protrusion block 2 (top, assembled texture: connector pin)
        [  Piston  ]              ← protrusion block 1 (assembled texture: stuffing box seal)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ← top of cylinder frame (assembled texture: cylinder head cap)
[Cyl]   [  Piston  ]   [Cyl]
[Cyl]   [          ]   [Cyl]     ← upper cylinder layer (inner face texture active)
[Cyl]   [  Piston  ]   [Cyl]
[Cyl]   [          ]   [Cyl]     ← lower cylinder layer
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ← top of Create Fluid Tank (boiler)
[Tank]  [  Tank    ]   [Tank]
[Tank]  [  Tank    ]   [Tank]    ← Create Fluid Tank blocks (player-placed, not our blocks)
[Tank]  [  Tank    ]   [Tank]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BB  ]  [   BB     ]   [BB  ]    ← Blaze Burners (player-placed, Create blocks)
```

Block counts for a minimal engine:
- 16 × `steam_cylinder` (8 per layer × 2 layers)
- 4 × `piston` (2 inside cylinder hollow + 2 above)
- 1 × `crankshaft`
- At least 9 × Create `fluid_tank` (3×3×1 minimum)
- At least 1 × Create `blaze_burner`

---

## Three-Layer Assembly

Assembly is layered. Each layer has its own validity and its own visual state.

### Layer 1 — The Boiler (Create's system, untouched)

The player builds a Create Fluid Tank structure and heats it with Blaze Burners. Create handles everything: heat indicator, water indicator, steam generation. We do not touch this layer. Our cylinder reads from it.

### Layer 2 — The Cylinder Ring (our auto-assembly)

The 3×3×2 ring of `SteamCylinder` blocks self-assembles when all 16 positions are filled correctly. The moment the 16th block is placed and the ring is complete:

- All 16 blocks flip `ASSEMBLED = true`
- Connected textures activate (inner bore faces appear, top ring shows cylinder head cap)
- The cylinder block entity designated as root scans the 9 blocks directly below the bottom ring layer for Create `FluidTankBlockEntity` instances
- If a valid boiler is found, the cylinder root caches the boiler position and starts reading `BoilerData`
- If no valid boiler is below, the cylinder assembles visually but shows a "no steam source" indicator (goggle overlay, no particles)

The cylinder ring also disassembles visually if any of the 16 blocks is removed, even if it has a valid boiler.

**Shape constraint**: The ring must be exactly 3×3 in cross-section with the centre 1×1 column hollow. No other shape is accepted in v1.

**Boiler detection**: The bottom ring layer checks positions (0,−1,0) through (2,−1,2) relative to its south-west corner for `FluidTankBlockEntity`. It needs at least the 8 positions directly below the ring blocks (positions matching the cylinder shell footprint) to be valid fluid tanks. The boiler does not need to be assembled in any Create-specific sense; it just needs to be fluid tanks with Blaze Burners below.

### Layer 3 — The Crankshaft (kinetic assembly)

When the `Crankshaft` block is placed, it scans downward along its Y axis:

1. Expects exactly 2 `piston` blocks immediately below it (the protruding column)
2. Expects a valid assembled `SteamCylinder` ring below those 2 piston blocks
3. Expects exactly 2 `piston` blocks inside the hollow centre of that ring
4. Expects a valid Create fluid tank layer directly below the ring's bottom layer

If all four checks pass: the crankshaft block entity stores references to the cylinder root and begins receiving steam data. It calls `updateGeneratedRotation()` and begins generating RPM proportional to the boiler's current efficiency.

If any check fails: the crankshaft sits inert and shows a "incomplete structure" goggle overlay.

**Revalidation triggers:**
- Any `piston` block placed or removed
- Any `steam_cylinder` block placed or removed within the expected positions
- Any Create fluid tank block placed or removed directly below the cylinder's bottom ring
- Crankshaft block entity loads from disk

---

## Block Details

### `SteamCylinder`

- Extends `SmartBlock` (Create's base, not vanilla `Block`)
- Block entity: `SteamCylinderBlockEntity extends SmartBlockEntity`
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
  - `INNER_FACE: EnumProperty<Direction>` — which face is the inner bore face (used for rendering the bore texture on the correct face toward the hollow centre)
- On placement: triggers connectivity scan among adjacent `SteamCylinder` blocks. If a complete 3×3×2 ring with hollow centre is detected, assembly fires.
- On removal: broadcasts disassembly to all cylinder blocks sharing the same ring; they all flip `ASSEMBLED = false`.
- One cylinder block is designated the ring root (deterministically: lowest BlockPos by Y then X then Z). The root block entity holds: boiler position cache, steam data cache, assembled flag.
- Implements `IHaveGoggleInformation`: shows boiler link status, heat level, water level.

### `SteamPiston` (registered as `piston`)

- Extends `SmartBlock`
- No block entity in v1 (purely structural and visual)
- Blockstate properties:
  - `ASSEMBLED: BooleanProperty` (default false)
  - `PISTON_SECTION: EnumProperty<PistonSection>` where `PistonSection` has values `INSIDE_LOW`, `INSIDE_HIGH`, `PROTRUDE_LOW`, `PROTRUDE_HIGH` — determines which texture variant and animation offset to use
- When the crankshaft validates the structure, it sets the assembled state and piston section on all 4 piston blocks.
- Animation: when `ASSEMBLED = true` and the crankshaft is generating, the piston renders an animated reciprocating motion driven by the crankshaft's current rotation angle. The visual piston stroke covers the 2 protruding block heights. No block actually moves; the animation is purely rendered via Flywheel.

### `Crankshaft`

- Extends `KineticBlock` (Create)
- Block entity: `CrankshaftBlockEntity extends GeneratingKineticBlockEntity`
- Axis: always `Direction.Axis.Y` (vertical only, v1)
- Shaft ports: N, S, E, W faces (horizontal). Top face is capped (piston connection). Bottom face is capped.
- `getGeneratedSpeed()`: returns configured governor RPM when assembled and steam is available; 0 otherwise.
- `calculateAddedStressCapacity()`: returns capacity proportional to boiler efficiency score; 0 when not assembled.
- Holds reference to `SteamCylinderBlockEntity` root position. On each server tick, reads the cylinder root's cached boiler data to update speed and capacity.
- Implements `IHaveGoggleInformation`: shows assembly status, boiler link, heat, water, current RPM, SU capacity.

### `Flywheel`

- Extends `SmartBlock`
- Attaches axially to the crankshaft (placed on the N, S, E, or W face where a shaft port exists)
- When attached to a valid crankshaft, registers its presence and grants +40% stress capacity bonus
- Without a flywheel: engine runs at 60% of calculated capacity
- With one flywheel: 100% capacity
- Deferred: animated spinning disk (Phase 7)

### `Governor`

- Extends `SmartBlock`
- Deferred to Phase 5
- Attaches to crankshaft's remaining shaft port faces
- Scroll wheel cycles RPM: 16 → 32 → 48 → 64
- Default RPM when no governor present: 32

---

## Package Layout

```
src/main/java/dev/gustavo/fullsteamahead/
  FullSteamAhead.java
  registry/
    ModBlocks.java
    ModBlockEntities.java
    ModItems.java
    ModCreativeTabs.java
  content/
    cylinder/
      SteamCylinderBlock.java
      SteamCylinderBlockEntity.java
      CylinderConnectivity.java        ← ring detection and root election logic
    piston/
      SteamPistonBlock.java
      PistonSection.java               ← enum: INSIDE_LOW, INSIDE_HIGH, PROTRUDE_LOW, PROTRUDE_HIGH
    crankshaft/
      CrankshaftBlock.java
      CrankshaftBlockEntity.java       ← GeneratingKineticBlockEntity, reads cylinder root
    flywheel/
      FlywheelBlock.java
    governor/
      GovernorBlock.java               ← deferred
  compat/
    create/
      CreateStressRegistration.java
    simulated/
      SimulatedMovementCompat.java
```

---

## Balance

Boiler reference: vanilla Create steam engine baseline.

| Parameter | Value |
|---|---|
| Governor RPM options | 16, 32, 48, 64 |
| Default RPM (no governor) | 32 |
| Base SU per cylinder pair | 1024 SU |
| Full engine (1 boiler, 1 cylinder ring, 1 flywheel) | 1024 SU at 100% boiler efficiency |
| Without flywheel | 614 SU (60%) |
| Maximum v1 capacity (balance testing target) | 4096 SU |

Steam formula (v1):

```
boiler_efficiency  = min(heat_score, water_score)        ← from Create BoilerData
flywheel_bonus     = flywheel_present ? 1.0 : 0.6
capacity_su        = BASE_CAPACITY * boiler_efficiency * flywheel_bonus
output_speed_rpm   = governor_rpm                         ← when efficiency > 0
```

All constants must be server config values.

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
- [x] Add: `CrankshaftBlock extends KineticBlock` with Y-axis orientation and horizontal shaft ports
- [x] Add: `FlywheelBlock` inert stub
- [x] Add: `GovernorBlock` inert stub
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

### Phase 4: Crankshaft Validation and Kinetic Output — Current

**Goal**: Crankshaft placed at top of piston column validates full structure and generates rotation.

Tasks:
- [x] Implement `CrankshaftBlockEntity extends GeneratingKineticBlockEntity`
- [x] Downward scan on placement: 2 piston blocks → assembled cylinder ring → boiler below ring
- [x] If valid: store cylinder root ref, call `updateGeneratedRotation()`
- [x] `getGeneratedSpeed()`: governor RPM when assembled and boiler efficiency > 0, else 0
- [x] `calculateAddedStressCapacity()`: `BASE_CAPACITY * boiler_efficiency * flywheel_bonus`
- [x] Read `BoilerData` from the `FluidTankBlockEntity` at boiler position each server tick
- [x] Add piston block state updates: set `ASSEMBLED` and `PISTON_SECTION` on all 4 piston blocks when crankshaft validates
- [x] Add revalidation on neighbour changes
- [x] Add goggle overlay: assembly status, RPM, SU, boiler efficiency, flywheel present
- [ ] Verify: built correctly → shaft turns; break piston → shaft stops; break boiler → shaft stops

Implementation note: Phase 4 treats the current engine as one custom boiler consumer because Create's boiler scan only counts vanilla Create steam engines. The flywheel remains an absent placeholder, so output capacity is intentionally capped at 60% until Phase 5.

### Phase 5: Flywheel and Governor

**Goal**: Flywheel grants capacity bonus. Governor controls RPM.

- [ ] Flywheel attaches to crankshaft horizontal shaft port; crankshaft detects presence
- [ ] Without flywheel: 60% capacity. With flywheel: 100%.
- [ ] Governor scroll wheel cycles RPM 16/32/48/64; crankshaft reads governor RPM
- [ ] Verify capacity and RPM change in real time on goggle overlay

### Phase 6: Aeronautics/Sable Compatibility

- [ ] Register `BlockMovementChecks` for all engine blocks
- [ ] Register `SimBlockMovementChecks` when Aeronautics present
- [ ] Test engine inside Sable sublevel powering Aeronautics propellers
- [ ] Verify NBT, kinetic state, and boiler link survive assembly/disassembly and reload

### Phase 7: Rendering and Ponder

- [ ] Animated piston reciprocation via Flywheel renderer (driven by crankshaft rotation angle)
- [ ] Animated flywheel spinning disc
- [ ] Steam particles from cylinder top when running
- [ ] Sound: rhythmic chuffing tied to piston animation
- [ ] Ponder scenes: how to build, water/heat, connecting shafts, aircraft use

### Phase 8: Balance, Config, Recipes

- [ ] Server config for: base capacity, flywheel bonus, RPM options, max piston height
- [ ] Recipes for all 5 blocks balanced against vanilla Create steam engine
- [ ] JEI/EMI display

### Phase 9: Hardening and Release

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
| Cylinder ring scan too expensive | Run only on placement/removal, not every tick; cache result |
| Piston animation desync | Drive animation entirely from crankshaft rotation angle on client |
| Sable assembly splits engine parts | Register Create and Simulated movement checks early |
| No flywheel required makes balance trivial | Flywheel mandatory for 100% output; 60% without is still useful |

---

## Art Direction

Stay as close as possible to base Create's visual language:

- Copper and brass for the cylinder casing (warm, industrial)
- Dark iron/steel for the piston rod
- Exposed shaft geometry on the crankshaft ends
- Riveted panel texture language on the cylinder outer faces
- Goggle overlays follow Create's own overlay style exactly
