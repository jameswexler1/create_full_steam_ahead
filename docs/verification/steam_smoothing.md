# Steam Pressure Smoothing / Inertia Verification

Date: 2026-06-06

Makes the per-network steam model feel real instead of twitchy. Stored steam mass and network volume
stay exact; only the *applied* pressure and the *boiler heat* ease toward their live targets (first
order). See `smoothing.md`.

## Implemented

- `SteamPhysics.approachExp(current, target, tauTicks)` — first-order exponential approach
  (`alpha = 1 - exp(-1/tau)`); tau <= 0 snaps.
- **Effective network pressure** (`SteamNetworkManager.smoothEffectivePressure`): each tick computes
  the live target pressure, reads `prevEffective` as the max of the member outlets' last
  `networkPressurePn` (no separate state map — the outlet BE already persists/syncs it), eases toward
  target with asymmetric tau (rise 60 / fall 80), divides tau by the emergency divisor when
  `target >= burst * emergencyMultiplier`, and clamps the per-tick move to `maxPressureDeltaPerTick`.
  Effective pressure drives engine draw/output, warnings, and bursts. Broken pipe ends drain stored
  steam toward `steamPhysics.openPipeTargetPressure` (`0 pN/m^2` by default) using the same smoothing
  curve, so the physical buffers do not empty instantly while engines still display nonzero pressure.
  After a burst, members'
  effective pressure is cleared to 0 (`clearEffectivePressure`).
- **Boiler thermal inertia**: pipe-fed boilers use shared manager state keyed by boiler controller
  position.
  Heat eases toward the water-gated target heat (heat-up 80 / cool-down 160 / dry 20 ticks).
  Production and temperature derive from effective heat. `SteamPhysics.temperatureK/productionMb` now take a double.
- New `steamSmoothing` config group: enabled, pressureRise/FallTauTicks, maxPressureDeltaPerTick,
  thermalInertiaEnabled, heatUp/coolDown/dryBoilerCoolDownTauTicks, emergencyPressureMultiplier,
  emergencyPressureTauDivisor.

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` after open-pipe atmospheric leak and shared boiler heat fixes

## Manual (runClient — delete runs/client/config/full_steam_ahead-server.toml first). NOT YET RUN.

- [ ] Stable 3x3x1 + 1 engine still settles ~1.0 MpN/m^2, full SU, 64 RPM (after a ~3 s ramp from placement).
- [ ] Break one active burner -> RPM/SU/pressure decay over seconds, not instantly; replace -> recover over seconds.
- [ ] Close a valve on a large boiler network -> pressure climbs over seconds (warning window), not 1-2 ticks.
- [ ] Sealed over-producing network still warns then bursts; with target >= 2x burst it accelerates.
- [ ] Open a pipe -> pressure decays toward `openPipeTargetPressure`; RPM/SU decay with the pressure instead of stopping on the first leak tick.
- [ ] Add a passive tank -> volume jumps immediately but effective pressure transitions smoothly.
- [ ] Multiple boilers on one network share one effective pressure; one boiler with multiple outlets does not multiply or temporarily lose production when a new outlet is added.
- [ ] Display Link + goggle pressures move smoothly and match. Reload mid-transition keeps effective pressure and seeds boiler heat from the current boiler target.
- [ ] `steamSmoothing.enabled=false` -> instant behavior returns.

## Notes

- Pipe-fed boiler heat is shared by physical boiler controller position, so multiple outlets split one smoothed production budget.
- A freshly placed network ramps from 0 over the rise tau (~3 s) — combined with heat warm-up this is
  the intended "spin-up" feel.
