# Large Steam Engine Create Addon Plan

Last updated: 2026-05-19

## Goal

Build a NeoForge Minecraft 1.21.1 Create addon that adds a large, player-customizable, multiblock steam engine. The engine should feel like a believable ship or aircraft power plant: visually large, mechanically configurable, compatible with Create shaft networks, and usable on Create Aeronautics/Sable moving sublevels.

The first implementation should produce Create rotational power. It should not directly push aircraft or ships in v1. Aeronautics propellers already consume Create rotation and apply Sable point forces, so the strongest compatibility path is:

`large steam engine -> Create shaft network -> Aeronautics propeller/thruster/other kinetic consumers -> Sable physics`

Direct Sable force output can be added later for turbine/thruster variants, but it is not required for the core steam engine.

## Research Snapshot

The workspace currently has no mod scaffold or existing source tree. It only contains Codex metadata, so the next development phase must begin by creating a NeoForge addon project.

Verified target stack:

- Minecraft: `1.21.1`
- Loader: `NeoForge`
- Java: `21`
- Create: current 1.21.1 release is `6.0.10`; Create's developer docs map the Maven dev dependency to `6.0.10-280`.
- Create dependency companions: Ponder, Flywheel, and Registrate are required in the dev classpath.
- Create Aeronautics: current Modrinth release checked is `1.2.1+mc1.21.1`, published 2026-04-28.
- Sable: required by Aeronautics; it provides interactive moving block sublevels and the physics pipeline.

Important source findings:

- Create's vanilla steam engine is not a normal large generator. `SteamEngineBlockEntity` reads a neighboring `FluidTankBlockEntity` boiler and updates a `PoweredShaftBlockEntity`.
- Create's `PoweredShaftBlockEntity` is a `GeneratingKineticBlockEntity` with dynamic speed and dynamic stress capacity. This is the right pattern to copy, not a class to reuse directly.
- Create's `BoilerData` only counts vanilla `SteamEngineBlock` attachments. Treating our large engine as a vanilla boiler attachment would require mixins into Create internals, so v1 should avoid depending on that path.
- Create exposes `BlockStressValues` for stress capacity, impact, and generated RPM tooltip metadata. Dynamic generator output can still override `calculateAddedStressCapacity()`.
- Sable exposes `BlockEntitySubLevelActor` for per-tick and per-physics-tick block entity behavior on sublevels.
- Sable/Aeronautics propellers implement `BlockEntitySubLevelPropellerActor`, which applies point forces through `QueuedForceGroup.applyAndRecordPointForce(...)`. We should let those blocks handle physics thrust.
- Simulated/Aeronautics has `SimBlockMovementChecks` in addition to Create's `BlockMovementChecks`. We need register assembly attachment behavior for our engine parts so the multiblock stays together when assembled into a ship or aircraft.

Primary references:

- Create developer dependency docs: https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1
- Create source: https://github.com/Creators-of-Create/Create
- Create Aeronautics Modrinth: https://modrinth.com/mod/create-aeronautics
- Simulated Project source: https://github.com/Creators-of-Aeronautics/Simulated-Project
- Sable Modrinth: https://modrinth.com/mod/sable
- Sable source and wiki: https://github.com/ryanhcode/sable and https://github.com/ryanhcode/sable/wiki

## Viability

This addon is viable.

The low-risk part is the Create integration: Create's kinetic system already supports custom generators through `GeneratingKineticBlockEntity`, dynamic speed, and dynamic stress capacity. A large engine output shaft can behave like Create's powered shaft while using our own controller and multiblock state.

The medium-risk part is the multiblock design. Minecraft block entities, chunk loading, block updates, schematics, and moving sublevels make large arbitrary structures harder than fixed-size machines. This is manageable if the engine uses one authoritative controller, cached scan results, strict size limits, and clear invalidation rules.

The highest-risk part is direct use of Create's vanilla boiler internals. `BoilerData` is not designed as a public addon extension point for non-Create engines. The v1 plan should implement our own pressure/steam model using NeoForge fluid capabilities and Create's public heat lookup API, while visually and mechanically matching Create's boiler expectations. A later optional adapter can integrate with vanilla Create boilers after a separate compatibility spike.

Aeronautics compatibility is viable if we stay inside Create's shaft network. Sable supports block entities inside sublevels, and Aeronautics already applies propeller forces from kinetic speed. We only need to ensure our blocks are movable, stay connected during assembly, tick correctly on sublevels, and do not duplicate generated stress through multiple outputs.

## Product Design

## Phase One Decisions

These choices are locked for the initial implementation scaffold:

- Display name: `Create: Full Steam Ahead`
- Mod id: `full_steam_ahead`
- Java package/group: `dev.gustavo.fullsteamahead`
- Root project/artifact name: `create-full-steam-ahead` / `full_steam_ahead`
- License: `MIT`
- Runtime dependency model: Create is required; Sable and Create Aeronautics are optional runtime compatibility targets.
- Art direction: stay as close as practical to base Create, using copper, andesite, brass, exposed shafts, gauges, riveted casings, and steam engine visual language.
- v1 max multiblock size: hard `3x3x3`, maximum `27` blocks.
- Phase one implementation scope: bootable scaffold only; no gameplay block registrations yet.

Default balance constants to carry into later phases:

- Governor RPM options: `16`, `32`, `48`, `64`.
- Default RPM: `32`.
- Base capacity reference: Create steam engine default capacity, `1024` SU.
- Target maximum v1 capacity before balance testing: `4096` SU per assembled engine.
- Maximum counted output couplings: `2`.
- Maximum counted cylinders: `4`.

### Player Experience

Players build a large engine from modular blocks:

- Engine Controller: forms and manages the multiblock.
- Engine Casing: structural valid blocks.
- Boiler Drum / Steam Chamber: stores water and pressure.
- Firebox / Heat Interface: reads heat from Blaze Burners and other Create-compatible heaters.
- Cylinder Block: increases torque/capacity.
- Piston Rod / Crank Block: visual and structural engine parts.
- Flywheel: stabilizes output and increases efficiency.
- Output Shaft Coupling: the actual kinetic generator output.
- Gauge / Governor: optional control/readout block.

The player right-clicks the controller with a wrench to assemble or refresh the engine. Goggles show pressure, water input, heat, cylinders, flywheels, output RPM, total SU capacity, and per-output allocation.

The multiblock should be customizable rather than a rigid structure. It should use a graph/scoring model:

- All parts must be connected to the controller through valid engine blocks.
- At least one boiler/steam chamber, one cylinder, one flywheel, and one output coupling are required.
- More cylinders increase torque.
- More steam chamber volume increases pressure reserve.
- More heat and water increase pressure generation.
- More output couplings divide total available capacity; they must not duplicate it.

### Initial Balance Direction

Use vanilla Create steam engines as the reference, not as a hard mechanical clone.

Baseline Create behavior found in source:

- Steam engine generated RPM effectively ranges from 16 to 64.
- Boiler heat is capped by boiler size, water supply, and heat.
- Stress capacity is dynamic and depends on engine efficiency.

Large engine v1 should keep default RPM in Create-friendly ranges:

- Governor options: `16`, `32`, `48`, `64` RPM.
- Optional config can unlock `96` or `128` RPM for servers that want stronger aircraft engines.
- Generated capacity should scale with validated structure score, pressure, and RPM.
- Total power must be conserved across outputs.

Suggested first formula:

```text
pressure_limit = min(heat_score, water_score, steam_chamber_score)
mechanical_score = cylinder_score * flywheel_efficiency
total_capacity_su = base_capacity_per_cylinder * mechanical_score * pressure_efficiency
output_speed = governor_rpm * direction
capacity_per_output = total_capacity_su * output_weight / total_output_weight
```

All constants must be server config values.

## Architecture

### Dependency Strategy

Required runtime dependency:

- Create `[6.0.10,6.1.0)`

Strongly tested optional dependencies:

- Sable `[1.2.1,1.3.0)`
- Create Aeronautics `[1.2.1,1.3.0)`

The base mod should work in a Create-only pack. Aeronautics/Sable compatibility should be conditionally registered when those mods are present. This avoids hard classloading failures for players who want the engine outside Aeronautics.

If direct Sable APIs become necessary, isolate them in a dedicated compat package and load them only behind a mod-present check. Do not put Sable interfaces on core block entity classes unless Sable becomes a required dependency.

### Proposed Package Layout

```text
src/main/java/<group>/largesteam/
  LargeSteam.java
  registry/
    ModBlocks.java
    ModBlockEntities.java
    ModItems.java
    ModCreativeTabs.java
    ModConfigs.java
  content/engine/
    LargeSteamEngineControllerBlock.java
    LargeSteamEngineControllerBlockEntity.java
    LargeSteamEnginePartBlock.java
    LargeSteamOutputShaftBlock.java
    LargeSteamOutputShaftBlockEntity.java
    LargeSteamEngineStructure.java
    LargeSteamEngineScanner.java
    LargeSteamPressureModel.java
    LargeSteamOutputAllocator.java
    LargeSteamGoggleTooltip.java
  compat/create/
    CreateStressRegistration.java
    CreatePonderPlugin.java
  compat/simulated/
    SimulatedMovementCompat.java
  data/
    recipes/
    lang/
    models/
```

### Core Block Entity Model

Use one authoritative controller.

`LargeSteamEngineControllerBlockEntity`

- Owns the multiblock state.
- Caches part positions, dimensions, output couplings, and scores.
- Stores water/steam/pressure data.
- Exposes NeoForge fluid capability for water input.
- Reads heat through Create-compatible heat APIs.
- Allocates total generated capacity across outputs.
- Sends compact sync packets or uses `SmartBlockEntity` sync.
- Invalidates on neighbor/part changes, chunk unload, disassembly, or wrench refresh.

`LargeSteamOutputShaftBlockEntity`

- Extends `GeneratingKineticBlockEntity`.
- Acts as the only kinetic source block in the structure.
- Receives speed and capacity allocation from the controller.
- Implements `getGeneratedSpeed()`.
- Overrides `calculateAddedStressCapacity()`.
- Calls `updateGeneratedRotation()` when speed/capacity changes.
- Returns zero output when the controller is invalid, unpressurized, unloaded, or broken.

Do not replace vanilla shafts with powered shafts. Add a dedicated output coupling block that visually and mechanically behaves like a shaft. This avoids fragile interactions with Create's own `PoweredShaftBlock`.

### Multiblock Scanner

Use a graph scanner rather than a fixed pattern.

Rules:

- Scan starts at controller.
- Accept only blocks tagged as our engine parts plus explicitly allowed Create blocks if needed.
- Limit max blocks and max radius through server config.
- Require all output couplings to be within the validated graph.
- Reject multiple controllers in one graph.
- Reject unloaded chunks.
- Reject structures crossing dimensions or sublevel boundaries.
- Cache a version/hash of block states to avoid expensive full rescans every tick.

Revalidation triggers:

- Controller wrench interaction.
- Neighbor update on engine parts.
- Part placed/broken.
- Output coupling added/removed.
- Lazy tick if dirty.
- Server load of controller block entity.

### Steam and Heat Model

Do not depend on Create `BoilerData` in v1.

Implement our own pressure model:

- Fluid storage: water only.
- Input: NeoForge `Capabilities.FluidHandler.BLOCK`.
- Heat: read blocks below fireboxes/boilers using Create's public boiler heater lookup path where possible.
- Pressure: generated from heat and water, consumed by kinetic load.
- Reserve: steam chamber volume increases buffer size.
- Safety: if pressure exceeds configured limit, vent steam and reduce efficiency rather than explode in v1.

Later compatibility spike:

- Investigate an adapter for Create fluid tanks/boilers.
- Only ship this if it can be done without unstable mixins or if the mixin surface is small and covered by tests.

### Kinetic Output

The output shaft must be a real Create kinetic generator.

Behavior:

- If controller is valid and pressure is sufficient, each output generates the selected RPM.
- If the attached network overpowers the output, follow Create generator rules and avoid sign conflicts.
- Direction is set by controller scroll behavior or wrench interaction.
- Capacity is allocated so the sum of all outputs equals the controller's calculated total.
- Breaking an output removes its allocation and refreshes the controller.
- Breaking the controller immediately sets all outputs to zero.

Anti-exploit rules:

- Total capacity is calculated once on the controller.
- Outputs receive shares from a deterministic allocator.
- Adding more output shafts cannot multiply SU.
- Output BEs persist their controller position but revalidate it before generating.
- If the controller is missing, unloaded, or not assembled, output speed and capacity are zero.

### Create Integration

Use Create APIs and conventions:

- `GeneratingKineticBlockEntity` for generator output.
- `IRotate`/shaft-facing behavior for output blocks.
- `IHaveGoggleInformation` for controller and output diagnostics.
- `BlockStressValues` registration for baseline tooltip metadata.
- Create's wrench behavior for assembly, rotation, and disassembly.
- Ponder scenes for player education.
- Datagen for recipes, blockstates, models, tags, loot tables, and lang.

Avoid:

- Direct mutation of Create kinetic networks outside documented Create patterns.
- Reusing Create's `PoweredShaftBlockEntity` directly.
- Mixin changes to Create's steam engine or boiler in v1.

### Aeronautics and Sable Compatibility

Required v1 compatibility work:

- Ensure every engine block is movable unless intentionally config-disabled.
- Do not add blocks to `#simulated:non_movable`.
- Register Create `BlockMovementChecks` so engine parts attach to each other during normal Create movement.
- If Simulated/Aeronautics is present, register `SimBlockMovementChecks` so Sable assembly treats engine parts as one connected structure.
- Test the engine inside a Sable sublevel with Aeronautics propellers attached through shafts.
- Verify block entity NBT, kinetic state, pressure state, and output allocation survive assembly/disassembly and world reload.

Optional future Sable work:

- A turbine exhaust block that implements a Sable actor and applies a small point force.
- A force debug integration using Sable force groups.
- A sublevel-aware gauge using Sable position transforms.

Do not make direct Sable force output part of the main engine until the Create kinetic path is stable.

## Development Phases

### Phase 0: Decisions - Complete

Deliverable: final technical decisions before code.

- [x] Pick mod id, package group, and display name: `full_steam_ahead`, `dev.gustavo.fullsteamahead`, `Create: Full Steam Ahead`.
- [x] Decide whether Aeronautics/Sable are optional or required at runtime: optional runtime compatibility targets.
- [x] Decide art direction and block list: base Create-inspired copper/andesite/brass visual direction.
- [x] Decide default balance constants.
- [x] Decide max multiblock size for v1: hard `3x3x3`, maximum 27 blocks.

### Phase 1: Project Scaffold - Complete

Deliverable: bootable NeoForge 1.21.1 addon project.

- [x] Create NeoForge ModDevGradle project.
- [x] Add Create Maven dependencies from official Create docs.
- [x] Add optional Sable/Aeronautics metadata as runtime compatibility targets.
- [x] Add Java 21 toolchain.
- [x] Add `neoforge.mods.toml` dependency on Create.
- [x] Add client, server, and data run configs.
- [x] Verify `compileJava`, `processResources`, `build`, and `runClient` startup.

### Phase 2: Registration and Minimal Blocks - Complete

Deliverable: blocks appear in-game and can be placed.

- [x] Register controller, casing, boiler drum, firebox, steam cylinder, piston rod, flywheel, output coupling, and governor.
- [x] Add simple Create-texture placeholder block models and item models.
- [x] Add loot tables, lang entries, mining tags, and creative tab entry.
- [x] Verify all nine blocks appear in-game and are placeable.
- [ ] Recipes are intentionally deferred until mechanics and balance exist.
- [ ] Wrench behavior is deferred until multiblock and kinetic behavior need it.

### Phase 3: Multiblock Formation

Deliverable: controller can validate a structure.

- [ ] Implement scanner and `LargeSteamEngineStructure`.
- [ ] Add validation error messages.
- [ ] Add dirty/revalidate lifecycle.
- [ ] Add controller NBT serialization.
- [ ] Add goggle tooltip showing structure status.
- [ ] Add tests for valid, invalid, too large, duplicate controller, missing output, and unloaded chunk cases.

### Phase 4: Steam Pressure Model

Deliverable: formed engine has water, heat, pressure, and efficiency.

- Add water tank capability.
- Add heat scanning.
- Add pressure generation and decay.
- Add pressure consumption under load.
- Add config for capacities, heat scaling, water use, and pressure limits.
- Add goggles and particles/sounds for active/venting states.

### Phase 5: Kinetic Output

Deliverable: engine powers Create shafts.

- Implement `LargeSteamOutputShaftBlockEntity`.
- Connect controller allocation to output BEs.
- Add speed and direction controls.
- Add dynamic stress capacity.
- Add network update handling.
- Test with shafts, gearboxes, belts, stressometers, and standard Create consumers.

### Phase 6: Aeronautics/Sable Compatibility

Deliverable: engine works on a moving aircraft/ship.

- Register movement checks.
- Test assembly into Sable sublevel.
- Test engine powering Aeronautics propellers.
- Test save/load while assembled.
- Test disassembly and reassembly.
- Test multiple engines on one craft.
- Profile large craft tick cost.

### Phase 7: UX, Rendering, and Ponder

Deliverable: polished player-facing feature.

- Add animated flywheel/piston visuals.
- Add pressure gauge model/overlay.
- Add steam particles and sounds.
- Add Ponder scenes: basic build, water/heat, output shafts, aircraft use.
- Add JEI/EMI-friendly recipes and tags.
- Add clear tooltips for every component.

### Phase 8: Balance and Server Config

Deliverable: configurable and multiplayer-safe behavior.

- Add server config for size limits, capacity, water use, heat scaling, RPM caps, output limits, and optional failure behavior.
- Add client config for particles/sounds.
- Add default recipes balanced against Create steam engines.
- Test with common Create addon packs.

### Phase 9: Hardening

Deliverable: release candidate.

- Dedicated server startup test.
- Client/server sync test.
- World reload test.
- Schematic placement test.
- Create contraption movement test.
- Sable/Aeronautics sublevel assembly test.
- Performance test with many engines and one very large craft.
- Crash audit for null levels, unloaded chunks, missing optional mods, and invalid NBT.

### Phase 10: Release

Deliverable: published addon.

- Build release jar.
- Publish source and license.
- Publish Modrinth/CurseForge metadata.
- Include tested version matrix.
- Document known incompatibilities.
- Include example builds and Ponder scenes.

## Test Matrix

Minimum automated tests:

- Valid small engine forms.
- Valid large engine forms.
- Missing controller fails.
- Duplicate controller fails.
- Missing output fails.
- Too-large engine fails.
- Output capacity does not duplicate when adding outputs.
- Output stops when controller breaks.
- Output resumes after save/load.
- Water input changes pressure.
- Heat input changes pressure.
- No water means no sustained output.
- Direction reversal does not destroy networks unless Create's generator conflict rules require it.

Manual compatibility tests:

- Create shafts and gearboxes.
- Create stressometer/speedometer.
- Create belts and mechanical crafters.
- Aeronautics wooden/andesite/smart propellers.
- Assembled Sable sublevel ship.
- Assembled Sable aircraft.
- Dedicated server with no client-only classes loaded server-side.
- Optional dependency absent: Create-only instance must still start.

Performance targets:

- Controller does not rescan every tick.
- A 200-block engine should not cause measurable server spikes after validation.
- Multiple outputs should not add more than O(outputs) work per tick.
- Structure scan should be bounded by config and fail gracefully.

## Main Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Create boiler internals change | Breaks integration | Avoid in v1; own pressure model |
| Multiple outputs duplicate SU | Severe exploit | Controller-owned total capacity allocator |
| Large scans lag servers | Server TPS drop | Dirty cache, max size/radius, lazy validation |
| Sable classloading if absent | Startup crash | Optional compat isolation |
| Sable assembly misses engine parts | Engine splits on ship assembly | Register Create and Simulated movement checks |
| Kinetic network conflicts | Shafts break or detach | Follow `GeneratingKineticBlockEntity` patterns |
| Block entities lose state on sublevels | Engine stops on craft | Save/load and assembly tests early |
| Rendering too expensive | Client FPS drop | Simple v1 renderer, Flywheel visuals after logic is stable |

## Definition of Done for v1

- The addon starts on NeoForge 1.21.1 with Create 6.0.10.
- A player can build a large multiblock steam engine with at least 20 blocks.
- The engine forms, stores water, reads heat, builds pressure, and outputs Create rotation.
- The output shaft powers normal Create kinetic networks.
- The engine can power Aeronautics propellers through shafts.
- The engine works on an assembled Sable/Aeronautics craft.
- Breaking parts invalidates safely.
- Adding output shafts never duplicates total generated SU.
- Save/load works on singleplayer and dedicated server.
- Ponder or in-game tooltips explain construction without requiring external docs.

## Recommended Next Step

Create the NeoForge addon scaffold and implement a minimal controller plus one output shaft. Do not start with full rendering or direct Sable physics. The first proof must be: a formed multiblock generates rotational power, a shaft turns, and an Aeronautics propeller receives that rotation on a Sable craft.
