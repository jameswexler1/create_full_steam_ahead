# Linear RPM Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew runClient` completed mod initialization and resource loading on 2026-07-17 with the network-shared client phase mixin and active-network retiming accessor applied

Verified on 2026-07-17. Unit tests and the full build pass after moving phase ownership from individual rendered blocks to the connected kinetic network and coordinating multiple FSA generators through one stable shaft command; the previous in-world animation-continuity validation remains recorded below.

## Animation Continuity

- [x] Apply a client-only phase correction through Create's `KineticBlockEntity.getRotationAngleOffset` hook, which is consumed by both Flywheel instances and fallback kinetic rendering.
- [x] Track one correction per FSA-driven kinetic network and derive each component correction from its stable signed speed ratio, so shafts, gears, and belts retain their mechanical relationship as source RPM changes.
- [x] Give every equal-speed powered shaft on one network the same corrected base angle before applying the existing ordered engine-bank offset (`0°/180°/0°` for three cylinders).
- [x] Retain unwrapped network correction internally so fractional and reversed speed ratios remain phase-correct, and weakly expire inactive network/component state after chunk or world transitions.
- [x] Keep target RPM and SU continuous while coalescing Create kinetic-source propagation into shared 10-tick windows during active ramps.
- [x] Require `0.5 RPM` of accumulated active-ramp change before propagation, then apply the exact target after 20 stable ticks so steady-state RPM remains exact.
- [x] Keep starts, stops, reversals, ownership changes, and initial shaft synchronization immediate.
- [x] Refresh stress capacity without rebuilding the rotational network when only generated SU changes.
- [x] Hold the piston linkage's last moving angle across a transient zero-speed propagation frame; genuine shutdown still returns to the existing rest pose.
- [x] User validation on 2026-07-14: normal-gameplay RPM transitions remained visually continuous with no piston, linkage, or shaft restart/flicker.
- [x] Unit coverage on 2026-07-17 verifies delayed components receive the same network correction, speed transitions preserve angle, signed/fractional speed ratios remain coherent, and the three-engine phase inputs remain A/B/A.

## Self-Feed Stability Regression

- [x] Preserve per-tick thermodynamic target RPM, pressure, consumed steam, and generated SU calculations.
- [x] Align deferred generator updates across engine banks so several engines do not reset one shared kinetic/fluid network on staggered ticks.
- [x] Leave at least one complete five-tick Create boiler water-supply sample between deferred kinetic propagations.
- [x] Compare Create boiler attachment changes after FSA devices are included, eliminating the false stable `1 -> 0 -> 1` whistle-count transition.
- [x] Reevaluate Create boiler activation only when the FSA boiler source changes between inactive and active/residual-pressure states.
- [x] Retime established active, same-direction kinetic source trees without detaching them, using Create's conveyed-speed calculation for gears, belts, chain drives, and rotation speed controllers.
- [x] Keep the existing FSA kinetic-network owner stable while connected FSA followers change fractional RPM; followers can raise the common command without detaching and taking ownership.
- [x] Convert each FSA generator's local target through its live speed ratio before selecting the strongest compatible command, preserving geared and reversed branch ratios.
- [x] Let an owner engine with zero personal output carry the common shaft command at zero personal capacity while another connected FSA engine remains active; a genuinely all-stopped bank still uses Create's normal stop propagation.
- [x] Preserve independent per-engine capacity accounting: shared RPM never duplicates SU, and a stopped engine contributes zero.
- [x] Notify downstream kinetic block entities only when their final speed changed; a pump held at constant speed by a rotation speed controller no longer receives the transient zero-speed callback that resets its fluid network.
- [x] Preserve Create's normal detach/attach behavior for starts, stops, reversals, missing networks, and generators following or competing with another source.
- [x] Keep a recently observed non-steam boiler input classified as an input for 40 ticks while Create temporarily clears pipe flow during a legitimate rebuild.
- [x] Unit-test immediate transitions, update windows, quiet period, accumulated deadband, exact settling, no-op matching speeds, and the in-place-retiming eligibility boundary.
- [x] Unit-test strongest-source selection, temporary owner dropout, geared local-to-owner speed conversion, all-stopped fallback, invalid ratios, and direction conflicts.

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
- [ ] Three equal-output engines on one straight shaft remain synchronized as A/B/A: the first and third reach maximum stroke together while the middle reaches minimum stroke, including during pressure-driven RPM ramps.
- [ ] Differently throttled generators on one shaft do not break, oscillate, or repeatedly rebuild the kinetic network; record the observed Create behavior.
- [ ] A steamless engine connected to a powered shaft continues animating passively at the shaft's actual speed.
- [ ] Slowly ramp pressure from zero to rated and back down; piston, connecting rod, crank, powered shaft, and attached shafts never restart, reverse, or flash to rest.
- [ ] Cause a sharp pressure change with a valve or leak; phase remains continuous even while the displayed RPM changes quickly.
- [ ] Repeat both ramp tests with Flywheel enabled and with its backend disabled to exercise fallback rendering.
- [ ] Attach gears, belts, a speedometer, and at least one passive engine; all connected visuals preserve phase and their proper speed ratios during RPM changes.
- [ ] Reload the world and unload/reload the engine chunk, then ramp RPM again; no stale client phase survives incorrectly.
- [ ] Assemble the running setup as a Sable/Aeronautics simulated contraption, vary admission, disassemble it, and confirm continuity and synchronization in each state.
- [ ] Self-feed regression: engine shaft network → speed controller at 256 RPM → pump directly into the boiler. Ramp pressure for at least 200 ticks and confirm water supply no longer cycles between full, partial, and empty.
- [ ] Repeat the cold self-feed startup with three FSA cylinders on the same shaft. Confirm the speed-controller output remains exactly 256 RPM while engine RPM rises fractionally and no cylinder repeatedly takes over the kinetic network.
- [ ] During the same startup, temporarily starve whichever cylinder currently owns the network while at least one adjacent cylinder remains powered. The pump and common shaft must continue without a zero-speed frame; total capacity must fall only by the starved cylinder's SU.
- [ ] Repeat the self-feed regression with a stopped starter pump still attached; it must neither consume water nor destabilize the active pump.
- [ ] Repeat both self-feed checks in a normal world and on a Sable/Simulated contraption.
