# Steam Pressure Smoothing and Inertia

This document defines the recommended smoothing pass for the current steam pressure model. It is a design plan, not an implementation patch.

## Current Problem

The current pipe-fed steam system computes pressure from the live network every server tick:

```text
pressure = gasConstant * storedSteamMb * temperatureK / networkVolumeM3
```

That formula is the right source of truth, but the system currently applies the result immediately. Minecraft runs at 20 ticks per second, so any topology or heat change can become visible in 0.05 seconds.

Observed symptoms:

- Breaking a Blaze Burner immediately changes usable heat and boiler temperature.
- Closing, blocking, or occluding part of a network instantly removes that branch volume from the pressure calculation.
- A large boiler can inject hundreds of mB/t into a suddenly smaller network, so pressure can jump from about `627 kpN/m^2` to above `1 MpN/m^2` in a fraction of a second.

The math is internally consistent, but the model has no thermal inertia, no pressure response time, and no transition handling when pipe networks split or shrink.

## Physical Reading

The current ideal-gas direction is still valid for gameplay. NASA's ideal-gas material describes the same relationship between pressure, volume, gas amount, and absolute temperature, and NIST steam data reinforces that real steam properties depend heavily on temperature and pressure. We do not need full steam tables for gameplay, but we should respect the qualitative behavior:

- Less volume with the same steam mass means higher pressure.
- More temperature with the same steam mass and volume means higher pressure.
- Steam systems do not feel instantaneous because boilers, pipes, and engine loads have thermal and mechanical inertia.

For game simulation, the best fit is a first-order response model. First-order systems are commonly represented with a time constant: after one time constant the value has moved most of the way toward the new target, but not all the way. That maps well to "pressure should approach the new value over a few seconds."

References used:

- NASA Glenn, Equation of State: https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/equation-of-state/
- NIST/ASME Steam Properties Database: https://www.nist.gov/publications/nistasme-steam-properties-version-30
- MIT OpenCourseWare first-order differential equation material: https://ocw.mit.edu/

## Design Goals

- Keep stored steam mass real. Do not fake or smooth the amount of steam in tanks, outlets, or inlets.
- Keep the ideal-gas target pressure as the real thermodynamic target.
- Smooth the pressure applied to engines, goggles, display links, warnings, and bursts.
- Smooth boiler heat and temperature so burner changes do not snap instantly.
- Preserve valve, vent, tank, and multi-boiler behavior.
- Avoid making smoothing exploitable. A sealed overpressure network should still become dangerous, only not instantly.
- Keep all timings server-configurable.

## Recommended Model

Use two pressures:

```text
targetPressure = immediate ideal-gas pressure from current mass, temperature, and volume
effectivePressure = smoothed pressure used by gameplay and display
```

`targetPressure` is recomputed every tick exactly as it is now. `effectivePressure` approaches it gradually:

```text
alpha = 1 - exp(-1 / tauTicks)
effective += (target - effective) * alpha
```

Use different time constants for rising and falling pressure:

```text
if targetPressure > effectivePressure:
    tau = pressureRiseTauTicks
else:
    tau = pressureFallTauTicks
```

Suggested defaults:

```text
pressureRiseTauTicks = 60   # 3 seconds
pressureFallTauTicks = 80   # 4 seconds
```

The fall time can be slightly slower because a boiler and pipe network should not lose heat/pressure the instant a burner disappears. Venting and explosions still drain real steam, so pressure can fall quickly when steam mass is actually removed.

Add an optional maximum per-tick pressure movement:

```text
maxPressureDeltaPerTick = 25_000 pN/m^2
```

This is a safety clamp. It prevents single-tick jumps from being visually absurd even if the target changes drastically. At this default, pressure can still move about `500 kpN/m^2` per second, which is fast but readable.

## Boiler Thermal Inertia

The boiler should also smooth heat before converting it to steam temperature and production.

Current logic:

```text
usableHeat = min(activeBurnerHeat, waterLimitedHeat)
temperatureK = baseK + usableHeat * temperaturePerHeatK
production = usableHeat * boilerHeight * steamPerHeatUnit
```

Recommended logic:

```text
targetHeat = min(activeBurnerHeat, waterLimitedHeat)
effectiveHeat += (targetHeat - effectiveHeat) * heatAlpha
temperatureK = baseK + effectiveHeat * temperaturePerHeatK
production = floor(effectiveHeat * boilerHeight * steamPerHeatUnit)
```

Suggested defaults:

```text
heatUpTauTicks = 80       # 4 seconds
coolDownTauTicks = 160    # 8 seconds
```

This means firing burners warms the boiler over a few seconds, and breaking burners cools it over a longer period.

Water should remain stricter than heat. If water supply drops to zero, production should fall quickly because a dry boiler should not keep producing useful steam for long.

Suggested water rule:

```text
if waterLimitedHeat == 0:
    use coolDownTauTicks = 20  # 1 second emergency production falloff
```

That keeps heat inertia without allowing dry-boiler exploits.

## Network State Tracking

`SteamNetworkManager` currently builds a temporary `Network` object every tick. To smooth pressure, it needs persistent state per connected pressure network.

Add a server-side state map:

```text
Map<Level, Map<NetworkKey, NetworkDynamicsState>>
```

`NetworkDynamicsState` should store:

```text
double effectivePressurePn
double lastTargetPressurePn
long lastSeenGameTime
Set<BlockPos> outletPositions
Set<BlockPos> memberHints
```

A good first `NetworkKey` is the sorted set of outlet positions in the connected network. This is stable when a storage branch is added or removed but the outlet side remains the same.

When a network splits into multiple outlet groups:

- If the exact key exists, reuse it.
- If not, seed the new state from the most recent old state that overlaps any outlet position.
- If no overlap exists, seed from the new target pressure.

Remove stale states after about 5 seconds:

```text
networkStateTtlTicks = 100
```

## What Uses Target Pressure vs Effective Pressure

Use `targetPressure` for:

- Internal diagnostics.
- Calculating how severe a physical imbalance is.
- Open-end vent drain strength, because open vents should relieve actual stored steam aggressively.

Use `effectivePressure` for:

- Engine output.
- Engine requested draw.
- Goggle and Display Link readouts.
- Overpressure warning.
- Burst threshold.
- Leak damage scaling if it is based on pressure rather than drained steam amount.

This gives the player time to perceive danger while preserving real steam mass. A sealed network with a very high target pressure will still drive `effectivePressure` upward rapidly and eventually burst.

Optional emergency rule:

```text
if targetPressure >= burstPressure * 2:
    use pressureRiseTauTicks / 3
```

This prevents absurdly overpressured networks from hiding behind smoothing for too long.

## Engine Behavior

Pipe-fed engines should use `effectivePressure` exactly where they currently use network pressure:

```text
requestedFlow = fullEngineFlowMb * pressureFactor(effectivePressure)
outputFactor = min(pressureFactor(effectivePressure), consumedFlow / fullEngineFlowMb)
```

This means:

- Engine output rises smoothly when pressure builds.
- Engine output falls smoothly when pressure falls.
- Fair distribution still works, because the manager still computes one shared per-engine draw cap.

The current stored-steam flow remains real. If engines consume less during low effective pressure, excess steam can still accumulate and raise target pressure.

## Venting Behavior

Open pipe ends should remain fast relief paths. If a pipe is open, the network should not burst just because effective pressure has not caught up yet.

Recommended vent calculation:

```text
ventPressure = max(targetPressure, effectivePressure)
ventDrain = drainToPressureMb(storedMb, tempK, volume, openPipeTargetPressure)
```

This preserves the intended behavior: broken/open pipes can drain enough steam to pull the network toward atmosphere before burst checks. A future controlled vent valve can deliberately use a higher target, but an accidentally open pipe should behave as a rupture.

## Burst Behavior

Warnings and bursts should use `effectivePressure`, not raw target pressure.

This is the main gameplay benefit:

- Closing a valve or occluding a branch does not explode instantly.
- The player sees pressure rising and has a short window to react.
- If the system remains sealed, it still reaches burst pressure.

For safety, add the emergency acceleration rule above when target pressure is extremely high.

## Direct Compact Mode

Direct compact mode should not be included in this smoothing pass beyond optional boiler thermal inertia. It is already documented as a simplified compatibility mode.

Recommended:

- Apply boiler heat smoothing to direct compact output only if it is easy to share the same boiler thermal state.
- Do not add full pressure storage, bursting, or topology smoothing to direct compact mode yet.

The pipe-fed network should remain the real pressure simulation.

## Config Additions

Add a new config group:

```text
steamSmoothing
```

Recommended options:

```text
enabled = true
pressureRiseTauTicks = 60
pressureFallTauTicks = 80
maxPressureDeltaPerTick = 25_000
thermalInertiaEnabled = true
heatUpTauTicks = 80
coolDownTauTicks = 160
dryBoilerCoolDownTauTicks = 20
emergencyPressureMultiplier = 2.0
emergencyPressureTauDivisor = 3.0
networkStateTtlTicks = 100
```

Also add `steamPhysics.openPipeTargetPressure = 0` so open pipe ends drain toward atmosphere instead of the rated operating pressure.

All should be server-side config.

## Implementation Plan

1. Add a small helper:

```java
public static double approachExp(double current, double target, double tauTicks) {
    if (tauTicks <= 0) {
        return target;
    }
    double alpha = 1.0D - Math.exp(-1.0D / tauTicks);
    return current + (target - current) * alpha;
}
```

2. Add `SteamSmoothingConfig` entries to `FullSteamConfig`.

3. Add `NetworkDynamicsState` to `SteamNetworkManager`.

4. After building a network and computing raw `targetPressure`, resolve the persistent state and compute `effectivePressure`.

5. Use `effectivePressure` for:

- `SteamPhysics.requestedFlowMb(...)`
- `inlet.applyNetworkState(...)`
- `outlet.applyNetworkState(...)`
- warning/burst checks

6. Keep venting based on `max(targetPressure, effectivePressure)`.

7. Add boiler thermal state keyed by boiler controller position:

```text
Map<BlockPos, BoilerThermalState>
```

This state should live in a small shared manager keyed by the physical boiler controller so multiple outlets on one boiler read the same heat curve and split one production budget.

8. Use smoothed effective heat for outlet production and temperature.

9. Add goggle/display diagnostic lines only if needed:

```text
Target pressure: ...
Effective pressure: ...
```

Default goggles should probably show only effective pressure to avoid confusing players.

## Acceptance Tests

- A stable 3x3x1 boiler with one engine still reaches about `1.0 MpN/m^2`, full SU, and 64 RPM.
- Breaking one active Blaze Burner causes output and pressure to decay over seconds, not instantly.
- Replacing the burner causes output and pressure to recover over seconds.
- Closing a valve on a large boiler network raises pressure visibly over time, not in one or two ticks.
- A sealed overproducing network still eventually warns and bursts.
- Opening a pipe still relieves pressure quickly enough to prevent burst if the relief path is large enough.
- Adding a passive Create Fluid Tank still increases network volume immediately, but displayed/effective pressure transitions smoothly.
- Multiple boilers on one pipe network share one effective pressure.
- Multiple outlets on one boiler do not create multiple thermal states or duplicate production.
- Display Link pressure values update smoothly and match goggles.

## Recommendation

Implement this as the next physics polish phase. Do not lower `gasConstant` as the primary fix. The current problem is not only pressure magnitude; it is response time. Lowering the constant would make pressure weaker everywhere while still allowing abrupt jumps.

The best behavior is:

```text
real mass and volume, smoothed heat and effective pressure
```

That keeps the system physically readable, Create-friendly, and much more pleasant to playtest.
