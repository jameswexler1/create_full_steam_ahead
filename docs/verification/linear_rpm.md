# Linear RPM Verification

## Automated

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`

Verified on 2026-07-14. Compilation reports only the pre-existing deprecated Sable burst compatibility API note.

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
- [ ] Admission-controlled engine at rated pressure: signals `0`, `5`, `10`, and `15` produce approximately `64`, `42.7`, `21.3`, and `0 RPM`.
- [ ] A positive output below `1/64` of full output runs at the `1 RPM` minimum; exactly zero output remains stopped.
- [ ] SU remains `147,456 * outputFactor` and still reaches `147,456 SU` at full output.
- [ ] Leaks, closed valves, boiler cooldown, and steam shortages lower RPM progressively with pressure/flow rather than stopping at old tier boundaries.
- [ ] Equal-output engines sharing a shaft remain synchronized and alternate piston phase correctly.
- [ ] Differently throttled generators on one shaft do not break, oscillate, or repeatedly rebuild the kinetic network; record the observed Create behavior.
- [ ] A steamless engine connected to a powered shaft continues animating passively at the shaft's actual speed.
