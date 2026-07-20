# Per-network pN/m² steam model verification

Date: 2026-06-06

Implements `new_steam_physics.md`: one shared pressure per connected steam network drives engine
output, venting, and bursting. Units are pN/m^2 (P_RATED = 1.0 MpN/m^2).

## What changed

- `SteamPressure` — pN/m^2 unit + format (pN/kpN/MpN) and pressure factor.
- `SteamPhysics` — reworked: temperatureK, pressurePn = gasConstant*stored*T/volume, height-scaled
  productionMb(usableHeat, height), requestedFlowMb, outputFactor = min(pressureF, flowF), continuous
  SU, linear RPM, ventMb, and burstPower.
- `SteamNetworkManager` — server level-tick handler. Per tick: BFS each loaded boiler outlet over
  Create pipes (valve-aware, stops at closed FluidValveBlock), aggregates stored steam + volume +
  production + temperature across the network, computes one pressure, fairly allocates per-engine draw
  caps, applies pressure/cap/venting/warn to member outlets + inlets, and bursts boilers past
  burstPressure. Outlets self-register on initialize; manager lazy-cleans stale entries.
- `FullSteamBoilerIntegration.usableHeatUnits` — burner heat under the footprint (normal=1, cake=2)
  capped by water.
- `BoilerOutletBlockEntity` — produces height-scaled steam into its buffer (the network's stored
  steam), keeps the pipe-flow transport, and shows the manager-provided network pressure/volume/
  engines/venting. No longer self-computes pressure or bursts.
- `SteamInletBlockEntity` — receives network pressure + fair draw cap from the manager.
- `PistonHeadBlockEntity` — engines draw through their active inlet up to the manager cap and output
  min(pressure, flow), with linear RPM and continuous SU. Goggles show pN/m^2.
- `FullSteamConfig` — replaced the bar group with pN/m^2 knobs (gasConstant, ratedPressure,
  warnPressure, burstPressure, temperature, fullEngineSu/Flow, maxRpm, ventCoefficient, bufferCap).

Equilibrium note: a matched boiler self-settles at exactly P_RATED (engine demand = fullFlow *
pressureFactor; production balances demand only when pressureFactor = 1). gasConstant only sets how
much stored steam a pressure represents (buffer fill / burst timing), not the equilibrium pressure.

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

## Manual (runClient — delete runs/client/config/full_steam_ahead-server.toml first). NOT YET RUN.

- [ ] 3x3x1 + 9 normal burners + 1 engine -> pressure ~1.0 MpN/m^2, ~90 mB/t, 147456 SU @ 64 RPM.
- [ ] 3x3x3 + 9 burners + 3 engines -> each rated, total 442368 SU.
- [ ] Undersupplied (260 mB/t, 3 engines) -> all below full, none starved, P below rated.
- [ ] Oversupplied closed network (no consumers, no vent) -> P rises -> warn -> burst.
- [ ] Open pipe end -> vents, P relieved.
- [ ] Closed valve between boiler and engine -> engine unpowered; boiler side may build pressure.
- [ ] Two boilers on one manifold -> productions sum; engines fair-share; outlets on one boiler don't duplicate.
- [ ] Goggles show MpN/m^2 (never bar). Reload preserves stored steam + state.

## Known follow-ups (not in this pass)

- Leak/exhaust particles + scald still scale from local steam amount, not network pressure.
- Every engine uses a pressure network; boiler overpressure is independent of engine placement.
- Calibration (gasConstant, ventCoefficient, burst timing) is first-pass; tune in-game.
