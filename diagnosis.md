# Steam Smoothing Diagnosis

Date: 2026-06-06

This document diagnoses the wrong runtime behavior observed after the steam smoothing / inertia implementation. It is written for the developer who will make the next code pass. No source code was changed while preparing this diagnosis.

## Build Status

The current implementation compiles:

```sh
env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build
```

Result: passed.

Relevant current commit:

```text
290310a feat: steam pressure and boiler heat smoothing (inertia)
```

## Player Observations

Observed during manual testing:

- Breaking a pipe so steam leaks from an open pipe end sometimes does not reduce pressure.
- In a large boiler setup, pressure continued rising and stabilized around `1 MpN/m^2`.
- Reconnecting the same system to 8 engines made pressure go down.
- Leaking directly out of the `boiler_outlet` behaved correctly in some tests.
- Leaking through a pipe end was the problematic case.
- Adding a second outlet to a boiler sometimes made pressure go down. This was obvious on larger systems and less obvious on smaller boilers.
- Valve branch opening/closing behaved correctly.
- Partial water starvation reduced pressure correctly.

The behavior is not caused by old saved boiler state. It is explained by current code paths.

## Diagnosis 1: Pipe-End Leaks Are Relief Valves, Not Atmospheric Ruptures

The main issue is in `SteamNetworkManager.stepAndApply`.

Relevant code:

```java
double prePressure = SteamPhysics.pressurePn(network.storedMb, tempK, volume);
int ventDrained = 0;
if (!network.openEnds.isEmpty()) {
    int ventEach = SteamPhysics.ventMb(prePressure);
    int atmosphericRelief = SteamPhysics.drainToPressureMb(
            network.storedMb,
            tempK,
            volume,
            SteamPressure.rated()
    );
    int requestedDrain = Math.min(network.storedMb,
            Math.max(ventEach * network.openEnds.size(), atmosphericRelief));
    if (requestedDrain > 0) {
        ventDrained = drainFromNetwork(level, network, requestedDrain);
    }
}
```

This means an open pipe does **not** currently drain toward atmospheric or zero pressure. It drains toward `SteamPressure.rated()`, which is `1.0 MpN/m^2`.

That exactly explains the observed plateau:

```text
open pipe leak -> pressure rises or falls toward roughly rated pressure
rated pressure = 1.0 MpN/m^2
observed plateau = around 1 MpN/m^2
```

So the pipe leak is not behaving like a broken open steam line. It is behaving like a pressure relief path whose target pressure is rated operating pressure.

## Why Pressure Can Rise While Leaking

`SteamPhysics.ventMb(prePressure)` is also limited by `steamPhysics.ventCoefficient`.

Current default:

```java
DEFAULT_STEAM_VENT_COEFFICIENT = 120.0D;
```

The formula:

```java
ventMb = ventCoefficient * pressureFactor
```

At `627 kpN/m^2`, pressure factor is about:

```text
627,000 / 1,000,000 = 0.627
```

So one open pipe drains roughly:

```text
120 mB/t * 0.627 = 75 mB/t
```

But 8 full engines consume:

```text
8 engines * 90 mB/t = 720 mB/t
```

So if the boiler can feed about 8 engines, reconnecting those engines can drain far more steam than a single open pipe leak currently drains below rated pressure.

This explains the reported behavior:

```text
one open pipe leak -> drains ~75 to 120 mB/t, depending on pressure
8 engines -> can drain up to 720 mB/t
therefore pressure can rise with a leak but fall with engines
```

From a gameplay perspective, this feels backwards. A full broken pipe should be a dramatic pressure loss, not weaker than a few consumers.

## Why Direct Outlet Leaks Behave Differently

Direct outlet leaks and open pipe-end leaks are handled by different code paths.

In `BoilerOutletBlockEntity.pushSteam`, when there is no pipe or target in front of the outlet, the outlet uses:

```java
return ventSteam(worldPosition, facing, remaining);
```

`ventSteam(...)` drains the outlet buffer directly:

```java
int leaked = steamBuffer.drain(maxAmount, IFluidHandler.FluidAction.EXECUTE).getAmount();
```

So a direct open outlet can dump all remaining outlet steam for that tick.

But if there is a pipe and that pipe has an open end, `BoilerOutletBlockEntity.applyPipePressure` only marks the pipe end as an open endpoint:

```java
if (FluidPropagator.isOpenEnd(level, node.pos(), direction)) {
    // Pressure marker only; the network manager physically vents + spawns the steam cloud.
    pipe.addPressure(direction, false, pressure);
    hasEndpoint = true;
    openEnd = true;
}
```

The physical drain for that pipe leak then happens in `SteamNetworkManager`, where it uses the rated-pressure relief logic described above.

Therefore:

```text
direct outlet open to air -> direct buffer dump
pipe open to air -> capped network relief toward rated pressure
```

These two should probably be aligned. A full open pipe should not be less severe than opening the outlet directly.

## Detection Caveat

There are two possible classes of pipe-leak bugs:

1. The open pipe end is not detected.
2. The open pipe end is detected, but its drain logic is too weak.

Based on the observed plateau around `1 MpN/m^2`, the second case is the likely one.

How to distinguish in-game:

- If goggles/status show `Steam venting`, or leak particles appear at the open pipe end, then the open end is being detected.
- If there are no particles and no `Steam venting` status, then the BFS/open-end detection path should be investigated separately.

In the reported case, the pressure behavior matches "detected but too weak / wrong target".

## Recommended Fix For Pipe Leaks

The design should distinguish between:

- a future controlled vent valve, which may intentionally regulate pressure around rated;
- a broken/open pipe end, which should dump steam toward atmosphere.

For open pipe ends, do not drain toward `SteamPressure.rated()`. Drain toward a low atmospheric target.

Suggested new config:

```text
steamPhysics.openPipeTargetPressure = 0 or 25_000 pN/m^2
```

Then use:

```java
int atmosphericRelief = SteamPhysics.drainToPressureMb(
        network.storedMb,
        tempK,
        volume,
        FullSteamConfig.openPipeTargetPressure()
);
```

If using `0`, the open pipe tries to dump all stored network steam each tick. That is aggressive, but it matches a full-bore pipe rupture and the player's expectation that pressure collapses.

If using a small nonzero value such as `25 kpN/m^2`, the pressure does not mathematically become exactly zero, but it still collapses dramatically.

The fixed drain should still use the real target/pre-smoothed pressure for venting. Do not base physical venting only on smoothed effective pressure, or smoothing could hide a runaway sealed system.

Recommended logic shape:

```java
double ventPressure = Math.max(prePressure, effectivePressure);
int ventEach = SteamPhysics.ventMb(ventPressure);
int atmosphericRelief = SteamPhysics.drainToPressureMb(
        network.storedMb,
        tempK,
        volume,
        FullSteamConfig.openPipeTargetPressure()
);
int requestedDrain = Math.min(network.storedMb,
        Math.max(ventEach * network.openEnds.size(), atmosphericRelief));
```

Important: if `atmosphericRelief` drains to a low target, `ventEach` becomes a fallback/minimum, not the main limiter.

## Recommended Balance For Open Pipe Leaks

Current `ventCoefficient = 120 mB/t` is only slightly above one full engine's `90 mB/t`. That is too weak for a full broken pipe in a large boiler network.

Possible approaches:

1. Keep `ventCoefficient` for particles/damage intensity, but let `drainToPressureMb(..., openPipeTargetPressure)` do the real pressure relief.
2. Add a separate config:

```text
steamPhysics.openPipeVentCoefficient = 720 or higher
```

3. Scale open-pipe leak drain by network production:

```text
open_pipe_drain >= network.productionMb
```

For gameplay, option 1 is simplest and most physically intuitive. A full open pipe is atmosphere. It should drain as much stored steam as needed to collapse pressure, not merely consume one engine's worth of steam.

## Diagnosis 2: Adding A Second Outlet Can Temporarily Reduce Real Production

The second issue is caused by boiler heat smoothing being stored per `BoilerOutletBlockEntity`.

Relevant code in `BoilerOutletBlockEntity.calculateSteamBudget`:

```java
int targetHeat = FullSteamBoilerIntegration.usableHeatUnits(boiler);

if (FullSteamConfig.thermalInertiaEnabled()) {
    boolean dry = data.getMaxHeatLevelForWaterSupply() <= 0;
    double tau = targetHeat >= effectiveHeat
            ? FullSteamConfig.heatUpTauTicks()
            : dry ? FullSteamConfig.dryBoilerCoolDownTauTicks() : FullSteamConfig.coolDownTauTicks();
    effectiveHeat = SteamPhysics.approachExp(effectiveHeat, targetHeat, tau);
} else {
    effectiveHeat = targetHeat;
}

int totalProductionMb = SteamPhysics.productionMb(effectiveHeat, height);
int outletProductionMb = FullSteamBoilerIntegration.steamUnitsForOutlet(boiler, worldPosition, totalProductionMb);
```

`effectiveHeat` lives on the outlet block entity, not on the physical boiler.

This creates a real production loss when adding a second outlet to an already-hot boiler.

Example:

```text
one 3x3x1 boiler at full heat:
targetHeat = 9
existing outlet effectiveHeat ~= 9
outlet count = 1
existing outlet production = 90 mB/t
network total production = 90 mB/t
```

Now add a second outlet:

```text
existing outlet effectiveHeat ~= 9
new outlet effectiveHeat = 0
outlet count = 2
```

Existing outlet computes:

```text
totalProduction from its local effectiveHeat = 90 mB/t
share across 2 outlets = 45 mB/t
```

New outlet computes:

```text
totalProduction from its local effectiveHeat ~= 0 mB/t
share across 2 outlets = 0 mB/t initially
```

Actual network production becomes:

```text
45 + 0 = 45 mB/t
```

It should have remained about:

```text
90 mB/t total, split as 45 + 45
```

So the pressure drop after adding a second outlet is not just cosmetic. It is a real temporary production cut caused by per-outlet thermal state.

This also explains why it may be more visible on large boiler systems: the transient production loss is much larger in absolute terms.

## What The Two-Outlet Test Was Supposed To Prove

The intended test is:

1. Build one hot boiler with one outlet connected to a steam network.
2. Let it stabilize.
3. Add a second outlet to the same physical boiler.
4. Connect the second outlet to the same pipe network, or at least do not leave it as an uncontrolled open leak unless that is the test.
5. Total boiler production should stay governed by one physical boiler budget.

Expected:

```text
one boiler with two outlets should split one shared production budget
it should not duplicate production
it should not lose production just because a new outlet has no thermal history
```

Current behavior can fail the third point because the new outlet's `effectiveHeat` starts cold.

If the second outlet is left open to air or connected to a different open branch, pressure going down is expected. In that case, the second outlet is a new leak/load.

## Recommended Fix For Shared Boiler Heat

Thermal inertia should belong to the physical boiler controller, not to each outlet.

Recommended structure:

```text
Map<Level, Map<BlockPos, BoilerThermalState>>
```

Key:

```text
boiler controller BlockPos
```

State:

```text
double effectiveHeat
long lastSeenGameTime
```

Then all outlets attached to the same boiler read the same smoothed heat for that boiler.

The production calculation should become:

```text
targetHeat = usableHeatUnits(boiler)
effectiveHeat = sharedThermalStateForBoiler.approach(targetHeat)
totalProductionMb = productionMb(effectiveHeat, boilerHeight)
outletProductionMb = split totalProductionMb across attached outlets
```

That preserves:

- one boiler;
- one heat inertia curve;
- one total production budget;
- deterministic outlet splitting.

Do not store independent `effectiveHeat` per outlet.

## Persistence Options For Shared Boiler Heat

Possible implementations:

1. Ephemeral manager state only.

```text
WeakHashMap<Level, Map<BlockPos, BoilerThermalState>>
```

Pros:

- Simple.
- No custom data on Create tank block entities.
- Correct while the world is running.

Cons:

- On world reload, heat state is lost unless seeded.

Recommended seed:

```text
if no thermal state exists, initialize effectiveHeat to targetHeat
```

This avoids cold-starting existing hot boilers after reload.

2. Store state on a canonical outlet.

Pros:

- Uses existing block entity NBT.

Cons:

- Fragile when the canonical outlet is removed.
- Still outlet-owned, not truly boiler-owned.

3. Attach capability/data to Create Fluid Tank controller.

Pros:

- Most conceptually correct.

Cons:

- More invasive and likely not worth it for this addon stage.

Recommended for now: option 1 with target seeding and stale cleanup.

## Diagnosis 3: Network Pressure Smoothing State Is Outlet-Derived

`SteamNetworkManager.smoothEffectivePressure` reads previous pressure as the max pressure among member outlets:

```java
double prevEffective = 0.0D;
for (BoilerOutletBlockEntity outlet : network.outlets) {
    prevEffective = Math.max(prevEffective, outlet.getNetworkPressurePn());
}
```

This is acceptable as a first implementation, but it has edge cases:

- Merging a high-pressure outlet network with a low-pressure outlet network inherits the highest pressure instantly.
- Adding a stale outlet can seed pressure from that outlet's last remembered pressure.
- Splitting networks does not keep distinct per-network pressure history except through whichever outlets remain.

This is probably not the cause of the pipe-leak plateau. The plateau is much more directly explained by the rated-pressure vent target. But a future polish pass should replace outlet-derived pressure state with a real `NetworkDynamicsState` keyed by outlet set or network membership.

## Recommended Fix For Network Pressure State

Use a persistent manager state:

```text
Map<Level, Map<NetworkKey, NetworkDynamicsState>>
```

`NetworkKey` can initially be a sorted immutable list/set of outlet positions in the connected network.

`NetworkDynamicsState`:

```text
double effectivePressurePn
double lastTargetPressurePn
long lastSeenGameTime
```

When a network changes:

- exact key exists: reuse it;
- no exact key: seed from overlapping prior state;
- no overlap: seed from current target pressure or 0 depending desired startup behavior.

This is not urgent for the leak bug, but it will make smoothing more predictable.

## Priority Fix Order

1. Fix open pipe leak semantics:
   - open pipe drains toward atmospheric/near-zero pressure, not rated pressure;
   - direct outlet leak and pipe-end leak should have comparable pressure consequences.

2. Fix shared boiler thermal state:
   - move `effectiveHeat` from per-outlet state to per-boiler-controller state;
   - split one shared smoothed production budget across all outlets.

3. Add/adjust config:
   - `openPipeTargetPressure`;
   - possibly `openPipeVentCoefficient`;
   - keep existing `ventCoefficient` only if it still has a clear purpose.

4. Improve pressure smoothing persistence:
   - replace max-outlet-pressure seeding with a real per-network dynamics state.

## Acceptance Tests

### Open Pipe Leak

- Build a large boiler capable of feeding many engines.
- Stabilize pressure with engines connected.
- Break a pipe so one pipe end is open.
- Confirm status shows venting and particles appear.
- Expected after fix: pressure falls dramatically below rated pressure instead of climbing/stabilizing at `1 MpN/m^2`.

### Direct Outlet vs Pipe End

- Test outlet facing open air.
- Test outlet connected to one pipe ending open.
- Expected after fix: both are severe leaks. They do not need identical particles, but pressure behavior should be comparable.

### Large Boiler With 8 Engines

- Stabilize a boiler feeding 8 engines.
- Replace the engines with one open pipe leak.
- Expected after fix: the open pipe should not be weaker than the engine bank in pressure behavior. Pressure should drop hard unless the design explicitly says one pipe leak has limited flow.

### Two Outlets On One Boiler

- Heat a boiler with one outlet until stable.
- Add a second outlet and connect it to the same pipe network.
- Expected after shared boiler thermal state fix: total production remains one boiler budget; it does not temporarily halve because the second outlet is cold.

### Second Outlet Left Open

- Heat a boiler with one connected outlet.
- Add a second outlet and leave it open.
- Expected: pressure may drop because the second outlet is a real leak. This should be documented as intentional.

### Small Boiler

- Repeat two-outlet and open-pipe tests with a small boiler.
- Expected: same qualitative behavior as large boiler, only smaller numbers.

## Short Conclusion

The current wrong pipe-leak behavior is not random and is not primarily caused by old worlds. Open pipe ends are currently implemented as a relief valve toward rated pressure with a small fixed drain coefficient. That is why pressure can keep rising while leaking and stabilize around `1 MpN/m^2`.

The current two-outlet oddness is also explainable: thermal inertia is stored per outlet. Adding a new outlet to a hot boiler introduces a cold `effectiveHeat` participant while also increasing the outlet count, temporarily reducing real total boiler production.

Both should be fixed before treating the smoothing implementation as stable.
