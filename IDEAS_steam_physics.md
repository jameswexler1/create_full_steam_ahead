# Idea: pressure/volume/temperature steam physics

Living scratchpad for redesigning steam from a flow-only model into a
pressure/heat/volume model. Read + edit freely as we go. Nothing here is committed to yet.

Status: **IMPLEMENTED (v1).** Locked design below; flow-only model replaced.

---

## LOCKED DESIGN (shipped)

Decisions (2026-06-05):
- **Replace** the flow-only model outright (no opt-in flag).
- Specs from **boiler geometry + heat level**. **No** regulator/throttle block (maybe later).
- **Continuous** RPM (0..64), not snapped tiers.

Model (`content/steam/SteamPhysics.java`), two intrinsic boiler inputs:
- `T` = water-gated heat units (compact: blaze-burner units 0..18; piped: `min(activeHeat, waterMax)`).
- `V` = boiler vessel volume = `width^2 * height` blocks.
- `pressure p = (T/T_ref) / (V/V_ref)`  (config `steamPhysics.temperatureReference=9`, `volumeReference=9`).
- `RPM = clamp(rpmReference * p, 0, 64)`  (`rpmReference=64`), `p` clamped `[pressureMin 0.1, pressureMax 4.0]`.
- `SU(direct) = clamp(suReference * (T/T_ref) * (V/V_ref), 0, suMax)`  (`suReference=147456`, `suMax=2_000_000`).
- Identity: `SU*RPM ∝ T^2` → **heat sets power; boiler shape is a (power-neutral) torque↔speed trade.**
  - Wide/large boiler → big V → low p → low RPM, high SU (torque).
  - Tall/thin boiler → small V → high p → high RPM, low SU (speed).
  - **Baseline = nine NORMAL burners on a 3×3×1 boiler** (T=9): pressure **1.0, RPM 64, SU 147456**.
    Blaze cakes (T=18) raise pressure to **2.0** but RPM is capped at 64, so they only **double SU
    (294912)**. (`T_ref=9` = nine normal burners = the 100% reference; cakes are 200% pressure/SU.)

Propagation:
- **Direct/compact** engine reads its boiler geometry locally (`PistonHeadBlockEntity.calculateDirectSteamOutput`).
- **Piped**: `BoilerOutletBlockEntity` computes its boiler's `p` AND capacity `SU = suRef·(T/T_ref)·(V/V_ref)`
  (same formula as direct) and reports both to every reachable assembled `SteamInletBlockEntity` during
  the steam-push BFS (`reportSupply`, max-wins, decays after 10 ticks). **Capacity is split evenly
  across the inlets the boiler feeds** so multiple engines share one boiler's power (no duplication,
  power-conserving). The piston reads `inlet.getSupplyPressureRatio()` → RPM and
  `inlet.getSupplyCapacitySu()` → SU, so piped engines get the full volume↔pressure trade: small
  boiler = high pressure/RPM, low SU; big boiler = low pressure/RPM, high SU. Unknown supply falls
  back to pressure 1.0 and flow-based SU.

Surfaced on goggles: outlet shows pressure + volume + heat; piston shows pressure + RPM + SU.

Open follow-ups (not done): optional regulator block; piped-mode strict power conservation;
tuning `pressureMin/Max`, `rpmReference`, `suReference` after in-game testing; ponder/JEI docs.

---

## Current model (what we have today)

Flow-only, single scalar. (`BoilerOutletBlockEntity`, `PistonHeadBlockEntity`, `FullSteamConfig`,
`README.md`.)

- Production: `productionRate = heatUnits * steamPerHeatUnit` mB/t, where
  `heatUnits = min(activeBurnerUnits, waterHeatLevel) * boilerHeight`. Pure rate.
- Consumption → output: `heatUnits = ceil(consumedMb / steamPerHeatUnit)`, `SU = consumedMb *
  suPerSteamMb` (capped), `RPM = tier(consumedMb)`. Tiers 16/32/48/64.
- **No pressure, no volume, no temperature.** Boiler size only scales total mB/t. Big vs small
  boiler differ only in throughput, never in the RPM-vs-SU character.

## What we want (user intent)

Steam shouldn't be one number. Bring **heat + pressure (especially) + volume** into play so boiler
*shape/size* gives different "specs", like a torque-vs-speed tradeoff:
- **Big boiler** → large steam volume → more **SU** but lower **RPM** (slow, torquey).
- **Small high-pressure boiler** → high **RPM** but less SU (fast, weak).
- Power (SU × RPM) roughly conserved for a given energy input; pressure picks the operating point.

---

## How Clockwork (VS) does it — inspiration, NOT to copy

Clockwork = Create × Valkyrien Skies airships. Source: `github.com/ValkyrienSkies/Clockwork`,
package `org.valkyrienskies.clockwork.content.logistics.gas`. The actual gas thermodynamics live in
a **separate library, Kelvin** (`org.valkyrienskies.kelvin`) — Clockwork just wires nodes into it.

Model (ideal-gas, graph-based):
- The world has a **gas network** of nodes (ducts, tanks, ports) — `duct/DuctPipeNode.kt` uses
  Kelvin `PipeDuctNode`/`ILeakNode`. Each node has a **volume**; gas in it has **mass (amount)** and
  **temperature** → Kelvin computes **pressure** (PV = nRT style). Gas flows down pressure gradients
  through ducts/valves; leaks vent (their `pockets/nozzle/LeakParticle`).
- Gas **reactions** are datapack-driven (`data/vs_clockwork/kelvin_reactions/*.json`,
  `recipes/gas_crafting/*.json`) — combustion etc. add heat/mass (phlogiston, stellane…).
- **SteamGenerator** (`generation/steam_generator/SteamGeneratorBlockEntity.kt`) sits on a Create
  Fluid Tank boiler and each tick injects steam gas into the network:
  - `efficiency = clamp(tank.boiler.getEngineEfficiency(totalTankSize), 0, 1)` (reuses Create boiler
    heat/water),
  - `mass = maxMass(0.1) * efficiency`,
  - `temperature = 80 * (max(0.25, activeHeat) * 100)^0.34` — **hotter boiler ⇒ hotter steam**,
  - `network.addGasAtTemperature(node, steam, mass, temperature)`; pressure cap
    `maxPressurePerLevel = 17_000_000 / 8`.
- **GasEngine** (`engine/GasEngineBlockEntity.kt` + `mixin/.../MixinSteamEngineBlockEntity`) reads
  the **temperature at its node** → `efficiency = (temp - 80) / 290 / 6` → drives Create's steam
  engine shaft speed/stress. Power scales with **delivered gas temperature/pressure**, not raw mB.
- Heaters/coal burner add heat; heatsink stores it; compressor adds pressure; exhaust vents.

Why we won't clone it: Kelvin is a heavy per-tick gas-network graph tied to VS, with its own duct
blocks and reaction datapacks. We want the *feel* (pressure/volume specs), not a second logistics
network. Our steam already rides **Create's own fluid pipes**, which we should keep.

---

## Proposed model for us (lightweight, no gas-network library)

Keep steam on Create pipes. Add a few derived scalars instead of a node graph.

1. **Steam carries pressure + temperature, not just mB.** Represent per **boiler-outlet / pipe
   path**, not per block, to stay cheap:
   - `temperature ∝ activeHeat` (burner heat / blaze-cake), like Clockwork's `80*(heat*100)^0.34`.
   - `pressure = f(producedMass, networkVolume, temperature)` — ideal-gas-ish: more mass or hotter
     ⇒ higher pressure; more volume ⇒ lower. `networkVolume` ≈ reachable pipe/tank block count (or a
     cheaper proxy: boiler footprint/height + a configured nominal).
2. **Engine output is a torque–speed curve, not a tier table:**
   - `SU (torque) ∝ delivered steam mass/flow` (how much steam you can move),
   - `RPM (speed) ∝ delivered pressure` (clamped to 64),
   - bounded so `SU × RPM ≲ energyInput` ⇒ raising one lowers the other.
3. **Boiler specs emerge from geometry:**
   - wide/tall boiler = big volume = low pressure, high mass ⇒ high SU / low RPM,
   - small/throttled boiler (or a new "pressure regulator"/throttle block) = high pressure ⇒ high
     RPM / low SU.
4. Reuse what exists: Create boiler `activeHeat`/water via `BoilerData` (already read), valve-aware
   pressure traversal (already implemented), the steam buffer, goggle overlays (add pressure/temp
   lines), and the leak handler (could vent more / hotter at high pressure → ties into the new scald
   damage).

### Possible new/changed pieces
- A `SteamState`/`SteamParcel` value (mB + pressure + temperature) flowing outlet → inlet.
- Outlet computes pressure from heat + estimated network volume; inlet reports delivered pressure to
  the piston head.
- Piston head output = `torqueFromFlow × speedFromPressure` curve instead of `tier(mB)`.
- Optional **throttle/regulator** block to trade SU↔RPM manually (the "small high-pressure" lever).
- Config: enable-new-model flag, pressure curve constants, volume proxy, power cap.

---

## Feasibility & effort

- **Simplified pressure model: feasible / medium effort.** It's mostly math in
  `BoilerOutletBlockEntity` (produce pressure) and `PistonHeadBlockEntity` (consume → torque-speed
  curve) + a richer steam value object + goggle/config. No new logistics network.
- **Full Kelvin-style per-node gas sim: not recommended.** High complexity + per-tick graph cost +
  would fork us off Create pipes. Only worth it if we ever want true multi-gas reactions.

### Risks / unknowns
- Create pipe networks don't cleanly expose "volume" — need a proxy (reachable-block count is
  O(network) per change; cache it like the existing pressure traversal). 
- Rebalancing the whole SU/RPM table; keep parity so a stock boiler ≈ today's output at default.
- Backwards compat: gate behind a config/model flag; old worlds keep working.
- Direct-compact mode (no pipes) needs its own pressure proxy (boiler size directly).
- Multiplayer/airship (Sable) — pressure state must be NBT-safe like the rest.

---

## Open decisions (fill as we discuss)
- [ ] Pressure scope: per outlet, per pipe path, or per connected network?
- [ ] `networkVolume` source: reachable-block count vs boiler-geometry proxy vs configured nominal?
- [ ] Exact torque–speed curve + whether `SU × RPM` is hard-capped (energy conservation) or soft.
- [ ] New throttle/regulator block, or geometry-only specs?
- [ ] Replace the flow-only model outright, or add as an opt-in "realistic steam" config mode?
- [ ] Keep RPM tiers (snap to 16/32/48/64) or go continuous?

## Reference files
- Ours: `content/steam/BoilerOutletBlockEntity.java`, `content/piston/PistonHeadBlockEntity.java`,
  `content/steam/SteamInletBlockEntity.java`, `config/FullSteamConfig.java`, `PLAN.md` (Balance).
- Clockwork (cloned to `/tmp/clockwork_src`): `content/logistics/gas/generation/steam_generator/
  SteamGeneratorBlockEntity.kt`, `engine/GasEngineBlockEntity.kt`,
  `mixin/content/gas_engine/MixinSteamEngineBlockEntity.java`, `duct/DuctPipeNode.kt`,
  `data/vs_clockwork/kelvin_reactions/*`.
