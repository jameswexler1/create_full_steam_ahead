# Phase 20 Verification: Pipe-Fed-Only Engines

## Scope

Direct compact engine mode has been removed. Create Fluid Tank boilers still produce, store,
pressurize, vent, display, and export steam through `boiler_outlet` blocks or eligible direct pipe
ports. Engines now consume steam only through one active assembled `steam_inlet`.

## Automated Results

- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `find src/main/resources -name '*.json' -exec jq empty {} +`
- [x] `git diff --check`

## Manual Migration Test

- [ ] Load an existing upright direct compact engine with no inlet.
- [ ] Confirm the world loads without a crash.
- [ ] Confirm the piston head reports `Steam inlet required`.
- [ ] Confirm the hidden powered shaft is restored to an ordinary Create shaft and produces no RPM
  or SU.
- [ ] Confirm the cylinder ring remains visually assembled and reports `No steam inlet`.

## Manual Functional Matrix

- [ ] Upright cylinder-only ring cannot preview, claim, or power a shaft.
- [ ] Inverted cylinder-only ring cannot preview, claim, or power a shaft.
- [ ] One active inlet allows shaft preview/placement in both orientations.
- [ ] An assembled inlet with no delivered steam keeps the passive linkage but produces 0 RPM/SU.
- [ ] Supplying pressure through pipes restores proportional RPM/SU, exhaust particles, sound, and
  animation.
- [ ] A second passive inlet remains decorative and does not duplicate demand.
- [ ] A boiler immediately below a ring provides no implicit power.
- [ ] Explicit outlets and direct boiler-to-pipe ports still feed engines normally.
- [ ] Sealed-boiler pressure, relief valves, goggles, Display Links, gauges, and bursts are unchanged.
- [ ] Simulated/Aeronautics assembly does not attach an unrelated Fluid Tank merely because it sits
  below an upright cylinder ring.
