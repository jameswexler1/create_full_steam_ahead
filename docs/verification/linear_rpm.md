# Linear RPM Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` reached the main menu with the client phase mixin active

Verified on 2026-07-17. Compilation and the full build pass after stabilizing self-fed pump networks; the previous in-world animation-continuity validation remains recorded below.

## Animation Continuity

- [x] Apply a client-only phase correction through Create's `KineticBlockEntity.getRotationAngleOffset` hook, which is consumed by both Flywheel instances and fallback kinetic rendering.
- [x] Track phase independently for each FSA-driven kinetic block so shafts, gears, and other speed-ratio components retain their own continuous angle as source RPM changes.
- [x] Keep target RPM and SU continuous while coalescing Create kinetic-source propagation into shared 10-tick windows during active ramps.
- [x] Require `0.5 RPM` of accumulated active-ramp change before propagation, then apply the exact target after 20 stable ticks so steady-state RPM remains exact.
- [x] Keep starts, stops, reversals, ownership changes, and initial shaft synchronization immediate.
- [x] Refresh stress capacity without rebuilding the rotational network when only generated SU changes.
- [x] Hold the piston linkage's last moving angle across a transient zero-speed propagation frame; genuine shutdown still returns to the existing rest pose.
- [x] User validation on 2026-07-14: normal-gameplay RPM transitions remained visually continuous with no piston, linkage, or shaft restart/flicker.

## Self-Feed Stability Regression

- [x] Preserve per-tick thermodynamic target RPM, pressure, consumed steam, and generated SU calculations.
- [x] Align deferred generator updates across engine banks so several engines do not reset one shared kinetic/fluid network on staggered ticks.
- [x] Leave at least one complete five-tick Create boiler water-supply sample between deferred kinetic propagations.
- [x] Compare Create boiler attachment changes after FSA devices are included, eliminating the false stable `1 -> 0 -> 1` whistle-count transition.
- [x] Reevaluate Create boiler activation only when the FSA boiler source changes between inactive and active/residual-pressure states.
- [x] Unit-test immediate transitions, update windows, quiet period, accumulated deadband, exact settling, and no-op matching speeds.

## Expected Mapping

All engine source modes use `outputFactor = min(pressureFactor, deliveredFlowFactor)`.

```text
outputFactor == 0: 0 RPM
outputFactor > 0:  max(1, maxRpm * outputFactor)
```

With the default `maxRpm = 64`, quarter, half, three-quarter, and full output are respectively `16`, `32`, `48`, and `64 RPM`. Values between those points must no longer snap to a tier.

## Manual In-World Checks

- [ ] Direct compact engine: vary active burner/water-limited output and confirm RPM progresses instead of snapping to four plateaus.
- [ ] Unrestricted pipe-fed engine: vary pressure or available flow and confirm RPM follows the displayed output continuously.
- [ ] Admission-controlled engine at rated pressure: signals `0`, `5`, `10`, and `15` produce approximately `0`, `21.3`, `42.7`, and `64 RPM`.
- [ ] A positive output below `1/64` of full output runs at the `1 RPM` minimum; exactly zero output remains stopped.
- [ ] SU remains `147,456 * outputFactor` and still reaches `147,456 SU` at full output.
- [ ] Leaks, closed valves, boiler cooldown, and steam shortages lower RPM progressively with pressure/flow rather than stopping at old tier boundaries.
- [ ] Equal-output engines sharing a shaft remain synchronized and alternate piston phase correctly.
- [ ] Differently throttled generators on one shaft do not break, oscillate, or repeatedly rebuild the kinetic network; record the observed Create behavior.
- [ ] A steamless engine connected to a powered shaft continues animating passively at the shaft's actual speed.
- [ ] Slowly ramp pressure from zero to rated and back down; piston, connecting rod, crank, powered shaft, and attached shafts never restart, reverse, or flash to rest.
- [ ] Cause a sharp pressure change with a valve or leak; phase remains continuous even while the displayed RPM changes quickly.
- [ ] Repeat both ramp tests with Flywheel enabled and with its backend disabled to exercise fallback rendering.
- [ ] Attach gears, belts, a speedometer, and at least one passive engine; all connected visuals preserve phase and their proper speed ratios during RPM changes.
- [ ] Reload the world and unload/reload the engine chunk, then ramp RPM again; no stale client phase survives incorrectly.
- [ ] Assemble the running setup as a Sable/Aeronautics simulated contraption, vary admission, disassemble it, and confirm continuity and synchronization in each state.
- [ ] Self-feed regression: engine shaft network → speed controller at 256 RPM → pump directly into the boiler. Ramp pressure for at least 200 ticks and confirm water supply no longer cycles between full, partial, and empty.
- [ ] Repeat the self-feed regression with a stopped starter pump still attached; it must neither consume water nor destabilize the active pump.
- [ ] Repeat both self-feed checks in a normal world and on a Sable/Simulated contraption.
