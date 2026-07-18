# Engine Shaft Reload Continuity

## Automated Verification

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava test`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`

## Implementation Checks

- [x] Temporary load states do not demote a claimed powered shaft.
- [x] Definitive missing piston, shaft, or ring states still invalidate the engine.
- [x] Reserved stroke cells are invisible, non-colliding, itemless, and accepted by engine validation.
- [x] Reserved stroke cells connect the last piston body to the shaft for all supported one-to-three-block gaps.
- [x] Saved powered-shaft ownership can restore continuity before normal structure revalidation.
- [x] Create and Simulated movement rules include the reserved linkage cells.

## Manual World Verification

- [ ] Build an upright engine with one piston body and a one-block shaft gap. Save, leave the world, reload, and confirm the shaft remains attached and powered.
- [ ] Repeat upright with three piston bodies and a three-block shaft gap.
- [ ] Repeat both layouts upside down; confirm the shaft neither converts nor falls when the world loads.
- [ ] Repeat on a Simulated contraption with the shaft line otherwise isolated from the hull.
- [ ] Repeat on a Simulated contraption with the shaft network physically touching the hull.
- [ ] Test an engine that existed before this fix: load it once, confirm it remains intact, save, and reload again.
- [ ] Break a shaft, piston body, piston head, and cylinder wall in separate tests. Confirm normal disassembly and successful rebuilding each time.
- [ ] Confirm the animated connecting rod, collision, lighting, shaft RPM/SU, and upright/inverted visuals are unchanged.
