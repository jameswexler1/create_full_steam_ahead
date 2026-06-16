# Phase 8.5 Verification

Date: 2026-06-01

Implemented:

- Added vertical `facing=up/down` state to piston head, piston, steam cylinder, and steam inlet blocks.
- Added stair-like vertical placement for piston head and piston body.
- Refactored engine validation around stroke direction while preserving upright direct compact and pipe-fed behavior.
- Added upside-down pipe-fed-only validation requiring one active assembled `steam_inlet`.
- Updated shaft placement helper, powered shaft survival, movement checks, cylinder hitboxes, and piston/linkage rendering for vertical inversion.
- Reused mirrored existing cylinder section models for upside-down assembled ring visuals.
- Follow-up fix: cylinder assembly now corrects stale piston head/body facing from the actual center position, validation retries the opposite stroke direction when placement state is stale, and animated partial models are explicitly flipped for inverted engines.
- Follow-up fix: regular shaft placement now revalidates nearby engines immediately instead of waiting for piston-head lazy validation.
- Follow-up fix: inverted linkage now renders as a single rigid 180-degree flip of the fully posed upright assembly about the head block center, applied as the outermost transform. The slider-crank is solved only in the upright frame; the previous approach negated per-part heights and crank rotation while flipping the raw model innermost, which pivoted the connecting rod about its big end and broke the joints. Connecting rod, crank, piston, and head now stay connected and the crank lands on the inverted shaft. Upright rendering is byte-identical (`orientForStroke` is a no-op for upward stroke).

Automated checks:

- [x] `find src/main/resources \( -name '*.json' -o -name '*.mcmeta' \) -exec jq empty {} +`
- [x] `git diff --check`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build`
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` after inverted power/render fix
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileJava` after shaft placement fix
- [x] `env GRADLE_USER_HOME=/tmp/gradle-home ./gradlew build` after inverted linkage rigid-flip fix
- [x] Offline kinematic check: rod small end meets piston wrist pin and rod big end meets the crank pin at all crank angles for both orientations (max joint error 2.2e-16); inverted crank pivot resolves to the inverted shaft block center.

Manual runtime checklist:

- [ ] Existing upright direct compact engine still assembles, animates, and produces expected SU/RPM.
- [ ] Existing upright pipe-fed engine still receives steam and produces expected SU/RPM.
- [ ] Piston head placement on an underside or upper-half side face creates a down-facing piston head.
- [ ] Upside-down pipe-fed engine assembles with shaft below, empty stroke space, piston body, and piston head in the upper ring center.
- [ ] Upside-down engine without a steam inlet remains invalid with a clear status.
- [ ] Upside-down engine does not run from a direct compact boiler and only runs from piped steam.
- [ ] Shaft ghost helper appears below the inverted piston body and places the shaft correctly.
- [ ] Inverted piston/head/linkage visuals animate along the downward stroke for both X and Z shaft axes.
- [ ] Save/reload preserves inverted assembly, shaft link, inlet link, and animation.
- [ ] Aeronautics/Simulated contraption preserves inverted engine structure and shaft output.
